package com.zeroide.core.plugins;

import com.zeroide.api.PluginDescriptor;

import java.nio.file.Path;

record PluginCandidate(Path jarPath, PluginDescriptor descriptor) {
}
