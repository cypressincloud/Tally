# Requirements Document

## Introduction

本文档定义了"AI分类关键字批量输入"功能的需求。该功能是对现有AI分类关键字规则功能的增强，允许用户在添加或编辑规则时，通过空格分隔的方式批量输入多个关键字，系统将自动为同一个分类创建多条规则，从而提高用户设置关键字规则的效率。

## Glossary

- **Ai_Category_Rule_Activity**: AI分类关键字规则管理页面，用户可以查看、添加、编辑和删除关键字规则
- **Keyword_Input_Field**: 关键字输入框，用户在此输入一个或多个关键字
- **Batch_Input_Parser**: 批量输入解析器，负责将空格分隔的关键字字符串解析为多个独立的关键字
- **Rule_Storage**: 规则存储模块，使用SharedPreferences持久化存储关键字规则
- **Category_Rule**: 关键字规则对象，包含关键字、主分类和子分类信息
- **System**: 整个AI分类关键字批量输入功能系统

## Requirements

### Requirement 1: 批量输入格式支持

**User Story:** 作为用户，我希望能够在关键字输入框中输入多个关键字并用空格分隔，以便一次性为同一个分类设置多个关键字规则。

#### Acceptance Criteria

1. THE Keyword_Input_Field SHALL 接受包含空格的文本输入
2. THE Keyword_Input_Field SHALL 在输入提示中显示"输入关键字，多个关键字用空格分隔"
3. WHEN 用户输入包含空格的文本, THE System SHALL 将其识别为批量输入模式
4. THE Batch_Input_Parser SHALL 使用空格作为关键字分隔符
5. THE Batch_Input_Parser SHALL 移除关键字前后的空白字符
6. THE Batch_Input_Parser SHALL 过滤掉空字符串关键字

### Requirement 2: 批量输入解析

**User Story:** 作为用户，我希望系统能够正确解析我输入的多个关键字，以便每个关键字都能生成独立的规则。

#### Acceptance Criteria

1. WHEN 用户输入"外卖 美团 饿了么", THE Batch_Input_Parser SHALL 解析为三个关键字["外卖", "美团", "饿了么"]
2. WHEN 用户输入包含多个连续空格的文本, THE Batch_Input_Parser SHALL 将连续空格视为单个分隔符
3. WHEN 用户输入前后包含空格的文本, THE Batch_Input_Parser SHALL 移除首尾空格
4. WHEN 用户输入单个关键字（不含空格）, THE Batch_Input_Parser SHALL 解析为单个关键字列表
5. WHEN 用户输入仅包含空格的文本, THE Batch_Input_Parser SHALL 返回空列表

### Requirement 3: 批量规则创建

**User Story:** 作为用户，我希望系统能够为每个解析出的关键字创建独立的规则，以便所有关键字都能生效。

#### Acceptance Criteria

1. WHEN 用户在添加规则对话框中输入多个关键字并确认, THE System SHALL 为每个关键字创建一条独立的Category_Rule
2. THE System SHALL 为所有批量创建的规则使用相同的主分类
3. THE System SHALL 为所有批量创建的规则使用相同的子分类
4. WHEN 批量创建规则时, THE System SHALL 按输入顺序依次保存每条规则到Rule_Storage
5. WHEN 所有规则保存成功, THE System SHALL 刷新规则列表
6. WHEN 所有规则保存成功, THE System SHALL 显示成功提示"已添加 N 条规则"

### Requirement 4: 重复关键字检测

**User Story:** 作为用户，我希望系统能够检测并提示重复的关键字，以便避免创建重复的规则。

#### Acceptance Criteria

1. WHEN 用户输入的关键字列表中包含重复项, THE System SHALL 自动去重
2. WHEN 用户输入的关键字与已存在的规则重复, THE System SHALL 显示警告对话框
3. THE 警告对话框 SHALL 列出所有重复的关键字
4. THE 警告对话框 SHALL 提供"跳过重复项"和"取消"两个选项
5. WHEN 用户选择"跳过重复项", THE System SHALL 仅为不重复的关键字创建规则
6. WHEN 用户选择"取消", THE System SHALL 关闭对话框不创建任何规则

### Requirement 5: 批量输入验证

**User Story:** 作为用户，我希望系统能够验证我的批量输入，以便及时发现输入错误。

#### Acceptance Criteria

1. WHEN 用户输入的文本解析后为空列表, THE System SHALL 显示错误提示"请输入至少一个关键字"
2. WHEN 用户未选择分类, THE System SHALL 显示错误提示"请选择分类"
3. WHEN 单个关键字长度超过50个字符, THE System SHALL 显示错误提示"关键字'{关键字}'过长，最多50个字符"
4. WHEN 批量输入的关键字数量超过20个, THE System SHALL 显示警告提示"一次最多添加20个关键字"
5. THE System SHALL 在显示错误提示后保持对话框打开状态

### Requirement 6: 编辑模式下的批量输入

**User Story:** 作为用户，我希望在编辑规则时也能使用批量输入功能，以便快速修改规则。

#### Acceptance Criteria

1. WHEN 用户在编辑规则对话框中输入多个关键字, THE System SHALL 删除原有规则
2. WHEN 用户在编辑规则对话框中输入多个关键字, THE System SHALL 为每个新关键字创建独立的规则
3. THE System SHALL 为所有新创建的规则使用用户选择的分类
4. WHEN 编辑操作完成, THE System SHALL 刷新规则列表
5. WHEN 编辑操作完成, THE System SHALL 显示成功提示"已更新为 N 条规则"

### Requirement 7: UI提示优化

**User Story:** 作为用户，我希望界面能够清楚地提示我如何使用批量输入功能，以便我能够正确使用该功能。

#### Acceptance Criteria

1. THE Keyword_Input_Field SHALL 显示提示文本"输入关键字，多个关键字用空格分隔"
2. THE 添加/编辑规则对话框 SHALL 在关键字输入框下方显示示例文本"例如：外卖 美团 饿了么"
3. THE 示例文本 SHALL 使用次要文本颜色#888888
4. THE 示例文本 SHALL 使用12sp字体大小
5. WHEN 用户输入包含空格的文本, THE System SHALL 在对话框底部显示预览"将创建 N 条规则"

### Requirement 8: 批量操作反馈

**User Story:** 作为用户，我希望在批量创建规则时能够看到操作进度，以便了解操作是否成功。

#### Acceptance Criteria

1. WHEN 批量创建规则数量大于5条, THE System SHALL 显示进度对话框
2. THE 进度对话框 SHALL 显示当前进度"正在创建规则 X/N"
3. THE 进度对话框 SHALL 使用不可取消的模式
4. WHEN 所有规则创建完成, THE System SHALL 自动关闭进度对话框
5. WHEN 批量创建过程中发生错误, THE System SHALL 显示错误提示并停止创建

### Requirement 9: 数据持久化

**User Story:** 作为用户，我希望批量创建的规则能够被正确保存，以便下次打开应用时规则仍然有效。

#### Acceptance Criteria

1. THE System SHALL 将每条批量创建的规则独立保存到Rule_Storage
2. THE Rule_Storage SHALL 使用JSON数组格式存储所有规则
3. WHEN 批量创建规则完成, THE System SHALL 触发WebDAV自动同步
4. THE System SHALL 使用BackupManager.triggerAutoUploadIfEnabled()触发自动同步
5. WHEN 应用重启后, THE System SHALL 正确加载所有批量创建的规则

### Requirement 10: 向后兼容性

**User Story:** 作为用户，我希望升级后原有的单关键字输入方式仍然可用，以便我可以选择适合的输入方式。

#### Acceptance Criteria

1. WHEN 用户输入单个关键字（不含空格）, THE System SHALL 按原有逻辑创建单条规则
2. THE System SHALL 保持原有的单关键字输入体验不变
3. THE System SHALL 正确加载和显示旧版本创建的规则
4. WHEN 用户编辑旧版本创建的规则, THE System SHALL 支持批量输入功能

### Requirement 11: 特殊字符处理

**User Story:** 作为用户，我希望系统能够正确处理包含特殊字符的关键字，以便我可以设置各种类型的关键字。

#### Acceptance Criteria

1. THE Batch_Input_Parser SHALL 保留关键字中的中文字符
2. THE Batch_Input_Parser SHALL 保留关键字中的英文字母和数字
3. THE Batch_Input_Parser SHALL 保留关键字中的常见标点符号（如：、。！？）
4. THE Batch_Input_Parser SHALL 使用空格作为唯一的分隔符
5. WHEN 关键字包含制表符或换行符, THE Batch_Input_Parser SHALL 将其视为空格处理

### Requirement 12: 批量删除提示

**User Story:** 作为用户，我希望在删除规则时能够看到相关的批量创建信息，以便更好地管理规则。

#### Acceptance Criteria

1. THE 规则列表项 SHALL 保持现有的删除功能不变
2. THE 删除确认对话框 SHALL 显示标准提示"确定要删除此规则吗？"
3. WHEN 用户删除规则, THE System SHALL 仅删除选中的单条规则
4. THE System SHALL 不提供批量删除功能（保持简单）
