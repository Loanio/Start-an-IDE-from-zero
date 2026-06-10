package com.zeroide.core.services;

import com.zeroide.api.LanguageDefinition;
import com.zeroide.api.LanguageService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class DefaultLanguageService implements LanguageService {
    private final Map<String, LanguageDefinition> languages = new LinkedHashMap<>();

    @Override
    public void registerLanguage(LanguageDefinition language) {
        languages.put(language.id(), language);
    }

    @Override
    public void unregisterLanguage(String languageId) {
        languages.remove(languageId);
    }

    @Override
    public Optional<LanguageDefinition> language(String languageId) {
        return Optional.ofNullable(languages.get(languageId));
    }

    @Override
    public Optional<LanguageDefinition> detectLanguage(Path path) {
        if (path == null || path.getFileName() == null) {
            return Optional.empty();
        }

        String fileName = path.getFileName().toString();
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        String extension = extensionOf(fileName);
        return languages.values().stream()
                .filter(language -> matches(language, normalizedName, extension))
                .findFirst();
    }

    @Override
    public List<LanguageDefinition> languages() {
        return new ArrayList<>(languages.values());
    }

    private static boolean matches(LanguageDefinition language, String normalizedName, String extension) {
        boolean filenameMatch = language.filenames().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedName::equals);
        boolean extensionMatch = !extension.isBlank() && language.extensions().stream()
                .map(DefaultLanguageService::normalizeExtension)
                .anyMatch(extension::equals);
        return filenameMatch || extensionMatch;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String normalizeExtension(String extension) {
        String value = extension.toLowerCase(Locale.ROOT);
        return value.startsWith(".") ? value : "." + value;
    }
}
