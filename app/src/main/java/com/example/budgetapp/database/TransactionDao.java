package com.example.budgetapp.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TransactionDao {
    @Insert
    void insert(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    LiveData<List<Transaction>> getAllTransactions();

    // --- 【新增】用于备份还原的方法 ---

    // 1. 导出时使用：同步获取所有数据（不通过 LiveData，直接拿到 List）
    @Query("SELECT * FROM transactions")
    List<Transaction> getAllTransactionsSync();

    // 2. 导入时使用：批量插入
    @Insert
    void insertAll(List<Transaction> transactions);

    // 3. 导入时使用：清空旧数据
    @Query("DELETE FROM transactions")
    void deleteAll();
}