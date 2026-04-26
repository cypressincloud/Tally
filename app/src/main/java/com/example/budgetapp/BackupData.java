package com.example.budgetapp;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.RenewalItem;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackupData {
    public int version = 5;
    public long createTime;

    public List<AssetAccount> assets;
    public List<String> expenseCategories;
    public List<String> incomeCategories;
    public Map<String, List<String>> subCategoryMap;
    public List<Goal> goals; // 👈 新增这一行

    public AssistantConfigData assistantConfig;
    public List<String> autoAssetRules;
    public List<RenewalItem> renewalList;

    // 【修复】记录数据类型，防止导入后 SharedPreferences 抛出 ClassCastException
    public Map<String, PrefItem> appPreferences;

    public List<Transaction> records;

    public BackupData() {}

    public BackupData(List<Transaction> records, List<AssetAccount> assets, List<Goal> goals) {
        this.createTime = System.currentTimeMillis();
        this.records = records;
        this.assets = assets;
        this.goals = goals; // 👈 新增赋值
    }

    // 保留旧的 2 个参数的构造方法（供微信、支付宝等外部账单导入使用）
    public BackupData(List<Transaction> records, List<AssetAccount> assets) {
        this.records = records;
        this.assets = assets;
    }
    public static class AssistantConfigData {
        public boolean enableAutoTrack;
        public boolean enableRefund;
        public boolean enableAssets;
        public int defaultAssetId;
        public Set<String> expenseKeywords;
        public Set<String> incomeKeywords;
        public float weekdayRate;
        public float holidayRate;
        public float monthlyBaseSalary;
    }

    // 【新增】用来包裹配置类型和值的静态类
    public static class PrefItem {
        public String type;
        public String value;
        public PrefItem() {}
        public PrefItem(String type, String value) {
            this.type = type;
            this.value = value;
        }
    }
}