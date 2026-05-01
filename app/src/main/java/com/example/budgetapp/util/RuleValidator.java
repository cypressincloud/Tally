package com.example.budgetapp.util;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则验证器
 * 负责验证批量输入的关键字，检测重复、长度、数量等
 */
public class RuleValidator {
    public static final int MAX_KEYWORD_LENGTH = 50;
    public static final int MAX_BATCH_COUNT = 20;
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        public boolean isValid;
        public String errorMessage;
        public List<String> duplicateKeywords;  // 与现有规则重复的关键字
        public List<String> validKeywords;      // 验证通过的关键字
        
        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.duplicateKeywords = new ArrayList<>();
            this.validKeywords = new ArrayList<>();
        }
    }
    
    /**
     * 验证批量输入的关键字列表
     * @param context 上下文
     * @param keywords 关键字列表
     * @param excludeIndex 编辑模式下要排除的规则索引（-1表示添加模式）
     * @return 验证结果
     */
    public static ValidationResult validate(Context context, List<String> keywords, int excludeIndex) {
        // 1. 检查是否为空
        if (keywords == null || keywords.isEmpty()) {
            return new ValidationResult(false, "请输入至少一个关键字");
        }
        
        // 2. 检查数量限制
        if (keywords.size() > MAX_BATCH_COUNT) {
            return new ValidationResult(false, "一次最多添加" + MAX_BATCH_COUNT + "个关键字");
        }
        
        // 3. 检查每个关键字的长度
        for (String keyword : keywords) {
            if (keyword.length() > MAX_KEYWORD_LENGTH) {
                return new ValidationResult(false, 
                    "关键字'" + keyword + "'过长，最多" + MAX_KEYWORD_LENGTH + "个字符");
            }
        }
        
        // 4. 检查重复
        ValidationResult result = new ValidationResult(true, "");
        List<CategoryRule> existingRules = AiCategoryRuleManager.getRules(context);
        
        for (String keyword : keywords) {
            boolean duplicate = false;
            for (int i = 0; i < existingRules.size(); i++) {
                if (i == excludeIndex) continue;  // 编辑模式下排除当前规则
                if (existingRules.get(i).getKeyword().equals(keyword)) {
                    duplicate = true;
                    break;
                }
            }
            
            if (duplicate) {
                result.duplicateKeywords.add(keyword);
            } else {
                result.validKeywords.add(keyword);
            }
        }
        
        return result;
    }
    
    /**
     * 检查关键字是否与现有规则重复
     * @param context 上下文
     * @param keyword 关键字
     * @param excludeIndex 要排除的规则索引
     * @return true表示重复
     */
    private static boolean isDuplicate(Context context, String keyword, int excludeIndex) {
        List<CategoryRule> existingRules = AiCategoryRuleManager.getRules(context);
        for (int i = 0; i < existingRules.size(); i++) {
            if (i == excludeIndex) continue;
            if (existingRules.get(i).getKeyword().equals(keyword)) {
                return true;
            }
        }
        return false;
    }
}
