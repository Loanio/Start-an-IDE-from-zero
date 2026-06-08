package com.zeroide.plugins.filetree;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.FileOpenedEvent;
import com.zeroide.api.events.WorkspaceChangedEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    private Label rootLabel;
    private Label statusLabel;
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
        Label title = new Label("Files");
        title.getStyleClass().add("plugin-panel-title");
        statusLabel = new Label("Workspace");
        statusLabel.getStyleClass().addAll("plugin-panel-meta", "plugin-status-chip");
        HBox header = new HBox(title, spacer(), statusLabel);
        header.getStyleClass().add("plugin-panel-header");

        rootLabel = new Label("No workspace");
        rootLabel.getStyleClass().addAll("plugin-panel-meta", "plugin-path-label");
        rootLabel.setWrapText(true);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("plugin-quiet-button");
        refresh.setOnAction(ignored -> refresh());
        HBox actions = new HBox(6, refresh);
        actions.getStyleClass().add("plugin-toolbar");

        treeView = new TreeView<>();
        treeView.getStyleClass().add("file-tree-view");
        treeView.setShowRoot(false);
        treeView.setCellFactory(ignored -> new PathCell());

        VBox panel = new VBox(8, header, rootLabel, actions, treeView);
        panel.getStyleClass().addAll("plugin-panel", "file-tree-panel");
        installTreeDoubleClickHandler(panel);
        VBox.setVgrow(treeView, Priority.ALWAYS);
        return panel;
    }

    private void refresh() {
        TreeItem<Path> root = treeView.getRoot();
        if (root != null) {
            setRootPath(root.getValue());
            statusLabel.setText("Refreshed");
        }
    }

    private void setRootPath(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        treeView.setRoot(new LazyPathItem(normalized));
        if (rootLabel != null) {
            rootLabel.setText(normalized.toString());
        }
        if (statusLabel != null) {
            statusLabel.setText("Workspace");
        }
        context.notifications().updateStatusItem(STATUS_ID, "Files " + normalized.getFileName());
    }

    private void openTreeItem(TreeItem<Path> item) {
        if (item == null) {
            return;
        }
        if (Files.isDirectory(item.getValue())) {
            item.setExpanded(!item.isExpanded());
            return;
        }
        context.editor().openFile(item.getValue());
        statusLabel.setText("Opened");
        context.notifications().updateStatusItem(STATUS_ID, "Opened " + item.getValue().getFileName());
    }

    private void installTreeDoubleClickHandler(VBox panel) {
        panel.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            PathCell cell = findPathCell(event.getTarget());
            if (cell != null && isPrimaryDoubleClick(event) && !cell.isEmpty()) {
                event.consume();
                openTreeItem(cell.getTreeItem());
            }
        });
        panel.addEventFilter(MouseEvent.MOUSE_RELEASED, this::consumeTreeDoubleClick);
        panel.addEventFilter(MouseEvent.MOUSE_CLICKED, this::consumeTreeDoubleClick);
    }

    private void consumeTreeDoubleClick(MouseEvent event) {
        PathCell cell = findPathCell(event.getTarget());
        if (cell != null && isPrimaryDoubleClick(event) && !cell.isEmpty()) {
            event.consume();
        }
    }

    private static boolean isPrimaryDoubleClick(MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2;
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static PathCell findPathCell(Object target) {
        if (!(target instanceof Node node)) {
            return null;
        }
        while (node != null) {
            if (node instanceof PathCell cell) {
                return cell;
            }
            node = node.getParent();
        }
        return null;
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
        private final Region icon = new Region();
        private final Label name = new Label();
        private final HBox row = new HBox(7, icon, name);

        private PathCell() {
            Region emptyDisclosure = new Region();
            emptyDisclosure.setMinSize(0, 0);
            emptyDisclosure.setPrefSize(0, 0);
            emptyDisclosure.setMaxSize(0, 0);
            setDisclosureNode(emptyDisclosure);

            row.getStyleClass().add("file-row");
            icon.getStyleClass().add("file-icon");
            name.getStyleClass().add("file-name");
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(Path path, boolean empty) {
            super.updateItem(path, empty);
            getStyleClass().removeAll("file-folder", "file-leaf");
            icon.getStyleClass().removeAll("folder-icon-simple", "file-icon-simple");
            if (empty || path == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Path fileName = path.getFileName();
            boolean directory = Files.isDirectory(path);
            getStyleClass().add(directory ? "file-folder" : "file-leaf");
            icon.getStyleClass().add(directory ? "folder-icon-simple" : "file-icon-simple");
            name.setText(fileName == null ? path.toString() : fileName.toString());
            setText(null);
            setGraphic(row);
        }
    }
}
