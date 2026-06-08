package com.zeroide.api;

import javafx.scene.Node;

public interface PanelService {
    void addPanel(String id, String title, Node content, PanelLocation location);

    void removePanel(String id);

    void selectPanel(String id);

    default void addSidebarPanel(String id, String title, Node content) {
        addPanel(id, title, content, PanelLocation.SIDEBAR);
    }

    default void addToolPanel(String id, String title, Node content) {
        addPanel(id, title, content, PanelLocation.RIGHT);
    }

    default void addBottomPanel(String id, String title, Node content) {
        addPanel(id, title, content, PanelLocation.BOTTOM);
    }
}
