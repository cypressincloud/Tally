package com.example.budgetapp.viewmodel;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

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
import com.example.budgetapp.widget.MonthSummaryWidget;
import com.example.budgetapp.widget.TodaySummaryWidget;

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
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.insert(transaction);
            notifyWidgetUpdate(); // 【新增】
        });
    }

    public void deleteTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.delete(transaction);
            notifyWidgetUpdate(); // 【新增】
        });
    }

    public void updateTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.update(transaction);
            notifyWidgetUpdate(); // 【新增】
        });
    }

    /**
     * 【增强】同步修改历史账单及对应的双边资产余额
     */
    public void updateTransactionWithAssetSync(Transaction oldTx, Transaction newTx) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                // 1. 处理己方支付账户（如微信、支付宝）的资产变更
                if (oldTx.assetId == newTx.assetId && oldTx.assetId != 0) {
                    AssetAccount asset = assetDao.getAssetByIdSync(oldTx.assetId);
                    if (asset != null) {
                        revertAssetBalance(asset, oldTx); // 先撤回旧金额
                        applyAssetBalance(asset, newTx);  // 再应用新金额
                        assetDao.update(asset);
                    }
                } else {
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

                // 2. 处理对方资产（负债/借出对象）的变更
                // a) 撤回旧对象的金额
                if (oldTx.type == 3 || oldTx.type == 4) {
                    if (oldTx.targetObject != null && !oldTx.targetObject.isEmpty()) {
                        int oldTargetType = (oldTx.type == 3) ? 1 : 2;
                        AssetAccount oldTargetAccount = assetDao.getAssetByNameAndType(oldTx.targetObject, oldTargetType);
                        if (oldTargetAccount != null) {
                            oldTargetAccount.amount -= oldTx.amount;
                            assetDao.update(oldTargetAccount);
                        }
                    }
                }
                // b) 追加新对象的金额
                if (newTx.type == 3 || newTx.type == 4) {
                    if (newTx.targetObject != null && !newTx.targetObject.isEmpty()) {
                        int newTargetType = (newTx.type == 3) ? 1 : 2;
                        AssetAccount newTargetAccount = assetDao.getAssetByNameAndType(newTx.targetObject, newTargetType);
                        if (newTargetAccount == null) {
                            newTargetAccount = new AssetAccount(newTx.targetObject, newTx.amount, newTargetType);
                            assetDao.insert(newTargetAccount);
                        } else {
                            newTargetAccount.amount += newTx.amount;
                            assetDao.update(newTargetAccount);
                        }
                    }
                }

                // 3. 最终更新数据库中的账单记录
                transactionDao.update(newTx);
            });
            notifyWidgetUpdate(); // 【新增】事务完成后通知刷新
        });
    }

    // 【增强】撤回账单对己方资产的影响 (兼容 0支出, 1收入, 3负债, 4借出)
    private void revertAssetBalance(AssetAccount asset, Transaction tx) {
        if (asset.type == 0 || asset.type == 2) {
            // 普通资产/借出账户：撤回支出(0)和借出(4)余额增加，撤回收入(1)和负债借入(3)余额减少
            if (tx.type == 0 || tx.type == 4) asset.amount += tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount -= tx.amount;
        } else if (asset.type == 1) {
            // 负债账户(信用卡)：撤回支出(0)和借出(4)负债减少，撤回收入(1)和负债借入(3)负债增加
            if (tx.type == 0 || tx.type == 4) asset.amount -= tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount += tx.amount;
        }
    }

    // 【增强】应用账单对己方资产的影响 (兼容 0支出, 1收入, 3负债, 4借出)
    private void applyAssetBalance(AssetAccount asset, Transaction tx) {
        if (asset.type == 0 || asset.type == 2) {
            if (tx.type == 0 || tx.type == 4) asset.amount -= tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount += tx.amount;
        } else if (asset.type == 1) {
            if (tx.type == 0 || tx.type == 4) asset.amount += tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount -= tx.amount;
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
     * 【增强】撤回账单功能
     * @param transaction 要删除的账单
     * @param targetAssetId 关联要恢复余额的己方资产ID
     */
    public void revokeTransaction(Transaction transaction, int targetAssetId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                // 1. 删除交易流水
                transactionDao.delete(transaction);

                // 2. 撤回己方支付账户余额 (如微信、支付宝)
                if (targetAssetId != 0) {
                    AssetAccount asset = assetDao.getAssetByIdSync(targetAssetId);
                    if (asset != null) {
                        if (asset.type == 0 || asset.type == 2) {
                            // 普通资产/借出账户：撤回支出(0)或借出(4)余额增加，撤回收入(1)或负债借入(3)余额减少
                            if (transaction.type == 0 || transaction.type == 4) asset.amount += transaction.amount;
                            else if (transaction.type == 1 || transaction.type == 3) asset.amount -= transaction.amount;
                        } else if (asset.type == 1) {
                            // 负债账户(信用卡)：撤回支出(0)/借出(4)负债减少，撤回收入(1)/借入(3)负债增加
                            if (transaction.type == 0 || transaction.type == 4) asset.amount -= transaction.amount;
                            else if (transaction.type == 1 || transaction.type == 3) asset.amount += transaction.amount;
                        }
                        assetDao.update(asset);
                    }
                }

                // 3. 撤回对方资产 (负债/借出对象) 并自动删除归零账户
                if (transaction.type == 3 || transaction.type == 4) {
                    if (transaction.targetObject != null && !transaction.targetObject.isEmpty()) {
                        int targetAssetType = (transaction.type == 3) ? 1 : 2; // 3->负债区(1), 4->借出区(2)
                        AssetAccount targetAccount = assetDao.getAssetByNameAndType(transaction.targetObject, targetAssetType);
                        if (targetAccount != null) {
                            // 撤回时，扣除这笔交易带来的欠款/借出金额
                            targetAccount.amount -= transaction.amount;

                            // 【预期效果实现】：如果撤回后，该对象欠款/借款金额归零（处理浮点精度 <= 0.01），则直接删除该资产
                            if (targetAccount.amount <= 0.01) {
                                assetDao.delete(targetAccount);
                            } else {
                                assetDao.update(targetAccount);
                            }
                        }
                    }
                }
            });
            notifyWidgetUpdate(); // 【新增】撤回完成后通知刷新
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
                } else if (asset.type == 1 || asset.type == 2) { // 【修改这里】兼容借出
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
                notifyWidgetUpdate(); // 【新增】
            }
        });
    }

    // ================= 业务逻辑：撤回与自动续费 =================
    // (在 ViewModel 中新增转移方法)

    /**
     * 资产转移：处理余额增减（包含优惠逻辑），并生成一条转账记录
     */
    public void transferAsset(AssetAccount fromAccount, AssetAccount toAccount, double amount, double discount, String note) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 实际扣款金额 = 设定转账金额 - 优惠金额
            double actualDeduct = amount - discount;

            // 1. 处理转出账户余额 (以实际扣款金额计算)
            if (fromAccount.type == 1) {
                // 从负债账户转出（例如用信用卡取现借出），意味着负债增加
                fromAccount.amount += actualDeduct;
            } else {
                // 从资产(0)、借出(2)、理财(3)转出，余额减少
                fromAccount.amount -= actualDeduct;
            }

            // 2. 处理转入账户余额 (目标账户全额入账或全额抵扣负债)
            if (toAccount.type == 1) {
                // 转入负债账户（例如还信用卡），负债减少目标全额
                toAccount.amount -= amount;
            } else {
                // 转入资产(0)、借出(2)、理财(3)，余额增加全额
                toAccount.amount += amount;
            }

            // 更新数据库中的资产信息
            assetDao.update(fromAccount);
            assetDao.update(toAccount);

            // 3. 生成对应的账单明细
            Transaction transaction = new Transaction();
            transaction.amount = actualDeduct; // 账单记录实际支出的金额
            transaction.type = 2; // 转账
            transaction.category = "资产互转";

            String noteContent = fromAccount.name + " -> " + toAccount.name;
            // 如果存在优惠，在备注里标明原单金额和优惠
            if (discount > 0) {
                noteContent += " (账单:" + amount + " 优惠:" + discount + ")";
            }
            transaction.note = noteContent + (note.isEmpty() ? "" : " | 备注: " + note);
            transaction.date = System.currentTimeMillis();
            transaction.assetId = fromAccount.id; // 关联转出账户

            transactionDao.insert(transaction);
            notifyWidgetUpdate();
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
    // 修改方法签名，增加 Float minAmount, Float maxAmount 参数
    public LiveData<List<Transaction>> getFilteredTransactions(long start, long end, Integer type, Float minAmount, Float maxAmount, String keyword) {
        // 如果你的 ViewModel 直接调用了 dao：
        return transactionDao.getFilteredTransactions(start, end, type, minAmount, maxAmount, keyword);
    }

    // ================= 通知桌面小组件刷新 =================
    private void notifyWidgetUpdate() {
        com.example.budgetapp.widget.WidgetUtils.updateAllWidgets(getApplication());
    }

}