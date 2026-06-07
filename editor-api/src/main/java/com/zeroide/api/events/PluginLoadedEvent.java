package com.zeroide.api.events;

import com.zeroide.api.PluginDescriptor;

public record PluginLoadedEvent(PluginDescriptor descriptor) {
}
