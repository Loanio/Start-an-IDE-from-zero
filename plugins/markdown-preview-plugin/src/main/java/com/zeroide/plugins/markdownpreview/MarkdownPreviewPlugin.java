package com.zeroide.plugins.markdownpreview;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.FileOpenedEvent;
import com.zeroide.api.events.TextChangedEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class MarkdownPreviewPlugin implements Plugin {
    private static final String PANEL_ID = "plugin.markdown-preview.panel";
    private static final String STATUS_ID = "plugin.markdown-preview.status";
    private static final String REFRESH_ID = "plugin.markdown-preview.refresh";

    private EditorContext context;
    private Subscription textSubscription;
    private Subscription fileSubscription;
    private Label blockCountLabel;
    private Label fileLabel;
    private VBox previewContent;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.notifications().addStatusItem(STATUS_ID, "Markdown preview");
        context.commands().registerCommand(REFRESH_ID, "Refresh Preview", this::render);
        context.commands().addMenuItem("Markdown", REFRESH_ID, REFRESH_ID);
        context.panels().addToolPanel(PANEL_ID, "Preview", buildPanel());
        textSubscription = context.events().subscribe(TextChangedEvent.class, ignored -> render());
        fileSubscription = context.events().subscribe(FileOpenedEvent.class, ignored -> render());
        render();
    }

    @Override
    public void onUnload() {
        if (textSubscription != null) {
            textSubscription.close();
        }
        if (fileSubscription != null) {
            fileSubscription.close();
        }
        if (context != null) {
            context.commands().removeMenuItem(REFRESH_ID);
            context.commands().unregisterCommand(REFRESH_ID);
            context.panels().removePanel(PANEL_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private VBox buildPanel() {
        Label title = new Label("Preview");
        title.getStyleClass().add("plugin-panel-title");
        blockCountLabel = new Label("0 blocks");
        blockCountLabel.getStyleClass().add("plugin-panel-meta");
        blockCountLabel.getStyleClass().add("plugin-status-chip");
        HBox header = new HBox(title, spacer(), blockCountLabel);
        header.getStyleClass().add("plugin-panel-header");

        fileLabel = new Label("Unsaved document");
        fileLabel.getStyleClass().addAll("plugin-panel-meta", "plugin-path-label");
        fileLabel.setWrapText(true);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("plugin-quiet-button");
        refresh.setOnAction(ignored -> render());
        HBox actions = new HBox(6, refresh);
        actions.getStyleClass().add("plugin-toolbar");

        previewContent = new VBox(8);
        previewContent.getStyleClass().add("markdown-preview");
        previewContent.setPadding(new Insets(12));

        ScrollPane scrollPane = new ScrollPane(previewContent);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("markdown-preview-shell");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox panel = new VBox(8, header, fileLabel, actions, scrollPane);
        panel.getStyleClass().add("plugin-panel");
        return panel;
    }

    private void render() {
        if (previewContent == null) {
            return;
        }
        previewContent.getChildren().clear();
        Optional<Path> currentFile = context.editor().getCurrentFile();
        fileLabel.setText(currentFile
                .map(MarkdownPreviewPlugin::displayName)
                .orElse("Unsaved document"));

        if (currentFile.isPresent() && !isMarkdownFile(currentFile.get())) {
            Label unsupported = label("Markdown preview is only available for Markdown files.");
            unsupported.getStyleClass().add("markdown-muted");
            previewContent.getChildren().add(unsupported);
            blockCountLabel.setText("0 blocks");
            context.notifications().updateStatusItem(STATUS_ID, "Not a Markdown file");
            return;
        }

        String[] lines = context.editor().getText().split("\\R", -1);
        StringBuilder codeBlock = new StringBuilder();
        boolean inCode = false;

        for (String line : lines) {
            if (line.startsWith("```")) {
                if (inCode) {
                    previewContent.getChildren().add(code(codeBlock.toString().stripTrailing()));
                    codeBlock.setLength(0);
                    inCode = false;
                } else {
                    inCode = true;
                }
                continue;
            }

            if (inCode) {
                codeBlock.append(line).append(System.lineSeparator());
                continue;
            }

            Node node = renderLine(line);
            if (node != null) {
                previewContent.getChildren().add(node);
            }
        }

        if (inCode && !codeBlock.isEmpty()) {
            previewContent.getChildren().add(code(codeBlock.toString().stripTrailing()));
        }

        if (previewContent.getChildren().isEmpty()) {
            Label empty = label("Nothing to preview.");
            empty.getStyleClass().add("markdown-muted");
            previewContent.getChildren().add(empty);
        }

        blockCountLabel.setText(previewContent.getChildren().size() + " blocks");
        context.notifications().updateStatusItem(STATUS_ID, previewContent.getChildren().size() + " blocks");
    }

    private static boolean isMarkdownFile(Path path) {
        String fileName = displayName(path).toLowerCase(Locale.ROOT);
        return fileName.endsWith(".md")
                || fileName.endsWith(".markdown")
                || fileName.endsWith(".mdown")
                || fileName.endsWith(".mkd");
    }

    private static String displayName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static Node renderLine(String line) {
        String stripped = line.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        if (stripped.startsWith("#")) {
            int level = headingLevel(stripped);
            String text = stripped.substring(level).strip();
            Label label = label(text.isEmpty() ? stripped : text);
            int size = switch (level) {
                case 1 -> 24;
                case 2 -> 20;
                case 3 -> 17;
                default -> 14;
            };
            label.getStyleClass().add("markdown-heading");
            label.getStyleClass().add("markdown-heading-" + level);
            label.setStyle("-fx-font-size: " + size + "px;");
            return label;
        }
        if (stripped.matches("-{3,}|\\*{3,}|_{3,}")) {
            Separator separator = new Separator();
            separator.getStyleClass().add("markdown-divider");
            return separator;
        }
        if (stripped.startsWith("- ") || stripped.startsWith("* ")) {
            return listItem("-", stripped.substring(2).strip());
        }
        if (stripped.matches("\\d+\\.\\s+.*")) {
            int dot = stripped.indexOf('.');
            return listItem(stripped.substring(0, dot + 1), stripped.substring(dot + 1).strip());
        }
        if (stripped.startsWith(">")) {
            Label label = label(stripped.substring(1).strip());
            label.getStyleClass().add("markdown-quote");
            return label;
        }
        Label label = label(stripped);
        label.getStyleClass().add("markdown-body");
        return label;
    }

    private static int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return Math.min(level, 6);
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static Label code(String text) {
        Label label = label(text.isEmpty() ? " " : text);
        label.getStyleClass().add("markdown-code");
        return label;
    }

    private static HBox listItem(String markerText, String text) {
        Label marker = new Label(markerText);
        marker.getStyleClass().add("markdown-list-marker");
        Label body = label(text);
        body.getStyleClass().add("markdown-body");
        HBox row = new HBox(8, marker, body);
        row.getStyleClass().add("markdown-list-row");
        HBox.setHgrow(body, Priority.ALWAYS);
        return row;
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
