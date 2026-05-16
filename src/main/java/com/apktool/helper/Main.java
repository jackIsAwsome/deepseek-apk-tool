package com.apktool.helper;

import com.apktool.helper.SmaliAdRemover.RemovalResult;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI 入口
 *
 * 用法:
 *   java -jar apk-tool.jar decompile  <input.apk> [output-dir]
 *   java -jar apk-tool.jar compile    <source-dir> [output.apk]
 *   java -jar apk-tool.jar remove-ad  <input.apk> [output.apk]
 *   java -jar apk-tool.jar sign       <unsigned.apk>
 */
public class Main {

    private static final ApkToolWrapper apkTool = new ApkToolWrapper();

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();
        try {
            switch (command) {
                case "decompile":
                    handleDecompile(args);
                    break;
                case "compile":
                    handleCompile(args);
                    break;
                case "remove-ad":
                    handleRemoveAd(args);
                    break;
                case "sign":
                    handleSign(args);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void handleDecompile(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: apk-tool decompile <input.apk> [output-dir]");
            System.exit(1);
        }
        Path apk = Paths.get(args[1]);
        Path out = args.length >= 3 ? Paths.get(args[2])
                : Paths.get(apk.getFileName().toString().replace(".apk", "_decompiled"));

        System.out.println("Decompiling: " + apk + " -> " + out);
        apkTool.decompile(apk, out);
        System.out.println("Decompile complete: " + out);
    }

    private static void handleCompile(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: apk-tool compile <source-dir> [output.apk]");
            System.exit(1);
        }
        Path src = Paths.get(args[1]);
        Path apk = args.length >= 3 ? Paths.get(args[2])
                : Paths.get(src.getFileName() + ".apk");

        System.out.println("Compiling: " + src + " -> " + apk);
        apkTool.compile(src, apk);
        System.out.println("Compile complete: " + apk);
    }

    private static void handleRemoveAd(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: apk-tool remove-ad <input.apk> [output.apk]");
            System.exit(1);
        }

        Path inputApk = Paths.get(args[1]);
        Path outputApk = args.length >= 3 ? Paths.get(args[2])
                : Paths.get(inputApk.getFileName().toString().replace(".apk", "_no_ads.apk"));

        String baseName = inputApk.getFileName().toString().replace(".apk", "");
        Path tempDir = Paths.get("temp_" + baseName);

        // Step 1: decompile
        System.out.println("[1/4] Decompiling...");
        apkTool.decompile(inputApk, tempDir);

        // Step 2: remove ads
        System.out.println("[2/4] Removing ad SDKs...");
        SmaliAdRemover remover = new SmaliAdRemover(tempDir);
        RemovalResult result = remover.removeAds();
        System.out.println(result);

        // Step 3: recompile
        System.out.println("[3/4] Recompiling...");
        apkTool.compile(tempDir, outputApk);

        // Step 4: sign
        System.out.println("[4/4] Signing...");
        Path unsigned = Paths.get(outputApk.toString().replace(".apk", "_unsigned.apk"));
        if (!unsigned.equals(outputApk)) {
            java.nio.file.Files.move(outputApk, unsigned,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            // rename to unsigned first
            Path tmpUnsigned = Paths.get(outputApk.toString() + ".unsigned");
            java.nio.file.Files.move(outputApk, tmpUnsigned,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            unsigned = tmpUnsigned;
        }
        apkTool.sign(unsigned, outputApk);
        java.nio.file.Files.deleteIfExists(unsigned);

        System.out.println("Done! Output: " + outputApk);

        // cleanup
        deleteRecursive(tempDir);
    }

    private static void handleSign(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: apk-tool sign <unsigned.apk>");
            System.exit(1);
        }
        Path unsigned = Paths.get(args[1]);
        Path signed = Paths.get(unsigned.getFileName().toString().replace(".apk", "_signed.apk"));

        System.out.println("Signing: " + unsigned + " -> " + signed);
        apkTool.sign(unsigned, signed);
        System.out.println("Sign complete: " + signed);
    }

    private static void printUsage() {
        System.out.println("APK Tool - APK compile/decompile & ad removal utility");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar apk-tool.jar decompile  <input.apk> [output-dir]");
        System.out.println("  java -jar apk-tool.jar compile    <source-dir> [output.apk]");
        System.out.println("  java -jar apk-tool.jar remove-ad  <input.apk> [output.apk]");
        System.out.println("  java -jar apk-tool.jar sign       <unsigned.apk>");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar apk-tool.jar decompile app.apk");
        System.out.println("  java -jar apk-tool.jar remove-ad app.apk app_no_ads.apk");
    }

    private static void deleteRecursive(Path dir) {
        try {
            java.nio.file.Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(f -> {
                        try { java.nio.file.Files.deleteIfExists(f); }
                        catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
