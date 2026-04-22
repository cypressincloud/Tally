package com.example.budgetapp.database;

/**
 * 专供年视图和统计页使用的高性能轻量级对象
 * 只包含计算日结余必需的 3 个字段，极大地节省内存和查询时间
 */
public class TransactionMinimal {
    public long date;
    public int type;
    public double amount;
}