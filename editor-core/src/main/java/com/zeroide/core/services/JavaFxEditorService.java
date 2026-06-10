package com.zeroide.core.services;

import com.zeroide.api.EditorService;
import com.zeroide.api.EventBus;
import com.zeroide.api.LanguageService;
import com.zeroide.api.events.FileOpenedEvent;
import com.zeroide.api.events.FileSavedEvent;
import com.zeroide.core.editor.RichCodeEditor;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class JavaFxEditorService implements EditorService {
    private static final Logger log = LoggerFactory.getLogger(JavaFxEditorService.class);

    private final RichCodeEditor editor;
    private final Window owner;
    private final EventBus eventBus;
    private final LanguageService languageService;
    private Path currentFile;
    private String currentLanguageId;

    public JavaFxEditorService(RichCodeEditor editor, Window owner, EventBus eventBus, LanguageService languageService) {
        this.editor = editor;
        this.owner = owner;
        this.eventBus = eventBus;
        this.languageService = languageService;
    }

    @Override
    public String getText() {
        return editor.getText();
    }

    @Override
    public void replaceText(String text) {
        runOnFxThread(() -> editor.replaceText(text == null ? "" : text));
    }

    @Override
    public void insertText(String text) {
        runOnFxThread(() -> editor.insertText(editor.getCaretPosition(), text));
    }

    @Override
    public void selectRange(int start, int end) {
        runOnFxThread(() -> {
            int length = editor.getLength();
            int safeStart = Math.max(0, Math.min(start, length));
            int safeEnd = Math.max(safeStart, Math.min(end, length));
            editor.requestFocus();
            editor.selectRange(safeStart, safeEnd);
        });
    }

    @Override
    public Optional<Path> getCurrentFile() {
        return Optional.ofNullable(currentFile);
    }

    @Override
    public void openFile(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            currentFile = path;
            refreshLanguage();
            replaceText(text);
            eventBus.publish(new FileOpenedEvent(path, text));
        } catch (IOException ex) {
            log.warn("Cannot open file {}", path, ex);
        }
    }

    @Override
    public void saveCurrentFile() {
        if (currentFile == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save File");
            var selected = chooser.showSaveDialog(owner);
            if (selected == null) {
                return;
            }
            currentFile = selected.toPath();
        }

        try {
            String text = getText();
            Files.writeString(currentFile, text, StandardCharsets.UTF_8);
            eventBus.publish(new FileSavedEvent(currentFile, text));
        } catch (IOException ex) {
            log.warn("Cannot save file {}", currentFile, ex);
        }
    }

    public void newFile() {
        currentFile = null;
        refreshLanguage();
        replaceText("");
    }

    public void refreshLanguage() {
        String languageId = languageService.detectLanguage(currentFile)
                .map(language -> language.id())
                .orElse(null);
        setLanguageId(languageId);
    }

    public Optional<String> getLanguageId() {
        return Optional.ofNullable(currentLanguageId);
    }

    public void setLanguageId(String languageId) {
        currentLanguageId = languageId;
        runOnFxThread(() -> editor.setLanguageId(languageId));
    }

    public void saveAs(Path path) {
        currentFile = path;
        refreshLanguage();
        saveCurrentFile();
    }

    private static void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }
}
