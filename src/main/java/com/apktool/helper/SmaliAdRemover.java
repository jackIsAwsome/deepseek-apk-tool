package com.apktool.helper;

import com.apktool.helper.model.AdSdkSignature;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 广告检测与移除引擎
 * 处理 smali 文件、AndroidManifest.xml、layout 文件中的广告代码
 */
public class SmaliAdRemover {

    private final List<AdSdkSignature> signatures;
    private final Path decompiledDir;

    private int smaliFilesModified = 0;
    private int manifestEntriesRemoved = 0;
    private int layoutEntriesRemoved = 0;
    private int filesDeleted = 0;

    public SmaliAdRemover(Path decompiledDir) {
        this.decompiledDir = decompiledDir;
        this.signatures = loadBuiltinSignatures();
    }

    public SmaliAdRemover(Path decompiledDir, List<AdSdkSignature> signatures) {
        this.decompiledDir = decompiledDir;
        this.signatures = signatures;
    }

    // ---- 内置广告 SDK 特征 ----

    private static List<AdSdkSignature> loadBuiltinSignatures() {
        List<AdSdkSignature> list = new ArrayList<>();

        list.add(new AdSdkSignature(
                "Google AdMob",
                "com.google.android.gms.ads",
                Set.of("AdView", "AdManager", "InterstitialAd", "RewardedAd",
                       "AdRequest", "MobileAds", "AdSize", "NativeExpressAdView",
                       "AppOpenAd", "RewardedInterstitialAd"),
                Set.of("com.google.android.gms.ads.AdActivity",
                       "com.google.android.gms.ads.AdService"),
                List.of("com.google.android.gms.permission.AD_ID")
        ));

        list.add(new AdSdkSignature(
                "Facebook Audience Network",
                "com.facebook.ads",
                Set.of("AdView", "InterstitialAd", "RewardedVideoAd", "NativeAd",
                       "AudienceNetworkAds", "AdSettings"),
                Set.of("com.facebook.ads.AudienceNetworkActivity",
                       "com.facebook.ads.AudienceNetworkFullScreenActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "Unity Ads",
                "com.unity3d.ads",
                Set.of("UnityAds", "UnityAdsShow", "IUnityAdsListener",
                       "IUnityAdsShowListener", "Advertisement"),
                Set.of("com.unity3d.ads.adunit.AdUnitActivity",
                       "com.unity3d.ads.adunit.AdUnitTransparentActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "AppLovin",
                "com.applovin",
                Set.of("AppLovinSdk", "AppLovinAdView", "AppLovinInterstitialAd",
                       "AppLovinIncentivizedAd", "AppLovinAd", "AppLovinRewardedAd"),
                Set.of("com.applovin.adview.AppLovinInterstitialActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "ironSource",
                "com.ironsource",
                Set.of("IronSource", "InterstitialAd", "RewardedVideoAd",
                       "BannerAd", "LevelPlay"),
                Set.of("com.ironsource.sdk.controller.ControllerActivity",
                       "com.ironsource.sdk.controller.InterstitialActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "Vungle",
                "com.vungle",
                Set.of("Vungle", "VungleAd", "VungleInterstitial", "VungleBanner"),
                Set.of("com.vungle.warren.ui.VungleActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "Mintegral",
                "com.mbridge.msdk",
                Set.of("MBridge", "MInterstitial", "MRewardVideo", "MBanner"),
                Set.of("com.mintegral.msdk.activity.MTGActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "AdColony",
                "com.adcolony",
                Set.of("AdColony", "AdColonyInterstitial", "AdColonyAdView",
                       "AdColonyRewardedAd", "AdColonyNativeAd"),
                Set.of("com.adcolony.sdk.AdColonyActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "Chartboost",
                "com.chartboost",
                Set.of("Chartboost", "ChartboostBanner", "ChartboostInterstitial",
                       "ChartboostRewarded"),
                Set.of("com.chartboost.sdk.ChartboostActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "InMobi",
                "com.inmobi",
                Set.of("InMobi", "InMobiBanner", "InMobiInterstitial",
                       "InMobiNative", "InMobiAdRequest"),
                Set.of("com.inmobi.ads.InMobiActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "Pangle (TikTok)",
                "com.bytedance.sdk.openadsdk",
                Set.of("TTAdManager", "TTAdNative", "TTRewardVideoAd",
                       "TTInterstitialAd", "TTBannerAd", "TTFullScreenVideoAd"),
                Set.of("com.bytedance.sdk.openadsdk.activity.TTAdActivity",
                       "com.bytedance.sdk.openadsdk.activity.TTRewardVideoActivity",
                       "com.bytedance.sdk.openadsdk.activity.TTInterstitialActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "MyTarget",
                "com.my.target",
                Set.of("MyTarget", "MyTargetView", "MyTargetInterstitialAd",
                       "MyTargetRewardedAd"),
                Set.of("com.my.target.common.MyTargetActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "StartApp",
                "com.startapp",
                Set.of("StartAppSDK", "StartAppAd", "StartAppBanner",
                       "StartAppInterstitial", "StartAppNativeAd"),
                Set.of("com.startapp.sdk.adsbase.activities.AdActivity"),
                List.of()
        ));

        list.add(new AdSdkSignature(
                "Fyber (Digital Turbine)",
                "com.fyber",
                Set.of("Fyber", "InterstitialAd", "RewardedVideoAd", "BannerAd"),
                Set.of("com.fyber.inneractive.sdk.activities.InneractiveAdActivity"),
                List.of()
        ));

        return list;
    }

    // ---- 广告移除流程 ----

    /**
     * 执行完整的广告移除流程，返回变更统计
     */
    public RemovalResult removeAds() throws IOException {
        removeAdSmaliFiles();
        removeFromManifest();
        removeFromLayoutFiles();
        removeAdResourceDirs();
        return new RemovalResult(smaliFilesModified, manifestEntriesRemoved,
                layoutEntriesRemoved, filesDeleted);
    }

    // ---- Smali 文件处理 ----

    private void removeAdSmaliFiles() throws IOException {
        Path smaliRoot = findSmaliRoot(decompiledDir);
        if (smaliRoot == null) {
            System.out.println("[SmaliAdRemover] No smali directory found");
            return;
        }

        // 收集所有匹配的 smali 目录
        List<Path> smaliDirs = listSmaliDirs(smaliRoot);

        for (Path smaliDir : smaliDirs) {
            List<Path> filesToDelete = new ArrayList<>();
            List<Path> filesToPatch = new ArrayList<>();

            try (Stream<Path> stream = Files.walk(smaliDir)) {
                stream.filter(Files::isRegularFile)
                      .filter(f -> f.toString().endsWith(".smali"))
                      .forEach(f -> {
                          if (shouldDeleteSmaliFile(f)) {
                              filesToDelete.add(f);
                          } else if (referencesAdSdk(f)) {
                              filesToPatch.add(f);
                          }
                      });
            }

            for (Path file : filesToDelete) {
                Files.deleteIfExists(file);
                filesDeleted++;
            }
            for (Path file : filesToPatch) {
                patchSmaliFile(file);
                smaliFilesModified++;
            }
        }
    }

    /**
     * 判断 smali 文件是否整个都需要删除（属于广告 SDK 自身文件）
     */
    private boolean shouldDeleteSmaliFile(Path smaliFile) {
        String path = smaliFile.toString().replace('\\', '/');
        for (AdSdkSignature sig : signatures) {
            String pkgPath = sig.getPackagePrefix().replace('.', '/');
            if (path.contains("/" + pkgPath + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 smali 文件是否引用了广告 SDK（但不属于广告 SDK 自身）
     */
    private boolean referencesAdSdk(Path smaliFile) {
        try {
            String content = Files.readString(smaliFile);
            for (AdSdkSignature sig : signatures) {
                // 用 Lpackage/... 的 smali 类引用格式匹配
                String smaliPrefix = "L" + sig.getPackagePrefix().replace('.', '/');
                if (content.contains(smaliPrefix)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    /**
     * 对引用广告 SDK 的 smali 文件做修补：把调用指令替换为空操作
     */
    private void patchSmaliFile(Path smaliFile) throws IOException {
        List<String> lines = Files.readAllLines(smaliFile);
        List<String> patched = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            boolean isAdLine = false;

            for (AdSdkSignature sig : signatures) {
                String smaliPrefix = "L" + sig.getPackagePrefix().replace('.', '/');
                if (trimmed.contains(smaliPrefix)) {
                    isAdLine = true;
                    break;
                }
                // 也检查 slashed 格式 (smali 字段描述符)
                String slashed = sig.getPackagePrefix().replace('.', '/');
                if (trimmed.contains(slashed)) {
                    isAdLine = true;
                    break;
                }
            }

            if (isAdLine && (trimmed.startsWith("invoke-") || trimmed.startsWith("sget-")
                    || trimmed.startsWith("sput-") || trimmed.startsWith("new-instance"))) {
                // 将广告相关调用替换为 nop 或移除
                patched.add("    nop    ; removed ad call");
            } else if (isAdLine && trimmed.startsWith(".field") && trimmed.contains("L")) {
                // 删除广告类型字段
                patched.add("    # removed ad field: " + trimmed);
                // 保留原行做注释参考，不保留实际字段
            } else {
                patched.add(line);
            }
        }

        Files.write(smaliFile, patched);
    }

    // ---- AndroidManifest.xml 处理 ----

    private void removeFromManifest() throws IOException {
        Path manifest = decompiledDir.resolve("AndroidManifest.xml");
        if (!Files.exists(manifest)) return;

        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        List<String> filtered = new ArrayList<>();
        boolean skipBlock = false;
        int blockDepth = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (skipBlock) {
                if (trimmed.startsWith("</")) {
                    blockDepth--;
                    if (blockDepth <= 0) {
                        skipBlock = false;
                        blockDepth = 0;
                    }
                } else if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>")) {
                    blockDepth++;
                }
                manifestEntriesRemoved++;
                continue;
            }

            boolean shouldSkip = false;
            for (AdSdkSignature sig : signatures) {
                // 检查 activity/service/receiver/meta-data 的 android:name
                if ((trimmed.startsWith("<activity ") || trimmed.startsWith("<service ")
                        || trimmed.startsWith("<receiver ") || trimmed.startsWith("<meta-data "))
                        && trimmed.contains("android:name=")) {
                    for (String comp : sig.getManifestComponents()) {
                        if (trimmed.contains(comp)) {
                            shouldSkip = true;
                            break;
                        }
                    }
                }
                // 检查 permission
                for (String perm : sig.getPermissions()) {
                    if (trimmed.contains(perm)) {
                        shouldSkip = true;
                        break;
                    }
                }
                if (shouldSkip) break;
            }

            if (shouldSkip) {
                if (!trimmed.endsWith("/>")) {
                    skipBlock = true;
                    blockDepth = 1;
                }
                manifestEntriesRemoved++;
                continue;
            }

            filtered.add(line);
        }

        if (manifestEntriesRemoved > 0) {
            Files.write(manifest, filtered, StandardCharsets.UTF_8);
        }
    }

    // ---- Layout 文件处理 ----

    private void removeFromLayoutFiles() throws IOException {
        Path layoutDir = decompiledDir.resolve("res/layout");
        if (!Files.exists(layoutDir)) return;

        try (Stream<Path> stream = Files.list(layoutDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(f -> f.toString().endsWith(".xml"))
                  .forEach(this::patchLayoutFile);
        }
    }

    private void patchLayoutFile(Path layoutFile) {
        try {
            List<String> lines = Files.readAllLines(layoutFile);
            List<String> filtered = new ArrayList<>();
            boolean skipBlock = false;
            int depth = 0;

            for (String line : lines) {
                String trimmed = line.trim();

                if (skipBlock) {
                    if (trimmed.startsWith("</")) {
                        depth--;
                    } else if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>")) {
                        depth++;
                    }
                    if (depth <= 0) {
                        skipBlock = false;
                        depth = 0;
                    }
                    layoutEntriesRemoved++;
                    continue;
                }

                boolean isAdView = false;
                for (AdSdkSignature sig : signatures) {
                    String pkg = sig.getPackagePrefix();
                    if (trimmed.startsWith("<" + pkg) || trimmed.startsWith("<" + pkg.split("\\.")[pkg.split("\\.").length - 1])) {
                        isAdView = true;
                        break;
                    }
                    if (trimmed.contains(pkg)) {
                        isAdView = true;
                        break;
                    }
                    for (String keyword : sig.getClassKeywords()) {
                        if (trimmed.contains(keyword + " ") || trimmed.contains(keyword + "\n")
                                || trimmed.endsWith(keyword) || trimmed.contains("\"" + keyword + "\"")) {
                            isAdView = true;
                            break;
                        }
                    }
                    if (isAdView) break;
                }

                if (isAdView) {
                    if (!trimmed.endsWith("/>")) {
                        skipBlock = true;
                        depth = 1;
                    }
                    layoutEntriesRemoved++;
                    continue;
                }

                filtered.add(line);
            }

            if (layoutEntriesRemoved > 0) {
                Files.write(layoutFile, filtered);
            }
        } catch (IOException ignored) {
        }
    }

    // ---- 广告资源目录删除 ----

    private void removeAdResourceDirs() throws IOException {
        List<Path> roots = new ArrayList<>();

        Path smaliRoot = findSmaliRoot(decompiledDir);
        if (smaliRoot != null) {
            roots.add(smaliRoot);
        }

        // 也检查 res/raw 中的广告 JSON 配置
        Path rawDir = decompiledDir.resolve("res/raw");
        if (Files.exists(rawDir)) {
            // 不删除整个 raw 目录，只标记
        }

        for (Path root : roots) {
            try (Stream<Path> stream = Files.list(root)) {
                List<Path> dirs = stream.filter(Files::isDirectory).collect(Collectors.toList());
                for (Path dir : dirs) {
                    for (AdSdkSignature sig : signatures) {
                        String pkgSegment = sig.getPackagePrefix().split("\\.")[0];
                        if (dir.getFileName().toString().startsWith(pkgSegment)) {
                            deleteRecursive(dir);
                            filesDeleted += countFiles(dir);
                        }
                    }
                }
            }
        }
    }

    private int countFiles(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return (int) s.filter(Files::isRegularFile).count();
        }
    }

    private void deleteRecursive(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> {
                try { Files.deleteIfExists(f); } catch (IOException ignored) {}
            });
        }
    }

    // ---- 工具方法 ----

    private Path findSmaliRoot(Path base) {
        // 尝试常规 smali 目录
        for (String name : List.of("smali", "smali_classes2", "smali_classes3",
                                    "smali_classes4", "smali_classes5")) {
            Path p = base.resolve(name);
            if (Files.isDirectory(p)) return base; // 返回 base，内部用 listSmaliDirs 遍历
        }
        return null;
    }

    private List<Path> listSmaliDirs(Path base) throws IOException {
        List<Path> dirs = new ArrayList<>();
        if (!Files.exists(base)) return dirs;
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isDirectory)
                  .filter(d -> d.getFileName().toString().startsWith("smali"))
                  .forEach(dirs::add);
        }
        return dirs;
    }

    // ---- 结果模型 ----

    public record RemovalResult(int smaliFilesModified, int manifestEntriesRemoved,
                                 int layoutEntriesRemoved, int filesDeleted) {
        public int totalChanges() {
            return smaliFilesModified + manifestEntriesRemoved + layoutEntriesRemoved + filesDeleted;
        }

        @Override
        public String toString() {
            return String.format(
                    "Advertisement Removal Results:\n" +
                    "  Smali files patched:      %d\n" +
                    "  Manifest entries removed: %d\n" +
                    "  Layout entries removed:   %d\n" +
                    "  Files deleted:            %d\n" +
                    "  Total changes:            %d",
                    smaliFilesModified, manifestEntriesRemoved,
                    layoutEntriesRemoved, filesDeleted, totalChanges());
        }
    }
}
