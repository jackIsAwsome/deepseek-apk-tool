package com.apktool.helper;

import brut.androlib.ApkDecoder;
import brut.androlib.ApkBuilder;
import brut.androlib.Config;
import brut.androlib.exceptions.AndrolibException;
import brut.directory.ExtFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 封装 apktool 的反编译 / 重编译 / 签名操作
 */
public class ApkToolWrapper {

    /**
     * 反编译 APK 到指定目录
     */
    public void decompile(Path apkPath, Path outputDir) throws AndrolibException, IOException {
        if (!Files.exists(apkPath)) {
            throw new IOException("APK not found: " + apkPath);
        }
        Files.createDirectories(outputDir);

        Config config = new Config();
        config.setDecodeSources(Config.DECODE_SOURCES_SMALI);
        config.setDecodeResources(Config.DECODE_RESOURCES_FULL);
        config.setForceDelete(true);

        ApkDecoder decoder = new ApkDecoder(new ExtFile(apkPath.toFile()), config);
        decoder.decode(outputDir.toFile());
    }

    /**
     * 将反编译后的目录重编译为 APK
     */
    public void compile(Path sourceDir, Path outputApk) throws AndrolibException, IOException {
        if (!Files.exists(sourceDir)) {
            throw new IOException("Source directory not found: " + sourceDir);
        }
        Files.createDirectories(outputApk.getParent());

        Config config = new Config();
        ApkBuilder builder = new ApkBuilder(new ExtFile(sourceDir.toFile()), config);
        builder.build(outputApk.toFile());
    }

    /**
     * 签名 APK（使用 Android debug keystore）
     */
    public void sign(Path unsignedApk, Path outputApk) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        File jarsigner = new File(javaHome, "bin/jarsigner");

        File keystore = findDebugKeystore();
        if (!keystore.exists()) {
            throw new IOException("Debug keystore not found: " + keystore);
        }

        ProcessBuilder pb = new ProcessBuilder(
                jarsigner.getAbsolutePath(),
                "-keystore", keystore.getAbsolutePath(),
                "-storepass", "android",
                "-keypass", "android",
                "-signedjar", outputApk.toString(),
                unsignedApk.toString(),
                "androiddebugkey"
        );
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("jarsigner failed with exit code " + exitCode);
        }
    }

    private File findDebugKeystore() {
        String userHome = System.getProperty("user.home");
        return new File(userHome, ".android/debug.keystore");
    }
}
