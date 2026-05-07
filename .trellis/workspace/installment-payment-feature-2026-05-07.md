# 分期付款功能实施日志

**日期**: 2026-05-07  
**功能**: 资产模块新增分期付款管理功能  
**状态**: 🚧 进行中  
**实施策略**: 专注于 `app/src` 目录，MVP 最小可行版本

## 实施策略调整

由于这是一个较大的功能，我将采用 MVP（最小可行产品）策略：

### MVP 范围
1. ✅ 数据层：AssetAccount 扩展 + 数据库迁移
2. 🚧 UI 层：将 RadioGroup 改为 Spinner，添加"分期"选项
3. 🚧 基础功能：创建分期账户，显示在负债列表
4. ⏳ 详情页面：后续版本实现

### 暂缓功能（V2）
- 分期详情页面
- 期数网格交互
- 编辑/删除分期

这样可以先让基础功能运行起来，后续再逐步完善。

## 实施进度

### ✅ Phase 1: 数据层（已完成）
- [x] `AssetAccount.java` 添加分期字段和辅助方法
- [x] `AppDatabase.java` 升级到版本 22，添加迁移
- [x] 编译验证通过

### 🚧 Phase 2: UI 布局（部分完成）
- [x] `strings.xml` 添加 `asset_types` 数组
- [x] `dialog_add_asset.xml` 将 RadioGroup 改为 Spinner
- [x] `dialog_add_asset.xml` 添加分期输入表单布局
- [ ] `AssetsFragment.java` 修改对话框逻辑（RadioGroup -> Spinner）
- [ ] `AssetsFragment.java` 添加分期输入处理逻辑
- [ ] `AssetsFragment.java` 修改列表显示逻辑

### ⏳ Phase 3: 详情页面（待实施）
- [ ] 创建 `InstallmentDetailActivity.java`
- [ ] 创建 `activity_installment_detail.xml`
- [ ] 创建 `InstallmentPeriodAdapter.java`
- [ ] 创建 `item_installment_period.xml`

## 当前状态

**编译错误**：`AssetsFragment.java` 中仍在使用 RadioGroup 相关的 ID，需要修改为 Spinner 逻辑。

**错误位置**：
- 第 507 行：`RadioGroup rgType = view.findViewById(R.id.rg_asset_type);`
- 第 705-783 行：多处使用 `R.id.rb_asset`、`R.id.rb_lent` 等 RadioButton ID

**需要修改的逻辑**：
1. 将 `RadioGroup rgType` 改为 `Spinner spinnerType`
2. 将 `rgType.setOnCheckedChangeListener()` 改为 `spinnerType.setOnItemSelectedListener()`
3. 将 `rgType.check(R.id.rb_xxx)` 改为 `spinnerType.setSelection(index)`
4. 将 `rgType.getCheckedRadioButtonId()` 改为 `spinnerType.getSelectedItemPosition()`
5. 添加分期输入表单的显示/隐藏逻辑
6. 添加分期数据的保存逻辑

## 技术细节

### 数据模型变更
```java
// AssetAccount 新增字段
public int totalInstallments = 0;        // 总期数
public double installmentAmount = 0.0;   // 每期金额
public String paidInstallments = "[]";   // 已还期数 JSON
```

### 数据库迁移 SQL
```sql
ALTER TABLE asset_accounts ADD COLUMN totalInstallments INTEGER NOT NULL DEFAULT 0;
ALTER TABLE asset_accounts ADD COLUMN installmentAmount REAL NOT NULL DEFAULT 0.0;
ALTER TABLE asset_accounts ADD COLUMN paidInstallments TEXT DEFAULT '[]';
```

### 颜色方案
- **未还期数背景**：`@color/cat_unselected_bg`
- **未还期数文字**：`@color/text_secondary`
- **已还期数背景**：`@color/app_blue`
- **已还期数文字**：`@color/text_white`

## 下一步

1. 同步更新 `app/app/src` 目录的数据库文件
2. 开始 Phase 2：创建 UI 布局文件
3. 查找并修改添加资产对话框

## 注意事项

- 两个目录（`app/src` 和 `app/app/src`）需要保持同步
- JSON 解析需要处理异常情况
- 数据库迁移需要充分测试
