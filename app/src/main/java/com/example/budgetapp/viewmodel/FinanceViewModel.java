package com.example.budgetapp.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

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

    // ================= 新增：动态查询所需变量 =================
    // 存储当前请求的时间范围：[0]是start，[1]是end
    private final MutableLiveData<long[]> currentRangeFilter = new MutableLiveData<>();

    // 动态观察该时间段内的账单
    private final LiveData<List<Transaction>> rangeTransactions;
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

        // 新增：利用 Transformations.switchMap 实现只要 currentRangeFilter 变化，就自动去数据库查新范围的数据
        rangeTransactions = Transformations.switchMap(currentRangeFilter, range -> {
            if (range == null || range.length != 2) {
                return new MutableLiveData<>();
            }
            return transactionDao.getTransactionsByRangeLive(range[0], range[1]);
        });
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

    // 保留原方法，供只修改照片、备注等不涉及金额变动的场景使用
    public void updateTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> transactionDao.update(transaction));
    }

    /**
     * 【新增】同步修改历史账单及对应的资产余额
     * 解决修改历史账单金额、类型或账户时，资产未同步变化的问题。
     */
    public void updateTransactionWithAssetSync(Transaction oldTx, Transaction newTx) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. 处理资产变更
            if (oldTx.assetId == newTx.assetId && oldTx.assetId != 0) {
                // 资产账户未变，计算并应用差额
                AssetAccount asset = assetDao.getAssetByIdSync(oldTx.assetId);
                if (asset != null) {
                    revertAssetBalance(asset, oldTx); // 先撤回旧金额
                    applyAssetBalance(asset, newTx);  // 再应用新金额
                    assetDao.update(asset);
                }
            } else {
                // 资产账户发生了改变，分别撤回旧账户金额，追加到新账户
                if (oldTx.assetId != 0) {
                    AssetAccount oldAsset = assetDao.getAssetByIdSync(oldTx.assetId);
                    if (oldAsset != null) {
                        revertAssetBalance(oldAsset, oldTx);
                        assetDao.update(oldAsset);
                    }
                }
                if (newTx.assetId != 0) {
                    AssetAccount newAsset = assetDao.getAssetByIdSync(newTx.assetId);
                    if (newAsset != null) {
                        applyAssetBalance(newAsset, newTx);
                        assetDao.update(newAsset);
                    }
                }
            }
            // 2. 最终更新数据库中的账单记录
            transactionDao.update(newTx);
        });
    }

    // 【新增辅助方法】撤回账单对资产的影响
    private void revertAssetBalance(AssetAccount asset, Transaction tx) {
        if (asset.type == 0) { // 普通资产：撤回支出余额增加，撤回收入余额减少
            if (tx.type == 0) asset.amount += tx.amount;
            else if (tx.type == 1) asset.amount -= tx.amount;
        } else if (asset.type == 1) { // 负债账户(信用卡)：撤回支出负债减少，撤回收入负债增加
            if (tx.type == 0) asset.amount -= tx.amount;
            else if (tx.type == 1) asset.amount += tx.amount;
        }
    }

    // 【新增辅助方法】应用账单对资产的影响
    private void applyAssetBalance(AssetAccount asset, Transaction tx) {
        if (asset.type == 0) { // 普通资产：支出余额减少，收入余额增加
            if (tx.type == 0) asset.amount -= tx.amount;
            else if (tx.type == 1) asset.amount += tx.amount;
        } else if (asset.type == 1) { // 负债账户(信用卡)：支出负债增加，收入负债减少
            if (tx.type == 0) asset.amount += tx.amount;
            else if (tx.type == 1) asset.amount -= tx.amount;
        }
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

    // ================= 业务逻辑：撤回与自动续费 =================
    // (在 ViewModel 中新增转移方法)

    /**
     * 资产转移：处理余额增减，并生成一条转账记录
     */
    public void transferAsset(AssetAccount fromAccount, AssetAccount toAccount, double amount, String note) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. 处理转出账户余额
            if (fromAccount.type == 1) {
                // 从负债账户转出（例如用信用卡取现借出），意味着负债增加
                fromAccount.amount += amount;
            } else {
                // 从资产(0)、借出(2)、理财(3)转出，余额减少
                fromAccount.amount -= amount;
            }

            // 2. 处理转入账户余额
            if (toAccount.type == 1) {
                // 转入负债账户（例如还信用卡），负债减少
                toAccount.amount -= amount;
            } else {
                // 转入资产(0)、借出(2)、理财(3)，余额增加
                toAccount.amount += amount;
            }

            // 更新数据库中的资产信息
            assetDao.update(fromAccount);
            assetDao.update(toAccount);

            // 3. 生成对应的账单明细
            Transaction transaction = new Transaction();
            transaction.amount = amount;
            // ⚠️使用 2 代表转账，避免转账被混淆统计入常规的“支出(0)”或“收入(1)”中
            // 需要确保你的 DetailsAdapter 和 StatsFragment 支持或过滤 type = 2 的情况
            transaction.type = 2;
            transaction.category = "资产互转";
            String noteContent = fromAccount.name + " -> " + toAccount.name;
            transaction.note = noteContent + (note.isEmpty() ? "" : " (" + note + ")");
            transaction.date = System.currentTimeMillis();
            transaction.assetId = fromAccount.id; // 关联转出账户

            transactionDao.insert(transaction);
        });
    }

// ================= 新增：动态按需加载 API =================

    /**
     * Fragment 调用此方法设置当前要查看的时间范围
     */
    public void setDateRange(long startMillis, long endMillis) {
        currentRangeFilter.setValue(new long[]{startMillis, endMillis});
    }

    /**
     * Fragment 观察此 LiveData 获取按需加载的账单数据
     */
    public LiveData<List<Transaction>> getRangeTransactions() {
        return rangeTransactions;
    }

    /**
     * 直接获取指定时间段的总收支（用于顶部面板统计）
     */
    public LiveData<Double> getTotalAmountByType(long start, long end, int type) {
        return transactionDao.getTotalAmountByTypeLive(start, end, type);
    }

    /**
     * 获取指定时间段的加班总金额
     */
    public LiveData<Double> getOvertimeTotalAmount(long start, long end) {
        return transactionDao.getOvertimeTotalAmountLive(start, end);
    }

    /**
     * 供 DetailsFragment 使用：直接从数据库进行多条件混合查询
     */
    public LiveData<List<Transaction>> getFilteredTransactions(long start, long end, Integer type, String category) {
        return transactionDao.getFilteredTransactionsLive(start, end, type, category);
    }

}