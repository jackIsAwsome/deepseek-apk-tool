package com.apktool.helper.core;

import brut.androlib.ApkDecoder;
import brut.androlib.ApkBuilder;
import brut.androlib.Config;
import brut.androlib.exceptions.AndrolibException;
import brut.directory.ExtFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ApkToolWrapper {

    private final String frameworkDir;

    static {
        if (System.getProperty("os.name") == null) {
            System.setProperty("os.name", "Linux");
        }
    }

    public ApkToolWrapper(String frameworkDir) {
        this.frameworkDir = frameworkDir;
    }

    public void decompile(Path apkPath, Path outputDir) throws AndrolibException, IOException {
        if (!Files.exists(apkPath)) {
            throw new IOException("APK not found: " + apkPath);
        }
        Files.createDirectories(outputDir);

        System.setProperty("os.name", "Linux");

        Config config = new Config();
        config.setFrameworkDirectory(frameworkDir);
        config.setDecodeSources(Config.DECODE_SOURCES_SMALI);
        config.setDecodeResources(Config.DECODE_RESOURCES_FULL);
        config.setForceDecodeManifest(Config.FORCE_DECODE_MANIFEST_FULL);
        config.setForceDelete(true);
        config.setKeepBrokenResources(true);
        config.setNoCrunch(true);

        try {
            ApkDecoder decoder = new ApkDecoder(new ExtFile(apkPath.toFile()), config);
            decoder.decode(outputDir.toFile());
        } catch (NoClassDefFoundError e) {
            // javax.imageio not available on Android; retry with resources disabled
            System.err.println("Full resource decode failed: " + e.getMessage());
            System.err.println("Retrying with resources disabled...");
            clearDir(outputDir);
            config.setDecodeResources(Config.DECODE_RESOURCES_NONE);
            ApkDecoder decoder = new ApkDecoder(new ExtFile(apkPath.toFile()), config);
            decoder.decode(outputDir.toFile());
        }
    }

    public void compile(Path sourceDir, Path outputApk) throws AndrolibException, IOException {
        if (!Files.exists(sourceDir)) {
            throw new IOException("Source directory not found: " + sourceDir);
        }
        Files.createDirectories(outputApk.getParent());

        System.setProperty("os.name", "Linux");

        Config config = new Config();
        config.setFrameworkDirectory(frameworkDir);
        ApkBuilder builder = new ApkBuilder(new ExtFile(sourceDir.toFile()), config);
        builder.build(outputApk.toFile());
    }

    public void sign(Path unsignedApk, Path outputApk, File keystore,
                     String storePass, String keyAlias, String keyPass)
            throws Exception {
        JavaApkSigner.sign(unsignedApk, outputApk, keystore, storePass, keyAlias, keyPass);
    }

    private void clearDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        }
        Files.createDirectories(dir);
    }
}
