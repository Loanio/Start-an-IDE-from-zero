package com.zeroide.api.events;

import java.nio.file.Path;

public record WorkspaceChangedEvent(Path workspace) {
}
