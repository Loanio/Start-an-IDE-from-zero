package com.zeroide.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface LanguageService {
    void registerLanguage(LanguageDefinition language);

    void unregisterLanguage(String languageId);

    Optional<LanguageDefinition> language(String languageId);

    Optional<LanguageDefinition> detectLanguage(Path path);

    List<LanguageDefinition> languages();
}
