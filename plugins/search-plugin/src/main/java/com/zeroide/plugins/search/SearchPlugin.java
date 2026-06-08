package com.zeroide.plugins.search;

import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;
import com.zeroide.api.Subscription;
import com.zeroide.api.events.TextChangedEvent;
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

import java.util.ArrayList;
import java.util.List;

public final class SearchPlugin implements Plugin {
    private static final String PANEL_ID = "plugin.search.panel";
    private static final String STATUS_ID = "plugin.search.status";
    private static final String SEARCH_ID = "plugin.search.run";

    private EditorContext context;
    private Subscription textSubscription;
    private TextField queryField;
    private TextField replacementField;
    private CheckBox caseSensitive;
    private Label countLabel;
    private ListView<SearchHit> results;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.ui().addStatusItem(STATUS_ID, "Search ready");
        context.ui().addMenuAction("Search", SEARCH_ID, "Search Current File", this::search);
        context.ui().addToolPanel(PANEL_ID, "Search", buildPanel());
        textSubscription = context.events().subscribe(TextChangedEvent.class, ignored -> search());
    }

    @Override
    public void onUnload() {
        if (textSubscription != null) {
            textSubscription.close();
        }
        if (context != null) {
            context.ui().removeMenuAction(SEARCH_ID);
            context.ui().removeToolPanel(PANEL_ID);
            context.ui().removeStatusItem(STATUS_ID);
        }
    }

    private VBox buildPanel() {
        Label title = new Label("Search");
        title.getStyleClass().add("plugin-panel-title");
        countLabel = new Label("0 matches");
        countLabel.getStyleClass().add("plugin-panel-meta");
        HBox header = new HBox(title, spacer(), countLabel);
        header.getStyleClass().add("plugin-panel-header");

        queryField = new TextField();
        queryField.setPromptText("Find");
        queryField.textProperty().addListener((ignored, oldValue, newValue) -> search());

        replacementField = new TextField();
        replacementField.setPromptText("Replace");

        caseSensitive = new CheckBox("Aa");
        caseSensitive.getStyleClass().add("plugin-toggle");
        caseSensitive.selectedProperty().addListener((ignored, oldValue, newValue) -> search());

        Button find = new Button("Find");
        find.getStyleClass().add("plugin-primary-button");
        find.setOnAction(ignored -> search());

        Button replaceAll = new Button("Replace All");
        replaceAll.getStyleClass().add("plugin-quiet-button");
        replaceAll.setOnAction(ignored -> replaceAll());

        Button clear = new Button("Clear");
        clear.getStyleClass().add("plugin-quiet-button");
        clear.setOnAction(ignored -> clear());

        HBox actions = new HBox(6, caseSensitive, find, replaceAll, clear);
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
                Label snippet = new Label(hit.snippet());
                snippet.getStyleClass().add("search-snippet");
                HBox row = new HBox(8, line, snippet);
                row.getStyleClass().add("search-result-row");
                setText(null);
                setGraphic(row);
            }
        });
        results.setOnMouseClicked(ignored -> selectCurrentHit());

        VBox panel = new VBox(8, header, queryField, replacementField, actions, results);
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
            context.ui().updateStatusItem(STATUS_ID, "Search ready");
            return;
        }

        List<SearchHit> hits = findHits(context.editor().getText(), query, caseSensitive.isSelected());
        results.setItems(FXCollections.observableArrayList(hits));
        countLabel.setText(hits.size() + " matches");
        context.ui().updateStatusItem(STATUS_ID, hits.size() + " matches");
    }

    private void replaceAll() {
        String query = queryField.getText();
        if (query == null || query.isEmpty()) {
            return;
        }
        String replacement = replacementField.getText() == null ? "" : replacementField.getText();
        String text = context.editor().getText();
        List<SearchHit> hits = findHits(text, query, caseSensitive.isSelected());
        int matches = hits.size();
        context.editor().replaceText(replace(text, hits, replacement));
        context.ui().updateStatusItem(STATUS_ID, "Replaced " + matches);
    }

    private void clear() {
        queryField.clear();
        replacementField.clear();
        results.setItems(FXCollections.observableArrayList());
        countLabel.setText("0 matches");
        context.ui().updateStatusItem(STATUS_ID, "Search ready");
    }

    private void selectCurrentHit() {
        SearchHit hit = results.getSelectionModel().getSelectedItem();
        if (hit != null) {
            context.editor().selectRange(hit.start(), hit.end());
        }
    }

    private static List<SearchHit> findHits(String text, String query, boolean matchCase) {
        List<SearchHit> hits = new ArrayList<>();
        String haystack = matchCase ? text : text.toLowerCase();
        String needle = matchCase ? query : query.toLowerCase();
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            hits.add(toHit(text, index, query.length()));
            index += Math.max(needle.length(), 1);
        }
        return hits;
    }

    private static SearchHit toHit(String text, int index, int queryLength) {
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
        return new SearchHit(line, index, index + queryLength, snippet);
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

    private record SearchHit(int line, int start, int end, String snippet) {
    }
}
