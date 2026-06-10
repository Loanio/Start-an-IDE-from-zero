package com.zeroide.core.services;

import com.zeroide.api.LanguageDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLanguageServiceTest {
    @Test
    void detectsLanguageByExtensionAndFilename() {
        DefaultLanguageService service = new DefaultLanguageService();
        service.registerLanguage(new LanguageDefinition(
                "markdown",
                "Markdown",
                List.of("md"),
                List.of(".md", "markdown"),
                List.of("README")
        ));

        assertEquals("markdown", service.detectLanguage(Path.of("notes.md")).orElseThrow().id());
        assertEquals("markdown", service.detectLanguage(Path.of("README")).orElseThrow().id());
    }

    @Test
    void unregisterRemovesLanguageDetection() {
        DefaultLanguageService service = new DefaultLanguageService();
        service.registerLanguage(new LanguageDefinition("json", "JSON", List.of(), List.of(".json"), List.of()));

        service.unregisterLanguage("json");

        assertTrue(service.detectLanguage(Path.of("settings.json")).isEmpty());
    }
}
