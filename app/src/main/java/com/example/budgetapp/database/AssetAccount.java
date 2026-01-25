package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "asset_accounts")
public class AssetAccount {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String name;
    public double amount;
    
    // 0: 资产, 1: 负债, 2: 借出
    public int type;
    
    public long updateTime;

    // 【新增】货币符号
    public String currencySymbol;

    public AssetAccount(String name, double amount, int type) {
        this.name = name;
        this.amount = amount;
        this.type = type;
        this.updateTime = System.currentTimeMillis();
        this.currencySymbol = "¥"; // 默认值
    }
}