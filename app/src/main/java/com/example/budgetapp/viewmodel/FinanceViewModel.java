package com.example.budgetapp.viewmodel;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.AssetAccountDao;
import com.example.budgetapp.database.BudgetPlan;
import com.example.budgetapp.database.BudgetPlanDao;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.GoalDao;
import com.example.budgetapp.database.RenewalItem;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import com.example.budgetapp.widget.MonthSummaryWidget;
import com.example.budgetapp.widget.TodaySummaryWidget;
import com.example.budgetapp.util.AutoCategoryRule;
import com.example.budgetapp.util.AutoCategoryRuleManager;
import com.example.budgetapp.util.BudgetCalculator;

import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

/**
 * 核心 ViewModel：管理所有财务数据，包括账单、资产和预算存储目标。
 */
public class FinanceViewModel extends AndroidViewModel {
    private final TransactionDao transactionDao;
    private final AssetAccountDao assetDao;
    private final GoalDao goalDao; // 新增 GoalDao
    private final BudgetPlanDao budgetPlanDao;
    private final AppDatabase database; // 显式持有数据库引用以供 DAO 访问

    private final LiveData<List<Transaction>> allTransactions;
    private final LiveData<List<AssetAccount>> allAssets;
    private final LiveData<List<Goal>> allGoals; // 新增 LiveData 观察存储目标
    private final LiveData<List<BudgetPlan>> allBudgetPlans;

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
        budgetPlanDao = database.budgetPlanDao();

        // 3. 初始化 LiveData (观察者模式)
        allTransactions = transactionDao.getAllTransactions();
        allAssets = assetDao.getAllAssets();
        allGoals = goalDao.getAllGoals(); // 获取所有目标
        allBudgetPlans = budgetPlanDao.getAllPlans();

        // 新增：利用 Transformations.switchMap 实现只要 currentRangeFilter 变化，就自动去数据库查新范围的数据
        rangeTransactions = Transformations.switchMap(currentRangeFilter, range -> {
            if (range == null || range.length != 2) {
                return new MutableLiveData<>();
            }
            return transactionDao.getTransactionsByRangeLive(range[0], range[1]);
        });
        migrateLegacyBudgetIfNeeded();
    }

    private void migrateLegacyBudgetIfNeeded() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                if (!budgetPlanDao.getAllPlansSync().isEmpty()) return;
                SharedPreferences prefs = getApplication().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                float amount = prefs.getFloat("monthly_budget", 0f);
                if (amount <= 0) return;
                LocalDate start = LocalDate.now().withDayOfMonth(1);
                long legacyStart = prefs.getLong("budget_start_time", 0);
                if (legacyStart > 0) {
                    start = Instant.ofEpochMilli(legacyStart).atZone(ZoneId.systemDefault()).toLocalDate();
                }
                LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                budgetPlanDao.insert(new BudgetPlan("旧版月度预算",
                        start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), amount));
            });
        });
    }

    /**
     * 【新增】同步增加账单及对应的双边资产余额（用于 AI 记账和正常入账）
     */
    public void addTransactionWithAssetSync(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            applyAutoCategoryRule(transaction);
            database.runInTransaction(() -> {
                // 1. 处理己方支付账户（如微信、支付宝）的资产变更
                if (transaction.assetId != 0) {
                    AssetAccount asset = assetDao.getAssetByIdSync(transaction.assetId);
                    if (asset != null) {
                        applyAssetBalance(asset, transaction); // 调用已有的加减逻辑
                        assetDao.update(asset);
                    }
                }

                // 2. 处理对方资产（负债/借出对象）的变更
                if (transaction.type == 3 || transaction.type == 4) {
                    // 类型3（负债借入）或类型4（借出）：增加对应的负债/借出账户金额
                    if (transaction.targetObject != null && !transaction.targetObject.isEmpty()) {
                        int targetType = (transaction.type == 3) ? 1 : 2;
                        AssetAccount targetAccount = assetDao.getAssetByNameAndType(transaction.targetObject, targetType);
                        if (targetAccount == null) {
                            // 如果是新对象，直接创建这个资产
                            targetAccount = new AssetAccount(transaction.targetObject, transaction.amount, targetType);
                            assetDao.insert(targetAccount);
                        } else if (targetAccount.id != transaction.assetId) {
                            // 如果已有对象，累加欠款/借出额
                            targetAccount.amount += transaction.amount;
                            assetDao.update(targetAccount);
                        }
                    }
                } else if (transaction.type == 0 && transaction.note != null && !transaction.note.isEmpty()) {
                    // 类型0（支出）：检查备注是否匹配负债账户名称，如果匹配则减少负债
                    AssetAccount liabilityAccount = assetDao.getAssetByNameAndType(transaction.note, 1);
                    if (liabilityAccount != null && liabilityAccount.id != transaction.assetId) {
                        liabilityAccount.amount -= transaction.amount;
                        // 如果负债已还清，可以选择删除账户或保留为0
                        if (liabilityAccount.amount <= 0) {
                            liabilityAccount.amount = 0;
                        }
                        assetDao.update(liabilityAccount);
                    }
                } else if (transaction.type == 1 && transaction.note != null && !transaction.note.isEmpty()) {
                    // 类型1（收入）：检查备注是否匹配借出账户名称，如果匹配则减少借出
                    AssetAccount lentAccount = assetDao.getAssetByNameAndType(transaction.note, 2);
                    if (lentAccount != null && lentAccount.id != transaction.assetId) {
                        lentAccount.amount -= transaction.amount;
                        // 如果借出已收回，可以选择删除账户或保留为0
                        if (lentAccount.amount <= 0) {
                            lentAccount.amount = 0;
                        }
                        assetDao.update(lentAccount);
                    }
                }

                // 3. 最终把账单记录插进数据库
                transactionDao.insert(transaction);
            });
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
            notifyWidgetUpdate(); // 事务完成后通知桌面小部件刷新
        });
    }

    // ================= 账单记录 (Transaction) 相关 =================

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

    public void addTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            applyAutoCategoryRule(transaction);
            transactionDao.insert(transaction);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
            notifyWidgetUpdate(); // 【新增】
        });
    }

    /** Applies user classification rules to manually created and AI-created transactions. */
    private void applyAutoCategoryRule(Transaction transaction) {
        if (transaction == null || transaction.note == null) return;
        AutoCategoryRule rule = AutoCategoryRuleManager.findMatch(
                getApplication(), "", transaction.note, transaction.type);
        if (rule != null) {
            transaction.type = rule.getTargetType();
            transaction.category = rule.getCategory();
            transaction.subCategory = rule.getSubCategory();
            return;
        }
        AutoCategoryRuleManager.DefaultCategory fallback = AutoCategoryRuleManager.findDefault(
                getApplication(), "", transaction.type);
        if (fallback != null) {
            transaction.category = fallback.category;
            transaction.subCategory = fallback.subCategory;
        }
    }

    public void deleteTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.runInTransaction(() -> {
                if (transaction.type == 2) {
                    revertTransferBalance(transaction);
                } else {
                    revertTransactionLinkedBalances(transaction, transaction.assetId);
                }
                transactionDao.delete(transaction);
            });
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
            notifyWidgetUpdate(); // 【新增】
        });
    }

    public void updateTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.update(transaction);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
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
                // a) 撤回旧交易对负债/借出账户的影响
                if (oldTx.type == 3 || oldTx.type == 4) {
                    // 旧交易是负债借入或借出：减少对应账户金额
                    if (oldTx.targetObject != null && !oldTx.targetObject.isEmpty()) {
                        int oldTargetType = (oldTx.type == 3) ? 1 : 2;
                        AssetAccount oldTargetAccount = assetDao.getAssetByNameAndType(oldTx.targetObject, oldTargetType);
                        if (oldTargetAccount != null && oldTargetAccount.id != oldTx.assetId) {
                            oldTargetAccount.amount -= oldTx.amount;
                            if (oldTargetAccount.amount <= 0) {
                                oldTargetAccount.amount = 0;
                            }
                            assetDao.update(oldTargetAccount);
                        }
                    }
                } else if (oldTx.type == 0 && oldTx.note != null && !oldTx.note.isEmpty()) {
                    // 旧交易是支出还款：撤回时增加负债
                    AssetAccount liabilityAccount = assetDao.getAssetByNameAndType(oldTx.note, 1);
                    if (liabilityAccount != null && liabilityAccount.id != oldTx.assetId) {
                        liabilityAccount.amount += oldTx.amount;
                        assetDao.update(liabilityAccount);
                    }
                } else if (oldTx.type == 1 && oldTx.note != null && !oldTx.note.isEmpty()) {
                    // 旧交易是收入收款：撤回时增加借出
                    AssetAccount lentAccount = assetDao.getAssetByNameAndType(oldTx.note, 2);
                    if (lentAccount != null && lentAccount.id != oldTx.assetId) {
                        lentAccount.amount += oldTx.amount;
                        assetDao.update(lentAccount);
                    }
                }
                
                // b) 应用新交易对负债/借出账户的影响
                if (newTx.type == 3 || newTx.type == 4) {
                    // 新交易是负债借入或借出：增加对应账户金额
                    if (newTx.targetObject != null && !newTx.targetObject.isEmpty()) {
                        int newTargetType = (newTx.type == 3) ? 1 : 2;
                        AssetAccount newTargetAccount = assetDao.getAssetByNameAndType(newTx.targetObject, newTargetType);
                        if (newTargetAccount == null) {
                            newTargetAccount = new AssetAccount(newTx.targetObject, newTx.amount, newTargetType);
                            assetDao.insert(newTargetAccount);
                        } else if (newTargetAccount.id != newTx.assetId) {
                            newTargetAccount.amount += newTx.amount;
                            assetDao.update(newTargetAccount);
                        }
                    }
                } else if (newTx.type == 0 && newTx.note != null && !newTx.note.isEmpty()) {
                    // 新交易是支出还款：减少负债
                    AssetAccount liabilityAccount = assetDao.getAssetByNameAndType(newTx.note, 1);
                    if (liabilityAccount != null && liabilityAccount.id != newTx.assetId) {
                        liabilityAccount.amount -= newTx.amount;
                        if (liabilityAccount.amount <= 0) {
                            liabilityAccount.amount = 0;
                        }
                        assetDao.update(liabilityAccount);
                    }
                } else if (newTx.type == 1 && newTx.note != null && !newTx.note.isEmpty()) {
                    // 新交易是收入收款：减少借出
                    AssetAccount lentAccount = assetDao.getAssetByNameAndType(newTx.note, 2);
                    if (lentAccount != null && lentAccount.id != newTx.assetId) {
                        lentAccount.amount -= newTx.amount;
                        if (lentAccount.amount <= 0) {
                            lentAccount.amount = 0;
                        }
                        assetDao.update(lentAccount);
                    }
                }

                // 3. 最终更新数据库中的账单记录
                transactionDao.update(newTx);
            });
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
            notifyWidgetUpdate(); // 【新增】事务完成后通知刷新
        });
    }

    // 【增强】撤回账单对己方资产的影响 (兼容 0支出, 1收入, 3负债, 4借出)
    private void revertAssetBalance(AssetAccount asset, Transaction tx) {
        if (asset.type == 0 || asset.type == 3) {
            // 普通资产/理财账户：撤回支出(0)和借出(4)余额增加，撤回收入(1)和负债借入(3)余额减少
            if (tx.type == 0 || tx.type == 4) asset.amount += tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount -= tx.amount;
        } else if (asset.type == 1) {
            // 负债账户(信用卡)：撤回支出(0)和借出(4)负债减少，撤回收入(1)和负债借入(3)负债增加
            if (tx.type == 0 || tx.type == 4) asset.amount -= tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount += tx.amount;
        } else if (asset.type == 2) {
            // 借出账户：撤回支出(0)和借出(4)借出减少，撤回收入(1)和负债借入(3)借出增加（撤回还款）
            if (tx.type == 0 || tx.type == 4) asset.amount -= tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount += tx.amount;
        }
    }

    // 【增强】应用账单对己方资产的影响 (兼容 0支出, 1收入, 3负债, 4借出)
    private void applyAssetBalance(AssetAccount asset, Transaction tx) {
        if (asset.type == 0 || asset.type == 3) {
            // 普通资产/理财账户：支出(0)和借出(4)余额减少，收入(1)和负债借入(3)余额增加
            if (tx.type == 0 || tx.type == 4) asset.amount -= tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount += tx.amount;
        } else if (asset.type == 1) {
            // 负债账户(信用卡)：支出(0)和借出(4)负债增加，收入(1)和负债借入(3)负债减少（还债）
            if (tx.type == 0 || tx.type == 4) asset.amount += tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount -= tx.amount;
        } else if (asset.type == 2) {
            // 借出账户：支出(0)和借出(4)借出增加，收入(1)和负债借入(3)借出减少（对方还钱）
            if (tx.type == 0 || tx.type == 4) asset.amount += tx.amount;
            else if (tx.type == 1 || tx.type == 3) asset.amount -= tx.amount;
        }
    }

    private void revertTransactionLinkedBalances(Transaction transaction, int fallbackAssetId) {
        int assetId = fallbackAssetId != 0 ? fallbackAssetId : transaction.assetId;
        if (assetId != 0) {
            AssetAccount asset = assetDao.getAssetByIdSync(assetId);
            if (asset != null) {
                revertAssetBalance(asset, transaction);
                assetDao.update(asset);
            }
        }

        if (transaction.type == 3 || transaction.type == 4) {
            if (transaction.targetObject != null && !transaction.targetObject.isEmpty()) {
                int targetType = (transaction.type == 3) ? 1 : 2;
                AssetAccount target = assetDao.getAssetByNameAndType(transaction.targetObject, targetType);
                if (target != null && target.id != assetId) {
                    target.amount -= transaction.amount;
                    if (target.amount <= 0.01) assetDao.delete(target);
                    else assetDao.update(target);
                }
            }
        } else if (transaction.type == 0 && transaction.note != null && !transaction.note.isEmpty()) {
            AssetAccount liability = assetDao.getAssetByNameAndType(transaction.note, 1);
            if (liability != null && liability.id != assetId) {
                liability.amount += transaction.amount;
                assetDao.update(liability);
            }
        } else if (transaction.type == 1 && transaction.note != null && !transaction.note.isEmpty()) {
            AssetAccount lent = assetDao.getAssetByNameAndType(transaction.note, 2);
            if (lent != null && lent.id != assetId) {
                lent.amount += transaction.amount;
                assetDao.update(lent);
            }
        }
    }

    private void revertTransferBalance(Transaction transaction) {
        AssetAccount fromAccount = assetDao.getAssetByIdSync(transaction.assetId);
        if (fromAccount != null) {
            if (fromAccount.type == 1) fromAccount.amount -= transaction.amount;
            else fromAccount.amount += transaction.amount;
            assetDao.update(fromAccount);
        }

        AssetAccount toAccount = findTransferTargetAccount(transaction);
        if (toAccount != null) {
            double targetAmount = getTransferTargetAmount(transaction, toAccount);
            if (toAccount.type == 1) toAccount.amount += targetAmount;
            else toAccount.amount -= targetAmount;
            assetDao.update(toAccount);
        }
    }

    private AssetAccount findTransferTargetAccount(Transaction transaction) {
        if (transaction.targetObject != null && !transaction.targetObject.isEmpty()) {
            try {
                AssetAccount target = assetDao.getAssetByIdSync(Integer.parseInt(transaction.targetObject));
                if (target != null) return target;
            } catch (NumberFormatException ignored) {}
        }

        String note = transaction.note == null ? "" : transaction.note;
        int arrow = note.indexOf(" -> ");
        if (arrow < 0) return null;
        String afterArrow = note.substring(arrow + 4).trim();
        int metadataStart = afterArrow.indexOf(" | ");
        if (metadataStart >= 0) afterArrow = afterArrow.substring(0, metadataStart).trim();

        AssetAccount bestMatch = null;
        for (AssetAccount account : assetDao.getAllAssetsSync()) {
            if (account.id == transaction.assetId || account.name == null || account.name.isEmpty()) continue;
            if (afterArrow.startsWith(account.name)
                    && (bestMatch == null || account.name.length() > bestMatch.name.length())) {
                bestMatch = account;
            }
        }
        return bestMatch;
    }

    private double getTransferTargetAmount(Transaction transaction, AssetAccount targetAccount) {
        String note = transaction.note == null ? "" : transaction.note;
        int arrow = note.indexOf(" -> ");
        if (arrow >= 0 && targetAccount.name != null) {
            String afterArrow = note.substring(arrow + 4).trim();
            if (afterArrow.startsWith(targetAccount.name)) {
                afterArrow = afterArrow.substring(targetAccount.name.length());
            }
            java.util.regex.Matcher amountMatcher = java.util.regex.Pattern
                    .compile("\\([^0-9]*([0-9]+(?:\\.[0-9]+)?)")
                    .matcher(afterArrow);
            if (amountMatcher.find()) {
                try {
                    return Double.parseDouble(amountMatcher.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
        return Math.max(0, transaction.amount);
    }

    // ================= 资产账户 (Asset) 相关 =================

    public LiveData<List<AssetAccount>> getAllAssets() {
        return allAssets;
    }

    public void addAsset(AssetAccount asset) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            assetDao.insert(asset);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
    }

    public void updateAsset(AssetAccount asset) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            assetDao.update(asset);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
    }

    public void deleteAsset(AssetAccount asset) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            assetDao.delete(asset);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
    }

    // ================= 预算目标 (Goal) 相关 =================

    public LiveData<List<Goal>> getAllGoals() {
        return allGoals;
    }

    public LiveData<List<BudgetPlan>> getAllBudgetPlans() {
        return allBudgetPlans;
    }

    public void insertBudgetPlan(BudgetPlan plan) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            budgetPlanDao.insert(plan);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
    }

    public void updateBudgetPlan(BudgetPlan plan) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            budgetPlanDao.update(plan);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
    }

    public void deleteBudgetPlan(BudgetPlan plan) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            budgetPlanDao.delete(plan);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
    }

    /** Settles each expired plan once and evenly adds its non-negative daily surplus to goals. */
    public void settleExpiredBudgetPlans() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean[] changed = {false};
            database.runInTransaction(() -> {
                LocalDate today = LocalDate.now();
                List<BudgetPlan> plans = budgetPlanDao.getAllPlansSync();
                List<Goal> goals = goalDao.getAllGoalsSync();
                List<Transaction> transactions = transactionDao.getAllTransactionsSync();
                for (BudgetPlan plan : plans) {
                    LocalDate end = Instant.ofEpochMilli(plan.endDate).atZone(ZoneId.systemDefault()).toLocalDate();
                    if (!plan.enabled || plan.settled || !end.isBefore(today)) continue;
                    double surplus = BudgetCalculator.remainingAmount(plan, transactions);
                    List<Goal> activeGoals = new ArrayList<>();
                    for (Goal goal : goals) {
                        LocalDate created = Instant.ofEpochMilli(goal.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
                        LocalDate finished = goal.finishedDate > 0
                                ? Instant.ofEpochMilli(goal.finishedDate).atZone(ZoneId.systemDefault()).toLocalDate() : null;
                        if (!created.isAfter(end) && (!goal.isFinished || finished == null || !finished.isBefore(end))) {
                            activeGoals.add(goal);
                        }
                    }
                    Map<Integer, Double> allocation = BudgetCalculator.distributeEvenly(surplus, activeGoals);
                    for (Goal goal : activeGoals) {
                        Double value = allocation.get(goal.id);
                        if (value != null) { goal.savedAmount += value; goalDao.update(goal); }
                    }
                    plan.allocatedToGoals = allocation.values().stream()
                            .mapToDouble(Double::doubleValue).sum();
                    plan.settled = true;
                    budgetPlanDao.update(plan);
                    changed[0] = true;
                }
            });
            if (changed[0]) {
                com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
            }
        });
    }

    public void insertGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            goalDao.insert(goal);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
    }

    public void deleteGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            goalDao.delete(goal);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
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
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
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
                // 2. 撤回交易对己方资产及负债/借出账户的影响
                revertTransactionLinkedBalances(transaction, targetAssetId);
            });
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
            notifyWidgetUpdate(); // 【新增】撤回完成后通知刷新
        });
    }

    public void updateGoal(Goal goal) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            goalDao.update(goal);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
        });
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
                com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
                notifyWidgetUpdate(); // 【新增】
            }
        });
    }

    // ================= 业务逻辑：撤回与自动续费 =================
    // (在 ViewModel 中新增转移方法)

    /**
     * 资产转移：处理余额增减（包含优惠逻辑和货币转换），并生成一条转账记录
     */
    public void transferAsset(AssetAccount fromAccount, AssetAccount toAccount, double amount, double discount, String note) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 实际扣款金额 = 设定转账金额 - 优惠金额
            double actualDeduct = amount - discount;

            // 【新增】货币转换逻辑
            SharedPreferences prefs = getApplication().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE);
            boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
            boolean singleCurrencyMode = prefs.getBoolean("single_currency_mode", false);
            
            // 如果启用了货币功能且开启了统一货币模式，需要进行汇率转换
            if (isCurrencyEnabled && singleCurrencyMode) {
                String fromCurrencySymbol = (fromAccount.currencySymbol != null && !fromAccount.currencySymbol.isEmpty()) 
                    ? fromAccount.currencySymbol : "¥";
                String toCurrencySymbol = (toAccount.currencySymbol != null && !toAccount.currencySymbol.isEmpty()) 
                    ? toAccount.currencySymbol : "¥";
                
                String fromCurrencyCode = getCurrencyCode(fromCurrencySymbol);
                String toCurrencyCode = getCurrencyCode(toCurrencySymbol);
                
                // 如果转出和转入账户的货币不同，需要进行汇率转换
                if (!fromCurrencyCode.equals(toCurrencyCode)) {
                    // 使用汇率管理器进行同步转换
                    // 注意：这里使用简化处理，实际应该异步处理并在UI层显示转换结果
                    // 但考虑到 ViewModel 已经在后台线程执行，我们直接同步获取缓存汇率
                    com.example.budgetapp.util.ExchangeRateManager rateManager = 
                        new com.example.budgetapp.util.ExchangeRateManager(getApplication());
                    
                    // 从缓存获取汇率（这里使用简化逻辑，假设缓存已存在）
                    // 实际转账金额按汇率转换
                    double convertedAmount = convertAmountSync(rateManager, amount, fromCurrencyCode, toCurrencyCode);
                    double convertedDiscount = convertAmountSync(rateManager, discount, fromCurrencyCode, toCurrencyCode);
                    if (Double.isNaN(convertedAmount) || Double.isNaN(convertedDiscount)) {
                        android.util.Log.e("FinanceViewModel", "Missing exchange rate; transfer aborted");
                        return;
                    }
                    double convertedActualDeduct = convertedAmount - convertedDiscount;
                    
                    // 1. 处理转出账户余额 (以转出币种的实际扣款金额计算)
                    if (fromAccount.type == 1) {
                        fromAccount.amount += actualDeduct;
                    } else {
                        fromAccount.amount -= actualDeduct;
                    }

                    // 2. 处理转入账户余额 (以转入币种的转换后金额计算)
                    if (toAccount.type == 1) {
                        toAccount.amount -= convertedAmount;
                    } else {
                        toAccount.amount += convertedAmount;
                    }

                    // 更新数据库中的资产信息
                    assetDao.update(fromAccount);
                    assetDao.update(toAccount);

                    // 3. 生成对应的账单明细
                    Transaction transaction = new Transaction();
                    transaction.amount = actualDeduct; // 账单记录实际支出的金额（转出币种）
                    transaction.type = 2; // 转账
                    transaction.category = "资产互转";

                    String noteContent = fromAccount.name + "(" + fromCurrencySymbol + String.format("%.2f", amount) + ") -> " 
                                       + toAccount.name + "(" + toCurrencySymbol + String.format("%.2f", convertedAmount) + ")";
                    if (discount > 0) {
                        noteContent += " (优惠:" + discount + ")";
                    }
                    transaction.note = noteContent + (note.isEmpty() ? "" : " | 备注: " + note);
                    transaction.date = System.currentTimeMillis();
                    transaction.assetId = fromAccount.id;
                    transaction.targetObject = String.valueOf(toAccount.id);

                    transactionDao.insert(transaction);
                    com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
                    notifyWidgetUpdate();
                    return;
                }
            }
            
            // 【原有逻辑】：相同货币或未启用货币转换
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
            transaction.targetObject = String.valueOf(toAccount.id);

            transactionDao.insert(transaction);
            com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(getApplication());
            notifyWidgetUpdate();
        });
    }
    
    /**
     * 同步获取汇率并转换金额（从缓存）
     */
    private double convertAmountSync(com.example.budgetapp.util.ExchangeRateManager rateManager, 
                                     double amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }
        
        // 尝试从缓存获取汇率
        SharedPreferences ratePrefs = getApplication().getSharedPreferences("exchange_rate_prefs", android.content.Context.MODE_PRIVATE);
        try {
            String ratesJson = ratePrefs.getString("rates_json", "{}");
            String baseCurrency = ratePrefs.getString("base_currency", "");
            
            org.json.JSONObject rates = new org.json.JSONObject(ratesJson);
            
            // 如果基础货币是转出货币，直接获取转入货币汇率
            if (baseCurrency.equals(fromCurrency) && rates.has(toCurrency)) {
                double rate = rates.getDouble(toCurrency);
                return amount * rate;
            }
            // 如果基础货币是转入货币，需要取倒数
            else if (baseCurrency.equals(toCurrency) && rates.has(fromCurrency)) {
                double rate = rates.getDouble(fromCurrency);
                return amount / rate;
            }
            // 如果基础货币都不是，需要交叉计算
            else if (rates.has(fromCurrency) && rates.has(toCurrency)) {
                double fromRate = rates.getDouble(fromCurrency);
                double toRate = rates.getDouble(toCurrency);
                return amount * (toRate / fromRate);
            }
        } catch (Exception e) {
            android.util.Log.e("FinanceViewModel", "Error converting currency", e);
        }
        
        // 缺少有效汇率时返回 NaN，调用方必须中止转账，不能按 1:1 入账。
        return Double.NaN;
    }
    
    /**
     * 将货币符号转换为货币代码
     */
    private String getCurrencyCode(String symbol) {
        return com.example.budgetapp.util.CurrencyUtils.symbolToCode(symbol);
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
    public LiveData<List<Transaction>> getFilteredTransactions(long start, long end, Integer type, Float minAmount, Float maxAmount, String keyword, String assetName) {
        // 如果你的 ViewModel 直接调用了 dao：
        return transactionDao.getFilteredTransactions(start, end, type, minAmount, maxAmount, keyword, assetName);
    }

    // ================= 通知桌面小组件刷新 =================
    private void notifyWidgetUpdate() {
        com.example.budgetapp.widget.WidgetUtils.updateAllWidgets(getApplication());
    }

}
