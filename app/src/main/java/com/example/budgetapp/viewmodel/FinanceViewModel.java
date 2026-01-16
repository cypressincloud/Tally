package com.example.budgetapp.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.AssetAccountDao;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import java.util.List;

public class FinanceViewModel extends AndroidViewModel {
    private final TransactionDao transactionDao;
    private final AssetAccountDao assetDao;
    private final LiveData<List<Transaction>> allTransactions;
    private final LiveData<List<AssetAccount>> allAssets;

    public FinanceViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        transactionDao = db.transactionDao();
        assetDao = db.assetAccountDao();
        allTransactions = transactionDao.getAllTransactions();
        allAssets = assetDao.getAllAssets();
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

    // --- 资产相关操作 ---

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

    // --- 新增：撤回功能 ---
    public void revokeTransaction(Transaction transaction, int targetAssetId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. 删除账单
            transactionDao.delete(transaction);

            // 2. 如果选择了有效资产，则更新余额
            if (targetAssetId != 0) {
                AssetAccount asset = assetDao.getAssetByIdSync(targetAssetId);
                if (asset != null) {
                    // 撤回逻辑：
                    // 如果原账单是支出(type=0)，撤回意味着钱回到了资产，资产增加
                    // 如果原账单是收入(type=1)，撤回意味着钱被扣除，资产减少
                    if (transaction.type == 0) {
                        asset.amount += transaction.amount;
                    } else if (transaction.type == 1) {
                        asset.amount -= transaction.amount;
                    }
                    assetDao.update(asset);
                }
            }
        });
    }
}