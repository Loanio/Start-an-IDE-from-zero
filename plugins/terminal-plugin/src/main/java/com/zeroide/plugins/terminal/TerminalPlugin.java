package com.zeroide.plugins.terminal;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.WorkspaceChangedEvent;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TerminalPlugin implements Plugin {
    private static final String PANEL_ID = "plugin.terminal.panel";
    private static final String STATUS_ID = "plugin.terminal.status";
    private static final String CLEAR_ID = "plugin.terminal.clear";

    private EditorContext context;
    private Subscription workspaceSubscription;
    private TextField commandField;
    private Label cwdLabel;
    private TextArea output;
    private final List<String> history = new ArrayList<>();
    private int historyIndex;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.ui().addStatusItem(STATUS_ID, "Terminal ready");
        context.ui().addMenuAction("Terminal", CLEAR_ID, "Clear Terminal", this::clear);
        context.ui().addToolPanel(PANEL_ID, "Terminal", buildPanel());
        workspaceSubscription = context.events().subscribe(WorkspaceChangedEvent.class, ignored -> updateCwdLabel());
    }

    @Override
    public void onUnload() {
        if (workspaceSubscription != null) {
            workspaceSubscription.close();
        }
        if (context != null) {
            context.ui().removeMenuAction(CLEAR_ID);
            context.ui().removeToolPanel(PANEL_ID);
            context.ui().removeStatusItem(STATUS_ID);
        }
    }

    private VBox buildPanel() {
        Label title = new Label("Terminal");
        title.getStyleClass().add("plugin-panel-title");
        cwdLabel = new Label();
        cwdLabel.getStyleClass().add("plugin-panel-meta");
        cwdLabel.setWrapText(true);
        HBox header = new HBox(title, spacer());
        header.getStyleClass().add("plugin-panel-header");

        commandField = new TextField();
        commandField.setPromptText("Command");
        commandField.setOnAction(ignored -> runCommand());
        commandField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                showHistory(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                showHistory(1);
                event.consume();
            }
        });

        Button run = new Button("Run");
        run.getStyleClass().add("plugin-primary-button");
        run.setOnAction(ignored -> runCommand());

        Button clear = new Button("Clear");
        clear.getStyleClass().add("plugin-quiet-button");
        clear.setOnAction(ignored -> clear());

        HBox commandRow = new HBox(6, commandField, run, clear);
        commandRow.getStyleClass().add("plugin-toolbar");
        HBox.setHgrow(commandField, Priority.ALWAYS);

        output = new TextArea();
        output.setEditable(false);
        output.setWrapText(false);
        output.getStyleClass().add("plugin-output");

        cwdLabel.setText(workingDirectory().toString());

        VBox panel = new VBox(8, header, cwdLabel, commandRow, output);
        panel.getStyleClass().add("plugin-panel");
        VBox.setVgrow(output, Priority.ALWAYS);
        return panel;
    }

    private void runCommand() {
        String command = commandField.getText();
        if (command == null || command.isBlank()) {
            return;
        }

        Path workingDirectory = workingDirectory();
        cwdLabel.setText(workingDirectory.toString());
        remember(command);
        commandField.clear();
        append(System.lineSeparator() + workingDirectory + System.lineSeparator() + "$ " + command + System.lineSeparator());
        context.ui().updateStatusItem(STATUS_ID, "Terminal running");

        Thread worker = new Thread(() -> {
            List<String> shellCommand = shellCommand(command);
            ProcessBuilder builder = new ProcessBuilder(shellCommand);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            try {
                Process process = builder.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        append(line + System.lineSeparator());
                    }
                }
                int exitCode = process.waitFor();
                append("[exit " + exitCode + "]" + System.lineSeparator());
                context.ui().updateStatusItem(STATUS_ID, "Terminal done");
            } catch (IOException ex) {
                append("Cannot run command: " + ex.getMessage() + System.lineSeparator());
                context.ui().updateStatusItem(STATUS_ID, "Terminal error");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                append("Command interrupted." + System.lineSeparator());
                context.ui().updateStatusItem(STATUS_ID, "Terminal interrupted");
            }
        }, "zero-ide-terminal-plugin");
        worker.setDaemon(true);
        worker.start();
    }

    private Path workingDirectory() {
        return context.workspace().getWorkspace()
                .or(() -> context.editor().getCurrentFile()
                .map(path -> Files.isDirectory(path) ? path : path.getParent())
                .filter(path -> path != null && Files.isDirectory(path)))
                .orElse(Path.of(System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
    }

    private void updateCwdLabel() {
        Platform.runLater(() -> cwdLabel.setText(workingDirectory().toString()));
    }

    private static List<String> shellCommand(String command) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        if (windows) {
            return List.of("powershell.exe", "-NoProfile", "-Command", command);
        }
        return List.of("sh", "-lc", command);
    }

    private void clear() {
        output.clear();
    }

    private void append(String text) {
        Platform.runLater(() -> output.appendText(text));
    }

    private void remember(String command) {
        if (history.isEmpty() || !history.get(history.size() - 1).equals(command)) {
            history.add(command);
        }
        historyIndex = history.size();
    }

    private void showHistory(int direction) {
        if (history.isEmpty()) {
            return;
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        if (historyIndex == history.size()) {
            commandField.clear();
        } else {
            commandField.setText(history.get(historyIndex));
            commandField.positionCaret(commandField.getText().length());
        }
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
