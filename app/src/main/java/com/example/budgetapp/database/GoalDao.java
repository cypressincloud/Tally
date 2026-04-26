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

    // 【新增】同步获取所有的 Goal，用于 WebDAV 上传打包
    @Query("SELECT * FROM goals")
    List<Goal> getAllGoalsSync();

    @Query("UPDATE goals SET isPriority = 0")
    void clearPriorities();

    @Insert
    void insert(Goal goal);

    // 【新增】批量插入，用于 WebDAV 下载同步时恢复数据
    @Insert
    void insertAll(List<Goal> goals);

    @Update
    void update(Goal goal);

    @Delete
    void delete(Goal goal);

    // 【新增】清空所有，用于 WebDAV 下载覆盖前清理旧数据
    @Query("DELETE FROM goals")
    void deleteAll();
}