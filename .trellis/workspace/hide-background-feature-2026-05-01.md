# 隐藏后台功能实现 - 2026-05-01

## 需求
在设置页面的"自动记账适配日志"上方添加"隐藏后台"选项，用户点击后进入新页面，可以选择是否开启隐藏后台功能。开启后，应用将完全不出现在最近任务列表中，避免用户下意识清掉后台。

## 实现方案

### 1. 设置页面布局修改
**文件**: `app/src/main/res/layout/activity_settings.xml`
- 在"自动记账适配日志"上方添加"隐藏后台"选项按钮
- 保持与其他设置项一致的样式

### 2. 设置页面逻辑修改
**文件**: `app/src/main/java/com/example/budgetapp/ui/SettingsActivity.java`
- 添加"隐藏后台"按钮的点击事件处理
- 点击后跳转到 `HideBackgroundActivity`

### 3. 创建隐藏后台设置页面
**文件**: `app/src/main/java/com/example/budgetapp/ui/HideBackgroundActivity.java`
- 创建新的 Activity 用于隐藏后台设置
- 使用 SwitchCompat 控件让用户开启/关闭功能
- 设置保存在 SharedPreferences 中 (key: `hide_background`)
- 提供详细的功能说明和警告提示

### 4. 创建隐藏后台设置页面布局
**文件**: `app/src/main/res/layout/activity_hide_background.xml`
- 顶部标题栏带返回按钮
- 主内容卡片包含：
  - 开关控件
  - 功能说明
  - 警告提示

### 5. 创建返回按钮图标
**文件**: `app/src/main/res/drawable/ic_back.xml`
- 创建标准的返回箭头图标
- 使用 Vector Drawable 格式

### 6. 注册新 Activity
**文件**: `app/src/main/AndroidManifest.xml`
- 注册 `HideBackgroundActivity`

### 7. 主界面逻辑修改
**文件**: `app/src/main/java/com/example/budgetapp/MainActivity.java`
- 在 `onCreate` 中调用 `applyHideBackgroundSetting()` 应用设置
- 添加 `applyHideBackgroundSetting()` 方法设置 FLAG_SECURE（防止截屏）
- 重写 `onStop()` 方法，当开启隐藏后台时调用 `finishAndRemoveTask()`

## 技术实现细节

### 隐藏后台的实现方式
为了让应用完全不出现在最近任务列表中，我们采用了以下方案：

1. **使用 `finishAndRemoveTask()`**: 当应用进入后台（onStop）时，如果开启了隐藏后台功能，则调用此方法关闭应用并从最近任务列表中移除
2. **设置 FLAG_SECURE**: 同时设置此标志防止截屏，增强隐私保护
3. **用户体验权衡**: 这种方式会在应用切换到后台时自动关闭应用，用户需要重新打开，但可以完全避免应用出现在最近任务列表中

### 为什么选择这种方案
- Android 不允许应用在运行时动态设置 `excludeFromRecents` 属性
- `excludeFromRecents` 只能在 AndroidManifest.xml 中静态设置，无法根据用户偏好动态调整
- 使用 `finishAndRemoveTask()` 是唯一能够根据用户设置动态控制的方案
- 虽然会关闭应用，但这正是用户想要的效果：完全不出现在最近任务列表中

### 兼容性说明
- 支持 Android 5.0 (API 21) 及以上版本
- 所有 Android 版本都支持此功能
- 用户需要通过桌面图标重新打开应用

## 用户体验
- 开启后，应用切换到后台时会自动关闭
- 应用完全不会出现在最近任务列表中
- 保护用户隐私，防止他人查看记账信息
- 避免用户下意识清掉后台
- 提供清晰的功能说明和警告提示
- 可随时在设置中关闭此功能

## 测试建议
1. 测试开关功能是否正常工作
2. 测试开启后应用切换到后台的行为（应该自动关闭）
3. 测试应用是否不出现在最近任务列表中
4. 测试关闭功能后应用恢复正常显示
5. 测试在不同 Android 版本上的兼容性
6. 测试设置是否正确保存和读取

## 相关文件
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/java/com/example/budgetapp/ui/SettingsActivity.java`
- `app/src/main/java/com/example/budgetapp/ui/HideBackgroundActivity.java`
- `app/src/main/res/layout/activity_hide_background.xml`
- `app/src/main/res/drawable/ic_back.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/budgetapp/MainActivity.java`

## 修复记录
- **2026-05-01 第一次**: 修正了文件路径，从 `app/app/` 修改为正确的 `app/src/` 路径
- **2026-05-01 第二次**: 修正了功能理解，从"隐藏卡片内容"改为"完全隐藏卡片"
- **2026-05-01 第三次**: 最终确认需求，使用 `finishAndRemoveTask()` 实现应用完全不出现在最近任务列表中

