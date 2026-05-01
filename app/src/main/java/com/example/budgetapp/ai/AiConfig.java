package com.example.budgetapp.ai;

import android.content.Context;
import android.content.SharedPreferences;

public class AiConfig {
    public static final String PREFS_NAME = "app_prefs";
    public static final String KEY_ENABLED = "ai_accounting_enabled";
    
    // 共享连接信息（向后兼容）
    public static final String KEY_BASE_URL = "ai_base_url";
    public static final String KEY_API_KEY = "ai_api_key";
    
    // 文本模型配置
    public static final String KEY_TEXT_MODEL = "ai_text_model";
    public static final String KEY_TEXT_BASE_URL = "ai_text_base_url";
    public static final String KEY_TEXT_API_KEY = "ai_text_api_key";
    public static final String KEY_TEXT_TEST_OK = "ai_text_test_ok";
    
    // 视觉模型配置
    public static final String KEY_VISION_MODEL = "ai_vision_model";
    public static final String KEY_VISION_BASE_URL = "ai_vision_base_url";
    public static final String KEY_VISION_API_KEY = "ai_vision_api_key";
    public static final String KEY_VISION_TEST_OK = "ai_vision_test_ok";
    
    // 音频模型配置
    public static final String KEY_AUDIO_MODEL = "ai_audio_model";
    public static final String KEY_AUDIO_BASE_URL = "ai_audio_base_url";
    public static final String KEY_AUDIO_API_KEY = "ai_audio_api_key";
    public static final String KEY_AUDIO_TEST_OK = "ai_audio_test_ok";

    private static final String LEGACY_KEY_MODEL = "ai_model";
    private static final String LEGACY_KEY_SUPPORTS_VISION = "ai_supports_vision";
    private static final String LEGACY_KEY_SUPPORTS_AUDIO = "ai_supports_audio";

    public boolean enabled;
    
    // 共享连接信息（向后兼容，作为默认值）
    public String baseUrl;
    public String apiKey;
    
    // 文本模型配置
    public String textModel;
    public String textBaseUrl;
    public String textApiKey;
    public boolean textTestOk;
    
    // 视觉模型配置
    public String visionModel;
    public String visionBaseUrl;
    public String visionApiKey;
    public boolean visionTestOk;
    
    // 音频模型配置
    public String audioModel;
    public String audioBaseUrl;
    public String audioApiKey;
    public boolean audioTestOk;

    public static AiConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String legacyModel = trim(prefs.getString(LEGACY_KEY_MODEL, ""));

        AiConfig config = new AiConfig();
        config.enabled = prefs.getBoolean(KEY_ENABLED, false);
        
        // 加载共享连接信息（向后兼容）
        config.baseUrl = trim(prefs.getString(KEY_BASE_URL, ""));
        config.apiKey = trim(prefs.getString(KEY_API_KEY, ""));
        
        // 加载文本模型配置
        config.textModel = firstNonEmpty(trim(prefs.getString(KEY_TEXT_MODEL, "")), legacyModel);
        config.textBaseUrl = trim(prefs.getString(KEY_TEXT_BASE_URL, ""));
        config.textApiKey = trim(prefs.getString(KEY_TEXT_API_KEY, ""));
        config.textTestOk = prefs.getBoolean(KEY_TEXT_TEST_OK, !config.textModel.isEmpty());
        
        // 加载视觉模型配置
        config.visionModel = firstNonEmpty(trim(prefs.getString(KEY_VISION_MODEL, "")), legacyModel);
        config.visionBaseUrl = trim(prefs.getString(KEY_VISION_BASE_URL, ""));
        config.visionApiKey = trim(prefs.getString(KEY_VISION_API_KEY, ""));
        config.visionTestOk = prefs.getBoolean(KEY_VISION_TEST_OK, prefs.getBoolean(LEGACY_KEY_SUPPORTS_VISION, false));
        
        // 加载音频模型配置
        config.audioModel = firstNonEmpty(trim(prefs.getString(KEY_AUDIO_MODEL, "")), legacyModel);
        config.audioBaseUrl = trim(prefs.getString(KEY_AUDIO_BASE_URL, ""));
        config.audioApiKey = trim(prefs.getString(KEY_AUDIO_API_KEY, ""));
        config.audioTestOk = prefs.getBoolean(KEY_AUDIO_TEST_OK, prefs.getBoolean(LEGACY_KEY_SUPPORTS_AUDIO, false));
        
        return config;
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                // 保存共享连接信息（向后兼容）
                .putString(KEY_BASE_URL, trim(baseUrl))
                .putString(KEY_API_KEY, trim(apiKey))
                // 保存文本模型配置
                .putString(KEY_TEXT_MODEL, trim(textModel))
                .putString(KEY_TEXT_BASE_URL, trim(textBaseUrl))
                .putString(KEY_TEXT_API_KEY, trim(textApiKey))
                .putBoolean(KEY_TEXT_TEST_OK, textTestOk)
                // 保存视觉模型配置
                .putString(KEY_VISION_MODEL, trim(visionModel))
                .putString(KEY_VISION_BASE_URL, trim(visionBaseUrl))
                .putString(KEY_VISION_API_KEY, trim(visionApiKey))
                .putBoolean(KEY_VISION_TEST_OK, visionTestOk)
                // 保存音频模型配置
                .putString(KEY_AUDIO_MODEL, trim(audioModel))
                .putString(KEY_AUDIO_BASE_URL, trim(audioBaseUrl))
                .putString(KEY_AUDIO_API_KEY, trim(audioApiKey))
                .putBoolean(KEY_AUDIO_TEST_OK, audioTestOk)
                .apply();
    }

    public boolean hasSharedConnection() {
        return !trim(baseUrl).isEmpty() && !trim(apiKey).isEmpty();
    }

    public boolean isTextReady() {
        String url = getEffectiveTextBaseUrl();
        String key = getEffectiveTextApiKey();
        return !trim(url).isEmpty() && !trim(key).isEmpty() && !trim(textModel).isEmpty();
    }

    public boolean isVisionReady() {
        String url = getEffectiveVisionBaseUrl();
        String key = getEffectiveVisionApiKey();
        return !trim(url).isEmpty() && !trim(key).isEmpty() && !trim(visionModel).isEmpty();
    }

    public boolean isAudioReady() {
        String url = getEffectiveAudioBaseUrl();
        String key = getEffectiveAudioApiKey();
        return !trim(url).isEmpty() && !trim(key).isEmpty() && !trim(audioModel).isEmpty();
    }
    
    /**
     * 获取文本模型的有效 Base URL（如果未配置则使用共享配置）
     */
    public String getEffectiveTextBaseUrl() {
        return !trim(textBaseUrl).isEmpty() ? trim(textBaseUrl) : trim(baseUrl);
    }
    
    /**
     * 获取文本模型的有效 API Key（如果未配置则使用共享配置）
     */
    public String getEffectiveTextApiKey() {
        return !trim(textApiKey).isEmpty() ? trim(textApiKey) : trim(apiKey);
    }
    
    /**
     * 获取视觉模型的有效 Base URL（如果未配置则使用文本模型配置）
     */
    public String getEffectiveVisionBaseUrl() {
        if (!trim(visionBaseUrl).isEmpty()) {
            return trim(visionBaseUrl);
        }
        return getEffectiveTextBaseUrl();
    }
    
    /**
     * 获取视觉模型的有效 API Key（如果未配置则使用文本模型配置）
     */
    public String getEffectiveVisionApiKey() {
        if (!trim(visionApiKey).isEmpty()) {
            return trim(visionApiKey);
        }
        return getEffectiveTextApiKey();
    }
    
    /**
     * 获取音频模型的有效 Base URL（如果未配置则使用文本模型配置）
     */
    public String getEffectiveAudioBaseUrl() {
        if (!trim(audioBaseUrl).isEmpty()) {
            return trim(audioBaseUrl);
        }
        return getEffectiveTextBaseUrl();
    }
    
    /**
     * 获取音频模型的有效 API Key（如果未配置则使用文本模型配置）
     */
    public String getEffectiveAudioApiKey() {
        if (!trim(audioApiKey).isEmpty()) {
            return trim(audioApiKey);
        }
        return getEffectiveTextApiKey();
    }

    public boolean isEnabledAndReady() {
        return enabled && isTextReady();
    }

    public boolean matchesSharedEndpoint(AiConfig other) {
        if (other == null) return false;
        return trim(baseUrl).equals(trim(other.baseUrl))
                && trim(apiKey).equals(trim(other.apiKey));
    }

    public boolean hasAnyScreenshotSupport() {
        return isTextReady() || isVisionReady();
    }

    private static String firstNonEmpty(String primary, String fallback) {
        return !trim(primary).isEmpty() ? trim(primary) : trim(fallback);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
