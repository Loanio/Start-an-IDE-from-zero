package com.zeroide.core.services;

import com.zeroide.api.EditorContext;
import com.zeroide.api.EditorService;
import com.zeroide.api.EventBus;
import com.zeroide.api.UIService;

public record DefaultEditorContext(
        EditorService editor,
        EventBus events,
        UIService ui
) implements EditorContext {
}
