# 分期付款功能设计文档

## 架构设计

### 1. 数据层

#### 1.1 数据库变更
```java
// AssetAccount.java 新增字段
public int totalInstallments = 0;        // 总期数
public double installmentAmount = 0.0;   // 每期金额
public String paidInstallments = "[]";   // 已还期数 JSON 数组

// 数据库版本升级
// 从当前版本 -> 新版本
// 添加迁移策略
```

#### 1.2 辅助方法
```java
// AssetAccount.java
public List<Integer> getPaidInstallmentsList() {
    try {
        JSONArray array = new JSONArray(paidInstallments);
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(array.getInt(i));
        }
        return list;
    } catch (Exception e) {
        return new ArrayList<>();
    }
}

public void setPaidInstallmentsList(List<Integer> list) {
    JSONArray array = new JSONArray(list);
    paidInstallments = array.toString();
}

public int getRemainingInstallments() {
    return totalInstallments - getPaidInstallmentsList().size();
}

public double getRemainingAmount() {
    return getRemainingInstallments() * installmentAmount;
}

public double getTotalAmount() {
    return totalInstallments * installmentAmount;
}
```

### 2. UI 层

#### 2.1 添加资产对话框改造

**原布局**：
```xml
<RadioGroup android:id="@+id/rg_type">
    <RadioButton android:id="@+id/rb_asset" android:text="资产" />
    <RadioButton android:id="@+id/rb_liability" android:text="负债" />
    <RadioButton android:id="@+id/rb_lent" android:text="借出" />
    <RadioButton android:id="@+id/rb_investment" android:text="理财" />
</RadioGroup>
```

**新布局**：
```xml
<Spinner
    android:id="@+id/spinner_asset_type"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:entries="@array/asset_types" />

<!-- strings.xml -->
<string-array name="asset_types">
    <item>资产</item>
    <item>负债</item>
    <item>借出</item>
    <item>理财</item>
    <item>分期</item>
</string-array>
```

#### 2.2 分期输入表单
```xml
<!-- 当选择"分期"时显示 -->
<LinearLayout
    android:id="@+id/layout_installment_fields"
    android:visibility="gone">
    
    <EditText
        android:id="@+id/et_installment_name"
        android:hint="分期对象（如：花呗、信用卡）" />
    
    <EditText
        android:id="@+id/et_total_installments"
        android:hint="总期数"
        android:inputType="number" />
    
    <EditText
        android:id="@+id/et_installment_amount"
        android:hint="每期金额"
        android:inputType="numberDecimal" />
    
    <TextView
        android:id="@+id/tv_total_amount"
        android:text="总金额：¥0.00" />
</LinearLayout>
```

#### 2.3 分期卡片布局
```xml
<!-- item_installment_card.xml -->
<androidx.cardview.widget.CardView>
    <LinearLayout android:orientation="vertical">
        
        <!-- 标题行 -->
        <TextView
            android:id="@+id/tv_installment_name"
            android:text="花呗分期"
            android:textSize="16sp"
            android:textStyle="bold" />
        
        <!-- 进度信息 -->
        <TextView
            android:id="@+id/tv_installment_progress"
            android:text="还剩 8/12 期"
            android:textSize="14sp" />
        
        <!-- 金额信息 -->
        <LinearLayout android:orientation="horizontal">
            <TextView
                android:id="@+id/tv_installment_amount"
                android:text="¥500.00/期"
                android:textSize="18sp" />
            
            <TextView
                android:id="@+id/tv_total_amount"
                android:text="总计 ¥6000.00"
                android:textSize="12sp" />
        </LinearLayout>
        
        <!-- 进度条 -->
        <ProgressBar
            android:id="@+id/progress_installment"
            style="?android:attr/progressBarStyleHorizontal"
            android:max="100" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

#### 2.4 分期详情页面布局
```xml
<!-- activity_installment_detail.xml -->
<LinearLayout android:orientation="vertical">
    
    <!-- 顶部信息卡片 -->
    <androidx.cardview.widget.CardView>
        <LinearLayout android:orientation="vertical">
            <TextView android:id="@+id/tv_name" android:textSize="20sp" />
            <TextView android:id="@+id/tv_total_info" android:text="12 期 × ¥500.00 = ¥6000.00" />
            <TextView android:id="@+id/tv_paid_info" android:text="已还 4 期 (¥2000.00)" />
            <TextView android:id="@+id/tv_remaining_info" android:text="剩余 8 期 (¥4000.00)" />
        </LinearLayout>
    </androidx.cardview.widget.CardView>
    
    <!-- 期数网格 -->
    <TextView android:text="还款进度" android:textSize="16sp" />
    <RecyclerView
        android:id="@+id/rv_installments"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
    
    <!-- 底部操作按钮 -->
    <LinearLayout android:orientation="horizontal">
        <Button android:id="@+id/btn_edit" android:text="编辑" />
        <Button android:id="@+id/btn_delete" android:text="删除" />
    </LinearLayout>
</LinearLayout>
```

#### 2.5 期数网格项布局
```xml
<!-- item_installment_period.xml -->
<androidx.cardview.widget.CardView
    android:layout_width="60dp"
    android:layout_height="60dp"
    app:cardCornerRadius="12dp">
    
    <TextView
        android:id="@+id/tv_period_number"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:text="1"
        android:textSize="18sp"
        android:textStyle="bold" />
</androidx.cardview.widget.CardView>
```

### 3. 业务逻辑层

#### 3.1 AssetsFragment 修改

**添加资产对话框逻辑**：
```java
// 1. 将 RadioGroup 改为 Spinner
Spinner spinnerType = dialog.findViewById(R.id.spinner_asset_type);
LinearLayout layoutInstallment = dialog.findViewById(R.id.layout_installment_fields);

// 2. 监听 Spinner 选择
spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // position: 0=资产, 1=负债, 2=借出, 3=理财, 4=分期
        if (position == 4) {
            // 显示分期输入表单
            layoutInstallment.setVisibility(View.VISIBLE);
            // 隐藏普通金额输入
            etAmount.setVisibility(View.GONE);
        } else {
            layoutInstallment.setVisibility(View.GONE);
            etAmount.setVisibility(View.VISIBLE);
        }
    }
});

// 3. 保存分期数据
if (selectedType == 4) {
    account.type = 4;
    account.totalInstallments = Integer.parseInt(etTotalInstallments.getText().toString());
    account.installmentAmount = Double.parseDouble(etInstallmentAmount.getText().toString());
    account.amount = account.getTotalAmount(); // 总金额
    account.paidInstallments = "[]"; // 初始为空
}
```

**列表显示逻辑**：
```java
// 在 Adapter 中判断类型
if (item.type == 4) {
    // 使用分期卡片布局
    holder.tvProgress.setText(String.format("还剩 %d/%d 期", 
        item.getRemainingInstallments(), item.totalInstallments));
    holder.tvAmount.setText(String.format("¥%.2f/期", item.installmentAmount));
    holder.tvTotal.setText(String.format("总计 ¥%.2f", item.getTotalAmount()));
    
    // 设置进度条
    int progress = (int) ((item.getPaidInstallmentsList().size() * 100.0) / item.totalInstallments);
    holder.progressBar.setProgress(progress);
}
```

**点击事件**：
```java
holder.itemView.setOnClickListener(v -> {
    if (item.type == 4) {
        // 打开分期详情页面
        Intent intent = new Intent(context, InstallmentDetailActivity.class);
        intent.putExtra("account_id", item.id);
        context.startActivity(intent);
    } else {
        // 原有逻辑
        showAddOrEditDialog(item, item.type);
    }
});
```

#### 3.2 InstallmentDetailActivity

```java
public class InstallmentDetailActivity extends AppCompatActivity {
    
    private AssetAccount account;
    private RecyclerView rvInstallments;
    private InstallmentPeriodAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_installment_detail);
        
        int accountId = getIntent().getIntExtra("account_id", -1);
        loadAccount(accountId);
        
        setupRecyclerView();
        setupButtons();
    }
    
    private void setupRecyclerView() {
        rvInstallments.setLayoutManager(new GridLayoutManager(this, 4));
        adapter = new InstallmentPeriodAdapter(account, this::onPeriodClick);
        rvInstallments.setAdapter(adapter);
    }
    
    private void onPeriodClick(int period) {
        List<Integer> paidList = account.getPaidInstallmentsList();
        
        if (paidList.contains(period)) {
            // 已还 -> 未还
            paidList.remove(Integer.valueOf(period));
        } else {
            // 未还 -> 已还
            paidList.add(period);
        }
        
        account.setPaidInstallmentsList(paidList);
        account.amount = account.getRemainingAmount(); // 更新剩余金额
        
        // 保存到数据库
        viewModel.updateAsset(account);
        
        // 触发自动同步
        BackupManager.triggerAutoUploadIfEnabled(this);
        
        // 刷新 UI
        adapter.notifyDataSetChanged();
        updateSummary();
    }
}
```

#### 3.3 InstallmentPeriodAdapter

```java
public class InstallmentPeriodAdapter extends RecyclerView.Adapter<InstallmentPeriodAdapter.ViewHolder> {
    
    private AssetAccount account;
    private OnPeriodClickListener listener;
    private Context context;
    
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int period = position + 1;
        holder.tvPeriod.setText(String.valueOf(period));
        
        boolean isPaid = account.getPaidInstallmentsList().contains(period);
        
        if (isPaid) {
            // 已还：主题色
            int themeColor = ContextCompat.getColor(context, R.color.app_blue);
            holder.cardView.setCardBackgroundColor(themeColor);
            holder.tvPeriod.setTextColor(ContextCompat.getColor(context, R.color.text_white));
        } else {
            // 未还：浅灰色（使用项目颜色资源）
            int bgColor = ContextCompat.getColor(context, R.color.cat_unselected_bg);
            int textColor = ContextCompat.getColor(context, R.color.text_secondary);
            holder.cardView.setCardBackgroundColor(bgColor);
            holder.tvPeriod.setTextColor(textColor);
        }
        
        holder.itemView.setOnClickListener(v -> listener.onPeriodClick(period));
    }
    
    interface OnPeriodClickListener {
        void onPeriodClick(int period);
    }
}
```

### 4. 数据计算逻辑

#### 4.1 总资产计算
```java
// AssetsFragment.java - updateAssetSummary()
if (acc.isIncludedInTotal) {
    if (acc.type == 0 || acc.type == 3) {
        totalAsset += acc.amount;
    } else if (acc.type == 2) {
        totalAsset += acc.amount;
    } else if (acc.type == 1 || acc.type == 4) {
        // 负债和分期都减少总资产
        totalAsset -= acc.amount;
    }
}
```

#### 4.2 负债统计
```java
// 分期账户的金额 = 剩余应还金额
if (acc.type == 1 || acc.type == 4) {
    totalLiability += acc.amount;
}
```

## 实现步骤

### Phase 1: 数据库和模型
1. 修改 `AssetAccount.java`，添加新字段
2. 添加辅助方法
3. 升级数据库版本
4. 编写数据库迁移代码

### Phase 2: UI 布局
1. 创建分期相关布局文件
2. 修改添加资产对话框布局
3. 创建 `InstallmentDetailActivity` 布局

### Phase 3: 业务逻辑
1. 修改 `AssetsFragment` 添加资产逻辑
2. 实现 `InstallmentDetailActivity`
3. 实现 `InstallmentPeriodAdapter`
4. 更新总资产计算逻辑

### Phase 4: 测试和优化
1. 功能测试
2. UI 优化
3. 性能优化
4. WebDAV 同步测试

## 注意事项

1. **数据库迁移**：确保旧数据兼容
2. **JSON 解析**：处理异常情况
3. **UI 响应**：期数网格点击要有视觉反馈
4. **数据同步**：每次状态变更都要触发同步
5. **边界情况**：0 期、全部已还等情况
