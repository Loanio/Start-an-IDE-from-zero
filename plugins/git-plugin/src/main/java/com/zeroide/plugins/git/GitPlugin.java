package com.zeroide.plugins.git;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.WorkspaceChangedEvent;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class GitPlugin implements Plugin {
    private static final String PANEL_ID = "plugin.git.panel";
    private static final String LOG_PANEL_ID = "plugin.git.log-panel";
    private static final String STATUS_ID = "plugin.git.status";
    private static final String STATUS_ACTION_ID = "plugin.git.status-action";
    private static final String LOG_ACTION_ID = "plugin.git.log-action";

    private EditorContext context;
    private Subscription workspaceSubscription;
    private Label repoLabel;
    private Label commandLabel;
    private TextArea output;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.notifications().addStatusItem(STATUS_ID, "Git ready");
        context.commands().registerCommand(STATUS_ACTION_ID, "Status", () -> runGit("status", "--short", "--branch"));
        context.commands().registerCommand(LOG_ACTION_ID, "Recent Commits", () -> runGit("log", "--oneline", "-10"));
        context.commands().addMenuItem("Git", STATUS_ACTION_ID, STATUS_ACTION_ID);
        context.commands().addMenuItem("Git", LOG_ACTION_ID, LOG_ACTION_ID);
        context.panels().addToolPanel(PANEL_ID, "Git", buildOverviewPanel());
        context.panels().addBottomPanel(LOG_PANEL_ID, "Git Log", buildLogPanel());
        workspaceSubscription = context.events().subscribe(WorkspaceChangedEvent.class, ignored -> runGit("status", "--short", "--branch"));
        runGit("status", "--short", "--branch");
    }

    @Override
    public void onUnload() {
        if (workspaceSubscription != null) {
            workspaceSubscription.close();
        }
        if (context != null) {
            context.commands().removeMenuItem(STATUS_ACTION_ID);
            context.commands().removeMenuItem(LOG_ACTION_ID);
            context.commands().unregisterCommand(STATUS_ACTION_ID);
            context.commands().unregisterCommand(LOG_ACTION_ID);
            context.panels().removePanel(PANEL_ID);
            context.panels().removePanel(LOG_PANEL_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private VBox buildOverviewPanel() {
        Label title = new Label("Git");
        title.getStyleClass().add("plugin-panel-title");
        commandLabel = new Label("status");
        commandLabel.getStyleClass().add("plugin-panel-meta");
        commandLabel.getStyleClass().add("plugin-status-chip");
        HBox header = new HBox(title, spacer(), commandLabel);
        header.getStyleClass().add("plugin-panel-header");

        repoLabel = new Label("No repository");
        repoLabel.getStyleClass().add("plugin-panel-meta");
        repoLabel.getStyleClass().add("plugin-path-label");
        repoLabel.setWrapText(true);

        Button status = new Button("Status");
        status.getStyleClass().add("plugin-primary-button");
        status.setOnAction(ignored -> runGit("status", "--short", "--branch"));

        Button log = new Button("Log");
        log.getStyleClass().add("plugin-quiet-button");
        log.setOnAction(ignored -> runGit("log", "--oneline", "-10"));

        Button diff = new Button("Diff");
        diff.getStyleClass().add("plugin-quiet-button");
        diff.setOnAction(ignored -> runGit("diff", "--stat"));

        Button branch = new Button("Branch");
        branch.getStyleClass().add("plugin-quiet-button");
        branch.setOnAction(ignored -> runGit("branch", "--show-current"));

        HBox actions = new HBox(6, status, log, diff, branch);
        actions.getStyleClass().add("plugin-toolbar");
        VBox panel = new VBox(8, header, repoLabel, actions);
        panel.getStyleClass().add("plugin-panel");
        return panel;
    }

    private VBox buildLogPanel() {
        Label title = new Label("Git Log");
        title.getStyleClass().add("plugin-panel-title");
        Label hint = new Label("Command output");
        hint.getStyleClass().add("plugin-panel-meta");
        hint.getStyleClass().add("plugin-status-chip");
        HBox header = new HBox(title, spacer(), hint);
        header.getStyleClass().add("plugin-panel-header");

        output = new TextArea();
        output.setEditable(false);
        output.setWrapText(false);
        output.getStyleClass().add("plugin-output");

        VBox panel = new VBox(8, header, output);
        panel.getStyleClass().add("plugin-panel");
        VBox.setVgrow(output, Priority.ALWAYS);
        return panel;
    }

    private void runGit(String... args) {
        Optional<Path> repository = findRepository();
        if (repository.isEmpty()) {
            repoLabel.setText("No repository");
            commandLabel.setText("not found");
            setOutput("No Git repository found from the current file or working directory.");
            context.notifications().updateStatusItem(STATUS_ID, "No repository");
            return;
        }

        Path repo = repository.get();
        repoLabel.setText(repo.toString());
        commandLabel.setText("git " + String.join(" ", args));
        context.notifications().updateStatusItem(STATUS_ID, "Git running");
        Thread worker = new Thread(() -> {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(Arrays.asList(args));
            String result = runCommand(repo, command);
            setOutput("$ " + String.join(" ", command) + System.lineSeparator() + System.lineSeparator() + result);
            context.notifications().updateStatusItem(STATUS_ID, "Git done");
        }, "zero-ide-git-plugin");
        worker.setDaemon(true);
        worker.start();
    }

    private String runCommand(Path repo, List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(repo.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append(System.lineSeparator());
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                result.append("Exit code: ").append(exitCode).append(System.lineSeparator());
            }
            if (result.isEmpty()) {
                result.append("No output.").append(System.lineSeparator());
            }
            return result.toString();
        } catch (IOException ex) {
            return "Cannot run git: " + ex.getMessage();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "Git command interrupted.";
        }
    }

    private Optional<Path> findRepository() {
        Path start = context.workspace().getWorkspace()
                .or(() -> context.editor().getCurrentFile()
                .map(path -> Files.isDirectory(path) ? path : path.getParent())
                .filter(path -> path != null))
                .orElse(Path.of(System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();

        for (Path current = start; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) {
                return Optional.of(current);
            }
        }
        return Optional.empty();
    }

    private void setOutput(String text) {
        Platform.runLater(() -> output.setText(text));
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
