package com.zeroide.core.services;

import com.zeroide.api.CommandDescriptor;
import com.zeroide.api.NotificationLevel;
import com.zeroide.api.PanelLocation;
import com.zeroide.api.UIService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JavaFxUiService implements UIService {
    private final MenuBar menuBar;
    private final HBox statusBar;
    private final TabPane sidebarPanels;
    private final TabPane toolPanels;
    private final TabPane bottomPanels;
    private final Map<String, Label> statusItems = new LinkedHashMap<>();
    private final Map<String, MenuItem> menuItems = new LinkedHashMap<>();
    private final Map<String, RegisteredCommand> commands = new LinkedHashMap<>();
    private final Map<String, Tab> sidebarPanelItems = new LinkedHashMap<>();
    private final Map<String, Tab> panelItems = new LinkedHashMap<>();
    private final Map<String, Tab> bottomPanelItems = new LinkedHashMap<>();
    private Scene scene;

    public JavaFxUiService(MenuBar menuBar, HBox statusBar, TabPane sidebarPanels, TabPane toolPanels, TabPane bottomPanels) {
        this.menuBar = menuBar;
        this.statusBar = statusBar;
        this.sidebarPanels = sidebarPanels;
        this.toolPanels = toolPanels;
        this.bottomPanels = bottomPanels;
    }

    @Override
    public void addStatusItem(String id, String text) {
        runOnFxThread(() -> {
            if (statusItems.containsKey(id)) {
                updateStatusItem(id, text);
                return;
            }
            Label label = new Label(text);
            label.getStyleClass().add("status-item");
            statusItems.put(id, label);
            statusBar.getChildren().add(label);
        });
    }

    @Override
    public void updateStatusItem(String id, String text) {
        runOnFxThread(() -> {
            Label label = statusItems.get(id);
            if (label != null) {
                label.setText(text);
            }
        });
    }

    @Override
    public void removeStatusItem(String id) {
        runOnFxThread(() -> {
            Label label = statusItems.remove(id);
            if (label != null) {
                statusBar.getChildren().remove(label);
            }
        });
    }

    @Override
    public void addMenuAction(String menu, String id, String text, Runnable action) {
        registerCommand(id, text, action);
        addMenuItem(menu, id, id);
    }

    @Override
    public void removeMenuAction(String id) {
        removeMenuItem(id);
        unregisterCommand(id);
    }

    @Override
    public void registerCommand(String id, String title, Runnable action) {
        registerCommand(id, title, null, action);
    }

    @Override
    public void registerCommand(String id, String title, String keyBinding, Runnable action) {
        if (id == null || id.isBlank() || action == null) {
            return;
        }
        removeAccelerator(id);
        commands.put(id, new RegisteredCommand(id, title == null ? id : title, keyBinding, action));
        installAccelerator(id);
    }

    @Override
    public void unregisterCommand(String id) {
        removeAccelerator(id);
        commands.remove(id);
    }

    @Override
    public void executeCommand(String id) {
        RegisteredCommand command = commands.get(id);
        if (command != null) {
            command.action().run();
        }
    }

    @Override
    public List<CommandDescriptor> commands() {
        return commands.values().stream()
                .map(command -> new CommandDescriptor(command.id(), command.title(), command.keyBinding()))
                .sorted(Comparator.comparing(CommandDescriptor::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public void addMenuItem(String menu, String id, String commandId) {
        runOnFxThread(() -> {
            removeMenuItem(id);
            RegisteredCommand command = commands.get(commandId);
            if (command == null) {
                return;
            }
            Menu targetMenu = findOrCreateMenu(menu);
            MenuItem menuItem = new MenuItem(command.title());
            menuItem.setOnAction(ignored -> executeCommand(commandId));
            menuItems.put(id, menuItem);
            targetMenu.getItems().add(menuItem);
        });
    }

    @Override
    public void removeMenuItem(String id) {
        runOnFxThread(() -> {
            MenuItem menuItem = menuItems.remove(id);
            if (menuItem != null) {
                for (Menu menu : menuBar.getMenus()) {
                    menu.getItems().remove(menuItem);
                }
            }
        });
    }

    public void attachScene(Scene scene) {
        this.scene = scene;
        commands.keySet().forEach(this::installAccelerator);
    }

    @Override
    public void addPanel(String id, String title, javafx.scene.Node content, PanelLocation location) {
        if (location == PanelLocation.SIDEBAR) {
            addPanel(id, title, content, sidebarPanels, sidebarPanelItems);
        } else if (location == PanelLocation.BOTTOM) {
            addPanel(id, title, content, bottomPanels, bottomPanelItems);
        } else {
            addPanel(id, title, content, toolPanels, panelItems);
        }
    }

    @Override
    public void removePanel(String id) {
        runOnFxThread(() -> {
            removePanel(id, sidebarPanels, sidebarPanelItems);
            removePanel(id, toolPanels, panelItems);
            removePanel(id, bottomPanels, bottomPanelItems);
        });
    }

    @Override
    public void selectPanel(String id) {
        runOnFxThread(() -> {
            selectPanel(id, sidebarPanels, sidebarPanelItems);
            selectPanel(id, toolPanels, panelItems);
            selectPanel(id, bottomPanels, bottomPanelItems);
        });
    }

    @Override
    public void addToolPanel(String id, String title, javafx.scene.Node content) {
        addPanel(id, title, content, PanelLocation.RIGHT);
    }

    @Override
    public void removeToolPanel(String id) {
        runOnFxThread(() -> removePanel(id, toolPanels, panelItems));
    }

    @Override
    public void addBottomPanel(String id, String title, javafx.scene.Node content) {
        addPanel(id, title, content, PanelLocation.BOTTOM);
    }

    @Override
    public void removeBottomPanel(String id) {
        runOnFxThread(() -> removePanel(id, bottomPanels, bottomPanelItems));
    }

    @Override
    public void notify(NotificationLevel level, String title, String message) {
        runOnFxThread(() -> {
            Alert alert = new Alert(alertType(level));
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void addPanel(String id, String title, javafx.scene.Node content, TabPane target, Map<String, Tab> items) {
        runOnFxThread(() -> {
            removePanel(id);
            boolean selectNewPanel = target.getTabs().isEmpty();
            if (content instanceof Region region) {
                region.setMinWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
            }
            Tab tab = new Tab(title, content);
            tab.setClosable(false);
            items.put(id, tab);
            target.getTabs().add(tab);
            if (selectNewPanel) {
                target.getSelectionModel().select(tab);
            }
        });
    }

    private static void removePanel(String id, TabPane target, Map<String, Tab> items) {
        Tab tab = items.remove(id);
        if (tab != null) {
            target.getTabs().remove(tab);
        }
    }

    private static void selectPanel(String id, TabPane target, Map<String, Tab> items) {
        Tab tab = items.get(id);
        if (tab != null) {
            target.getSelectionModel().select(tab);
        }
    }

    private static Alert.AlertType alertType(NotificationLevel level) {
        if (level == NotificationLevel.ERROR) {
            return Alert.AlertType.ERROR;
        }
        if (level == NotificationLevel.WARNING) {
            return Alert.AlertType.WARNING;
        }
        return Alert.AlertType.INFORMATION;
    }

    private Menu findOrCreateMenu(String title) {
        return menuBar.getMenus().stream()
                .filter(menu -> menu.getText().equals(title))
                .findFirst()
                .orElseGet(() -> {
                    Menu menu = new Menu(title);
                    menuBar.getMenus().add(menu);
                    return menu;
                });
    }

    private static void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    private void installAccelerator(String commandId) {
        if (scene == null) {
            return;
        }
        RegisteredCommand command = commands.get(commandId);
        if (command == null || command.keyBinding() == null || command.keyBinding().isBlank()) {
            return;
        }
        runOnFxThread(() -> scene.getAccelerators().put(
                KeyCombination.keyCombination(command.keyBinding()),
                () -> executeCommand(commandId)
        ));
    }

    private void removeAccelerator(String commandId) {
        if (scene == null) {
            return;
        }
        RegisteredCommand command = commands.get(commandId);
        if (command == null || command.keyBinding() == null || command.keyBinding().isBlank()) {
            return;
        }
        runOnFxThread(() -> scene.getAccelerators().remove(KeyCombination.keyCombination(command.keyBinding())));
    }

    private record RegisteredCommand(String id, String title, String keyBinding, Runnable action) {
    }
}
