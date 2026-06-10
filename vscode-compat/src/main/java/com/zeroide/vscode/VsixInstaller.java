package com.zeroide.vscode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VsixInstaller {
    private final VsCodeManifestReader manifestReader;

    public VsixInstaller() {
        this(new VsCodeManifestReader());
    }

    public VsixInstaller(VsCodeManifestReader manifestReader) {
        this.manifestReader = manifestReader;
    }

    public InstalledVsCodeExtension install(Path vsixPath, Path installDirectory) throws IOException {
        VsCodeExtensionDescriptor descriptor = manifestReader.readVsix(vsixPath);
        Path target = installDirectory.resolve(descriptor.id() + "-" + descriptor.version()).normalize();
        replaceDirectory(target);
        extract(vsixPath, target);

        Path extensionRoot = Files.exists(target.resolve("extension/package.json"))
                ? target.resolve("extension")
                : target;
        VsCodeExtensionDescriptor installedDescriptor = manifestReader.readPackageJson(extensionRoot.resolve("package.json"));
        return new InstalledVsCodeExtension(installedDescriptor, extensionRoot);
    }

    private static void extract(Path vsixPath, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipFile zipFile = new ZipFile(vsixPath.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path output = safeOutputPath(target, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                Files.createDirectories(output.getParent());
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path safeOutputPath(Path target, String entryName) throws IOException {
        Path output = target.resolve(entryName).normalize();
        if (!output.startsWith(target)) {
            throw new IOException("Unsafe VSIX entry path: " + entryName);
        }
        return output;
    }

    private static void replaceDirectory(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Cannot delete " + path, ex);
                        }
                    });
        } catch (IllegalStateException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }
}
