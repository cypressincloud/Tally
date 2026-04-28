# 数据库规范

## Room 架构

### 三层结构

1. **Entity（实体类）**: 定义数据库表结构
2. **DAO（数据访问对象）**: 定义数据库操作方法
3. **Database（数据库类）**: 管理数据库实例和版本

## Entity 规范

### 注解使用

```java
@Entity(tableName = "transactions")
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    @ColumnInfo(name = "amount")
    private double amount;
    
    @ColumnInfo(name = "created_at")
    private long createdAt;
}
```

### 命名规范

- 表名使用小写复数形式，如 `transactions`, `asset_accounts`
- 列名使用 snake_case，如 `created_at`, `account_id`
- 主键统一命名为 `id`

### 数据类型

- 金额使用 `double` 类型
- 时间戳使用 `long` 类型（毫秒）
- 布尔值使用 `boolean` 类型
- 外键使用 `long` 类型

## DAO 规范

### 返回类型

- 查询单条数据返回 `LiveData<Entity>`
- 查询列表返回 `LiveData<List<Entity>>`
- 插入操作返回 `long`（插入的 ID）
- 更新/删除操作返回 `int`（影响的行数）

### 查询优化

```java
@Dao
public interface TransactionDao {
    // 使用 LiveData 自动更新 UI
    @Query("SELECT * FROM transactions ORDER BY created_at DESC")
    LiveData<List<Transaction>> getAllTransactions();
    
    // 使用索引提高查询性能
    @Query("SELECT * FROM transactions WHERE account_id = :accountId")
    LiveData<List<Transaction>> getTransactionsByAccount(long accountId);
    
    // 批量操作
    @Insert
    void insertAll(Transaction... transactions);
}
```

### 事务处理

- 多个相关操作使用 `@Transaction` 注解
- 确保数据一致性
- 避免长时间持有事务锁

## Database 规范

### 版本管理

```java
@Database(entities = {Transaction.class, AssetAccount.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TransactionDao transactionDao();
    public abstract AssetAccountDao assetAccountDao();
}
```

### 迁移策略

- 版本升级必须提供 Migration
- 保留用户数据，避免破坏性更新
- 测试迁移逻辑的正确性

```java
static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override
    public void migrate(SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE transactions ADD COLUMN note TEXT");
    }
};
```

## 性能优化

### 索引使用

- 为频繁查询的列添加索引
- 外键列建议添加索引
- 避免过多索引影响写入性能

### 查询优化

- 只查询需要的列，避免 `SELECT *`
- 使用分页加载大量数据
- 避免在循环中执行查询

### 线程安全

- 数据库操作必须在后台线程
- 使用 Executors 管理线程池
- LiveData 自动处理线程切换
