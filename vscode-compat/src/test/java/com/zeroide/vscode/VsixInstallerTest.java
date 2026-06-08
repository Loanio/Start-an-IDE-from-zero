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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VsixInstallerTest {
    @TempDir
    Path tempDir;

    @Test
    void installsVsixIntoVersionedDirectory() throws IOException {
        Path vsix = tempDir.resolve("demo.vsix");
        writeVsix(vsix,
                new Entry("extension/package.json", packageJson("demo", "1.0.0")),
                new Entry("extension/snippets/demo.json", "{}"));

        InstalledVsCodeExtension installed = new VsixInstaller().install(vsix, tempDir.resolve("extensions"));

        assertEquals("zero.demo", installed.descriptor().id());
        assertTrue(Files.exists(installed.installPath().resolve("package.json")));
        assertTrue(Files.exists(installed.installPath().resolve("snippets/demo.json")));
    }

    @Test
    void rejectsUnsafeVsixEntryPath() throws IOException {
        Path vsix = tempDir.resolve("unsafe.vsix");
        writeVsix(vsix,
                new Entry("extension/package.json", packageJson("unsafe", "1.0.0")),
                new Entry("../escape.txt", "bad"));

        assertThrows(IOException.class, () -> new VsixInstaller().install(vsix, tempDir.resolve("extensions")));
    }

    private static String packageJson(String name, String version) {
        return """
                {
                  "name": "%s",
                  "displayName": "Demo",
                  "version": "%s",
                  "publisher": "zero",
                  "engines": {
                    "vscode": "^1.90.0"
                  },
                  "contributes": {
                    "snippets": [
                      {
                        "language": "demo",
                        "path": "./snippets/demo.json"
                      }
                    ]
                  }
                }
                """.formatted(name, version);
    }

    private static void writeVsix(Path vsix, Entry... entries) throws IOException {
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(vsix))) {
            for (Entry entry : entries) {
                outputStream.putNextEntry(new ZipEntry(entry.name()));
                outputStream.write(entry.content().getBytes(StandardCharsets.UTF_8));
                outputStream.closeEntry();
            }
        }
    }

    private record Entry(String name, String content) {
    }
}
