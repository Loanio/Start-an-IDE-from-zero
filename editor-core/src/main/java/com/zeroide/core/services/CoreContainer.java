package com.zeroide.core.services;

import com.zeroide.api.EditorContext;
import com.zeroide.api.EditorService;
import com.zeroide.api.EventBus;
import com.zeroide.api.UIService;
import com.zeroide.core.events.DefaultEventBus;
import com.zeroide.core.plugins.DynamicPluginManager;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.springframework.context.support.GenericApplicationContext;

import java.nio.file.Path;

public final class CoreContainer implements AutoCloseable {
    private final GenericApplicationContext springContext;

    private CoreContainer(GenericApplicationContext springContext) {
        this.springContext = springContext;
    }

    public static CoreContainer create(TextArea textArea, MenuBar menuBar, HBox statusBar, TabPane toolPanels, Window owner, Path pluginDirectory) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(EventBus.class, DefaultEventBus::new);
        context.registerBean(EditorService.class, () -> new JavaFxEditorService(textArea, owner, context.getBean(EventBus.class)));
        context.registerBean(UIService.class, () -> new JavaFxUiService(menuBar, statusBar, toolPanels));
        context.registerBean(EditorContext.class, () -> new DefaultEditorContext(
                context.getBean(EditorService.class),
                context.getBean(EventBus.class),
                context.getBean(UIService.class)
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
