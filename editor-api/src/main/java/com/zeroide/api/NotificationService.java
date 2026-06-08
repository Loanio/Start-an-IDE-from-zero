package com.zeroide.api;

public interface NotificationService {
    void addStatusItem(String id, String text);

    void updateStatusItem(String id, String text);

    void removeStatusItem(String id);

    void notify(NotificationLevel level, String title, String message);

    default void showInfo(String title, String message) {
        notify(NotificationLevel.INFO, title, message);
    }

    default void showWarning(String title, String message) {
        notify(NotificationLevel.WARNING, title, message);
    }

    default void showError(String title, String message) {
        notify(NotificationLevel.ERROR, title, message);
    }
}
