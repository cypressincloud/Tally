package com.example.budgetapp.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 批量输入解析器
 * 负责将空格分隔的关键字字符串解析为多个独立的关键字
 */
public class BatchInputParser {

    /**
     * 解析批量输入的关键字字符串
     * @param input 用户输入的字符串（可能包含多个关键字，用空格分隔）
     * @return 解析后的关键字列表（已去重、已trim）
     */
    public static List<String> parseKeywords(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1. 将制表符和换行符替换为空格
        String normalized = input.replaceAll("[\\t\\n\\r]+", " ");
        
        // 2. 按空格分割
        String[] parts = normalized.split("\\s+");
        
        // 3. 过滤和规范化
        List<String> keywords = new ArrayList<>();
        Set<String> seen = new HashSet<>();  // 用于去重
        
        for (String part : parts) {
            String keyword = normalizeKeyword(part);
            if (!keyword.isEmpty() && !seen.contains(keyword)) {
                keywords.add(keyword);
                seen.add(keyword);
            }
        }
        
        return keywords;
    }
    
    /**
     * 检查输入是否为批量输入模式（包含空格）
     * @param input 用户输入的字符串
     * @return true表示批量输入，false表示单个关键字
     */
    public static boolean isBatchInput(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        // 检查是否包含空格、制表符或换行符
        return input.matches(".*[\\s\\t\\n\\r]+.*");
    }
    
    /**
     * 规范化单个关键字（trim + 特殊字符处理）
     * @param keyword 原始关键字
     * @return 规范化后的关键字
     */
    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        // Trim前后空格
        return keyword.trim();
    }
}
