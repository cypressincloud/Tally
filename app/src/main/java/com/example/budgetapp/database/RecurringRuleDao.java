package com.example.budgetapp.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RecurringRuleDao {
    @Insert
    long insert(RecurringRule rule);

    @Delete
    void delete(RecurringRule rule);

    @Update
    void update(RecurringRule rule);

    @Query("SELECT * FROM recurring_rules ORDER BY createTime DESC")
    LiveData<List<RecurringRule>> getAllRules();

    @Query("SELECT * FROM recurring_rules")
    List<RecurringRule> getAllRulesSync();

    @Query("SELECT * FROM recurring_rules WHERE isEnabled = 1")
    List<RecurringRule> getEnabledRulesSync();

    @Query("SELECT * FROM recurring_rules WHERE id = :ruleId")
    RecurringRule getRuleByIdSync(int ruleId);

    @Query("DELETE FROM recurring_rules")
    void deleteAll();
}
