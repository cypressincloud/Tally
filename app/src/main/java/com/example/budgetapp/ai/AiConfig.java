package com.example.budgetapp.ai;

import android.content.Context;
import android.content.SharedPreferences;

public class AiConfig {
    public static final String PREFS_NAME = "app_prefs";
    public static final String KEY_ENABLED = "ai_accounting_enabled";
    public static final String KEY_BASE_URL = "ai_base_url";
    public static final String KEY_API_KEY = "ai_api_key";
    public static final String KEY_TEXT_MODEL = "ai_text_model";
    public static final String KEY_VISION_MODEL = "ai_vision_model";
    public static final String KEY_AUDIO_MODEL = "ai_audio_model";
    public static final String KEY_TEXT_TEST_OK = "ai_text_test_ok";
    public static final String KEY_VISION_TEST_OK = "ai_vision_test_ok";
    public static final String KEY_AUDIO_TEST_OK = "ai_audio_test_ok";

    private static final String LEGACY_KEY_MODEL = "ai_model";
    private static final String LEGACY_KEY_SUPPORTS_VISION = "ai_supports_vision";
    private static final String LEGACY_KEY_SUPPORTS_AUDIO = "ai_supports_audio";

    public boolean enabled;
    public String baseUrl;
    public String apiKey;
    public String textModel;
    public String visionModel;
    public String audioModel;
    public boolean textTestOk;
    public boolean visionTestOk;
    public boolean audioTestOk;

    public static AiConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String legacyModel = trim(prefs.getString(LEGACY_KEY_MODEL, ""));

        AiConfig config = new AiConfig();
        config.enabled = prefs.getBoolean(KEY_ENABLED, false);
        config.baseUrl = trim(prefs.getString(KEY_BASE_URL, ""));
        config.apiKey = trim(prefs.getString(KEY_API_KEY, ""));
        config.textModel = firstNonEmpty(trim(prefs.getString(KEY_TEXT_MODEL, "")), legacyModel);
        config.visionModel = firstNonEmpty(trim(prefs.getString(KEY_VISION_MODEL, "")), legacyModel);
        config.audioModel = firstNonEmpty(trim(prefs.getString(KEY_AUDIO_MODEL, "")), legacyModel);
        config.textTestOk = prefs.getBoolean(KEY_TEXT_TEST_OK, !config.textModel.isEmpty());
        config.visionTestOk = prefs.getBoolean(KEY_VISION_TEST_OK, prefs.getBoolean(LEGACY_KEY_SUPPORTS_VISION, false));
        config.audioTestOk = prefs.getBoolean(KEY_AUDIO_TEST_OK, prefs.getBoolean(LEGACY_KEY_SUPPORTS_AUDIO, false));
        return config;
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_BASE_URL, trim(baseUrl))
                .putString(KEY_API_KEY, trim(apiKey))
                .putString(KEY_TEXT_MODEL, trim(textModel))
                .putString(KEY_VISION_MODEL, trim(visionModel))
                .putString(KEY_AUDIO_MODEL, trim(audioModel))
                .putBoolean(KEY_TEXT_TEST_OK, textTestOk)
                .putBoolean(KEY_VISION_TEST_OK, visionTestOk)
                .putBoolean(KEY_AUDIO_TEST_OK, audioTestOk)
                .apply();
    }

    public boolean hasSharedConnection() {
        return !trim(baseUrl).isEmpty() && !trim(apiKey).isEmpty();
    }

    public boolean isTextReady() {
        return hasSharedConnection() && !trim(textModel).isEmpty();
    }

    public boolean isVisionReady() {
        return hasSharedConnection() && !trim(visionModel).isEmpty();
    }

    public boolean isAudioReady() {
        return hasSharedConnection() && !trim(audioModel).isEmpty();
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
