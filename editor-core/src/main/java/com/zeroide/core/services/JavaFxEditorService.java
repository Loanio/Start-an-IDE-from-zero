package com.zeroide.core.services;

import com.zeroide.api.EditorService;
import com.zeroide.api.EventBus;
import com.zeroide.api.events.FileOpenedEvent;
import com.zeroide.api.events.FileSavedEvent;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
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

    private final TextArea textArea;
    private final Window owner;
    private final EventBus eventBus;
    private Path currentFile;

    public JavaFxEditorService(TextArea textArea, Window owner, EventBus eventBus) {
        this.textArea = textArea;
        this.owner = owner;
        this.eventBus = eventBus;
    }

    @Override
    public String getText() {
        return textArea.getText();
    }

    @Override
    public void replaceText(String text) {
        runOnFxThread(() -> textArea.setText(text == null ? "" : text));
    }

    @Override
    public void insertText(String text) {
        runOnFxThread(() -> textArea.insertText(textArea.getCaretPosition(), text));
    }

    @Override
    public void selectRange(int start, int end) {
        runOnFxThread(() -> {
            int length = textArea.getLength();
            int safeStart = Math.max(0, Math.min(start, length));
            int safeEnd = Math.max(safeStart, Math.min(end, length));
            textArea.requestFocus();
            textArea.selectRange(safeStart, safeEnd);
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
        replaceText("");
    }

    public void saveAs(Path path) {
        currentFile = path;
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
