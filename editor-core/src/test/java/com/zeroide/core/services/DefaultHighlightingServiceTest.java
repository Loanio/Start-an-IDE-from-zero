package com.zeroide.core.services;

import com.zeroide.api.HighlightSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultHighlightingServiceTest {
    @Test
    void registersAndUnregistersHighlighter() {
        DefaultHighlightingService service = new DefaultHighlightingService();
        service.registerHighlighter("plain", text -> List.of(new HighlightSpan(text.length(), null)));

        assertEquals(1, service.highlighter("plain").orElseThrow().highlight("abc").size());

        service.unregisterHighlighter("plain");

        assertTrue(service.highlighter("plain").isEmpty());
    }
}
