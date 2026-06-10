package com.zeroide.core.services;

import com.zeroide.api.HighlightingService;
import com.zeroide.api.SyntaxHighlighter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class DefaultHighlightingService implements HighlightingService {
    private final Map<String, SyntaxHighlighter> highlighters = new LinkedHashMap<>();

    @Override
    public void registerHighlighter(String languageId, SyntaxHighlighter highlighter) {
        highlighters.put(languageId, highlighter);
    }

    @Override
    public void unregisterHighlighter(String languageId) {
        highlighters.remove(languageId);
    }

    @Override
    public Optional<SyntaxHighlighter> highlighter(String languageId) {
        return Optional.ofNullable(highlighters.get(languageId));
    }
}
