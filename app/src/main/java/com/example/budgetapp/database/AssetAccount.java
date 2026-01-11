package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "asset_accounts")
public class AssetAccount {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String name;   // 资产名称 / 负债对象 / 借款对象
    public double amount; // 金额
    
    // 0: 资产 (Asset), 1: 负债 (Liability), 2: 借出 (Lent Out)
    public int type; 
    
    public long updateTime;

    public AssetAccount(String name, double amount, int type) {
        this.name = name;
        this.amount = amount;
        this.type = type;
        this.updateTime = System.currentTimeMillis();
    }
}