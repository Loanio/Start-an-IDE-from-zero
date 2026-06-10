package com.zeroide.plugins.javalanguage;

import com.zeroide.api.EditorContext;
import com.zeroide.api.HighlightSpan;
import com.zeroide.api.LanguageDefinition;
import com.zeroide.api.Plugin;
import com.zeroide.api.SyntaxHighlighter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaLanguagePlugin implements Plugin {
    private static final String LANGUAGE_ID = "java";
    private static final String STATUS_ID = "plugin.java-language.status";

    private EditorContext context;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.languages().registerLanguage(new LanguageDefinition(
                LANGUAGE_ID,
                "Java",
                List.of("Java"),
                List.of(".java"),
                List.of()
        ));
        context.highlighting().registerHighlighter(LANGUAGE_ID, new JavaSyntaxHighlighter());
        context.notifications().addStatusItem(STATUS_ID, "Java language");
    }

    @Override
    public void onUnload() {
        if (context != null) {
            context.highlighting().unregisterHighlighter(LANGUAGE_ID);
            context.languages().unregisterLanguage(LANGUAGE_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private static final class JavaSyntaxHighlighter implements SyntaxHighlighter {
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
                "(?<COMMENT>" + COMMENT_PATTERN + ")"
                        + "|(?<STRING>" + STRING_PATTERN + ")"
                        + "|(?<CHAR>" + CHAR_PATTERN + ")"
                        + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                        + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                        + "|(?<BRACE>" + BRACE_PATTERN + ")"
        );

        @Override
        public List<HighlightSpan> highlight(String text) {
            Matcher matcher = TOKEN_PATTERN.matcher(text);
            List<HighlightSpan> spans = new ArrayList<>();
            int lastEnd = 0;
            while (matcher.find()) {
                addSpan(spans, matcher.start() - lastEnd, null);
                addSpan(spans, matcher.end() - matcher.start(), styleClassFor(matcher));
                lastEnd = matcher.end();
            }
            addSpan(spans, text.length() - lastEnd, null);
            return spans;
        }

        private static String styleClassFor(Matcher matcher) {
            if (matcher.group("COMMENT") != null) {
                return "comment";
            }
            if (matcher.group("STRING") != null) {
                return "string";
            }
            if (matcher.group("CHAR") != null) {
                return "char";
            }
            if (matcher.group("KEYWORD") != null) {
                return "keyword";
            }
            if (matcher.group("NUMBER") != null) {
                return "number";
            }
            if (matcher.group("BRACE") != null) {
                return "brace";
            }
            return null;
        }

        private static void addSpan(List<HighlightSpan> spans, int length, String styleClass) {
            if (length > 0) {
                spans.add(new HighlightSpan(length, styleClass));
            }
        }
    }
}
