package com.zeroide.plugins.filetree;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.FileOpenedEvent;
import com.zeroide.api.events.WorkspaceChangedEvent;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class FileTreePlugin implements Plugin {
    private static final String PANEL_ID = "plugin.file-tree.panel";
    private static final String STATUS_ID = "plugin.file-tree.status";
    private static final String REFRESH_ID = "plugin.file-tree.refresh";

    private EditorContext context;
    private Subscription fileSubscription;
    private Subscription workspaceSubscription;
    private TreeView<Path> treeView;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.notifications().addStatusItem(STATUS_ID, "File tree");
        context.commands().registerCommand(REFRESH_ID, "Refresh File Tree", this::refresh);
        context.commands().addMenuItem("Explorer", REFRESH_ID, REFRESH_ID);
        context.panels().addSidebarPanel(PANEL_ID, "Files", buildPanel());
        fileSubscription = context.events().subscribe(FileOpenedEvent.class, event ->
                context.notifications().updateStatusItem(STATUS_ID, "Opened " + event.path().getFileName())
        );
        workspaceSubscription = context.events().subscribe(WorkspaceChangedEvent.class, event -> setRootPath(event.workspace()));
        setRootPath(initialRoot());
    }

    @Override
    public void onUnload() {
        if (fileSubscription != null) {
            fileSubscription.close();
        }
        if (workspaceSubscription != null) {
            workspaceSubscription.close();
        }
        if (context != null) {
            context.commands().removeMenuItem(REFRESH_ID);
            context.commands().unregisterCommand(REFRESH_ID);
            context.panels().removePanel(PANEL_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private VBox buildPanel() {
        treeView = new TreeView<>();
        treeView.setShowRoot(false);
        treeView.setCellFactory(ignored -> new PathCell());
        treeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedFile();
            }
        });

        VBox panel = new VBox(treeView);
        panel.getStyleClass().addAll("plugin-panel", "file-tree-panel");
        VBox.setVgrow(treeView, Priority.ALWAYS);
        return panel;
    }

    private void refresh() {
        TreeItem<Path> root = treeView.getRoot();
        if (root != null) {
            setRootPath(root.getValue());
        }
    }

    private void setRootPath(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        treeView.setRoot(new LazyPathItem(normalized));
        context.notifications().updateStatusItem(STATUS_ID, "Files " + normalized.getFileName());
    }

    private void openSelectedFile() {
        TreeItem<Path> item = treeView.getSelectionModel().getSelectedItem();
        if (item == null || Files.isDirectory(item.getValue())) {
            return;
        }
        context.editor().openFile(item.getValue());
        context.notifications().updateStatusItem(STATUS_ID, "Opened " + item.getValue().getFileName());
    }

    private Path initialRoot() {
        return context.workspace().getWorkspace()
                .or(() -> context.editor().getCurrentFile()
                .map(path -> Files.isDirectory(path) ? path : path.getParent())
                .filter(path -> path != null && Files.isDirectory(path)))
                .orElse(Path.of(System.getProperty("user.dir")));
    }

    private static final class LazyPathItem extends TreeItem<Path> {
        private boolean loaded;

        private LazyPathItem(Path path) {
            super(path);
        }

        @Override
        public boolean isLeaf() {
            return !Files.isDirectory(getValue());
        }

        @Override
        public javafx.collections.ObservableList<TreeItem<Path>> getChildren() {
            if (!loaded && Files.isDirectory(getValue())) {
                loaded = true;
                try (Stream<Path> stream = Files.list(getValue())) {
                    stream
                            .filter(path -> !path.getFileName().toString().startsWith("."))
                            .sorted(Comparator
                                    .comparing((Path path) -> !Files.isDirectory(path))
                                    .thenComparing(path -> path.getFileName().toString().toLowerCase()))
                            .map(LazyPathItem::new)
                            .forEach(super.getChildren()::add);
                } catch (IOException ignored) {
                    // Unreadable folders are shown as empty nodes.
                }
            }
            return super.getChildren();
        }
    }

    private static final class PathCell extends TreeCell<Path> {
        @Override
        protected void updateItem(Path path, boolean empty) {
            super.updateItem(path, empty);
            getStyleClass().removeAll("file-folder", "file-leaf");
            if (empty || path == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Path fileName = path.getFileName();
            getStyleClass().add(Files.isDirectory(path) ? "file-folder" : "file-leaf");
            setText(fileName == null ? path.toString() : fileName.toString());
            setGraphic(null);
        }
    }
}
