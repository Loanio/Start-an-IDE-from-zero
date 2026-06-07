package com.zeroide.api.events;

import java.nio.file.Path;

public record FileSavedEvent(Path path, String text) {
}
