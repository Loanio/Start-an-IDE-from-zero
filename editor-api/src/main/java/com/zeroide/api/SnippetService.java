package com.zeroide.api;

import java.util.List;

public interface SnippetService {
    void registerSnippet(Snippet snippet);

    void unregisterSnippet(String snippetId);

    List<Snippet> snippets(String languageId);
}
