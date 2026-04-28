# 实现计划: AI分类关键字规则

## 概述

本功能为AI记账系统添加关键字规则管理能力。用户可以在AI设置页面中管理关键字到分类的映射规则，当AI识别账单时，系统会根据账单描述中的关键字自动应用对应的分类规则。

实现顺序遵循"数据模型 → 业务逻辑 → UI界面 → 集成"的原则，确保每一步都可以独立验证。

## 任务

- [x] 1. 创建数据模型和业务逻辑核心类
  - [x] 1.1 创建CategoryRule数据模型类
    - 在`app/app/src/main/java/com/example/budgetapp/util/`目录下创建`CategoryRule.java`
    - 实现字段：keyword (String), category (String), subCategory (String), type (int)
    - 实现构造函数、getter/setter方法
    - 实现`toJson()`和`fromJson()`方法用于JSON序列化
    - 支持向后兼容：fromJson时如果没有subCategory字段，默认为空字符串
    - _Requirements: 7.2_
  
  - [ ]* 1.2 为CategoryRule编写property测试
    - **Property 1: JSON序列化往返一致性**
    - **Validates: Requirements 7.2**
    - 生成随机规则列表，验证序列化后反序列化能恢复原始数据

  - [x] 1.3 创建AiCategoryRuleManager规则管理器
    - 在`app/app/src/main/java/com/example/budgetapp/util/`目录下创建`AiCategoryRuleManager.java`
    - 实现`getRules(Context)`方法：从SharedPreferences加载规则列表
    - 实现`addRule(Context, CategoryRule)`方法：添加新规则
    - 实现`updateRule(Context, int, CategoryRule)`方法：更新指定位置的规则
    - 实现`deleteRule(Context, int)`方法：删除指定位置的规则
    - 实现`saveRules(Context, List<CategoryRule>)`私有方法：保存规则列表到SharedPreferences
    - 使用SharedPreferences文件名"ai_category_rule_prefs"，键名"rules"
    - 保存成功后调用`BackupManager.triggerAutoUploadIfEnabled(context)`触发WebDAV同步
    - 支持向后兼容：加载时处理没有subCategory字段的旧数据
    - _Requirements: 7.1, 7.2, 7.3, 7.5, 7.6_

  - [ ]* 1.4 为AiCategoryRuleManager编写单元测试
    - 测试规则的添加、更新、删除操作
    - 测试JSON序列化和反序列化
    - 测试空输入验证
    - 测试重复关键字检查
    - _Requirements: 7.1, 7.2, 7.3_

- [x] 2. 实现规则应用逻辑
  - [x] 2.1 在AiCategoryRuleManager中实现规则应用方法
    - 实现`applyRules(Context, TransactionDraft)`方法：应用规则到账单草稿
    - 实现`matchesKeyword(String, String)`私有方法：不区分大小写的关键字匹配
    - 遍历所有规则，使用第一个匹配的规则更新分类和子分类
    - 如果没有匹配的规则，保持原分类不变
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [ ]* 2.2 为规则应用逻辑编写property测试
    - **Property 2: 关键字匹配大小写不敏感**
    - **Property 3: 规则应用优先级**
    - **Property 4: 无匹配时保持原分类**
    - **Validates: Requirements 10.3, 10.4, 10.5**
    - 生成随机关键字和描述，验证匹配逻辑的正确性

- [x] 3. 创建规则管理页面布局文件
  - [x] 3.1 创建规则列表项布局
    - 创建`app/app/src/main/res/layout/item_category_rule.xml`
    - 使用CardView作为根布局，圆角20dp，白色背景，0dp elevation
    - 左侧显示关键字和分类文本（垂直排列）
    - 右侧显示编辑图标
    - 内边距20dp
    - _Requirements: 3.4, 3.5, 3.6, 9.2, 9.3, 9.10_

  - [x] 3.2 创建规则管理页面主布局
    - 创建`app/app/src/main/res/layout/activity_ai_category_rule.xml`
    - 使用LinearLayout作为根布局，背景色@color/bar_background
    - 顶部显示标题"AI分类关键字规则"（28sp粗体）
    - 中间使用RecyclerView展示规则列表
    - 底部显示"添加规则"按钮（主题色#327ffc，圆角16dp）
    - 添加空状态TextView（初始隐藏）："暂无规则，点击下方按钮添加"
    - _Requirements: 3.1, 3.2, 4.1, 9.1, 9.5, 9.6, 9.9_

  - [x] 3.3 创建添加/编辑规则对话框布局
    - 创建`app/app/src/main/res/layout/dialog_add_rule.xml`
    - 包含关键字输入框（EditText）
    - 包含分类选择器（Spinner）
    - 包含确认和取消按钮
    - 使用圆角20dp的卡片式设计
    - _Requirements: 4.2, 4.3, 4.4, 5.1, 5.2_

- [x] 4. 实现规则管理页面Activity
  - [x] 4.1 创建AiCategoryRuleActivity基础框架
    - 在`app/app/src/main/java/com/example/budgetapp/ui/`目录下创建`AiCategoryRuleActivity.java`
    - 继承AppCompatActivity
    - 在`onCreate()`中初始化RecyclerView、按钮等UI组件
    - 设置沉浸式状态栏（与项目一致）
    - 调用`loadRules()`加载规则列表
    - _Requirements: 2.2, 2.3, 3.1_

  - [x] 4.2 实现规则列表展示逻辑
    - 创建`RuleListAdapter`内部类，继承`RecyclerView.Adapter`
    - 实现ViewHolder绑定规则数据到列表项
    - 实现`loadRules()`方法：从AiCategoryRuleManager加载规则
    - 实现`refreshList()`方法：刷新RecyclerView
    - 实现空状态显示逻辑：规则列表为空时显示提示文本
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 4.3 实现添加规则功能
    - 实现`showAddRuleDialog()`方法：显示添加规则对话框
    - 从CategoryManager获取收入和支出分类列表
    - 在Spinner中分组显示收入分类和支出分类
    - 实现输入验证：关键字非空、分类已选择
    - 调用`AiCategoryRuleManager.addRule()`保存规则
    - 保存成功后刷新列表并显示Toast提示
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 4.4 实现编辑规则功能
    - 实现`showEditRuleDialog(CategoryRule, int)`方法：显示编辑规则对话框
    - 预填充当前规则的关键字和分类
    - 实现输入验证：关键字非空、分类已选择
    - 调用`AiCategoryRuleManager.updateRule()`更新规则
    - 更新成功后刷新列表并显示Toast提示
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [x] 4.5 实现删除规则功能
    - 实现`showDeleteConfirmDialog(CategoryRule, int)`方法：显示删除确认对话框
    - 显示提示文本"确定要删除此规则吗？"
    - 用户确认后调用`AiCategoryRuleManager.deleteRule()`删除规则
    - 删除成功后刷新列表并显示Toast提示
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 4.6 实现列表项点击事件
    - 设置编辑图标点击事件：调用`showEditRuleDialog()`
    - 设置列表项长按事件：调用`showDeleteConfirmDialog()`
    - _Requirements: 3.6, 5.1, 6.1_

- [x] 5. Checkpoint - 验证规则管理页面功能
  - 确保所有测试通过，询问用户是否有问题

- [x] 6. 在AI设置页面添加入口卡片
  - [x] 6.1 在activity_ai_setting.xml中添加入口卡片
    - 在"启用AI记账"开关下方添加CardView
    - 卡片标题："AI分类关键字规则"
    - 卡片描述："设置关键字自动分类规则"
    - 右侧显示向右箭头图标
    - 使用圆角20dp，白色背景，0dp elevation
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 9.2, 9.3, 9.4_

  - [x] 6.2 在AiSettingActivity.java中添加点击事件
    - 找到卡片的点击事件处理代码位置
    - 添加点击监听器，启动AiCategoryRuleActivity
    - _Requirements: 2.1_

- [x] 7. 集成规则应用逻辑到AI识别流程
  - [x] 7.1 在TransactionDraftMapper中集成规则应用
    - 找到`TransactionDraftMapper.fromJson()`方法
    - 在AI识别完成、返回TransactionDraft之前调用`AiCategoryRuleManager.applyRules(context, draft)`
    - 确保规则应用在设置draft字段之后执行
    - _Requirements: 10.1, 10.2, 10.6_

  - [ ]* 7.2 编写集成测试
    - 测试规则持久化和加载
    - 测试与CategoryManager的集成
    - 测试与TransactionDraftMapper的集成
    - 测试WebDAV自动同步触发
    - _Requirements: 7.4, 7.5, 7.6, 10.1, 10.2_

- [x] 8. 在AndroidManifest.xml中注册Activity
  - [x] 8.1 注册AiCategoryRuleActivity
    - 在`app/app/src/main/AndroidManifest.xml`中添加activity声明
    - 设置合适的主题和启动模式
    - _Requirements: 2.2_

- [x] 9. Final Checkpoint - 完整功能验证
  - 确保所有测试通过，询问用户是否有问题

- [x] 10. UI优化 - 使用CategoryAdapter替代Spinner
  - [x] 10.1 更新CategoryRule模型支持子分类
    - 在`CategoryRule.java`中添加`subCategory`字段
    - 添加新的构造函数：`CategoryRule(String keyword, String category, String subCategory, int type)`
    - 添加`getSubCategory()`和`setSubCategory()`方法
    - 更新`toJson()`方法：添加subCategory字段序列化
    - 更新`fromJson()`方法：支持subCategory字段反序列化（向后兼容，如果没有该字段默认为空字符串）
    - _Requirements: UI优化需求_

  - [x] 10.2 更新AiCategoryRuleManager支持子分类
    - 更新`applyRules()`方法：应用规则时同时设置主分类和子分类
    - 如果规则有子分类，设置`draft.subCategory = rule.getSubCategory()`
    - 如果规则没有子分类，设置`draft.subCategory = ""`
    - _Requirements: UI优化需求_

  - [x] 10.3 重构dialog_add_rule.xml布局
    - 移除Spinner组件（`sp_category`）
    - 添加RecyclerView组件用于分类选择（`rv_category`）
    - 添加"选择分类"标题TextView
    - 添加"已选择"提示TextView（显示当前选中的分类和子分类）
    - 保持现有的关键字输入框和按钮布局
    - 使用与项目一致的圆角20dp卡片设计
    - _Requirements: UI优化需求_

  - [x] 10.4 更新AiCategoryRuleActivity使用CategoryAdapter
    - 移除`CategorySpinnerItem`类和相关Spinner逻辑
    - 移除`setupCategorySpinner()`和`selectCategoryInSpinner()`方法
    - 在`showAddRuleDialog()`中初始化CategoryAdapter
    - 根据`CategoryManager.isDetailedCategoryEnabled()`设置布局管理器：
      - 详细模式：使用FlexboxLayoutManager
      - 网格模式：使用GridLayoutManager(5列)
    - 实现分类点击监听：更新选中分类，清空子分类
    - 实现分类长按监听：显示子分类选择对话框
    - 添加"已选择"提示更新逻辑
    - _Requirements: UI优化需求_

  - [x] 10.5 实现子分类选择对话框
    - 创建`showSubCategoryDialog(String category, String[] selectedSubCategory)`方法
    - 使用`dialog_select_sub_category.xml`布局
    - 从`CategoryManager.getSubCategories(context, category)`获取子分类列表
    - 使用ChipGroup展示子分类，支持单选
    - 设置Chip样式：选中时使用`@color/app_yellow`背景
    - 点击Chip选择子分类并关闭对话框
    - 再次点击已选中的Chip取消选择
    - 如果子分类列表为空，显示"暂无细分选项"提示
    - _Requirements: UI优化需求_

  - [x] 10.6 更新编辑规则对话框支持子分类
    - 在`showEditRuleDialog()`中预选当前规则的主分类和子分类
    - 如果规则有子分类，在"已选择"提示中显示
    - 保存时同时保存主分类和子分类
    - _Requirements: UI优化需求_

  - [x] 10.7 更新规则列表项显示子分类
    - 在`item_category_rule.xml`中调整布局（如果需要）
    - 在`RuleListAdapter.onBindViewHolder()`中显示子分类
    - 格式："{类型} - {主分类} > {子分类}"（如果有子分类）
    - 格式："{类型} - {主分类}"（如果没有子分类）
    - _Requirements: UI优化需求_

- [x] 11. UI优化验证
  - 测试详细分类模式和网格模式切换
  - 测试主分类选择和子分类选择
  - 测试规则保存和加载（包含子分类）
  - 测试规则应用（验证子分类正确应用到账单）
  - 测试向后兼容性（旧数据没有subCategory字段）
  - 确保所有功能正常，询问用户是否有问题

## 注意事项

- 任务标记`*`的为可选测试任务，可以跳过以加快MVP开发
- 每个任务都引用了具体的需求条款，确保可追溯性
- 规则管理器使用SharedPreferences存储，遵循项目规范
- 所有数据变更操作都会触发WebDAV自动同步
- UI设计遵循项目一致的Material Design风格
- 关键字匹配使用不区分大小写的包含匹配
- 多个关键字匹配时使用第一个匹配的规则

## UI优化说明

任务10-11为UI优化任务，将基础Spinner替换为项目标准的CategoryAdapter：

**优化内容**:
1. **CategoryAdapter集成**: 使用与RecordFragment相同的分类选择方式
2. **两级分类支持**: 支持主分类+子分类选择
3. **布局模式**: 支持详细模式（chip样式）和网格模式（5列）
4. **长按手势**: 长按主分类选择子分类
5. **UI一致性**: 与项目整体设计语言保持一致

**参考实现**:
- `RecordFragment.java` (第1190-1600行): 分类选择逻辑
- `CategoryAdapter.java`: 分类适配器实现
- `dialog_select_sub_category.xml`: 子分类选择对话框

**向后兼容**:
- 旧数据没有subCategory字段时，默认为空字符串
- JSON反序列化时使用`optString("subCategory", "")`确保兼容性
