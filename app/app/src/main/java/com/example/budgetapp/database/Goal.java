package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "goals")
public class Goal {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public double targetAmount;
    public double savedAmount;
    public boolean isPriority;
    public long createdAt;

    // --- 新增的两个字段 ---
    public boolean isFinished;
    public long finishedDate;

    public Goal(String name, double targetAmount, double savedAmount, boolean isPriority, long createdAt) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.isPriority = isPriority;
        this.createdAt = createdAt;
        this.isFinished = false; // 默认未完成
        this.finishedDate = 0;
    }
}