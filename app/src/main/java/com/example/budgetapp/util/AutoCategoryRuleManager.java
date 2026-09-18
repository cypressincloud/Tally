package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.budgetapp.BackupManager;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Persists and applies rules for automatic-record classification. */
public final class AutoCategoryRuleManager {
    private static final String PREF_NAME = "auto_category_rule_prefs";
    private static final String KEY_RULES = "rules";
    private static final String DEFAULT_PREF_NAME = "app_default_category_prefs";
    private static final String DEFAULT_KEY = "defaults";

    private AutoCategoryRuleManager() {}

    public static final class DefaultCategory {
        public final String packageName;
        public final int type;
        public final String category;
        public final String subCategory;
        public DefaultCategory(String packageName, int type, String category, String subCategory) {
            this.packageName = packageName == null ? "" : packageName;
            this.type = type;
            this.category = category == null ? "" : category;
            this.subCategory = subCategory == null ? "" : subCategory;
        }
    }

    public static List<DefaultCategory> getDefaults(Context context) {
        List<DefaultCategory> result = new ArrayList<>();
        String raw = context.getSharedPreferences(DEFAULT_PREF_NAME, Context.MODE_PRIVATE)
                .getString(DEFAULT_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject item = array.getJSONObject(i);
                result.add(new DefaultCategory(item.optString("packageName", ""),
                        item.optInt("type", 0), item.optString("category", ""),
                        item.optString("subCategory", "")));
            }
        } catch (Exception ignored) { }
        return result;
    }

    public static void saveDefaults(Context context, List<DefaultCategory> defaults) {
        saveDefaults(context, defaults, true);
    }

    public static void restoreDefaults(Context context, List<DefaultCategory> defaults) {
        saveDefaults(context, defaults, false);
    }

    private static void saveDefaults(Context context, List<DefaultCategory> defaults,
                                     boolean triggerBackup) {
        JSONArray array = new JSONArray();
        try {
            for (DefaultCategory item : defaults) {
                org.json.JSONObject json = new org.json.JSONObject();
                json.put("packageName", item.packageName);
                json.put("type", item.type);
                json.put("category", item.category);
                json.put("subCategory", item.subCategory);
                array.put(json);
            }
        } catch (Exception ignored) { return; }
        context.getSharedPreferences(DEFAULT_PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(DEFAULT_KEY, array.toString()).apply();
        if (triggerBackup) BackupManager.triggerAutoUploadIfEnabled(context);
    }

    public static DefaultCategory findDefault(Context context, String packageName, int type) {
        DefaultCategory global = null;
        for (DefaultCategory item : getDefaults(context)) {
            if (item.type != type) continue;
            if (item.packageName.isEmpty()) global = item;
            else if (item.packageName.equals(packageName)) return item;
        }
        return global;
    }

    public static List<AutoCategoryRule> getRules(Context context) {
        List<AutoCategoryRule> result = new ArrayList<>();
        String raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_RULES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(AutoCategoryRule.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            // Ignore malformed user preferences and keep the app usable.
        }
        return result;
    }

    public static void saveRules(Context context, List<AutoCategoryRule> rules) {
        saveRules(context, rules, true);
    }

    public static void restoreRules(Context context, List<AutoCategoryRule> rules) {
        saveRules(context, rules, false);
    }

    private static void saveRules(Context context, List<AutoCategoryRule> rules,
                                  boolean triggerBackup) {
        JSONArray array = new JSONArray();
        try {
            for (AutoCategoryRule rule : rules) array.put(rule.toJson());
        } catch (Exception ignored) {
            return;
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_RULES, array.toString()).apply();
        if (triggerBackup) BackupManager.triggerAutoUploadIfEnabled(context);
    }

    public static void addRule(Context context, AutoCategoryRule rule) {
        List<AutoCategoryRule> rules = getRules(context);
        rules.add(rule);
        saveRules(context, rules);
    }

    public static void deleteRule(Context context, int index) {
        List<AutoCategoryRule> rules = getRules(context);
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
            saveRules(context, rules);
        }
    }

    public static void updateRule(Context context, int index, AutoCategoryRule rule) {
        List<AutoCategoryRule> rules = getRules(context);
        if (index >= 0 && index < rules.size()) {
            rules.set(index, rule);
            saveRules(context, rules);
        }
    }

    /** Applies the first matching rule, preserving the user's rule order. */
    public static AutoCategoryRule findMatch(Context context, String packageName, String note,
                                             int currentType) {
        if (note == null) return null;
        for (AutoCategoryRule rule : getRules(context)) {
            if (!rule.getPackageName().isEmpty() && !rule.getPackageName().equals(packageName)) continue;
            if (rule.getSourceType() != 2 && rule.getSourceType() != currentType) continue;
            if (matches(note, rule)) return rule;
        }
        return null;
    }

    private static boolean matches(String note, AutoCategoryRule rule) {
        String expression = rule.getExpression();
        if (expression.isEmpty()) return false;
        switch (rule.getMatchType()) {
            case AutoCategoryRule.MATCH_EQUALS:
                return note.trim().equals(expression.trim());
            case AutoCategoryRule.MATCH_REGEX:
                try { return Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(note).find(); }
                catch (RuntimeException ignored) { return false; }
            case AutoCategoryRule.MATCH_CONTAINS:
            default:
                return note.toLowerCase(Locale.ROOT).contains(expression.toLowerCase(Locale.ROOT));
        }
    }

    public static String validateExpression(String expression, int matchType) {
        if (expression == null || expression.trim().isEmpty()) return "请输入匹配内容";
        if (matchType == AutoCategoryRule.MATCH_REGEX) {
            try { Pattern.compile(expression); }
            catch (RuntimeException e) { return "正则表达式无效：" + e.getMessage(); }
        }
        return null;
    }
}
