package com.zeroide.core.editor;

import com.zeroide.api.HighlightSpan;
import com.zeroide.api.HighlightingService;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class RichCodeEditor extends CodeArea {
    private HighlightingService highlightingService;
    private String languageId;

    public RichCodeEditor() {
        getStyleClass().add("code-editor");
        setWrapText(false);
        setParagraphGraphicFactory(LineNumberFactory.get(this));
        textProperty().addListener((ignored, oldText, newText) -> applyHighlighting(newText));
    }

    public void setHighlightingService(HighlightingService highlightingService) {
        this.highlightingService = highlightingService;
        applyHighlighting(getText());
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
        applyHighlighting(getText());
    }

    public void setInitialText(String text) {
        replaceText(text == null ? "" : text);
        getUndoManager().forgetHistory();
        moveTo(0);
        requestFollowCaret();
    }

    private void applyHighlighting(String text) {
        setStyleSpans(0, computeHighlighting(text == null ? "" : text, highlightingService, languageId));
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text, HighlightingService highlightingService, String languageId) {
        List<HighlightSpan> spans = highlightingService == null || languageId == null
                ? List.of(new HighlightSpan(text.length(), null))
                : highlightingService.highlighter(languageId)
                .map(highlighter -> highlighter.highlight(text))
                .orElseGet(() -> List.of(new HighlightSpan(text.length(), null)));
        return toStyleSpans(text.length(), spans);
    }

    private static StyleSpans<Collection<String>> toStyleSpans(int textLength, List<HighlightSpan> spans) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int consumed = 0;

        for (HighlightSpan span : new ArrayList<>(spans)) {
            if (consumed >= textLength) {
                break;
            }
            int length = Math.max(0, Math.min(span.length(), textLength - consumed));
            if (length == 0) {
                continue;
            }
            spansBuilder.add(styleClasses(span.styleClass()), length);
            consumed += length;
        }

        if (consumed < textLength) {
            spansBuilder.add(Collections.emptyList(), textLength - consumed);
        }
        if (textLength == 0) {
            spansBuilder.add(Collections.emptyList(), 0);
        }
        return spansBuilder.create();
    }

    private static Collection<String> styleClasses(String styleClass) {
        if (styleClass == null || styleClass.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(styleClass);
    }
}
