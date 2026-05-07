# 设计文档

## 简介

本文档描述AI系统提示词自定义功能的技术设计。


## 概述

本功能允许用户在AI设置页面查看和编辑AI系统提示词（System Prompt），实现对AI识别规则的完全自定义。用户可以根据个人需求调整提示词内容，并在所有AI识别场景（文本、截图、语音、对话）中生效。

### 核心目标

1. **可视化管理**：提供友好的UI界面查看和编辑系统提示词
2. **持久化存储**：使用SharedPreferences保存自定义提示词
3. **灵活切换**：支持自定义提示词与默认提示词之间的切换
4. **全局生效**：自定义提示词对所有AI识别场景生效

### 技术约束

- **存储位置**：`app/src/` (NOT `app/app/src/`)
- **存储方式**：SharedPreferences (`ai_prompt_prefs`, MODE_PRIVATE)
- **存储键名**：`custom_system_prompt`
- **修改目标**：`AiAccountingClient.buildSystemPrompt(Context)`
- **UI模式**：遵循 `AiSettingActivity`、`AiCategoryRuleActivity` 的设计模式


## 系统架构设计

### 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    AiSettingActivity                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  [AI分类关键字规则] CardView (已存在)                │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  [AI识别提示词] CardView (新增)                      │  │
│  │  - 标题: AI识别提示词                                │  │
│  │  - 描述: 自定义AI识别规则和行为                     │  │
│  │  - 点击: 跳转到 AiPromptEditorActivity               │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ Intent
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              AiPromptEditorActivity (新增)                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  状态指示器                                           │  │
│  │  - 当前使用: 默认提示词 / 自定义提示词               │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  多行文本编辑框 (EditText)                           │  │
│  │  - 显示当前提示词内容                                │  │
│  │  - 支持编辑                                           │  │
│  │  - 等宽字体                                           │  │
│  │  - 可滚动                                             │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  字符数统计 (TextView)                               │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  操作按钮区域                                         │  │
│  │  [查看规则说明] [恢复默认] [保存]                   │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ 使用
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              PromptManager (新增工具类)                      │
│  - getCustomPrompt(Context): String                         │
│  - saveCustomPrompt(Context, String): void                  │
│  - clearCustomPrompt(Context): void                         │
│  - hasCustomPrompt(Context): boolean                        │
│  - validatePrompt(String): ValidationResult                 │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ 读写
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              SharedPreferences                               │
│  文件名: ai_prompt_prefs                                    │
│  键名: custom_system_prompt                                 │
│  值: 自定义提示词文本                                       │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ 被调用
                            ▼
┌─────────────────────────────────────────────────────────────┐
│         AiAccountingClient.buildSystemPrompt()              │
│  修改逻辑:                                                   │
│  1. 检查是否存在自定义提示词                                │
│  2. 如果存在，返回自定义提示词                              │
│  3. 如果不存在，返回默认构建的提示词                        │
└─────────────────────────────────────────────────────────────┘
```

### 组件交互流程

#### 1. 用户打开提示词编辑器

```
用户 → AiSettingActivity → 点击[AI识别提示词]卡片
  → Intent → AiPromptEditorActivity.onCreate()
  → PromptManager.hasCustomPrompt()
  → 如果有自定义: PromptManager.getCustomPrompt()
  → 如果无自定义: AiAccountingClient.buildSystemPrompt()
  → 显示提示词内容
```

#### 2. 用户保存自定义提示词

```
用户 → 编辑提示词 → 点击[保存]按钮
  → PromptManager.validatePrompt()
  → 验证通过 → PromptManager.saveCustomPrompt()
  → SharedPreferences.edit().putString().apply()
  → 更新状态指示器
  → Toast提示保存成功
```

#### 3. 用户恢复默认提示词

```
用户 → 点击[恢复默认]按钮
  → 显示确认对话框
  → 用户确认 → PromptManager.clearCustomPrompt()
  → SharedPreferences.edit().remove().apply()
  → AiAccountingClient.buildSystemPrompt()
  → 显示默认提示词
  → 更新状态指示器
  → Toast提示恢复成功
```

#### 4. AI识别时使用提示词

```
AI识别请求 → AiAccountingClient.recognizeText/Image/Audio()
  → buildSystemPrompt(Context)
  → PromptManager.hasCustomPrompt()
  → 如果有: 返回 PromptManager.getCustomPrompt()
  → 如果无: 返回默认构建的提示词
  → 发送给AI模型
```


## 数据模型设计

### SharedPreferences 结构

```java
// 文件名
private static final String PREF_NAME = "ai_prompt_prefs";

// 键名
private static final String KEY_CUSTOM_PROMPT = "custom_system_prompt";

// 存储模式
Context.MODE_PRIVATE
```

### 数据格式

```java
// 自定义提示词存储格式
{
    "custom_system_prompt": "你是一个中文记账助手...(完整提示词文本)"
}

// 示例
SharedPreferences prefs = context.getSharedPreferences("ai_prompt_prefs", Context.MODE_PRIVATE);
String customPrompt = prefs.getString("custom_system_prompt", null);

// 如果 customPrompt == null，表示未设置自定义提示词
// 如果 customPrompt != null，表示已设置自定义提示词
```

### 验证结果数据结构

```java
public class ValidationResult {
    public boolean isValid;           // 是否验证通过
    public String errorMessage;       // 错误消息（如果验证失败）
    public String warningMessage;     // 警告消息（可选）
    
    public ValidationResult(boolean isValid, String errorMessage, String warningMessage) {
        this.isValid = isValid;
        this.errorMessage = errorMessage;
        this.warningMessage = warningMessage;
    }
    
    public static ValidationResult success() {
        return new ValidationResult(true, null, null);
    }
    
    public static ValidationResult error(String message) {
        return new ValidationResult(false, message, null);
    }
    
    public static ValidationResult warning(String message) {
        return new ValidationResult(true, null, message);
    }
}
```


## UI设计

### 1. AiSettingActivity 新增卡片

**布局文件**: `app/src/main/res/layout/activity_ai_setting.xml`

**插入位置**: 在 `card_ai_category_rules` CardView 之后

```xml
<!-- AI识别提示词卡片 -->
<androidx.cardview.widget.CardView
    android:id="@+id/card_ai_prompt"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginTop="16dp"
    app:cardBackgroundColor="@color/white"
    app:cardCornerRadius="20dp"
    app:cardElevation="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="20dp"
        android:gravity="center_vertical">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="AI识别提示词"
                android:textColor="?android:attr/textColorPrimary"
                android:textSize="16sp"
                android:textStyle="bold" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="自定义AI识别规则和行为"
                android:textColor="#888888"
                android:textSize="12sp" />
        </LinearLayout>

    </LinearLayout>
</androidx.cardview.widget.CardView>
```

### 2. AiPromptEditorActivity 布局

**布局文件**: `app/src/main/res/layout/activity_ai_prompt_editor.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bar_background">

    <!-- 标题栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:padding="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="AI识别提示词"
            android:textSize="28sp"
            android:textStyle="bold"
            android:textColor="?android:attr/textColorPrimary"/>
    </LinearLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingBottom="20dp">

            <!-- 状态指示器卡片 -->
            <androidx.cardview.widget.CardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginHorizontal="16dp"
                android:layout_marginTop="8dp"
                app:cardBackgroundColor="@color/white"
                app:cardCornerRadius="20dp"
                app:cardElevation="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <TextView
                        android:id="@+id/tv_status_indicator"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="当前使用：默认提示词"
                        android:textSize="14sp"
                        android:textStyle="bold"
                        android:textColor="#888888" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="提示词定义了AI如何识别和处理交易信息"
                        android:textSize="12sp"
                        android:textColor="#888888" />
                </LinearLayout>
            </androidx.cardview.widget.CardView>

            <!-- 提示词编辑器卡片 -->
            <androidx.cardview.widget.CardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginHorizontal="16dp"
                android:layout_marginTop="16dp"
                app:cardBackgroundColor="@color/white"
                app:cardCornerRadius="20dp"
                app:cardElevation="0dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="提示词内容"
                        android:textSize="14sp"
                        android:textStyle="bold"
                        android:textColor="@color/app_blue" />

                    <EditText
                        android:id="@+id/et_prompt_content"
                        android:layout_width="match_parent"
                        android:layout_height="400dp"
                        android:layout_marginTop="12dp"
                        android:background="@drawable/bg_edittext_rounded"
                        android:padding="16dp"
                        android:gravity="top|start"
                        android:inputType="textMultiLine"
                        android:scrollbars="vertical"
                        android:textSize="13sp"
                        android:fontFamily="monospace"
                        android:hint="在此输入或编辑系统提示词..."
                        android:textColor="?android:attr/textColorPrimary"
                        android:textColorHint="#CCCCCC" />

                    <TextView
                        android:id="@+id/tv_char_count"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="字符数: 0"
                        android:textSize="12sp"
                        android:textColor="#888888" />
                </LinearLayout>
            </androidx.cardview.widget.CardView>

        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

    <!-- 底部按钮区域 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:background="@color/bar_background">

        <Button
            android:id="@+id/btn_view_rules"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_height="56dp"
            android:layout_weight="1"
            android:layout_marginEnd="8dp"
            android:text="查看规则说明"
            android:textAllCaps="false"
            android:textSize="14sp"
            android:textColor="@color/button_text_yellow_selector"
            app:strokeColor="@color/button_stroke_yellow_selector"
            app:strokeWidth="2dp"
            app:cornerRadius="16dp"
            app:rippleColor="@color/app_blue" />

        <Button
            android:id="@+id/btn_restore_default"
            style="@style/Widget.MaterialComponents.Button.OutlinedButton"
            android:layout_width="0dp"
            android:layout_height="56dp"
            android:layout_weight="1"
            android:layout_marginStart="4dp"
            android:layout_marginEnd="4dp"
            android:text="恢复默认"
            android:textAllCaps="false"
            android:textSize="14sp"
            android:textColor="@color/button_text_yellow_selector"
            app:strokeColor="@color/button_stroke_yellow_selector"
            app:strokeWidth="2dp"
            app:cornerRadius="16dp"
            app:rippleColor="@color/app_blue" />

        <Button
            android:id="@+id/btn_save"
            android:layout_width="0dp"
            android:layout_height="56dp"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:backgroundTint="@color/app_blue"
            android:text="保存"
            android:textAllCaps="false"
            android:textColor="@color/real_white"
            android:textSize="14sp"
            app:cornerRadius="16dp" />
    </LinearLayout>
</LinearLayout>
```

### 3. 规则说明对话框布局

**布局文件**: `app/src/main/res/layout/dialog_prompt_rules.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="24dp"
    app:cardCornerRadius="20dp"
    app:cardElevation="0dp"
    app:cardBackgroundColor="@color/white">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="默认提示词规则说明"
            android:textSize="18sp"
            android:textStyle="bold"
            android:textColor="?android:attr/textColorPrimary" />

        <androidx.core.widget.NestedScrollView
            android:layout_width="match_parent"
            android:layout_height="400dp"
            android:layout_marginTop="16dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/tv_rules_content"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textSize="13sp"
                    android:lineSpacingExtra="4dp"
                    android:textColor="?android:attr/textColorPrimary" />

            </LinearLayout>
        </androidx.core.widget.NestedScrollView>

        <Button
            android:id="@+id/btn_close"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:layout_marginTop="16dp"
            android:backgroundTint="@color/app_blue"
            android:text="关闭"
            android:textAllCaps="false"
            android:textColor="@color/real_white"
            app:cornerRadius="12dp" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

### 4. 恢复默认确认对话框布局

**布局文件**: `app/src/main/res/layout/dialog_restore_default.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="32dp"
    app:cardCornerRadius="20dp"
    app:cardElevation="0dp"
    app:cardBackgroundColor="@color/white">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="恢复默认提示词"
            android:textSize="18sp"
            android:textStyle="bold"
            android:textColor="?android:attr/textColorPrimary" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="确定要恢复默认提示词吗？当前的自定义内容将被清除。"
            android:textSize="14sp"
            android:textColor="#666666" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:orientation="horizontal">

            <Button
                android:id="@+id/btn_cancel"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="0dp"
                android:layout_height="48dp"
                android:layout_weight="1"
                android:layout_marginEnd="8dp"
                android:text="取消"
                android:textAllCaps="false"
                android:textColor="@color/button_text_yellow_selector"
                app:strokeColor="@color/button_stroke_yellow_selector"
                app:strokeWidth="2dp"
                app:cornerRadius="12dp" />

            <Button
                android:id="@+id/btn_confirm"
                android:layout_width="0dp"
                android:layout_height="48dp"
                android:layout_weight="1"
                android:layout_marginStart="8dp"
                android:backgroundTint="@color/app_blue"
                android:text="确认"
                android:textAllCaps="false"
                android:textColor="@color/real_white"
                app:cornerRadius="12dp" />
        </LinearLayout>
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

### 5. EditText 背景 Drawable

**文件**: `app/src/main/res/drawable/bg_edittext_rounded.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#F5F5F5" />
    <corners android:radius="12dp" />
    <stroke
        android:width="1dp"
        android:color="#E0E0E0" />
</shape>
```

### UI 设计规范

#### 颜色规范
- **主色调**: `@color/app_blue` (#2196F3)
- **背景色**: `@color/bar_background` (#F5F5F5)
- **卡片背景**: `@color/white` (#FFFFFF)
- **文本主色**: `?android:attr/textColorPrimary`
- **文本次要色**: `#888888`
- **状态-默认**: `#888888` (灰色)
- **状态-自定义**: `@color/app_blue` (蓝色)
- **警告色**: `#FF9800` (橙色)
- **错误色**: `#F44336` (红色)

#### 尺寸规范
- **卡片圆角**: 20dp
- **卡片阴影**: 0dp (无阴影)
- **卡片水平边距**: 16dp
- **卡片垂直间距**: 16dp
- **卡片内边距**: 20dp
- **按钮高度**: 56dp (主要按钮), 48dp (对话框按钮)
- **按钮圆角**: 16dp (主要按钮), 12dp (对话框按钮)
- **标题字体**: 28sp (页面标题), 18sp (对话框标题)
- **正文字体**: 14sp-16sp
- **次要文字**: 12sp-13sp

#### 沉浸式状态栏
```java
// 在 onCreate() 中设置
WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

// 应用窗口插入
ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
    Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
    v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
    return WindowInsetsCompat.CONSUMED;
});
```


## 核心类设计

### 1. PromptManager (新增工具类)

**文件路径**: `app/src/main/java/com/example/budgetapp/util/PromptManager.java`

**职责**: 管理自定义提示词的存储、读取、验证

```java
package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AI系统提示词管理器
 * 负责自定义提示词的存储、读取和验证
 */
public class PromptManager {
    
    private static final String PREF_NAME = "ai_prompt_prefs";
    private static final String KEY_CUSTOM_PROMPT = "custom_system_prompt";
    private static final int MIN_PROMPT_LENGTH = 50;
    
    /**
     * 获取自定义提示词
     * @param context 上下文
     * @return 自定义提示词，如果不存在返回 null
     */
    public static String getCustomPrompt(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CUSTOM_PROMPT, null);
    }
    
    /**
     * 保存自定义提示词
     * @param context 上下文
     * @param prompt 提示词内容
     */
    public static void saveCustomPrompt(Context context, String prompt) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CUSTOM_PROMPT, prompt).apply();
    }
    
    /**
     * 清除自定义提示词（恢复默认）
     * @param context 上下文
     */
    public static void clearCustomPrompt(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_CUSTOM_PROMPT).apply();
    }
    
    /**
     * 检查是否存在自定义提示词
     * @param context 上下文
     * @return true 如果存在自定义提示词
     */
    public static boolean hasCustomPrompt(Context context) {
        return getCustomPrompt(context) != null;
    }
    
    /**
     * 验证提示词有效性
     * @param prompt 提示词内容
     * @return 验证结果
     */
    public static ValidationResult validatePrompt(String prompt) {
        // 检查是否为空
        if (prompt == null || prompt.trim().isEmpty()) {
            return ValidationResult.error("提示词不能为空");
        }
        
        // 检查是否仅包含空白字符
        if (prompt.trim().isEmpty()) {
            return ValidationResult.error("提示词不能为空");
        }
        
        // 检查长度
        if (prompt.length() < MIN_PROMPT_LENGTH) {
            return ValidationResult.warning("提示词过短，可能无法正常工作");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        public final String warningMessage;
        
        private ValidationResult(boolean isValid, String errorMessage, String warningMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.warningMessage = warningMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }
        
        public static ValidationResult warning(String message) {
            return new ValidationResult(true, null, message);
        }
    }
}
```

### 2. AiPromptEditorActivity (新增Activity)

**文件路径**: `app/src/main/java/com/example/budgetapp/ui/AiPromptEditorActivity.java`

**职责**: 提供提示词编辑界面

```java
package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.ai.AiAccountingClient;
import com.example.budgetapp.util.PromptManager;

/**
 * AI系统提示词编辑器
 */
public class AiPromptEditorActivity extends AppCompatActivity {
    
    private TextView tvStatusIndicator;
    private EditText etPromptContent;
    private TextView tvCharCount;
    private Button btnViewRules;
    private Button btnRestoreDefault;
    private Button btnSave;
    
    private boolean isCustomPrompt = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 沉浸式状态栏设置
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        
        setContentView(R.layout.activity_ai_prompt_editor);
        
        // 适配内边距
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            final int originalPaddingTop = rootLayout.getPaddingTop();
            final int originalPaddingBottom = rootLayout.getPaddingBottom();
            
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                    v.getPaddingLeft(),
                    originalPaddingTop + insets.top,
                    v.getPaddingRight(),
                    originalPaddingBottom + insets.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }
        
        initViews();
        loadPrompt();
    }
    
    private void initViews() {
        tvStatusIndicator = findViewById(R.id.tv_status_indicator);
        etPromptContent = findViewById(R.id.et_prompt_content);
        tvCharCount = findViewById(R.id.tv_char_count);
        btnViewRules = findViewById(R.id.btn_view_rules);
        btnRestoreDefault = findViewById(R.id.btn_restore_default);
        btnSave = findViewById(R.id.btn_save);
        
        // 字符数统计
        etPromptContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                updateCharCount();
            }
        });
        
        // 按钮点击事件
        btnViewRules.setOnClickListener(v -> showRulesDialog());
        btnRestoreDefault.setOnClickListener(v -> showRestoreDefaultDialog());
        btnSave.setOnClickListener(v -> savePrompt());
    }
    
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
    
    private void updateStatusIndicator(boolean isCustom) {
        if (isCustom) {
            tvStatusIndicator.setText("当前使用：自定义提示词");
            tvStatusIndicator.setTextColor(getResources().getColor(R.color.app_blue, null));
        } else {
            tvStatusIndicator.setText("当前使用：默认提示词");
            tvStatusIndicator.setTextColor(0xFF888888);
        }
    }
    
    private void updateCharCount() {
        int count = etPromptContent.getText().toString().length();
        tvCharCount.setText("字符数: " + count);
    }
    
    private void savePrompt() {
        String prompt = etPromptContent.getText().toString();
        
        // 验证提示词
        PromptManager.ValidationResult result = PromptManager.validatePrompt(prompt);
        
        if (!result.isValid) {
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示警告（如果有）
        if (result.warningMessage != null) {
            Toast.makeText(this, result.warningMessage, Toast.LENGTH_LONG).show();
        }
        
        // 保存提示词
        PromptManager.saveCustomPrompt(this, prompt);
        isCustomPrompt = true;
        updateStatusIndicator(true);
        
        Toast.makeText(this, "提示词已保存", Toast.LENGTH_SHORT).show();
    }
    
    private void showRestoreDefaultDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_restore_default, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
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
        
        dialog.show();
    }
    
    private void showRulesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_prompt_rules, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        TextView tvRulesContent = view.findViewById(R.id.tv_rules_content);
        Button btnClose = view.findViewById(R.id.btn_close);
        
        // 设置规则说明内容
        String rulesContent = buildRulesContent();
        tvRulesContent.setText(rulesContent);
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private String buildRulesContent() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("默认提示词包含以下主要规则类别：\n\n");
        
        sb.append("【JSON 输出规则】\n");
        sb.append("定义AI返回的数据格式，确保返回标准的JSON数组结构。\n\n");
        
        sb.append("【多账单识别规则】\n");
        sb.append("处理截图中包含多条交易记录的情况，确保每条记录都被正确识别。\n\n");
        
        sb.append("【金额识别规则】\n");
        sb.append("识别实际支付金额，区分原价、优惠价、折扣等不同类型的金额。\n\n");
        
        sb.append("【收支类型规则】\n");
        sb.append("判断交易是支出还是收入，处理退款、转账等特殊情况。\n\n");
        
        sb.append("【时间识别规则】\n");
        sb.append("从截图或文本中提取交易时间，处理各种时间格式。\n\n");
        
        sb.append("【分类规则】\n");
        sb.append("根据交易内容自动分配合适的分类和子分类。\n\n");
        
        sb.append("【备注规则】\n");
        sb.append("提取商户名称、商品名称等关键信息作为备注。\n\n");
        
        sb.append("【资产账户规则】\n");
        sb.append("识别支付方式并匹配到对应的资产账户。\n\n");
        
        sb.append("提示：您可以根据个人需求修改这些规则，或添加新的规则。");
        
        return sb.toString();
    }
}
```

### 3. AiSettingActivity 修改

**文件路径**: `app/src/main/java/com/example/budgetapp/ui/AiSettingActivity.java`

**修改内容**: 在 `initView()` 方法中添加提示词卡片点击事件

```java
// 在 initView() 方法中添加
findViewById(R.id.card_ai_prompt).setOnClickListener(v -> {
    Intent intent = new Intent(this, AiPromptEditorActivity.class);
    startActivity(intent);
});
```

### 4. AiAccountingClient 修改

**文件路径**: `app/src/main/java/com/example/budgetapp/ai/AiAccountingClient.java`

**修改方法**: `buildSystemPrompt(Context context)`

**修改前**:
```java
private String buildSystemPrompt(Context context) {
    StringBuilder builder = new StringBuilder();
    // ... 构建默认提示词
    return builder.toString();
}
```

**修改后**:
```java
private String buildSystemPrompt(Context context) {
    // 检查是否存在自定义提示词
    if (PromptManager.hasCustomPrompt(context)) {
        return PromptManager.getCustomPrompt(context);
    }
    
    // 如果没有自定义提示词，构建默认提示词
    StringBuilder builder = new StringBuilder();
    // ... 构建默认提示词（保持原有逻辑）
    return builder.toString();
}
```

**注意**: 需要在文件顶部添加导入语句：
```java
import com.example.budgetapp.util.PromptManager;
```


## 方法设计

### PromptManager 核心方法

#### 1. getCustomPrompt()

```java
/**
 * 获取自定义提示词
 * 
 * @param context 上下文对象
 * @return 自定义提示词字符串，如果不存在返回 null
 * 
 * 实现逻辑:
 * 1. 获取 SharedPreferences 实例 (ai_prompt_prefs, MODE_PRIVATE)
 * 2. 读取 custom_system_prompt 键的值
 * 3. 返回值（可能为 null）
 */
public static String getCustomPrompt(Context context)
```

#### 2. saveCustomPrompt()

```java
/**
 * 保存自定义提示词
 * 
 * @param context 上下文对象
 * @param prompt 要保存的提示词内容
 * 
 * 实现逻辑:
 * 1. 获取 SharedPreferences 实例
 * 2. 调用 edit() 获取编辑器
 * 3. 使用 putString() 保存提示词
 * 4. 调用 apply() 异步提交
 */
public static void saveCustomPrompt(Context context, String prompt)
```

#### 3. clearCustomPrompt()

```java
/**
 * 清除自定义提示词（恢复默认）
 * 
 * @param context 上下文对象
 * 
 * 实现逻辑:
 * 1. 获取 SharedPreferences 实例
 * 2. 调用 edit() 获取编辑器
 * 3. 使用 remove() 删除 custom_system_prompt 键
 * 4. 调用 apply() 异步提交
 */
public static void clearCustomPrompt(Context context)
```

#### 4. hasCustomPrompt()

```java
/**
 * 检查是否存在自定义提示词
 * 
 * @param context 上下文对象
 * @return true 如果存在自定义提示词，否则返回 false
 * 
 * 实现逻辑:
 * 1. 调用 getCustomPrompt(context)
 * 2. 检查返回值是否为 null
 * 3. 返回布尔结果
 */
public static boolean hasCustomPrompt(Context context)
```

#### 5. validatePrompt()

```java
/**
 * 验证提示词有效性
 * 
 * @param prompt 要验证的提示词内容
 * @return ValidationResult 验证结果对象
 * 
 * 实现逻辑:
 * 1. 检查 prompt 是否为 null 或空字符串
 *    - 如果是，返回 ValidationResult.error("提示词不能为空")
 * 2. 检查 prompt.trim() 是否为空（仅包含空白字符）
 *    - 如果是，返回 ValidationResult.error("提示词不能为空")
 * 3. 检查 prompt.length() 是否小于 MIN_PROMPT_LENGTH (50)
 *    - 如果是，返回 ValidationResult.warning("提示词过短，可能无法正常工作")
 * 4. 所有检查通过，返回 ValidationResult.success()
 */
public static ValidationResult validatePrompt(String prompt)
```

### AiPromptEditorActivity 核心方法

#### 1. loadPrompt()

```java
/**
 * 加载提示词内容
 * 
 * 实现逻辑:
 * 1. 调用 PromptManager.hasCustomPrompt(this)
 * 2. 如果存在自定义提示词:
 *    a. 调用 PromptManager.getCustomPrompt(this) 获取内容
 *    b. 设置 isCustomPrompt = true
 *    c. 调用 updateStatusIndicator(true)
 * 3. 如果不存在自定义提示词:
 *    a. 创建 AiAccountingClient 实例
 *    b. 调用 buildSystemPrompt(this) 获取默认提示词
 *    c. 设置 isCustomPrompt = false
 *    d. 调用 updateStatusIndicator(false)
 * 4. 将提示词内容设置到 etPromptContent
 * 5. 调用 updateCharCount() 更新字符数统计
 */
private void loadPrompt()
```

#### 2. savePrompt()

```java
/**
 * 保存提示词
 * 
 * 实现逻辑:
 * 1. 从 etPromptContent 获取文本内容
 * 2. 调用 PromptManager.validatePrompt(prompt) 验证
 * 3. 如果验证失败 (!result.isValid):
 *    a. 显示 Toast 提示错误消息
 *    b. 返回，不执行保存
 * 4. 如果有警告消息 (result.warningMessage != null):
 *    a. 显示 Toast 提示警告消息（允许继续保存）
 * 5. 调用 PromptManager.saveCustomPrompt(this, prompt)
 * 6. 设置 isCustomPrompt = true
 * 7. 调用 updateStatusIndicator(true)
 * 8. 显示 Toast 提示"提示词已保存"
 */
private void savePrompt()
```

#### 3. showRestoreDefaultDialog()

```java
/**
 * 显示恢复默认提示词确认对话框
 * 
 * 实现逻辑:
 * 1. 创建 AlertDialog.Builder
 * 2. 加载 dialog_restore_default.xml 布局
 * 3. 设置对话框背景透明
 * 4. 获取按钮引用 (btnCancel, btnConfirm)
 * 5. btnCancel 点击: 关闭对话框
 * 6. btnConfirm 点击:
 *    a. 调用 PromptManager.clearCustomPrompt(this)
 *    b. 创建 AiAccountingClient 实例
 *    c. 调用 buildSystemPrompt(this) 获取默认提示词
 *    d. 将默认提示词设置到 etPromptContent
 *    e. 设置 isCustomPrompt = false
 *    f. 调用 updateStatusIndicator(false)
 *    g. 调用 updateCharCount()
 *    h. 显示 Toast 提示"已恢复默认提示词"
 *    i. 关闭对话框
 * 7. 显示对话框
 */
private void showRestoreDefaultDialog()
```

#### 4. showRulesDialog()

```java
/**
 * 显示规则说明对话框
 * 
 * 实现逻辑:
 * 1. 创建 AlertDialog.Builder
 * 2. 加载 dialog_prompt_rules.xml 布局
 * 3. 设置对话框背景透明
 * 4. 获取 TextView (tvRulesContent) 和 Button (btnClose)
 * 5. 调用 buildRulesContent() 构建规则说明文本
 * 6. 将规则说明设置到 tvRulesContent
 * 7. btnClose 点击: 关闭对话框
 * 8. 显示对话框
 */
private void showRulesDialog()
```

#### 5. buildRulesContent()

```java
/**
 * 构建规则说明内容
 * 
 * @return 规则说明文本
 * 
 * 实现逻辑:
 * 1. 创建 StringBuilder
 * 2. 添加标题: "默认提示词包含以下主要规则类别："
 * 3. 依次添加各规则类别及其描述:
 *    - JSON 输出规则
 *    - 多账单识别规则
 *    - 金额识别规则
 *    - 收支类型规则
 *    - 时间识别规则
 *    - 分类规则
 *    - 备注规则
 *    - 资产账户规则
 * 4. 添加提示信息
 * 5. 返回构建的字符串
 */
private String buildRulesContent()
```

#### 6. updateStatusIndicator()

```java
/**
 * 更新状态指示器
 * 
 * @param isCustom 是否为自定义提示词
 * 
 * 实现逻辑:
 * 1. 如果 isCustom == true:
 *    a. 设置文本: "当前使用：自定义提示词"
 *    b. 设置颜色: @color/app_blue (蓝色)
 * 2. 如果 isCustom == false:
 *    a. 设置文本: "当前使用：默认提示词"
 *    b. 设置颜色: #888888 (灰色)
 */
private void updateStatusIndicator(boolean isCustom)
```

#### 7. updateCharCount()

```java
/**
 * 更新字符数统计
 * 
 * 实现逻辑:
 * 1. 从 etPromptContent 获取文本内容
 * 2. 调用 length() 获取字符数
 * 3. 格式化字符串: "字符数: {count}"
 * 4. 设置到 tvCharCount
 */
private void updateCharCount()
```

### AiAccountingClient 修改方法

#### buildSystemPrompt() 修改

```java
/**
 * 构建系统提示词
 * 
 * @param context 上下文对象
 * @return 系统提示词字符串
 * 
 * 修改后的实现逻辑:
 * 1. 调用 PromptManager.hasCustomPrompt(context)
 * 2. 如果存在自定义提示词:
 *    a. 调用 PromptManager.getCustomPrompt(context)
 *    b. 直接返回自定义提示词
 * 3. 如果不存在自定义提示词:
 *    a. 创建 StringBuilder
 *    b. 按原有逻辑构建默认提示词
 *    c. 返回构建的提示词
 * 
 * 注意: 自定义提示词完全替换默认提示词，不是追加
 */
private String buildSystemPrompt(Context context)
```


## 数据流设计

### 1. 提示词读取流程

```
┌─────────────────────────────────────────────────────────────┐
│  AI识别请求 (文本/截图/语音/对话)                           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  AiAccountingClient.recognizeText/Image/Audio()             │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  AiAccountingClient.buildSystemPrompt(Context)              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  PromptManager.hasCustomPrompt(Context)                     │
│  检查: SharedPreferences 中是否存在 custom_system_prompt    │
└─────────────────────────────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │                       │
                ▼                       ▼
        ┌───────────────┐       ┌───────────────┐
        │  存在自定义   │       │  不存在自定义 │
        └───────────────┘       └───────────────┘
                │                       │
                ▼                       ▼
┌─────────────────────────┐   ┌─────────────────────────┐
│ PromptManager.          │   │ 构建默认提示词          │
│ getCustomPrompt()       │   │ (原有逻辑)              │
│ 从 SharedPreferences    │   │ - 添加时间              │
│ 读取自定义提示词        │   │ - 添加规则              │
└─────────────────────────┘   │ - 添加分类列表          │
                │               │ - 添加资产列表          │
                │               └─────────────────────────┘
                │                       │
                └───────────┬───────────┘
                            ▼
                ┌─────────────────────────┐
                │  返回提示词字符串       │
                └─────────────────────────┘
                            │
                            ▼
                ┌─────────────────────────┐
                │  发送给 AI 模型         │
                └─────────────────────────┘
```

### 2. 提示词保存流程

```
┌─────────────────────────────────────────────────────────────┐
│  用户在 AiPromptEditorActivity 编辑提示词                   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  用户点击 [保存] 按钮                                       │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  AiPromptEditorActivity.savePrompt()                        │
│  1. 获取 EditText 中的文本内容                              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  PromptManager.validatePrompt(prompt)                       │
│  验证提示词有效性                                           │
└─────────────────────────────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │                       │
                ▼                       ▼
        ┌───────────────┐       ┌───────────────┐
        │  验证失败     │       │  验证通过     │
        └───────────────┘       └───────────────┘
                │                       │
                ▼                       ▼
┌─────────────────────────┐   ┌─────────────────────────┐
│ 显示错误 Toast          │   │ 检查是否有警告消息      │
│ 阻止保存                │   └─────────────────────────┘
└─────────────────────────┘               │
                                          ▼
                            ┌─────────────────────────┐
                            │ 如果有警告，显示 Toast  │
                            │ (不阻止保存)            │
                            └─────────────────────────┘
                                          │
                                          ▼
                            ┌─────────────────────────┐
                            │ PromptManager.          │
                            │ saveCustomPrompt()      │
                            └─────────────────────────┘
                                          │
                                          ▼
                            ┌─────────────────────────┐
                            │ SharedPreferences       │
                            │ .edit()                 │
                            │ .putString(             │
                            │   "custom_system_prompt"│
                            │   , prompt)             │
                            │ .apply()                │
                            └─────────────────────────┘
                                          │
                                          ▼
                            ┌─────────────────────────┐
                            │ 更新 UI 状态指示器      │
                            │ 显示成功 Toast          │
                            └─────────────────────────┘
```

### 3. 恢复默认流程

```
┌─────────────────────────────────────────────────────────────┐
│  用户点击 [恢复默认] 按钮                                   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  显示确认对话框                                             │
│  "确定要恢复默认提示词吗？当前的自定义内容将被清除"        │
└─────────────────────────────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                │                       │
                ▼                       ▼
        ┌───────────────┐       ┌───────────────┐
        │  用户取消     │       │  用户确认     │
        └───────────────┘       └───────────────┘
                │                       │
                ▼                       ▼
┌─────────────────────────┐   ┌─────────────────────────┐
│ 关闭对话框              │   │ PromptManager.          │
│ 不做任何操作            │   │ clearCustomPrompt()     │
└─────────────────────────┘   └─────────────────────────┘
                                          │
                                          ▼
                            ┌─────────────────────────┐
                            │ SharedPreferences       │
                            │ .edit()                 │
                            │ .remove(                │
                            │   "custom_system_prompt"│
                            │ )                       │
                            │ .apply()                │
                            └─────────────────────────┘
                                          │
                                          ▼
                            ┌─────────────────────────┐
                            │ AiAccountingClient      │
                            │ .buildSystemPrompt()    │
                            │ 获取默认提示词          │
                            └─────────────────────────┘
                                          │
                                          ▼
                            ┌─────────────────────────┐
                            │ 更新 EditText 内容      │
                            │ 更新状态指示器          │
                            │ 更新字符数统计          │
                            │ 显示成功 Toast          │
                            │ 关闭对话框              │
                            └─────────────────────────┘
```

### 4. 数据持久化机制

#### SharedPreferences 存储

```java
// 存储位置
/data/data/com.example.budgetapp/shared_prefs/ai_prompt_prefs.xml

// 文件内容格式
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="custom_system_prompt">你是一个中文记账助手...</string>
</map>

// 如果未设置自定义提示词，文件可能不存在或为空
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
</map>
```

#### 数据读写时机

**写入时机**:
- 用户在 AiPromptEditorActivity 点击 [保存] 按钮
- 验证通过后立即写入 SharedPreferences

**读取时机**:
- AiPromptEditorActivity.onCreate() 加载提示词时
- AiAccountingClient.buildSystemPrompt() 构建提示词时
- 所有 AI 识别场景（文本、截图、语音、对话）

**删除时机**:
- 用户在 AiPromptEditorActivity 点击 [恢复默认] 并确认

#### 数据一致性保证

1. **原子性**: 使用 SharedPreferences.apply() 确保异步写入
2. **即时生效**: 保存后立即更新 UI 状态
3. **全局生效**: 所有 AI 识别场景共享同一份提示词
4. **无缓存**: 每次调用 buildSystemPrompt() 都实时读取 SharedPreferences


## 错误处理设计

### 1. 输入验证错误

#### 错误场景 1: 提示词为空

**触发条件**: 用户保存时提示词内容为空或仅包含空白字符

**处理方式**:
```java
if (prompt == null || prompt.trim().isEmpty()) {
    Toast.makeText(this, "提示词不能为空", Toast.LENGTH_SHORT).show();
    return; // 阻止保存
}
```

**用户体验**: 显示 Toast 提示，不执行保存操作

#### 错误场景 2: 提示词过短

**触发条件**: 提示词长度小于 50 个字符

**处理方式**:
```java
if (prompt.length() < MIN_PROMPT_LENGTH) {
    Toast.makeText(this, "提示词过短，可能无法正常工作", Toast.LENGTH_LONG).show();
    // 显示警告但允许保存
}
```

**用户体验**: 显示警告 Toast，但仍允许用户保存

### 2. 存储错误

#### 错误场景 3: SharedPreferences 写入失败

**触发条件**: 磁盘空间不足或权限问题

**处理方式**:
```java
try {
    SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    prefs.edit().putString(KEY_CUSTOM_PROMPT, prompt).apply();
} catch (Exception e) {
    Log.e("PromptManager", "Failed to save custom prompt", e);
    // apply() 是异步的，通常不会抛出异常
    // 但如果需要确保写入成功，可以使用 commit()
}
```

**用户体验**: 使用 apply() 异步写入，通常不会失败

#### 错误场景 4: SharedPreferences 读取失败

**触发条件**: 文件损坏或权限问题

**处理方式**:
```java
try {
    SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    return prefs.getString(KEY_CUSTOM_PROMPT, null);
} catch (Exception e) {
    Log.e("PromptManager", "Failed to read custom prompt", e);
    return null; // 返回 null，使用默认提示词
}
```

**降级策略**: 如果读取失败，返回 null，系统将使用默认提示词

### 3. UI 错误

#### 错误场景 5: Activity 启动失败

**触发条件**: 布局文件缺失或资源错误

**处理方式**:
```java
try {
    Intent intent = new Intent(this, AiPromptEditorActivity.class);
    startActivity(intent);
} catch (Exception e) {
    Log.e("AiSettingActivity", "Failed to start AiPromptEditorActivity", e);
    Toast.makeText(this, "无法打开提示词编辑器", Toast.LENGTH_SHORT).show();
}
```

**用户体验**: 显示错误提示，不影响其他功能

#### 错误场景 6: 对话框显示失败

**触发条件**: 布局文件缺失或 Activity 已销毁

**处理方式**:
```java
try {
    AlertDialog dialog = builder.create();
    if (!isFinishing() && !isDestroyed()) {
        dialog.show();
    }
} catch (Exception e) {
    Log.e("AiPromptEditorActivity", "Failed to show dialog", e);
    Toast.makeText(this, "无法显示对话框", Toast.LENGTH_SHORT).show();
}
```

**用户体验**: 显示错误提示，不崩溃

### 4. 数据一致性错误

#### 错误场景 7: 自定义提示词损坏

**触发条件**: SharedPreferences 中的数据被外部修改或损坏

**处理方式**:
```java
public static String getCustomPrompt(Context context) {
    try {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String prompt = prefs.getString(KEY_CUSTOM_PROMPT, null);
        
        // 验证数据完整性
        if (prompt != null && prompt.trim().isEmpty()) {
            // 数据损坏，清除并返回 null
            clearCustomPrompt(context);
            return null;
        }
        
        return prompt;
    } catch (Exception e) {
        Log.e("PromptManager", "Failed to read custom prompt", e);
        return null;
    }
}
```

**降级策略**: 如果数据损坏，清除并使用默认提示词

### 5. AI 调用错误

#### 错误场景 8: 自定义提示词导致 AI 返回错误

**触发条件**: 用户自定义的提示词格式不正确，导致 AI 无法正确识别

**处理方式**:
- 在 AiAccountingClient 中保持原有的错误处理逻辑
- 如果 AI 返回错误，显示错误提示
- 不自动回退到默认提示词（用户需要手动修改或恢复默认）

**用户体验**: 
```java
// 在 AI 识别失败时
Toast.makeText(context, "AI识别失败，请检查提示词设置", Toast.LENGTH_LONG).show();
```

### 6. 边界情况处理

#### 边界情况 1: 提示词过长

**处理方式**: 不限制最大长度，但在 UI 中使用可滚动的 EditText

```xml
<EditText
    android:id="@+id/et_prompt_content"
    android:layout_height="400dp"
    android:scrollbars="vertical"
    android:maxLines="1000" />
```

#### 边界情况 2: 特殊字符

**处理方式**: 允许所有 Unicode 字符，不做特殊处理

```java
// SharedPreferences 自动处理 XML 转义
prefs.edit().putString(KEY_CUSTOM_PROMPT, prompt).apply();
```

#### 边界情况 3: 多语言支持

**处理方式**: 支持任意语言的提示词

```java
// 使用 UTF-8 编码，支持所有语言
// SharedPreferences 默认使用 UTF-8
```

### 7. 错误日志

#### 日志级别

```java
// 错误日志
Log.e("PromptManager", "Failed to save custom prompt", e);

// 警告日志
Log.w("PromptManager", "Prompt is too short: " + prompt.length());

// 信息日志
Log.i("PromptManager", "Custom prompt saved successfully");

// 调试日志
Log.d("PromptManager", "Loading custom prompt");
```

#### 日志内容

- **操作类型**: 保存、读取、删除、验证
- **操作结果**: 成功、失败、警告
- **错误信息**: 异常堆栈、错误原因
- **上下文信息**: 提示词长度、是否存在自定义提示词

### 8. 降级策略总结

| 错误场景 | 降级策略 | 用户影响 |
|---------|---------|---------|
| 提示词为空 | 阻止保存，显示错误提示 | 无法保存空提示词 |
| 提示词过短 | 显示警告，允许保存 | 可能影响 AI 识别效果 |
| 存储写入失败 | 使用 apply() 异步写入 | 通常不会失败 |
| 存储读取失败 | 返回 null，使用默认提示词 | 自动回退到默认提示词 |
| Activity 启动失败 | 显示错误提示 | 无法打开编辑器 |
| 对话框显示失败 | 显示错误提示 | 无法显示对话框 |
| 自定义提示词损坏 | 清除并使用默认提示词 | 自动回退到默认提示词 |
| AI 识别失败 | 显示错误提示 | 需要用户手动修改提示词 |


## 测试策略

### 1. 单元测试

#### PromptManager 测试

```java
@Test
public void testGetCustomPrompt_whenNotExists_returnsNull() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PromptManager.clearCustomPrompt(context);
    
    String result = PromptManager.getCustomPrompt(context);
    
    assertNull(result);
}

@Test
public void testSaveAndGetCustomPrompt_success() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String testPrompt = "这是一个测试提示词";
    
    PromptManager.saveCustomPrompt(context, testPrompt);
    String result = PromptManager.getCustomPrompt(context);
    
    assertEquals(testPrompt, result);
}

@Test
public void testHasCustomPrompt_whenExists_returnsTrue() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PromptManager.saveCustomPrompt(context, "测试");
    
    boolean result = PromptManager.hasCustomPrompt(context);
    
    assertTrue(result);
}

@Test
public void testClearCustomPrompt_success() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PromptManager.saveCustomPrompt(context, "测试");
    
    PromptManager.clearCustomPrompt(context);
    
    assertFalse(PromptManager.hasCustomPrompt(context));
}

@Test
public void testValidatePrompt_whenEmpty_returnsError() {
    PromptManager.ValidationResult result = PromptManager.validatePrompt("");
    
    assertFalse(result.isValid);
    assertEquals("提示词不能为空", result.errorMessage);
}

@Test
public void testValidatePrompt_whenTooShort_returnsWarning() {
    PromptManager.ValidationResult result = PromptManager.validatePrompt("短");
    
    assertTrue(result.isValid);
    assertEquals("提示词过短，可能无法正常工作", result.warningMessage);
}

@Test
public void testValidatePrompt_whenValid_returnsSuccess() {
    String validPrompt = "这是一个足够长的有效提示词，包含了足够的内容来通过验证测试";
    PromptManager.ValidationResult result = PromptManager.validatePrompt(validPrompt);
    
    assertTrue(result.isValid);
    assertNull(result.errorMessage);
    assertNull(result.warningMessage);
}
```

#### AiAccountingClient 测试

```java
@Test
public void testBuildSystemPrompt_whenNoCustomPrompt_returnsDefault() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PromptManager.clearCustomPrompt(context);
    AiAccountingClient client = new AiAccountingClient();
    
    String result = client.buildSystemPrompt(context);
    
    assertNotNull(result);
    assertTrue(result.contains("你是一个中文记账助手"));
}

@Test
public void testBuildSystemPrompt_whenHasCustomPrompt_returnsCustom() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String customPrompt = "这是自定义提示词";
    PromptManager.saveCustomPrompt(context, customPrompt);
    AiAccountingClient client = new AiAccountingClient();
    
    String result = client.buildSystemPrompt(context);
    
    assertEquals(customPrompt, result);
}
```

### 2. UI 测试

#### AiPromptEditorActivity 测试

```java
@Test
public void testLoadPrompt_whenNoCustomPrompt_showsDefault() {
    // 清除自定义提示词
    PromptManager.clearCustomPrompt(InstrumentationRegistry.getInstrumentation().getTargetContext());
    
    // 启动 Activity
    ActivityScenario<AiPromptEditorActivity> scenario = 
        ActivityScenario.launch(AiPromptEditorActivity.class);
    
    // 验证状态指示器显示"默认提示词"
    onView(withId(R.id.tv_status_indicator))
        .check(matches(withText(containsString("默认提示词"))));
    
    // 验证 EditText 包含默认提示词内容
    onView(withId(R.id.et_prompt_content))
        .check(matches(withText(containsString("你是一个中文记账助手"))));
}

@Test
public void testSavePrompt_whenValid_success() {
    ActivityScenario<AiPromptEditorActivity> scenario = 
        ActivityScenario.launch(AiPromptEditorActivity.class);
    
    // 输入自定义提示词
    String customPrompt = "这是一个测试用的自定义提示词，长度足够通过验证";
    onView(withId(R.id.et_prompt_content))
        .perform(clearText(), typeText(customPrompt));
    
    // 点击保存按钮
    onView(withId(R.id.btn_save))
        .perform(click());
    
    // 验证 Toast 提示
    onView(withText("提示词已保存"))
        .inRoot(withDecorView(not(is(scenario.getActivity().getWindow().getDecorView()))))
        .check(matches(isDisplayed()));
    
    // 验证状态指示器更新为"自定义提示词"
    onView(withId(R.id.tv_status_indicator))
        .check(matches(withText(containsString("自定义提示词"))));
}

@Test
public void testSavePrompt_whenEmpty_showsError() {
    ActivityScenario<AiPromptEditorActivity> scenario = 
        ActivityScenario.launch(AiPromptEditorActivity.class);
    
    // 清空 EditText
    onView(withId(R.id.et_prompt_content))
        .perform(clearText());
    
    // 点击保存按钮
    onView(withId(R.id.btn_save))
        .perform(click());
    
    // 验证错误 Toast
    onView(withText("提示词不能为空"))
        .inRoot(withDecorView(not(is(scenario.getActivity().getWindow().getDecorView()))))
        .check(matches(isDisplayed()));
}

@Test
public void testRestoreDefault_success() {
    // 先保存自定义提示词
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PromptManager.saveCustomPrompt(context, "自定义提示词");
    
    ActivityScenario<AiPromptEditorActivity> scenario = 
        ActivityScenario.launch(AiPromptEditorActivity.class);
    
    // 点击恢复默认按钮
    onView(withId(R.id.btn_restore_default))
        .perform(click());
    
    // 在确认对话框中点击确认
    onView(withId(R.id.btn_confirm))
        .perform(click());
    
    // 验证成功 Toast
    onView(withText("已恢复默认提示词"))
        .inRoot(withDecorView(not(is(scenario.getActivity().getWindow().getDecorView()))))
        .check(matches(isDisplayed()));
    
    // 验证状态指示器更新为"默认提示词"
    onView(withId(R.id.tv_status_indicator))
        .check(matches(withText(containsString("默认提示词"))));
}

@Test
public void testCharCountUpdate_realtime() {
    ActivityScenario<AiPromptEditorActivity> scenario = 
        ActivityScenario.launch(AiPromptEditorActivity.class);
    
    // 输入文本
    String text = "测试文本";
    onView(withId(R.id.et_prompt_content))
        .perform(clearText(), typeText(text));
    
    // 验证字符数统计更新
    onView(withId(R.id.tv_char_count))
        .check(matches(withText("字符数: " + text.length())));
}
```

### 3. 集成测试

#### 端到端测试场景

```java
@Test
public void testEndToEnd_customPromptUsedInAiRecognition() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    
    // 1. 保存自定义提示词
    String customPrompt = "自定义AI识别规则";
    PromptManager.saveCustomPrompt(context, customPrompt);
    
    // 2. 创建 AiAccountingClient
    AiAccountingClient client = new AiAccountingClient();
    
    // 3. 调用 buildSystemPrompt
    String result = client.buildSystemPrompt(context);
    
    // 4. 验证返回的是自定义提示词
    assertEquals(customPrompt, result);
    
    // 5. 清除自定义提示词
    PromptManager.clearCustomPrompt(context);
    
    // 6. 再次调用 buildSystemPrompt
    String defaultResult = client.buildSystemPrompt(context);
    
    // 7. 验证返回的是默认提示词
    assertNotEquals(customPrompt, defaultResult);
    assertTrue(defaultResult.contains("你是一个中文记账助手"));
}
```

### 4. 手动测试清单

#### 功能测试

- [ ] 从 AiSettingActivity 点击"AI识别提示词"卡片，能正常跳转到编辑器
- [ ] 编辑器首次打开时显示默认提示词
- [ ] 编辑器状态指示器正确显示"默认提示词"或"自定义提示词"
- [ ] 编辑提示词时字符数统计实时更新
- [ ] 保存空提示词时显示错误提示并阻止保存
- [ ] 保存过短提示词时显示警告但允许保存
- [ ] 保存有效提示词后显示成功提示
- [ ] 保存后状态指示器更新为"自定义提示词"
- [ ] 点击"恢复默认"显示确认对话框
- [ ] 确认恢复默认后提示词内容恢复为默认
- [ ] 恢复默认后状态指示器更新为"默认提示词"
- [ ] 点击"查看规则说明"显示规则说明对话框
- [ ] 规则说明对话框内容完整且可滚动

#### AI 识别测试

- [ ] 使用默认提示词进行文本识别，功能正常
- [ ] 保存自定义提示词后进行文本识别，使用自定义提示词
- [ ] 使用默认提示词进行截图识别，功能正常
- [ ] 保存自定义提示词后进行截图识别，使用自定义提示词
- [ ] 使用默认提示词进行语音识别，功能正常
- [ ] 保存自定义提示词后进行语音识别，使用自定义提示词
- [ ] 使用默认提示词进行 AI 对话，功能正常
- [ ] 保存自定义提示词后进行 AI 对话，使用自定义提示词

#### UI/UX 测试

- [ ] 沉浸式状态栏正常显示
- [ ] 卡片圆角、阴影、边距符合设计规范
- [ ] 按钮样式与应用其他页面一致
- [ ] 文本颜色、字体大小符合设计规范
- [ ] EditText 可正常滚动查看长文本
- [ ] 对话框背景透明，圆角正常
- [ ] 状态指示器颜色正确（默认灰色，自定义蓝色）
- [ ] Toast 提示信息清晰易懂

#### 边界测试

- [ ] 输入超长提示词（10000+ 字符）能正常保存和显示
- [ ] 输入包含特殊字符的提示词能正常保存和显示
- [ ] 输入包含 Emoji 的提示词能正常保存和显示
- [ ] 输入包含换行符的提示词能正常保存和显示
- [ ] 快速连续点击保存按钮不会导致重复保存
- [ ] 在编辑过程中旋转屏幕，内容不丢失

#### 兼容性测试

- [ ] Android 8.0 (API 26) 正常运行
- [ ] Android 9.0 (API 28) 正常运行
- [ ] Android 10 (API 29) 正常运行
- [ ] Android 11 (API 30) 正常运行
- [ ] Android 12 (API 31) 正常运行
- [ ] Android 13 (API 33) 正常运行
- [ ] Android 14 (API 34) 正常运行

### 5. 性能测试

#### 响应时间

- 打开编辑器页面: < 500ms
- 加载提示词内容: < 100ms
- 保存提示词: < 50ms
- 恢复默认提示词: < 100ms
- 显示对话框: < 200ms

#### 内存占用

- 编辑器页面内存占用: < 50MB
- 长文本编辑内存占用: < 100MB

### 6. 回归测试

在完成功能开发后，需要验证以下现有功能未受影响：

- [ ] AI 文本识别功能正常
- [ ] AI 截图识别功能正常
- [ ] AI 语音识别功能正常
- [ ] AI 对话功能正常
- [ ] AI 分类关键字规则功能正常
- [ ] AI 设置页面其他功能正常
- [ ] 应用其他模块功能正常

## 实现优先级

### P0 (必须实现)

1. PromptManager 工具类
2. AiPromptEditorActivity 基本功能
3. AiAccountingClient.buildSystemPrompt() 修改
4. AiSettingActivity 添加入口卡片
5. 基本 UI 布局和样式

### P1 (重要功能)

1. 提示词验证逻辑
2. 恢复默认功能
3. 状态指示器
4. 字符数统计
5. 错误处理和降级策略

### P2 (增强功能)

1. 规则说明对话框
2. 详细的错误提示
3. UI 动画和过渡效果
4. 完善的日志记录

## 实现注意事项

1. **代码位置**: 所有新增代码必须在 `app/src/` 目录下，不是 `app/app/src/`
2. **命名规范**: 遵循 Java 命名规范，类名使用 PascalCase，方法名使用 camelCase
3. **注释规范**: 所有公共方法必须添加 JavaDoc 注释
4. **资源命名**: 布局文件使用 `activity_` 或 `dialog_` 前缀，ID 使用下划线命名法
5. **颜色使用**: 使用 `colors.xml` 中定义的颜色，不要硬编码颜色值
6. **字符串资源**: 所有用户可见的文本应定义在 `strings.xml` 中（本功能可以硬编码中文）
7. **线程安全**: SharedPreferences 操作使用 `apply()` 而不是 `commit()`
8. **内存泄漏**: 注意 Context 的使用，避免内存泄漏
9. **向后兼容**: 确保代码在 Android 8.0+ 上正常运行
10. **测试覆盖**: 核心逻辑必须有单元测试覆盖

## 总结

本设计文档详细描述了 AI 系统提示词自定义功能的技术实现方案，包括：

- **系统架构**: 清晰的组件划分和交互流程
- **数据模型**: SharedPreferences 存储结构和验证机制
- **UI 设计**: 完整的布局文件和设计规范
- **核心类设计**: PromptManager、AiPromptEditorActivity 的详细实现
- **方法设计**: 所有核心方法的签名和实现逻辑
- **数据流设计**: 提示词读取、保存、恢复的完整流程
- **错误处理**: 全面的错误场景和降级策略
- **测试策略**: 单元测试、UI 测试、集成测试和手动测试清单

该设计遵循 Android 开发最佳实践，与现有代码风格保持一致，确保功能的可靠性和可维护性。

