package com.zeroide.plugins.jsonlanguage;

import com.zeroide.api.EditorContext;
import com.zeroide.api.HighlightSpan;
import com.zeroide.api.LanguageDefinition;
import com.zeroide.api.Plugin;
import com.zeroide.api.SyntaxHighlighter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonLanguagePlugin implements Plugin {
    private static final String LANGUAGE_ID = "json";
    private static final String STATUS_ID = "plugin.json-language.status";

    private EditorContext context;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.languages().registerLanguage(new LanguageDefinition(
                LANGUAGE_ID,
                "JSON",
                List.of("JSON"),
                List.of(".json", ".jsonc"),
                List.of()
        ));
        context.highlighting().registerHighlighter(LANGUAGE_ID, new JsonSyntaxHighlighter());
        context.notifications().addStatusItem(STATUS_ID, "JSON language");
    }

    @Override
    public void onUnload() {
        if (context != null) {
            context.highlighting().unregisterHighlighter(LANGUAGE_ID);
            context.languages().unregisterLanguage(LANGUAGE_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private static final class JsonSyntaxHighlighter implements SyntaxHighlighter {
        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                "(?<PROPERTY>\"([^\"\\\\]|\\\\.)*\"\\s*:)"
                        + "|(?<STRING>\"([^\"\\\\]|\\\\.)*\")"
                        + "|(?<NUMBER>-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)"
                        + "|(?<LITERAL>\\b(?:true|false|null)\\b)"
                        + "|(?<BRACE>[{}\\[\\],:])"
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
            if (matcher.group("PROPERTY") != null) {
                return "property";
            }
            if (matcher.group("STRING") != null) {
                return "string";
            }
            if (matcher.group("NUMBER") != null) {
                return "number";
            }
            if (matcher.group("LITERAL") != null) {
                return "literal";
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
