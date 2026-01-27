package com.example.budgetapp;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import java.util.List;

public class BackupData {
    public int version = 2; // 升级版本号
    public long createTime;
    public List<Transaction> records;
    public List<AssetAccount> assets;
    
    // 【新增】用于接收导入的分类配置
    public List<String> expenseCategories;
    public List<String> incomeCategories;

    // 无参构造函数 (Gson 需要)
    public BackupData() {
    }

    public BackupData(List<Transaction> records, List<AssetAccount> assets) {
        this.createTime = System.currentTimeMillis();
        this.records = records;
        this.assets = assets;
    }
}