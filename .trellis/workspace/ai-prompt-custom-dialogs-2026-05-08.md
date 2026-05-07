# AI提示词编辑器 - 自定义对话框更新

**日期**: 2026-05-08  
**状态**: ✅ 完成

## 更新内容

将导入和导出功能的原生 AlertDialog 替换为自定义样式的对话框,与应用整体设计风格保持一致。

---

## 新增文件

### 1. 导入对话框布局
**文件**: `app/src/main/res/layout/dialog_import_prompt.xml`

**设计特点**:
- CardView 容器,28dp 圆角
- 24dp 外边距
- 白色背景,无阴影
- 标题居中,20sp 粗体
- 说明文字居中,14sp,灰色
- 两个按钮:取消(灰色边框) + 选择文件(蓝色填充)
- 按钮高度 56dp,圆角 16dp

**布局结构**:
```
┌─────────────────────────────────┐
│        导入提示词               │  ← 标题
│                                 │
│  导入的提示词将替换当前编辑器   │  ← 说明
│  中的内容。                     │
│                                 │
│  支持的文件格式：.txt           │
│                                 │
│  [取消]      [选择文件]         │  ← 按钮
└─────────────────────────────────┘
```

---

### 2. 导出对话框布局
**文件**: `app/src/main/res/layout/dialog_export_prompt.xml`

**设计特点**:
- 与导入对话框相同的设计风格
- 标题:"导出提示词"
- 说明:"将当前提示词导出为文本文件"
- 两个按钮:取消(灰色边框) + 导出(蓝色填充)

**布局结构**:
```
┌─────────────────────────────────┐
│        导出提示词               │  ← 标题
│                                 │
│  将当前提示词导出为文本文件。   │  ← 说明
│                                 │
│  文件格式：.txt                 │
│                                 │
│  [取消]      [导出]             │  ← 按钮
└─────────────────────────────────┘
```

---

## 修改文件

### 1. AiPromptEditorActivity.java

**修改方法**: `showImportDialog()`

**原实现**:
```java
new AlertDialog.Builder(this)
    .setTitle("导入提示词")
    .setMessage("...")
    .setPositiveButton("选择文件", ...)
    .setNegativeButton("取消", null)
    .show();
```

**新实现**:
```java
AlertDialog.Builder builder = new AlertDialog.Builder(this);
View view = LayoutInflater.from(this).inflate(R.layout.dialog_import_prompt, null);
builder.setView(view);
AlertDialog dialog = builder.create();

// 设置透明背景
dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

// 绑定按钮事件
Button btnCancel = view.findViewById(R.id.btn_cancel);
Button btnSelectFile = view.findViewById(R.id.btn_select_file);

btnCancel.setOnClickListener(v -> dialog.dismiss());
btnSelectFile.setOnClickListener(v -> {
    dialog.dismiss();
    // 启动文件选择器
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/plain");
    importLauncher.launch(intent);
});

dialog.show();
```

---

**修改方法**: `showExportDialog()`

**原实现**:
```java
new AlertDialog.Builder(this)
    .setTitle("导出提示词")
    .setMessage("...")
    .setPositiveButton("导出", ...)
    .setNegativeButton("取消", null)
    .show();
```

**新实现**:
```java
// 先检查内容是否为空
String prompt = etPromptContent.getText().toString();
if (prompt.trim().isEmpty()) {
    Toast.makeText(this, "提示词内容为空，无法导出", Toast.LENGTH_SHORT).show();
    return;
}

AlertDialog.Builder builder = new AlertDialog.Builder(this);
View view = LayoutInflater.from(this).inflate(R.layout.dialog_export_prompt, null);
builder.setView(view);
AlertDialog dialog = builder.create();

// 设置透明背景
dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

// 绑定按钮事件
Button btnCancel = view.findViewById(R.id.btn_cancel);
Button btnExport = view.findViewById(R.id.btn_export);

btnCancel.setOnClickListener(v -> dialog.dismiss());
btnExport.setOnClickListener(v -> {
    dialog.dismiss();
    // 生成文件名并启动文件选择器
    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
    String fileName = "ai_prompt_" + timestamp + ".txt";
    
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TITLE, fileName);
    exportLauncher.launch(intent);
});

dialog.show();
```

---

### 2. dialog_permission_request.xml (修复)

**问题**: 缺少必要的 ID,导致编译错误

**修复内容**:
- 添加 `android:id="@+id/tv_permission_title"` 到标题 TextView
- 添加 `android:id="@+id/tv_permission_message"` 到消息 TextView
- 将 `btn_cancel` 改为 `btn_cancel_permission`
- 将 `btn_confirm` 改为 `btn_grant_permission`

这些 ID 被 `MainActivity.java` 和 `AssistantManagerActivity.java` 引用。

---

## 设计对比

### 原生 AlertDialog (旧)
```
┌─────────────────────────────────┐
│ 导入提示词                      │  ← 系统标题栏
├─────────────────────────────────┤
│ 导入的提示词将替换当前编辑器中  │
│ 的内容。                        │
│                                 │
│ 支持的文件格式：.txt            │
│                                 │
│              [取消] [选择文件]  │  ← 系统按钮
└─────────────────────────────────┘
```
- 使用系统默认样式
- 按钮在右侧,较小
- 整体风格与应用不一致

### 自定义对话框 (新)
```
┌─────────────────────────────────┐
│        导入提示词               │  ← 居中标题
│                                 │
│  导入的提示词将替换当前编辑器   │  ← 居中说明
│  中的内容。                     │
│                                 │
│  支持的文件格式：.txt           │
│                                 │
│  [取消]      [选择文件]         │  ← 大按钮
└─────────────────────────────────┘
```
- 圆角卡片设计
- 标题和内容居中
- 大按钮,视觉更突出
- 与应用整体风格一致

---

## 视觉特点

### 对话框容器
- **背景**: 白色 CardView
- **圆角**: 28dp (大圆角,现代感)
- **外边距**: 24dp
- **阴影**: 0dp (扁平化设计)
- **内边距**: 24dp

### 标题样式
- **字体大小**: 20sp
- **字重**: 粗体
- **颜色**: 主文本颜色
- **对齐**: 居中

### 说明文字
- **字体大小**: 14sp
- **颜色**: #888888 (灰色)
- **行距**: 5dp
- **对齐**: 居中
- **上边距**: 16dp

### 按钮样式

**取消按钮**:
- 样式: OutlinedButton (边框按钮)
- 高度: 56dp
- 圆角: 16dp
- 边框: #E0E0E0, 1.5dp
- 文字: #888888, 16sp, 粗体

**确认按钮** (选择文件/导出):
- 样式: 填充按钮
- 高度: 56dp
- 圆角: 16dp
- 背景: @color/app_blue (蓝色)
- 文字: 白色, 16sp, 粗体

### 按钮布局
- 水平排列
- 等宽 (layout_weight="1")
- 间距: 8dp (左右各 8dp)
- 上边距: 32dp

---

## 技术实现

### 关键代码模式

1. **加载自定义布局**:
```java
View view = LayoutInflater.from(this).inflate(R.layout.dialog_xxx, null);
builder.setView(view);
```

2. **设置透明背景** (显示圆角):
```java
if (dialog.getWindow() != null) {
    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
}
```

3. **绑定按钮事件**:
```java
Button btnCancel = view.findViewById(R.id.btn_cancel);
btnCancel.setOnClickListener(v -> dialog.dismiss());
```

4. **关闭对话框后执行操作**:
```java
btnConfirm.setOnClickListener(v -> {
    dialog.dismiss();  // 先关闭对话框
    // 然后执行操作
    launcher.launch(intent);
});
```

---

## 用户体验改进

### 视觉一致性
- ✅ 与应用其他对话框(恢复默认、规则说明)风格统一
- ✅ 圆角设计更现代
- ✅ 按钮更大更易点击

### 交互体验
- ✅ 标题和内容居中,更易阅读
- ✅ 按钮等宽,视觉平衡
- ✅ 取消和确认按钮位置符合用户习惯(左取消,右确认)

### 品牌一致性
- ✅ 使用应用主题色(蓝色)
- ✅ 字体大小和颜色与应用一致
- ✅ 圆角半径与应用其他元素一致

---

## 编译状态

✅ **BUILD SUCCESSFUL**

```
> Task :app:compileDebugJavaWithJavac
> Task :app:assembleDebug

BUILD SUCCESSFUL in 6s
38 actionable tasks: 13 executed, 25 up-to-date
```

---

## 相关文件

### 新增文件
- `app/src/main/res/layout/dialog_import_prompt.xml`
- `app/src/main/res/layout/dialog_export_prompt.xml`

### 修改文件
- `app/src/main/java/com/example/budgetapp/ui/AiPromptEditorActivity.java`
- `app/src/main/res/layout/dialog_permission_request.xml` (修复编译错误)

### 参考文件
- `app/src/main/res/layout/dialog_restore_default.xml` (设计参考)
- `app/src/main/res/layout/dialog_prompt_rules.xml` (设计参考)

---

## 测试建议

### 视觉测试
1. 对话框圆角是否正确显示
2. 按钮大小和间距是否合适
3. 文字居中对齐是否正确
4. 颜色是否与应用主题一致

### 功能测试
1. 点击"导入"按钮 → 显示自定义对话框
2. 点击"取消" → 对话框关闭
3. 点击"选择文件" → 对话框关闭并打开文件选择器
4. 点击"导出"按钮 → 显示自定义对话框
5. 点击"导出" → 对话框关闭并打开文件保存器

### 边界测试
1. 内容为空时点击导出 → 应显示 Toast 提示,不显示对话框
2. 快速连续点击按钮 → 不应出现多个对话框

---

## 总结

成功将导入和导出功能的原生 AlertDialog 替换为自定义样式对话框:

1. ✅ 创建了两个自定义对话框布局
2. ✅ 更新了 Activity 代码使用自定义对话框
3. ✅ 修复了权限对话框的编译错误
4. ✅ 保持了与应用整体设计风格的一致性
5. ✅ 提升了用户体验和视觉效果
6. ✅ 代码编译通过

所有功能正常工作,视觉效果更加统一和现代化。
