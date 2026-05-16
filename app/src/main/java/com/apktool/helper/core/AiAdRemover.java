package com.apktool.helper.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class AiAdRemover {

    private final Path decompiledDir;
    private final AiAdAnalyzer.AiResult aiResult;

    private int filesDeleted;
    private int filesPatched;
    private int manifestChanges;
    private int dirsDeleted;

    public AiAdRemover(Path decompiledDir, AiAdAnalyzer.AiResult aiResult) {
        this.decompiledDir = decompiledDir;
        this.aiResult = aiResult;
    }

    public RemovalResult applyPatches() throws IOException {
        filesDeleted = 0;
        filesPatched = 0;
        manifestChanges = 0;
        dirsDeleted = 0;

        for (AiAdAnalyzer.AiPatch patch : aiResult.patches) {
            try {
                applyPatch(patch);
            } catch (Exception e) {
                System.err.println("AI patch failed [" + patch.action + " " + patch.filePath + "]: " + e.getMessage());
            }
        }

        return new RemovalResult(filesDeleted, filesPatched, manifestChanges, dirsDeleted);
    }

    private void applyPatch(AiAdAnalyzer.AiPatch patch) throws IOException {
        if (patch.action == null) return;

        switch (patch.action) {
            case "delete_smali":
                deleteSmaliFile(patch.filePath);
                break;
            case "delete_dir":
                deleteDirectory(patch.filePath);
                break;
            case "patch_smali":
                patchSmaliFile(patch.filePath, patch.target, patch.replacement);
                break;
            case "remove_from_manifest":
                removeFromManifest(patch.target);
                break;
            case "remove_from_manifest_permission":
                removeManifestPermission(patch.target);
                break;
            case "patch_manifest":
                patchManifest(patch.target, patch.replacement);
                break;
        }
    }

    private void deleteSmaliFile(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) return;
        Path file = decompiledDir.resolve(relativePath);
        if (Files.exists(file)) {
            Files.delete(file);
            filesDeleted++;
        }
    }

    private void deleteDirectory(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) return;
        Path dir = decompiledDir.resolve(relativePath);
        if (Files.isDirectory(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(f -> {
                    try { Files.deleteIfExists(f); filesDeleted++; } catch (IOException ignored) {}
                });
            }
            dirsDeleted++;
        }
    }

    private void patchSmaliFile(String relativePath, String target, String replacement) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) return;
        Path file = decompiledDir.resolve(relativePath);
        if (!Files.exists(file)) return;

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> patched = new ArrayList<>();

        if (target != null && !target.isEmpty()) {
            Pattern pattern = Pattern.compile(target);
            for (String line : lines) {
                if (pattern.matcher(line).find()) {
                    if ("nop".equals(replacement) || replacement == null || replacement.isEmpty()) {
                        patched.add("    nop    ; AI: removed ad call");
                    } else {
                        patched.add(line.replaceAll(target, replacement));
                    }
                } else {
                    patched.add(line);
                }
            }
        }

        Files.write(file, patched);
        filesPatched++;
    }

    private void removeFromManifest(String target) throws IOException {
        if (target == null || target.isEmpty()) return;
        Path manifest = decompiledDir.resolve("AndroidManifest.xml");
        if (!Files.exists(manifest)) return;

        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        List<String> filtered = new ArrayList<>();
        boolean skipBlock = false;
        int depth = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (skipBlock) {
                if (trimmed.startsWith("</")) depth--;
                else if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>")) depth++;
                if (depth <= 0) { skipBlock = false; depth = 0; }
                manifestChanges++;
                continue;
            }

            if (trimmed.contains(target)) {
                if (!trimmed.endsWith("/>")) { skipBlock = true; depth = 1; }
                manifestChanges++;
                continue;
            }
            filtered.add(line);
        }

        Files.write(manifest, filtered, StandardCharsets.UTF_8);
    }

    private void removeManifestPermission(String target) throws IOException {
        if (target == null || target.isEmpty()) return;
        Path manifest = decompiledDir.resolve("AndroidManifest.xml");
        if (!Files.exists(manifest)) return;

        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        List<String> filtered = new ArrayList<>();

        for (String line : lines) {
            if (line.contains(target)) {
                manifestChanges++;
                continue;
            }
            filtered.add(line);
        }

        Files.write(manifest, filtered, StandardCharsets.UTF_8);
    }

    private void patchManifest(String target, String replacement) throws IOException {
        if (target == null || target.isEmpty()) return;
        Path manifest = decompiledDir.resolve("AndroidManifest.xml");
        if (!Files.exists(manifest)) return;

        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        List<String> patched = new ArrayList<>();

        for (String line : lines) {
            if (line.contains(target) && replacement != null) {
                patched.add(line.replace(target, replacement));
                manifestChanges++;
            } else {
                patched.add(line);
            }
        }

        Files.write(manifest, patched, StandardCharsets.UTF_8);
    }

    public static class RemovalResult {
        public final int filesDeleted;
        public final int filesPatched;
        public final int manifestChanges;
        public final int dirsDeleted;

        public RemovalResult(int filesDeleted, int filesPatched, int manifestChanges, int dirsDeleted) {
            this.filesDeleted = filesDeleted;
            this.filesPatched = filesPatched;
            this.manifestChanges = manifestChanges;
            this.dirsDeleted = dirsDeleted;
        }

        public int totalChanges() {
            return filesDeleted + filesPatched + manifestChanges + dirsDeleted;
        }

        @Override
        public String toString() {
            return String.format(
                "AI Ad Removal Results:\n" +
                "  Files deleted:       %d\n" +
                "  Files patched:       %d\n" +
                "  Manifest changes:    %d\n" +
                "  Directories deleted: %d\n" +
                "  Total changes:       %d",
                filesDeleted, filesPatched, manifestChanges, dirsDeleted, totalChanges());
        }
    }
}
