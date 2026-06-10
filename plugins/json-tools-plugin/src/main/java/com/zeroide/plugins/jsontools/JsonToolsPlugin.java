package com.zeroide.plugins.jsontools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroide.api.EditorContext;
import com.zeroide.api.Plugin;

public final class JsonToolsPlugin implements Plugin {
    private static final String STATUS_ID = "plugin.json-tools.status";
    private static final String FORMAT_COMMAND_ID = "plugin.json-tools.format";
    private static final String MINIFY_COMMAND_ID = "plugin.json-tools.minify";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EditorContext context;

    @Override
    public void onLoad(EditorContext context) {
        this.context = context;
        context.notifications().addStatusItem(STATUS_ID, "JSON tools");
        context.commands().registerCommand(FORMAT_COMMAND_ID, "JSON: Format Document", this::formatJson);
        context.commands().registerCommand(MINIFY_COMMAND_ID, "JSON: Minify Document", this::minifyJson);
        context.commands().addMenuItem("JSON", FORMAT_COMMAND_ID, FORMAT_COMMAND_ID);
        context.commands().addMenuItem("JSON", MINIFY_COMMAND_ID, MINIFY_COMMAND_ID);
    }

    @Override
    public void onUnload() {
        if (context != null) {
            context.commands().removeMenuItem(FORMAT_COMMAND_ID);
            context.commands().removeMenuItem(MINIFY_COMMAND_ID);
            context.commands().unregisterCommand(FORMAT_COMMAND_ID);
            context.commands().unregisterCommand(MINIFY_COMMAND_ID);
            context.notifications().removeStatusItem(STATUS_ID);
        }
    }

    private void formatJson() {
        transform(true);
    }

    private void minifyJson() {
        transform(false);
    }

    private void transform(boolean pretty) {
        try {
            JsonNode root = objectMapper.readTree(context.editor().getText());
            String output = pretty
                    ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
                    : objectMapper.writeValueAsString(root);
            context.editor().replaceText(output + System.lineSeparator());
            context.notifications().showInfo("JSON", pretty ? "Formatted document." : "Minified document.");
        } catch (Exception ex) {
            context.notifications().showError("JSON", "The current document is not valid JSON.");
        }
    }
}
