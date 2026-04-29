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

        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());

        builder.append("你是一个中文记账助手，只能返回 JSON 数组，不要输出 markdown、解释、代码块或多余文字。");
        builder.append("当前系统时间是：").append(currentTime).append("。");

        builder.append("\n\n【JSON 输出规则】");
        builder.append("1. 必须始终返回 JSON 数组。");
        builder.append("2. 数组里的每一项代表一笔独立账单。");
        builder.append("3. 每一项必须包含：type、amount、category、subCategory、note、asset、time。");
        builder.append("4. type 只能是 0 或 1。0 表示支出，1 表示收入。");
        builder.append("5. amount 必须是正数纯数字，不要带负号、货币符号、单位或逗号。");
        builder.append("6. 不要返回 null。无法判断的字符串字段使用空字符串。");

        builder.append("\n\n【多账单识别规则，必须严格遵守】");
        builder.append("1. 如果截图中出现多条交易记录、账单列表、订单列表、收支明细列表，每一行或每一个卡片都必须识别为一条独立账单。");
        builder.append("2. 不要把多条账单金额相加，不要合并成一条账单。");
        builder.append("3. 不要输出汇总金额，不要输出自己计算出来的合计金额。");
        builder.append("4. 列表页中每条记录右侧或附近的黑色金额通常是该条账单的实际金额。");
        builder.append("5. 灰色划线金额、原价、优惠前金额不能作为 amount。");
        builder.append("6. 如果连续多条记录商户相同，也必须按时间和金额分别生成多条 JSON。");
        builder.append("7. 列表中金额前面的负号表示支出，type 为 0，amount 输出正数。");
        builder.append("8. 列表中金额前面的正号、收入、退款、到账表示收入，type 为 1，amount 输出正数。");
        builder.append("9. 如果截图中能看到几条完整交易记录，就必须返回几条 JSON 对象。");
        builder.append("10. 只识别截图中可见的完整账单，不要猜测屏幕外的账单。");

        builder.append("\n\n【金额识别规则，必须严格遵守】");
        builder.append("1. amount 必须是整笔订单的最终实际支付金额，而不是原价、优惠前金额、优惠金额或单品金额。");
        builder.append("2. 最高优先级字段：实付、实付款、实际支付、支付金额、已支付、付款金额、应付、合计、订单金额、支付成功页面的大号金额。");
        builder.append("3. 如果页面顶部或中间有醒目的大号金额，并且当前状态是支付成功、交易成功、订单完成，该大号金额通常就是实际支付金额。");
        builder.append("4. 如果大号金额带负号，例如 -19.90，表示支出 19.90，amount 应填写 19.90，type 为 0。");
        builder.append("5. 不要把原价、划线价、市场价、商品原价、优惠前金额作为 amount。");
        builder.append("6. 如果出现“原价 ¥29.90”和醒目的“-19.90”，amount 应为 19.90，不是 29.90。");
        builder.append("7. 不要把优惠金额、折扣金额、红包、优惠券、满减、共减、已减、立减、饭卡抵扣、金币抵扣、淘金币抵扣、会员抵扣作为 amount。");
        builder.append("8. 外卖、电商、超市、团购、打车等订单截图中，优先读取页面底部或结算区域的“实付/实付款/合计/应付”金额。");
        builder.append("9. 不要把商品旁边的“到手价”“到手”“券后价”“预估到手”“单品价格”“商品价格”当作整笔订单金额。");
        builder.append("10. 如果同时出现“共减¥xx”和另一个金额，应忽略“共减¥xx”，选择后面的实际支付金额。");
        builder.append("11. 如果出现类似“共减¥25.08 ¥4.86”，amount 应为 4.86，不是 25.08。");
        builder.append("12. 如果出现类似“实付款 共减¥51.74 ¥22.66”，amount 应为 22.66，不是 51.74。");
        builder.append("13. 如果出现类似“到手¥2.66”和“合计 ¥4.86”，amount 应为 4.86，不是 2.66。");
        builder.append("14. 如果一条记录显示黑色金额 -5.00，灰色划线金额 15.00，amount 应为 5.00，不是 15.00。");
        builder.append("15. 如果出现运费、打包费、餐盒费、配送费等费用，最终金额应以合计/实付为准，不要只取单品金额。");
        builder.append("16. 如果无法找到实付、合计、应付或支付成功大号金额，再选择最能代表整笔消费的总金额。");
        builder.append("17. 如果页面同时出现商品旁边的“实付¥x”和价格明细区域的“实付款¥y”，必须选择价格明细区域或结算区域的“实付款¥y”作为整笔订单金额。");
        builder.append("18. 商品行里的“实付”“到手价”“单品实付”只表示单个商品优惠后的价格，不代表整单金额。");
        builder.append("19. 如果页面有打包费、配送费、运费、服务费等额外费用，amount 必须包含这些费用后的最终实付款。");
        builder.append("20. 如果出现类似商品实付¥0.5、打包费¥1.5、配送费¥1、实付款¥3，amount 应为 3，不是 0.5。");
        builder.append("21. 价格明细、合计、实付款、应付款区域的金额优先级高于商品列表区域的金额。");

        builder.append("\n\n【收支类型规则】");
        builder.append("1. 购物、餐饮、外卖、交通、充值、缴费、付款、消费、转账给别人，type 为 0。");
        builder.append("2. 退款、收款、工资、奖金、转入到账、报销到账，type 为 1。");
        builder.append("3. 如果截图是订单完成、交易成功、支付成功、付款成功，通常是支出，type 为 0。");
        builder.append("4. 如果截图明确包含退款成功、退款到账、已退款，则 type 为 1。");

        builder.append("\n\n【时间识别规则】");
        builder.append("1. time 格式必须严格为 yyyy-MM-dd HH:mm。");
        builder.append("2. 如果截图中有订单时间、支付时间、交易时间、付款时间、创建时间、下单时间，优先使用这些时间。");
        builder.append("3. 不要把配送时间、预计送达时间、承诺送达时间、发货时间当作账单发生时间。");
        builder.append("4. 如果截图只有月日和时分，例如 4月28日 17:49，则年份使用当前系统时间中的年份。");
        builder.append("5. 如果用户没有明确说明账单发生时间，截图中也没有可用时间，则使用当前系统时间。");

        builder.append("\n\n【分类规则】");
        builder.append("1. 分类必须优先使用给定的项目分类。");
        builder.append("2. 如果命中了二级分类，category 必须写对应一级分类，subCategory 必须写对应二级分类。");
        builder.append("3. 如果没有适合的分类，category 使用“其他”，subCategory 留空。");
        builder.append("4. 外卖、餐厅、饭、奶茶、咖啡、早餐、午餐、晚餐优先归为餐饮相关分类。");
        builder.append("5. 便利店、超市、商场、淘宝、京东、拼多多、天猫优先归为购物相关分类，除非商品明显属于餐饮、交通、医疗等更具体分类。");

        builder.append("\n\n【备注规则】");
        builder.append("1. note 应简洁描述这笔账，例如商家名、商品名或用途。");
        builder.append("2. 不要把订单号、交易单号、手机号、条形码、无关广告、配送承诺写入 note。");
        builder.append("3. 多账单列表中，如果只能看到商户名，则 note 使用商户名。");

        builder.append("\n\n【资产账户规则】");
        builder.append("1. asset 必须尽量从给定资产列表中选择最合适的账户名称。");
        builder.append("2. 如果截图或用户描述中出现微信、支付宝、银行卡、余额、花呗、信用卡等支付方式，应匹配最接近的资产账户。");
        builder.append("3. 如果出现具体银行卡尾号或信用卡名称，应优先匹配对应资产账户。");
        builder.append("4. 如果无法判断，可以留空。");

        builder.append("\n\n支出分类与二级分类：");
        appendCategories(builder, buildPromptCategories(context, false), context);

        builder.append("\n\n收入分类与二级分类：");
        appendCategories(builder, buildPromptCategories(context, true), context);

        builder.append("\n\n可用资产账户：");
        appendAssets(builder, context);

        builder.append("\n\n只返回 JSON 数组。示例：");
        builder.append("[");
        builder.append("{\"type\":0,\"amount\":5.00,\"category\":\"购物\",\"subCategory\":\"\",\"note\":\"武汉中百便利店有限公司\",\"asset\":\"\",\"time\":\"")
                .append(currentTime)
                .append("\"},");
        builder.append("{\"type\":0,\"amount\":19.90,\"category\":\"购物\",\"subCategory\":\"\",\"note\":\"武汉中百便利店有限公司\",\"asset\":\"\",\"time\":\"")
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
