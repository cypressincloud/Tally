package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.budgetapp.BackupManager;
import com.example.budgetapp.ai.TransactionDraft;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI分类关键字规则管理器
 * 负责规则的增删改查和持久化存储
 */
public class AiCategoryRuleManager {
    private static final String TAG = "AiCategoryRuleManager";
    private static final String PREF_NAME = "ai_category_rule_prefs";
    private static final String KEY_RULES = "rules";

    /**
     * 获取所有规则
     * @param context 上下文
     * @return 规则列表
     */
    public static List<CategoryRule> getRules(Context context) {
        List<CategoryRule> rules = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String rulesJson = prefs.getString(KEY_RULES, "[]");
        
        try {
            JSONArray jsonArray = new JSONArray(rulesJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                CategoryRule rule = CategoryRule.fromJson(jsonObject);
                rules.add(rule);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse rules JSON", e);
        }
        
        return rules;
    }

    /**
     * 添加新规则
     * @param context 上下文
     * @param rule 规则对象
     */
    public static void addRule(Context context, CategoryRule rule) {
        List<CategoryRule> rules = getRules(context);
        rules.add(rule);
        saveRules(context, rules);
    }

    /**
     * 更新指定位置的规则
     * @param context 上下文
     * @param index 规则索引
     * @param rule 新的规则对象
     */
    public static void updateRule(Context context, int index, CategoryRule rule) {
        List<CategoryRule> rules = getRules(context);
        if (index >= 0 && index < rules.size()) {
            rules.set(index, rule);
            saveRules(context, rules);
        }
    }

    /**
     * 删除指定位置的规则
     * @param context 上下文
     * @param index 规则索引
     */
    public static void deleteRule(Context context, int index) {
        List<CategoryRule> rules = getRules(context);
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
            saveRules(context, rules);
        }
    }

    /**
     * 保存规则列表到SharedPreferences
     * @param context 上下文
     * @param rules 规则列表
     */
    private static void saveRules(Context context, List<CategoryRule> rules) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (CategoryRule rule : rules) {
                jsonArray.put(rule.toJson());
            }
            
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_RULES, jsonArray.toString()).apply();
            
            // 触发WebDAV自动同步
            BackupManager.triggerAutoUploadIfEnabled(context);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save rules", e);
        }
    }

    /**
     * 批量添加规则
     * @param context 上下文
     * @param rules 规则列表
     */
    public static void addRules(Context context, List<CategoryRule> rules) {
        if (rules == null || rules.isEmpty()) {
            Log.w(TAG, "Attempted to add empty rules list");
            return;
        }
        
        try {
            List<CategoryRule> existingRules = getRules(context);
            existingRules.addAll(rules);
            saveRules(context, existingRules);
            Log.d(TAG, "Successfully added " + rules.size() + " rules");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add rules", e);
            throw new RuntimeException("批量添加规则失败", e);
        }
    }

    /**
     * 批量替换规则（用于编辑模式）
     * @param context 上下文
     * @param index 要替换的规则索引
     * @param newRules 新的规则列表
     */
    public static void replaceRule(Context context, int index, List<CategoryRule> newRules) {
        if (newRules == null || newRules.isEmpty()) {
            Log.w(TAG, "Attempted to replace with empty rules list");
            return;
        }
        
        try {
            List<CategoryRule> rules = getRules(context);
            if (index >= 0 && index < rules.size()) {
                rules.remove(index);  // 删除原规则
                rules.addAll(index, newRules);  // 在相同位置插入新规则
                saveRules(context, rules);
                Log.d(TAG, "Successfully replaced rule at index " + index + " with " + newRules.size() + " rules");
            } else {
                Log.w(TAG, "Invalid index for rule replacement: " + index);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to replace rule", e);
            throw new RuntimeException("批量替换规则失败", e);
        }
    }

    /**
     * 应用规则到账单草稿
     * @param context 上下文
     * @param draft 账单草稿
     */
    public static void applyRules(Context context, TransactionDraft draft) {
        if (draft == null || draft.note == null || draft.note.isEmpty()) {
            return;
        }
        
        List<CategoryRule> rules = getRules(context);
        for (CategoryRule rule : rules) {
            if (matchesKeyword(draft.note, rule.getKeyword())) {
                // 使用第一个匹配的规则
                draft.category = rule.getCategory();
                draft.type = rule.getType();
                // 应用子分类（如果有）
                if (rule.getSubCategory() != null && !rule.getSubCategory().isEmpty()) {
                    draft.subCategory = rule.getSubCategory();
                } else {
                    draft.subCategory = "";
                }
                Log.d(TAG, "Applied rule: " + rule.getKeyword() + " -> " + rule.getCategory() + 
                      (rule.getSubCategory().isEmpty() ? "" : " > " + rule.getSubCategory()));
                break;
            }
        }
    }

    /**
     * 关键字匹配（不区分大小写）
     * @param description 账单描述
     * @param keyword 关键字
     * @return 是否匹配
     */
    private static boolean matchesKeyword(String description, String keyword) {
        if (description == null || keyword == null) {
            return false;
        }
        return description.toLowerCase().contains(keyword.toLowerCase());
    }
}
