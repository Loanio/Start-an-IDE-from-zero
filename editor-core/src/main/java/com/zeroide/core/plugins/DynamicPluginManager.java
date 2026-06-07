package com.zeroide.core.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.PluginDescriptor;
import com.zeroide.api.events.PluginLoadedEvent;
import com.zeroide.api.events.PluginUnloadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;

public final class DynamicPluginManager {
    private static final Logger log = LoggerFactory.getLogger(DynamicPluginManager.class);

    private final Path pluginDirectory;
    private final EditorContext editorContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, LoadedPlugin> loadedPlugins = new LinkedHashMap<>();

    public DynamicPluginManager(Path pluginDirectory, EditorContext editorContext) {
        this.pluginDirectory = pluginDirectory;
        this.editorContext = editorContext;
    }

    public Path pluginDirectory() {
        return pluginDirectory;
    }

    public List<LoadedPlugin> loadedPlugins() {
        return List.copyOf(loadedPlugins.values());
    }

    public List<PluginDescriptor> loadAll() {
        try {
            Files.createDirectories(pluginDirectory);
        } catch (IOException ex) {
            log.warn("Cannot create plugin directory {}", pluginDirectory, ex);
            return List.of();
        }

        List<PluginCandidate> candidates = discoverCandidates();
        Map<String, PluginCandidate> pending = new HashMap<>();
        for (PluginCandidate candidate : candidates) {
            pending.put(candidate.descriptor().id(), candidate);
        }

        List<PluginDescriptor> loaded = new ArrayList<>();
        boolean progressed;
        do {
            progressed = false;
            List<PluginCandidate> ready = pending.values().stream()
                    .filter(candidate -> dependenciesLoaded(candidate.descriptor()))
                    .sorted(Comparator.comparing(candidate -> candidate.descriptor().id()))
                    .toList();
            for (PluginCandidate candidate : ready) {
                if (load(candidate.jarPath()).isPresent()) {
                    loaded.add(candidate.descriptor());
                }
                pending.remove(candidate.descriptor().id());
                progressed = true;
            }
        } while (progressed && !pending.isEmpty());

        pending.values().forEach(candidate -> log.warn(
                "Plugin {} skipped because dependencies are missing: {}",
                candidate.descriptor().id(),
                candidate.descriptor().dependencies()
        ));

        return loaded;
    }

    public Optional<LoadedPlugin> load(Path jarPath) {
        try {
            PluginDescriptor descriptor = readDescriptor(jarPath);
            if (loadedPlugins.containsKey(descriptor.id())) {
                log.info("Plugin {} is already loaded", descriptor.id());
                return Optional.empty();
            }
            if (!dependenciesLoaded(descriptor)) {
                log.warn("Plugin {} has unresolved dependencies {}", descriptor.id(), descriptor.dependencies());
                return Optional.empty();
            }

            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, Plugin.class.getClassLoader());
            Class<?> pluginClass = Class.forName(descriptor.entryClass(), true, classLoader);
            if (!Plugin.class.isAssignableFrom(pluginClass)) {
                classLoader.close();
                throw new IllegalArgumentException(descriptor.entryClass() + " does not implement Plugin");
            }

            Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();
            plugin.onLoad(editorContext);

            LoadedPlugin loadedPlugin = new LoadedPlugin(jarPath, descriptor, plugin, classLoader);
            loadedPlugins.put(descriptor.id(), loadedPlugin);
            editorContext.events().publish(new PluginLoadedEvent(descriptor));
            log.info("Loaded plugin {} from {}", descriptor.id(), jarPath);
            return Optional.of(loadedPlugin);
        } catch (Exception ex) {
            log.warn("Failed to load plugin jar {}", jarPath, ex);
            return Optional.empty();
        }
    }

    public boolean unload(String pluginId) {
        LoadedPlugin loadedPlugin = loadedPlugins.get(pluginId);
        if (loadedPlugin == null) {
            return false;
        }

        List<String> dependents = loadedPlugins.values().stream()
                .map(LoadedPlugin::descriptor)
                .filter(descriptor -> descriptor.dependencies().contains(pluginId))
                .map(PluginDescriptor::id)
                .toList();
        dependents.forEach(this::unload);

        loadedPlugins.remove(pluginId);
        try {
            loadedPlugin.instance().onUnload();
        } catch (Exception ex) {
            log.warn("Plugin {} failed during unload", pluginId, ex);
        }
        try {
            loadedPlugin.classLoader().close();
        } catch (IOException ex) {
            log.warn("Class loader close failed for plugin {}", pluginId, ex);
        }
        editorContext.events().publish(new PluginUnloadedEvent(loadedPlugin.descriptor()));
        log.info("Unloaded plugin {}", pluginId);
        return true;
    }

    public void unloadAll() {
        List<String> pluginIds = new ArrayList<>(loadedPlugins.keySet());
        for (int i = pluginIds.size() - 1; i >= 0; i--) {
            unload(pluginIds.get(i));
        }
    }

    private List<PluginCandidate> discoverCandidates() {
        try (var paths = Files.list(pluginDirectory)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(this::safeCandidate)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException ex) {
            log.warn("Cannot scan plugin directory {}", pluginDirectory, ex);
            return List.of();
        }
    }

    private Optional<PluginCandidate> safeCandidate(Path jarPath) {
        try {
            return Optional.of(new PluginCandidate(jarPath, readDescriptor(jarPath)));
        } catch (Exception ex) {
            log.warn("Plugin jar {} skipped", jarPath, ex);
            return Optional.empty();
        }
    }

    private boolean dependenciesLoaded(PluginDescriptor descriptor) {
        return loadedPlugins.keySet().containsAll(descriptor.dependencies());
    }

    private PluginDescriptor readDescriptor(Path jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            var entry = jarFile.getJarEntry("plugin.json");
            if (entry == null) {
                throw new IOException("Missing plugin.json");
            }
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                PluginMetadata metadata = objectMapper.readValue(inputStream, PluginMetadata.class);
                validate(metadata, jarPath);
                return new PluginDescriptor(
                        metadata.id,
                        metadata.name,
                        metadata.version,
                        metadata.entryClass,
                        metadata.dependencies
                );
            }
        }
    }

    private static void validate(PluginMetadata metadata, Path jarPath) {
        if (isBlank(metadata.id) || isBlank(metadata.name) || isBlank(metadata.version) || isBlank(metadata.entryClass)) {
            throw new IllegalArgumentException("Invalid plugin.json in " + jarPath);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
