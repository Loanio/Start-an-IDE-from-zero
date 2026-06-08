package com.zeroide.core.services;

import com.zeroide.api.CommandService;
import com.zeroide.api.EditorContext;
import com.zeroide.api.EditorService;
import com.zeroide.api.EventBus;
import com.zeroide.api.NotificationService;
import com.zeroide.api.PanelService;
import com.zeroide.api.UIService;
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
        context.registerBean(EditorService.class, () -> new JavaFxEditorService(editor, owner, context.getBean(EventBus.class)));
        context.registerBean(WorkspaceService.class, () -> new DefaultWorkspaceService(context.getBean(EventBus.class)));
        context.registerBean(JavaFxUiService.class, () -> new JavaFxUiService(menuBar, statusBar, sidebarPanels, toolPanels, bottomPanels));
        context.registerBean(UIService.class, () -> context.getBean(JavaFxUiService.class));
        context.registerBean(PanelService.class, () -> context.getBean(JavaFxUiService.class));
        context.registerBean(CommandService.class, () -> context.getBean(JavaFxUiService.class));
        context.registerBean(NotificationService.class, () -> context.getBean(JavaFxUiService.class));
        context.registerBean(EditorContext.class, () -> new DefaultEditorContext(
                context.getBean(EditorService.class),
                context.getBean(EventBus.class),
                context.getBean(UIService.class),
                context.getBean(WorkspaceService.class),
                context.getBean(PanelService.class),
                context.getBean(CommandService.class),
                context.getBean(NotificationService.class)
        ));
        context.registerBean(DynamicPluginManager.class, () -> new DynamicPluginManager(
                pluginDirectory,
                context.getBean(EditorContext.class)
        ));
        context.refresh();
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
