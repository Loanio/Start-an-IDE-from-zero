package com.zeroide.core;

import com.zeroide.api.EditorService;
import com.zeroide.api.events.TextChangedEvent;
import com.zeroide.core.plugins.DynamicPluginManager;
import com.zeroide.core.plugins.LoadedPlugin;
import com.zeroide.core.services.CoreContainer;
import com.zeroide.core.services.JavaFxEditorService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Path;
import java.util.List;

public final class ZeroIdeApp extends Application {
    private TextArea editor;
    private Label fileLabel;
    private Label metricsLabel;
    private ListView<LoadedPlugin> pluginList;
    private TabPane toolPanelTabs;
    private CoreContainer container;
    private DynamicPluginManager pluginManager;
    private JavaFxEditorService editorService;
    private double dragOffsetX;
    private double dragOffsetY;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);

        MenuBar menuBar = new MenuBar();
        editor = buildEditor();
        HBox statusBar = buildStatusBar();
        pluginList = buildPluginList();
        toolPanelTabs = buildToolPanelTabs();

        Path pluginDirectory = resolvePluginDirectory();
        container = CoreContainer.create(editor, menuBar, statusBar, toolPanelTabs, stage, pluginDirectory);
        pluginManager = container.getBean(DynamicPluginManager.class);
        editorService = (JavaFxEditorService) container.getBean(EditorService.class);

        configureMenus(menuBar, stage);
        configureEditorEvents();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        VBox windowChrome = new VBox(buildTitleBar(stage), menuBar);
        windowChrome.getStyleClass().add("window-chrome");
        root.setTop(windowChrome);
        root.setCenter(buildWorkspace());
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1180, 760);
        scene.setFill(Color.web("#1e1e1e"));
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stage.setTitle("Zero IDE");
        configureWindowIcon(stage);
        stage.setScene(scene);
        stage.show();

        pluginManager.loadAll();
        refreshPluginList();
        updateMetrics();
    }

    private void configureWindowIcon(Stage stage) {
        var iconUrl = getClass().getResource("/icons/zero-ide.png");
        if (iconUrl != null) {
            stage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }
    }

    private HBox buildTitleBar(Stage stage) {
        Label title = new Label("Zero IDE");
        title.getStyleClass().add("window-title");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimize = windowButton("minimize", "Minimize");
        minimize.setOnAction(ignored -> stage.setIconified(true));

        Button maximize = windowButton("maximize", "Maximize or restore");
        maximize.setOnAction(ignored -> stage.setMaximized(!stage.isMaximized()));

        Button close = windowButton("close", "Close");
        close.getStyleClass().add("close-button");
        close.setOnAction(ignored -> Platform.exit());

        HBox controls = new HBox(minimize, maximize, close);
        controls.getStyleClass().add("window-controls");

        HBox titleBar = new HBox(title, spacer, controls);
        titleBar.getStyleClass().add("title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setOnMousePressed(event -> {
            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();
        });
        titleBar.setOnMouseDragged(event -> {
            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
            }
        });
        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
        return titleBar;
    }

    private Button windowButton(String iconName, String tooltip) {
        Button button = new Button();
        button.getStyleClass().add("window-button");
        button.setGraphic(windowIcon(iconName));
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    private StackPane windowIcon(String iconName) {
        StackPane icon = new StackPane();
        icon.getStyleClass().addAll("window-icon", iconName + "-icon");

        if ("close".equals(iconName)) {
            Region firstLine = new Region();
            firstLine.getStyleClass().addAll("close-line", "close-line-first");
            Region secondLine = new Region();
            secondLine.getStyleClass().addAll("close-line", "close-line-second");
            icon.getChildren().addAll(firstLine, secondLine);
        } else {
            Region shape = new Region();
            shape.getStyleClass().add("window-icon-shape");
            icon.getChildren().add(shape);
        }

        return icon;
    }

    @Override
    public void stop() {
        if (pluginManager != null) {
            pluginManager.unloadAll();
        }
        if (container != null) {
            container.close();
        }
    }

    private TextArea buildEditor() {
        TextArea textArea = new TextArea();
        textArea.getStyleClass().add("code-editor");
        textArea.setWrapText(false);
        textArea.setText("""
                public class HelloZeroIde {
                    public static void main(String[] args) {
                        System.out.println("Hello, plugin architecture.");
                    }
                }
                """);
        return textArea;
    }

    private SplitPane buildWorkspace() {
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(buildSidebar(), editor, toolPanelTabs);
        splitPane.setDividerPositions(0.22, 0.72);
        return splitPane;
    }

    private TabPane buildToolPanelTabs() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("tool-tabs");
        tabs.setMinWidth(300);
        tabs.setPrefWidth(360);
        return tabs;
    }

    private VBox buildSidebar() {
        Label title = new Label("EXPLORER");
        title.getStyleClass().add("sidebar-title");

        Label projectName = new Label("zero-ide");
        projectName.getStyleClass().add("project-name");

        Label pluginTitle = new Label("PLUGINS");
        pluginTitle.getStyleClass().add("sidebar-title");

        Button loadJarButton = new Button("Load Jar");
        loadJarButton.setMaxWidth(Double.MAX_VALUE);
        loadJarButton.setOnAction(ignored -> chooseAndLoadPlugin());

        Button unloadButton = new Button("Unload");
        unloadButton.setMaxWidth(Double.MAX_VALUE);
        unloadButton.setOnAction(ignored -> unloadSelectedPlugin());

        Button refreshButton = new Button("Refresh");
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        refreshButton.setOnAction(ignored -> {
            pluginManager.loadAll();
            refreshPluginList();
        });

        HBox pluginActions = new HBox(8, loadJarButton, unloadButton, refreshButton);
        pluginActions.getStyleClass().add("plugin-actions");
        HBox.setHgrow(loadJarButton, Priority.ALWAYS);
        HBox.setHgrow(unloadButton, Priority.ALWAYS);
        HBox.setHgrow(refreshButton, Priority.ALWAYS);

        VBox sidebar = new VBox(10, title, projectName, new Separator(Orientation.HORIZONTAL), pluginTitle, pluginList, pluginActions);
        sidebar.getStyleClass().add("sidebar");
        VBox.setVgrow(pluginList, Priority.ALWAYS);
        return sidebar;
    }

    private ListView<LoadedPlugin> buildPluginList() {
        ListView<LoadedPlugin> listView = new ListView<>();
        listView.getStyleClass().add("plugin-list");
        listView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(LoadedPlugin plugin, boolean empty) {
                super.updateItem(plugin, empty);
                if (empty || plugin == null) {
                    setText(null);
                } else {
                    setText(plugin.descriptor().name() + "  " + plugin.descriptor().version());
                }
            }
        });
        return listView;
    }

    private HBox buildStatusBar() {
        fileLabel = new Label("Untitled");
        fileLabel.getStyleClass().add("status-item");
        metricsLabel = new Label("");
        metricsLabel.getStyleClass().add("status-item");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusBar = new HBox(14, fileLabel, metricsLabel, spacer);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(0, 12, 0, 12));
        return statusBar;
    }

    private void configureMenus(MenuBar menuBar, Stage stage) {
        Menu fileMenu = new Menu("File");
        MenuItem newFile = item("New", "Shortcut+N", ignored -> {
            editorService.newFile();
            fileLabel.setText("Untitled");
        });
        MenuItem open = item("Open...", "Shortcut+O", ignored -> openFile(stage));
        MenuItem save = item("Save", "Shortcut+S", ignored -> {
            editorService.saveCurrentFile();
            updateFileLabel();
        });
        MenuItem saveAs = item("Save As...", "Shortcut+Shift+S", ignored -> saveAs(stage));
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(ignored -> Platform.exit());
        fileMenu.getItems().addAll(newFile, open, save, saveAs, new SeparatorMenuItem(), exit);

        Menu pluginMenu = new Menu("Plugins");
        MenuItem loadAll = new MenuItem("Load All From Plugin Folder");
        loadAll.setOnAction(ignored -> {
            pluginManager.loadAll();
            refreshPluginList();
        });
        MenuItem unloadSelected = new MenuItem("Unload Selected");
        unloadSelected.setOnAction(ignored -> unloadSelectedPlugin());
        pluginMenu.getItems().addAll(loadAll, unloadSelected);

        menuBar.getMenus().addAll(fileMenu, pluginMenu);
    }

    private MenuItem item(String title, String shortcut, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem item = new MenuItem(title);
        item.setAccelerator(KeyCombination.keyCombination(shortcut));
        item.setOnAction(action);
        return item;
    }

    private void configureEditorEvents() {
        editor.textProperty().addListener((ignored, oldText, newText) -> {
            container.getBean(com.zeroide.api.EventBus.class).publish(new TextChangedEvent(newText));
            updateMetrics();
        });
    }

    private void openFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open File");
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            editorService.openFile(file.toPath());
            updateFileLabel();
        }
    }

    private void saveAs(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save File As");
        var file = chooser.showSaveDialog(stage);
        if (file != null) {
            editorService.saveAs(file.toPath());
            updateFileLabel();
        }
    }

    private void chooseAndLoadPlugin() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Plugin Jar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Plugin jars", "*.jar"));
        var file = chooser.showOpenDialog(editor.getScene().getWindow());
        if (file != null) {
            pluginManager.load(file.toPath());
            refreshPluginList();
        }
    }

    private void unloadSelectedPlugin() {
        LoadedPlugin selected = pluginList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            pluginManager.unload(selected.descriptor().id());
            refreshPluginList();
        }
    }

    private void refreshPluginList() {
        List<LoadedPlugin> loaded = pluginManager.loadedPlugins();
        pluginList.setItems(FXCollections.observableArrayList(loaded));
    }

    private void updateMetrics() {
        String text = editor.getText();
        long lines = text.isEmpty() ? 1 : text.lines().count();
        metricsLabel.setText(lines + " lines | " + text.length() + " chars");
    }

    private void updateFileLabel() {
        fileLabel.setText(editorService.getCurrentFile()
                .map(path -> path.getFileName().toString())
                .orElse("Untitled"));
    }

    private static Path resolvePluginDirectory() {
        String configured = System.getProperty("zeroide.pluginDir");
        if (configured == null || configured.isBlank()) {
            return Path.of("plugins").toAbsolutePath().normalize();
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
