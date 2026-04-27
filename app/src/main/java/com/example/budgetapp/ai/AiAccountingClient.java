package com.example.budgetapp.ai;

import android.content.Context;
import android.util.Base64;

import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AiAccountingClient {
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 30000;

    private AiConfig config;

    public static class TestResult {
        public boolean textOk;
        public boolean visionOk;
        public boolean audioOk;
        public String textMessage = "";
        public String visionMessage = "";
        public String audioMessage = "";

        public String summary() {
            return "文本模型: " + textMessage
                    + "\n视觉模型: " + visionMessage
                    + "\n音频模型: " + audioMessage;
        }
    }

    public void setConfig(AiConfig config) {
        this.config = config;
    }

    public List<TransactionDraft> parseText(Context context, String text) throws Exception {
        ensureTextReady();
        String reply = chat(
                config.textModel,
                buildSystemPrompt(context),
                new JSONObject().put("role", "user").put("content", text)
        );
        return parseDrafts(context, reply);
    }

    public List<TransactionDraft> parseVisionImage(Context context, String prompt, byte[] imageBytes, String mimeType) throws Exception {
        ensureVisionReady();
        String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", prompt));
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64);
        content.put(new JSONObject().put("type", "image_url").put("image_url", imageUrl));
        String reply = chat(
                config.visionModel,
                buildSystemPrompt(context),
                new JSONObject().put("role", "user").put("content", content)
        );
        return parseDrafts(context, reply);
    }

    public String transcribeAudio(byte[] audioBytes, String fileName, String mimeType) throws Exception {
        ensureAudioReady();
        HttpURLConnection connection = openConnection(resolveUrl("/audio/transcriptions"), false);
        String boundary = "----BudgetAppBoundary" + System.currentTimeMillis();
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);

        try (DataOutputStream stream = new DataOutputStream(connection.getOutputStream())) {
            writeFormField(stream, boundary, "model", config.audioModel);
            writeFormField(stream, boundary, "language", "zh");
            writeFormField(stream, boundary, "response_format", "json");
            writeFileField(stream, boundary, "file", fileName, mimeType, audioBytes);
            stream.writeBytes("--" + boundary + "--\r\n");
            stream.flush();
        }

        int responseCode = connection.getResponseCode();
        String response = readResponse(connection, responseCode);
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException(buildHttpError(responseCode, response));
        }

        JSONObject object = new JSONObject(response);
        String text = object.optString("text", "").trim();
        if (text.isEmpty()) {
            throw new IOException("音频转写成功，但没有返回文字结果。");
        }
        return text;
    }

    public TestResult testConfiguration(Context context) {
        ensureConfig();
        TestResult result = new TestResult();

        if (!config.isTextReady()) {
            result.textMessage = "未配置文本模型";
        } else {
            try {
                chat(
                        config.textModel,
                        "你是接口测试助手，只返回 ok。",
                        new JSONObject().put("role", "user").put("content", "hi")
                );
                result.textOk = true;
                result.textMessage = "可用";
            } catch (Exception e) {
                result.textMessage = e.getMessage();
            }
        }

        if (!config.isVisionReady()) {
            result.visionMessage = "未配置视觉模型";
        } else {
            try {
                byte[] tinyPng = Base64.decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9p+1tWwAAAAASUVORK5CYII=",
                        Base64.DEFAULT
                );
                probeVision("请读取这张图片，并且只回复 ok。", tinyPng, "image/png");
                result.visionOk = true;
                result.visionMessage = "可用";
            } catch (Exception e) {
                result.visionMessage = classifyCapabilityFailure(e.getMessage(), "模型暂不支持图片识别");
            }
        }

        if (!config.isAudioReady()) {
            result.audioMessage = "未配置音频模型";
        } else {
            try {
                transcribeAudio(buildSilentWav(), "sample.wav", "audio/wav");
                result.audioOk = true;
                result.audioMessage = "可用";
            } catch (Exception e) {
                if (looksLikeWorkingAudioEndpoint(e.getMessage())) {
                    result.audioOk = true;
                    result.audioMessage = "可用";
                } else {
                    result.audioMessage = classifyCapabilityFailure(e.getMessage(), "模型暂不支持音频转写");
                }
            }
        }

        config.textTestOk = result.textOk;
        config.visionTestOk = result.visionOk;
        config.audioTestOk = result.audioOk;
        config.save(context);
        return result;
    }

    private String chat(String model, String systemPrompt, JSONObject userMessage) throws Exception {
        HttpURLConnection connection = openConnection(resolveUrl("/chat/completions"), true);
        connection.setDoOutput(true);

        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", 0.1);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(userMessage);
        payload.put("messages", messages);

        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.getOutputStream().write(bytes);
        connection.getOutputStream().flush();
        connection.getOutputStream().close();

        int responseCode = connection.getResponseCode();
        String response = readResponse(connection, responseCode);
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException(buildHttpError(responseCode, response));
        }

        JSONObject root = new JSONObject(response);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IOException("AI 接口返回成功，但没有 choices。");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) {
            throw new IOException("AI 接口返回成功，但缺少 message。");
        }
        return flattenContent(message.opt("content"));
    }

    private String probeVision(String prompt, byte[] imageBytes, String mimeType) throws Exception {
        String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", prompt));
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64);
        content.put(new JSONObject().put("type", "image_url").put("image_url", imageUrl));
        return chat(
                config.visionModel,
                "你是视觉能力测试助手，读取图片后只回复 ok。",
                new JSONObject().put("role", "user").put("content", content)
        );
    }

    private List<TransactionDraft> parseDrafts(Context context, String responseText) throws Exception {
        String coreJson = extractJsonBlock(responseText);
        if (coreJson.isEmpty()) {
            throw new IOException("AI 没有返回可识别的账单 JSON。原始回复：" + responseText);
        }

        List<TransactionDraft> drafts = new ArrayList<>();
        if (coreJson.startsWith("[")) {
            JSONArray array = new JSONArray(coreJson);
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject) {
                    drafts.add(TransactionDraftMapper.fromJson(context, (JSONObject) item));
                }
            }
        } else {
            JSONObject object = new JSONObject(coreJson);
            JSONArray array = object.optJSONArray("transactions");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.get(i);
                    if (item instanceof JSONObject) {
                        drafts.add(TransactionDraftMapper.fromJson(context, (JSONObject) item));
                    }
                }
            } else {
                drafts.add(TransactionDraftMapper.fromJson(context, object));
            }
        }

        List<TransactionDraft> validDrafts = new ArrayList<>();
        for (TransactionDraft draft : drafts) {
            if (draft.amount > 0d) {
                validDrafts.add(draft);
            }
        }
        if (validDrafts.isEmpty()) {
            throw new IOException("AI 返回了结果，但没有提取到有效金额。原始回复：" + responseText);
        }
        return validDrafts;
    }

    private HttpURLConnection openConnection(String url, boolean jsonContentType) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Authorization", "Bearer " + config.apiKey.trim());
        if (jsonContentType) {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        return connection;
    }

    private String resolveUrl(String endpoint) throws Exception {
        String raw = config.baseUrl == null ? "" : config.baseUrl.trim();
        if (raw.isEmpty()) {
            throw new IOException("请先填写 Base URL。");
        }
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = "https://" + raw;
        }
        URI uri = new URI(raw);
        String path = uri.getPath() == null ? "" : uri.getPath().trim();
        String normalizedPath;
        if (path.endsWith("/chat/completions")) {
            normalizedPath = path.substring(0, path.length() - "/chat/completions".length()) + endpoint;
        } else if (path.endsWith("/audio/transcriptions")) {
            normalizedPath = path.substring(0, path.length() - "/audio/transcriptions".length()) + endpoint;
        } else if (path.endsWith("/v1")) {
            normalizedPath = path + endpoint;
        } else if (path.contains("/v1/")) {
            normalizedPath = path;
        } else if (path.isEmpty() || "/".equals(path)) {
            normalizedPath = "/v1" + endpoint;
        } else {
            normalizedPath = path + (path.endsWith("/") ? "v1" + endpoint : "/v1" + endpoint);
        }
        URI normalizedUri = new URI(
                uri.getScheme(),
                uri.getUserInfo(),
                uri.getHost(),
                uri.getPort(),
                normalizedPath,
                uri.getQuery(),
                uri.getFragment()
        );
        return normalizedUri.toString();
    }

    private String buildSystemPrompt(Context context) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是一个中文记账助手，只能返回 JSON，不要输出 markdown、解释或代码块。");
        builder.append("请始终返回 JSON 数组。每一项必须包含：type、amount、category、subCategory、note、asset。");
        builder.append("type 只能是 0 或 1。0 表示支出，1 表示收入。amount 必须是纯数字，不要带货币符号。");
        builder.append("分类必须优先使用给定的项目分类；如果命中了二级分类，category 必须写对应一级分类，subCategory 必须写对应二级分类。");
        builder.append("如果没有适合的分类，统一使用“其他”，subCategory 留空。");
        builder.append("asset 必须尽量从给定资产列表中选择最合适的账户名称；如果无法判断，可以留空。");
        builder.append("\n\n支出分类与二级分类：");
        appendCategories(builder, buildPromptCategories(context, false), context);
        builder.append("\n\n收入分类与二级分类：");
        appendCategories(builder, buildPromptCategories(context, true), context);
        builder.append("\n\n可用资产账户：");
        appendAssets(builder, context);
        builder.append("\n\n只返回 JSON 数组，例如：");
        builder.append("[{\"type\":0,\"amount\":25,\"category\":\"餐饮\",\"subCategory\":\"午餐\",\"note\":\"午饭\",\"asset\":\"微信余额\"}]");
        return builder.toString();
    }

    private void appendCategories(StringBuilder builder, List<String> categories, Context context) {
        for (String category : categories) {
            builder.append("\n- ").append(category).append(": ");
            List<String> subCategories = CategoryManager.getSubCategories(context, category);
            if (subCategories == null || subCategories.isEmpty()) {
                builder.append("[]");
            } else {
                builder.append(subCategories.toString());
            }
        }
    }

    private List<String> buildPromptCategories(Context context, boolean income) {
        List<String> source = income
                ? CategoryManager.getIncomeCategories(context)
                : CategoryManager.getExpenseCategories(context);
        List<String> categories = new ArrayList<>();
        if (source != null) {
            for (String category : source) {
                if (category != null && !category.trim().isEmpty()) {
                    categories.add(category.trim());
                }
            }
        }
        if (!categories.contains("其他")) {
            categories.add("其他");
        }
        return categories;
    }

    private void appendAssets(StringBuilder builder, Context context) {
        List<AssetAccount> allAssets = AppDatabase.getDatabase(context).assetAccountDao().getAllAssetsSync();
        int defaultAssetId = new AssistantConfig(context).getDefaultAssetId();
        boolean appended = false;
        if (allAssets != null) {
            for (AssetAccount asset : allAssets) {
                if (asset == null || asset.name == null || asset.name.trim().isEmpty()) {
                    continue;
                }
                if (asset.type != 0 && asset.type != 1 && asset.type != 2) {
                    continue;
                }
                appended = true;
                builder.append("\n- ").append(asset.name.trim())
                        .append(" (").append(getAssetTypeLabel(asset.type)).append(")");
                if (asset.id == defaultAssetId) {
                    builder.append(" [默认资产]");
                }
            }
        }
        if (!appended) {
            builder.append("\n- 无资产账户");
        }
    }

    private String getAssetTypeLabel(int type) {
        if (type == 1) {
            return "负债";
        }
        if (type == 2) {
            return "借出";
        }
        return "资产";
    }

    private String flattenContent(Object content) {
        if (content instanceof String) {
            return ((String) content).trim();
        }
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null && "text".equals(object.optString("type"))) {
                    builder.append(object.optString("text"));
                }
            }
            return builder.toString().trim();
        }
        return String.valueOf(content).trim();
    }

    private String extractJsonBlock(String content) {
        int arrayStart = content.indexOf('[');
        int arrayEnd = content.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return content.substring(arrayStart, arrayEnd + 1).trim();
        }
        int objStart = content.indexOf('{');
        int objEnd = content.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            return content.substring(objStart, objEnd + 1).trim();
        }
        return "";
    }

    private static void writeFormField(DataOutputStream stream, String boundary, String name, String value) throws IOException {
        stream.writeBytes("--" + boundary + "\r\n");
        stream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        stream.write(value.getBytes(StandardCharsets.UTF_8));
        stream.writeBytes("\r\n");
    }

    private static void writeFileField(DataOutputStream stream, String boundary, String name, String fileName, String mimeType, byte[] bytes) throws IOException {
        stream.writeBytes("--" + boundary + "\r\n");
        stream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n");
        stream.writeBytes("Content-Type: " + mimeType + "\r\n\r\n");
        stream.write(bytes);
        stream.writeBytes("\r\n");
    }

    private static byte[] buildSilentWav() {
        return Base64.decode(
                "UklGRlQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YTAAAAAA",
                Base64.DEFAULT
        );
    }

    private static String readResponse(HttpURLConnection connection, int responseCode) throws IOException {
        InputStream stream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static String buildHttpError(int responseCode, String response) {
        String detail = extractErrorDetail(response);
        if (responseCode == 401) {
            return "鉴权失败，请检查 API Key。";
        }
        if (responseCode == 404) {
            return "接口地址或模型不存在。";
        }
        if (responseCode == 429) {
            return "请求过于频繁或额度不足。";
        }
        return detail.isEmpty() ? ("HTTP " + responseCode) : ("HTTP " + responseCode + "：" + detail);
    }

    private static String extractErrorDetail(String response) {
        try {
            JSONObject object = new JSONObject(response);
            Object error = object.opt("error");
            if (error instanceof JSONObject) {
                JSONObject errorObject = (JSONObject) error;
                return firstNonEmpty(
                        errorObject.optString("message", ""),
                        errorObject.optString("code", ""),
                        response
                );
            }
            return firstNonEmpty(object.optString("message", ""), response);
        } catch (Exception ignored) {
            return response == null ? "" : response;
        }
    }

    private static String classifyCapabilityFailure(String message, String fallback) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("image") || normalized.contains("vision")) {
            return "模型暂不支持图片识别";
        }
        if (normalized.contains("audio") || normalized.contains("transcription") || normalized.contains("speech")) {
            return "模型暂不支持音频转写";
        }
        return (message == null || message.trim().isEmpty()) ? fallback : message.trim();
    }

    private static boolean looksLikeWorkingAudioEndpoint(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("audio too short")
                || normalized.contains("too short")
                || normalized.contains("no speech")
                || normalized.contains("silence")
                || normalized.contains("empty audio")
                || normalized.contains("invalid audio");
    }

    private void ensureConfig() {
        if (config == null || !config.hasSharedConnection()) {
            throw new IllegalStateException("AI 配置不完整。");
        }
    }

    private void ensureTextReady() {
        ensureConfig();
        if (!config.isTextReady()) {
            throw new IllegalStateException("未配置文本模型。");
        }
    }

    private void ensureVisionReady() {
        ensureConfig();
        if (!config.isVisionReady()) {
            throw new IllegalStateException("未配置视觉模型。");
        }
    }

    private void ensureAudioReady() {
        ensureConfig();
        if (!config.isAudioReady()) {
            throw new IllegalStateException("未配置音频模型。");
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
