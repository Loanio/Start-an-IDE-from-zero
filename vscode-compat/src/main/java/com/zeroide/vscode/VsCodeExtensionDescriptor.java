package com.zeroide.vscode;

import java.nio.file.Path;
import java.util.List;

public record VsCodeExtensionDescriptor(
        String id,
        String name,
        String displayName,
        String version,
        String publisher,
        String description,
        String vscodeEngine,
        String main,
        String browser,
        List<String> activationEvents,
        VsCodeContributions contributions,
        Path extensionRoot
) {
    public VsCodeExtensionDescriptor {
        activationEvents = activationEvents == null ? List.of() : List.copyOf(activationEvents);
        contributions = contributions == null
                ? new VsCodeContributions(List.of(), List.of(), List.of(), List.of(), List.of())
                : contributions;
    }

    public boolean hasExecutableEntryPoint() {
        return isPresent(main) || isPresent(browser);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
