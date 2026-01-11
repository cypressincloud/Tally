package com.example.budgetapp.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface AssetAccountDao {
    @Insert
    void insert(AssetAccount account);

    @Delete
    void delete(AssetAccount account);

    @Update
    void update(AssetAccount account);

    @Query("SELECT * FROM asset_accounts ORDER BY updateTime DESC")
    LiveData<List<AssetAccount>> getAllAssets();

    // 【新增】同步方法，供Service使用，仅查询特定类型的资产（如 type=0 为资产）
    @Query("SELECT * FROM asset_accounts WHERE type = :type ORDER BY updateTime DESC")
    List<AssetAccount> getAssetsByTypeSync(int type);
    
    // 【新增】根据ID获取单个资产，用于更新余额
    @Query("SELECT * FROM asset_accounts WHERE id = :id LIMIT 1")
    AssetAccount getAssetByIdSync(int id);
}