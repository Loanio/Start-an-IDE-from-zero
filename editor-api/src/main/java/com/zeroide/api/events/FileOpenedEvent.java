package com.zeroide.api.events;

import java.nio.file.Path;

public record FileOpenedEvent(Path path, String text) {
}
