# AI系统提示词自定义 - 数据库线程问题修复

**日期**: 2026-05-08  
**问题**: 应用崩溃 - 在主线程访问数据库  
**状态**: ✅ 已修复

## 问题描述

应用在打开 `AiPromptEditorActivity` 时崩溃，错误信息：

```
java.lang.IllegalStateException: Cannot access database on the main thread 
since it may potentially lock the UI for a long period of time.

at com.example.budgetapp.database.AssetAccountDao_Impl.getAllAssetsSync(AssetAccountDao_Impl.java:318)
at com.example.budgetapp.ai.AiAccountingClient.appendAssets(AiAccountingClient.java:489)
at com.example.budgetapp.ai.AiAccountingClient.buildSystemPrompt(AiAccountingClient.java:443)
at com.example.budgetapp.ui.AiPromptEditorActivity.loadPrompt(AiPromptEditorActivity.java:108)
at com.example.budgetapp.ui.AiPromptEditorActivity.onCreate(AiPromptEditorActivity.java:70)
```

## 问题原因

### 调用链分析

1. `AiPromptEditorActivity.onCreate()` 在主线程调用 `loadPrompt()`
2. `loadPrompt()` 调用 `AiAccountingClient.buildSystemPrompt()`
3. `buildSystemPrompt()` 调用 `appendAssets()`
4. `appendAssets()` 调用 `getAllAssetsSync()` **访问数据库**
5. Room 数据库不允许在主线程进行同步查询操作

### 根本原因

`buildSystemPrompt()` 方法需要从数据库读取资产账户列表来构建完整的提示词，但这个操作在主线程执行，违反了 Android Room 的线程安全规则。

## 解决方案

将数据库访问操作移到后台线程执行，然后在主线程更新 UI。

### 修改 1: `loadPrompt()` 方法

**修改文件**: `app/src/main/java/com/example/budgetapp/ui/AiPromptEditorActivity.java`

**修改前**:
```java
private void loadPrompt() {
    String prompt;
    if (PromptManager.hasCustomPrompt(this)) {
        prompt = PromptManager.getCustomPrompt(this);
        isCustomPrompt = true;
        updateStatusIndicator(true);
    } else {
        prompt = new AiAccountingClient().buildSystemPrompt(this);
        isCustomPrompt = false;
        updateStatusIndicator(false);
    }
    
    etPromptContent.setText(prompt);
    updateCharCount();
}
```

**修改后**:
```java
private void loadPrompt() {
    // 在后台线程加载提示词
    new Thread(() -> {
        String prompt;
        boolean isCustom;
        
        if (PromptManager.hasCustomPrompt(this)) {
            prompt = PromptManager.getCustomPrompt(this);
            isCustom = true;
        } else {
            prompt = new AiAccountingClient().buildSystemPrompt(this);
            isCustom = false;
        }
        
        // 在主线程更新 UI
        final String finalPrompt = prompt;
        final boolean finalIsCustom = isCustom;
        runOnUiThread(() -> {
            isCustomPrompt = finalIsCustom;
            updateStatusIndicator(finalIsCustom);
            etPromptContent.setText(finalPrompt);
            updateCharCount();
        });
    }).start();
}
```

### 修改 2: `showRestoreDefaultDialog()` 中的恢复默认逻辑

**修改前**:
```java
btnConfirm.setOnClickListener(v -> {
    // 清除自定义提示词
    PromptManager.clearCustomPrompt(this);
    
    // 加载默认提示词
    String defaultPrompt = new AiAccountingClient().buildSystemPrompt(this);
    etPromptContent.setText(defaultPrompt);
    
    isCustomPrompt = false;
    updateStatusIndicator(false);
    updateCharCount();
    
    Toast.makeText(this, "已恢复默认提示词", Toast.LENGTH_SHORT).show();
    dialog.dismiss();
});
```

**修改后**:
```java
btnConfirm.setOnClickListener(v -> {
    // 清除自定义提示词
    PromptManager.clearCustomPrompt(this);
    
    // 在后台线程加载默认提示词
    new Thread(() -> {
        String defaultPrompt = new AiAccountingClient().buildSystemPrompt(this);
        
        // 在主线程更新 UI
        runOnUiThread(() -> {
            etPromptContent.setText(defaultPrompt);
            isCustomPrompt = false;
            updateStatusIndicator(false);
            updateCharCount();
            Toast.makeText(this, "已恢复默认提示词", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }).start();
});
```

## 技术细节

### Android 线程模型

1. **主线程（UI 线程）**: 
   - 负责处理 UI 更新和用户交互
   - 不能执行耗时操作（如数据库访问、网络请求）
   - 阻塞主线程会导致 ANR（Application Not Responding）

2. **后台线程**: 
   - 用于执行耗时操作
   - 不能直接更新 UI
   - 需要通过 `runOnUiThread()` 或 Handler 切换到主线程更新 UI

### Room 数据库线程规则

Room 默认不允许在主线程执行数据库操作，原因：
- 数据库查询可能耗时较长
- 阻塞主线程会导致 UI 卡顿
- 可能导致 ANR

### 解决方案模式

```java
// 1. 在后台线程执行耗时操作
new Thread(() -> {
    // 数据库访问或其他耗时操作
    String result = performDatabaseOperation();
    
    // 2. 切换到主线程更新 UI
    runOnUiThread(() -> {
        updateUI(result);
    });
}).start();
```

## 验证结果

✅ **编译检查**: 无错误  
✅ **线程安全**: 数据库访问在后台线程执行  
✅ **UI 更新**: 在主线程执行  
✅ **功能完整**: 保持原有功能不变

## 影响范围

### 修改的方法
1. `loadPrompt()` - Activity 启动时加载提示词
2. `showRestoreDefaultDialog()` 中的确认按钮点击事件 - 恢复默认提示词

### 不受影响的功能
- ✅ 保存自定义提示词（不涉及数据库访问）
- ✅ 查看规则说明（不涉及数据库访问）
- ✅ 字符数统计（纯 UI 操作）
- ✅ 状态指示器（纯 UI 操作）

## 用户体验

### 修改前
- 应用崩溃，无法使用

### 修改后
- ✅ 应用正常启动
- ✅ 提示词正常加载（可能有轻微延迟，但不会阻塞 UI）
- ✅ 恢复默认功能正常工作
- ✅ 无 ANR 风险

## 最佳实践

### Android 开发规范
1. **永远不要在主线程执行数据库操作**
2. **使用后台线程处理耗时操作**
3. **使用 `runOnUiThread()` 更新 UI**
4. **考虑使用 LiveData、ViewModel、Coroutines 等现代方案**

### 可选的改进方案

如果未来需要进一步优化，可以考虑：

1. **使用 AsyncTask**（已废弃，不推荐）
2. **使用 ExecutorService**:
```java
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.execute(() -> {
    // 后台操作
    runOnUiThread(() -> {
        // UI 更新
    });
});
```

3. **使用 Kotlin Coroutines**（推荐，但需要 Kotlin）:
```kotlin
lifecycleScope.launch {
    val prompt = withContext(Dispatchers.IO) {
        buildSystemPrompt()
    }
    updateUI(prompt)
}
```

4. **使用 LiveData + ViewModel**（推荐）:
```java
viewModel.getPrompt().observe(this, prompt -> {
    updateUI(prompt);
});
```

## 总结

✅ **问题已修复**: 应用不再崩溃  
✅ **线程安全**: 遵循 Android 线程模型  
✅ **功能完整**: 所有功能正常工作  
✅ **用户体验**: 无阻塞，响应流畅

该修复确保了应用的稳定性和用户体验，同时遵循了 Android 开发的最佳实践。
