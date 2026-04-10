package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CurrencyUtils;
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AssetsFragment extends Fragment {

    private FinanceViewModel viewModel;
    private AssistantConfig config;
    private TextView tvTotalAssets, tvTotalLiability, tvTotalLent, tvListTitle;
    private LinearLayout layoutAssets, layoutLiability, layoutLent;
    private RecyclerView rvAssets;
    private AssetAdapter adapter;
    private List<AssetAccount> allAccounts = new ArrayList<>();

    // 0: 资产, 1: 负债, 2: 借出
    private int currentType = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_assets, container, false);

        config = new AssistantConfig(requireContext());

        initViews(view);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), list -> {
            this.allAccounts = list;
            updateUI();
        });

        return view;
    }

    private void initViews(View view) {
        tvTotalAssets = view.findViewById(R.id.tv_total_assets);
        tvTotalLiability = view.findViewById(R.id.tv_total_liability);
        tvTotalLent = view.findViewById(R.id.tv_total_lent);
        tvListTitle = view.findViewById(R.id.tv_list_title);

        layoutAssets = view.findViewById(R.id.layout_assets);
        layoutLiability = view.findViewById(R.id.layout_liability);
        layoutLent = view.findViewById(R.id.layout_lent);

        rvAssets = view.findViewById(R.id.rv_assets_list);
        rvAssets.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new AssetAdapter(
                account -> showAddOrEditDialog(account, account.type),
                account -> {
                    // 【修改】允许资产(0)和负债(1)设为默认支付项
                    if (account.type == 0 || account.type == 1) {
                        int currentDefaultId = config.getDefaultAssetId();
                        if (currentDefaultId == account.id) {
                            config.setDefaultAssetId(-1);
                            adapter.setDefaultId(-1);
                            Toast.makeText(getContext(), "已取消默认支付项", Toast.LENGTH_SHORT).show();
                        } else {
                            config.setDefaultAssetId(account.id);
                            adapter.setDefaultId(account.id);
                            Toast.makeText(getContext(), "已设为默认支付项", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "仅支持将资产或负债设为默认支付项", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        rvAssets.setAdapter(adapter);

        view.findViewById(R.id.fab_add_asset).setOnClickListener(v -> {
            // 添加触摸振动反馈
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            showAddOrEditDialog(null, currentType);
        });

        // 🌟 补齐转移按钮的点击监听
        View fabTransfer = view.findViewById(R.id.fab_transfer_asset);
        if (fabTransfer != null) {
            fabTransfer.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                if (allAccounts == null || allAccounts.size() < 2) {
                    Toast.makeText(getContext(), "至少需要两个资产账户才能进行转移", Toast.LENGTH_SHORT).show();
                    return;
                }
                showTransferDialog();
            });
        }

        // 新增转移按钮监听
        view.findViewById(R.id.fab_transfer_asset).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            if (allAccounts == null || allAccounts.size() < 2) {
                Toast.makeText(getContext(), "至少需要两个资产账户才能进行转移", Toast.LENGTH_SHORT).show();
                return;
            }
            showTransferDialog();
        });

        layoutAssets.setOnClickListener(v -> switchType(0));
        layoutLiability.setOnClickListener(v -> switchType(1));
        layoutLent.setOnClickListener(v -> switchType(2));
    }

    // 🌟 补齐完整的资产转移弹窗渲染与数据处理逻辑 (分别提示转出/转入)
    private void showTransferDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_transfer_asset, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        android.widget.Spinner spinnerFrom = view.findViewById(R.id.spinner_from_account);
        android.widget.Spinner spinnerTo = view.findViewById(R.id.spinner_to_account);
        EditText etAmount = view.findViewById(R.id.et_transfer_amount);
        EditText etNote = view.findViewById(R.id.et_transfer_note);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        // 🌟 1. 分别为“转出”和“转入”创建数据列表，并插入专属提示词
        List<String> fromAccountNames = new ArrayList<>();
        List<String> toAccountNames = new ArrayList<>();
        fromAccountNames.add("请选择转出账户");
        toAccountNames.add("请选择转入账户");

        for (AssetAccount acc : allAccounts) {
            String symbol = (acc.currencySymbol != null && !acc.currencySymbol.isEmpty()) ? acc.currencySymbol : "¥";
            String accountDisplay = acc.name + " (" + symbol + String.format("%.2f", acc.amount) + ")";
            fromAccountNames.add(accountDisplay);
            toAccountNames.add(accountDisplay);
        }

        // 🌟 2. 抽象出一个创建适配器的方法，让第 0 项（提示词）变成浅色且不可点击
        android.widget.ArrayAdapter<String> fromAdapter = createHintAdapter(fromAccountNames);
        android.widget.ArrayAdapter<String> toAdapter = createHintAdapter(toAccountNames);

        spinnerFrom.setAdapter(fromAdapter);
        spinnerTo.setAdapter(toAdapter);

        // 默认显示为第0项（即提示词）
        spinnerFrom.setSelection(0);
        spinnerTo.setSelection(0);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            int fromIndex = spinnerFrom.getSelectedItemPosition();
            int toIndex = spinnerTo.getSelectedItemPosition();

            // 拦截未选择的情况 (索引为 0 代表选中的是提示词)
            if (fromIndex == 0 || toIndex == 0) {
                Toast.makeText(getContext(), "请选择转出和转入账户", Toast.LENGTH_SHORT).show();
                return;
            }

            if (fromIndex == toIndex) {
                Toast.makeText(getContext(), "转出和转入账户不能相同", Toast.LENGTH_SHORT).show();
                return;
            }

            String amountStr = etAmount.getText().toString().trim();
            if (amountStr.isEmpty()) {
                Toast.makeText(getContext(), "请输入转账金额", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    Toast.makeText(getContext(), "转账金额必须大于0", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "金额格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取实际账户时，索引需要减 1（因为第 0 项是虚拟的提示词）
            AssetAccount fromAccount = allAccounts.get(fromIndex - 1);
            AssetAccount toAccount = allAccounts.get(toIndex - 1);
            String note = etNote.getText().toString().trim();

            viewModel.transferAsset(fromAccount, toAccount, amount, note);
            Toast.makeText(getContext(), "资产转移成功", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // 🌟 辅助方法：生成带有浅色提示词特效的 ArrayAdapter
    private android.widget.ArrayAdapter<String> createHintAdapter(List<String> dataList) {
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                getContext(),
                R.layout.item_spinner_dropdown,
                dataList
        ) {
            @Override
            public boolean isEnabled(int position) {
                return position != 0; // 禁用第 0 项点击
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) v;
                if (position == 0) {
                    tv.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.text_secondary));
                } else {
                    tv.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.text_primary));
                }
                return v;
            }

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = (TextView) v;
                if (position == 0) {
                    tv.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.text_secondary));
                } else {
                    tv.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.text_primary));
                }
                return v;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        return adapter;
    }

    private void switchType(int type) {
        if (currentType != type) {
            currentType = type;
            refreshList();
        }
    }

    private void updateUI() {
        if (allAccounts == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

        if (!isCurrencyEnabled) {
            // ================== 单一币种逻辑 ==================
            double totalAsset = 0;
            double totalLiability = 0;
            double totalLent = 0;

            for (AssetAccount acc : allAccounts) {
                // 1. 统计各自的分类总额（不论是否计入总资产，面板上的负债/借出总额应如实显示）
                if (acc.type == 1) {
                    totalLiability += acc.amount;
                } else if (acc.type == 2) {
                    totalLent += acc.amount;
                }

                // 2. 统计顶部的大字“总资产”（根据是否勾选了计入总资产来加减）
                if (acc.isIncludedInTotal) {
                    if (acc.type == 0 || acc.type == 3) {
                        totalAsset += acc.amount; // 资产和理财：增加总资产
                    } else if (acc.type == 2) {
                        totalAsset += acc.amount; // 借出（别人欠我的钱）：增加总资产
                    } else if (acc.type == 1) {
                        totalAsset -= acc.amount; // 负债（我欠别人的钱）：扣减总资产
                    }
                }
            }

            // 恢复默认大字体
            tvTotalAssets.setTextSize(32);
            tvTotalAssets.setText(String.format("%.2f", totalAsset));
            tvTotalLiability.setText(String.format("%.2f", totalLiability));
            tvTotalLent.setText(String.format("%.2f", totalLent));

        } else {
            // ================== 多币种逻辑 ==================
            Map<String, Double> assetMap = new TreeMap<>();
            Map<String, Double> liabilityMap = new TreeMap<>();
            Map<String, Double> lentMap = new TreeMap<>();

            for (AssetAccount acc : allAccounts) {
                String symbol = (acc.currencySymbol != null && !acc.currencySymbol.isEmpty()) ? acc.currencySymbol : "¥";

                // 1. 统计各自的分类总额
                if (acc.type == 1) {
                    liabilityMap.put(symbol, liabilityMap.getOrDefault(symbol, 0.0) + acc.amount);
                } else if (acc.type == 2) {
                    lentMap.put(symbol, lentMap.getOrDefault(symbol, 0.0) + acc.amount);
                }

                // 2. 统计顶部的大字“总资产”
                if (acc.isIncludedInTotal) {
                    double currentTotal = assetMap.getOrDefault(symbol, 0.0);
                    if (acc.type == 0 || acc.type == 3) {
                        currentTotal += acc.amount; // 资产和理财
                    } else if (acc.type == 2) {
                        currentTotal += acc.amount; // 借出：加
                    } else if (acc.type == 1) {
                        currentTotal -= acc.amount; // 负债：减
                    }
                    assetMap.put(symbol, currentTotal);
                }
            }

            // 如果包含多个币种，缩小字体以容纳更多内容
            if (assetMap.size() > 1) {
                tvTotalAssets.setTextSize(20);
            } else {
                tvTotalAssets.setTextSize(32);
            }

            tvTotalAssets.setText(formatMultiCurrency(assetMap));
            tvTotalLiability.setText(formatMultiCurrency(liabilityMap));
            tvTotalLent.setText(formatMultiCurrency(lentMap));
        }

        refreshList();
    }

    private String formatMultiCurrency(Map<String, Double> map) {
        if (map.isEmpty()) return "0.00";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            if (sb.length() > 0) sb.append("  "); // 使用两个空格分隔，增加可读性
            sb.append(entry.getKey()).append(String.format("%.2f", entry.getValue()));
        }
        return sb.toString();
    }

    private void refreshList() {
        List<AssetAccount> filteredList = new ArrayList<>();
        for (AssetAccount acc : allAccounts) {
            // 当选中资产(0)时，同时显示普通资产(0)和理财(3)
            if (currentType == 0 && (acc.type == 0 || acc.type == 3)) {
                filteredList.add(acc);
            } else if (acc.type == currentType) {
                filteredList.add(acc);
            }
        }

        // 【新增排序逻辑】
        // 当处于“我的资产”页面时，将理财(type=3)沉底，普通资产(type=0)置顶
        if (currentType == 0) {
            java.util.Collections.sort(filteredList, new java.util.Comparator<AssetAccount>() {
                @Override
                public int compare(AssetAccount o1, AssetAccount o2) {
                    return Integer.compare(o1.type, o2.type);
                }
            });
        }

        adapter.setDefaultId(config.getDefaultAssetId());
        adapter.setData(filteredList);

        if (currentType == 0) {
            tvListTitle.setText("我的资产");
        } else if (currentType == 1) {
            tvListTitle.setText("我的负债");
        } else {
            tvListTitle.setText("我的借出");
        }
    }

    private void showDatePickerDialog(long currentDateMillis, java.util.function.Consumer<Long> onDateSelected) {
        if (getContext() == null) return;
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);

        dialog.setOnShowListener(d -> {
            View bottomSheet = ((com.google.android.material.bottomsheet.BottomSheetDialog) d).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
        });

        java.time.LocalDate baseDate = java.time.Instant.ofEpochMilli(currentDateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        android.widget.NumberPicker npYear = dialog.findViewById(R.id.np_year);
        android.widget.NumberPicker npMonth = dialog.findViewById(R.id.np_month);
        android.widget.NumberPicker npDay = dialog.findViewById(R.id.np_day);
        TextView tvPreview = dialog.findViewById(R.id.tv_date_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        npYear.setMinValue(2000);
        npYear.setMaxValue(2050);
        npYear.setValue(baseDate.getYear());

        npMonth.setMinValue(1);
        npMonth.setMaxValue(12);
        npMonth.setValue(baseDate.getMonthValue());

        npDay.setMinValue(1);
        npDay.setMaxValue(java.time.YearMonth.of(baseDate.getYear(), baseDate.getMonthValue()).lengthOfMonth());
        npDay.setValue(baseDate.getDayOfMonth());

        android.widget.NumberPicker.OnValueChangeListener listener = (picker, oldVal, newVal) -> {
            int maxDays = java.time.YearMonth.of(npYear.getValue(), npMonth.getValue()).lengthOfMonth();
            if (npDay.getMaxValue() != maxDays) {
                npDay.setMaxValue(maxDays);
                if (npDay.getValue() > maxDays) npDay.setValue(maxDays);
            }
            try {
                java.time.LocalDate d = java.time.LocalDate.of(npYear.getValue(), npMonth.getValue(), npDay.getValue());
                tvPreview.setText(d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA)));
            } catch (Exception ignored) {}
        };

        npYear.setOnValueChangedListener(listener);
        npMonth.setOnValueChangedListener(listener);
        npDay.setOnValueChangedListener(listener);

        try {
            tvPreview.setText(baseDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA)));
        } catch (Exception ignored) {}

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            java.time.LocalDate selected = java.time.LocalDate.of(npYear.getValue(), npMonth.getValue(), npDay.getValue());
            long millis = selected.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            onDateSelected.accept(millis);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showAddOrEditDialog(AssetAccount existing, int initType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_asset, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        RadioGroup rgType = view.findViewById(R.id.rg_asset_type);
        EditText etName = view.findViewById(R.id.et_asset_name);
        EditText etAmount = view.findViewById(R.id.et_asset_amount);
        Button btnCurrency = view.findViewById(R.id.btn_currency);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSave = view.findViewById(R.id.btn_save);
        Button btnDelete = view.findViewById(R.id.btn_delete);

        // 【新增 1】初始化 Spinner
        android.widget.Spinner spinnerInclude = view.findViewById(R.id.spinner_include_in_total);
        android.widget.ArrayAdapter<String> includeAdapter = new android.widget.ArrayAdapter<>(
                getContext(),
                R.layout.item_spinner_dropdown,
                new String[]{"计入总资产", "不计入总资产"}
        );
        includeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerInclude.setAdapter(includeAdapter);

        // ========== 新增代码：初始化颜色 Spinner ==========
        android.widget.Spinner spinnerColor = view.findViewById(R.id.spinner_asset_color);
        android.widget.ArrayAdapter<String> colorAdapter = new android.widget.ArrayAdapter<>(
                getContext(),
                R.layout.item_spinner_dropdown,
                new String[]{"默认颜色", "红色背景", "绿色背景"}
        );
        colorAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerColor.setAdapter(colorAdapter);
        // =================================================

        // 理财专用的控件 (更新为 Spinner 和 EditText)
        LinearLayout layoutInvestment = view.findViewById(R.id.layout_investment_details);
        android.widget.Spinner spinnerDepositType = view.findViewById(R.id.spinner_deposit_type);
        android.widget.Spinner spinnerInterestType = view.findViewById(R.id.spinner_interest_type);
        EditText etDuration = view.findViewById(R.id.et_duration);
        EditText etRate = view.findViewById(R.id.et_interest_rate);
        EditText etDepositDate = view.findViewById(R.id.et_deposit_date); // 新增存入时间控件
        EditText etExpected = view.findViewById(R.id.et_expected_total);

        // 初始化下拉框的数据 (应用统一的下拉样式 R.layout.item_spinner_dropdown)
        android.widget.ArrayAdapter<String> depositAdapter = new android.widget.ArrayAdapter<>(
                getContext(),
                R.layout.item_spinner_dropdown,
                new String[]{"定期", "活期"}
        );
        depositAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerDepositType.setAdapter(depositAdapter);

        android.widget.ArrayAdapter<String> interestAdapter = new android.widget.ArrayAdapter<>(
                getContext(),
                R.layout.item_spinner_dropdown,
                new String[]{"单利", "复利"}
        );
        interestAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerInterestType.setAdapter(interestAdapter);

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

        if (isCurrencyEnabled) {
            btnCurrency.setVisibility(View.VISIBLE);
            if (existing != null && existing.currencySymbol != null && !existing.currencySymbol.isEmpty()) {
                btnCurrency.setText(existing.currencySymbol);
            } else {
                btnCurrency.setText("¥");
            }
            btnCurrency.setOnClickListener(v -> CurrencyUtils.showCurrencyDialog(getContext(), btnCurrency, false));
        } else {
            btnCurrency.setVisibility(View.GONE);
        }

        // 处理存入时间的逻辑
        final long[] currentDepositMillis = {System.currentTimeMillis()};
        java.text.SimpleDateFormat sdfDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA);

        // 初始化已有数据
        if (existing != null) {
            etName.setText(existing.name);
            etAmount.setText(String.format("%.2f", existing.amount)); // 修改为保留两位小数
            btnCancel.setVisibility(View.GONE);
            btnDelete.setVisibility(View.VISIBLE);

            // 【新增 2】回显是否计入总资产 (0:计入, 1:不计入)
            spinnerInclude.setSelection(existing.isIncludedInTotal ? 0 : 1);

            // ========== 新增代码：回显颜色选择 ==========
            spinnerColor.setSelection(existing.colorType);
            // ===========================================

            // 回显理财数据
            if (existing.type == 3) {
                spinnerDepositType.setSelection(existing.isFixedTerm ? 0 : 1);
                spinnerInterestType.setSelection(existing.isCompoundInterest ? 1 : 0);
                etDuration.setText(String.valueOf(existing.durationMonths));
                etRate.setText(String.valueOf(existing.interestRate));
                if (existing.depositDate > 0) {
                    currentDepositMillis[0] = existing.depositDate;
                }
                etExpected.setText(String.format("预计结算资产: %.2f", existing.expectedReturn));
            }
        } else {
            btnCancel.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.GONE);
        }

        // 无论新增还是修改，初始化显示存入时间
        etDepositDate.setText("存入时间: " + sdfDate.format(new java.util.Date(currentDepositMillis[0])));

        // 呼出日期选择器
        etDepositDate.setOnClickListener(v -> {
            showDatePickerDialog(currentDepositMillis[0], newMillis -> {
                currentDepositMillis[0] = newMillis;
                etDepositDate.setText("存入时间: " + sdfDate.format(new java.util.Date(currentDepositMillis[0])));
            });
        });

        // 动态计算理财预期收益的逻辑
        Runnable calculateExpected = () -> {
            try {
                String amountStr = etAmount.getText().toString().trim();
                String durationStr = etDuration.getText().toString().trim();
                String rateStr = etRate.getText().toString().trim();

                if (!amountStr.isEmpty() && !durationStr.isEmpty() && !rateStr.isEmpty()) {
                    double principal = Double.parseDouble(amountStr);
                    double annualRate = Double.parseDouble(rateStr) / 100.0;
                    int months = Integer.parseInt(durationStr);

                    double expected = 0;
                    // 判断是否选择复利（1 为复利，0 为单利）
                    if (spinnerInterestType.getSelectedItemPosition() == 1) {
                        expected = principal * Math.pow(1 + (annualRate / 12.0), months);
                    } else {
                        expected = principal + (principal * annualRate * (months / 12.0));
                    }

                    etExpected.setText(String.format("预计结算资产: %.2f", expected));
                } else {
                    etExpected.setText("预计结算资产: 0.00");
                }
            } catch (Exception e) {
                etExpected.setText("预计结算资产: 0.00");
            }
        };

        // 监听本金、时长、利率的输入变化
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { calculateExpected.run(); }
        };
        etAmount.addTextChangedListener(watcher);
        etDuration.addTextChangedListener(watcher);
        etRate.addTextChangedListener(watcher);

        // 监听下拉框选项变化
        android.widget.AdapterView.OnItemSelectedListener spinnerListener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                calculateExpected.run();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        };
        spinnerDepositType.setOnItemSelectedListener(spinnerListener);
        spinnerInterestType.setOnItemSelectedListener(spinnerListener);

        // 切换资产类型时的 UI 更新
        Runnable updateLabels = () -> {
            int selectedId = rgType.getCheckedRadioButtonId();
            String titleSuffix = "";
            String nameHint = "";
            String amountHint = ""; // 动态控制金额提示词
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

            // 【新增 3】如果是新建资产，根据选中的类型自动切换“计入/不计入”的默认状态
            if (existing == null) {
                if (selectedId == R.id.rb_asset) {
                    spinnerInclude.setSelection(0); // 资产默认“计入总资产”
                } else {
                    spinnerInclude.setSelection(1); // 负债、借出、理财默认“不计入总资产”
                }
            }

            tvTitle.setText((existing == null ? "添加" : "修改") + titleSuffix);
            etName.setHint(nameHint);
            etAmount.setHint(amountHint);
        };

        rgType.setOnCheckedChangeListener((group, checkedId) -> updateLabels.run());

        int targetType = (existing != null) ? existing.type : initType;
        if (targetType == 1) rgType.check(R.id.rb_liability);
        else if (targetType == 2) rgType.check(R.id.rb_lent);
        else if (targetType == 3) rgType.check(R.id.rb_investment);
        else rgType.check(R.id.rb_asset);

        updateLabels.run();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDelete.setOnClickListener(v -> {
            AlertDialog.Builder delBuilder = new AlertDialog.Builder(getContext());
            View delView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
            delBuilder.setView(delView);
            AlertDialog delDialog = delBuilder.create();
            if (delDialog.getWindow() != null) delDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            TextView tvMsg = delView.findViewById(R.id.tv_dialog_message);
            tvMsg.setText("确定要删除记录 “" + existing.name + "” 吗？\n相关记录可能无法正确显示。");

            delView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(dv -> delDialog.dismiss());
            delView.findViewById(R.id.btn_dialog_confirm).setOnClickListener(dv -> {
                viewModel.deleteAsset(existing);
                delDialog.dismiss();
                dialog.dismiss();
            });

            delDialog.show();
        });

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim();
            if (name.isEmpty() || amountStr.isEmpty()) return;

            double amount = 0;
            try { amount = Double.parseDouble(amountStr); } catch (NumberFormatException e) { return; }

            int finalType = 0;
            int selectedId = rgType.getCheckedRadioButtonId();
            if (selectedId == R.id.rb_liability) finalType = 1;
            else if (selectedId == R.id.rb_lent) finalType = 2;
            else if (selectedId == R.id.rb_investment) finalType = 3;

            String symbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

            AssetAccount accountToSave = (existing == null) ? new AssetAccount(name, amount, finalType) : existing;
            accountToSave.name = name;
            accountToSave.amount = amount;
            accountToSave.type = finalType;
            accountToSave.currencySymbol = symbol;
            accountToSave.updateTime = System.currentTimeMillis();

            // 【新增 4】保存是否计入总资产的选项
            accountToSave.isIncludedInTotal = (spinnerInclude.getSelectedItemPosition() == 0);

            // ========== 新增代码：保存颜色选择 ==========
            accountToSave.colorType = spinnerColor.getSelectedItemPosition();
            // ===========================================

            if (finalType == 3) {
                // 读取 Spinner 状态
                accountToSave.isFixedTerm = spinnerDepositType.getSelectedItemPosition() == 0;
                accountToSave.isCompoundInterest = spinnerInterestType.getSelectedItemPosition() == 1;
                accountToSave.depositDate = currentDepositMillis[0]; // 保存存入时间

                try {
                    accountToSave.durationMonths = Integer.parseInt(etDuration.getText().toString().trim());
                    double annualRate = Double.parseDouble(etRate.getText().toString().trim());
                    accountToSave.interestRate = annualRate;

                    if (accountToSave.isCompoundInterest) {
                        accountToSave.expectedReturn = amount * Math.pow(1 + ((annualRate / 100.0) / 12.0), accountToSave.durationMonths);
                    } else {
                        accountToSave.expectedReturn = amount + (amount * (annualRate / 100.0) * (accountToSave.durationMonths / 12.0));
                    }
                } catch (Exception e) {
                    accountToSave.durationMonths = 0;
                    accountToSave.interestRate = 0.0;
                    accountToSave.expectedReturn = amount;
                }
            }

            if (existing == null) viewModel.addAsset(accountToSave);
            else viewModel.updateAsset(accountToSave);

            dialog.dismiss();
        });

        dialog.show();
    }
    // --- Adapter ---
    private static class AssetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_NORMAL = 0;
        private static final int TYPE_INVESTMENT = 3;

        private List<AssetAccount> data = new ArrayList<>();
        private final OnItemClickListener listener;
        private final OnItemLongClickListener longListener;
        private int defaultAssetId = -1;

        interface OnItemClickListener {
            void onClick(AssetAccount account);
        }

        interface OnItemLongClickListener {
            void onLongClick(AssetAccount account);
        }

        AssetAdapter(OnItemClickListener listener, OnItemLongClickListener longListener) {
            this.listener = listener;
            this.longListener = longListener;
        }

        void setData(List<AssetAccount> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        void setDefaultId(int id) {
            this.defaultAssetId = id;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return data.get(position).type == 3 ? TYPE_INVESTMENT : TYPE_NORMAL;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_INVESTMENT) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_asset_investment, parent, false);
                return new InvestmentVH(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_asset_detail, parent, false);
                return new NormalVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AssetAccount item = data.get(position);
            Context context = holder.itemView.getContext();
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

            // 【关键修复：一定要在这里声明 isCustomBg】
            boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;

            String symbol = (item.currencySymbol != null && !item.currencySymbol.isEmpty()) ? item.currencySymbol : "¥";

            if (holder instanceof NormalVH) {
                NormalVH normalHolder = (NormalVH) holder;
                normalHolder.tvName.setText(item.name);

                String amountStr = String.format("%.2f", item.amount);
                if (isCurrencyEnabled) {
                    normalHolder.tvAmount.setText(symbol + amountStr);
                } else {
                    normalHolder.tvAmount.setText(amountStr);
                }

                boolean isDefault = (item.id == defaultAssetId);
                boolean isCustomColor = (item.colorType == 1 || item.colorType == 2);
                normalHolder.itemView.setSelected(isDefault);

                // ========== 1. 背景颜色和圆角处理 (默认资产优先级最高) ==========
                if (isDefault) {
                    // 最高优先级：如果是默认支付项，强制使用 selector 背景
                    // 配合上面的 setSelected(true)，系统会自动渲染出主题高亮色
                    normalHolder.itemView.setBackgroundResource(R.drawable.selector_asset_bg);
                } else if (isCustomColor) {
                    // 次优先级：非默认项但设置了红绿色
                    int bgColor = item.colorType == 1 ?
                            androidx.core.content.ContextCompat.getColor(context, R.color.income_red) :
                            androidx.core.content.ContextCompat.getColor(context, R.color.expense_green);

                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setColor(bgColor);
                    float radius = 12f * context.getResources().getDisplayMetrics().density;
                    gd.setCornerRadius(radius);
                    normalHolder.itemView.setBackground(gd);
                } else {
                    // 最低优先级：普通未选中背景
                    normalHolder.itemView.setBackgroundResource(R.drawable.selector_asset_bg);
                }

                // ========== 2. 字体反色处理 ==========
                if (isDefault || isCustomColor) {
                    // 只要是默认支付项或者设置了红绿背景，字体全白
                    normalHolder.tvName.setTextColor(Color.WHITE);
                    normalHolder.tvAmount.setTextColor(Color.WHITE);
                } else {
                    // 恢复默认字体颜色
                    try {
                        normalHolder.tvName.setTextColor(context.getColor(R.color.text_primary));
                    } catch (Exception e) {
                        normalHolder.tvName.setTextColor(Color.BLACK);
                    }

                    if (item.type == 0) {
                        normalHolder.tvAmount.setTextColor(context.getColor(R.color.app_yellow));
                    } else if (item.type == 1) {
                        normalHolder.tvAmount.setTextColor(context.getColor(R.color.expense_green));
                    } else {
                        normalHolder.tvAmount.setTextColor(context.getColor(R.color.income_red));
                    }
                }

                // 【应用透明度】
                android.graphics.drawable.Drawable bg = normalHolder.itemView.getBackground();
                if (bg != null) {
                    bg.mutate().setAlpha(isCustomBg ? 230 : 255);
                }

                normalHolder.tvNote.setVisibility(View.GONE);
            }
            else if (holder instanceof InvestmentVH) {
                InvestmentVH invHolder = (InvestmentVH) holder;
                String typeStr = item.isFixedTerm ? "定期" : "活期";
                String interestModeStr = item.isCompoundInterest ? "复利" : "单利";

                String depositStr = "--";
                String settlementStr = "--";
                if (item.depositDate > 0) {
                    java.time.LocalDate dDate = java.time.Instant.ofEpochMilli(item.depositDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    depositStr = dDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    java.time.LocalDate sDate = dDate.plusMonths(item.durationMonths);
                    settlementStr = sDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }

                String info = String.format("%s (%s | %s)\n本金: %s%.2f\n存入: %s | 结算: %s\n周期: %d个月 | 年化: %.2f%%\n预计结算: %s%.2f",
                        item.name, typeStr, interestModeStr, symbol, item.amount, depositStr, settlementStr, item.durationMonths, item.interestRate, symbol, item.expectedReturn);

                invHolder.tvInfo.setText(info);

                boolean isDefault = (item.id == defaultAssetId);
                boolean isCustomColor = (item.colorType == 1 || item.colorType == 2);
                invHolder.itemView.setSelected(isDefault);

                // ========== 1. 字体反色处理 ==========
                if (isDefault || isCustomColor) {
                    invHolder.tvInfo.setTextColor(Color.WHITE);
                } else {
                    invHolder.tvInfo.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary));
                }

                // ========== 2. 背景颜色和透明度处理 ==========
                if (invHolder.itemView instanceof androidx.cardview.widget.CardView) {
                    androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) invHolder.itemView;
                    int surfaceColor = androidx.core.content.ContextCompat.getColor(context, R.color.white);

                    if (item.colorType == 1) {
                        surfaceColor = androidx.core.content.ContextCompat.getColor(context, R.color.income_red);
                    } else if (item.colorType == 2) {
                        surfaceColor = androidx.core.content.ContextCompat.getColor(context, R.color.expense_green);
                    }

                    card.setCardBackgroundColor(isCustomBg ?
                            androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230) : surfaceColor);
                }
            }

            holder.itemView.setOnClickListener(v -> listener.onClick(item));
            holder.itemView.setOnLongClickListener(v -> {
                longListener.onLongClick(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        // 普通资产/负债/借出 ViewHolder
        static class NormalVH extends RecyclerView.ViewHolder {
            TextView tvName, tvAmount, tvNote;
            NormalVH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_detail_date);
                tvAmount = v.findViewById(R.id.tv_detail_amount);
                tvNote = v.findViewById(R.id.tv_detail_note);
            }
        }

        // 理财卡片 ViewHolder
        static class InvestmentVH extends RecyclerView.ViewHolder {
            TextView tvInfo;
            InvestmentVH(View v) {
                super(v);
                // 对应 item_asset_investment.xml 中的 TextView ID
                tvInfo = v.findViewById(R.id.tv_investment_info);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
        updateFragmentTransparency(isCustomBg);
    }

    private void updateFragmentTransparency(boolean isCustomBg) {
        View view = getView();
        if (view == null) return;

        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);
        androidx.cardview.widget.CardView cardAssetsSummary = view.findViewById(R.id.card_assets_summary);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabAddAsset = view.findViewById(R.id.fab_add_asset);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabTransferAsset = view.findViewById(R.id.fab_transfer_asset);

        if (isCustomBg) {
            // 1. 基础背景全透明
            view.setBackgroundColor(Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(Color.TRANSPARENT);

            // 2. 顶部总计卡片：90%透明度 (230)
            if (cardAssetsSummary != null) {
                int surfaceColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white);
                int translucentSurface = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230);
                cardAssetsSummary.setCardBackgroundColor(translucentSurface);
            }

            // 3. 添加资产按钮：90%透明度
            if (fabAddAsset != null) {
                int fabColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.app_yellow);
                int translucentFab = androidx.core.graphics.ColorUtils.setAlphaComponent(fabColor, 230);
                fabAddAsset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(translucentFab));
            }

            if (fabTransferAsset != null) {
                int whiteColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white);
                fabTransferAsset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(whiteColor, 230)));
            }

        } else {
            // ================= 恢复系统默认模式 =================
            view.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);

            if (cardAssetsSummary != null) {
                cardAssetsSummary.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
            }
            if (fabAddAsset != null) {
                fabAddAsset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.app_yellow)));
            }

            fabAddAsset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.app_yellow)));

            if (fabTransferAsset != null) {
                int transferColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.transfer_fab_bg);
                fabTransferAsset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(transferColor));
            }

        }

        // 通知列表刷新，以便更新列表项的透明度
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void applyThemeBackground() {
        View view = getView();
        if (view == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);

        if (prefs.getInt("theme_mode", -1) == 3) {
            view.setBackgroundColor(Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(Color.TRANSPARENT);
        } else {
            view.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);
        }
    }

}