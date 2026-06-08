package com.zeroide.plugins.git;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.WorkspaceChangedEvent;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
    private Label branchLabel;
    private Label changesLabel;
    private Label remoteLabel;
    private TextField commitMessageField;
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

        branchLabel = new Label("-");
        changesLabel = new Label("-");
        remoteLabel = new Label("-");
        HBox summary = new HBox(6,
                summaryCard("Branch", branchLabel),
                summaryCard("Changes", changesLabel),
                summaryCard("Remote", remoteLabel));
        summary.getStyleClass().add("git-summary-grid");

        Button status = button("Status", "plugin-primary-button", () -> runGit("status", "--short", "--branch"));
        Button log = button("Log", "plugin-quiet-button", () -> runGit("log", "--oneline", "-10"));
        Button diff = button("Diff", "plugin-quiet-button", () -> runGit("diff", "--stat"));
        Button branch = button("Branch", "plugin-quiet-button", () -> runGit("branch", "-vv"));
        HBox inspectActions = new HBox(6, status, log, diff, branch);
        inspectActions.getStyleClass().add("plugin-toolbar");

        Button fetch = button("Fetch", "plugin-quiet-button", () -> runGit("fetch", "--all", "--prune"));
        Button pull = button("Pull", "plugin-quiet-button", () -> runGit("pull", "--ff-only"));
        Button push = button("Push", "plugin-quiet-button", () -> runGit("push"));
        Button addAll = button("Add All", "plugin-quiet-button", () -> runGit("add", "-A"));
        HBox syncActions = new HBox(6, fetch, pull, push, addAll);
        syncActions.getStyleClass().add("plugin-toolbar");

        commitMessageField = new TextField();
        commitMessageField.setPromptText("Commit message");
        commitMessageField.getStyleClass().add("plugin-command-field");
        HBox.setHgrow(commitMessageField, Priority.ALWAYS);
        Button commit = button("Commit", "plugin-primary-button", this::commit);
        HBox commitActions = new HBox(6, commitMessageField, commit);
        commitActions.getStyleClass().add("plugin-toolbar");

        VBox panel = new VBox(8, header, repoLabel, summary, inspectActions, syncActions, commitActions);
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

    private void commit() {
        String message = commitMessageField.getText();
        if (message == null || message.isBlank()) {
            commandLabel.setText("message required");
            setOutput("Enter a commit message before committing.");
            context.notifications().updateStatusItem(STATUS_ID, "Commit message required");
            return;
        }
        runGit("commit", "-m", message.strip());
    }

    private void runGit(String... args) {
        Optional<Path> repository = findRepository();
        if (repository.isEmpty()) {
            repoLabel.setText("No repository");
            commandLabel.setText("not found");
            setSummary("-", "-", "-");
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
            refreshSummary(repo);
            setOutput("$ " + String.join(" ", command) + System.lineSeparator() + System.lineSeparator() + result);
            context.notifications().updateStatusItem(STATUS_ID, "Git done");
        }, "zero-ide-git-plugin");
        worker.setDaemon(true);
        worker.start();
    }

    private String runCommand(Path repo, List<String> command) {
        String result = runCommandRaw(repo, command);
        if (result.isBlank()) {
            return "No output." + System.lineSeparator();
        }
        return result;
    }

    private String runCommandRaw(Path repo, List<String> command) {
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

    private void refreshSummary(Path repo) {
        String branch = firstLine(runCommandRaw(repo, List.of("git", "branch", "--show-current"))).orElse("detached");
        long changes = runCommandRaw(repo, List.of("git", "status", "--porcelain")).lines()
                .filter(line -> !line.isBlank())
                .count();
        String remote = firstLine(runCommandRaw(repo, List.of("git", "remote"))).orElse("none");
        setSummary(branch.isBlank() ? "detached" : branch, changes == 1 ? "1 file" : changes + " files", remote);
    }

    private void setSummary(String branch, String changes, String remote) {
        Platform.runLater(() -> {
            branchLabel.setText(branch);
            changesLabel.setText(changes);
            remoteLabel.setText(remote);
        });
    }

    private void setOutput(String text) {
        Platform.runLater(() -> output.setText(text));
    }

    private static Optional<String> firstLine(String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank() && !line.startsWith("Exit code:"))
                .findFirst();
    }

    private static VBox summaryCard(String labelText, Label value) {
        Label label = new Label(labelText);
        label.getStyleClass().add("git-summary-label");
        value.getStyleClass().add("git-summary-value");
        VBox card = new VBox(3, label, value);
        card.getStyleClass().add("git-summary-card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private static Button button(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(ignored -> action.run());
        return button;
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
