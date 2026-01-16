package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RecordFragment extends Fragment {

    private FinanceViewModel viewModel;
    private CalendarAdapter adapter;
    private YearMonth currentMonth;
    private LocalDate selectedDate;
    private TextView tvMonthTitle;

    private TextView tvIncome, tvExpense, tvBalance, tvOvertime;
    private LinearLayout layoutIncome, layoutExpense, layoutBalance, layoutOvertime;

    private List<AssetAccount> cachedAssets = new ArrayList<>();
    private TransactionListAdapter currentDetailAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        currentMonth = YearMonth.now();

        tvMonthTitle = view.findViewById(R.id.tv_month_title);

        tvIncome = view.findViewById(R.id.tv_month_income);
        tvExpense = view.findViewById(R.id.tv_month_expense);
        tvBalance = view.findViewById(R.id.tv_month_balance);
        tvOvertime = view.findViewById(R.id.tv_month_overtime);

        layoutIncome = view.findViewById(R.id.layout_stat_income);
        layoutExpense = view.findViewById(R.id.layout_stat_expense);
        layoutBalance = view.findViewById(R.id.layout_stat_balance);
        layoutOvertime = view.findViewById(R.id.layout_stat_overtime);

        RecyclerView recyclerView = view.findViewById(R.id.calendar_recycler);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 7));

        adapter = new CalendarAdapter(date -> {
            if (date.equals(selectedDate)) {
                showDateDetailDialog(date);
            } else {
                selectedDate = date;
                adapter.setSelectedDate(date);
            }
        });
        recyclerView.setAdapter(adapter);

        layoutBalance.setOnClickListener(v -> switchFilterMode(0));
        layoutIncome.setOnClickListener(v -> switchFilterMode(1));
        layoutExpense.setOnClickListener(v -> switchFilterMode(2));
        layoutOvertime.setOnClickListener(v -> switchFilterMode(3));

        tvMonthTitle.setOnClickListener(v -> {
            long selection = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("选择月份")
                    .setSelection(selection)
                    .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
                    .setTheme(R.style.ThemeOverlay_App_DatePicker)
                    .build();
            datePicker.addOnPositiveButtonClickListener(selectionMillis -> {
                LocalDate date = Instant.ofEpochMilli(selectionMillis).atZone(ZoneOffset.UTC).toLocalDate();
                currentMonth = YearMonth.from(date);
                updateCalendar();
            });
            datePicker.show(getParentFragmentManager(), "MONTH_PICKER");
        });

        view.findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            currentMonth = currentMonth.minusMonths(1);
            updateCalendar();
        });
        view.findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            currentMonth = currentMonth.plusMonths(1);
            updateCalendar();
        });

        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), list -> updateCalendar());

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            if (assets != null) {
                cachedAssets = assets;
                if (currentDetailAdapter != null) {
                    currentDetailAdapter.setAssets(assets);
                }
            }
        });

        updateCalendar();
        return view;
    }

    private void switchFilterMode(int mode) {
        adapter.setFilterMode(mode);
    }

    private void updateCalendar() {
        tvMonthTitle.setText(currentMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        List<LocalDate> days = new ArrayList<>();
        int length = currentMonth.lengthOfMonth();
        for (int i = 1; i <= length; i++) {
            days.add(currentMonth.atDay(i));
        }
        List<Transaction> allList = viewModel.getAllTransactions().getValue();
        List<Transaction> currentList = allList != null ? allList : new ArrayList<>();
        adapter.updateData(days, currentList);
        calculateMonthTotals(currentList);
    }

    private void calculateMonthTotals(List<Transaction> transactions) {
        double totalIncome = 0;
        double totalExpense = 0;
        double totalOvertime = 0;
        int year = currentMonth.getYear();
        int month = currentMonth.getMonthValue();
        for (Transaction t : transactions) {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            if (date.getYear() == year && date.getMonthValue() == month) {
                if (t.type == 1) {
                    if ("加班".equals(t.category)) {
                        totalOvertime += t.amount;
                    } else {
                        totalIncome += t.amount;
                    }
                } else {
                    totalExpense += t.amount;
                }
            }
        }
        double balance = totalIncome - totalExpense;
        tvIncome.setText(String.format("+%.2f", totalIncome));
        tvExpense.setText(String.format("-%.2f", totalExpense));
        tvOvertime.setText(String.format("+%.2f", totalOvertime));
        String sign = balance >= 0 ? "+" : "";
        tvBalance.setText(String.format("%s%.2f", sign, balance));
    }

    private void showDateDetailDialog(LocalDate date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_transaction_list, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            DateTimeFormatter chFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA);
            tvTitle.setText(date.format(chFormatter));
        }

        RecyclerView rvList = dialogView.findViewById(R.id.rv_detail_list);
        rvList.setLayoutManager(new LinearLayoutManager(getContext()));

        TransactionListAdapter listAdapter = new TransactionListAdapter(transaction -> {
            dialog.dismiss();
            LocalDate transDate = Instant.ofEpochMilli(transaction.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(transaction, transDate);
        });
        
        listAdapter.setAssets(cachedAssets);
        currentDetailAdapter = listAdapter;
        rvList.setAdapter(listAdapter);

        List<Transaction> all = viewModel.getAllTransactions().getValue();
        if (all != null) {
            long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            List<Transaction> dayList = all.stream()
                    .filter(t -> t.date >= start && t.date < end)
                    .collect(Collectors.toList());
            listAdapter.setTransactions(dayList);
        }

        Button btnAddNormal = dialogView.findViewById(R.id.btn_add_transaction);
        if (btnAddNormal != null) {
            btnAddNormal.setOnClickListener(v -> {
                dialog.dismiss();
                showAddOrEditDialog(null, date);
            });
        }
        Button btnAddOvertime = dialogView.findViewById(R.id.btn_add_overtime);
        if (btnAddOvertime != null) {
            btnAddOvertime.setOnClickListener(v -> {
                dialog.dismiss();
                showOvertimeDialog(date);
            });
        }
        Button btnClose = dialogView.findViewById(R.id.btn_close_dialog);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> currentDetailAdapter = null);

        dialog.show();
    }

    private void showOvertimeDialog(LocalDate date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_add_overtime, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etRate = view.findViewById(R.id.et_hourly_rate);
        EditText etDuration = view.findViewById(R.id.et_duration);
        TextView tvResult = view.findViewById(R.id.tv_calculated_amount);
        Button btnSave = view.findViewById(R.id.btn_save_overtime);
        Button btnCancel = view.findViewById(R.id.btn_cancel_overtime);

        etRate.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                calculateOvertime(etRate, etDuration, tvResult);
            }
        };
        etRate.addTextChangedListener(watcher);
        etDuration.addTextChangedListener(watcher);

        btnSave.setOnClickListener(v -> {
            String rateStr = etRate.getText().toString();
            String durationStr = etDuration.getText().toString();

            if (!rateStr.isEmpty() && !durationStr.isEmpty()) {
                double rate = Double.parseDouble(rateStr);
                double duration = Double.parseDouble(durationStr);
                double totalAmount = rate * duration;

                long ts = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

                Transaction transaction = new Transaction(ts, 1, "加班", totalAmount);
                transaction.note = String.format("时长: %s小时, 时薪: %s", durationStr, rateStr);

                viewModel.addTransaction(transaction);
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
    
    private void calculateOvertime(EditText etRate, EditText etDuration, TextView tvResult) {
        try {
            String r = etRate.getText().toString();
            String d = etDuration.getText().toString();
            if (!r.isEmpty() && !d.isEmpty()) {
                double rate = Double.parseDouble(r);
                double duration = Double.parseDouble(d);
                tvResult.setText(String.format("预计收入: %.2f", rate * duration));
            } else {
                tvResult.setText("预计收入: 0.00");
            }
        } catch (Exception e) {
            tvResult.setText("预计收入: 0.00");
        }
    }

    private void showAddOrEditDialog(Transaction existingTransaction, LocalDate date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvDate = dialogView.findViewById(R.id.tv_dialog_date);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        RadioGroup rgCategory = dialogView.findViewById(R.id.rg_category);
        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        EditText etCustomCategory = dialogView.findViewById(R.id.et_custom_category);
        EditText etRemark = dialogView.findViewById(R.id.et_remark);
        EditText etNote = dialogView.findViewById(R.id.et_note);
        Spinner spAsset = dialogView.findViewById(R.id.sp_asset);
        
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        
        // 新增：获取撤回按钮
        TextView tvRevoke = dialogView.findViewById(R.id.tv_revoke);

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        AssistantConfig config = new AssistantConfig(requireContext());
        boolean isAssetEnabled = config.isAssetsEnabled();

        List<AssetAccount> assetList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.item_spinner_dropdown);

        if (isAssetEnabled) {
            spAsset.setVisibility(View.VISIBLE);

            AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
            noAsset.id = 0;
            assetList.add(noAsset);

            adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            spAsset.setAdapter(adapter);

            viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
                assetList.clear();
                assetList.add(noAsset);
                if (assets != null) {
                    for (AssetAccount a : assets) {
                        if (a.type == 0) {
                            assetList.add(a);
                        }
                    }
                }
                List<String> names = assetList.stream().map(a -> a.name).collect(Collectors.toList());
                adapter.clear();
                adapter.addAll(names);
                adapter.notifyDataSetChanged();

                if (existingTransaction != null && existingTransaction.assetId != 0) {
                    for (int i = 0; i < assetList.size(); i++) {
                        if (assetList.get(i).id == existingTransaction.assetId) {
                            spAsset.setSelection(i);
                            break;
                        }
                    }
                } else if (existingTransaction == null) {
                    int defaultAssetId = config.getDefaultAssetId();
                    if (defaultAssetId != -1) {
                        for (int i = 0; i < assetList.size(); i++) {
                            if (assetList.get(i).id == defaultAssetId) {
                                spAsset.setSelection(i);
                                break;
                            }
                        }
                    }
                }
            });
        } else {
            spAsset.setVisibility(View.GONE);
        }

        final java.util.Calendar calendar = java.util.Calendar.getInstance();
        if (existingTransaction != null) {
            calendar.setTimeInMillis(existingTransaction.date);
        } else {
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(java.util.Calendar.YEAR, date.getYear());
            calendar.set(java.util.Calendar.MONTH, date.getMonthValue() - 1);
            calendar.set(java.util.Calendar.DAY_OF_MONTH, date.getDayOfMonth());
        }

        Runnable updateDateDisplay = () -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA);
            tvDate.setText(sdf.format(calendar.getTime()));
        };
        updateDateDisplay.run();

        tvDate.setOnClickListener(v -> {
            long currentMillis = calendar.getTimeInMillis();
            long offset = TimeZone.getDefault().getOffset(currentMillis);

            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("选择日期")
                    .setSelection(currentMillis + offset)
                    .setPositiveButtonText("确认")
                    .setNegativeButtonText("取消")
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                java.util.Calendar selectedCal = java.util.Calendar.getInstance();
                long correctMillis = selection - TimeZone.getDefault().getOffset(selection);
                selectedCal.setTimeInMillis(correctMillis);

                calendar.set(java.util.Calendar.YEAR, selectedCal.get(java.util.Calendar.YEAR));
                calendar.set(java.util.Calendar.MONTH, selectedCal.get(java.util.Calendar.MONTH));
                calendar.set(java.util.Calendar.DAY_OF_MONTH, selectedCal.get(java.util.Calendar.DAY_OF_MONTH));

                MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_24H)
                        .setHour(calendar.get(java.util.Calendar.HOUR_OF_DAY))
                        .setMinute(calendar.get(java.util.Calendar.MINUTE))
                        .setTitleText("选择时间")
                        .setPositiveButtonText("确认")
                        .setNegativeButtonText("取消")
                        .build();

                timePicker.addOnPositiveButtonClickListener(view -> {
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, timePicker.getHour());
                    calendar.set(java.util.Calendar.MINUTE, timePicker.getMinute());
                    updateDateDisplay.run();
                });

                timePicker.show(getParentFragmentManager(), "time_picker");
            });

            datePicker.show(getParentFragmentManager(), "date_picker");
        });

        rgCategory.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_custom) {
                etCustomCategory.setVisibility(View.VISIBLE);
                etCustomCategory.requestFocus();
            } else {
                etCustomCategory.setVisibility(View.GONE);
            }
        });

        rgType.setOnCheckedChangeListener((g, id) -> {
            boolean isExpense = (id == R.id.rb_expense);
            rgCategory.setVisibility(isExpense ? View.VISIBLE : View.GONE);
            if (isExpense) {
                etCustomCategory.setVisibility(rgCategory.getCheckedRadioButtonId() == R.id.rb_custom ? View.VISIBLE : View.GONE);
            } else {
                etCustomCategory.setVisibility(View.GONE);
            }
        });

        if (existingTransaction != null) {
            btnSave.setText("保存修改");
            etAmount.setText(String.valueOf(existingTransaction.amount));
            if (existingTransaction.remark != null) etRemark.setText(existingTransaction.remark);
            if (existingTransaction.note != null) etNote.setText(existingTransaction.note);

            if (existingTransaction.type == 1) {
                rgType.check(R.id.rb_income);
            } else {
                rgType.check(R.id.rb_expense);
                boolean isStandardCategory = false;
                for (int i = 0; i < rgCategory.getChildCount(); i++) {
                    View child = rgCategory.getChildAt(i);
                    if (child instanceof RadioButton) {
                        if (((RadioButton) child).getText().toString().equals(existingTransaction.category)) {
                            ((RadioButton) child).setChecked(true);
                            isStandardCategory = true;
                            break;
                        }
                    }
                }
                if (!isStandardCategory) {
                    rgCategory.check(R.id.rb_custom);
                    etCustomCategory.setText(existingTransaction.category);
                }
            }

            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("确认删除")
                        .setMessage("确定要删除这条记录吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            viewModel.deleteTransaction(existingTransaction);
                            dialog.dismiss();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            // --- 新增：撤回按钮逻辑 ---
            tvRevoke.setVisibility(View.VISIBLE);
            tvRevoke.setOnClickListener(v -> {
                showRevokeDialog(existingTransaction, dialog);
            });
            
        } else {
            btnSave.setText("保存");
            btnDelete.setVisibility(View.GONE);
            // 新建模式下不显示撤回
            tvRevoke.setVisibility(View.GONE); 
            SimpleDateFormat noteSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            etNote.setText(noteSdf.format(calendar.getTime()) + " manual");
        }

        btnSave.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString();
            if (!amountStr.isEmpty()) {
                double amount = Double.parseDouble(amountStr);
                int type = rgType.getCheckedRadioButtonId() == R.id.rb_income ? 1 : 0;
                String category = "收入";
                if (type == 0) {
                    int checkedId = rgCategory.getCheckedRadioButtonId();
                    if (checkedId == R.id.rb_custom) {
                        category = etCustomCategory.getText().toString().trim();
                        if (category.isEmpty()) {
                            Toast.makeText(getContext(), "请输入自定义分类", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else if (checkedId != -1) {
                        category = ((RadioButton) dialogView.findViewById(checkedId)).getText().toString();
                    } else {
                        category = "其他";
                    }
                }
                String userRemark = etRemark.getText().toString().trim();
                String noteContent = etNote.getText().toString().trim();
                long ts = calendar.getTimeInMillis();

                int selectedAssetId = 0;
                if (isAssetEnabled) {
                    int selectedPos = spAsset.getSelectedItemPosition();
                    if (selectedPos >= 0 && selectedPos < assetList.size()) {
                        selectedAssetId = assetList.get(selectedPos).id;
                    }
                }

                if (existingTransaction == null) {
                    Transaction t = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    t.assetId = selectedAssetId;
                    viewModel.addTransaction(t);

                    if (selectedAssetId != 0) {
                         for (AssetAccount asset : assetList) {
                             if (asset.id == selectedAssetId) {
                                 if (type == 1) asset.amount += amount;
                                 else asset.amount -= amount;
                                 viewModel.updateAsset(asset);
                                 break;
                             }
                         }
                    }
                } else {
                    Transaction updateT = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    updateT.id = existingTransaction.id;
                    updateT.assetId = selectedAssetId;
                    viewModel.updateTransaction(updateT);
                }
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    // --- 新增：显示撤回记录对话框 ---
    private void showRevokeDialog(Transaction transaction, AlertDialog parentDialog) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_revoke_transaction, null);
        builder.setView(view);
        AlertDialog revokeDialog = builder.create();
        if (revokeDialog.getWindow() != null) revokeDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Spinner spRevokeAsset = view.findViewById(R.id.sp_revoke_asset);
        Button btnCancel = view.findViewById(R.id.btn_revoke_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_revoke_confirm);

        List<AssetAccount> assetList = new ArrayList<>();
        // 不关联资产选项
        AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
        noAsset.id = 0;
        
        // 资产配置
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spRevokeAsset.setAdapter(adapter);

        // 加载资产数据
        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            assetList.clear();
            assetList.add(noAsset);
            if (assets != null) {
                for (AssetAccount a : assets) {
                    if (a.type == 0) {
                        assetList.add(a);
                    }
                }
            }
            List<String> names = assetList.stream().map(a -> a.name).collect(Collectors.toList());
            adapter.clear();
            adapter.addAll(names);
            adapter.notifyDataSetChanged();

            // 默认选中当前账单关联的资产
            int targetIndex = 0;
            if (transaction.assetId != 0) {
                for (int i = 0; i < assetList.size(); i++) {
                    if (assetList.get(i).id == transaction.assetId) {
                        targetIndex = i;
                        break;
                    }
                }
            }
            spRevokeAsset.setSelection(targetIndex);
        });

        btnCancel.setOnClickListener(v -> revokeDialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            int selectedPos = spRevokeAsset.getSelectedItemPosition();
            if (selectedPos >= 0 && selectedPos < assetList.size()) {
                AssetAccount selectedAsset = assetList.get(selectedPos);
                
                // 执行撤回逻辑
                viewModel.revokeTransaction(transaction, selectedAsset.id);
                
                String msg = selectedAsset.id == 0 ? "已撤回记录（无资产变动）" : "已撤回并退款至 " + selectedAsset.name;
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                
                revokeDialog.dismiss();
                if (parentDialog != null && parentDialog.isShowing()) {
                    parentDialog.dismiss();
                }
            }
        });

        revokeDialog.show();
    }
    
    private static class DecimalDigitsInputFilter implements InputFilter {
        private final Pattern mPattern;
        public DecimalDigitsInputFilter(int digitsAfterZero) {
            mPattern = Pattern.compile("[0-9]*+((\\.[0-9]{0," + (digitsAfterZero - 1) + "})?)||(\\.)?");
        }
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            String replacement = source.subSequence(start, end).toString();
            String newVal = dest.subSequence(0, dstart).toString() + replacement + dest.subSequence(dend, dest.length()).toString();
            Matcher matcher = mPattern.matcher(newVal);
            if (!matcher.matches()) {
                if (newVal.contains(".")) {
                    int index = newVal.indexOf(".");
                    if (newVal.length() - index - 1 > 2) return "";
                }
            }
            return null;
        }
    }
}