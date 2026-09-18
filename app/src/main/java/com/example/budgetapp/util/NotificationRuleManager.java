package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persistence and matching helpers for user-defined notification accounting rules. */
public final class NotificationRuleManager {
    private static final String PREFS = "notification_accounting_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_RULES = "rules";
    private static final String KEY_DIRECT_POST_NOTIFICATION = "direct_post_notification";
    private static final String KEY_DEFAULTS_INITIALIZED = "defaults_initialized";

    private NotificationRuleManager() {}

    public static boolean isRecentScreenDuplicate(long now, long screenTriggerTime,
                                                   double screenAmount, double notificationAmount,
                                                   long windowMs) {
        return screenTriggerTime > 0 && now >= screenTriggerTime
                && now - screenTriggerTime < windowMs
                && Math.abs(screenAmount - notificationAmount) < 0.005;
    }

    public static final class Rule {
        public String id;
        public String packageName;
        public String appName;
        public String regex;
        public int amountGroup = 1;
        public int type; // 0 expense, 1 income
        public int assetId = -1;
        public long delayMs = 5000L;
        public boolean directPost;

        public Rule() {}

        public Rule copy() {
            Rule copy = new Rule();
            copy.id = id; copy.packageName = packageName; copy.appName = appName;
            copy.regex = regex; copy.amountGroup = amountGroup; copy.type = type;
            copy.assetId = assetId; copy.delayMs = delayMs; copy.directPost = directPost;
            return copy;
        }

        JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("id", id); json.put("packageName", packageName); json.put("appName", appName);
            json.put("regex", regex); json.put("amountGroup", amountGroup); json.put("type", type);
            json.put("assetId", assetId); json.put("delayMs", delayMs); json.put("directPost", directPost);
            return json;
        }

        static Rule fromJson(JSONObject json) {
            Rule rule = new Rule();
            rule.id = json.optString("id", String.valueOf(System.nanoTime()));
            rule.packageName = json.optString("packageName", "");
            rule.appName = json.optString("appName", rule.packageName);
            rule.regex = json.optString("regex", "");
            rule.amountGroup = Math.max(0, json.optInt("amountGroup", 1));
            rule.type = json.optInt("type", 0);
            rule.assetId = json.optInt("assetId", -1);
            rule.delayMs = Math.max(0, json.optLong("delayMs", 5000L));
            rule.directPost = json.optBoolean("directPost", false);
            return rule;
        }
    }

    public static final class Match {
        public final Rule rule;
        public final double amount;
        public Match(Rule rule, double amount) { this.rule = rule; this.amount = amount; }
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isDirectPostNotificationEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DIRECT_POST_NOTIFICATION, true);
    }

    public static void setDirectPostNotificationEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_DIRECT_POST_NOTIFICATION, enabled).apply();
    }

    public static List<Rule> getRules(Context context) {
        List<Rule> rules = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RULES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) rules.add(Rule.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) {}
        ensureDefaults(context, rules);
        return rules;
    }

    private static void ensureDefaults(Context context, List<Rule> rules) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DEFAULTS_INITIALIZED, false)) return;
        Rule wechatFee = new Rule();
        wechatFee.id = "preset_wechat_fee";
        wechatFee.packageName = "com.tencent.mm";
        wechatFee.appName = "微信";
        wechatFee.regex = "已扣费\\s*[￥¥]?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?";
        wechatFee.amountGroup = 1;
        wechatFee.type = 0;
        wechatFee.assetId = -1;
        wechatFee.delayMs = 5000L;
        wechatFee.directPost = true;
        rules.add(wechatFee);
        saveRules(context, rules);
        prefs.edit().putBoolean(KEY_DEFAULTS_INITIALIZED, true).apply();
    }

    public static void saveRules(Context context, List<Rule> rules) {
        JSONArray array = new JSONArray();
        try { for (Rule rule : rules) array.put(rule.toJson()); } catch (Exception ignored) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RULES, array.toString()).apply();
    }

    public static Match match(Context context, String packageName, String text) {
        if (!isEnabled(context) || packageName == null || text == null) return null;
        for (Rule rule : getRules(context)) {
            if (!packageName.equals(rule.packageName) || rule.regex == null || rule.regex.trim().isEmpty()) continue;
            try {
                Matcher matcher = Pattern.compile(rule.regex, Pattern.MULTILINE).matcher(text);
                if (!matcher.find()) continue;
                int group = rule.amountGroup;
                if (group > matcher.groupCount()) group = 0;
                String value = matcher.group(group);
                if (value == null) continue;
                value = value.replace(",", "").replace("￥", "").replace("¥", "").trim();
                if (!value.matches("[-+]?[0-9]+(?:\\.[0-9]+)?")) {
                    Matcher number = Pattern.compile("[-+]?[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?").matcher(value);
                    if (number.find()) value = number.group().replace(",", "");
                }
                double amount = Double.parseDouble(value);
                if (amount > 0 && !Double.isInfinite(amount) && !Double.isNaN(amount)) return new Match(rule, amount);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
