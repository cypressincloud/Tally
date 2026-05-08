package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.BackupManager;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.util.List;

public class InstallmentDetailActivity extends AppCompatActivity {

    private FinanceViewModel viewModel;
    private AssetAccount account;
    // 1. 从变量声明中删除 tvTotalInfo
    private TextView tvTitle, tvInstallmentName, tvPaidInfo, tvRemainingInfo;
    private RecyclerView rvInstallments;
    private InstallmentPeriodAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 开启全局沉浸式 (Edge-to-Edge)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_installment_detail);

        // 2. 动态处理状态栏和小白条的 Insets 边距，与 SettingsActivity 保持一致
        View rootView = findViewById(R.id.installment_detail_root);
        if (rootView != null) {
            final int originalPaddingLeft = rootView.getPaddingLeft();
            final int originalPaddingTop = rootView.getPaddingTop();
            final int originalPaddingRight = rootView.getPaddingRight();
            final int originalPaddingBottom = rootView.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        originalPaddingLeft + insets.left,
                        originalPaddingTop + insets.top,
                        originalPaddingRight + insets.right,
                        originalPaddingBottom + insets.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);

        initViews();
        loadAccount();
    }

    private void initViews() {
        ImageButton btnEdit = findViewById(R.id.btn_edit);
        ImageButton btnDelete = findViewById(R.id.btn_delete);

        tvTitle = findViewById(R.id.tv_title);
        tvInstallmentName = findViewById(R.id.tv_installment_name);

        // 2. 删除或注释掉下面这行，因为我们在 updateUI() 中使用的是新的 ID
        // tvTotalInfo = findViewById(R.id.tv_total_info);

        tvPaidInfo = findViewById(R.id.tv_paid_info);
        tvRemainingInfo = findViewById(R.id.tv_remaining_info);

        rvInstallments = findViewById(R.id.rv_installments);
        if (rvInstallments != null) {
            rvInstallments.setLayoutManager(new GridLayoutManager(this, 4));
        }

        btnEdit.setOnClickListener(v -> editInstallment());
        btnDelete.setOnClickListener(v -> deleteInstallment());
    }

    private void loadAccount() {
        int accountId = getIntent().getIntExtra("account_id", -1);
        if (accountId == -1) {
            Toast.makeText(this, "无效的分期账户", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel.getAllAssets().observe(this, accounts -> {
            for (AssetAccount acc : accounts) {
                if (acc.id == accountId) {
                    account = acc;
                    updateUI();
                    break;
                }
            }
        });
    }

    // 在 updateUI 中加入逻辑
    private void updateUI() {
        if (account == null) return;

        // 读取配置：统一使用 enable_currency 键值
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean showCurrency = prefs.getBoolean("enable_currency", false);

        String symbol = "";
        if (showCurrency) {
            symbol = (account.currencySymbol != null && !account.currencySymbol.isEmpty())
                    ? account.currencySymbol : "¥";
        }

        // 🌟 核心修改：标题不再写死，显示当前分期账户的名称
        if (tvInstallmentName != null) {
            tvInstallmentName.setText(account.name);
        }

        // 更新大总额显示（受币种开关控制）
        TextView tvTotalAmountDisplay = findViewById(R.id.tv_total_amount_display);
        if (tvTotalAmountDisplay != null) {
            tvTotalAmountDisplay.setText(String.format("%s%.2f", symbol, account.getTotalAmount()));
        }

        // 更新规格信息（受币种开关控制）
        TextView tvSpecs = findViewById(R.id.tv_installment_specs);
        if (tvSpecs != null) {
            tvSpecs.setText(String.format("%d 期 · 每期 %s%.2f",
                    account.totalInstallments, symbol, account.installmentAmount));
        }

        // 更新进度条和底部已还/剩余统计
        ProgressBar pbProgress = findViewById(R.id.pb_repayment_progress);
        int paidCount = account.getPaidInstallmentsList().size();
        if (pbProgress != null) {
            pbProgress.setMax(account.totalInstallments);
            pbProgress.setProgress(paidCount);
        }

        tvPaidInfo.setText(String.format("已还 %d 期 (%s%.2f)",
                paidCount, symbol, account.getPaidAmount()));
        tvRemainingInfo.setText(String.format("剩余 %d 期 (%s%.2f)",
                account.getRemainingInstallments(), symbol, account.getRemainingAmount()));

        // 刷新列表
        if (adapter == null) {
            adapter = new InstallmentPeriodAdapter(account, this::onPeriodClick);
            rvInstallments.setAdapter(adapter);
        } else {
            adapter.updateAccount(account);
        }
    }
    private void onPeriodClick(int period) {
        if (account == null) return;

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
        updateUI();
    }

    private void editInstallment() {
        if (account == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_asset, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // 获取控件
        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        android.widget.Spinner spinnerType = view.findViewById(R.id.spinner_asset_type);
        android.widget.EditText etName = view.findViewById(R.id.et_asset_name);
        android.widget.EditText etAmount = view.findViewById(R.id.et_asset_amount);
        android.widget.Button btnCurrency = view.findViewById(R.id.btn_currency);
        android.widget.Spinner spinnerInclude = view.findViewById(R.id.spinner_include_in_total);
        android.widget.Spinner spinnerColor = view.findViewById(R.id.spinner_asset_color);
        LinearLayout layoutInstallment = view.findViewById(R.id.layout_installment_fields);
        android.widget.EditText etTotalInstallments = view.findViewById(R.id.et_total_installments);
        android.widget.EditText etInstallmentAmount = view.findViewById(R.id.et_installment_amount);
        TextView tvTotalAmount = view.findViewById(R.id.tv_total_amount_display);
        android.widget.Button btnCancel = view.findViewById(R.id.btn_cancel);
        android.widget.Button btnSave = view.findViewById(R.id.btn_save);
        android.widget.Button btnDelete = view.findViewById(R.id.btn_delete);

        // 设置标题
        tvTitle.setText("修改分期");

        // 隐藏不需要的控件
        spinnerType.setVisibility(View.GONE); // 隐藏类型选择
        etAmount.setVisibility(View.GONE); // 隐藏普通金额输入
        btnCurrency.setVisibility(View.GONE); // 隐藏币种按钮
        spinnerInclude.setVisibility(View.GONE); // 隐藏计入总资产选项
        spinnerColor.setVisibility(View.GONE); // 隐藏颜色选择
        btnDelete.setVisibility(View.GONE); // 隐藏删除按钮（详情页已有删除按钮）
        btnCancel.setVisibility(View.VISIBLE); // 显示取消按钮

        // 显示分期输入表单
        layoutInstallment.setVisibility(View.VISIBLE);

        // 回显当前数据
        etName.setText(account.name);
        etName.setHint("分期对象");
        etTotalInstallments.setText(String.valueOf(account.totalInstallments));
        etInstallmentAmount.setText(String.format("%.2f", account.installmentAmount));

        // 自动计算总金额
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
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
                    tvTotalAmount.setText(String.format("总金额：¥%.2f", total));
                } catch (Exception e) {
                    tvTotalAmount.setText("总金额：¥0.00");
                }
            }
        };
        etTotalInstallments.addTextChangedListener(watcher);
        etInstallmentAmount.addTextChangedListener(watcher);

        // 初始化显示总金额
        tvTotalAmount.setText(String.format("总金额：¥%.2f", account.getTotalAmount()));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String periodsStr = etTotalInstallments.getText().toString().trim();
            String amountStr = etInstallmentAmount.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "请输入分期对象名称", Toast.LENGTH_SHORT).show();
                return;
            }

            if (periodsStr.isEmpty()) {
                Toast.makeText(this, "请输入总期数", Toast.LENGTH_SHORT).show();
                return;
            }

            if (amountStr.isEmpty()) {
                Toast.makeText(this, "请输入每期金额", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int newTotalInstallments = Integer.parseInt(periodsStr);
                double newInstallmentAmount = Double.parseDouble(amountStr);

                if (newTotalInstallments <= 0) {
                    Toast.makeText(this, "总期数必须大于0", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (newInstallmentAmount <= 0) {
                    Toast.makeText(this, "每期金额必须大于0", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 检查是否修改了总期数
                if (newTotalInstallments != account.totalInstallments) {
                    // 如果减少了期数，需要清理超出范围的已还期数
                    List<Integer> paidList = account.getPaidInstallmentsList();
                    List<Integer> newPaidList = new java.util.ArrayList<>();
                    for (Integer period : paidList) {
                        if (period <= newTotalInstallments) {
                            newPaidList.add(period);
                        }
                    }
                    account.setPaidInstallmentsList(newPaidList);
                }

                // 更新数据
                account.name = name;
                account.totalInstallments = newTotalInstallments;
                account.installmentAmount = newInstallmentAmount;
                account.amount = account.getRemainingAmount(); // 更新剩余金额
                account.updateTime = System.currentTimeMillis();

                // 保存到数据库
                viewModel.updateAsset(account);

                // 触发自动同步
                BackupManager.triggerAutoUploadIfEnabled(this);

                Toast.makeText(this, "修改成功", Toast.LENGTH_SHORT).show();
                dialog.dismiss();

                // 刷新 UI
                updateUI();

            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void deleteInstallment() {
        if (account == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvMsg = view.findViewById(R.id.tv_dialog_message);
        tvMsg.setText("确定要删除分期 \"" + account.name + "\" 吗？\n所有还款记录将被删除。");

        view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            viewModel.deleteAsset(account);
            Toast.makeText(this, "分期已删除", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            finish();
        });

        dialog.show();
    }

    /**
     * 期数网格适配器
     */
    private static class InstallmentPeriodAdapter extends RecyclerView.Adapter<InstallmentPeriodAdapter.ViewHolder> {

        private AssetAccount account;
        private final OnPeriodClickListener listener;

        interface OnPeriodClickListener {
            void onPeriodClick(int period);
        }

        InstallmentPeriodAdapter(AssetAccount account, OnPeriodClickListener listener) {
            this.account = account;
            this.listener = listener;
        }

        void updateAccount(AssetAccount account) {
            this.account = account;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_installment_period, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int period = position + 1;
            holder.tvPeriod.setText(String.valueOf(period));

            boolean isPaid = account.getPaidInstallmentsList().contains(period);

            if (isPaid) {
                // 已还：主题色
                int themeColor = androidx.core.content.ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.app_blue);
                holder.cardView.setCardBackgroundColor(themeColor);
                holder.tvPeriod.setTextColor(androidx.core.content.ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.text_white));
            } else {
                // 未还：浅灰色（使用项目颜色资源）
                int bgColor = androidx.core.content.ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.cat_unselected_bg);
                int textColor = androidx.core.content.ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.text_secondary);
                holder.cardView.setCardBackgroundColor(bgColor);
                holder.tvPeriod.setTextColor(textColor);
            }

            holder.itemView.setOnClickListener(v -> {
                // 添加触摸振动反馈
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                listener.onPeriodClick(period);
            });
        }

        @Override
        public int getItemCount() {
            return account.totalInstallments;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            androidx.cardview.widget.CardView cardView;
            TextView tvPeriod;

            ViewHolder(View view) {
                super(view);
                cardView = view.findViewById(R.id.card_period);
                tvPeriod = view.findViewById(R.id.tv_period_number);
            }
        }
    }
}