package com.zeroide.core.plugins;

import com.zeroide.api.Plugin;
import com.zeroide.api.PluginDescriptor;

import java.net.URLClassLoader;
import java.nio.file.Path;

public record LoadedPlugin(
        Path jarPath,
        PluginDescriptor descriptor,
        Plugin instance,
        URLClassLoader classLoader
) {
}
