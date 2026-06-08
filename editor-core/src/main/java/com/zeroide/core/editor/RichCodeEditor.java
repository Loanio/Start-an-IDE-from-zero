package com.zeroide.core.editor;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RichCodeEditor extends CodeArea {
    private static final String[] KEYWORDS = new String[]{
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record",
            "sealed", "permits", "non-sealed", "yield"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String CHAR_PATTERN = "'([^'\\\\]|\\\\.)'";
    private static final String COMMENT_PATTERN = "//[^\\n]*|/\\*(.|\\R)*?\\*/";
    private static final String NUMBER_PATTERN = "\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b";
    private static final String BRACE_PATTERN = "[{}()\\[\\]]";

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<CHAR>" + CHAR_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
                    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
    );

    public RichCodeEditor() {
        getStyleClass().add("code-editor");
        setWrapText(false);
        setParagraphGraphicFactory(LineNumberFactory.get(this));
        textProperty().addListener((ignored, oldText, newText) -> applyHighlighting(newText));
    }

    public void setInitialText(String text) {
        replaceText(text == null ? "" : text);
        getUndoManager().forgetHistory();
        moveTo(0);
        requestFollowCaret();
    }

    private void applyHighlighting(String text) {
        setStyleSpans(0, computeHighlighting(text == null ? "" : text));
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        int lastKeywordEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = styleClassFor(matcher);
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKeywordEnd);
            spansBuilder.add(List.of(styleClass), matcher.end() - matcher.start());
            lastKeywordEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKeywordEnd);
        return spansBuilder.create();
    }

    private static String styleClassFor(Matcher matcher) {
        if (matcher.group("KEYWORD") != null) {
            return "keyword";
        }
        if (matcher.group("STRING") != null) {
            return "string";
        }
        if (matcher.group("CHAR") != null) {
            return "char";
        }
        if (matcher.group("COMMENT") != null) {
            return "comment";
        }
        if (matcher.group("NUMBER") != null) {
            return "number";
        }
        if (matcher.group("BRACE") != null) {
            return "brace";
        }
        return "";
    }
}
