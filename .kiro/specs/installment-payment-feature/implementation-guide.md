# AssetsFragment.java 修改指南

## 文件位置
`app/src/main/java/com/example/budgetapp/ui/AssetsFragment.java`

## 类型映射关系

### 数据库 type 值
- `0` = 资产
- `1` = 负债
- `2` = 借出
- `3` = 理财
- `4` = 分期（新增）

### Spinner position 值
- `0` = 资产
- `1` = 理财
- `2` = 负债
- `3` = 借出
- `4` = 分期（新增）

### 转换公式
```java
// type -> spinner position
if (type == 0) position = 0;      // 资产
else if (type == 1) position = 2; // 负债
else if (type == 2) position = 3; // 借出
else if (type == 3) position = 1; // 理财
else if (type == 4) position = 4; // 分期

// spinner position -> type
if (position == 0) type = 0;      // 资产
else if (position == 1) type = 3; // 理财
else if (position == 2) type = 1; // 负债
else if (position == 3) type = 2; // 借出
else if (position == 4) type = 4; // 分期
```

---

## 修改步骤

### 步骤 1：添加分期输入表单的监听器（约第 620 行之后）

在 `spinnerInterestType.setOnItemSelectedListener(spinnerListener);` 之后添加：

```java
// 【新增】分期金额自动计算
android.text.TextWatcher installmentWatcher = new android.text.TextWatcher() {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}
    @Override
    public void afterTextChanged(android.text.Editable s) {
        try {
            int periods = etTotalInstallments.getText().toString().isEmpty() ? 0 
                : Integer.parseInt(etTotalInstallments.getText().toString());
            double amount = etInstallmentAmount.getText().toString().isEmpty() ? 0.0 
                : Double.parseDouble(etInstallmentAmount.getText().toString());
            double total = periods * amount;
            tvTotalAmountDisplay.setText(String.format("总金额：¥%.2f", total));
        } catch (Exception e) {
            tvTotalAmountDisplay.setText("总金额：¥0.00");
        }
    }
};
etTotalInstallments.addTextChangedListener(installmentWatcher);
etInstallmentAmount.addTextChangedListener(installmentWatcher);
```

### 步骤 2：修改 updateLabels（约第 705-745 行）

**查找这段代码：**
```java
// 切换资产类型时的 UI 更新
Runnable updateLabels = () -> {
    int selectedId = rgType.getCheckedRadioButtonId();
    String titleSuffix = "";
    String nameHint = "";
    String amountHint = "";
    layoutInvestment.setVisibility(View.GONE);

    if (selectedId == R.id.rb_asset) {
        titleSuffix = "资产";
        nameHint = "资产名称";
        amountHint = "资产金额";
    } else if (selectedId == R.id.rb_liability) {
        titleSuffix = "负债";
        nameHint = "负债对象";
        amountHint = "负债金额";
    } else if (selectedId == R.id.rb_lent) {
        titleSuffix = "借出";
        nameHint = "借款对象";
        amountHint = "借出金额";
    } else if (selectedId == R.id.rb_investment) {
        titleSuffix = "理财";
        nameHint = "理财产品或银行名称";
        amountHint = "理财本金";
        layoutInvestment.setVisibility(View.VISIBLE);
        calculateExpected.run();
    }

    if (existing == null) {
        if (selectedId == R.id.rb_asset) {
            spinnerInclude.setSelection(0);
        } else {
            spinnerInclude.setSelection(1);
        }
    }

    tvTitle.setText((existing == null ? "添加" : "修改") + titleSuffix);
    etName.setHint(nameHint);
    etAmount.setHint(amountHint);
};
```

**替换为：**
```java
// 【修改】切换资产类型时的 UI 更新（改为 Spinner 逻辑）
Runnable updateLabels = () -> {
    int selectedPosition = spinnerType.getSelectedItemPosition();
    // 0:资产, 1:理财, 2:负债, 3:借出, 4:分期
    String titleSuffix = "";
    String nameHint = "";
    String amountHint = "";
    layoutInvestment.setVisibility(View.GONE);
    layoutInstallment.setVisibility(View.GONE);
    etAmount.setVisibility(View.VISIBLE);

    if (selectedPosition == 0) { // 资产
        titleSuffix = "资产";
        nameHint = "资产名称";
        amountHint = "资产金额";
    } else if (selectedPosition == 1) { // 理财
        titleSuffix = "理财";
        nameHint = "理财产品或银行名称";
        amountHint = "理财本金";
        layoutInvestment.setVisibility(View.VISIBLE);
        calculateExpected.run();
    } else if (selectedPosition == 2) { // 负债
        titleSuffix = "负债";
        nameHint = "负债对象";
        amountHint = "负债金额";
    } else if (selectedPosition == 3) { // 借出
        titleSuffix = "借出";
        nameHint = "借款对象";
        amountHint = "借出金额";
    } else if (selectedPosition == 4) { // 分期
        titleSuffix = "分期";
        nameHint = "分期对象";
        amountHint = "";
        layoutInstallment.setVisibility(View.VISIBLE);
        etAmount.setVisibility(View.GONE); // 隐藏普通金额输入
    }

    if (existing == null) {
        if (selectedPosition == 0) { // 资产
            spinnerInclude.setSelection(0);
        } else {
            spinnerInclude.setSelection(1);
        }
    }

    tvTitle.setText((existing == null ? "添加" : "修改") + titleSuffix);
    etName.setHint(nameHint);
    etAmount.setHint(amountHint);
};
```

### 步骤 3：修改类型选择监听器（约第 747 行）

**查找这段代码：**
```java
rgType.setOnCheckedChangeListener((group, checkedId) -> updateLabels.run());
```

**替换为：**
```java
// 【修改】Spinner 选择监听
spinnerType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
    @Override
    public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        updateLabels.run();
    }
    @Override
    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
});
```

### 步骤 4：修改初始类型设置（约第 749-752 行）

**查找这段代码：**
```java
int targetType = (existing != null) ? existing.type : initType;
if (targetType == 1) rgType.check(R.id.rb_liability);
else if (targetType == 2) rgType.check(R.id.rb_lent);
else if (targetType == 3) rgType.check(R.id.rb_investment);
else rgType.check(R.id.rb_asset);
```

**替换为：**
```java
// 【修改】设置初始选中项（type -> spinner position）
int targetType = (existing != null) ? existing.type : initType;
int spinnerPosition = 0;
if (targetType == 0) spinnerPosition = 0;      // 资产
else if (targetType == 1) spinnerPosition = 2; // 负债
else if (targetType == 2) spinnerPosition = 3; // 借出
else if (targetType == 3) spinnerPosition = 1; // 理财
else if (targetType == 4) spinnerPosition = 4; // 分期
spinnerType.setSelection(spinnerPosition);
```

### 步骤 5：回显分期数据（约第 620 行，在理财数据回显之后）

在理财数据回显代码之后添加：

```java
// 【新增】回显分期数据
if (existing != null && existing.type == 4) {
    etTotalInstallments.setText(String.valueOf(existing.totalInstallments));
    etInstallmentAmount.setText(String.format("%.2f", existing.installmentAmount));
}
```

### 步骤 6：修改保存按钮逻辑（约第 787-790 行）

**查找这段代码：**
```java
int finalType = 0;
int selectedId = rgType.getCheckedRadioButtonId();
if (selectedId == R.id.rb_liability) finalType = 1;
else if (selectedId == R.id.rb_lent) finalType = 2;
else if (selectedId == R.id.rb_investment) finalType = 3;
```

**替换为：**
```java
// 【修改】获取选中的类型（spinner position -> type）
int selectedPosition = spinnerType.getSelectedItemPosition();
int finalType = 0;
if (selectedPosition == 0) finalType = 0;      // 资产
else if (selectedPosition == 1) finalType = 3; // 理财
else if (selectedPosition == 2) finalType = 1; // 负债
else if (selectedPosition == 3) finalType = 2; // 借出
else if (selectedPosition == 4) finalType = 4; // 分期
```

### 步骤 7：添加分期数据保存逻辑（约第 850 行，在理财数据保存之后）

在理财数据保存代码之后添加：

```java
// 【新增】保存分期数据
if (finalType == 4) {
    try {
        accountToSave.totalInstallments = Integer.parseInt(etTotalInstallments.getText().toString());
        accountToSave.installmentAmount = Double.parseDouble(etInstallmentAmount.getText().toString());
        accountToSave.amount = accountToSave.getTotalAmount(); // 总金额
        accountToSave.paidInstallments = "[]"; // 初始为空
    } catch (Exception e) {
        Toast.makeText(getContext(), "请输入有效的分期信息", Toast.LENGTH_SHORT).show();
        return;
    }
}
```

### 步骤 8：修改总资产计算逻辑（约第 330-340 行）

**查找这段代码：**
```java
if (acc.isIncludedInTotal) {
    if (acc.type == 0 || acc.type == 3) {
        totalAsset += acc.amount;
    } else if (acc.type == 2) {
        totalAsset += acc.amount;
    } else if (acc.type == 1) {
        totalAsset -= acc.amount;
    }
}
```

**替换为：**
```java
if (acc.isIncludedInTotal) {
    if (acc.type == 0 || acc.type == 3) {
        totalAsset += acc.amount; // 资产和理财
    } else if (acc.type == 2) {
        totalAsset += acc.amount; // 借出
    } else if (acc.type == 1 || acc.type == 4) {
        totalAsset -= acc.amount; // 负债和分期（减少总资产）
    }
}
```

同样修改多币种逻辑（约第 365 行）：

```java
if (acc.isIncludedInTotal) {
    double currentTotal = assetMap.getOrDefault(symbol, 0.0);
    if (acc.type == 0 || acc.type == 3) {
        currentTotal += acc.amount; // 资产和理财
    } else if (acc.type == 2) {
        currentTotal += acc.amount; // 借出
    } else if (acc.type == 1 || acc.type == 4) {
        currentTotal -= acc.amount; // 负债和分期
    }
    assetMap.put(symbol, currentTotal);
}
```

### 步骤 9：修改负债统计（约第 325 行）

**查找这段代码：**
```java
if (acc.type == 1) {
    totalLiability += acc.amount;
}
```

**替换为：**
```java
if (acc.type == 1 || acc.type == 4) {
    totalLiability += acc.amount; // 负债和分期都计入负债统计
}
```

同样修改多币种逻辑（约第 355 行）：

```java
if (acc.type == 1 || acc.type == 4) {
    liabilityMap.put(symbol, liabilityMap.getOrDefault(symbol, 0.0) + acc.amount);
}
```

---

## 验证步骤

完成所有修改后：

1. **编译项目**：
   ```bash
   gradlew.bat :app:assembleDebug
   ```

2. **检查编译错误**：
   - 不应该再有 `rb_asset`、`rb_liability` 等符号找不到的错误
   - 不应该有 `rgType` 相关的错误

3. **运行测试**：
   - 打开资产模块
   - 点击添加资产
   - 选择"分期"类型
   - 输入分期信息
   - 保存并查看列表

---

## 注意事项

1. **备份文件**：修改前先备份 `AssetsFragment.java`
2. **逐步修改**：按照步骤顺序修改，每修改一步编译一次
3. **行号可能不准确**：如果代码有变动，行号可能不完全匹配，请根据代码内容查找
4. **使用 IDE 查找功能**：使用 Ctrl+F 查找原代码片段
5. **保持缩进**：复制代码时注意保持正确的缩进

---

## 快速查找技巧

在 IDE 中使用以下关键词快速定位：

- `rgType.getCheckedRadioButtonId()` - 需要改为 `spinnerType.getSelectedItemPosition()`
- `R.id.rb_asset` - 需要删除或改为 position 判断
- `rgType.check(` - 需要改为 `spinnerType.setSelection(`
- `rgType.setOnCheckedChangeListener` - 需要改为 `spinnerType.setOnItemSelectedListener`

---

## 完成后的功能

修改完成后，你将拥有：

1. ✅ 下拉框选择资产类型（包含"分期"选项）
2. ✅ 选择分期时显示专用输入表单
3. ✅ 自动计算总金额
4. ✅ 分期数据正确保存到数据库
5. ✅ 分期账户显示在负债列表中
6. ✅ 总资产计算正确（分期减少总资产）

---

## 后续开发（V2 版本）

当前 MVP 完成后，可以继续开发：

1. 分期详情页面（InstallmentDetailActivity）
2. 期数网格交互
3. 点击切换还款状态
4. 编辑和删除分期
5. 分期卡片优化显示

这些功能的详细设计已在 `design.md` 中提供。
