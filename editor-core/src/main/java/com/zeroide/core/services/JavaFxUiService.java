package com.zeroide.core.services;

import com.zeroide.api.UIService;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JavaFxUiService implements UIService {
    private final MenuBar menuBar;
    private final HBox statusBar;
    private final Map<String, Label> statusItems = new LinkedHashMap<>();
    private final Map<String, MenuItem> menuItems = new LinkedHashMap<>();

    public JavaFxUiService(MenuBar menuBar, HBox statusBar) {
        this.menuBar = menuBar;
        this.statusBar = statusBar;
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
        runOnFxThread(() -> {
            removeMenuAction(id);
            Menu targetMenu = findOrCreateMenu(menu);
            MenuItem menuItem = new MenuItem(text);
            menuItem.setOnAction(ignored -> action.run());
            menuItems.put(id, menuItem);
            targetMenu.getItems().add(menuItem);
        });
    }

    @Override
    public void removeMenuAction(String id) {
        runOnFxThread(() -> {
            MenuItem menuItem = menuItems.remove(id);
            if (menuItem != null) {
                for (Menu menu : menuBar.getMenus()) {
                    menu.getItems().remove(menuItem);
                }
            }
        });
    }

    @Override
    public void showInfo(String title, String message) {
        runOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
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
}
