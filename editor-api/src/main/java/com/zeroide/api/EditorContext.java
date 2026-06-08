package com.zeroide.api;

public interface EditorContext {
    EditorService editor();

    EventBus events();

    UIService ui();

    WorkspaceService workspace();
}
