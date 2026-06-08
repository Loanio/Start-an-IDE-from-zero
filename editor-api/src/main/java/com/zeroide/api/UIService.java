package com.zeroide.api;

import javafx.scene.Node;

public interface UIService {
    void addStatusItem(String id, String text);

    void updateStatusItem(String id, String text);

    void removeStatusItem(String id);

    void addMenuAction(String menu, String id, String text, Runnable action);

    void removeMenuAction(String id);

    void addToolPanel(String id, String title, Node content);

    void removeToolPanel(String id);

    void showInfo(String title, String message);
}
