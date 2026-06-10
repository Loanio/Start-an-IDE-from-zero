package com.zeroide.vscode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VsCodeManifestReader {
    private static final String EXTENSION_PACKAGE_JSON = "extension/package.json";
    private static final String ROOT_PACKAGE_JSON = "package.json";

    private final ObjectMapper objectMapper;

    public VsCodeManifestReader() {
        this(new ObjectMapper());
    }

    public VsCodeManifestReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VsCodeExtensionDescriptor readPackageJson(Path packageJson) throws IOException {
        try (InputStream inputStream = Files.newInputStream(packageJson)) {
            return read(inputStream, packageJson.getParent());
        }
    }

    public VsCodeExtensionDescriptor readVsix(Path vsixPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(vsixPath.toFile())) {
            ZipEntry packageJson = packageJsonEntry(zipFile);
            if (packageJson == null) {
                throw new IOException("VSIX does not contain package.json");
            }
            try (InputStream inputStream = zipFile.getInputStream(packageJson)) {
                return read(inputStream, null);
            }
        }
    }

    private VsCodeExtensionDescriptor read(InputStream inputStream, Path extensionRoot) throws IOException {
        JsonNode root = objectMapper.readTree(inputStream);
        String name = text(root, "name");
        String publisher = text(root, "publisher");
        validate(name, publisher);

        return new VsCodeExtensionDescriptor(
                publisher + "." + name,
                name,
                text(root, "displayName"),
                text(root, "version"),
                publisher,
                text(root, "description"),
                text(root.path("engines"), "vscode"),
                text(root, "main"),
                text(root, "browser"),
                textList(root.path("activationEvents")),
                contributions(root.path("contributes")),
                extensionRoot
        );
    }

    private static ZipEntry packageJsonEntry(ZipFile zipFile) {
        ZipEntry extensionPackage = zipFile.getEntry(EXTENSION_PACKAGE_JSON);
        if (extensionPackage != null) {
            return extensionPackage;
        }
        return zipFile.getEntry(ROOT_PACKAGE_JSON);
    }

    private static VsCodeContributions contributions(JsonNode contributes) {
        return new VsCodeContributions(
                commands(contributes.path("commands")),
                languages(contributes.path("languages")),
                grammars(contributes.path("grammars")),
                snippets(contributes.path("snippets")),
                themes(contributes.path("themes"))
        );
    }

    private static List<VsCodeCommandContribution> commands(JsonNode commands) {
        List<VsCodeCommandContribution> results = new ArrayList<>();
        if (commands.isArray()) {
            commands.forEach(command -> results.add(new VsCodeCommandContribution(
                    text(command, "command"),
                    text(command, "title"),
                    text(command, "category")
            )));
        }
        return results;
    }

    private static List<VsCodeLanguageContribution> languages(JsonNode languages) {
        List<VsCodeLanguageContribution> results = new ArrayList<>();
        if (languages.isArray()) {
            languages.forEach(language -> results.add(new VsCodeLanguageContribution(
                    text(language, "id"),
                    textList(language.path("aliases")),
                    textList(language.path("extensions")),
                    textList(language.path("filenames")),
                    text(language, "configuration")
            )));
        }
        return results;
    }

    private static List<VsCodeGrammarContribution> grammars(JsonNode grammars) {
        List<VsCodeGrammarContribution> results = new ArrayList<>();
        if (grammars.isArray()) {
            grammars.forEach(grammar -> results.add(new VsCodeGrammarContribution(
                    text(grammar, "language"),
                    text(grammar, "scopeName"),
                    text(grammar, "path")
            )));
        }
        return results;
    }

    private static List<VsCodeSnippetContribution> snippets(JsonNode snippets) {
        List<VsCodeSnippetContribution> results = new ArrayList<>();
        if (snippets.isArray()) {
            snippets.forEach(snippet -> results.add(new VsCodeSnippetContribution(
                    text(snippet, "language"),
                    text(snippet, "path")
            )));
        }
        return results;
    }

    private static List<VsCodeThemeContribution> themes(JsonNode themes) {
        List<VsCodeThemeContribution> results = new ArrayList<>();
        if (themes.isArray()) {
            themes.forEach(theme -> results.add(new VsCodeThemeContribution(
                    text(theme, "label"),
                    text(theme, "uiTheme"),
                    text(theme, "path")
            )));
        }
        return results;
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            });
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static void validate(String name, String publisher) {
        if (isBlank(name) || isBlank(publisher)) {
            throw new IllegalArgumentException("VS Code extension package.json must contain name and publisher");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
