package com.zeroide.vscode;

import java.util.List;

public record VsCodeLanguageContribution(
        String id,
        List<String> aliases,
        List<String> extensions,
        List<String> filenames,
        String configuration
) {
    public VsCodeLanguageContribution {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        extensions = extensions == null ? List.of() : List.copyOf(extensions);
        filenames = filenames == null ? List.of() : List.copyOf(filenames);
    }
}
