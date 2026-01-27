package com.example.budgetapp;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackupData {
    public int version = 4; 
    public long createTime;
    
    // 【修改】调整字段顺序：将资产、配置等元数据放在前面
    public List<AssetAccount> assets;
    public List<String> expenseCategories;
    public List<String> incomeCategories;
    public Map<String, List<String>> subCategoryMap;
    public AssistantConfigData assistantConfig;
    public List<String> autoAssetRules;

    // 【修改】将数据量最大的交易记录放在最后，方便查看 JSON
    public List<Transaction> records;

    public BackupData() {
    }

    public BackupData(List<Transaction> records, List<AssetAccount> assets) {
        this.createTime = System.currentTimeMillis();
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
}