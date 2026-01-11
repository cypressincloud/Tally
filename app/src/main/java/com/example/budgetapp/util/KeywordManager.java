package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KeywordManager {

    private static final String PREF_NAME = "app_specific_keywords_prefs";

    // 定义支持的包名常量
    public static final String PKG_WECHAT = "com.tencent.mm";
    public static final String PKG_ALIPAY = "com.eg.android.AlipayGphone";
    public static final String PKG_JINGDONG = "com.jingdong.app.mall";
    public static final String PKG_PINDUODUO = "com.xunmeng.pinduoduo";
    public static final String PKG_DOUYIN = "com.ss.android.ugc.aweme";
    public static final String PKG_TAOBAO = "com.taobao.taobao";
    public static final String PKG_MEITUAN = "com.sankuai.meituan";

    // 类型常量
    public static final int TYPE_EXPENSE = 0;
    public static final int TYPE_INCOME = 1;

    /**
     * 获取支持的应用列表
     */
    public static Map<String, String> getSupportedApps() {
        Map<String, String> apps = new HashMap<>();
        apps.put(PKG_WECHAT, "微信");
        apps.put(PKG_ALIPAY, "支付宝");
        apps.put(PKG_TAOBAO, "淘宝");
        apps.put(PKG_JINGDONG, "京东");
        apps.put(PKG_PINDUODUO, "拼多多");
        apps.put(PKG_DOUYIN, "抖音");
        apps.put(PKG_MEITUAN, "美团");
        return apps;
    }

    /**
     * 获取指定应用、指定类型的关键字列表
     */
    public static Set<String> getKeywords(Context context, String packageName, int type) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = "keywords_" + packageName + "_" + type;
        return prefs.getStringSet(key, new HashSet<>());
    }

    /**
     * 添加关键字
     */
    public static void addKeyword(Context context, String packageName, int type, String keyword) {
        Set<String> current = new HashSet<>(getKeywords(context, packageName, type));
        current.add(keyword);
        saveKeywords(context, packageName, type, current);
    }

    /**
     * 删除关键字
     */
    public static void removeKeyword(Context context, String packageName, int type, String keyword) {
        Set<String> current = new HashSet<>(getKeywords(context, packageName, type));
        current.remove(keyword);
        saveKeywords(context, packageName, type, current);
    }

    private static void saveKeywords(Context context, String packageName, int type, Set<String> newKeywords) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = "keywords_" + packageName + "_" + type;
        prefs.edit().putStringSet(key, newKeywords).apply();
    }

    /**
     * 初始化默认数据
     * 【更新】增加了退款相关的默认关键字
     */
    public static void initDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        if (prefs.getAll().isEmpty()) {
            // --- 微信 ---
            // 支出
            addKeyword(context, PKG_WECHAT, TYPE_EXPENSE, "付款方式");
            // 收入 (包含收款和退款)
            addKeyword(context, PKG_WECHAT, TYPE_INCOME, "已存入零钱"); // 新增

            // --- 支付宝 ---
            // 支出
            addKeyword(context, PKG_ALIPAY, TYPE_EXPENSE, "支付成功");
        }
    }
}