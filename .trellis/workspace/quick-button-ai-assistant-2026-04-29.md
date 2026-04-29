# 快捷按钮设置 - 添加"直接进入AI记账助手"选项

**日期**: 2026-04-29  
**任务**: 在设置里面的"快捷按钮设置"加一个"直接进入AI记账助手"

## 修改概述

为快捷按钮设置对话框添加了第三个选项"直接进入AI记账助手"，用户可以设置快捷按钮直接跳转到AI记账助手页面。

## 修改文件

### 1. 布局文件修改

#### app/src/main/res/layout/dialog_quick_record_settings.xml
- 添加了新的 RadioButton: `rb_quick_ai_assistant`
- 文本: "直接进入AI记账助手"
- 保持与其他选项一致的样式

#### app/app/src/main/res/layout/dialog_quick_record_settings.xml
- 同步修改 app/app 目录下的布局文件

### 2. SettingsActivity 修改

#### app/src/main/java/com/example/budgetapp/ui/SettingsActivity.java
**showQuickRecordSettingDialog() 方法**:
- 添加对 `currentMode == 2` 的判断，选中 `rb_quick_ai_assistant`
- 在 RadioGroup 监听器中添加对 `rb_quick_ai_assistant` 的处理
- 设置 `selectedMode = 2` 和提示文本 "直接进入AI记账助手"

#### app/app/src/main/java/com/example/budgetapp/ui/SettingsActivity.java
- 同步修改 app/app 目录下的文件

### 3. RecordFragment 修改

#### app/src/main/java/com/example/budgetapp/ui/RecordFragment.java
**快捷按钮点击事件**:
- 添加对 `quickMode == 2` 的判断
- 检查 AI 配置是否启用和就绪
- 如果配置正确，跳转到 `AiChatActivity`
- 如果配置未完成，提示用户先配置 AI 记账

#### app/app/src/main/java/com/example/budgetapp/ui/RecordFragment.java
**导入语句**:
- 添加 `import com.example.budgetapp.ai.AiConfig;`
- 同步修改 app/app 目录下的文件

## 功能说明

### 快捷按钮模式

现在快捷按钮支持三种模式：

1. **模式 0 (默认)**: 进入账单详情页
   - 点击快捷按钮后显示当天的账单详情对话框

2. **模式 1**: 直接进入记一笔
   - 点击快捷按钮后直接打开记账对话框

3. **模式 2 (新增)**: 直接进入AI记账助手
   - 点击快捷按钮后直接跳转到 AI 记账助手页面
   - 需要先配置 AI 记账功能（Base URL、API Key、文本模型）
   - 如果未配置，会提示用户先完成配置

### 配置方式

用户可以通过以下路径设置快捷按钮行为：
1. 进入"设置"
2. 点击"快捷按钮设置"
3. 选择三个选项之一：
   - 进入账单详情页
   - 直接进入记一笔
   - 直接进入AI记账助手 ⭐ 新增

### 存储方式

- 使用 SharedPreferences 存储: `app_prefs`
- Key: `quick_record_mode`
- 值:
  - `0`: 进入账单详情页
  - `1`: 直接进入记一笔
  - `2`: 直接进入AI记账助手

## 技术细节

### AI 配置检查

在跳转到 AI 记账助手前，会检查：
```java
AiConfig config = AiConfig.load(requireContext());
if (config.isEnabledAndReady()) {
    startActivity(new Intent(requireContext(), AiChatActivity.class));
} else {
    Toast.makeText(requireContext(), 
        "请先在设置中启用 AI 记账，并至少填写 Base URL、API Key、文本模型。", 
        Toast.LENGTH_LONG).show();
}
```

### 目标 Activity

- **AiChatActivity**: AI 记账助手页面
- 位置: `com.example.budgetapp.ui.AiChatActivity`

## 验证结果

✅ 编译成功  
✅ 布局文件修改完成  
✅ 逻辑代码修改完成  
✅ app 和 app/app 目录同步修改

## 用户体验

1. **便捷性**: 用户可以一键直接进入 AI 记账助手，无需多次点击
2. **灵活性**: 三种模式满足不同用户的使用习惯
3. **安全性**: 未配置 AI 时会给出明确提示，引导用户完成配置
4. **一致性**: 与现有的两个选项保持一致的交互体验

## 后续建议

1. 可以考虑在长按快捷按钮时显示快捷菜单，让用户临时选择不同的行为
2. 可以添加快捷按钮的图标变化，根据不同模式显示不同图标
3. 可以在首次使用时显示引导提示，帮助用户了解快捷按钮的功能
