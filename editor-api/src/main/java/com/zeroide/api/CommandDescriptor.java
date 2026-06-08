package com.zeroide.api;

import java.util.Optional;

public record CommandDescriptor(String id, String title, Optional<String> keyBinding) {
    public CommandDescriptor(String id, String title, String keyBinding) {
        this(id, title, Optional.ofNullable(keyBinding).filter(value -> !value.isBlank()));
    }
}
