package com.zeroide.vscode;

public record VsCodeCommandContribution(
        String command,
        String title,
        String category
) {
}
