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
    
    // 【新增】关联资产ID，默认为0 (表示无关联)
    public int assetId;

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
        this.assetId = 0; // 默认不关联
    }
}