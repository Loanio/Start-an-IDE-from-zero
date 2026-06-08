package com.zeroide.plugins.filetree;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.FileOpenedEvent;
import com.zeroide.api.events.WorkspaceChangedEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

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
    private Label rootLabel;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.ui().addStatusItem(STATUS_ID, "File tree");
        context.ui().addMenuAction("Explorer", REFRESH_ID, "Refresh File Tree", this::refresh);
        context.ui().addToolPanel(PANEL_ID, "Files", buildPanel());
        fileSubscription = context.events().subscribe(FileOpenedEvent.class, event ->
                context.ui().updateStatusItem(STATUS_ID, "Opened " + event.path().getFileName())
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
            context.ui().removeMenuAction(REFRESH_ID);
            context.ui().removeToolPanel(PANEL_ID);
            context.ui().removeStatusItem(STATUS_ID);
        }
    }

    private VBox buildPanel() {
        Label title = new Label("Files");
        title.getStyleClass().add("plugin-panel-title");
        rootLabel = new Label();
        rootLabel.getStyleClass().add("plugin-panel-meta");
        rootLabel.setWrapText(true);

        HBox header = new HBox(title, spacer());
        header.getStyleClass().add("plugin-panel-header");

        Button choose = new Button("Open Folder");
        choose.getStyleClass().add("plugin-primary-button");
        choose.setOnAction(ignored -> chooseRoot());

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("plugin-quiet-button");
        refresh.setOnAction(ignored -> refresh());

        Button current = new Button("Current File");
        current.getStyleClass().add("plugin-quiet-button");
        current.setOnAction(ignored -> setRootPath(initialRoot()));

        HBox actions = new HBox(6, choose, refresh, current);
        actions.getStyleClass().add("plugin-toolbar");
        treeView = new TreeView<>();
        treeView.setShowRoot(false);
        treeView.setCellFactory(ignored -> new PathCell());
        treeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedFile();
            }
        });

        VBox panel = new VBox(8, header, rootLabel, actions, treeView);
        panel.getStyleClass().add("plugin-panel");
        VBox.setVgrow(treeView, Priority.ALWAYS);
        return panel;
    }

    private void chooseRoot() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Workspace Folder");
        Path currentRoot = treeView.getRoot() == null ? null : treeView.getRoot().getValue();
        if (currentRoot != null && Files.isDirectory(currentRoot)) {
            chooser.setInitialDirectory(currentRoot.toFile());
        }
        var selected = chooser.showDialog(null);
        if (selected != null) {
            context.workspace().openWorkspace(selected.toPath());
        }
    }

    private void refresh() {
        TreeItem<Path> root = treeView.getRoot();
        if (root != null) {
            setRootPath(root.getValue());
        }
    }

    private void setRootPath(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        rootLabel.setText(normalized.toString());
        treeView.setRoot(new LazyPathItem(normalized));
        context.ui().updateStatusItem(STATUS_ID, "Files " + normalized.getFileName());
    }

    private void openSelectedFile() {
        TreeItem<Path> item = treeView.getSelectionModel().getSelectedItem();
        if (item == null || Files.isDirectory(item.getValue())) {
            return;
        }
        context.editor().openFile(item.getValue());
        context.ui().updateStatusItem(STATUS_ID, "Opened " + item.getValue().getFileName());
    }

    private Path initialRoot() {
        return context.workspace().getWorkspace()
                .or(() -> context.editor().getCurrentFile()
                .map(path -> Files.isDirectory(path) ? path : path.getParent())
                .filter(path -> path != null && Files.isDirectory(path)))
                .orElse(Path.of(System.getProperty("user.dir")));
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
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
            if (empty || path == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Path fileName = path.getFileName();
            Label badge = new Label(Files.isDirectory(path) ? "DIR" : "FILE");
            badge.getStyleClass().add(Files.isDirectory(path) ? "file-kind-folder" : "file-kind-file");

            Label name = new Label(fileName == null ? path.toString() : fileName.toString());
            name.getStyleClass().add("file-name");

            HBox row = new HBox(8, badge, name);
            row.getStyleClass().add("file-row");
            setText(null);
            setGraphic(row);
        }
    }
}
