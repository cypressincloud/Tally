// src/main/java/com/example/budgetapp/database/RenewalItem.java
package com.example.budgetapp.database;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class RenewalItem {
    public String id;
    public String period; // "Year" 或 "Month"
    public int month;
    public int day;
    public String object;
    public float amount;

    public RenewalItem() {
        this.id = UUID.randomUUID().toString();
    }

    // 用于从 JSON 恢复对象
    public static RenewalItem fromJson(String jsonStr) throws JSONException {
        JSONObject obj = new JSONObject(jsonStr);
        RenewalItem item = new RenewalItem();
        item.id = obj.getString("id");
        item.period = obj.getString("period");
        item.month = obj.getInt("month");
        item.day = obj.getInt("day");
        item.object = obj.getString("object");
        item.amount = (float) obj.getDouble("amount");
        return item;
    }

    // 转换为 JSON 存储
    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("period", period);
            obj.put("month", month);
            obj.put("day", day);
            obj.put("object", object);
            obj.put("amount", amount);
            return obj.toString();
        } catch (JSONException e) {
            return "";
        }
    }
}