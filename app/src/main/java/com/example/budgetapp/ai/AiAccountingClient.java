package com.example.budgetapp.ai;

import android.content.Context;
import android.util.Base64;

import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.PromptManager;

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
        String reply = chatWithEndpoint(
                config.textModel,
                config.getEffectiveTextBaseUrl(),
                config.getEffectiveTextApiKey(),
                buildSystemPrompt(context),
                new JSONObject().put("role", "user").put("content", text)
        );
        return parseDrafts(context, reply);
    }

    public List<TransactionDraft> parseVisionImage(Context context, String prompt, byte[] imageBytes, String mimeType) throws Exception {
        ensureVisionReady();
        String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        JSONArray content = new JSONArray();

        boolean isDeepSeekOcr = config.visionModel != null &&
                config.visionModel.toLowerCase().contains("deepseek-ocr");

        if (isDeepSeekOcr) {
            // DeepSeek-OCR: 图片在前，使用特殊 OCR 提示词
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64);
            content.put(new JSONObject().put("type", "image_url").put("image_url", imageUrl));
            // 先用 OCR 提取文字，再附加用户的记账提示
            content.put(new JSONObject().put("type", "text").put("text",
                    "<image>\n<|grounding|>OCR this image.\n\n" + prompt));
        } else {
            content.put(new JSONObject().put("type", "text").put("text", prompt));
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64);
            imageUrl.put("detail", "high");
            content.put(new JSONObject().put("type", "image_url").put("image_url", imageUrl));
        }

        String reply = chatWithEndpoint(
                config.visionModel,
                config.getEffectiveVisionBaseUrl(),
                config.getEffectiveVisionApiKey(),
                buildSystemPrompt(context),
                new JSONObject().put("role", "user").put("content", content)
        );
        return parseDrafts(context, reply);
    }

    public String transcribeAudio(byte[] audioBytes, String fileName, String mimeType) throws Exception {
        ensureAudioReady();
        String baseUrl = config.getEffectiveAudioBaseUrl();
        String apiKey = config.getEffectiveAudioApiKey();
        HttpURLConnection connection = openConnectionWithEndpoint(resolveUrlWithBase(baseUrl, "/audio/transcriptions"), apiKey, false);
        String boundary = "----BudgetAppBoundary" + System.currentTimeMillis();
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);

        try (DataOutputStream stream = new DataOutputStream(connection.getOutputStream())) {
            // SiliconFlow API 只需要 model 和 file 两个参数
            writeFormField(stream, boundary, "model", config.audioModel);
            writeFileField(stream, boundary, "file", fileName, mimeType, audioBytes);
            stream.writeBytes("--" + boundary + "--\r\n");
            stream.flush();
        }

        int responseCode = connection.getResponseCode();
        String response = readResponse(connection, responseCode);
        
        // 添加日志：打印完整响应用于调试
        android.util.Log.d("AiAccountingClient", "Audio API Response Code: " + responseCode);
        android.util.Log.d("AiAccountingClient", "Audio API Response Body: " + response);
        
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException(buildHttpError(responseCode, response));
        }

        // 尝试解析响应
        try {
            JSONObject object = new JSONObject(response);
            String text = object.optString("text", "").trim();
            
            if (text.isEmpty()) {
                // 如果 text 字段为空，打印完整的 JSON 对象用于调试
                android.util.Log.e("AiAccountingClient", "Empty text field. Full response: " + response);
                throw new IOException("音频转写成功，但没有返回文字结果。响应内容：" + response);
            }
            
            return text;
        } catch (org.json.JSONException e) {
            // JSON 解析失败，可能响应格式不是预期的 JSON
            android.util.Log.e("AiAccountingClient", "Failed to parse JSON response: " + response, e);
            throw new IOException("无法解析音频转写响应。响应内容：" + response);
        }
    }

    public TestResult testConfiguration(Context context) {
        ensureConfig();
        TestResult result = new TestResult();

        if (!config.isTextReady()) {
            result.textMessage = "未配置文本模型";
        } else {
            try {
                chatWithEndpoint(
                        config.textModel,
                        config.getEffectiveTextBaseUrl(),
                        config.getEffectiveTextApiKey(),
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
                // 使用 8x8 红色 PNG 作为测试图片（比 1x1 更兼容各模型）
                byte[] testPng = Base64.decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAADklEQVQI12P4z8BQDwAEgAF/QualzQAAAABJRU5ErkJggg==",
                        Base64.DEFAULT
                );
                probeVisionWithEndpoint("这是一张测试图片，请回复 ok。", testPng, "image/png",
                        config.getEffectiveVisionBaseUrl(), config.getEffectiveVisionApiKey());
                result.visionOk = true;
                result.visionMessage = "可用";
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                // 只有明确的配置错误才判定为不可用
                if (msg.contains("鉴权失败") || msg.contains("401")) {
                    result.visionMessage = "API Key 无效，请检查配置";
                } else if (msg.contains("接口地址或模型不存在") || msg.contains("404")) {
                    result.visionMessage = "模型不存在，请检查模型名称";
                } else {
                    // 其他错误（如图片太小、格式问题等）不代表模型不支持视觉
                    result.visionOk = true;
                    result.visionMessage = "可用";
                }
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
        return chatWithEndpoint(model, config.baseUrl, config.apiKey, systemPrompt, userMessage);
    }

    private String chatWithEndpoint(String model, String baseUrl, String apiKey, String systemPrompt, JSONObject userMessage) throws Exception {
        HttpURLConnection connection = openConnectionWithEndpoint(resolveUrlWithBase(baseUrl, "/chat/completions"), apiKey, true);
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
        return probeVisionWithEndpoint(prompt, imageBytes, mimeType, config.baseUrl, config.apiKey);
    }

    private String probeVisionWithEndpoint(String prompt, byte[] imageBytes, String mimeType, String baseUrl, String apiKey) throws Exception {
        String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        JSONArray content = new JSONArray();

        // DeepSeek-OCR 需要特殊的提示词格式
        boolean isDeepSeekOcr = config.visionModel != null &&
                config.visionModel.toLowerCase().contains("deepseek-ocr");

        if (isDeepSeekOcr) {
            // DeepSeek-OCR: 图片在前，特殊提示词在后
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64);
            content.put(new JSONObject().put("type", "image_url").put("image_url", imageUrl));
            content.put(new JSONObject().put("type", "text").put("text", "<image>\n<|grounding|>OCR this image."));
        } else {
            content.put(new JSONObject().put("type", "text").put("text", prompt));
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64);
            imageUrl.put("detail", "low");
            content.put(new JSONObject().put("type", "image_url").put("image_url", imageUrl));
        }

        String systemPrompt = isDeepSeekOcr ? "" : "你是视觉能力测试助手，读取图片后只回复 ok。";
        return chatWithEndpoint(
                config.visionModel,
                baseUrl,
                apiKey,
                systemPrompt,
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
        return openConnectionWithEndpoint(url, config.apiKey, jsonContentType);
    }

    private HttpURLConnection openConnectionWithEndpoint(String url, String apiKey, boolean jsonContentType) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        if (jsonContentType) {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        return connection;
    }

    private String resolveUrl(String endpoint) throws Exception {
        return resolveUrlWithBase(config.baseUrl, endpoint);
    }

    private String resolveUrlWithBase(String baseUrl, String endpoint) throws Exception {
        String raw = baseUrl == null ? "" : baseUrl.trim();
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

    public String buildSystemPrompt(Context context) {
        StringBuilder builder = new StringBuilder();
        
        // Part 1: 静态规则（用户可编辑部分）
        String staticPrompt;
        if (PromptManager.hasCustomPrompt(context)) {
            // 使用自定义提示词
            staticPrompt = PromptManager.getCustomPrompt(context);
        } else {
            // 使用默认静态模板
            staticPrompt = PromptTemplate.buildEditableTemplate();
        }
        builder.append(staticPrompt);
        
        // Part 2: 动态内容（自动生成，对所有提示词都添加）
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());

        // 添加当前时间
        builder.append("\n\n当前系统时间是：").append(currentTime).append("。");

        // 添加分类信息
        builder.append("\n\n支出分类与二级分类：");
        appendCategories(builder, buildPromptCategories(context, false), context);

        builder.append("\n\n收入分类与二级分类：");
        appendCategories(builder, buildPromptCategories(context, true), context);

        // 添加资产账户信息
        builder.append("\n\n可用资产账户：");
        appendAssets(builder, context);

        // 添加 JSON 示例
        builder.append("\n\n只返回 JSON 数组。示例：");
        builder.append("[");
        builder.append("{\"type\":0,\"amount\":5.00,\"category\":\"购物\",\"subCategory\":\"便利店\",\"note\":\"美宜佳\",\"asset\":\"\",\"time\":\"")
                .append(currentTime)
                .append("\"},");
        builder.append("{\"type\":0,\"amount\":19.90,\"category\":\"餐饮\",\"subCategory\":\"快餐\",\"note\":\"麦当劳\",\"asset\":\"\",\"time\":\"")
                .append(currentTime)
                .append("\"}");
        builder.append("]");

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
                || normalized.contains("invalid audio")
                || normalized.contains("没有返回文字结果")
                || normalized.contains("empty text");
    }

    private void ensureConfig() {
        if (config == null) {
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
