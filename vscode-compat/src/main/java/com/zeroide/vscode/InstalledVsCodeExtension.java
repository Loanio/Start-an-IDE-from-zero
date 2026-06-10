package com.zeroide.vscode;

import java.nio.file.Path;

public record InstalledVsCodeExtension(
        VsCodeExtensionDescriptor descriptor,
        Path installPath
) {
}
