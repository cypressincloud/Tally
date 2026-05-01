# Implementation Plan: AI分类关键字批量输入

## Overview

本实现计划将AI分类关键字批量输入功能分解为可执行的编码任务。该功能允许用户通过空格分隔的方式批量输入多个关键字，系统自动为每个关键字创建独立的规则。实现包括批量输入解析、验证、规则创建、UI优化和自动同步集成。

## Tasks

- [x] 1. 创建批量输入解析工具类
  - 创建 `app/src/main/java/com/example/budgetapp/util/BatchInputParser.java`
  - 实现 `parseKeywords(String input)` 方法：解析空格分隔的关键字字符串
  - 实现 `isBatchInput(String input)` 方法：检测是否为批量输入模式
  - 实现 `normalizeKeyword(String keyword)` 私有方法：规范化单个关键字
  - 处理多个连续空格、制表符、换行符
  - 实现自动去重逻辑（使用HashSet）
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 2.5, 11.1, 11.2, 11.3, 11.4, 11.5_

- [ ]* 1.1 编写BatchInputParser单元测试
  - 测试单个关键字解析
  - 测试多个关键字解析
  - 测试多个连续空格处理
  - 测试首尾空格处理
  - 测试自动去重
  - 测试空输入和仅空格输入
  - 测试制表符和换行符处理
  - 测试isBatchInput方法
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 2. 创建规则验证工具类
  - 创建 `app/src/main/java/com/example/budgetapp/util/RuleValidator.java`
  - 定义常量：MAX_KEYWORD_LENGTH = 50, MAX_BATCH_COUNT = 20
  - 创建内部类 `ValidationResult`：封装验证结果（isValid, errorMessage, duplicateKeywords, validKeywords）
  - 实现 `validate(Context, List<String>, int)` 方法：验证关键字列表
  - 实现空输入检查
  - 实现数量限制检查（最多20个）
  - 实现长度限制检查（每个最多50字符）
  - 实现重复检测逻辑（与现有规则对比）
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ]* 2.1 编写RuleValidator单元测试
  - 测试空列表验证
  - 测试数量超限验证（>20个）
  - 测试关键字过长验证（>50字符）
  - 测试重复关键字检测
  - 测试有效关键字验证
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 3. 扩展AiCategoryRuleManager支持批量操作
  - 打开 `app/src/main/java/com/example/budgetapp/util/AiCategoryRuleManager.java`
  - 添加 `addRules(Context, List<CategoryRule>)` 方法：批量添加规则
  - 添加 `replaceRule(Context, int, List<CategoryRule>)` 方法：批量替换规则（用于编辑模式）
  - 在保存后调用 `BackupManager.triggerAutoUploadIfEnabled(context)` 触发WebDAV自动同步
  - 确保异常处理和日志记录
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 6.1, 6.2, 6.3, 6.4, 6.5, 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 4. 创建重复警告对话框布局
  - 创建 `app/src/main/res/layout/dialog_duplicate_warning.xml`
  - 使用CardView作为根布局，圆角12dp，边距16dp
  - 添加标题TextView (tv_message)：显示"以下关键字已存在："
  - 添加重复列表TextView (tv_duplicates)：显示重复关键字，使用次要文本颜色
  - 添加按钮容器LinearLayout：水平排列
  - 添加"跳过重复项"按钮 (btn_skip)：主要按钮样式
  - 添加"取消"按钮 (btn_cancel)：次要按钮样式
  - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 5. 创建进度对话框布局
  - 创建 `app/src/main/res/layout/dialog_progress.xml`
  - 使用CardView作为根布局，圆角12dp，边距16dp
  - 添加ProgressBar (progress_bar)：水平进度条样式
  - 添加进度文本TextView (tv_progress)：显示"正在创建规则 X/N"
  - 使用垂直LinearLayout排列组件
  - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [x] 6. 修改添加/编辑规则对话框布局
  - 打开 `app/src/main/res/layout/dialog_add_rule.xml`
  - 修改关键字输入框hint为"输入关键字，多个关键字用空格分隔"
  - 在关键字输入框下方添加示例文本TextView (tv_example)：显示"例如：外卖 美团 饿了么"
  - 设置示例文本颜色为#888888，字体大小12sp
  - 在对话框底部添加预览文本TextView (tv_preview)：显示"将创建 N 条规则"，初始隐藏
  - _Requirements: 1.1, 1.2, 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 7. Checkpoint - 验证基础组件
  - 确保所有工具类编译通过
  - 确保所有布局文件无错误
  - 确保所有测试通过（如果已编写）
  - 询问用户是否有问题

- [x] 8. 修改AiCategoryRuleActivity添加批量输入逻辑
  - 打开 `app/src/main/java/com/example/budgetapp/ui/AiCategoryRuleActivity.java`
  - 在 `showRuleDialog()` 方法中添加TextWatcher监听关键字输入
  - 实现实时预览：检测批量输入时显示"将创建 N 条规则"
  - 修改确认按钮点击逻辑：使用BatchInputParser解析关键字
  - 添加RuleValidator验证逻辑
  - 处理验证失败：显示Toast错误提示，保持对话框打开
  - 处理重复关键字：调用showDuplicateWarningDialog()
  - 处理成功：根据模式调用addRules()或replaceRule()
  - 显示成功Toast："已添加 N 条规则" 或 "已更新为 N 条规则"
  - 刷新规则列表
  - _Requirements: 1.3, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.5, 7.5, 10.1, 10.2_

- [x] 9. 实现重复警告对话框逻辑
  - 在AiCategoryRuleActivity中添加 `showDuplicateWarningDialog()` 方法
  - 接收参数：ValidationResult, category, subCategory, isExpense, position, parentDialog
  - 加载dialog_duplicate_warning.xml布局
  - 设置透明背景
  - 显示重复关键字列表（使用String.join(", ", ...)）
  - 实现"跳过重复项"按钮：仅保存validKeywords，显示成功Toast，刷新列表，关闭对话框
  - 实现"取消"按钮：关闭对话框，不执行任何操作
  - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 10. 实现进度对话框逻辑
  - 在AiCategoryRuleActivity中添加 `showProgressDialog()` 方法
  - 接收参数：List<CategoryRule>, category, subCategory, type
  - 加载dialog_progress.xml布局
  - 设置不可取消模式（setCancelable(false)）
  - 在后台线程中循环创建规则
  - 在主线程更新进度："正在创建规则 X/N"
  - 更新ProgressBar进度
  - 完成后在主线程调用AiCategoryRuleManager.addRules()
  - 显示成功Toast，刷新列表，关闭对话框
  - 添加错误处理：捕获异常，显示错误Toast
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 11. 优化批量操作用户体验
  - 确保批量创建规则数量>5时显示进度对话框
  - 确保批量创建规则数量≤5时直接创建，不显示进度对话框
  - 确保编辑模式下批量输入正确删除原规则并创建新规则
  - 确保单个关键字输入（不含空格）保持原有行为
  - 确保所有Toast提示文本清晰准确
  - _Requirements: 3.6, 6.5, 8.1, 10.1, 10.2_

- [x] 12. Checkpoint - 验证核心功能
  - 手动测试批量添加规则（2-5个关键字）
  - 手动测试大量关键字添加（>5个，验证进度对话框）
  - 手动测试重复关键字检测和警告对话框
  - 手动测试编辑模式下的批量输入
  - 手动测试单个关键字输入（向后兼容性）
  - 询问用户是否有问题

- [ ]* 13. 编写UI集成测试
  - 创建 `app/src/androidTest/java/com/example/budgetapp/ui/AiCategoryRuleBatchInputTest.java`
  - 测试批量添加多条规则
  - 测试预览文本显示
  - 测试重复警告对话框交互
  - 测试编辑模式下的批量输入
  - 测试空输入验证
  - 测试关键字长度验证
  - 测试关键字数量验证
  - _Requirements: 3.1, 3.6, 4.2, 4.5, 4.6, 5.1, 5.3, 5.4, 6.1, 6.5, 7.5_

- [x] 14. 验证WebDAV自动同步集成
  - 确认批量添加规则后触发自动同步
  - 确认编辑规则后触发自动同步
  - 检查BackupManager.triggerAutoUploadIfEnabled()调用位置
  - 验证自动同步开关控制生效
  - 验证备份时间戳正确更新
  - _Requirements: 9.3, 9.4_

- [x] 15. 全面测试和验证
  - 测试包含多个连续空格的输入
  - 测试包含制表符和换行符的输入
  - 测试特殊字符处理（中文、英文、数字、标点）
  - 测试应用重启后规则持久化
  - 测试旧版本规则的兼容性
  - 验证所有错误提示文本正确显示
  - 验证所有成功提示文本正确显示
  - _Requirements: 2.2, 2.3, 9.5, 10.3, 10.4, 11.1, 11.2, 11.3, 11.4, 11.5_

- [x] 16. Final checkpoint - 完整功能验证
  - 运行所有单元测试和集成测试
  - 执行完整的手动测试清单
  - 验证所有需求的验收标准
  - 确认WebDAV自动同步正常工作
  - 确认向后兼容性
  - 询问用户是否满意，是否有需要调整的地方

## Notes

- 任务标记 `*` 的为可选测试任务，可根据项目进度跳过以加快MVP交付
- 每个任务都明确引用了相关的需求编号，便于追溯
- Checkpoint任务确保在关键节点进行验证和用户确认
- 批量操作的核心逻辑在工具类中实现，保持代码模块化和可测试性
- UI层仅负责交互和展示，业务逻辑委托给工具类处理
- WebDAV自动同步集成遵循项目现有规范，使用BackupManager统一触发
- 所有数据操作在后台线程执行，UI更新在主线程执行
- 错误处理采用Toast提示，保持对话框打开状态便于用户修正输入
