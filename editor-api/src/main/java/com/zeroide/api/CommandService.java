package com.zeroide.api;

public interface CommandService {
    void registerCommand(String id, String title, Runnable action);

    void unregisterCommand(String id);

    void executeCommand(String id);

    void addMenuItem(String menu, String id, String commandId);

    void removeMenuItem(String id);
}
