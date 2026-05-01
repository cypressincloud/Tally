# 隐藏后台功能代码清理 - 2026-05-01

## 清理内容

已清理 `app/app/` 目录下的所有多余代码，只保留 `app/src/` 目录下的最终实现方案。

### 删除的文件

1. **app/app/src/main/java/com/example/budgetapp/ui/HideBackgroundActivity.java**
   - 删除原因：重复文件，实际使用的是 app/src 目录下的版本

2. **app/app/src/main/res/layout/activity_hide_background.xml**
   - 删除原因：重复文件，实际使用的是 app/src 目录下的版本

3. **app/app/src/main/res/drawable/ic_back.xml**
   - 删除原因：重复文件，实际使用的是 app/src 目录下的版本

### 清理的代码

1. **app/app/src/main/java/com/example/budgetapp/ui/SettingsActivity.java**
   - 删除了 `btn_hide_background` 的点击事件处理代码

2. **app/app/src/main/res/layout/activity_settings.xml**
   - 删除了 `btn_hide_background` 按钮及其分隔线

3. **app/app/src/main/AndroidManifest.xml**
   - 删除了 `HideBackgroundActivity` 的注册

4. **app/app/src/main/java/com/example/budgetapp/MainActivity.java**
   - 删除了 `onCreate` 中调用 `applyHideBackgroundSetting()` 的代码
   - 删除了 `applyHideBackgroundSetting()` 方法
   - 删除了 `onStop()` 方法

## 最终保留的实现（仅在 app/src/ 目录）

### 功能文件

1. **app/src/main/java/com/example/budgetapp/ui/HideBackgroundActivity.java**
   - 隐藏后台设置页面

2. **app/src/main/res/layout/activity_hide_background.xml**
   - 设置页面布局

3. **app/src/main/res/drawable/ic_back.xml**
   - 返回按钮图标

4. **app/src/main/java/com/example/budgetapp/ui/SettingsActivity.java**
   - 添加了 `btn_hide_background` 的点击事件

5. **app/src/main/res/layout/activity_settings.xml**
   - 添加了"隐藏后台"选项按钮

6. **app/src/main/AndroidManifest.xml**
   - 注册了 `HideBackgroundActivity`

7. **app/src/main/java/com/example/budgetapp/MainActivity.java**
   - 实现了隐藏后台的核心逻辑：
     - `applyHideBackgroundSetting()` - 设置 FLAG_SECURE
     - `onStop()` - 调用 `finishAndRemoveTask()` 关闭应用
     - `onResume()` - 重新应用设置

## 验证结果

所有文件编译通过，无诊断错误。

## 功能说明

最终实现的功能：
- 用户可以在设置中开启"隐藏后台"功能
- 开启后，应用切换到后台时会自动关闭
- 应用完全不会出现在最近任务列表中
- 用户需要通过桌面图标重新打开应用
- 同时设置 FLAG_SECURE 防止截屏
