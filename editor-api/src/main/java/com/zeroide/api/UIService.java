package com.zeroide.api;

import javafx.scene.Node;

public interface UIService extends PanelService, CommandService, NotificationService {
    void addMenuAction(String menu, String id, String text, Runnable action);

    void removeMenuAction(String id);

    @Override
    void addToolPanel(String id, String title, Node content);

    void removeToolPanel(String id);

    @Override
    void addBottomPanel(String id, String title, Node content);

    void removeBottomPanel(String id);
}
