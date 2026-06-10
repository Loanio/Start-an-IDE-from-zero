package com.zeroide.plugins.markdownlanguage;

import com.zeroide.api.EditorContext;
import com.zeroide.api.HighlightSpan;
import com.zeroide.api.LanguageDefinition;
import com.zeroide.api.Plugin;
import com.zeroide.api.SyntaxHighlighter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownLanguagePlugin implements Plugin {
    private static final String LANGUAGE_ID = "markdown";
    private static final String STATUS_ID = "plugin.markdown-language.status";

    private EditorContext context;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.languages().registerLanguage(new LanguageDefinition(
                LANGUAGE_ID,
                "Markdown",
                List.of("Markdown", "md"),
                List.of(".md", ".markdown"),
                List.of("README.md")
        ));
        context.highlighting().registerHighlighter(LANGUAGE_ID, new MarkdownSyntaxHighlighter());
        context.notifications().addStatusItem(STATUS_ID, "Markdown language");
    }

    @Override
    public void onUnload() {
        if (context != null) {
            context.highlighting().unregisterHighlighter(LANGUAGE_ID);
            context.languages().unregisterLanguage(LANGUAGE_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private static final class MarkdownSyntaxHighlighter implements SyntaxHighlighter {
        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                "(?<HEADING>^#{1,6} .*$)"
                        + "|(?<QUOTE>^>.*$)"
                        + "|(?<LIST>^\\s*([-*+] |\\d+\\. ).*$)"
                        + "|(?<CODE>`[^`]+`|^```.*$)",
                Pattern.MULTILINE
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
            if (matcher.group("HEADING") != null) {
                return "markup-heading";
            }
            if (matcher.group("QUOTE") != null) {
                return "markup-quote";
            }
            if (matcher.group("LIST") != null) {
                return "markup-list";
            }
            if (matcher.group("CODE") != null) {
                return "markup-code";
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
