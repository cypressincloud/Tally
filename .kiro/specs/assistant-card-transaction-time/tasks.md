# 实现计划: AI记账助手卡片显示账单时间

## 概述

为AI记账助手的草稿卡片添加账单时间显示功能。在卡片顶部标题行显示交易时间(格式:MM-dd HH:mm),帮助用户快速确认AI识别的时间是否准确。实现涉及布局文件修改和Java代码更新,保持与现有功能的完全兼容。

## 任务

- [x] 1. 修改草稿卡片布局文件,添加时间显示组件
  - 在item_ai_draft_card.xml的顶部LinearLayout中添加TextView组件(id: tv_transaction_time)
  - 设置TextView属性: wrap_content宽度, 14sp字体, textColorSecondary颜色, 8dp左边距
  - 调整tv_draft_status的layout_marginStart,移除原有的10dp边距
  - 确保新组件位于tv_draft_index之后、tv_draft_status之前
  - _需求: 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 4.1, 4.2_

- [x] 2. 在DraftCardController类中添加时间显示逻辑
  - [x] 2.1 添加TextView字段引用和初始化
    - 在DraftCardController类中添加private final TextView tvTransactionTime字段
    - 在构造函数中使用findViewById初始化tvTransactionTime
    - _需求: 3.1, 4.1_
  
  - [x] 2.2 实现时间格式化方法
    - 创建updateTransactionTime(long timestamp)方法
    - 实现timestamp <= 0时的回退逻辑(使用System.currentTimeMillis())
    - 使用SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())格式化时间
    - 调用tvTransactionTime.setText()更新UI显示
    - _需求: 1.1, 1.2, 3.2, 4.3_
  
  - [x] 2.3 在bind()方法中调用时间更新
    - 在bind()方法的现有逻辑后添加updateTransactionTime(model.draft.date)调用
    - 确保时间显示在卡片绑定时立即更新
    - _需求: 1.1, 3.1, 4.4_

- [ ]* 3. 编写单元测试验证时间格式化逻辑
  - 测试有效时间戳的格式化输出(例如: 1714291800000L → "04-28 14:30")
  - 测试零时间戳的回退逻辑(timestamp = 0 → 使用当前时间)
  - 测试负数时间戳的回退逻辑(timestamp < 0 → 使用当前时间)
  - 验证SimpleDateFormat使用正确的格式和Locale
  - _需求: 3.2, 3.3_

- [x] 4. 检查点 - 确保功能正常工作
  - 在Android设备或模拟器上运行应用
  - 通过AI记账助手创建新的账单草稿
  - 验证时间显示在卡片顶部且格式正确(MM-dd HH:mm)
  - 验证时间颜色为灰色(textColorSecondary)
  - 验证编辑和保存草稿时时间显示不变
  - 验证删除按钮位置和功能未受影响
  - 如有问题,询问用户并修复

## 注意事项

- 任务标记`*`为可选任务,可跳过以加快MVP交付
- 每个任务都引用了具体的需求编号,确保可追溯性
- 时间格式化使用SimpleDateFormat,确保国际化支持
- 所有UI更新在主线程执行,无需异步处理
- 保持与现有DraftCardController逻辑的完全兼容
