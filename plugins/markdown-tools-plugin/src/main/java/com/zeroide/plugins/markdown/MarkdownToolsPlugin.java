package com.zeroide.plugins.markdown;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.FileOpenedEvent;

public final class MarkdownToolsPlugin implements Plugin {
    private static final String STATUS_ID = "plugin.markdown.status";
    private static final String INSERT_HEADING_ID = "plugin.markdown.insert-heading";
    private static final String PREVIEW_ID = "plugin.markdown.preview";

    private EditorContext context;
    private Subscription fileSubscription;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.ui().addStatusItem(STATUS_ID, "Markdown ready");
        context.ui().addMenuAction("Markdown", INSERT_HEADING_ID, "Insert Heading", () ->
                context.editor().insertText("# Heading\n")
        );
        context.ui().addMenuAction("Markdown", PREVIEW_ID, "Show Outline", this::showOutline);
        fileSubscription = context.events().subscribe(FileOpenedEvent.class, event -> {
            if (event.path().getFileName().toString().endsWith(".md")) {
                context.ui().updateStatusItem(STATUS_ID, "Markdown file");
            }
        });
    }

    @Override
    public void onUnload() {
        if (fileSubscription != null) {
            fileSubscription.close();
        }
        if (context != null) {
            context.ui().removeMenuAction(INSERT_HEADING_ID);
            context.ui().removeMenuAction(PREVIEW_ID);
            context.ui().removeStatusItem(STATUS_ID);
        }
    }

    private void showOutline() {
        String outline = context.editor().getText().lines()
                .filter(line -> line.startsWith("#"))
                .map(String::strip)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("No markdown headings found.");
        context.ui().showInfo("Markdown Outline", outline);
    }
}
