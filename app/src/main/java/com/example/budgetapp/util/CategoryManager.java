package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CategoryManager {
    private static final String PREF_NAME = "category_prefs";
    private static final String KEY_EXPENSE = "key_expense_categories";
    private static final String KEY_INCOME = "key_income_categories";

    // 默认预设
    private static final String DEFAULT_EXPENSE = "餐饮,交通,购物,娱乐,医疗,教育,居家,自定义";
    private static final String DEFAULT_INCOME = "工资,奖金,投资,兼职,礼金,自定义";

    public static List<String> getExpenseCategories(Context context) {
        return getList(context, KEY_EXPENSE, DEFAULT_EXPENSE);
    }

    public static List<String> getIncomeCategories(Context context) {
        return getList(context, KEY_INCOME, DEFAULT_INCOME);
    }

    public static void saveExpenseCategories(Context context, List<String> list) {
        saveList(context, KEY_EXPENSE, list);
    }

    public static void saveIncomeCategories(Context context, List<String> list) {
        saveList(context, KEY_INCOME, list);
    }

    private static List<String> getList(Context context, String key, String defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedString = prefs.getString(key, defaultValue);
        // 处理空字符串情况
        if (savedString == null || savedString.isEmpty()) {
            savedString = defaultValue;
        }
        String[] array = savedString.split(",");
        return new ArrayList<>(Arrays.asList(array));
    }

    private static void saveList(Context context, String key, List<String> list) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String joinedString = TextUtils.join(",", list);
        prefs.edit().putString(key, joinedString).apply();
    }
}