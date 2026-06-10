package com.zeroide.api;

import java.util.Optional;

public interface HighlightingService {
    void registerHighlighter(String languageId, SyntaxHighlighter highlighter);

    void unregisterHighlighter(String languageId);

    Optional<SyntaxHighlighter> highlighter(String languageId);
}
