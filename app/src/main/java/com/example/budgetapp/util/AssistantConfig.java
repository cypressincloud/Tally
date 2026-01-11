package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class AssistantConfig {
    private static final String PREF_NAME = "budget_assistant_prefs";
    
    private static final String KEY_ENABLE = "key_enable_auto_track";
    private static final String KEY_ENABLE_REFUND = "key_enable_refund_monitor";
    private static final String KEY_ENABLE_ASSETS = "key_enable_assets_module";
    private static final String KEY_DEFAULT_ASSET_ID = "key_default_asset_id"; // 新增

    private static final String KEY_KEYWORDS_EXPENSE = "key_keywords_expense";
    private static final String KEY_KEYWORDS_INCOME = "key_keywords_income";

    private final SharedPreferences prefs;

    public AssistantConfig(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- 自动记账总开关 ---
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLE, true);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLE, enabled).apply();
    }

    // --- 退款监听开关 ---
    public boolean isRefundEnabled() {
        return prefs.getBoolean(KEY_ENABLE_REFUND, false); 
    }

    public void setRefundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLE_REFUND, enabled).apply();
    }

    // --- 资产模块开关 ---
    public boolean isAssetsEnabled() {
        return prefs.getBoolean(KEY_ENABLE_ASSETS, true); 
    }

    public void setAssetsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLE_ASSETS, enabled).apply();
    }

    // --- 默认资产设置 (新增) ---
    public int getDefaultAssetId() {
        return prefs.getInt(KEY_DEFAULT_ASSET_ID, -1);
    }

    public void setDefaultAssetId(int id) {
        prefs.edit().putInt(KEY_DEFAULT_ASSET_ID, id).apply();
    }

    // --- 关键字管理 ---
    public Set<String> getExpenseKeywords() {
        return new HashSet<>(prefs.getStringSet(KEY_KEYWORDS_EXPENSE, new HashSet<>()));
    }

    public void addExpenseKeyword(String keyword) {
        Set<String> current = getExpenseKeywords();
        current.add(keyword);
        prefs.edit().putStringSet(KEY_KEYWORDS_EXPENSE, current).apply();
    }

    public void removeExpenseKeyword(String keyword) {
        Set<String> current = getExpenseKeywords();
        current.remove(keyword);
        prefs.edit().putStringSet(KEY_KEYWORDS_EXPENSE, current).apply();
    }

    public Set<String> getIncomeKeywords() {
        return new HashSet<>(prefs.getStringSet(KEY_KEYWORDS_INCOME, new HashSet<>()));
    }

    public void addIncomeKeyword(String keyword) {
        Set<String> current = getIncomeKeywords();
        current.add(keyword);
        prefs.edit().putStringSet(KEY_KEYWORDS_INCOME, current).apply();
    }

    public void removeIncomeKeyword(String keyword) {
        Set<String> current = getIncomeKeywords();
        current.remove(keyword);
        prefs.edit().putStringSet(KEY_KEYWORDS_INCOME, current).apply();
    }
}