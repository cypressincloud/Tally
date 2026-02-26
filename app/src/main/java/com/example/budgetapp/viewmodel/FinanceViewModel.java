package com.example.budgetapp.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.AssetAccountDao;
import com.example.budgetapp.database.RenewalItem;
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

    // src/main/java/com/example/budgetapp/viewmodel/FinanceViewModel.java
    public void processAutoRenewal(RenewalItem renewal, int assetId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. 尝试扣减资产或增加负债
            AssetAccount asset = assetDao.getAssetByIdSync(assetId);
            boolean canProcess = false;
            if (asset != null) {
                if (asset.type == 0 && asset.amount >= renewal.amount) {
                    asset.amount -= renewal.amount;
                    canProcess = true;
                } else if (asset.type == 1) { // 如果是负债账户，直接增加负债额度
                    asset.amount += renewal.amount;
                    canProcess = true;
                }
            }

            if (canProcess) {
                assetDao.update(asset);

                // 2. 生成对应的支出账单，防止“总资产”计算时因缺少明细而对不上
                Transaction transaction = new Transaction();
                transaction.amount = renewal.amount;
                transaction.type = 0; // 支出
                transaction.category = "自动续费";
                transaction.note = "项目: " + renewal.object;
                transaction.date = System.currentTimeMillis();
                transaction.assetId = assetId;
                transactionDao.insert(transaction);
            } else {
                // 余额不足处理逻辑...
            }
        });
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
                    // 撤回逻辑：区分资产和负债
                    if (asset.type == 0) { // 普通资产
                        if (transaction.type == 0) {
                            asset.amount += transaction.amount; // 撤回支出，资产增加
                        } else if (transaction.type == 1) {
                            asset.amount -= transaction.amount; // 撤回收入，资产减少
                        }
                    } else if (asset.type == 1) { // 负债账户
                        if (transaction.type == 0) {
                            asset.amount -= transaction.amount; // 撤回支出(如刷信用卡)，负债减少
                        } else if (transaction.type == 1) {
                            asset.amount += transaction.amount; // 撤回收入(如还款)，负债增加
                        }
                    }
                    assetDao.update(asset);
                }
            }
        });
    }
}