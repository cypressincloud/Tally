package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "asset_accounts")
public class AssetAccount {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public double amount;

    // 0: 资产, 1: 负债, 2: 借出, 3: 理财
    public int type;

    public long updateTime;
    public String currencySymbol;

    // === 新增理财专属字段 ===
    public boolean isFixedTerm; // true 定期, false 活期
    public int durationMonths;  // 存储时间(个月)
    public double interestRate; // 年利率(%)
    public double expectedReturn; // 预计结算最终资产

    public long depositDate; // <--- 【新增】存入时间(时间戳)

    // === 新增：计息方式 ===
    public boolean isCompoundInterest; // true 复利(利滚利), false 单利

    public AssetAccount(String name, double amount, int type) {
        this.name = name;
        this.amount = amount;
        this.type = type;
        this.updateTime = System.currentTimeMillis();
        this.currencySymbol = "¥";
    }
}