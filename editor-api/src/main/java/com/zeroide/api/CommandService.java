package com.zeroide.api;

import java.util.List;

public interface CommandService {
    void registerCommand(String id, String title, Runnable action);

    void registerCommand(String id, String title, String keyBinding, Runnable action);

    void unregisterCommand(String id);

    void executeCommand(String id);

    List<CommandDescriptor> commands();

    void addMenuItem(String menu, String id, String commandId);

    void removeMenuItem(String id);
}
