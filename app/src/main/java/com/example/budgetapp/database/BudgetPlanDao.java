package com.example.budgetapp.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface BudgetPlanDao {
    @Query("SELECT * FROM budget_plans ORDER BY startDate DESC, createdAt DESC")
    LiveData<List<BudgetPlan>> getAllPlans();

    @Query("SELECT * FROM budget_plans ORDER BY startDate DESC, createdAt DESC")
    List<BudgetPlan> getAllPlansSync();

    @Query("SELECT * FROM budget_plans WHERE enabled = 1 AND startDate <= :date AND endDate >= :date ORDER BY startDate ASC")
    List<BudgetPlan> getActivePlansSync(long date);

    @Insert
    long insert(BudgetPlan plan);

    @Insert
    void insertAll(List<BudgetPlan> plans);

    @Update
    void update(BudgetPlan plan);

    @Delete
    void delete(BudgetPlan plan);

    @Query("DELETE FROM budget_plans")
    void deleteAll();
}
