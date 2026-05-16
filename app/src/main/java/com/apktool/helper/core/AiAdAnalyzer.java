package com.apktool.helper.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class AiAdAnalyzer {

    private static final String PREFS_NAME = "apktool_prefs";
    private static final String KEY_DEEPSEEK_API = "deepseek_api_key";
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";

    public static String getApiKey(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DEEPSEEK_API, "");
    }

    public static class AiPatch {
        public String filePath;
        public String action;     // "delete", "replace", "patch_smali", "remove_from_manifest"
        public String target;     // text/pattern to find
        public String replacement; // replacement text (empty for delete)
        public String reason;     // why this patch
    }

    public static class AiResult {
        public List<AiPatch> patches = new ArrayList<>();
        public String summary;
        public boolean success;
        public String error;
    }

    public AiResult analyze(Path decompiledDir, Context ctx) {
        AiResult result = new AiResult();
        try {
            String smaliSummary = collectSmaliSummary(decompiledDir);
            String manifest = readManifest(decompiledDir);
            String layouts = collectLayoutSummary(decompiledDir);

            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(smaliSummary, manifest, layouts);

            String response = callDeepSeek(systemPrompt, userPrompt,
                    getApiKey(ctx));

            result = parseResponse(response, decompiledDir);
        } catch (Exception e) {
            result.success = false;
            result.error = e.getMessage();
        }
        return result;
    }

    private String collectSmaliSummary(Path base) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String name : new String[]{"smali", "smali_classes2", "smali_classes3",
                "smali_classes4", "smali_classes5"}) {
            Path dir = base.resolve(name);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> stream = Files.walk(dir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(f -> f.toString().endsWith(".smali"))
                            .limit(300)
                            .forEach(f -> {
                                try {
                                    List<String> lines = Files.readAllLines(f);
                                    // Only include lines with ad-related keywords
                                    for (String line : lines) {
                                        String lower = line.toLowerCase();
                                        if (lower.contains("ads") || lower.contains("adview")
                                                || lower.contains("interstitial") || lower.contains("banner")
                                                || lower.contains("rewarded") || lower.contains("sdk")
                                                || lower.contains("unityads") || lower.contains("applovin")
                                                || lower.contains("vungle") || lower.contains("chartboost")
                                                || lower.contains("ironsource") || lower.contains("fyber")
                                                || lower.contains("inmobi") || lower.contains("adcolony")
                                                || lower.contains("mintegral") || lower.contains("pangle")
                                                || lower.contains("bytedance") || lower.contains("mytarget")
                                                || lower.contains("startapp") || lower.contains("facebook")
                                                || lower.contains(".ads") || lower.contains("admob")
                                                || lower.contains("gms/ads") || lower.contains("mopub")
                                                || lower.contains("youmi") || lower.contains("umeng")
                                                || lower.contains("baidu") || lower.contains("xiaomi")
                                                || lower.contains("qq.e") || lower.contains(".ad.")
                                                || lower.contains("admanager") || lower.contains("adrequest")
                                                || lower.contains("splashad") || lower.contains("nativead")
                                                || lower.contains("adunit") || lower.contains("adlistener")) {
                                            sb.append(f.getFileName()).append(": ").append(line).append("\n");
                                        }
                                    }
                                } catch (Exception ignored) {}
                            });
                }
            }
        }
        if (sb.length() == 0) sb.append("(no ad-related smali found in sample)");
        return sb.toString();
    }

    private String readManifest(Path base) throws Exception {
        Path m = base.resolve("AndroidManifest.xml");
        if (Files.exists(m)) {
            return new String(Files.readAllBytes(m), StandardCharsets.UTF_8);
        }
        return "(no manifest)";
    }

    private String collectLayoutSummary(Path base) throws Exception {
        StringBuilder sb = new StringBuilder();
        Path layoutDir = base.resolve("res/layout");
        if (Files.isDirectory(layoutDir)) {
            try (Stream<Path> stream = Files.list(layoutDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".xml"))
                        .forEach(f -> {
                            try {
                                List<String> lines = Files.readAllLines(f);
                                for (String line : lines) {
                                    String lower = line.toLowerCase();
                                    if (lower.contains("adview") || lower.contains("ad")
                                            || lower.contains("banner") || lower.contains("interstitial")) {
                                        sb.append(f.getFileName()).append(": ").append(line).append("\n");
                                    }
                                }
                            } catch (Exception ignored) {}
                        });
            }
        }
        if (sb.length() == 0) sb.append("(no ad-related layouts found)");
        return sb.toString();
    }

    private String buildSystemPrompt() {
        return "You are an expert Android reverse engineer specializing in removing ad SDKs " +
                "from decompiled APK files (smali code). You analyze decompiled APK contents " +
                "and produce precise patching instructions.\n\n" +
                "## Ad SDK Detection Patterns (from Python ad removal tool):\n" +
                "### Known Ad SDK Packages to identify and remove:\n" +
                "- com.google.android.gms.ads (Google AdMob)\n" +
                "- com.facebook.ads (Facebook Audience Network)\n" +
                "- com.unity3d.ads (Unity Ads)\n" +
                "- com.applovin.* (AppLovin)\n" +
                "- com.ironsource.* (ironSource)\n" +
                "- com.vungle.* (Vungle)\n" +
                "- com.mbridge.msdk.* (Mintegral)\n" +
                "- com.adcolony.* (AdColony)\n" +
                "- com.chartboost.* (Chartboost)\n" +
                "- com.inmobi.* (InMobi)\n" +
                "- com.bytedance.sdk.openadsdk.* (Pangle/TikTok)\n" +
                "- com.my.target.* (MyTarget)\n" +
                "- com.startapp.* (StartApp)\n" +
                "- com.fyber.* (Fyber)\n" +
                "- com.qq.e.* (Tencent GDT)\n" +
                "- com.baidu.* (Baidu ads)\n" +
                "- com.xiaomi.* (Xiaomi ads)\n" +
                "- com.umeng.* (Umeng)\n" +
                "- com.mopub.* (MoPub)\n" +
                "- com.youmi.* (Youmi)\n\n" +
                "### Manifest patterns to remove:\n" +
                "- <activity> entries with ad SDK package names\n" +
                "- <service> entries with ad SDK package names\n" +
                "- <receiver> entries with ad SDK package names\n" +
                "- <meta-data> entries with ad-related names\n" +
                "- Permissions like com.google.android.gms.permission.AD_ID\n\n" +
                "### Smali patching strategies:\n" +
                "1. DELETE entire .smali files that belong to ad SDK packages\n" +
                "2. REPLACE ad invocation calls (invoke-*) with nop instructions\n" +
                "3. REMOVE ad-related fields (.field) from remaining smali files\n" +
                "4. REPLACE UUID/APP_ID strings with zeros: 32-char hex → 000...000\n" +
                "5. REPLACE http:// and https:// ad URLs with hhp://\n" +
                "6. PATCH isGooglePlayServicesAvailable() to return 0 (SUCCESS)\n\n" +
                "### Baidu-specific patterns:\n" +
                "- BDAPPID / BDAPPKEY meta-data entries should be removed\n" +
                "- com.baidu.appx.BDBannerAd.setAdContext calls should be patched\n\n" +
                "Output ONLY valid JSON, no markdown or explanation outside the JSON.";
    }

    private String buildUserPrompt(String smali, String manifest, String layouts) {
        return "Analyze the following decompiled APK content and produce a list of " +
                "patches to remove all ad SDKs.\n\n" +
                "Respond with a JSON object:\n" +
                "{\n" +
                "  \"summary\": \"brief description of what was found\",\n" +
                "  \"patches\": [\n" +
                "    {\"action\": \"delete_smali\", \"path\": \"smali/com/ad/sdk/AdView.smali\", \"reason\": \"ad sdk class\"},\n" +
                "    {\"action\": \"remove_from_manifest\", \"target\": \"com.google.android.gms.ads.AdActivity\", \"reason\": \"admob activity\"},\n" +
                "    {\"action\": \"patch_smali\", \"path\": \"smali/com/app/MainActivity.smali\", \"target\": \"invoke-.*Lcom/google/android/gms/ads.*\", \"replacement\": \"nop\", \"reason\": \"remove ad call\"},\n" +
                "    {\"action\": \"delete_dir\", \"path\": \"smali/com/google/android/gms/ads\", \"reason\": \"entire admob package\"},\n" +
                "    {\"action\": \"remove_from_manifest_permission\", \"target\": \"com.google.android.gms.permission.AD_ID\", \"reason\": \"ad permission\"}\n" +
                "  ]\n" +
                "}\n\n" +
                "=== AndroidManifest.xml (first 300 lines) ===\n" + truncate(manifest, 8000) + "\n\n" +
                "=== Ad-related Layout XML (first 200 lines) ===\n" + truncate(layouts, 4000) + "\n\n" +
                "=== Ad-related Smali Code (first 500 lines) ===\n" + truncate(smali, 10000);
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.isEmpty()) return "(empty)";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "\n... (truncated, " + (s.length() - maxLen) + " more chars)";
    }

    private String callDeepSeek(String systemPrompt, String userPrompt, String apiKey) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        JSONObject body = new JSONObject();
        body.put("model", "deepseek-chat");
        body.put("temperature", 0.1);
        body.put("max_tokens", 4096);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.put(userMsg);

        body.put("messages", messages);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("DeepSeek API error " + code + ": " + readAll(conn.getErrorStream()));
        }

        String respText = readAll(conn.getInputStream());
        JSONObject resp = new JSONObject(respText);
        return resp.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    private String readAll(java.io.InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        r.close();
        return sb.toString();
    }

    private AiResult parseResponse(String response, Path decompiledDir) {
        AiResult result = new AiResult();
        try {
            // Extract JSON from response (may have markdown fences)
            String json = response;
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                if (json.contains("```")) json = json.substring(0, json.indexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3);
                if (json.contains("```")) json = json.substring(0, json.indexOf("```"));
            }
            json = json.trim();

            JSONObject obj = new JSONObject(json);
            result.summary = obj.optString("summary", "");
            result.success = true;

            JSONArray patches = obj.optJSONArray("patches");
            if (patches != null) {
                for (int i = 0; i < patches.length(); i++) {
                    JSONObject p = patches.getJSONObject(i);
                    AiPatch patch = new AiPatch();
                    patch.action = p.optString("action", "");
                    patch.filePath = p.optString("path", "");
                    patch.target = p.optString("target", "");
                    patch.replacement = p.optString("replacement", "");
                    patch.reason = p.optString("reason", "");
                    result.patches.add(patch);
                }
            }
        } catch (Exception e) {
            result.success = false;
            result.error = "Failed to parse AI response: " + e.getMessage() + "\nRaw: " + response;
        }
        return result;
    }
}
