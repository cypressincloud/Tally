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

    @Query("SELECT * FROM transactions")
    List<Transaction> getAllTransactionsSync();

    @Insert
    void insertAll(List<Transaction> transactions);

    @Query("DELETE FROM transactions")
    void deleteAll();

    // 【新增】查询指定范围内的账单
    @Query("SELECT * FROM transactions WHERE date >= :start AND date <= :end")
    List<Transaction> getTransactionsByRange(long start, long end);
}