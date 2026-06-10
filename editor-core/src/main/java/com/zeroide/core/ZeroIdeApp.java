package com.zeroide.core;

import com.zeroide.api.EditorService;
import com.zeroide.api.Subscription;
import com.zeroide.api.WorkspaceService;
import com.zeroide.api.CommandDescriptor;
import com.zeroide.api.CommandService;
import com.zeroide.api.events.TextChangedEvent;
import com.zeroide.api.events.PluginLoadedEvent;
import com.zeroide.api.events.PluginUnloadedEvent;
import com.zeroide.api.events.WorkspaceChangedEvent;
import com.zeroide.core.editor.RichCodeEditor;
import com.zeroide.core.plugins.DynamicPluginManager;
import com.zeroide.core.plugins.LoadedPlugin;
import com.zeroide.core.services.CoreContainer;
import com.zeroide.core.services.JavaFxEditorService;
import com.zeroide.core.services.JavaFxUiService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ZeroIdeApp extends Application {
    private RichCodeEditor editor;
    private Label fileLabel;
    private Label workspaceLabel;
    private Label projectNameLabel;
    private Label metricsLabel;
    private TabPane sidebarPanelTabs;
    private TabPane toolPanelTabs;
    private TabPane bottomPanelTabs;
    private BorderPane workspaceShell;
    private SplitPane workspaceBodySplitPane;
    private SplitPane workspaceSplitPane;
    private VBox sidebar;
    private BorderPane toolPanelHost;
    private BorderPane bottomPanelHost;
    private CoreContainer container;
    private DynamicPluginManager pluginManager;
    private JavaFxEditorService editorService;
    private JavaFxUiService uiService;
    private WorkspaceService workspaceService;
    private CommandService commandService;
    private Subscription workspaceSubscription;
    private Subscription pluginLoadedSubscription;
    private Subscription pluginUnloadedSubscription;
    private Menu recentWorkspacesMenu;
    private ListView<LoadedPlugin> pluginManagerList;
    private StackPane appRoot;
    private VBox commandPalette;
    private TextField commandPaletteInput;
    private ListView<CommandDescriptor> commandPaletteList;
    private boolean sidebarVisible = true;
    private boolean toolPanelVisible = true;
    private boolean bottomPanelVisible = true;
    private double sidebarDividerPosition = 0.20;
    private double toolPanelDividerPosition = 0.78;
    private double bottomDividerPosition = 0.68;
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
        sidebarPanelTabs = buildSidebarPanelTabs();
        toolPanelTabs = buildToolPanelTabs();
        bottomPanelTabs = buildBottomPanelTabs();

        Path pluginDirectory = resolvePluginDirectory();
        container = CoreContainer.create(editor, menuBar, statusBar, sidebarPanelTabs, toolPanelTabs, bottomPanelTabs, stage, pluginDirectory);
        pluginManager = container.getBean(DynamicPluginManager.class);
        editorService = (JavaFxEditorService) container.getBean(EditorService.class);
        uiService = container.getBean(JavaFxUiService.class);
        workspaceService = container.getBean(WorkspaceService.class);
        commandService = uiService;

        configureCoreCommands(stage);
        configureMenus(menuBar, stage);
        configureEditorEvents();
        configureWorkspaceEvents();
        configurePluginEvents();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        VBox windowChrome = new VBox(buildTitleBar(stage), menuBar);
        windowChrome.getStyleClass().add("window-chrome");
        root.setTop(windowChrome);
        root.setCenter(buildWorkspace());
        root.setBottom(statusBar);

        appRoot = new StackPane(root, buildCommandPalette());
        commandPalette.setVisible(false);
        commandPalette.setManaged(false);

        Scene scene = new Scene(appRoot, 1180, 760);
        scene.setFill(Color.web("#1e1e1e"));
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stage.setTitle("Zero IDE");
        configureWindowIcon(stage);
        stage.setScene(scene);
        uiService.attachScene(scene);
        stage.show();

        pluginManager.loadAll();
        updateMetrics();
        updateWorkspaceLabel();
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
        if (workspaceSubscription != null) {
            workspaceSubscription.close();
        }
        if (pluginLoadedSubscription != null) {
            pluginLoadedSubscription.close();
        }
        if (pluginUnloadedSubscription != null) {
            pluginUnloadedSubscription.close();
        }
        if (container != null) {
            container.close();
        }
    }

    private RichCodeEditor buildEditor() {
        RichCodeEditor codeEditor = new RichCodeEditor();
        codeEditor.setInitialText("""
                public class HelloZeroIde {
                    public static void main(String[] args) {
                        System.out.println("Hello, plugin architecture.");
                    }
                }
                """);
        return codeEditor;
    }

    private BorderPane buildWorkspace() {
        workspaceShell = new BorderPane();
        workspaceShell.getStyleClass().add("workspace-shell");

        sidebar = buildSidebar();
        toolPanelHost = buildToolPanelHost();
        bottomPanelHost = buildBottomPanelHost();
        workspaceSplitPane = new SplitPane();
        workspaceSplitPane.getStyleClass().add("workspace-split");
        workspaceBodySplitPane = new SplitPane();
        workspaceBodySplitPane.setOrientation(Orientation.VERTICAL);
        workspaceBodySplitPane.getStyleClass().add("workspace-body-split");

        configureResizableWorkspace();
        updateWorkspacePanels();
        return workspaceShell;
    }

    private void configureResizableWorkspace() {
        editor.setMinWidth(120);
        sidebar.setMinWidth(96);
        sidebar.setPrefWidth(260);
        sidebar.setMaxWidth(Double.MAX_VALUE);
        toolPanelHost.setMinWidth(128);
        toolPanelHost.setPrefWidth(320);
        toolPanelHost.setMaxWidth(Double.MAX_VALUE);
        bottomPanelHost.setMinHeight(110);
        bottomPanelHost.setPrefHeight(230);
        bottomPanelHost.setMaxHeight(Double.MAX_VALUE);

        SplitPane.setResizableWithParent(editor, true);
        SplitPane.setResizableWithParent(sidebar, true);
        SplitPane.setResizableWithParent(toolPanelHost, true);
        SplitPane.setResizableWithParent(bottomPanelHost, true);
    }

    private BorderPane buildToolPanelHost() {
        BorderPane host = new BorderPane();
        host.getStyleClass().add("tool-panel-host");
        host.setCenter(toolPanelTabs);
        return host;
    }

    private TabPane buildToolPanelTabs() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("tool-tabs");
        tabs.setMinWidth(0);
        tabs.setPrefWidth(320);
        tabs.setMaxWidth(Double.MAX_VALUE);
        return tabs;
    }

    private TabPane buildSidebarPanelTabs() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("sidebar-tabs");
        tabs.setMinWidth(0);
        tabs.setMinHeight(220);
        return tabs;
    }

    private BorderPane buildBottomPanelHost() {
        BorderPane host = new BorderPane();
        host.getStyleClass().add("bottom-panel-host");
        host.setCenter(bottomPanelTabs);
        return host;
    }

    private TabPane buildBottomPanelTabs() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("bottom-tabs");
        tabs.setMinHeight(160);
        tabs.setPrefHeight(230);
        tabs.getTabs().addAll(
                fixedTab("Problems", defaultPanelText("No problems detected.")),
                fixedTab("Output", defaultPanelText("No output yet."))
        );
        return tabs;
    }

    private Tab fixedTab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private TextArea defaultPanelText(String text) {
        TextArea output = new TextArea(text);
        output.setEditable(false);
        output.setWrapText(true);
        output.getStyleClass().add("plugin-output");
        return output;
    }

    private VBox buildCommandPalette() {
        Label title = new Label("Command Palette");
        title.getStyleClass().add("command-palette-title");

        commandPaletteInput = new TextField();
        commandPaletteInput.getStyleClass().add("command-palette-input");
        commandPaletteInput.setPromptText("Type a command");
        commandPaletteInput.textProperty().addListener((ignored, oldValue, newValue) -> refreshCommandPalette());
        commandPaletteInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideCommandPalette();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                executeSelectedCommand();
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                moveCommandSelection(1);
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                moveCommandSelection(-1);
                event.consume();
            }
        });

        commandPaletteList = new ListView<>();
        commandPaletteList.getStyleClass().add("command-palette-list");
        commandPaletteList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(CommandDescriptor command, boolean empty) {
                super.updateItem(command, empty);
                if (empty || command == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label name = new Label(command.title());
                name.getStyleClass().add("command-palette-command-title");
                Label meta = new Label(command.keyBinding().map(binding -> command.id() + "  " + binding).orElse(command.id()));
                meta.getStyleClass().add("command-palette-command-meta");
                VBox row = new VBox(2, name, meta);
                row.getStyleClass().add("command-palette-command");
                setText(null);
                setGraphic(row);
            }
        });
        commandPaletteList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                executeSelectedCommand();
            }
        });

        VBox palette = new VBox(8, title, commandPaletteInput, commandPaletteList);
        palette.getStyleClass().add("command-palette");
        palette.setMaxWidth(620);
        palette.setMaxHeight(420);
        StackPane.setAlignment(palette, Pos.TOP_CENTER);
        StackPane.setMargin(palette, new Insets(74, 0, 0, 0));
        commandPalette = palette;
        return palette;
    }

    private void showCommandPalette() {
        refreshCommandPalette();
        commandPalette.setManaged(true);
        commandPalette.setVisible(true);
        commandPalette.toFront();
        commandPaletteInput.requestFocus();
        commandPaletteInput.selectAll();
    }

    private void hideCommandPalette() {
        commandPalette.setVisible(false);
        commandPalette.setManaged(false);
        editor.requestFocus();
    }

    private void refreshCommandPalette() {
        if (commandPaletteList == null) {
            return;
        }

        String query = commandPaletteInput.getText() == null ? "" : commandPaletteInput.getText().toLowerCase(Locale.ROOT).strip();
        List<CommandDescriptor> commands = commandService.commands().stream()
                .filter(command -> commandMatches(command, query))
                .toList();
        commandPaletteList.setItems(FXCollections.observableArrayList(commands));
        if (!commands.isEmpty()) {
            commandPaletteList.getSelectionModel().select(0);
        }
    }

    private static boolean commandMatches(CommandDescriptor command, String query) {
        if (query.isBlank()) {
            return true;
        }
        return command.title().toLowerCase(Locale.ROOT).contains(query)
                || command.id().toLowerCase(Locale.ROOT).contains(query)
                || command.keyBinding().map(binding -> binding.toLowerCase(Locale.ROOT).contains(query)).orElse(false);
    }

    private void executeSelectedCommand() {
        CommandDescriptor selected = commandPaletteList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        hideCommandPalette();
        commandService.executeCommand(selected.id());
    }

    private void moveCommandSelection(int delta) {
        int size = commandPaletteList.getItems().size();
        if (size == 0) {
            return;
        }
        int current = commandPaletteList.getSelectionModel().getSelectedIndex();
        int next = Math.max(0, Math.min(size - 1, current + delta));
        commandPaletteList.getSelectionModel().select(next);
        commandPaletteList.scrollTo(next);
    }

    private VBox buildSidebar() {
        Label title = new Label("EXPLORER");
        title.getStyleClass().add("sidebar-title");

        Button collapse = sideToggleButton("<", "Hide left sidebar");
        collapse.getStyleClass().add("side-collapse-button");
        collapse.setOnAction(ignored -> toggleSidebar());
        HBox explorerHeader = new HBox(title, spacer(), collapse);
        explorerHeader.getStyleClass().add("side-panel-header");

        projectNameLabel = new Label("No workspace");
        projectNameLabel.getStyleClass().add("project-name");

        VBox sidebar = new VBox(6, explorerHeader, projectNameLabel, sidebarPanelTabs);
        sidebar.getStyleClass().add("sidebar");
        VBox.setVgrow(sidebarPanelTabs, Priority.ALWAYS);
        return sidebar;
    }

    private HBox buildStatusBar() {
        fileLabel = new Label("Untitled");
        fileLabel.getStyleClass().add("status-item");
        workspaceLabel = new Label("No workspace");
        workspaceLabel.getStyleClass().add("status-item");
        metricsLabel = new Label("");
        metricsLabel.getStyleClass().add("status-item");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusBar = new HBox(14, workspaceLabel, fileLabel, metricsLabel, spacer);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(0, 12, 0, 12));
        return statusBar;
    }

    private void configureCoreCommands(Stage stage) {
        commandService.registerCommand("core.command-palette.show", "Show Command Palette", "Shortcut+Shift+P", this::showCommandPalette);
        commandService.registerCommand("core.file.new", "File: New", "Shortcut+N", () -> {
            editorService.newFile();
            fileLabel.setText("Untitled");
        });
        commandService.registerCommand("core.file.open", "File: Open File", "Shortcut+O", () -> openFile(stage));
        commandService.registerCommand("core.file.open-folder", "File: Open Folder", "Shortcut+K", () -> openWorkspace(stage));
        commandService.registerCommand("core.file.save", "File: Save", "Shortcut+S", () -> {
            editorService.saveCurrentFile();
            updateFileLabel();
        });
        commandService.registerCommand("core.file.save-as", "File: Save As", "Shortcut+Shift+S", () -> saveAs(stage));
        commandService.registerCommand("core.view.toggle-sidebar", "View: Toggle Sidebar", "Shortcut+B", this::toggleSidebar);
        commandService.registerCommand("core.view.toggle-tools", "View: Toggle Tools", "Shortcut+Shift+B", this::toggleToolPanel);
        commandService.registerCommand("core.view.toggle-bottom-panel", "View: Toggle Bottom Panel", "Shortcut+J", this::toggleBottomPanel);
        commandService.registerCommand("core.plugins.load-jar", "Plugins: Load Jar", () -> chooseAndLoadPlugin());
        commandService.registerCommand("core.plugins.load-all", "Plugins: Load All", () -> {
            pluginManager.loadAll();
        });
        commandService.registerCommand("core.plugins.manage", "Plugins: Manage Plugins", this::showPluginManager);
        commandService.registerCommand("core.plugins.unload-all", "Plugins: Unload All", () -> pluginManager.unloadAll());
    }

    private void configureMenus(MenuBar menuBar, Stage stage) {
        Menu fileMenu = new Menu("File");
        MenuItem newFile = commandItem("core.file.new");
        MenuItem open = commandItem("core.file.open");
        MenuItem openFolder = commandItem("core.file.open-folder");
        recentWorkspacesMenu = new Menu("Recent Workspaces");
        refreshRecentWorkspacesMenu();
        MenuItem save = commandItem("core.file.save");
        MenuItem saveAs = commandItem("core.file.save-as");
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(ignored -> Platform.exit());
        fileMenu.getItems().addAll(newFile, open, openFolder, recentWorkspacesMenu, new SeparatorMenuItem(), save, saveAs, new SeparatorMenuItem(), exit);

        Menu pluginMenu = new Menu("Plugins");
        MenuItem loadJar = commandItem("core.plugins.load-jar");
        MenuItem loadAll = commandItem("core.plugins.load-all");
        MenuItem manage = commandItem("core.plugins.manage");
        MenuItem unloadAll = commandItem("core.plugins.unload-all");
        pluginMenu.getItems().addAll(loadJar, loadAll, manage, new SeparatorMenuItem(), unloadAll);

        Menu viewMenu = new Menu("View");
        MenuItem commandPalette = commandItem("core.command-palette.show");
        MenuItem toggleSidebar = commandItem("core.view.toggle-sidebar");
        MenuItem toggleTools = commandItem("core.view.toggle-tools");
        MenuItem togglePanel = commandItem("core.view.toggle-bottom-panel");
        viewMenu.getItems().add(commandPalette);
        viewMenu.getItems().add(new SeparatorMenuItem());
        viewMenu.getItems().addAll(toggleSidebar, toggleTools, togglePanel);

        menuBar.getMenus().addAll(fileMenu, viewMenu, pluginMenu);
    }

    private MenuItem commandItem(String commandId) {
        CommandDescriptor command = commandService.commands().stream()
                .filter(candidate -> candidate.id().equals(commandId))
                .findFirst()
                .orElse(new CommandDescriptor(commandId, commandId, (String) null));
        MenuItem item = new MenuItem(command.title());
        command.keyBinding().ifPresent(binding -> item.setAccelerator(KeyCombination.keyCombination(binding)));
        item.setOnAction(ignored -> commandService.executeCommand(commandId));
        return item;
    }

    private void configureEditorEvents() {
        editor.textProperty().addListener((ignored, oldText, newText) -> {
            container.getBean(com.zeroide.api.EventBus.class).publish(new TextChangedEvent(newText));
            updateMetrics();
        });
    }

    private void configureWorkspaceEvents() {
        workspaceSubscription = container.getBean(com.zeroide.api.EventBus.class).subscribe(WorkspaceChangedEvent.class, ignored -> {
            updateWorkspaceLabel();
            refreshRecentWorkspacesMenu();
        });
    }

    private void configurePluginEvents() {
        pluginLoadedSubscription = container.getBean(com.zeroide.api.EventBus.class).subscribe(PluginLoadedEvent.class, ignored -> {
            editorService.refreshLanguage();
            refreshPluginManager();
        });
        pluginUnloadedSubscription = container.getBean(com.zeroide.api.EventBus.class).subscribe(PluginUnloadedEvent.class, ignored -> {
            editorService.refreshLanguage();
            refreshPluginManager();
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

    private void openWorkspace(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Workspace Folder");
        workspaceService.getWorkspace().ifPresent(path -> chooser.setInitialDirectory(path.toFile()));
        var directory = chooser.showDialog(stage);
        if (directory != null) {
            workspaceService.openWorkspace(directory.toPath());
        }
    }

    private void chooseAndLoadPlugin() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Plugin Jar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Plugin jars", "*.jar"));
        var file = chooser.showOpenDialog(editor.getScene().getWindow());
        if (file != null) {
            pluginManager.load(file.toPath());
        }
    }

    private void showPluginManager() {
        if (pluginManagerList == null) {
            pluginManagerList = buildPluginManagerList();
            VBox panel = buildPluginManagerPanel();
            uiService.addToolPanel("core.plugins.manager", "Plugins", panel);
        }
        refreshPluginManager();
        uiService.selectPanel("core.plugins.manager");
    }

    private VBox buildPluginManagerPanel() {
        Label title = new Label("Loaded Plugins");
        title.getStyleClass().add("plugin-panel-title");
        Label meta = new Label("Unload individual plugins without restarting the IDE.");
        meta.getStyleClass().add("plugin-panel-meta");

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("plugin-quiet-button");
        refresh.setOnAction(ignored -> refreshPluginManager());

        HBox header = new HBox(8, new VBox(2, title, meta), spacer(), refresh);
        header.getStyleClass().add("plugin-panel-header");

        VBox panel = new VBox(8, header, pluginManagerList);
        panel.getStyleClass().add("plugin-panel");
        VBox.setVgrow(pluginManagerList, Priority.ALWAYS);
        return panel;
    }

    private ListView<LoadedPlugin> buildPluginManagerList() {
        ListView<LoadedPlugin> list = new ListView<>();
        list.getStyleClass().add("plugin-manager-list");
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(LoadedPlugin plugin, boolean empty) {
                super.updateItem(plugin, empty);
                if (empty || plugin == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(pluginRow(plugin));
            }
        });
        return list;
    }

    private HBox pluginRow(LoadedPlugin plugin) {
        var descriptor = plugin.descriptor();
        Label name = new Label(descriptor.name() + "  " + descriptor.version());
        name.getStyleClass().add("plugin-panel-title");
        Label id = new Label(descriptor.id());
        id.getStyleClass().add("plugin-panel-meta");
        Label path = new Label(plugin.jarPath().getFileName().toString());
        path.getStyleClass().add("plugin-path-label");

        Button unload = new Button("Unload");
        unload.getStyleClass().add("plugin-quiet-button");
        unload.setOnAction(ignored -> {
            pluginManager.unload(descriptor.id());
            refreshPluginManager();
        });

        VBox labels = new VBox(4, name, id, path);
        HBox row = new HBox(8, labels, spacer(), unload);
        row.getStyleClass().add("plugin-manager-row");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(labels, Priority.ALWAYS);
        return row;
    }

    private void refreshPluginManager() {
        if (pluginManagerList == null || pluginManager == null) {
            return;
        }
        Runnable refresh = () -> pluginManagerList.setItems(FXCollections.observableArrayList(pluginManager.loadedPlugins()));
        if (Platform.isFxApplicationThread()) {
            refresh.run();
        } else {
            Platform.runLater(refresh);
        }
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

    private void updateWorkspaceLabel() {
        String name = workspaceService.getWorkspace()
                .map(path -> {
                    Path fileName = path.getFileName();
                    return fileName == null ? path.toString() : fileName.toString();
                })
                .orElse("No workspace");
        workspaceLabel.setText("Workspace: " + name);
        if (projectNameLabel != null) {
            projectNameLabel.setText(name);
        }
    }

    private void refreshRecentWorkspacesMenu() {
        if (recentWorkspacesMenu == null || workspaceService == null) {
            return;
        }

        recentWorkspacesMenu.getItems().clear();
        List<Path> recent = workspaceService.getRecentWorkspaces();
        if (recent.isEmpty()) {
            MenuItem empty = new MenuItem("No recent workspaces");
            empty.setDisable(true);
            recentWorkspacesMenu.getItems().add(empty);
            return;
        }

        for (Path path : recent) {
            MenuItem item = new MenuItem(path.toString());
            item.setOnAction(ignored -> workspaceService.openWorkspace(path));
            recentWorkspacesMenu.getItems().add(item);
        }
    }

    private static Path resolvePluginDirectory() {
        String configured = System.getProperty("zeroide.pluginDir");
        if (configured == null || configured.isBlank()) {
            return Path.of("plugins").toAbsolutePath().normalize();
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private void toggleSidebar() {
        rememberDividerPositions();
        sidebarVisible = !sidebarVisible;
        updateWorkspacePanels();
    }

    private void toggleToolPanel() {
        rememberDividerPositions();
        toolPanelVisible = !toolPanelVisible;
        updateWorkspacePanels();
    }

    private void toggleBottomPanel() {
        rememberDividerPositions();
        bottomPanelVisible = !bottomPanelVisible;
        updateWorkspacePanels();
    }

    private void updateWorkspacePanels() {
        if (workspaceSplitPane == null) {
            return;
        }

        workspaceSplitPane.getItems().setAll(workspaceItems());
        workspaceShell.setLeft(sidebarVisible ? null : collapsedRail("FILES", ">", "Show left sidebar", this::toggleSidebar, "left-rail"));
        workspaceShell.setRight(toolPanelVisible ? null : collapsedRail("TOOLS", "<", "Show right tools", this::toggleToolPanel, "right-rail"));
        workspaceShell.setBottom(bottomPanelVisible ? null : collapsedBottomRail());

        if (sidebarVisible && toolPanelVisible) {
            workspaceSplitPane.setDividerPositions(sidebarDividerPosition, toolPanelDividerPosition);
        } else if (sidebarVisible) {
            workspaceSplitPane.setDividerPositions(sidebarDividerPosition);
        } else if (toolPanelVisible) {
            workspaceSplitPane.setDividerPositions(toolPanelDividerPosition);
        }

        if (workspaceBodySplitPane != null) {
            if (bottomPanelVisible) {
                workspaceBodySplitPane.getItems().setAll(workspaceSplitPane, bottomPanelHost);
                workspaceBodySplitPane.setDividerPositions(bottomDividerPosition);
            } else {
                workspaceBodySplitPane.getItems().setAll(workspaceSplitPane);
            }
            workspaceShell.setCenter(workspaceBodySplitPane);
        }
    }

    private void rememberDividerPositions() {
        if (workspaceSplitPane == null) {
            return;
        }

        double[] positions = workspaceSplitPane.getDividerPositions();
        if (positions.length == 2) {
            sidebarDividerPosition = clampDivider(positions[0]);
            toolPanelDividerPosition = clampDivider(positions[1]);
        } else if (positions.length == 1 && sidebarVisible) {
            sidebarDividerPosition = clampDivider(positions[0]);
        } else if (positions.length == 1 && toolPanelVisible) {
            toolPanelDividerPosition = clampDivider(positions[0]);
        }

        if (workspaceBodySplitPane != null && bottomPanelVisible) {
            double[] bodyPositions = workspaceBodySplitPane.getDividerPositions();
            if (bodyPositions.length == 1) {
                bottomDividerPosition = clampBottomDivider(bodyPositions[0]);
            }
        }
    }

    private List<Node> workspaceItems() {
        if (sidebarVisible && toolPanelVisible) {
            return List.of(sidebar, editor, toolPanelHost);
        }
        if (sidebarVisible) {
            return List.of(sidebar, editor);
        }
        if (toolPanelVisible) {
            return List.of(editor, toolPanelHost);
        }
        return List.of(editor);
    }

    private VBox collapsedRail(String text, String buttonText, String tooltip, Runnable action, String sideClass) {
        Region accent = new Region();
        accent.getStyleClass().add("collapsed-rail-accent");

        Button button = sideToggleButton(buttonText, tooltip);
        button.getStyleClass().add("collapsed-rail-button");
        button.setOnAction(ignored -> action.run());

        Label label = new Label(text);
        label.getStyleClass().add("collapsed-rail-label");

        VBox rail = new VBox(8, accent, button, label);
        rail.getStyleClass().addAll("collapsed-rail", sideClass);
        rail.setAlignment(Pos.TOP_CENTER);
        return rail;
    }

    private HBox collapsedBottomRail() {
        Region accent = new Region();
        accent.getStyleClass().add("collapsed-bottom-rail-accent");

        Label label = new Label("PANEL");
        label.getStyleClass().add("collapsed-bottom-rail-label");

        Button button = sideToggleButton("^", "Show bottom panel");
        button.getStyleClass().add("collapsed-bottom-rail-button");
        button.setOnAction(ignored -> toggleBottomPanel());

        HBox rail = new HBox(8, accent, label, spacer(), button);
        rail.getStyleClass().add("collapsed-bottom-rail");
        rail.setAlignment(Pos.CENTER_LEFT);
        return rail;
    }

    private Button sideToggleButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("side-toggle-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static double clampDivider(double position) {
        return Math.max(0.04, Math.min(0.96, position));
    }

    private static double clampBottomDivider(double position) {
        return Math.max(0.30, Math.min(0.92, position));
    }
}
