package com.zeroide.core.services;

import com.zeroide.api.EditorContext;
import com.zeroide.api.EditorService;
import com.zeroide.api.EventBus;
import com.zeroide.api.HighlightingService;
import com.zeroide.api.LanguageService;
import com.zeroide.api.SnippetService;
import com.zeroide.api.WorkspaceService;
import com.zeroide.core.events.DefaultEventBus;
import com.zeroide.core.editor.RichCodeEditor;
import com.zeroide.core.plugins.DynamicPluginManager;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.springframework.context.support.GenericApplicationContext;

import java.nio.file.Path;

public final class CoreContainer implements AutoCloseable {
    private final GenericApplicationContext springContext;

    private CoreContainer(GenericApplicationContext springContext) {
        this.springContext = springContext;
    }

    public static CoreContainer create(RichCodeEditor editor, MenuBar menuBar, HBox statusBar, TabPane sidebarPanels, TabPane toolPanels, TabPane bottomPanels, Window owner, Path pluginDirectory) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(EventBus.class, DefaultEventBus::new);
        context.registerBean(LanguageService.class, DefaultLanguageService::new);
        context.registerBean(HighlightingService.class, DefaultHighlightingService::new);
        context.registerBean(SnippetService.class, DefaultSnippetService::new);
        context.registerBean(EditorService.class, () -> new JavaFxEditorService(
                editor,
                owner,
                context.getBean(EventBus.class),
                context.getBean(LanguageService.class)
        ));
        context.registerBean(WorkspaceService.class, () -> new DefaultWorkspaceService(context.getBean(EventBus.class)));
        context.registerBean(JavaFxUiService.class, () -> new JavaFxUiService(menuBar, statusBar, sidebarPanels, toolPanels, bottomPanels));
        context.registerBean(EditorContext.class, () -> {
            JavaFxUiService uiService = context.getBean(JavaFxUiService.class);
            return new DefaultEditorContext(
                    context.getBean(EditorService.class),
                    context.getBean(EventBus.class),
                    uiService,
                    context.getBean(WorkspaceService.class),
                    uiService,
                    uiService,
                    uiService,
                    context.getBean(LanguageService.class),
                    context.getBean(HighlightingService.class),
                    context.getBean(SnippetService.class)
            );
        });
        context.registerBean(DynamicPluginManager.class, () -> new DynamicPluginManager(
                pluginDirectory,
                context.getBean(EditorContext.class)
        ));
        context.refresh();
        editor.setHighlightingService(context.getBean(HighlightingService.class));
        return new CoreContainer(context);
    }

    public <T> T getBean(Class<T> type) {
        return springContext.getBean(type);
    }

    @Override
    public void close() {
        springContext.close();
    }
}
