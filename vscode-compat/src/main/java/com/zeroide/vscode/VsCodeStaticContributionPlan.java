package com.zeroide.vscode;

import java.util.List;

public record VsCodeStaticContributionPlan(
        String extensionId,
        boolean executableExtension,
        String compatibilityNote,
        List<VsCodeCommandContribution> visibleCommands,
        List<VsCodeLanguageContribution> languages,
        List<VsCodeGrammarContribution> grammars,
        List<VsCodeSnippetContribution> snippets,
        List<VsCodeThemeContribution> themes
) {
    public VsCodeStaticContributionPlan {
        visibleCommands = visibleCommands == null ? List.of() : List.copyOf(visibleCommands);
        languages = languages == null ? List.of() : List.copyOf(languages);
        grammars = grammars == null ? List.of() : List.copyOf(grammars);
        snippets = snippets == null ? List.of() : List.copyOf(snippets);
        themes = themes == null ? List.of() : List.copyOf(themes);
    }
}
