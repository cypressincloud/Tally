package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A spending budget covering a concrete date range. */
@Entity(tableName = "budget_plans")
public class BudgetPlan {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public long startDate;
    public long endDate;
    public double totalAmount;
    public boolean enabled;
    public boolean settled;
    /** Amount transferred to savings goals when this plan was settled. */
    public double allocatedToGoals;
    public long createdAt;

    public BudgetPlan(String name, long startDate, long endDate, double totalAmount) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalAmount = totalAmount;
        this.enabled = true;
        this.settled = false;
        this.allocatedToGoals = 0;
        this.createdAt = System.currentTimeMillis();
    }
}
