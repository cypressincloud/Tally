package com.example.budgetapp.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount; // 新增
import com.example.budgetapp.database.AssetAccountDao; // 新增
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import java.util.List;

public class FinanceViewModel extends AndroidViewModel {
    private final TransactionDao transactionDao;
    private final AssetAccountDao assetDao; // 新增
    private final LiveData<List<Transaction>> allTransactions;
    private final LiveData<List<AssetAccount>> allAssets; // 新增

    public FinanceViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        transactionDao = db.transactionDao();
        assetDao = db.assetAccountDao(); // 初始化
        allTransactions = transactionDao.getAllTransactions();
        allAssets = assetDao.getAllAssets(); // 初始化
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

    public void addTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.insert(transaction));
    }

    public void deleteTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.delete(transaction));
    }

    public void updateTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.update(transaction));
    }

    // --- 【新增】资产相关操作 ---

    public LiveData<List<AssetAccount>> getAllAssets() {
        return allAssets;
    }

    public void addAsset(AssetAccount asset) {
        AppDatabase.databaseWriteExecutor.execute(() -> assetDao.insert(asset));
    }

    public void updateAsset(AssetAccount asset) {
        AppDatabase.databaseWriteExecutor.execute(() -> assetDao.update(asset));
    }

    public void deleteAsset(AssetAccount asset) {
        AppDatabase.databaseWriteExecutor.execute(() -> assetDao.delete(asset));
    }
}