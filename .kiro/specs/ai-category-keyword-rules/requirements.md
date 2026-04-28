# Requirements Document

## Introduction

本文档定义了"AI分类关键字规则"功能的需求。该功能允许用户在AI记账配置页面中管理关键字到分类的映射规则，当AI识别账单时，如果账单描述包含某个关键字，系统将自动将该账单归类到对应的分类中。

## Glossary

- **AI_Setting_Activity**: AI记账配置页面，用户可以配置AI记账的相关设置
- **Ai_Category_Rule_Activity**: AI分类关键字规则管理页面，用户可以查看、添加、编辑和删除关键字规则
- **Keyword_Rule**: 关键字规则，包含关键字和对应的分类信息
- **Category_Manager**: 分类管理器，负责获取和管理用户的收入和支出分类
- **Rule_Storage**: 规则存储模块，使用SharedPreferences持久化存储关键字规则
- **Rule_List**: 关键字规则列表，展示所有已设置的关键字规则
- **System**: 整个AI分类关键字规则功能系统

## Requirements

### Requirement 1: 入口卡片显示

**User Story:** 作为用户，我希望在AI记账配置页面看到"AI分类关键字规则"入口卡片，以便我可以访问关键字规则管理功能。

#### Acceptance Criteria

1. THE AI_Setting_Activity SHALL 在"启用AI记账"开关下方显示一个新的卡片组件
2. THE 卡片组件 SHALL 显示标题"AI分类关键字规则"
3. THE 卡片组件 SHALL 显示描述文本"设置关键字自动分类规则"
4. THE 卡片组件 SHALL 使用圆角20dp的卡片式设计
5. THE 卡片组件 SHALL 使用白色背景色
6. THE 卡片组件 SHALL 在卡片右侧显示向右箭头图标

### Requirement 2: 页面跳转

**User Story:** 作为用户，我希望点击"AI分类关键字规则"卡片后能够进入规则管理页面，以便我可以管理关键字规则。

#### Acceptance Criteria

1. WHEN 用户点击"AI分类关键字规则"卡片, THE AI_Setting_Activity SHALL 启动Ai_Category_Rule_Activity
2. THE Ai_Category_Rule_Activity SHALL 在AndroidManifest.xml中正确注册
3. THE Ai_Category_Rule_Activity SHALL 使用与项目一致的沉浸式状态栏设置

### Requirement 3: 规则列表展示

**User Story:** 作为用户，我希望在规则管理页面看到所有已设置的关键字规则，以便我可以了解当前的规则配置。

#### Acceptance Criteria

1. THE Ai_Category_Rule_Activity SHALL 使用RecyclerView展示关键字规则列表
2. WHEN 规则列表为空, THE System SHALL 显示空状态提示"暂无规则，点击下方按钮添加"
3. WHEN 规则列表不为空, THE System SHALL 显示每条规则的关键字和对应分类
4. THE 规则列表项 SHALL 使用卡片式设计，圆角20dp
5. THE 规则列表项 SHALL 显示关键字文本和分类文本
6. THE 规则列表项 SHALL 在右侧显示编辑图标

### Requirement 4: 添加新规则

**User Story:** 作为用户，我希望能够添加新的关键字规则，以便AI可以根据我的规则自动分类账单。

#### Acceptance Criteria

1. THE Ai_Category_Rule_Activity SHALL 在页面底部显示"添加规则"按钮
2. WHEN 用户点击"添加规则"按钮, THE System SHALL 显示添加规则对话框
3. THE 添加规则对话框 SHALL 包含关键字输入框
4. THE 添加规则对话框 SHALL 包含分类选择器
5. THE 分类选择器 SHALL 从Category_Manager获取用户的收入和支出分类列表
6. THE 分类选择器 SHALL 显示所有可用的分类选项
7. WHEN 用户输入关键字并选择分类后点击确认, THE System SHALL 保存新规则到Rule_Storage
8. WHEN 用户输入的关键字为空, THE System SHALL 显示错误提示"请输入关键字"
9. WHEN 用户未选择分类, THE System SHALL 显示错误提示"请选择分类"
10. WHEN 规则保存成功, THE System SHALL 刷新规则列表并显示成功提示

### Requirement 5: 编辑已有规则

**User Story:** 作为用户，我希望能够编辑已有的关键字规则，以便我可以修正或更新规则配置。

#### Acceptance Criteria

1. WHEN 用户点击规则列表项的编辑图标, THE System SHALL 显示编辑规则对话框
2. THE 编辑规则对话框 SHALL 预填充当前规则的关键字和分类
3. THE 编辑规则对话框 SHALL 允许用户修改关键字
4. THE 编辑规则对话框 SHALL 允许用户修改分类
5. WHEN 用户修改后点击确认, THE System SHALL 更新规则到Rule_Storage
6. WHEN 用户输入的关键字为空, THE System SHALL 显示错误提示"请输入关键字"
7. WHEN 用户未选择分类, THE System SHALL 显示错误提示"请选择分类"
8. WHEN 规则更新成功, THE System SHALL 刷新规则列表并显示成功提示

### Requirement 6: 删除规则

**User Story:** 作为用户，我希望能够删除不需要的关键字规则，以便保持规则列表的整洁。

#### Acceptance Criteria

1. WHEN 用户长按规则列表项, THE System SHALL 显示删除确认对话框
2. THE 删除确认对话框 SHALL 显示提示文本"确定要删除此规则吗？"
3. WHEN 用户确认删除, THE System SHALL 从Rule_Storage中删除该规则
4. WHEN 规则删除成功, THE System SHALL 刷新规则列表并显示成功提示
5. WHEN 用户取消删除, THE System SHALL 关闭对话框不执行删除操作

### Requirement 7: 规则持久化存储

**User Story:** 作为用户，我希望我设置的关键字规则能够被保存，以便下次打开应用时规则仍然有效。

#### Acceptance Criteria

1. THE Rule_Storage SHALL 使用SharedPreferences存储关键字规则
2. THE Rule_Storage SHALL 使用JSON格式序列化规则列表
3. THE Rule_Storage SHALL 使用独立的SharedPreferences文件"ai_category_rule_prefs"
4. WHEN 应用启动时, THE System SHALL 从Rule_Storage加载已保存的规则
5. WHEN 规则发生变更时, THE System SHALL 立即保存到Rule_Storage
6. THE Rule_Storage SHALL 在保存成功后触发WebDAV自动同步

### Requirement 8: 分类数据获取

**User Story:** 作为用户，我希望在选择分类时能够看到我已经设置的所有分类，以便我可以选择正确的分类。

#### Acceptance Criteria

1. THE System SHALL 通过Category_Manager.getExpenseCategories()获取支出分类列表
2. THE System SHALL 通过Category_Manager.getIncomeCategories()获取收入分类列表
3. THE 分类选择器 SHALL 分组显示收入分类和支出分类
4. THE 分类选择器 SHALL 在收入分类组显示标题"收入分类"
5. THE 分类选择器 SHALL 在支出分类组显示标题"支出分类"
6. WHEN Category_Manager返回空列表, THE System SHALL 显示提示"暂无分类，请先在分类设置中添加"

### Requirement 9: UI风格一致性

**User Story:** 作为用户，我希望新功能的界面风格与应用整体保持一致，以便获得统一的用户体验。

#### Acceptance Criteria

1. THE Ai_Category_Rule_Activity SHALL 使用与项目一致的背景色@color/bar_background
2. THE 卡片组件 SHALL 使用圆角20dp
3. THE 卡片组件 SHALL 使用白色背景@color/white
4. THE 卡片组件 SHALL 使用0dp的elevation
5. THE 按钮组件 SHALL 使用主题色#327ffc作为背景色
6. THE 按钮组件 SHALL 使用圆角16dp
7. THE 文本颜色 SHALL 使用?android:attr/textColorPrimary作为主要文本色
8. THE 次要文本颜色 SHALL 使用#888888
9. THE 页面标题 SHALL 使用28sp粗体文本
10. THE 卡片内边距 SHALL 使用20dp

### Requirement 10: AI分类应用规则

**User Story:** 作为用户，我希望AI识别账单时能够自动应用我设置的关键字规则，以便账单能够自动归类到正确的分类。

#### Acceptance Criteria

1. WHEN AI识别账单描述文本时, THE System SHALL 检查所有已设置的关键字规则
2. WHEN 账单描述包含某个关键字, THE System SHALL 将该账单的分类设置为对应的分类
3. WHEN 账单描述包含多个关键字, THE System SHALL 使用第一个匹配的规则
4. THE 关键字匹配 SHALL 使用不区分大小写的包含匹配
5. WHEN 没有关键字匹配, THE System SHALL 使用AI原始识别的分类
6. THE 规则应用逻辑 SHALL 在AI识别完成后、保存账单前执行

### Requirement 11: UI优化 - CategoryAdapter集成

**User Story:** 作为用户，我希望分类选择界面与应用其他页面保持一致，以便获得统一的用户体验。

#### Acceptance Criteria

1. THE 添加/编辑规则对话框 SHALL 使用RecyclerView + CategoryAdapter替代Spinner进行分类选择
2. THE CategoryAdapter SHALL 根据CategoryManager.isDetailedCategoryEnabled()动态切换布局模式
3. WHEN 详细分类模式启用时, THE System SHALL 使用FlexboxLayout展示chip样式的分类按钮
4. WHEN 详细分类模式未启用时, THE System SHALL 使用GridLayoutManager(5列)展示网格样式的分类按钮
5. THE 分类按钮 SHALL 使用与项目一致的颜色方案（选中：app_yellow，未选中：cat_unselected_bg）
6. THE 对话框 SHALL 显示"已选择"提示，实时显示当前选中的主分类和子分类

### Requirement 12: UI优化 - 两级分类支持

**User Story:** 作为用户，我希望能够为关键字规则设置子分类，以便更精确地管理分类规则。

#### Acceptance Criteria

1. THE CategoryRule模型 SHALL 添加subCategory字段存储子分类名称
2. WHEN 用户单击分类按钮, THE System SHALL 选择该主分类并清空子分类
3. WHEN 用户长按分类按钮, THE System SHALL 显示子分类选择对话框（如果启用了子分类功能）
4. THE 子分类选择对话框 SHALL 使用dialog_select_sub_category.xml布局
5. THE 子分类选择对话框 SHALL 使用ChipGroup展示子分类列表
6. WHEN 用户点击子分类Chip, THE System SHALL 选择该子分类并关闭对话框
7. WHEN 用户再次点击已选中的Chip, THE System SHALL 取消子分类选择
8. WHEN 子分类列表为空, THE System SHALL 显示"暂无细分选项"提示
9. THE 规则列表项 SHALL 显示主分类和子分类（格式："{类型} - {主分类} > {子分类}"）
10. WHEN 规则应用时, THE System SHALL 同时设置账单的主分类和子分类

### Requirement 13: UI优化 - 向后兼容性

**User Story:** 作为用户，我希望升级后旧的规则数据仍然可用，以便不丢失已设置的规则。

#### Acceptance Criteria

1. WHEN 加载旧版本规则数据（没有subCategory字段）, THE System SHALL 将subCategory默认设置为空字符串
2. THE JSON反序列化 SHALL 使用optString("subCategory", "")确保向后兼容
3. WHEN 显示没有子分类的规则, THE System SHALL 只显示主分类（格式："{类型} - {主分类}"）
4. THE 规则应用逻辑 SHALL 正确处理没有子分类的规则

