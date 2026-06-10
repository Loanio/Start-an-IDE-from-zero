package com.zeroide.vscode;

public record VsCodeGrammarContribution(
        String language,
        String scopeName,
        String path
) {
}
