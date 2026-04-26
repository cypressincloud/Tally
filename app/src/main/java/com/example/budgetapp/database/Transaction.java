package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// 【优化】添加 indices 索引，极大提升查询和过滤速度
@Entity(tableName = "transactions",
        indices = {
                @Index("date"),
                @Index("type"),
                @Index("category")
        })
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long date;
    public int type; // 0支出, 1收入, 2转账
    public String category;
    public double amount;
    public String note;
    public String remark;
    public int assetId;
    public String currencySymbol;
    public String photoPath;
    // 【新增】二级分类
    public String subCategory;
    // 【新增】负债/借出对象
    public String targetObject;


    // 【新增】是否不计入预算 (默认 false，即计入预算)
    @androidx.room.ColumnInfo(defaultValue = "0")
    public boolean excludeFromBudget;

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
        this.currencySymbol = "¥";
        this.subCategory = ""; // 默认为空
        this.photoPath = "";
        this.excludeFromBudget = false; // 【新增】默认为false
    }
}