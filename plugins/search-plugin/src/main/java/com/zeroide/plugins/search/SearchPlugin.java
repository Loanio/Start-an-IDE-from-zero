package com.zeroide.plugins.search;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.TextChangedEvent;
import com.zeroide.api.events.WorkspaceChangedEvent;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class SearchPlugin implements Plugin {
    private static final String PANEL_ID = "plugin.search.panel";
    private static final String STATUS_ID = "plugin.search.status";
    private static final String SEARCH_ID = "plugin.search.run";
    private static final String WORKSPACE_SEARCH_ID = "plugin.search.workspace";
    private static final int MAX_WORKSPACE_HITS = 500;

    private EditorContext context;
    private Subscription textSubscription;
    private Subscription workspaceSubscription;
    private TextField queryField;
    private TextField replacementField;
    private CheckBox caseSensitive;
    private Label countLabel;
    private Label scopeLabel;
    private ListView<SearchHit> results;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.notifications().addStatusItem(STATUS_ID, "Search ready");
        context.commands().registerCommand(SEARCH_ID, "Search Current File", this::search);
        context.commands().registerCommand(WORKSPACE_SEARCH_ID, "Search Workspace", this::searchWorkspace);
        context.commands().addMenuItem("Search", SEARCH_ID, SEARCH_ID);
        context.commands().addMenuItem("Search", WORKSPACE_SEARCH_ID, WORKSPACE_SEARCH_ID);
        context.panels().addToolPanel(PANEL_ID, "Search", buildPanel());
        textSubscription = context.events().subscribe(TextChangedEvent.class, ignored -> search());
        workspaceSubscription = context.events().subscribe(WorkspaceChangedEvent.class, ignored -> updateScopeLabel());
    }

    @Override
    public void onUnload() {
        if (textSubscription != null) {
            textSubscription.close();
        }
        if (workspaceSubscription != null) {
            workspaceSubscription.close();
        }
        if (context != null) {
            context.commands().removeMenuItem(SEARCH_ID);
            context.commands().removeMenuItem(WORKSPACE_SEARCH_ID);
            context.commands().unregisterCommand(SEARCH_ID);
            context.commands().unregisterCommand(WORKSPACE_SEARCH_ID);
            context.panels().removePanel(PANEL_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private VBox buildPanel() {
        Label title = new Label("Search");
        title.getStyleClass().add("plugin-panel-title");
        countLabel = new Label("0 matches");
        countLabel.getStyleClass().add("plugin-panel-meta");
        countLabel.getStyleClass().add("plugin-status-chip");
        HBox header = new HBox(title, spacer(), countLabel);
        header.getStyleClass().add("plugin-panel-header");
        scopeLabel = new Label();
        scopeLabel.getStyleClass().add("plugin-panel-meta");
        scopeLabel.getStyleClass().add("plugin-path-label");
        scopeLabel.setWrapText(true);
        updateScopeLabel();

        queryField = new TextField();
        queryField.setPromptText("Find");
        queryField.getStyleClass().add("plugin-search-field");
        queryField.textProperty().addListener((ignored, oldValue, newValue) -> search());

        replacementField = new TextField();
        replacementField.setPromptText("Replace");
        replacementField.getStyleClass().add("plugin-search-field");

        caseSensitive = new CheckBox("Aa");
        caseSensitive.getStyleClass().add("plugin-toggle");
        caseSensitive.selectedProperty().addListener((ignored, oldValue, newValue) -> search());

        Button find = new Button("Find");
        find.getStyleClass().add("plugin-primary-button");
        find.setOnAction(ignored -> search());

        Button replaceAll = new Button("Replace All");
        replaceAll.getStyleClass().add("plugin-quiet-button");
        replaceAll.setOnAction(ignored -> replaceAll());

        Button workspace = new Button("Workspace");
        workspace.getStyleClass().add("plugin-quiet-button");
        workspace.setOnAction(ignored -> searchWorkspace());

        Button clear = new Button("Clear");
        clear.getStyleClass().add("plugin-quiet-button");
        clear.setOnAction(ignored -> clear());

        HBox actions = new HBox(6, caseSensitive, find, workspace, replaceAll, clear);
        actions.getStyleClass().add("plugin-toolbar");
        results = new ListView<>();
        results.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(SearchHit hit, boolean empty) {
                super.updateItem(hit, empty);
                if (empty || hit == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label line = new Label(String.valueOf(hit.line()));
                line.getStyleClass().add("search-line-badge");
                Label snippet = new Label(hit.label());
                snippet.getStyleClass().add("search-snippet");
                snippet.setWrapText(true);
                snippet.setMaxWidth(Double.MAX_VALUE);
                HBox row = new HBox(8, line, snippet);
                row.getStyleClass().add("search-result-row");
                HBox.setHgrow(snippet, Priority.ALWAYS);
                setText(null);
                setGraphic(row);
            }
        });
        results.getStyleClass().add("search-results-list");
        results.setOnMouseClicked(ignored -> selectCurrentHit());

        VBox panel = new VBox(8, header, scopeLabel, queryField, replacementField, actions, results);
        panel.getStyleClass().add("plugin-panel");
        VBox.setVgrow(results, Priority.ALWAYS);
        return panel;
    }

    private void search() {
        if (queryField == null || results == null) {
            return;
        }
        String query = queryField.getText();
        if (query == null || query.isEmpty()) {
            results.setItems(FXCollections.observableArrayList());
            countLabel.setText("0 matches");
            context.notifications().updateStatusItem(STATUS_ID, "Search ready");
            return;
        }

        List<SearchHit> hits = findHits(null, context.editor().getText(), query, caseSensitive.isSelected());
        results.setItems(FXCollections.observableArrayList(hits));
        countLabel.setText(hits.size() + " matches");
        context.notifications().updateStatusItem(STATUS_ID, hits.size() + " matches");
    }

    private void replaceAll() {
        String query = queryField.getText();
        if (query == null || query.isEmpty()) {
            return;
        }
        String replacement = replacementField.getText() == null ? "" : replacementField.getText();
        String text = context.editor().getText();
        List<SearchHit> hits = findHits(null, text, query, caseSensitive.isSelected());
        int matches = hits.size();
        context.editor().replaceText(replace(text, hits, replacement));
        context.notifications().updateStatusItem(STATUS_ID, "Replaced " + matches);
    }

    private void clear() {
        queryField.clear();
        replacementField.clear();
        results.setItems(FXCollections.observableArrayList());
        countLabel.setText("0 matches");
        context.notifications().updateStatusItem(STATUS_ID, "Search ready");
    }

    private void selectCurrentHit() {
        SearchHit hit = results.getSelectionModel().getSelectedItem();
        if (hit != null) {
            if (hit.file() != null) {
                context.editor().openFile(hit.file());
            }
            context.editor().selectRange(hit.start(), hit.end());
        }
    }

    private void searchWorkspace() {
        if (queryField == null || results == null) {
            return;
        }
        String query = queryField.getText();
        if (query == null || query.isEmpty()) {
            clear();
            return;
        }

        Path workspace = context.workspace().getWorkspace().orElse(null);
        if (workspace == null) {
            context.notifications().updateStatusItem(STATUS_ID, "No workspace");
            return;
        }

        List<SearchHit> hits = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(workspace)) {
            stream
                    .filter(SearchPlugin::isSearchableFile)
                    .takeWhile(ignored -> hits.size() < MAX_WORKSPACE_HITS)
                    .forEach(path -> addWorkspaceHits(hits, path, query));
        } catch (IOException ex) {
            context.notifications().updateStatusItem(STATUS_ID, "Workspace search failed");
            return;
        }

        results.setItems(FXCollections.observableArrayList(hits));
        countLabel.setText(hits.size() + " matches");
        context.notifications().updateStatusItem(STATUS_ID, hits.size() + " workspace matches");
    }

    private void addWorkspaceHits(List<SearchHit> hits, Path path, String query) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            for (SearchHit hit : findHits(path, text, query, caseSensitive.isSelected())) {
                if (hits.size() == MAX_WORKSPACE_HITS) {
                    return;
                }
                hits.add(hit);
            }
        } catch (IOException ignored) {
            // Files that cannot be decoded as UTF-8 are skipped.
        }
    }

    private void updateScopeLabel() {
        if (scopeLabel == null) {
            return;
        }
        String scope = context.workspace().getWorkspace()
                .map(Path::toString)
                .orElse("No workspace");
        scopeLabel.setText(scope);
    }

    private static boolean isSearchableFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        Path fileName = path.getFileName();
        if (fileName == null || fileName.toString().startsWith(".")) {
            return false;
        }
        try {
            return Files.size(path) <= 1024 * 1024;
        } catch (IOException ex) {
            return false;
        }
    }

    private static List<SearchHit> findHits(Path file, String text, String query, boolean matchCase) {
        List<SearchHit> hits = new ArrayList<>();
        String haystack = matchCase ? text : text.toLowerCase();
        String needle = matchCase ? query : query.toLowerCase();
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            hits.add(toHit(file, text, index, query.length()));
            index += Math.max(needle.length(), 1);
        }
        return hits;
    }

    private static SearchHit toHit(Path file, String text, int index, int queryLength) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int lineEnd = text.indexOf('\n', index);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        String snippet = text.substring(lineStart, lineEnd).strip();
        return new SearchHit(file, line, index, index + queryLength, snippet);
    }

    private static String replace(String text, List<SearchHit> hits, String replacement) {
        StringBuilder result = new StringBuilder(text);
        for (int i = hits.size() - 1; i >= 0; i--) {
            SearchHit hit = hits.get(i);
            result.replace(hit.start(), hit.end(), replacement);
        }
        return result.toString();
    }

    private static HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private record SearchHit(Path file, int line, int start, int end, String snippet) {
        private String label() {
            if (file == null) {
                return snippet;
            }
            Path fileName = file.getFileName();
            return (fileName == null ? file.toString() : fileName.toString()) + "  " + snippet;
        }
    }
}
