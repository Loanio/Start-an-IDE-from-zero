package com.zeroide.core.services;

import com.zeroide.api.EditorContext;
import com.zeroide.api.EditorService;
import com.zeroide.api.EventBus;
import com.zeroide.api.UIService;
import com.zeroide.api.WorkspaceService;

public record DefaultEditorContext(
        EditorService editor,
        EventBus events,
        UIService ui,
        WorkspaceService workspace
) implements EditorContext {
}
