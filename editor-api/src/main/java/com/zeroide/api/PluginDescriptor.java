package com.zeroide.api;

import java.util.List;

public record PluginDescriptor(
        String id,
        String name,
        String version,
        String entryClass,
        List<String> dependencies
) {
    public PluginDescriptor {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
