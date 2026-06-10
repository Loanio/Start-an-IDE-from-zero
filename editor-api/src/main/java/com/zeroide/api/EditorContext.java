package com.zeroide.api;

public interface EditorContext {
    EditorService editor();

    EventBus events();

    UIService ui();

    WorkspaceService workspace();

    PanelService panels();

    CommandService commands();

    NotificationService notifications();

    LanguageService languages();

    HighlightingService highlighting();

    SnippetService snippets();
}
