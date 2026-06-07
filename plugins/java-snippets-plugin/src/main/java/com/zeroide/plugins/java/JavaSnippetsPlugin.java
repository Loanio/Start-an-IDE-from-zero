package com.zeroide.plugins.java;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.FileOpenedEvent;

public final class JavaSnippetsPlugin implements Plugin {
    private static final String STATUS_ID = "plugin.java.status";
    private static final String SOUT_ID = "plugin.java.sout";
    private static final String MAIN_ID = "plugin.java.main";

    private EditorContext context;
    private Subscription fileSubscription;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.ui().addStatusItem(STATUS_ID, "Java snippets");
        context.ui().addMenuAction("Java", SOUT_ID, "Insert println", () ->
                context.editor().insertText("System.out.println(\"\");")
        );
        context.ui().addMenuAction("Java", MAIN_ID, "Insert main method", () ->
                context.editor().insertText("""
                        public static void main(String[] args) {
                            System.out.println("");
                        }
                        """)
        );
        fileSubscription = context.events().subscribe(FileOpenedEvent.class, event -> {
            if (event.path().getFileName().toString().endsWith(".java")) {
                context.ui().updateStatusItem(STATUS_ID, "Java file");
            }
        });
    }

    @Override
    public void onUnload() {
        if (fileSubscription != null) {
            fileSubscription.close();
        }
        if (context != null) {
            context.ui().removeMenuAction(SOUT_ID);
            context.ui().removeMenuAction(MAIN_ID);
            context.ui().removeStatusItem(STATUS_ID);
        }
    }
}
