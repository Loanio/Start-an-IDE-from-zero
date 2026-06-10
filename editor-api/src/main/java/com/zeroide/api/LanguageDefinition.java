package com.zeroide.api;

import java.util.List;

public record LanguageDefinition(
        String id,
        String displayName,
        List<String> aliases,
        List<String> extensions,
        List<String> filenames
) {
    public LanguageDefinition {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        extensions = extensions == null ? List.of() : List.copyOf(extensions);
        filenames = filenames == null ? List.of() : List.copyOf(filenames);
    }
}
