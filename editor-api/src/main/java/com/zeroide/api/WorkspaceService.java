package com.zeroide.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface WorkspaceService {
    Optional<Path> getWorkspace();

    List<Path> getRecentWorkspaces();

    void openWorkspace(Path path);
}
