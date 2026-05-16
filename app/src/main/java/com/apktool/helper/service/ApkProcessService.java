package com.apktool.helper.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.apktool.helper.MainActivity;
import com.apktool.helper.R;
import com.apktool.helper.core.AiAdAnalyzer;
import com.apktool.helper.core.AiAdRemover;
import com.apktool.helper.core.ApkToolWrapper;
import com.apktool.helper.core.SmaliAdRemover;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApkProcessService extends Service {

    private static final String CHANNEL_ID = "apk_process";
    private static final int NOTIFY_ID = 1;

    public static final String ACTION_DECOMPILE = "decompile";
    public static final String ACTION_COMPILE = "compile";
    public static final String ACTION_AI_REMOVE_AD = "ai_remove_ad";
    public static final String ACTION_SIGN = "sign";

    public static final String EXTRA_INPUT = "input";
    public static final String EXTRA_OUTPUT = "output";
    public static final String EXTRA_KEYSTORE = "keystore";
    public static final String EXTRA_STORE_PASS = "store_pass";
    public static final String EXTRA_KEY_ALIAS = "key_alias";
    public static final String EXTRA_KEY_PASS = "key_pass";

    public interface ProcessCallback {
        void onLog(String message);
        void onComplete(boolean success, String message);
    }

    private static ProcessCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ApkToolWrapper apkTool;

    public static void setCallback(ProcessCallback cb) { callback = cb; }
    public static void clearCallback() { callback = null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        File frameworkDir = new File(getExternalFilesDir(null), "framework");
        if (!frameworkDir.exists()) {
            frameworkDir.mkdirs();
        }
        apkTool = new ApkToolWrapper(frameworkDir.getAbsolutePath());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getStringExtra("action");
        String input = intent.getStringExtra(EXTRA_INPUT);
        String output = intent.getStringExtra(EXTRA_OUTPUT);

        startForeground(NOTIFY_ID, buildNotification("Processing..."));
        log("Starting: " + action);

        new Thread(() -> {
            try {
                switch (action) {
                    case ACTION_DECOMPILE:
                        doDecompile(input, output);
                        break;
                    case ACTION_COMPILE:
                        doCompile(input, output);
                        break;
                    case ACTION_AI_REMOVE_AD:
                        doAiRemoveAd(input, output, intent);
                        break;
                    case ACTION_SIGN:
                        doSign(input, output, intent);
                        break;
                }
                onSuccess("Operation completed: " + output);
            } catch (Exception e) {
                onError(e.getMessage());
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void doDecompile(String apkPath, String outDir) throws Exception {
        log("Decompiling: " + apkPath);
        Path src = Paths.get(apkPath);
        Path out = Paths.get(outDir);
        clearDir(out);
        apkTool.decompile(src, out);
        log("Decompile finished: " + outDir);
    }

    private void doCompile(String srcDir, String outApk) throws Exception {
        log("Compiling: " + srcDir);
        Path src = Paths.get(srcDir);
        Path out = Paths.get(outApk);
        Files.createDirectories(out.getParent());
        apkTool.compile(src, out);
        log("Compile finished: " + outApk);
    }

    private void doAiRemoveAd(String apkPath, String outApk, Intent intent) throws Exception {
        String workDir = new File(getExternalFilesDir(null), "apktool").getAbsolutePath();
        Path decompiledDir = Paths.get(workDir, "decompiled");

        log("[1/5] Decompiling...");
        clearDir(decompiledDir);
        apkTool.decompile(Paths.get(apkPath), decompiledDir);

        log("[2/5] AI analyzing ad SDKs (may take 30-60s)...");
        AiAdAnalyzer analyzer = new AiAdAnalyzer();
        AiAdAnalyzer.AiResult aiResult = analyzer.analyze(decompiledDir, ApkProcessService.this);

        if (aiResult.success && !aiResult.patches.isEmpty()) {
            log("AI found " + aiResult.patches.size() + " items to patch");
            log("AI Summary: " + aiResult.summary);

            log("[3/5] Applying AI patches...");
            AiAdRemover aiRemover = new AiAdRemover(decompiledDir, aiResult);
            AiAdRemover.RemovalResult patchResult = aiRemover.applyPatches();
            log(patchResult.toString());
        } else {
            log("AI analysis failed or found nothing: " + aiResult.error);
            log("[3/5] Falling back to rule-based ad removal...");
            SmaliAdRemover remover = new SmaliAdRemover(decompiledDir);
            SmaliAdRemover.RemovalResult result = remover.removeAds();
            log(result.toString());
        }

        log("[4/5] Recompiling...");
        System.gc();
        Path unsignedApk = Paths.get(workDir, "unsigned.apk");
        apkTool.compile(decompiledDir, unsignedApk);

        log("[5/5] Signing...");
        String keystore = intent.getStringExtra(EXTRA_KEYSTORE);
        if (keystore == null || keystore.isEmpty()) {
            keystore = getDebugKeystore().getAbsolutePath();
        }
        String storePass = nvl(intent.getStringExtra(EXTRA_STORE_PASS), "android");
        String keyAlias = nvl(intent.getStringExtra(EXTRA_KEY_ALIAS), "androiddebugkey");
        String keyPass = nvl(intent.getStringExtra(EXTRA_KEY_PASS), "android");

        apkTool.sign(unsignedApk, Paths.get(outApk), new File(keystore),
                storePass, keyAlias, keyPass);
        Files.deleteIfExists(unsignedApk);

        log("Done! Output: " + outApk);
    }

    private void doSign(String unsignedPath, String outApk, Intent intent) throws Exception {
        String keystore = intent.getStringExtra(EXTRA_KEYSTORE);
        if (keystore == null || keystore.isEmpty()) {
            keystore = getDebugKeystore().getAbsolutePath();
        }
        String storePass = nvl(intent.getStringExtra(EXTRA_STORE_PASS), "android");
        String keyAlias = nvl(intent.getStringExtra(EXTRA_KEY_ALIAS), "androiddebugkey");
        String keyPass = nvl(intent.getStringExtra(EXTRA_KEY_PASS), "android");

        log("Signing: " + unsignedPath);
        apkTool.sign(Paths.get(unsignedPath), Paths.get(outApk), new File(keystore),
                storePass, keyAlias, keyPass);
        log("Signed: " + outApk);
    }

    private File getDebugKeystore() {
        File dir = new File(getExternalFilesDir(null), "keystore");
        dir.mkdirs();
        return new File(dir, "debug.keystore");
    }

    private void clearDir(Path dir) throws Exception {
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(java.util.Comparator.reverseOrder())
                    .forEach(f -> { try { Files.deleteIfExists(f); } catch (Exception ignored) {} });
        }
        Files.createDirectories(dir);
    }

    private String nvl(String val, String def) {
        return (val != null && !val.isEmpty()) ? val : def;
    }

    private void log(String msg) {
        if (callback != null) {
            mainHandler.post(() -> callback.onLog(msg));
        }
    }

    private void onSuccess(String msg) {
        log(msg);
        if (callback != null) {
            mainHandler.post(() -> callback.onComplete(true, msg));
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void onError(String msg) {
        log("Error: " + msg);
        if (callback != null) {
            mainHandler.post(() -> callback.onComplete(false, msg));
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "APK Processing",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows APK processing progress");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("APK Tool")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
