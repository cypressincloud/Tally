package com.example.budgetapp;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.RenewalItem; // 【新增】导入自动续费类型

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackupData {
    public int version = 5; // 【修改】升级版本号以兼容新数据
    public long createTime;

    // 【修改】调整字段顺序：将资产、配置等元数据放在前面
    public List<AssetAccount> assets;
    public List<String> expenseCategories;
    public List<String> incomeCategories;
    public Map<String, List<String>> subCategoryMap;
    public AssistantConfigData assistantConfig;
    public List<String> autoAssetRules;

    // ================= 新增备份字段 =================
    public List<RenewalItem> renewalList;
    public Map<String, String> appPreferences; // 存储所有的SharedPreferences应用偏好开关
    // ================================================

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