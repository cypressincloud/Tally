package com.example.budgetapp.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.AssetAccountDao;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.GoalDao;
import com.example.budgetapp.database.RenewalItem;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;

import java.util.List;

/**
 * 核心 ViewModel：管理所有财务数据，包括账单、资产和预算存储目标。
 */
public class FinanceViewModel extends AndroidViewModel {
    private final TransactionDao transactionDao;
    private final AssetAccountDao assetDao;
    private final GoalDao goalDao; // 新增 GoalDao
    private final AppDatabase database; // 显式持有数据库引用以供 DAO 访问

    private final LiveData<List<Transaction>> allTransactions;
    private final LiveData<List<AssetAccount>> allAssets;
    private final LiveData<List<Goal>> allGoals; // 新增 LiveData 观察存储目标

    public FinanceViewModel(@NonNull Application application) {
        super(application);
        // 1. 获取数据库实例
        database = AppDatabase.getDatabase(application);

        // 2. 初始化所有 DAO
        transactionDao = database.transactionDao();
        assetDao = database.assetAccountDao();
        goalDao = database.goalDao(); // 初始化新 DAO

        // 3. 初始化 LiveData (观察者模式)
        allTransactions = transactionDao.getAllTransactions();
        allAssets = assetDao.getAllAssets();
        allGoals = goalDao.getAllGoals(); // 获取所有目标
    }

    // ================= 账单记录 (Transaction) 相关 =================

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

    // ================= 资产账户 (Asset) 相关 =================

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

    // ================= 预算目标 (Goal) 相关 =================

    public LiveData<List<Goal>> getAllGoals() {
        return allGoals;
    }

    public void insertGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> goalDao.insert(goal));
    }

    public void deleteGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> goalDao.delete(goal));
    }

    /**
     * 设置唯一优先目标
     * 逻辑：清空之前所有的优先标记，将当前目标设为优先
     */
    public void setPriorityGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            goalDao.clearPriorities(); // 先将所有目标的 isPriority 设为 0
            goal.isPriority = true;
            goalDao.update(goal); // 更新当前目标的优先状态
        });
    }

    // ================= 业务逻辑：撤回与自动续费 =================

    /**
     * 撤回账单功能
     * @param transaction 要删除的账单
     * @param targetAssetId 关联要恢复余额的资产ID
     */
    public void revokeTransaction(Transaction transaction, int targetAssetId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. 删除账单
            transactionDao.delete(transaction);

            // 2. 如果选择了有效资产，则根据账单类型反向操作余额
            if (targetAssetId != 0) {
                AssetAccount asset = assetDao.getAssetByIdSync(targetAssetId);
                if (asset != null) {
                    if (asset.type == 0) { // 普通资产
                        if (transaction.type == 0) asset.amount += transaction.amount; // 撤回支出，钱回来
                        else if (transaction.type == 1) asset.amount -= transaction.amount; // 撤回收入，钱减掉
                    } else if (asset.type == 1) { // 负债账户 (信用卡等)
                        if (transaction.type == 0) asset.amount -= transaction.amount; // 撤回消费，负债减少
                        else if (transaction.type == 1) asset.amount += transaction.amount; // 撤回还款，负债增加
                    }
                    assetDao.update(asset);
                }
            }
        });
    }

    public void updateGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> goalDao.update(goal));
    }
    /**
     * 处理自动续费扣款逻辑
     */
    public void processAutoRenewal(RenewalItem renewal, int assetId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AssetAccount asset = assetDao.getAssetByIdSync(assetId);
            boolean canProcess = false;
            if (asset != null) {
                if (asset.type == 0 && asset.amount >= renewal.amount) {
                    asset.amount -= renewal.amount;
                    canProcess = true;
                } else if (asset.type == 1) { // 负债账户直接累加
                    asset.amount += renewal.amount;
                    canProcess = true;
                }
            }

            if (canProcess) {
                assetDao.update(asset);
                // 生成对应的账单明细
                Transaction transaction = new Transaction();
                transaction.amount = renewal.amount;
                transaction.type = 0;
                transaction.category = "自动续费";
                transaction.note = "项目: " + renewal.object;
                transaction.date = System.currentTimeMillis();
                transaction.assetId = assetId;
                transactionDao.insert(transaction);
            }
        });
    }
}