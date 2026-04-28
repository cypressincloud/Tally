# WebDAV 备份数据完整性检查

**日期**: 2026-04-28  
**目标**: 确保 WebDAV 备份能够完整恢复所有用户数据和设置

## 备份数据清单

### ✅ 已备份的数据

#### 1. 数据库表 (3个)
- ✅ **Transaction** (交易记录表)
  - 所有交易记录（收入/支出/转账/负债/借出）
  - 包含：金额、类型、分类、二级分类、时间、备注、资产ID、照片路径、货币符号等
  
- ✅ **AssetAccount** (资产账户表)
  - 所有资产账户（资产/负债/借出）
  - 包含：账户名称、余额、类型、货币符号、是否计入总资产等
  
- ✅ **Goal** (目标表)
  - 所有储蓄目标
  - 包含：目标名称、目标金额、当前金额、是否优先等

#### 2. 分类配置 (CategoryManager)
- ✅ **expenseCategories** - 支出分类列表
- ✅ **incomeCategories** - 收入分类列表
- ✅ **subCategoryMap** - 二级分类映射表

#### 3. 记账助手配置 (AssistantConfig)
- ✅ **enableAutoTrack** - 自动记账总开关
- ✅ **enableAssets** - 资产模块开关
- ✅ **defaultAssetId** - 默认资产ID
- ✅ **expenseKeywords** - 支出关键字集合
- ✅ **incomeKeywords** - 收入关键字集合
- ✅ **weekdayRate** - 工作日加班倍率
- ✅ **holidayRate** - 节假日加班倍率
- ✅ **monthlyBaseSalary** - 月薪底薪

#### 4. 自动资产规则 (AutoAssetManager)
- ✅ **autoAssetRules** - 自动资产绑定规则列表
  - 包含：应用包名、关键字、绑定资产ID

#### 5. 自动续费配置 (RenewalItem)
- ✅ **renewalList** - 自动续费项目列表
  - 包含：续费对象、金额、周期等

#### 6. 应用偏好设置 (SharedPreferences: app_prefs)
- ✅ **完整备份所有 app_prefs 配置**
  - 包含数据类型信息，防止恢复时类型转换异常
  - 涵盖所有功能模块的配置项

### 📋 app_prefs 中包含的主要配置项

根据代码分析，`app_prefs` 包含以下配置（已全部备份）：

**主题与显示**:
- `theme_mode` - 主题模式（跟随系统/浅色/深色/自定义背景）
- `minimalist_mode` - 极简模式开关
- `default_record_mode` - 默认记账模式
- `quick_record_mode` - 快速记账模式

**货币与照片**:
- `enable_currency` - 多币种功能开关
- `default_currency_symbol` - 默认货币符号
- `enable_photo_backup` - 照片备份功能开关
- `photo_backup_uri` - 照片备份路径

**预算功能**:
- `is_budget_enabled` - 预算功能开关
- `is_detailed_budget_enabled` - 详细预算开关
- `monthly_budget` - 月度预算金额
- `budget_start_time` - 预算开始时间
- 各分类预算金额（如 `budget_cat_餐饮`）

**安全设置**:
- 密码保护相关配置

**其他**:
- `is_premium_activated` - 高级功能激活状态
- `enable_details_module` - 详情模块开关
- 以及其他所有存储在 `app_prefs` 中的配置

## 备份实现验证

### 上传方法 (uploadToWebDAV)

```java
public static void uploadToWebDAV(Context context, String webdavUrl, 
    String username, String password, 
    List<Transaction> transactions, 
    List<AssetAccount> assets, 
    List<Goal> goals) throws Exception
```

**备份内容**:
1. ✅ 交易记录 (transactions)
2. ✅ 资产账户 (assets)
3. ✅ 储蓄目标 (goals)
4. ✅ 支出/收入分类 (expenseCategories, incomeCategories)
5. ✅ 二级分类 (subCategoryMap)
6. ✅ 自动资产规则 (autoAssetRules)
7. ✅ 记账助手配置 (assistantConfig)
8. ✅ 自动续费列表 (renewalList)
9. ✅ 应用偏好设置 (appPreferences) - **包含类型信息**

### 下载方法 (downloadFromWebDAV)

```java
public static BackupData downloadFromWebDAV(Context context, 
    String webdavUrl, String username, String password) throws Exception
```

**恢复内容**:
1. ✅ 支出/收入分类 → `CategoryManager.saveExpenseCategories/saveIncomeCategories`
2. ✅ 二级分类 → `CategoryManager.saveSubCategories`
3. ✅ 自动资产规则 → `AutoAssetManager.addRule`
4. ✅ 记账助手配置 → `restoreAssistantConfig`
5. ✅ 自动续费列表 → `AssistantConfig.saveRenewalList`
6. ✅ 应用偏好设置 → `SharedPreferences.Editor` (按类型恢复)
7. ✅ 返回 BackupData 对象，包含交易、资产、目标数据

### 同步方法 (WebdavSettingsActivity)

**数据库恢复流程**:
```java
db.runInTransaction(() -> {
    // 1. 清理旧数据
    db.transactionDao().deleteAll();
    db.goalDao().deleteAll();
    // 清理所有旧资产
    
    // 2. 插入新数据
    db.transactionDao().insertAll(data.records);
    db.assetAccountDao().insert(asset); // 逐个插入
    db.goalDao().insertAll(data.goals);
});
```

## 数据完整性验证

### ✅ 完整性检查通过

1. **数据库表**: 所有3个表都已备份和恢复
2. **分类配置**: 一级分类和二级分类都已备份
3. **助手配置**: 所有记账助手相关配置都已备份
4. **自动规则**: 自动资产规则和自动续费都已备份
5. **应用设置**: 所有 SharedPreferences 配置都已备份（包含类型信息）

### ✅ 恢复流程验证

**手动上传流程**:
1. 用户点击"上传数据"
2. 读取数据库中的所有数据（transactions, assets, goals）
3. 读取所有配置（分类、助手、规则、偏好设置）
4. 打包成 JSON 格式
5. 压缩成 ZIP 文件
6. 上传到 WebDAV 服务器
7. 保存备份时间戳

**手动同步流程**:
1. 用户点击"同步数据"并确认
2. 从 WebDAV 服务器下载 ZIP 文件
3. 解压并解析 JSON 数据
4. 恢复所有配置（分类、助手、规则、偏好设置）
5. 在事务中清理旧数据库数据
6. 插入新的数据库数据（transactions, assets, goals）
7. 提示用户重启应用使设置生效

**自动同步流程**:
1. 用户开启"自动同步"开关
2. 任何数据变更触发 `triggerAutoUploadIfEnabled()`
3. 检查开关状态和配置完整性
4. 在后台线程执行上传（与手动上传相同的数据）
5. 成功后保存备份时间戳
6. 失败时静默记录日志

## 潜在问题与建议

### ⚠️ 注意事项

1. **WebDAV 配置不备份**: 
   - WebDAV 的 URL、用户名、密码不会被备份
   - 用户需要在新设备上重新配置 WebDAV

2. **照片文件不备份**:
   - 交易记录中的 `photoPath` 字段会被备份
   - 但实际的照片文件不会上传到 WebDAV
   - 用户需要单独备份照片文件夹

3. **自动同步触发频率**:
   - 每次数据变更都会触发上传
   - 频繁操作可能导致多次上传
   - 建议：可以考虑添加防抖机制（如5分钟内只上传一次）

4. **网络状态检测**:
   - 当前没有检测网络状态
   - 建议：可以添加仅在 WiFi 下自动上传的选项

### ✅ 优点

1. **数据完整性**: 所有核心数据和配置都已备份
2. **类型安全**: SharedPreferences 备份包含类型信息，避免恢复时类型转换异常
3. **事务保证**: 数据库恢复使用事务，保证原子性
4. **用户友好**: 上传失败静默处理，不打扰用户

## 结论

✅ **WebDAV 备份功能已完整覆盖所有核心数据**

用户同步数据后，可以完整恢复到上传前的状态，包括：
- 所有交易记录
- 所有资产账户
- 所有储蓄目标
- 所有分类配置
- 所有记账助手配置
- 所有自动规则
- 所有应用偏好设置

**唯一不备份的内容**:
- WebDAV 配置本身（URL、用户名、密码）
- 照片文件（只备份路径）

这是合理的设计，因为：
1. WebDAV 配置是用于备份的工具，不应该被备份
2. 照片文件通常较大，不适合通过 WebDAV 备份，用户可以使用其他方式备份照片文件夹
