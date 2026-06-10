package com.zeroide.core.services;

import com.zeroide.api.Snippet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSnippetServiceTest {
    @Test
    void registersAndFiltersSnippetsByLanguage() {
        DefaultSnippetService service = new DefaultSnippetService();
        service.registerSnippet(new Snippet("java.main", "Main", "java", "public static void main(String[] args) {}"));
        service.registerSnippet(new Snippet("json.object", "Object", "json", "{}"));

        assertEquals(1, service.snippets("java").size());
        assertEquals("java.main", service.snippets("java").getFirst().id());
    }

    @Test
    void unregisterRemovesSnippet() {
        DefaultSnippetService service = new DefaultSnippetService();
        service.registerSnippet(new Snippet("java.main", "Main", "java", "body"));

        service.unregisterSnippet("java.main");

        assertTrue(service.snippets("java").isEmpty());
    }
}
