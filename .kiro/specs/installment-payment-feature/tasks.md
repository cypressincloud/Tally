# 分期付款功能任务清单

## Phase 1: 数据层 (优先级: 高)

### Task 1.1: 扩展 AssetAccount 实体
- [ ] 在 `AssetAccount.java` 添加字段：
  - `totalInstallments` (int)
  - `installmentAmount` (double)
  - `paidInstallments` (String, JSON 数组)
- [ ] 添加辅助方法：
  - `getPaidInstallmentsList()`
  - `setPaidInstallmentsList(List<Integer>)`
  - `getRemainingInstallments()`
  - `getRemainingAmount()`
  - `getTotalAmount()`
- [ ] 同时修改 `app/src` 和 `app/app/src` 两个目录

### Task 1.2: 数据库迁移
- [ ] 升级 `AppDatabase` 版本号
- [ ] 编写数据库迁移策略
- [ ] 添加 ALTER TABLE 语句
- [ ] 测试数据库升级

## Phase 2: UI 布局 (优先级: 高)

### Task 2.1: 修改添加资产对话框
- [ ] 查找当前对话框布局文件
- [ ] 将 RadioGroup 改为 Spinner
- [ ] 在 `strings.xml` 添加 `asset_types` 数组
- [ ] 添加分期输入表单布局（初始隐藏）：
  - 分期对象输入框
  - 总期数输入框
  - 每期金额输入框
  - 总金额显示文本

### Task 2.2: 创建分期卡片布局
- [ ] 创建 `item_installment_card.xml`
- [ ] 包含元素：
  - 分期对象名称
  - 进度信息（还剩 X/Y 期）
  - 每期金额
  - 总金额
  - 进度条

### Task 2.3: 创建分期详情页面布局
- [ ] 创建 `activity_installment_detail.xml`
- [ ] 顶部信息卡片
- [ ] 期数网格 RecyclerView
- [ ] 底部操作按钮（编辑、删除）

### Task 2.4: 创建期数网格项布局
- [ ] 创建 `item_installment_period.xml`
- [ ] 圆角矩形卡片
- [ ] 中间显示期数
- [ ] 支持两种状态颜色

## Phase 3: 业务逻辑 (优先级: 高)

### Task 3.1: 修改 AssetsFragment
- [ ] 修改 `showAddOrEditDialog()` 方法：
  - 将 RadioGroup 改为 Spinner
  - 添加 Spinner 选择监听
  - 根据选择显示/隐藏分期表单
- [ ] 添加分期表单输入验证
- [ ] 实现总金额自动计算
- [ ] 保存分期数据到数据库
- [ ] 修改列表 Adapter：
  - 判断 type == 4 使用分期卡片
  - 显示分期进度信息
  - 点击跳转到详情页面

### Task 3.2: 创建 InstallmentDetailActivity
- [ ] 创建新 Activity 类
- [ ] 实现数据加载
- [ ] 显示顶部统计信息
- [ ] 设置期数网格 RecyclerView
- [ ] 实现期数点击切换逻辑
- [ ] 实现编辑功能
- [ ] 实现删除功能
- [ ] 触发 WebDAV 自动同步

### Task 3.3: 创建 InstallmentPeriodAdapter
- [ ] 创建 Adapter 类
- [ ] 实现 ViewHolder
- [ ] 根据状态设置颜色：
  - 未还：浅灰色 (#E0E0E0)
  - 已还：主题色
- [ ] 实现点击监听接口

### Task 3.4: 更新资产计算逻辑
- [ ] 修改 `updateAssetSummary()` 方法
- [ ] 分期账户计入负债统计
- [ ] 分期账户按剩余金额计入总资产
- [ ] 测试多币种情况

## Phase 4: 集成和测试 (优先级: 中)

### Task 4.1: AndroidManifest 配置
- [ ] 注册 `InstallmentDetailActivity`
- [ ] 设置主题和父 Activity

### Task 4.2: 备份和恢复
- [ ] 修改 `BackupManager` 导出逻辑
- [ ] 修改 CSV 格式，包含分期字段
- [ ] 修改导入逻辑，解析分期数据
- [ ] 测试备份恢复功能

### Task 4.3: 功能测试
- [ ] 测试创建分期账户
- [ ] 测试期数状态切换
- [ ] 测试编辑分期
- [ ] 测试删除分期
- [ ] 测试总资产计算
- [ ] 测试 WebDAV 同步
- [ ] 测试数据库迁移

### Task 4.4: UI 优化
- [ ] 添加动画效果
- [ ] 优化颜色和间距
- [ ] 添加空状态提示
- [ ] 添加加载状态
- [ ] 适配深色模式

## Phase 5: 文档和发布 (优先级: 低)

### Task 5.1: 文档更新
- [ ] 更新 AGENTS.md
- [ ] 创建功能说明文档
- [ ] 更新数据库文档

### Task 5.2: 代码审查
- [ ] 代码规范检查
- [ ] 性能优化
- [ ] 内存泄漏检查

## 预估工作量

- **Phase 1**: 2-3 小时
- **Phase 2**: 2-3 小时
- **Phase 3**: 4-5 小时
- **Phase 4**: 2-3 小时
- **Phase 5**: 1-2 小时

**总计**: 11-16 小时

## 风险和注意事项

1. **数据库迁移风险**：需要充分测试，避免数据丢失
2. **JSON 解析异常**：需要处理各种边界情况
3. **UI 性能**：期数过多时网格可能卡顿
4. **同步冲突**：多设备同时修改分期状态
5. **向后兼容**：旧版本应用打开新数据

## 开始实施

建议按照 Phase 顺序依次实施，每个 Phase 完成后进行测试再进入下一个 Phase。
