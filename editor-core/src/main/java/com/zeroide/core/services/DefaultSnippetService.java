package com.zeroide.core.services;

import com.zeroide.api.Snippet;
import com.zeroide.api.SnippetService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultSnippetService implements SnippetService {
    private final Map<String, Snippet> snippets = new LinkedHashMap<>();

    @Override
    public void registerSnippet(Snippet snippet) {
        snippets.put(snippet.id(), snippet);
    }

    @Override
    public void unregisterSnippet(String snippetId) {
        snippets.remove(snippetId);
    }

    @Override
    public List<Snippet> snippets(String languageId) {
        return snippets.values().stream()
                .filter(snippet -> snippet.languageId().equals(languageId))
                .toList();
    }

    public List<Snippet> allSnippets() {
        return new ArrayList<>(snippets.values());
    }
}
