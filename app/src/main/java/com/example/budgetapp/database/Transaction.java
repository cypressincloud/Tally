package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long date;
    public int type;
    public String category;
    public double amount;
    public String note;
    public String remark;
    public int assetId;

    // 【新增】货币符号
    public String currencySymbol;

    public Transaction() {
    }

    @Ignore
    public Transaction(long date, int type, String category, double amount) {
        this(date, type, category, amount, "", "");
    }

    @Ignore
    public Transaction(long date, int type, String category, double amount, String note) {
        this(date, type, category, amount, note, "");
    }

    @Ignore
    public Transaction(long date, int type, String category, double amount, String note, String remark) {
        this.date = date;
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.note = note;
        this.remark = remark;
        this.assetId = 0;
        this.currencySymbol = "¥"; // 默认人民币
    }
}