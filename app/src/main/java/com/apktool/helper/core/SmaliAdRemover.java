package com.apktool.helper.core;

import com.apktool.helper.core.model.AdSdkSignature;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SmaliAdRemover {

    private final List<AdSdkSignature> signatures;
    private final Path decompiledDir;

    private int smaliFilesModified;
    private int manifestEntriesRemoved;
    private int layoutEntriesRemoved;
    private int filesDeleted;

    public SmaliAdRemover(Path decompiledDir) {
        this.decompiledDir = decompiledDir;
        this.signatures = loadBuiltinSignatures();
    }

    public SmaliAdRemover(Path decompiledDir, List<AdSdkSignature> signatures) {
        this.decompiledDir = decompiledDir;
        this.signatures = signatures;
    }

    public RemovalResult removeAds() throws IOException {
        smaliFilesModified = 0;
        manifestEntriesRemoved = 0;
        layoutEntriesRemoved = 0;
        filesDeleted = 0;
        removeAdSmaliFiles();
        removeFromManifest();
        removeFromLayoutFiles();
        removeAdResourceDirs();
        return new RemovalResult(smaliFilesModified, manifestEntriesRemoved,
                layoutEntriesRemoved, filesDeleted);
    }

    private static List<AdSdkSignature> loadBuiltinSignatures() {
        List<AdSdkSignature> list = new ArrayList<>();

        list.add(new AdSdkSignature("Google AdMob", "com.google.android.gms.ads",
                Set.of("AdView", "AdManager", "InterstitialAd", "RewardedAd",
                        "AdRequest", "MobileAds", "AdSize", "NativeExpressAdView",
                        "AppOpenAd", "RewardedInterstitialAd"),
                Set.of("com.google.android.gms.ads.AdActivity",
                        "com.google.android.gms.ads.AdService"),
                List.of("com.google.android.gms.permission.AD_ID")));

        list.add(new AdSdkSignature("Facebook Audience Network", "com.facebook.ads",
                Set.of("AdView", "InterstitialAd", "RewardedVideoAd", "NativeAd",
                        "AudienceNetworkAds", "AdSettings"),
                Set.of("com.facebook.ads.AudienceNetworkActivity",
                        "com.facebook.ads.AudienceNetworkFullScreenActivity"),
                List.of()));

        list.add(new AdSdkSignature("Unity Ads", "com.unity3d.ads",
                Set.of("UnityAds", "UnityAdsShow", "IUnityAdsListener",
                        "IUnityAdsShowListener", "Advertisement"),
                Set.of("com.unity3d.ads.adunit.AdUnitActivity",
                        "com.unity3d.ads.adunit.AdUnitTransparentActivity"),
                List.of()));

        list.add(new AdSdkSignature("AppLovin", "com.applovin",
                Set.of("AppLovinSdk", "AppLovinAdView", "AppLovinInterstitialAd",
                        "AppLovinIncentivizedAd", "AppLovinAd", "AppLovinRewardedAd"),
                Set.of("com.applovin.adview.AppLovinInterstitialActivity"),
                List.of()));

        list.add(new AdSdkSignature("ironSource", "com.ironsource",
                Set.of("IronSource", "InterstitialAd", "RewardedVideoAd", "BannerAd", "LevelPlay"),
                Set.of("com.ironsource.sdk.controller.ControllerActivity",
                        "com.ironsource.sdk.controller.InterstitialActivity"),
                List.of()));

        list.add(new AdSdkSignature("Vungle", "com.vungle",
                Set.of("Vungle", "VungleAd", "VungleInterstitial", "VungleBanner"),
                Set.of("com.vungle.warren.ui.VungleActivity"),
                List.of()));

        list.add(new AdSdkSignature("Mintegral", "com.mbridge.msdk",
                Set.of("MBridge", "MInterstitial", "MRewardVideo", "MBanner"),
                Set.of("com.mintegral.msdk.activity.MTGActivity"),
                List.of()));

        list.add(new AdSdkSignature("AdColony", "com.adcolony",
                Set.of("AdColony", "AdColonyInterstitial", "AdColonyAdView",
                        "AdColonyRewardedAd", "AdColonyNativeAd"),
                Set.of("com.adcolony.sdk.AdColonyActivity"),
                List.of()));

        list.add(new AdSdkSignature("Chartboost", "com.chartboost",
                Set.of("Chartboost", "ChartboostBanner", "ChartboostInterstitial",
                        "ChartboostRewarded"),
                Set.of("com.chartboost.sdk.ChartboostActivity"),
                List.of()));

        list.add(new AdSdkSignature("InMobi", "com.inmobi",
                Set.of("InMobi", "InMobiBanner", "InMobiInterstitial",
                        "InMobiNative", "InMobiAdRequest"),
                Set.of("com.inmobi.ads.InMobiActivity"),
                List.of()));

        list.add(new AdSdkSignature("Pangle (TikTok)", "com.bytedance.sdk.openadsdk",
                Set.of("TTAdManager", "TTAdNative", "TTRewardVideoAd",
                        "TTInterstitialAd", "TTBannerAd", "TTFullScreenVideoAd"),
                Set.of("com.bytedance.sdk.openadsdk.activity.TTAdActivity",
                        "com.bytedance.sdk.openadsdk.activity.TTRewardVideoActivity",
                        "com.bytedance.sdk.openadsdk.activity.TTInterstitialActivity"),
                List.of()));

        list.add(new AdSdkSignature("MyTarget", "com.my.target",
                Set.of("MyTarget", "MyTargetView", "MyTargetInterstitialAd", "MyTargetRewardedAd"),
                Set.of("com.my.target.common.MyTargetActivity"),
                List.of()));

        list.add(new AdSdkSignature("StartApp", "com.startapp",
                Set.of("StartAppSDK", "StartAppAd", "StartAppBanner",
                        "StartAppInterstitial", "StartAppNativeAd"),
                Set.of("com.startapp.sdk.adsbase.activities.AdActivity"),
                List.of()));

        list.add(new AdSdkSignature("Fyber", "com.fyber",
                Set.of("Fyber", "InterstitialAd", "RewardedVideoAd", "BannerAd"),
                Set.of("com.fyber.inneractive.sdk.activities.InneractiveAdActivity"),
                List.of()));

        return list;
    }

    private void removeAdSmaliFiles() throws IOException {
        Path smaliRoot = findSmaliRoot(decompiledDir);
        if (smaliRoot == null) return;

        List<Path> smaliDirs = listSmaliDirs(smaliRoot);
        for (Path smaliDir : smaliDirs) {
            List<Path> filesToDelete = new ArrayList<>();
            List<Path> filesToPatch = new ArrayList<>();

            try (Stream<Path> stream = Files.walk(smaliDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".smali"))
                        .forEach(f -> {
                            if (shouldDeleteSmaliFile(f)) filesToDelete.add(f);
                            else if (referencesAdSdk(f)) filesToPatch.add(f);
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

    private boolean shouldDeleteSmaliFile(Path smaliFile) {
        String path = smaliFile.toString().replace('\\', '/');
        for (AdSdkSignature sig : signatures) {
            String pkgPath = sig.getPackagePrefix().replace('.', '/');
            if (path.contains("/" + pkgPath + "/")) return true;
        }
        return false;
    }

    private boolean referencesAdSdk(Path smaliFile) {
        try {
            String content = new String(Files.readAllBytes(smaliFile), StandardCharsets.UTF_8);
            for (AdSdkSignature sig : signatures) {
                String smaliPrefix = "L" + sig.getPackagePrefix().replace('.', '/');
                if (content.contains(smaliPrefix)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private void patchSmaliFile(Path smaliFile) throws IOException {
        List<String> lines = Files.readAllLines(smaliFile);
        List<String> patched = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            boolean isAdLine = false;
            for (AdSdkSignature sig : signatures) {
                String smaliPrefix = "L" + sig.getPackagePrefix().replace('.', '/');
                if (trimmed.contains(smaliPrefix)) { isAdLine = true; break; }
                String slashed = sig.getPackagePrefix().replace('.', '/');
                if (trimmed.contains(slashed)) { isAdLine = true; break; }
            }

            if (isAdLine && (trimmed.startsWith("invoke-") || trimmed.startsWith("sget-")
                    || trimmed.startsWith("sput-") || trimmed.startsWith("new-instance"))) {
                patched.add("    nop    ; removed ad call");
            } else if (isAdLine && trimmed.startsWith(".field") && trimmed.contains("L")) {
                patched.add("    # removed ad field: " + trimmed);
            } else {
                patched.add(line);
            }
        }
        Files.write(smaliFile, patched);
    }

    private void removeFromManifest() throws IOException {
        Path manifest = decompiledDir.resolve("AndroidManifest.xml");
        if (!Files.exists(manifest)) return;

        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        List<String> filtered = new ArrayList<>();
        boolean skipBlock = false;
        int blockDepth = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (skipBlock) {
                if (trimmed.startsWith("</")) blockDepth--;
                else if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>")) blockDepth++;
                if (blockDepth <= 0) { skipBlock = false; blockDepth = 0; }
                manifestEntriesRemoved++;
                continue;
            }

            boolean shouldSkip = false;
            for (AdSdkSignature sig : signatures) {
                if ((trimmed.startsWith("<activity ") || trimmed.startsWith("<service ")
                        || trimmed.startsWith("<receiver ") || trimmed.startsWith("<meta-data "))
                        && trimmed.contains("android:name=")) {
                    for (String comp : sig.getManifestComponents()) {
                        if (trimmed.contains(comp)) { shouldSkip = true; break; }
                    }
                }
                for (String perm : sig.getPermissions()) {
                    if (trimmed.contains(perm)) { shouldSkip = true; break; }
                }
                if (shouldSkip) break;
            }

            if (shouldSkip) {
                if (!trimmed.endsWith("/>")) { skipBlock = true; blockDepth = 1; }
                manifestEntriesRemoved++;
                continue;
            }
            filtered.add(line);
        }

        if (manifestEntriesRemoved > 0) {
            Files.write(manifest, filtered, StandardCharsets.UTF_8);
        }
    }

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
                    if (trimmed.startsWith("</")) depth--;
                    else if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>")) depth++;
                    if (depth <= 0) { skipBlock = false; depth = 0; }
                    layoutEntriesRemoved++;
                    continue;
                }

                boolean isAdView = false;
                for (AdSdkSignature sig : signatures) {
                    if (trimmed.contains(sig.getPackagePrefix())) { isAdView = true; break; }
                    for (String keyword : sig.getClassKeywords()) {
                        if (trimmed.contains(keyword)) { isAdView = true; break; }
                    }
                    if (isAdView) break;
                }

                if (isAdView) {
                    if (!trimmed.endsWith("/>")) { skipBlock = true; depth = 1; }
                    layoutEntriesRemoved++;
                    continue;
                }
                filtered.add(line);
            }

            if (layoutEntriesRemoved > 0) Files.write(layoutFile, filtered);
        } catch (IOException ignored) {}
    }

    private void removeAdResourceDirs() throws IOException {
        Path smaliRoot = findSmaliRoot(decompiledDir);
        if (smaliRoot == null) return;
        try (Stream<Path> stream = Files.list(smaliRoot)) {
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

    private Path findSmaliRoot(Path base) {
        for (String name : List.of("smali", "smali_classes2", "smali_classes3",
                "smali_classes4", "smali_classes5")) {
            Path p = base.resolve(name);
            if (Files.isDirectory(p)) return base;
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

    public static final class RemovalResult {
        public final int smaliFilesModified;
        public final int manifestEntriesRemoved;
        public final int layoutEntriesRemoved;
        public final int filesDeleted;

        public RemovalResult(int smaliFilesModified, int manifestEntriesRemoved,
                              int layoutEntriesRemoved, int filesDeleted) {
            this.smaliFilesModified = smaliFilesModified;
            this.manifestEntriesRemoved = manifestEntriesRemoved;
            this.layoutEntriesRemoved = layoutEntriesRemoved;
            this.filesDeleted = filesDeleted;
        }

        public int totalChanges() {
            return smaliFilesModified + manifestEntriesRemoved + layoutEntriesRemoved + filesDeleted;
        }

        @Override
        public String toString() {
            return String.format(
                    "Ad Removal Results:\n" +
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
