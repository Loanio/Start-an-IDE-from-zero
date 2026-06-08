package com.zeroide.plugins.wordcount;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.TextChangedEvent;

public final class WordCountPlugin implements Plugin {
    private static final String STATUS_ID = "plugin.word-count.status";

    private EditorContext context;
    private Subscription textSubscription;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.notifications().addStatusItem(STATUS_ID, format(context.editor().getText()));
        textSubscription = context.events().subscribe(TextChangedEvent.class, event ->
                context.notifications().updateStatusItem(STATUS_ID, format(event.text()))
        );
    }

    @Override
    public void onUnload() {
        if (textSubscription != null) {
            textSubscription.close();
        }
        if (context != null) {
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private static String format(String text) {
        int words = countWords(text);
        return "Words " + words;
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
