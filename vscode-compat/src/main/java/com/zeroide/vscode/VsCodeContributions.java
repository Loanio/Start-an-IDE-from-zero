package com.zeroide.vscode;

import java.util.List;

public record VsCodeContributions(
        List<VsCodeCommandContribution> commands,
        List<VsCodeLanguageContribution> languages,
        List<VsCodeGrammarContribution> grammars,
        List<VsCodeSnippetContribution> snippets,
        List<VsCodeThemeContribution> themes
) {
    public VsCodeContributions {
        commands = commands == null ? List.of() : List.copyOf(commands);
        languages = languages == null ? List.of() : List.copyOf(languages);
        grammars = grammars == null ? List.of() : List.copyOf(grammars);
        snippets = snippets == null ? List.of() : List.copyOf(snippets);
        themes = themes == null ? List.of() : List.copyOf(themes);
    }
}
