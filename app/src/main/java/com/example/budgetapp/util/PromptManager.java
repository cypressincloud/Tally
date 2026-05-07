package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AI系统提示词管理器
 * 负责自定义提示词的存储、读取和验证
 */
public class PromptManager {
    
    private static final String PREF_NAME = "ai_prompt_prefs";
    private static final String KEY_CUSTOM_PROMPT = "custom_system_prompt";
    private static final String KEY_FIRST_TIME_RULES_SHOWN = "first_time_rules_shown";
    private static final int MIN_PROMPT_LENGTH = 50;
    
    /**
     * 获取自定义提示词
     * @param context 上下文
     * @return 自定义提示词，如果不存在返回 null
     */
    public static String getCustomPrompt(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CUSTOM_PROMPT, null);
    }
    
    /**
     * 保存自定义提示词
     * @param context 上下文
     * @param prompt 提示词内容
     */
    public static void saveCustomPrompt(Context context, String prompt) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CUSTOM_PROMPT, prompt).apply();
    }
    
    /**
     * 清除自定义提示词（恢复默认）
     * @param context 上下文
     */
    public static void clearCustomPrompt(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_CUSTOM_PROMPT).apply();
    }
    
    /**
     * 检查是否存在自定义提示词
     * @param context 上下文
     * @return true 如果存在自定义提示词
     */
    public static boolean hasCustomPrompt(Context context) {
        return getCustomPrompt(context) != null;
    }
    
    /**
     * 检查是否是首次访问（是否已显示过规则说明）
     * @param context 上下文
     * @return true 如果是首次访问
     */
    public static boolean isFirstTimeAccess(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return !prefs.getBoolean(KEY_FIRST_TIME_RULES_SHOWN, false);
    }
    
    /**
     * 标记已显示过规则说明
     * @param context 上下文
     */
    public static void markRulesShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FIRST_TIME_RULES_SHOWN, true).apply();
    }
    
    /**
     * 验证提示词有效性
     * @param prompt 提示词内容
     * @return 验证结果
     */
    public static ValidationResult validatePrompt(String prompt) {
        // 检查是否为空
        if (prompt == null || prompt.trim().isEmpty()) {
            return ValidationResult.error("提示词不能为空");
        }
        
        // 检查是否仅包含空白字符
        if (prompt.trim().isEmpty()) {
            return ValidationResult.error("提示词不能为空");
        }
        
        // 检查长度
        if (prompt.length() < MIN_PROMPT_LENGTH) {
            return ValidationResult.warning("提示词过短，可能无法正常工作");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        public final String warningMessage;
        
        private ValidationResult(boolean isValid, String errorMessage, String warningMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.warningMessage = warningMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }
        
        public static ValidationResult warning(String message) {
            return new ValidationResult(true, null, message);
        }
    }
}
