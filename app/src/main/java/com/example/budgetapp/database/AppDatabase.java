package com.example.budgetapp.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 【修改 1】版本号升级为 10
@Database(entities = {Transaction.class, AssetAccount.class}, version = 12, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();
    public abstract AssetAccountDao assetAccountDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    // ... 保持之前的 MIGRATION
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("ALTER TABLE transactions ADD COLUMN remark TEXT DEFAULT ''"); }
    };
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("CREATE TABLE IF NOT EXISTS `asset_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `amount` REAL NOT NULL, `type` INTEGER NOT NULL, `updateTime` INTEGER NOT NULL)"); }
    };
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("ALTER TABLE transactions ADD COLUMN assetId INTEGER NOT NULL DEFAULT 0"); }
    };
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("ALTER TABLE transactions ADD COLUMN currencySymbol TEXT DEFAULT '¥'"); }
    };
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("ALTER TABLE asset_accounts ADD COLUMN currencySymbol TEXT DEFAULT '¥'"); }
    };
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("ALTER TABLE transactions ADD COLUMN subCategory TEXT DEFAULT ''"); }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN photoPath TEXT DEFAULT ''");
        }
    };

    // 【新增 2】版本 9 -> 10 迁移：AssetAccount 增加理财相关字段
    // boolean 在 SQLite 中存储为 INTEGER，0 为 false，1 为 true
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE asset_accounts ADD COLUMN isFixedTerm INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE asset_accounts ADD COLUMN durationMonths INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE asset_accounts ADD COLUMN interestRate REAL NOT NULL DEFAULT 0.0");
            database.execSQL("ALTER TABLE asset_accounts ADD COLUMN expectedReturn REAL NOT NULL DEFAULT 0.0");
        }
    };

    // 2. 新增 10 -> 11 的迁移逻辑
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE asset_accounts ADD COLUMN isCompoundInterest INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 2. 【新增】11 -> 12 的迁移逻辑
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE asset_accounts ADD COLUMN depositDate INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "budget_database")
                            // 【修改 3】把 MIGRATION_9_10 加到构建器中
                            .addMigrations(
                                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                                    MIGRATION_11_12
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}