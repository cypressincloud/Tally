package com.example.budgetapp.util;

import android.text.TextUtils;
import com.example.budgetapp.database.Transaction;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExternalImportHelper {

    // 1. 定义一个内部类来接收外部 JSON 的结构
    // 字段名必须与外部 JSON 中的 key 完全一致
    private static class ExternalRecord {
        String key;
        String date;      // 格式: "2026-01-22 16:51:10"
        String category;  // 格式: "三餐"
        String type;      // 格式: "支出" 或 "收入"
        double money;     // 格式: 30.0
        // 其他字段(currency, username等)我们不需要，Gson会自动忽略
    }

    /**
     * 解析外部 JSON 字符串并转换为当前项目的 Transaction 对象列表
     */
    public static List<Transaction> parseExternalData(String jsonString) {
        List<Transaction> resultList = new ArrayList<>();
        
        if (TextUtils.isEmpty(jsonString)) {
            return resultList;
        }

        // 使用 Gson 解析 JSON 数组
        Gson gson = new Gson();
        List<ExternalRecord> externalList;
        try {
            externalList = gson.fromJson(jsonString, new TypeToken<List<ExternalRecord>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return resultList; // 解析失败返回空列表
        }

        if (externalList == null || externalList.isEmpty()) {
            return resultList;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        for (ExternalRecord record : externalList) {
            try {
                Transaction transaction = new Transaction();

                // --- 1. 日期转换 ---
                if (record.date != null) {
                    transaction.date = dateFormat.parse(record.date).getTime();
                } else {
                    transaction.date = System.currentTimeMillis(); 
                }

                // --- 2. 类型转换 ---
                // 假设你的项目中：0 = 支出, 1 = 收入
                if ("支出".equals(record.type)) {
                    transaction.type = 0; 
                } else if ("收入".equals(record.type)) {
                    transaction.type = 1;
                } else {
                    transaction.type = 0; // 默认作为支出
                }

                // --- 3. 基础字段映射 ---
                // 确保分类不为空
                transaction.category = TextUtils.isEmpty(record.category) ? "其他" : record.category;
                transaction.amount = record.money;
                
                // --- 4. 补充默认值 ---
                // 外部数据没有备注，我们留空
                transaction.note = ""; 
                transaction.remark = ""; // 也可以把 record.key 存入 remark 方便排查
                transaction.assetId = 0; // 默认为 0，表示不关联具体资产账户

                resultList.add(transaction);

            } catch (ParseException e) {
                e.printStackTrace();
                // 日期格式错误则跳过该条
            }
        }

        return resultList;
    }
}