package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

// src/main/java/com/example/budgetapp/database/Goal.java
@Entity(tableName = "goals")
public class Goal {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public double targetAmount;
    public double savedAmount; // 初始已存/手动调整后的基础金额
    public boolean isPriority;
    public long createdAt; // 新增：创建时间戳（当日零点）

    public Goal(String name, double targetAmount, double savedAmount, boolean isPriority, long createdAt) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.isPriority = isPriority;
        this.createdAt = createdAt;
    }
}