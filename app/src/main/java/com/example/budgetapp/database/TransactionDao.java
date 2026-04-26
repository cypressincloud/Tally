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

    // 【新增】小组件使用：同步查询指定时间段加班总收入
    @Query("SELECT SUM(amount) FROM transactions WHERE date >= :start AND date <= :end AND type = 1 AND category = '加班'")
    Double getOvertimeTotalAmountSync(long start, long end);

    // 【新增】小组件使用：同步查询指定时间段所有加班记录（用于计算时长）
    @Query("SELECT * FROM transactions WHERE date >= :start AND date <= :end AND type = 1 AND category = '加班'")
    List<Transaction> getOvertimeTransactionsSync(long start, long end);

    @Query("SELECT * FROM transactions WHERE date >= :start AND date <= :end")
    List<Transaction> getTransactionsByRange(long start, long end);

    // 【新增】批量修改一级分类名称（历史账单同步）
    @Query("UPDATE transactions SET category = :newCategory WHERE category = :oldCategory")
    void updateCategoryName(String oldCategory, String newCategory);

    // 【新增】批量修改二级分类名称（历史账单同步）
    @Query("UPDATE transactions SET subCategory = :newSubCategory WHERE category = :parentCategory AND subCategory = :oldSubCategory")
    void updateSubCategoryName(String parentCategory, String oldSubCategory, String newSubCategory);



    // ================= 以下为新增的高性能优化查询 =================

    // 1. 按需查询：只获取指定时间段内的账单（用于首页日历按月加载）
    @Query("SELECT * FROM transactions WHERE date >= :start AND date <= :end ORDER BY date DESC")
    LiveData<List<Transaction>> getTransactionsByRangeLive(long start, long end);

    // 2. 高级过滤：用于明细页 (DetailsFragment) 的高级筛选，null 表示该条件不限制
    // 使用普通的 LiveData<List<Transaction>> 返回类型，并加上金额筛选条件
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:minAmount IS NULL OR amount >= :minAmount) " +
            "AND (:maxAmount IS NULL OR amount <= :maxAmount) " +
            // 🌟 修改点：在下面的查询条件中加入了 OR subCategory LIKE '%' || :keyword || '%'
            "AND (:keyword IS NULL OR category LIKE '%' || :keyword || '%' OR subCategory LIKE '%' || :keyword || '%' OR note LIKE '%' || :keyword || '%') " +
            "ORDER BY date DESC")
    LiveData<List<Transaction>> getFilteredTransactions(long startDate, long endDate, Integer type, Float minAmount, Float maxAmount, String keyword);

    // 【新增】供桌面小组件使用：同步聚合查询指定时间的收入或支出总和
    @Query("SELECT SUM(amount) FROM transactions WHERE date >= :start AND date <= :end AND type = :type AND category != '资产互转'")
    Double getTotalAmountByTypeSync(long start, long end, int type);


    // 3. 聚合查询：直接让数据库计算指定时间段的收入或支出总和 (返回 Double 防止没数据时报错)
    @Query("SELECT SUM(amount) FROM transactions WHERE date >= :start AND date <= :end AND type = :type AND category != '资产互转'")
    LiveData<Double> getTotalAmountByTypeLive(long start, long end, int type);

    // 4. 聚合查询：直接计算加班总收入
    @Query("SELECT SUM(amount) FROM transactions WHERE date >= :start AND date <= :end AND type = 1 AND category = '加班'")
    LiveData<Double> getOvertimeTotalAmountLive(long start, long end);

    // 【新增】年视图专用的轻量级查询：只取3个字段，直接排除互转和转账，速度提升 10 倍以上
    @Query("SELECT date, type, amount FROM transactions WHERE date >= :start AND date <= :end AND type IN (0, 1) AND category != '资产互转'")
    List<TransactionMinimal> getMinimalTransactionsSync(long start, long end);

    /**
     * 【优化】年视图专用：查询指定年份内，哪些月份存在记账记录
     * 返回 1 到 12 的整数列表
     * date / 1000 是因为数据库存的是毫秒，strftime 需要秒
     */
    @Query("SELECT DISTINCT CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) " +
            "FROM transactions " +
            "WHERE date >= :start AND date <= :end " +
            "AND type IN (0, 1) AND category != '资产互转'")
    List<Integer> getMonthsWithDataSync(long start, long end);

}