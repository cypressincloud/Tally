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
    private static final String KEY_ENABLE_SUB_CATEGORY = "enable_sub_category";

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

    // 【新增】二级分类开关状态
    public static boolean isSubCategoryEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLE_SUB_CATEGORY, false);
    }

    public static void setSubCategoryEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLE_SUB_CATEGORY, enabled).apply();
    }

    // 【新增】获取某分类的二级分类列表
    public static List<String> getSubCategories(Context context, String parentCategory) {
        return getList(context, "sub_cat_" + parentCategory, "");
    }

    // 【新增】保存某分类的二级分类列表
    public static void saveSubCategories(Context context, String parentCategory, List<String> list) {
        saveList(context, "sub_cat_" + parentCategory, list);
    }

    private static List<String> getList(Context context, String key, String defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedString = prefs.getString(key, defaultValue);
        if (savedString == null || savedString.isEmpty()) {
            return new ArrayList<>(); // 修改为返回空列表而不是包含空字符串的列表
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