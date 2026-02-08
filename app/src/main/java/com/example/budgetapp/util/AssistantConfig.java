package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.budgetapp.database.RenewalItem;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssistantConfig {
    private static final String PREF_NAME = "budget_assistant_prefs";

    private static final String KEY_RENEWAL_LIST = "key_renewal_list_json";
    // 基础功能 Key
    private static final String KEY_ENABLE = "key_enable_auto_track";
    private static final String KEY_ENABLE_ASSETS = "key_enable_assets_module";
    private static final String KEY_DEFAULT_ASSET_ID = "key_default_asset_id";

    // 关键字管理 Key
    private static final String KEY_KEYWORDS_EXPENSE = "key_keywords_expense";
    private static final String KEY_KEYWORDS_INCOME = "key_keywords_income";

    // 薪资配置 Key
    private static final String KEY_WEEKDAY_RATE = "weekday_overtime_rate";
    private static final String KEY_HOLIDAY_RATE = "holiday_overtime_rate";
    private static final String KEY_MONTHLY_BASE_SALARY = "monthly_base_salary";

    // 自动续费配置 Key
    private static final String KEY_AUTO_RENEWAL_PERIOD = "auto_renewal_period";
    private static final String KEY_AUTO_RENEWAL_MONTH = "auto_renewal_month";
    private static final String KEY_AUTO_RENEWAL_DAY = "auto_renewal_day";
    private static final String KEY_AUTO_RENEWAL_OBJECT = "auto_renewal_object";
    private static final String KEY_AUTO_RENEWAL_AMOUNT = "auto_renewal_amount";
    private static final String KEY_LAST_RENEWAL_DATE = "last_renewal_executed_date";

    private final SharedPreferences prefs;

    public AssistantConfig(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- 获取所有续费项目 ---
    public List<RenewalItem> getRenewalList() {
        List<RenewalItem> list = new ArrayList<>();
        String json = prefs.getString(KEY_RENEWAL_LIST, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(RenewalItem.fromJson(array.getString(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- 保存所有续费项目 ---
    public void saveRenewalList(List<RenewalItem> list) {
        JSONArray array = new JSONArray();
        for (RenewalItem item : list) {
            array.put(item.toJson());
        }
        prefs.edit().putString(KEY_RENEWAL_LIST, array.toString()).apply();
    }

    // --- 自动记账总开关 ---
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLE, true);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLE, enabled).apply();
    }

    // --- 资产模块开关 ---
    public boolean isAssetsEnabled() {
        return prefs.getBoolean(KEY_ENABLE_ASSETS, true);
    }

    public void setAssetsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLE_ASSETS, enabled).apply();
    }

    // --- 默认资产设置 ---
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

    // --- 薪资与加班配置 ---
    public float getWeekdayOvertimeRate() {
        return prefs.getFloat(KEY_WEEKDAY_RATE, 0f);
    }

    public void setWeekdayOvertimeRate(float rate) {
        prefs.edit().putFloat(KEY_WEEKDAY_RATE, rate).apply();
    }

    public float getHolidayOvertimeRate() {
        return prefs.getFloat(KEY_HOLIDAY_RATE, 0f);
    }

    public void setHolidayOvertimeRate(float rate) {
        prefs.edit().putFloat(KEY_HOLIDAY_RATE, rate).apply();
    }

    public float getMonthlyBaseSalary() {
        return prefs.getFloat(KEY_MONTHLY_BASE_SALARY, 0f);
    }

    public void setMonthlyBaseSalary(float salary) {
        prefs.edit().putFloat(KEY_MONTHLY_BASE_SALARY, salary).apply();
    }

    // --- 自动续费相关设置 ---
    public String getAutoRenewalPeriod() {
        return prefs.getString(KEY_AUTO_RENEWAL_PERIOD, "Month");
    }

    public void setAutoRenewalPeriod(String period) {
        prefs.edit().putString(KEY_AUTO_RENEWAL_PERIOD, period).apply();
    }

    public int getAutoRenewalMonth() {
        return prefs.getInt(KEY_AUTO_RENEWAL_MONTH, 1);
    }

    public void setAutoRenewalMonth(int month) {
        prefs.edit().putInt(KEY_AUTO_RENEWAL_MONTH, month).apply();
    }

    public int getAutoRenewalDay() {
        return prefs.getInt(KEY_AUTO_RENEWAL_DAY, 1);
    }

    public void setAutoRenewalDay(int day) {
        prefs.edit().putInt(KEY_AUTO_RENEWAL_DAY, day).apply();
    }

    /**
     * 设置自动续费的具体月份和日期
     * 用于解决 AutoRenewalActivity 的编译错误
     */
    public void setAutoRenewalDate(int month, int day) {
        prefs.edit()
                .putInt(KEY_AUTO_RENEWAL_MONTH, month)
                .putInt(KEY_AUTO_RENEWAL_DAY, day)
                .apply();
    }

    public String getAutoRenewalObject() {
        return prefs.getString(KEY_AUTO_RENEWAL_OBJECT, "");
    }

    public void setAutoRenewalObject(String object) {
        prefs.edit().putString(KEY_AUTO_RENEWAL_OBJECT, object).apply();
    }

    public float getAutoRenewalAmount() {
        return prefs.getFloat(KEY_AUTO_RENEWAL_AMOUNT, 0f);
    }

    public void setAutoRenewalAmount(float amount) {
        prefs.edit().putFloat(KEY_AUTO_RENEWAL_AMOUNT, amount).apply();
    }

    public String getLastRenewalDate() {
        return prefs.getString(KEY_LAST_RENEWAL_DATE, "");
    }

    public void setLastRenewalDate(String date) {
        prefs.edit().putString(KEY_LAST_RENEWAL_DATE, date).apply();
    }
}