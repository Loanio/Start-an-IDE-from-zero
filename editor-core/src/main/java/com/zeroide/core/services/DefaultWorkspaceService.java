package com.zeroide.core.services;

import com.zeroide.api.EventBus;
import com.zeroide.api.WorkspaceService;
import com.zeroide.api.events.WorkspaceChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class DefaultWorkspaceService implements WorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(DefaultWorkspaceService.class);
    private static final String RECENT_KEY = "recentWorkspaces";
    private static final int MAX_RECENT_WORKSPACES = 8;

    private final EventBus eventBus;
    private final Preferences preferences;
    private Path workspace;

    public DefaultWorkspaceService(EventBus eventBus) {
        this.eventBus = eventBus;
        this.preferences = Preferences.userNodeForPackage(DefaultWorkspaceService.class);
        this.workspace = loadRecentWorkspaces().stream().findFirst().orElse(null);
    }

    @Override
    public Optional<Path> getWorkspace() {
        return Optional.ofNullable(workspace);
    }

    @Override
    public List<Path> getRecentWorkspaces() {
        return loadRecentWorkspaces();
    }

    @Override
    public void openWorkspace(Path path) {
        if (path == null) {
            return;
        }

        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            log.warn("Cannot open workspace {}, not a directory", normalized);
            return;
        }

        workspace = normalized;
        saveRecentWorkspaces(normalized);
        eventBus.publish(new WorkspaceChangedEvent(normalized));
    }

    private List<Path> loadRecentWorkspaces() {
        String raw = preferences.get(RECENT_KEY, "");
        if (raw.isBlank()) {
            return List.of();
        }

        List<Path> paths = new ArrayList<>();
        for (String value : raw.split("\\R")) {
            if (value.isBlank()) {
                continue;
            }
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                paths.add(path);
            }
        }
        return List.copyOf(paths);
    }

    private void saveRecentWorkspaces(Path selected) {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        paths.add(selected);
        paths.addAll(loadRecentWorkspaces());

        StringBuilder value = new StringBuilder();
        int count = 0;
        for (Path path : paths) {
            if (!Files.isDirectory(path)) {
                continue;
            }
            if (count == MAX_RECENT_WORKSPACES) {
                break;
            }
            if (!value.isEmpty()) {
                value.append(System.lineSeparator());
            }
            value.append(path);
            count++;
        }

        preferences.put(RECENT_KEY, value.toString());
        try {
            preferences.flush();
        } catch (BackingStoreException ex) {
            log.warn("Cannot persist recent workspaces", ex);
        }
    }
}
