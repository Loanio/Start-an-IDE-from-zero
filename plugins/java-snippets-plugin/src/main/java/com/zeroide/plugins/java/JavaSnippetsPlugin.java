package com.zeroide.plugins.java;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Snippet;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.FileOpenedEvent;

public final class JavaSnippetsPlugin implements Plugin {
    private static final String STATUS_ID = "plugin.java.status";
    private static final String SOUT_ID = "plugin.java.sout";
    private static final String MAIN_ID = "plugin.java.main";
    private static final String SOUT_SNIPPET_ID = "snippet.java.sout";
    private static final String MAIN_SNIPPET_ID = "snippet.java.main";

    private EditorContext context;
    private Subscription fileSubscription;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.notifications().addStatusItem(STATUS_ID, "Java snippets");
        context.snippets().registerSnippet(new Snippet(
                SOUT_SNIPPET_ID,
                "System.out.println",
                "java",
                "System.out.println(\"\");"
        ));
        context.snippets().registerSnippet(new Snippet(
                MAIN_SNIPPET_ID,
                "Main method",
                "java",
                """
                        public static void main(String[] args) {
                            System.out.println("");
                        }
                        """
        ));
        context.commands().registerCommand(SOUT_ID, "Insert println", () ->
                insertSnippet(SOUT_SNIPPET_ID)
        );
        context.commands().registerCommand(MAIN_ID, "Insert main method", () ->
                insertSnippet(MAIN_SNIPPET_ID)
        );
        context.commands().addMenuItem("Java", SOUT_ID, SOUT_ID);
        context.commands().addMenuItem("Java", MAIN_ID, MAIN_ID);
        fileSubscription = context.events().subscribe(FileOpenedEvent.class, event -> {
            if (event.path().getFileName().toString().endsWith(".java")) {
                context.notifications().updateStatusItem(STATUS_ID, "Java file");
            }
        });
    }

    @Override
    public void onUnload() {
        if (fileSubscription != null) {
            fileSubscription.close();
        }
        if (context != null) {
            context.commands().removeMenuItem(SOUT_ID);
            context.commands().removeMenuItem(MAIN_ID);
            context.commands().unregisterCommand(SOUT_ID);
            context.commands().unregisterCommand(MAIN_ID);
            context.snippets().unregisterSnippet(SOUT_SNIPPET_ID);
            context.snippets().unregisterSnippet(MAIN_SNIPPET_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private void insertSnippet(String snippetId) {
        context.snippets().snippets("java").stream()
                .filter(snippet -> snippet.id().equals(snippetId))
                .findFirst()
                .ifPresent(snippet -> context.editor().insertText(snippet.body()));
    }
}
