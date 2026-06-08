package com.zeroide.vscode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VsCodeManifestReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsVsixPackageJsonAndStaticContributions() throws IOException {
        Path vsix = tempDir.resolve("demo.vsix");
        writeVsix(vsix, "extension/package.json", packageJson());

        VsCodeExtensionDescriptor descriptor = new VsCodeManifestReader().readVsix(vsix);

        assertEquals("zero.demo-language", descriptor.id());
        assertEquals("Demo Language", descriptor.displayName());
        assertEquals("^1.90.0", descriptor.vscodeEngine());
        assertEquals("./dist/extension.js", descriptor.main());
        assertEquals(1, descriptor.activationEvents().size());
        assertEquals("demo.hello", descriptor.contributions().commands().getFirst().command());
        assertEquals(".demo", descriptor.contributions().languages().getFirst().extensions().getFirst());
        assertEquals("./snippets/demo.json", descriptor.contributions().snippets().getFirst().path());
    }

    @Test
    void mapsExecutableExtensionsToStaticOnlyPlan() throws IOException {
        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, packageJson(), StandardCharsets.UTF_8);

        VsCodeExtensionDescriptor descriptor = new VsCodeManifestReader().readPackageJson(packageJson);
        VsCodeStaticContributionPlan plan = new VsCodeContributionMapper().mapStaticContributions(descriptor);

        assertTrue(plan.executableExtension());
        assertTrue(plan.compatibilityNote().contains("only static contributions"));
        assertEquals(1, plan.languages().size());
    }

    private static String packageJson() {
        return """
                {
                  "name": "demo-language",
                  "displayName": "Demo Language",
                  "version": "1.2.3",
                  "publisher": "zero",
                  "description": "Demo language support",
                  "engines": {
                    "vscode": "^1.90.0"
                  },
                  "main": "./dist/extension.js",
                  "activationEvents": [
                    "onLanguage:demo"
                  ],
                  "contributes": {
                    "commands": [
                      {
                        "command": "demo.hello",
                        "title": "Hello Demo",
                        "category": "Demo"
                      }
                    ],
                    "languages": [
                      {
                        "id": "demo",
                        "aliases": ["Demo"],
                        "extensions": [".demo"],
                        "filenames": ["DemoFile"],
                        "configuration": "./language-configuration.json"
                      }
                    ],
                    "grammars": [
                      {
                        "language": "demo",
                        "scopeName": "source.demo",
                        "path": "./syntaxes/demo.tmLanguage.json"
                      }
                    ],
                    "snippets": [
                      {
                        "language": "demo",
                        "path": "./snippets/demo.json"
                      }
                    ],
                    "themes": [
                      {
                        "label": "Demo Light",
                        "uiTheme": "vs",
                        "path": "./themes/demo-light.json"
                      }
                    ]
                  }
                }
                """;
    }

    private static void writeVsix(Path vsix, String entryName, String content) throws IOException {
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(vsix))) {
            outputStream.putNextEntry(new ZipEntry(entryName));
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
    }
}
