package com.example.budgetapp;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import java.util.List;

public class BackupData {
    public int version = 2; // 升级版本号
    public long createTime;
    public List<Transaction> records;
    public List<AssetAccount> assets; // 【新增】资产列表

    // 无参构造函数 (Gson 需要)
    public BackupData() {
    }

    // 【修改】构造函数增加 assets 参数
    public BackupData(List<Transaction> records, List<AssetAccount> assets) {
        this.createTime = System.currentTimeMillis();
        this.records = records;
        this.assets = assets;
    }
}