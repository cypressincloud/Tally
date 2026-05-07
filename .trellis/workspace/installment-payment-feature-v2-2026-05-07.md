# 分期付款功能 V2 版本实施记录

**日期**: 2026-05-07  
**任务**: 实施分期付款功能 V2 版本 - 分期详情页面和期数管理

## 背景

MVP 版本已完成：
- ✅ 数据库扩展（AssetAccount 添加分期字段）
- ✅ UI 改造（下拉框选择类型，分期输入表单）
- ✅ 数据保存和显示
- ✅ 总资产计算逻辑更新

V2 版本目标：
- 创建分期详情页面
- 实现期数网格交互
- 点击切换还款状态
- 编辑和删除分期功能

## 实施内容

### 1. 创建布局文件

#### 1.1 分期详情页面布局
**文件**: `app/src/main/res/layout/activity_installment_detail.xml`

**内容**:
- 顶部标题栏（返回、标题、编辑、删除按钮）
- 信息卡片（分期对象、总期数、每期金额、已还/剩余信息）
- 还款进度标题
- 期数网格 RecyclerView

**特点**:
- 使用 CardView 展示信息
- ScrollView 包裹内容以支持滚动
- 使用项目现有图标资源

#### 1.2 期数网格项布局
**文件**: `app/src/main/res/layout/item_installment_period.xml`

**内容**:
- 60dp × 60dp 的 CardView
- 圆角 12dp
- 中间显示期数文字
- 支持两种状态颜色

### 2. 创建 InstallmentDetailActivity

**文件**: `app/src/main/java/com/example/budgetapp/ui/InstallmentDetailActivity.java`

**功能**:

#### 2.1 数据加载
```java
private void loadAccount() {
    int accountId = getIntent().getIntExtra("account_id", -1);
    viewModel.getAllAssets().observe(this, accounts -> {
        for (AssetAccount acc : accounts) {
            if (acc.id == accountId) {
                account = acc;
                updateUI();
                break;
            }
        }
    });
}
```

#### 2.2 UI 更新
- 显示分期对象名称
- 显示总期数、每期金额、总金额
- 显示已还期数和金额
- 显示剩余期数和金额
- 使用币种符号

#### 2.3 期数点击交互
```java
private void onPeriodClick(int period) {
    List<Integer> paidList = account.getPaidInstallmentsList();
    
    if (paidList.contains(period)) {
        // 已还 -> 未还
        paidList.remove(Integer.valueOf(period));
    } else {
        // 未还 -> 已还
        paidList.add(period);
    }
    
    account.setPaidInstallmentsList(paidList);
    account.amount = account.getRemainingAmount(); // 更新剩余金额
    
    // 保存到数据库
    viewModel.updateAsset(account);
    
    // 触发自动同步
    BackupManager.triggerAutoUploadIfEnabled(this);
    
    // 刷新 UI
    updateUI();
}
```

#### 2.4 删除功能
- 弹出确认对话框
- 使用项目统一的删除对话框样式
- 删除后关闭页面

### 3. 创建 InstallmentPeriodAdapter

**内部类**: `InstallmentDetailActivity.InstallmentPeriodAdapter`

**功能**:
- 显示所有期数（1 到 totalInstallments）
- 根据还款状态设置颜色：
  - **未还**: `@color/cat_unselected_bg` + `@color/text_secondary`
  - **已还**: `@color/app_blue` + `@color/text_white`
- 点击切换状态
- 添加触摸振动反馈

### 4. 更新 AssetsFragment

#### 4.1 分期卡片显示优化
```java
if (item.type == 4) {
    // 显示：还剩 X/Y 期 | ¥XXX.XX/期
    String installmentInfo = String.format("还剩 %d/%d 期 | %s%.2f/期",
            item.getRemainingInstallments(),
            item.totalInstallments,
            symbol,
            item.installmentAmount);
    normalHolder.tvAmount.setText(installmentInfo);
    normalHolder.tvAmount.setTextSize(14); // 稍微小一点以容纳更多信息
}
```

#### 4.2 点击跳转到详情页面
```java
holder.itemView.setOnClickListener(v -> {
    // 【新增】分期类型点击跳转到详情页面
    if (item.type == 4) {
        android.content.Intent intent = new android.content.Intent(
                v.getContext(), InstallmentDetailActivity.class);
        intent.putExtra("account_id", item.id);
        v.getContext().startActivity(intent);
    } else {
        listener.onClick(item);
    }
});
```

### 5. 注册 Activity

**文件**: `app/src/main/AndroidManifest.xml`

```xml
<activity
    android:name=".ui.InstallmentDetailActivity"
    android:label="分期详情"
    android:exported="false"
    android:theme="@style/Theme.BudgetApp" />
```

## 技术细节

### 颜色方案
- **未还期数背景**: `@color/cat_unselected_bg`（日间 #F5F5F5，夜间 #2C2C2C）
- **未还期数文字**: `@color/text_secondary`（日间 #757575，夜间 #A0A0A0）
- **已还期数背景**: `@color/app_blue`（主题色）
- **已还期数文字**: `@color/text_white`（日间 #FFFFFFFF，夜间 #E0E0E0）

### 数据同步
- 每次状态切换都会：
  1. 更新 `paidInstallments` JSON 数组
  2. 更新 `amount` 为剩余应还金额
  3. 保存到数据库
  4. 触发 WebDAV 自动同步
  5. 刷新 UI

### 用户体验
- 触摸振动反馈
- 平滑的状态切换
- 实时更新统计信息
- 确认对话框防止误删

## 编译测试

```bash
../gradlew.bat :app:assembleDebug
```

**结果**: ✅ 编译成功

**警告**:
- 使用了已过时的 API（正常）
- 未经检查的操作（正常）

## 功能验证清单

### 已完成 ✅
1. ✅ 创建分期详情页面布局
2. ✅ 创建期数网格项布局
3. ✅ 实现 InstallmentDetailActivity
4. ✅ 实现 InstallmentPeriodAdapter
5. ✅ 更新 AssetsFragment 显示逻辑
6. ✅ 添加点击跳转功能
7. ✅ 实现期数状态切换
8. ✅ 实现删除功能
9. ✅ 注册 Activity
10. ✅ 编译测试通过

### 待完成 🔄
1. 🔄 编辑功能（当前显示"开发中"提示）
2. 🔄 备份和恢复支持（CSV 导出/导入）
3. 🔄 实际设备测试

## 后续优化建议

### 短期优化
1. **编辑功能**: 复用 AssetsFragment 的对话框，支持修改分期信息
2. **进度条**: 在详情页面添加可视化进度条
3. **动画效果**: 期数状态切换时添加动画

### 长期优化
1. **还款日期**: 支持设置每期还款日期
2. **还款提醒**: 到期前提醒用户还款
3. **提前还款**: 支持一次性还清剩余期数
4. **还款历史**: 记录每期的还款时间
5. **导出账单**: 支持导出分期账单为 PDF

## 数据结构

### AssetAccount 分期相关字段
```java
public int type = 4;                     // 分期类型
public int totalInstallments = 0;        // 总期数
public double installmentAmount = 0.0;   // 每期金额
public String paidInstallments = "[]";   // 已还期数 JSON 数组
```

### 辅助方法
```java
public List<Integer> getPaidInstallmentsList()  // 获取已还期数列表
public void setPaidInstallmentsList(List<Integer> list)  // 设置已还期数列表
public int getRemainingInstallments()  // 获取剩余期数
public double getRemainingAmount()  // 获取剩余应还金额
public double getTotalAmount()  // 获取总金额
public double getPaidAmount()  // 获取已还金额
```

## 用户使用流程

### 创建分期
1. 打开资产模块
2. 点击"添加资产"按钮
3. 选择"分期"类型
4. 输入分期对象、总期数、每期金额
5. 保存

### 管理分期
1. 在负债列表中找到分期卡片
2. 点击卡片进入详情页面
3. 点击期数网格切换还款状态
4. 查看实时更新的统计信息

### 删除分期
1. 在详情页面点击删除按钮
2. 确认删除
3. 返回列表

## 注意事项

1. **数据一致性**: 每次状态切换都会更新 `amount` 字段，确保总资产计算正确
2. **JSON 解析**: 使用 try-catch 处理 JSON 解析异常
3. **币种支持**: 正确显示用户设置的币种符号
4. **自动同步**: 每次数据变更都会触发 WebDAV 自动同步
5. **内存管理**: 使用 LiveData 观察数据变化，避免内存泄漏

## 相关文件

### 新增文件
- `app/src/main/res/layout/activity_installment_detail.xml`
- `app/src/main/res/layout/item_installment_period.xml`
- `app/src/main/java/com/example/budgetapp/ui/InstallmentDetailActivity.java`

### 修改文件
- `app/src/main/java/com/example/budgetapp/ui/AssetsFragment.java`
- `app/src/main/AndroidManifest.xml`

### 相关文档
- `.kiro/specs/installment-payment-feature/requirements.md`
- `.kiro/specs/installment-payment-feature/design.md`
- `.kiro/specs/installment-payment-feature/tasks.md`
- `.kiro/specs/installment-payment-feature/implementation-guide.md`
- `.trellis/workspace/installment-payment-feature-2026-05-07.md` (MVP 版本)

## 总结

V2 版本成功实现了分期详情页面和期数管理功能，用户现在可以：
- 查看分期的详细信息
- 通过点击网格切换每期的还款状态
- 实时查看已还和剩余金额
- 删除不需要的分期账户

核心功能已完成，编译测试通过。下一步可以进行实际设备测试，并根据用户反馈进行优化。

**状态**: ✅ V2 版本核心功能完成
