# 实现计划：AI系统提示词自定义

## 概述

本实现计划将 AI 系统提示词自定义功能分解为一系列可执行的编码任务。该功能允许用户在 AI 设置页面查看和编辑 AI 系统提示词，实现对 AI 识别规则的完全自定义。实现将按照以下顺序进行：首先创建 UI 布局文件，然后实现工具类和 Activity，最后修改现有代码以集成新功能。

## 任务列表

- [ ] 1. 创建 UI 布局文件
  - [x] 1.1 创建 EditText 背景 Drawable
    - 创建文件 `app/src/main/res/drawable/bg_edittext_rounded.xml`
    - 定义圆角矩形背景，12dp 圆角，#F5F5F5 填充色，1dp #E0E0E0 边框
    - _需求: 10.6_
  
  - [x] 1.2 创建提示词编辑器主布局
    - 创建文件 `app/src/main/res/layout/activity_ai_prompt_editor.xml`
    - 实现沉浸式布局，包含标题栏、状态指示器卡片、提示词编辑器卡片、底部按钮区域
    - 使用 NestedScrollView 包裹内容区域，支持滚动
    - EditText 高度 400dp，等宽字体，支持多行输入和垂直滚动
    - 底部三个按钮：查看规则说明、恢复默认、保存
    - _需求: 2.4, 2.5, 2.6, 2.7, 3.1, 3.2, 3.3, 3.5, 9.1, 9.2, 10.1-10.7_
  
  - [~] 1.3 创建规则说明对话框布局
    - 创建文件 `app/src/main/res/layout/dialog_prompt_rules.xml`
    - 使用 CardView 包裹，20dp 圆角，24dp 外边距
    - 包含标题 TextView、NestedScrollView（400dp 高度）、规则内容 TextView、关闭按钮
    - _需求: 7.1, 7.2, 7.3, 7.6, 7.7_
  
  - [~] 1.4 创建恢复默认确认对话框布局
    - 创建文件 `app/src/main/res/layout/dialog_restore_default.xml`
    - 使用 CardView 包裹，20dp 圆角，32dp 外边距
    - 包含标题、提示消息、取消和确认按钮（水平排列）
    - _需求: 4.2, 4.3, 4.4_
  
  - [~] 1.5 在 AiSettingActivity 布局中添加提示词管理卡片
    - 修改文件 `app/src/main/res/layout/activity_ai_setting.xml`
    - 在 `card_ai_category_rules` CardView 之后添加新的 CardView
    - ID 为 `card_ai_prompt`，标题"AI识别提示词"，描述"自定义AI识别规则和行为"
    - 遵循相同的视觉设计模式：20dp 圆角，0dp 阴影，16dp 水平边距，20dp 内边距
    - _需求: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 2. 实现 PromptManager 工具类
  - [~] 2.1 创建 PromptManager 类和 ValidationResult 内部类
    - 创建文件 `app/src/main/java/com/example/budgetapp/util/PromptManager.java`
    - 定义常量：PREF_NAME = "ai_prompt_prefs", KEY_CUSTOM_PROMPT = "custom_system_prompt", MIN_PROMPT_LENGTH = 50
    - 实现 ValidationResult 静态内部类，包含 isValid、errorMessage、warningMessage 字段
    - 实现 ValidationResult 的三个静态工厂方法：success()、error(String)、warning(String)
    - _需求: 5.1, 5.2, 5.3, 8.1-8.7_
  
  - [~] 2.2 实现提示词存储和读取方法
    - 实现 `getCustomPrompt(Context)` 方法：从 SharedPreferences 读取自定义提示词，不存在返回 null
    - 实现 `saveCustomPrompt(Context, String)` 方法：使用 apply() 异步保存提示词到 SharedPreferences
    - 实现 `hasCustomPrompt(Context)` 方法：检查是否存在自定义提示词
    - _需求: 2.2, 2.3, 3.8, 5.1, 5.4, 5.5_
  
  - [~] 2.3 实现提示词清除和验证方法
    - 实现 `clearCustomPrompt(Context)` 方法：使用 remove() 删除自定义提示词
    - 实现 `validatePrompt(String)` 方法：验证提示词不为空、不仅包含空白字符、长度不小于 50 字符
    - 返回相应的 ValidationResult（error、warning 或 success）
    - _需求: 4.6, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

- [ ] 3. 实现 AiPromptEditorActivity
  - [~] 3.1 创建 Activity 类和初始化方法
    - 创建文件 `app/src/main/java/com/example/budgetapp/ui/AiPromptEditorActivity.java`
    - 在 `onCreate()` 中设置沉浸式状态栏（透明状态栏和导航栏）
    - 应用窗口插入以实现边到边显示
    - 实现 `initViews()` 方法：初始化所有 View 引用，设置按钮点击监听器
    - 添加 TextWatcher 监听 EditText 变化，实时更新字符数统计
    - _需求: 2.1, 2.4, 2.5, 3.1, 3.2, 3.4, 10.1, 10.5_
  
  - [~] 3.2 实现提示词加载和状态更新方法
    - 实现 `loadPrompt()` 方法：检查是否存在自定义提示词，存在则加载自定义提示词，否则调用 AiAccountingClient.buildSystemPrompt() 获取默认提示词
    - 实现 `updateStatusIndicator(boolean)` 方法：根据是否自定义更新状态指示器文本和颜色（自定义蓝色，默认灰色）
    - 实现 `updateCharCount()` 方法：获取 EditText 文本长度并更新字符数统计 TextView
    - _需求: 2.1, 2.2, 2.3, 2.7, 3.4, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_
  
  - [~] 3.3 实现提示词保存方法
    - 实现 `savePrompt()` 方法：获取 EditText 文本内容
    - 调用 PromptManager.validatePrompt() 验证提示词
    - 如果验证失败，显示错误 Toast 并返回
    - 如果有警告消息，显示警告 Toast 但继续保存
    - 调用 PromptManager.saveCustomPrompt() 保存提示词
    - 更新状态指示器，显示成功 Toast
    - _需求: 3.5, 3.6, 3.7, 3.8, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_
  
  - [~] 3.4 实现恢复默认功能
    - 实现 `showRestoreDefaultDialog()` 方法：加载 dialog_restore_default.xml 布局
    - 创建 AlertDialog，设置背景透明
    - 取消按钮：关闭对话框
    - 确认按钮：调用 PromptManager.clearCustomPrompt()，获取默认提示词，更新 EditText 和状态指示器，显示成功 Toast
    - _需求: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9_
  
  - [~] 3.5 实现规则说明功能
    - 实现 `buildRulesContent()` 方法：构建规则说明文本，包含 8 个规则类别及其描述
    - 规则类别：JSON 输出规则、多账单识别规则、金额识别规则、收支类型规则、时间识别规则、分类规则、备注规则、资产账户规则
    - 实现 `showRulesDialog()` 方法：加载 dialog_prompt_rules.xml 布局，创建 AlertDialog，设置规则内容，关闭按钮关闭对话框
    - _需求: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

- [ ] 4. 修改现有代码以集成新功能
  - [~] 4.1 在 AiSettingActivity 中添加提示词卡片点击事件
    - 修改文件 `app/src/main/java/com/example/budgetapp/ui/AiSettingActivity.java`
    - 在 `initView()` 方法中添加 `card_ai_prompt` 的点击监听器
    - 点击时创建 Intent 跳转到 AiPromptEditorActivity
    - _需求: 1.6_
  
  - [~] 4.2 修改 AiAccountingClient.buildSystemPrompt() 方法
    - 修改文件 `app/src/main/java/com/example/budgetapp/ai/AiAccountingClient.java`
    - 在文件顶部添加 `import com.example.budgetapp.util.PromptManager;`
    - 在 `buildSystemPrompt(Context)` 方法开头添加检查：如果 PromptManager.hasCustomPrompt(context) 返回 true，则直接返回 PromptManager.getCustomPrompt(context)
    - 保持原有默认提示词构建逻辑不变（作为 fallback）
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
  
  - [~] 4.3 在 AndroidManifest.xml 中注册 AiPromptEditorActivity
    - 修改文件 `app/src/main/AndroidManifest.xml`
    - 在 `<application>` 标签内添加 AiPromptEditorActivity 的声明
    - 设置 `android:name=".ui.AiPromptEditorActivity"`
    - 设置 `android:exported="false"`（仅应用内部使用）
    - _需求: 1.6, 2.1_

- [x] 5. 检查点 - 确保所有测试通过
  - 确保所有测试通过，询问用户是否有问题

## 注意事项

- 任务标记 `*` 的为可选任务，可以跳过以加快 MVP 开发
- 每个任务都引用了具体的需求编号，确保可追溯性
- 检查点任务确保增量验证
- 所有代码必须放在 `app/src/` 目录下（不是 `app/app/src/`）
- 使用 Java 8 特性和 Android 开发最佳实践
- 遵循项目现有的代码风格和命名规范
- SharedPreferences 操作使用 `apply()` 而不是 `commit()`
- 所有用户可见的文本使用中文（本功能可以硬编码，不需要 strings.xml）
- UI 设计遵循 Material Design 规范和应用现有设计模式
