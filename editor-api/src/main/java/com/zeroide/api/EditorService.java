package com.zeroide.api;

import java.nio.file.Path;
import java.util.Optional;

public interface EditorService {
    String getText();

    void replaceText(String text);

    void insertText(String text);

    Optional<Path> getCurrentFile();

    void openFile(Path path);

    void saveCurrentFile();
}
