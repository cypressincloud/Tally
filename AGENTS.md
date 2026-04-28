# Budget App - Android 预算管理应用

## 项目概述

这是一个 Android 原生预算管理应用，使用 Java 开发，帮助用户跟踪收支、管理资产账户、设置预算目标。

## 技术栈

- **语言**: Java
- **平台**: Android (原生应用)
- **数据库**: Room (SQLite)
- **架构**: MVVM (ViewModel + LiveData)
- **构建工具**: Gradle

## 核心功能模块

1. **交易记录** (Transaction)
   - 收入/支出记录
   - 分类管理
   - 关键词自动识别
   - 照片备份

2. **资产账户** (Asset Account)
   - 多账户管理
   - 自动资产跟踪
   - 账户余额计算

3. **预算管理** (Budget)
   - 预算设置
   - 预算历史
   - 超支提醒

4. **目标管理** (Goal)
   - 储蓄目标
   - 进度跟踪

5. **自动续费** (Auto Renewal)
   - 订阅管理
   - 续费提醒

6. **数据备份** (Backup)
   - WebDAV 云端备份
   - 自动同步功能
   - 本地导出/导入 (ZIP, CSV)
   - 第三方记账应用数据导入 (一木记账、蜜蜂记账)

## 项目结构

```
app/app/src/main/java/com/example/budgetapp/
├── database/           # 数据库实体和 DAO
│   ├── AppDatabase.java
│   ├── Transaction.java
│   ├── TransactionDao.java
│   ├── AssetAccount.java
│   ├── AssetAccountDao.java
│   ├── Goal.java
│   └── GoalDao.java
├── ui/                 # UI 界面
│   ├── MainActivity.java
│   ├── RecordFragment.java
│   ├── DetailsFragment.java
│   ├── BudgetFragment.java
│   ├── AssetsFragment.java
│   └── StatsFragment.java
├── viewmodel/          # ViewModel 层
│   └── FinanceViewModel.java
├── util/               # 工具类
│   ├── CategoryManager.java
│   ├── KeywordManager.java
│   ├── CurrencyUtils.java
│   └── AutoAssetManager.java
├── service/            # 后台服务
│   └── QuickAddTileService.java
├── BackupManager.java  # 备份管理
└── MyApplication.java  # Application 类
```

## 关键约定

### 代码规范
- 使用 Java 8 特性
- 遵循 Android 开发最佳实践
- 数据库操作必须在后台线程执行
- UI 更新必须在主线程

### 数据库规范
- 所有实体类使用 Room 注解
- DAO 接口返回 LiveData 用于 UI 观察
- 数据库版本变更需要提供迁移策略

### UI 规范
- Fragment 用于主要界面
- Activity 用于独立功能页面
- 使用 Material Design 组件

### WebDAV 自动同步规范
- 自动同步开关保存在 SharedPreferences (`webdav_prefs`, key: `webdav_auto_sync`)
- 使用 `BackupManager.triggerAutoUploadIfEnabled(Context)` 触发自动上传
- 自动上传在后台线程执行，不阻塞主线程
- 上传成功后自动保存备份时间戳 (`webdav_last_backup_time`)
- 上传失败静默处理，不打扰用户

#### 自动同步触发点
所有数据变更操作都已集成自动同步触发器：

**FinanceViewModel.java** (通过 ViewModel 的记账操作):
- `addTransaction()` - 普通手动记账
- `addTransactionWithAssetSync()` - AI 记账和带资产同步的记账
- `deleteTransaction()` - 删除交易
- `updateTransaction()` - 更新交易
- `updateTransactionWithAssetSync()` - 同步更新交易和资产
- `revokeTransaction()` - 撤回交易
- `addAsset()` / `updateAsset()` / `deleteAsset()` - 资产管理
- `insertGoal()` / `updateGoal()` / `deleteGoal()` / `setPriorityGoal()` - 目标管理
- `processAutoRenewal()` - 自动续费处理
- `transferAsset()` - 资产转移

**SelectToSpeakService.java** (无障碍自动记账):
- `saveToDatabase()` - 无障碍服务监听通知自动记账

**QuickAddTileService.java** (控制中心磁贴记账):
- `saveToDatabase()` - 快捷磁贴手动记账

**CategoryManager.java** (分类设置变更):
- `saveExpenseCategories()` / `saveIncomeCategories()` - 保存分类
- `saveSubCategories()` - 保存二级分类
- `setSubCategoryEnabled()` / `setDetailedCategoryEnabled()` - 分类开关

#### 实现细节
```java
// 在所有数据变更操作后调用
com.example.budgetapp.BackupManager.triggerAutoUploadIfEnabled(context);
```

该方法会：
1. 检查自动同步开关是否启用
2. 验证 WebDAV 配置是否完整
3. 在后台线程执行上传
4. 成功后保存备份时间戳
5. 失败时静默记录日志

## 当前任务

查看当前任务状态：
```bash
python "./.trellis/scripts/task.py" list
```

查看项目上下文：
```bash
python "./.trellis/scripts/get_context.py" --mode record
```

## 工作流程

1. **开始工作**: 先读取 AGENTS.md，确认当前任务，查看最新 journal
2. **规划阶段**: 明确需求、拆分任务、确定验收标准
3. **开发阶段**: 按规范实现功能
4. **验收阶段**: 测试功能、修复问题
5. **记录阶段**: 提交代码、更新 journal

## 相关文档

- 详细规范: `.trellis/spec/`
- 任务管理: `.trellis/tasks/`
- 工作日志: `.trellis/workspace/`
- Kiro 技能: `.agents/skills/`
- Kiro 配置: `.codex/`
