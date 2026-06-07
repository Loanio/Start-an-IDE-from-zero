package com.zeroide.api;

public interface UIService {
    void addStatusItem(String id, String text);

    void updateStatusItem(String id, String text);

    void removeStatusItem(String id);

    void addMenuAction(String menu, String id, String text, Runnable action);

    void removeMenuAction(String id);

    void showInfo(String title, String message);
}
