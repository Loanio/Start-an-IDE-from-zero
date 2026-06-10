package com.zeroide.plugins.todo;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.FileOpenedEvent;
import com.zeroide.api.events.TextChangedEvent;
import com.zeroide.api.events.WorkspaceChangedEvent;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TodoExplorerPlugin implements Plugin {
    private static final String PANEL_ID = "plugin.todo-explorer.panel";
    private static final String STATUS_ID = "plugin.todo-explorer.status";
    private static final String REFRESH_COMMAND_ID = "plugin.todo-explorer.refresh";
    private static final int MAX_FILE_BYTES = 256 * 1024;

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ListView<TodoItem> listView = new ListView<>();
    private EditorContext context;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.notifications().addStatusItem(STATUS_ID, "TODO 0");
        context.commands().registerCommand(REFRESH_COMMAND_ID, "TODO: Refresh", this::refresh);
        context.commands().addMenuItem("TODO", REFRESH_COMMAND_ID, REFRESH_COMMAND_ID);
        context.panels().addSidebarPanel(PANEL_ID, "TODO", buildPanel());
        subscriptions.add(context.events().subscribe(TextChangedEvent.class, event -> refreshCurrentFile(event.text())));
        subscriptions.add(context.events().subscribe(FileOpenedEvent.class, event -> refreshCurrentFile(event.text())));
        subscriptions.add(context.events().subscribe(WorkspaceChangedEvent.class, ignored -> refresh()));
        refresh();
    }

    @Override
    public void onUnload() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        if (context != null) {
            context.panels().removePanel(PANEL_ID);
            context.commands().removeMenuItem(REFRESH_COMMAND_ID);
            context.commands().unregisterCommand(REFRESH_COMMAND_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private VBox buildPanel() {
        Label title = new Label("TODO / FIXME");
        title.getStyleClass().add("plugin-panel-title");

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("plugin-quiet-button");
        refresh.setOnAction(ignored -> refresh());

        HBox header = new HBox(8, title, spacer(), refresh);
        header.getStyleClass().add("plugin-panel-header");

        listView.getStyleClass().add("todo-list");
        listView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(TodoItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.displayText());
            }
        });
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openSelectedTodo();
            }
        });

        VBox panel = new VBox(8, header, listView);
        panel.getStyleClass().add("plugin-panel");
        VBox.setVgrow(listView, Priority.ALWAYS);
        return panel;
    }

    private void refresh() {
        context.workspace().getWorkspace()
                .ifPresentOrElse(this::refreshWorkspace, () -> refreshCurrentFile(context.editor().getText()));
    }

    private void refreshWorkspace(Path workspace) {
        List<TodoItem> items = new ArrayList<>();
        try {
            Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!workspace.equals(directory) && isIgnoredName(directory.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && isTextLike(file)) {
                        scanFile(file, items);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            context.notifications().showWarning("TODO", "Cannot scan workspace.");
        }
        items.sort(Comparator.comparing(item -> item.path().toString()));
        updateItems(items);
    }

    private void refreshCurrentFile(String text) {
        Path path = context.editor().getCurrentFile().orElse(null);
        updateItems(scanText(path, text == null ? "" : text));
    }

    private void scanFile(Path path, List<TodoItem> items) {
        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                return;
            }
            items.addAll(scanText(path, Files.readString(path, StandardCharsets.UTF_8)));
        } catch (IOException ignored) {
            // Binary or unreadable files are ignored by the explorer.
        }
    }

    private List<TodoItem> scanText(Path path, String text) {
        List<TodoItem> items = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.contains("TODO") || upper.contains("FIXME")) {
                items.add(new TodoItem(path, i + 1, line.strip()));
            }
        }
        return items;
    }

    private boolean isTextLike(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals(".git")) {
            return false;
        }
        return name.endsWith(".java")
                || name.endsWith(".md")
                || name.endsWith(".json")
                || name.endsWith(".gradle")
                || name.endsWith(".xml")
                || name.endsWith(".txt")
                || name.endsWith(".css")
                || name.endsWith(".properties");
    }

    private static boolean isIgnoredName(String name) {
        return name.equals(".git") || name.equals(".gradle") || name.equals("build") || name.equals("out");
    }

    private void updateItems(List<TodoItem> items) {
        Platform.runLater(() -> {
            listView.setItems(FXCollections.observableArrayList(items));
            context.notifications().updateStatusItem(STATUS_ID, "TODO " + items.size());
        });
    }

    private void openSelectedTodo() {
        TodoItem item = listView.getSelectionModel().getSelectedItem();
        if (item == null || item.path() == null) {
            return;
        }
        context.editor().openFile(item.path());
        selectLine(item);
    }

    private void selectLine(TodoItem item) {
        try {
            String text = Files.readString(item.path(), StandardCharsets.UTF_8);
            int offset = lineOffset(text, item.line());
            int end = Math.min(text.length(), offset + item.text().length());
            context.editor().selectRange(offset, end);
        } catch (IOException ignored) {
            context.notifications().showWarning("TODO", "Cannot open TODO item.");
        }
    }

    private static int lineOffset(String text, int line) {
        int offset = 0;
        for (int currentLine = 1; currentLine < line && offset < text.length(); currentLine++) {
            int next = text.indexOf('\n', offset);
            if (next < 0) {
                return text.length();
            }
            offset = next + 1;
        }
        return offset;
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private record TodoItem(Path path, int line, String text) {
        private String displayText() {
            String file = path == null || path.getFileName() == null ? "Untitled" : path.getFileName().toString();
            return file + ":" + line + "  " + text;
        }
    }
}
