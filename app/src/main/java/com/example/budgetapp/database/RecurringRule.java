package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 周期记账规则实体
 * 用于管理用户设置的周期性收支记录
 */
@Entity(tableName = "recurring_rules")
public class RecurringRule {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public int type;                  // 0: 支出, 1: 收入
    public double amount;             // 交易金额
    public String currencySymbol;     // 币种符号 (如 "¥", "$")
    public String category;           // 一级分类
    public String subCategory;        // 二级分类 (可选)
    public int assetId;               // 关联资产账户 ID
    
    public String periodUnit;         // 周期单位: "DAY", "WEEK", "MONTH", "YEAR"
    public int periodInterval;        // 周期步长: 如 1(每月), 2(每2周)
    
    public long startDate;            // 开始日期毫秒时间戳 (截断至 00:00:00)
    public long endDate;              // 结束日期毫秒时间戳 (可为 0 或 Long.MAX_VALUE 表示无限)
    public String triggerTime;        // 每日生效具体时刻，如 "00:00"
    
    public String note;               // 备注
    public boolean isEnabled;         // 规则启用开关
    public long createTime;           // 创建时间

    public RecurringRule() {
        this.isEnabled = true;
        this.createTime = System.currentTimeMillis();
        this.triggerTime = "00:00";
        this.subCategory = "";
        this.note = "";
        this.endDate = 0; // 0 表示无限循环
    }
}
