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

// 【修改】版本号升级为 9
@Database(entities = {Transaction.class, AssetAccount.class}, version = 9, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();
    public abstract AssetAccountDao assetAccountDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    // ... (保留之前的 MIGRATION 2_3 到 7_8)
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

    // 【新增】版本 8 -> 9 迁移：Transaction 添加 photoPath
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN photoPath TEXT DEFAULT ''");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "budget_database")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}