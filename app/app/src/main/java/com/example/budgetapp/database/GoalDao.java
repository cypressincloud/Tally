package com.example.budgetapp.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface GoalDao {
    @Query("SELECT * FROM goals")
    LiveData<List<Goal>> getAllGoals();

    @Query("UPDATE goals SET isPriority = 0")
    void clearPriorities();

    @Insert
    void insert(Goal goal);

    @Update
    void update(Goal goal);

    @Delete
    void delete(Goal goal);
}