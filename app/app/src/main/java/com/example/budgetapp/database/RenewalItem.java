// src/main/java/com/example/budgetapp/database/RenewalItem.java
package com.example.budgetapp.database;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class RenewalItem {
    public String id;
    public String period; // "Year", "Month", 或 "Custom"
    public int year;      // 新增：用于自定义起算日期的年份
    public int month;
    public int day;
    public String object;
    public float amount;

    // 新增：用于自定义周期
    public int durationValue;
    public String durationUnit; // "Day", "Week", "Month", "Year"

    public RenewalItem() {
        this.id = UUID.randomUUID().toString();
    }

    public static RenewalItem fromJson(String jsonStr) throws JSONException {
        JSONObject obj = new JSONObject(jsonStr);
        RenewalItem item = new RenewalItem();
        item.id = obj.getString("id");
        item.period = obj.getString("period");
        item.year = obj.optInt("year", 0); // 兼容旧数据
        item.month = obj.getInt("month");
        item.day = obj.getInt("day");
        item.object = obj.getString("object");
        item.amount = (float) obj.getDouble("amount");

        item.durationValue = obj.optInt("durationValue", 1);
        item.durationUnit = obj.optString("durationUnit", "Month");
        return item;
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("period", period);
            obj.put("year", year);
            obj.put("month", month);
            obj.put("day", day);
            obj.put("object", object);
            obj.put("amount", amount);
            obj.put("durationValue", durationValue);
            obj.put("durationUnit", durationUnit);
            return obj.toString();
        } catch (JSONException e) {
            return "";
        }
    }
}