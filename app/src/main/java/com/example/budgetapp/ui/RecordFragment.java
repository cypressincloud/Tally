package com.example.budgetapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.budgetapp.util.CategoryManager; // 【新增引用】
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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

    // UI 控件
    private TextView tvMonthTitle;
    private TextView tvMonthLabel;

    private TextView tvIncome, tvExpense, tvBalance, tvOvertime;
    private LinearLayout layoutIncome, layoutExpense, layoutBalance, layoutOvertime;

    private List<AssetAccount> cachedAssets = new ArrayList<>();
    private TransactionListAdapter currentDetailAdapter;

    // 手势检测器
    private GestureDetector gestureDetector;

    // Activity Result Launcher
    private ActivityResultLauncher<Intent> yearCalendarLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        yearCalendarLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        int year = result.getData().getIntExtra("year", -1);
                        int month = result.getData().getIntExtra("month", -1);
                        if (year != -1 && month != -1) {
                            currentMonth = YearMonth.of(year, month);
                            updateCalendar();
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);

        if (currentMonth == null) {
            currentMonth = YearMonth.now();
        }

        initGestureDetector();

        // === 1. 初始化设置菜单按钮 ===
        ImageButton btnSettings = view.findViewById(R.id.btn_settings_menu);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), SettingsActivity.class);
                startActivity(intent);
            });
            // 初始可见性检查
            SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isMinimalist = prefs.getBoolean("minimalist_mode", false);
            btnSettings.setVisibility(isMinimalist ? View.GONE : View.VISIBLE);
        }

        tvMonthTitle = view.findViewById(R.id.tv_month_title);
        tvMonthLabel = view.findViewById(R.id.tv_month_label);

        tvIncome = view.findViewById(R.id.tv_month_income);
        tvExpense = view.findViewById(R.id.tv_month_expense);
        tvBalance = view.findViewById(R.id.tv_month_balance);
        tvOvertime = view.findViewById(R.id.tv_month_overtime);

        layoutIncome = view.findViewById(R.id.layout_stat_income);
        layoutExpense = view.findViewById(R.id.layout_stat_expense);
        layoutBalance = view.findViewById(R.id.layout_stat_balance);
        layoutOvertime = view.findViewById(R.id.layout_stat_overtime);

        // 快速记账按钮逻辑
        FloatingActionButton btnQuickRecord = view.findViewById(R.id.btn_quick_record);
        if (btnQuickRecord != null) {
            btnQuickRecord.setOnClickListener(v -> {
                showDateDetailDialog(LocalDate.now());
            });
        }

        RecyclerView recyclerView = view.findViewById(R.id.calendar_recycler);

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 7) {
            @Override
            public boolean canScrollVertically() {
                return false; // 禁止垂直滑动
            }
        };
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        adapter = new CalendarAdapter(date -> {
            if (date.equals(selectedDate)) {
                showDateDetailDialog(date);
            } else {
                selectedDate = date;
                adapter.setSelectedDate(date);
            }
        });
        recyclerView.setAdapter(adapter);

        recyclerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });

        if (tvMonthLabel != null) {
            tvMonthLabel.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), YearCalendarActivity.class);
                intent.putExtra("year", currentMonth.getYear());
                yearCalendarLauncher.launch(intent);
            });
            tvMonthLabel.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return false;
            });
        }

        layoutBalance.setOnClickListener(v -> switchFilterMode(0));
        layoutIncome.setOnClickListener(v -> switchFilterMode(1));
        layoutExpense.setOnClickListener(v -> switchFilterMode(2));
        layoutOvertime.setOnClickListener(v -> switchFilterMode(3));

        tvMonthTitle.setOnClickListener(v -> showCustomDatePicker());

        view.findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            currentMonth = currentMonth.minusYears(1);
            updateCalendar();
        });
        view.findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            currentMonth = currentMonth.plusYears(1);
            updateCalendar();
        });

        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), list -> {
            updateCalendar();

            if (currentDetailAdapter != null && selectedDate != null) {
                long start = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long end = selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                List<Transaction> dayList = list.stream()
                        .filter(t -> t.date >= start && t.date < end)
                        .collect(Collectors.toList());
                currentDetailAdapter.setTransactions(dayList);
            }
        });

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

    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) {
            ImageButton btnSettings = view.findViewById(R.id.btn_settings_menu);
            if (btnSettings != null) {
                SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                boolean isMinimalist = prefs.getBoolean("minimalist_mode", false);
                btnSettings.setVisibility(isMinimalist ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void showCustomDatePicker() {
        if (getContext() == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        LocalDate baseDate = selectedDate != null ? selectedDate : currentMonth.atDay(1);
        int curYear = baseDate.getYear();
        int curMonth = baseDate.getMonthValue();
        int curDay = baseDate.getDayOfMonth();

        NumberPicker npYear = dialog.findViewById(R.id.np_year);
        NumberPicker npMonth = dialog.findViewById(R.id.np_month);
        NumberPicker npDay = dialog.findViewById(R.id.np_day);
        TextView tvPreview = dialog.findViewById(R.id.tv_date_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        if (npYear == null || npMonth == null || npDay == null || btnConfirm == null || btnCancel == null) return;

        npYear.setMinValue(2000);
        npYear.setMaxValue(2050);
        npYear.setValue(curYear);

        npMonth.setMinValue(1);
        npMonth.setMaxValue(12);
        npMonth.setValue(curMonth);

        npDay.setMinValue(1);
        int maxDays = YearMonth.of(curYear, curMonth).lengthOfMonth();
        npDay.setMaxValue(maxDays);
        npDay.setValue(curDay);

        NumberPicker.OnValueChangeListener dateChangeListener = (picker, oldVal, newVal) -> {
            int y = npYear.getValue();
            int m = npMonth.getValue();
            int newMaxDays = YearMonth.of(y, m).lengthOfMonth();
            if (npDay.getMaxValue() != newMaxDays) {
                int currentD = npDay.getValue();
                npDay.setMaxValue(newMaxDays);
                if (currentD > newMaxDays) npDay.setValue(newMaxDays);
            }
            updatePreviewText(tvPreview, y, m, npDay.getValue());
        };

        npYear.setOnValueChangedListener(dateChangeListener);
        npMonth.setOnValueChangedListener(dateChangeListener);
        npDay.setOnValueChangedListener(dateChangeListener);

        updatePreviewText(tvPreview, curYear, curMonth, curDay);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            int year = npYear.getValue();
            int month = npMonth.getValue();
            int day = npDay.getValue();

            currentMonth = YearMonth.of(year, month);
            selectedDate = LocalDate.of(year, month, day);
            updateCalendar();

            adapter.setSelectedDate(selectedDate);

            dialog.dismiss();
        });

        dialog.show();
    }

    private void updatePreviewText(TextView tv, int year, int month, int day) {
        if (tv == null) return;
        try {
            LocalDate date = LocalDate.of(year, month, day);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);
            tv.setText(date.format(formatter));
        } catch (Exception e) {
            tv.setText(year + "年" + month + "月" + day + "日");
        }
    }

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) &&
                        Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    if (diffX > 0) {
                        currentMonth = currentMonth.minusMonths(1);
                    } else {
                        currentMonth = currentMonth.plusMonths(1);
                    }
                    updateCalendar();
                    return true;
                }
                return false;
            }
        });
    }

    private void switchFilterMode(int mode) {
        adapter.setFilterMode(mode);
    }

    private void updateCalendar() {
        tvMonthTitle.setText(currentMonth.format(DateTimeFormatter.ofPattern("yyyy")));
        if (tvMonthLabel != null) {
            tvMonthLabel.setText(currentMonth.getMonthValue() + "月");
        }

        List<LocalDate> days = new ArrayList<>();
        LocalDate firstDay = currentMonth.atDay(1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue();
        int offset = dayOfWeek - 1;

        LocalDate startOfGrid = firstDay.minusDays(offset);
        for (int i = 0; i < offset; i++) {
            days.add(startOfGrid.plusDays(i));
        }

        int length = currentMonth.lengthOfMonth();
        for (int i = 1; i <= length; i++) {
            days.add(currentMonth.atDay(i));
        }

        List<Transaction> allList = viewModel.getAllTransactions().getValue();
        List<Transaction> currentList = allList != null ? allList : new ArrayList<>();

        adapter.setCurrentMonth(currentMonth);
        adapter.updateData(days, currentList);

        if (selectedDate != null && YearMonth.from(selectedDate).equals(currentMonth)) {
            adapter.setSelectedDate(selectedDate);
        }

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

        // 自动填充时薪
        AssistantConfig config = new AssistantConfig(requireContext());
        float defaultRate = 0f;
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            defaultRate = config.getHolidayOvertimeRate();
        } else {
            defaultRate = config.getWeekdayOvertimeRate();
        }

        if (defaultRate > 0) {
            etRate.setText(String.valueOf(defaultRate));
        }

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
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvDate = dialogView.findViewById(R.id.tv_dialog_date);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        // 替换为 RecyclerView
        RecyclerView rvCategory = dialogView.findViewById(R.id.rv_category); 
        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        EditText etCustomCategory = dialogView.findViewById(R.id.et_custom_category);
        EditText etRemark = dialogView.findViewById(R.id.et_remark);
        EditText etNote = dialogView.findViewById(R.id.et_note);
        Spinner spAsset = dialogView.findViewById(R.id.sp_asset);

        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        TextView tvRevoke = dialogView.findViewById(R.id.tv_revoke);

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        // 【修改】从 CategoryManager 获取分类
        List<String> expenseCategories = CategoryManager.getExpenseCategories(getContext());
        List<String> incomeCategories = CategoryManager.getIncomeCategories(getContext());

        // 配置 RecyclerView (5列)
        rvCategory.setLayoutManager(new GridLayoutManager(getContext(), 5));
        
        final boolean[] isExpense = {true}; // 默认支出
        final String[] selectedCategory = {expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0)};

        // 创建 Adapter
        CategoryAdapter categoryAdapter = new CategoryAdapter(getContext(), expenseCategories, selectedCategory[0], category -> {
            selectedCategory[0] = category;
            if ("自定义".equals(category)) {
                etCustomCategory.setVisibility(View.VISIBLE);
                etCustomCategory.requestFocus();
            } else {
                etCustomCategory.setVisibility(View.GONE);
            }
        });
        rvCategory.setAdapter(categoryAdapter);

        // === 资产配置（保留原逻辑）===
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
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker().setTitleText("选择日期").setSelection(currentMillis + offset).setPositiveButtonText("确认").setNegativeButtonText("取消").build();
            datePicker.addOnPositiveButtonClickListener(selection -> {
                java.util.Calendar selectedCal = java.util.Calendar.getInstance();
                long correctMillis = selection - TimeZone.getDefault().getOffset(selection);
                selectedCal.setTimeInMillis(correctMillis);
                calendar.set(java.util.Calendar.YEAR, selectedCal.get(java.util.Calendar.YEAR));
                calendar.set(java.util.Calendar.MONTH, selectedCal.get(java.util.Calendar.MONTH));
                calendar.set(java.util.Calendar.DAY_OF_MONTH, selectedCal.get(java.util.Calendar.DAY_OF_MONTH));
                MaterialTimePicker timePicker = new MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(calendar.get(java.util.Calendar.HOUR_OF_DAY)).setMinute(calendar.get(java.util.Calendar.MINUTE)).setTitleText("选择时间").setPositiveButtonText("确认").setNegativeButtonText("取消").build();
                timePicker.addOnPositiveButtonClickListener(pickerView -> {
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, timePicker.getHour());
                    calendar.set(java.util.Calendar.MINUTE, timePicker.getMinute());
                    updateDateDisplay.run();
                });
                timePicker.show(getParentFragmentManager(), "time_picker");
            });
            datePicker.show(getParentFragmentManager(), "date_picker");
        });

        // 监听收入/支出切换
        rgType.setOnCheckedChangeListener((g, id) -> {
            boolean switchToExpense = (id == R.id.rb_expense);
            isExpense[0] = switchToExpense;
            
            // 切换数据源
            List<String> targetCategories = switchToExpense ? expenseCategories : incomeCategories;
            
            // 【修改】安全获取第一个元素
            String defaultCat = targetCategories.isEmpty() ? "自定义" : targetCategories.get(0);
            
            // 更新 Adapter
            categoryAdapter.updateData(targetCategories);
            categoryAdapter.setSelectedCategory(defaultCat);
            selectedCategory[0] = defaultCat;

            // 重置输入框状态
            if ("自定义".equals(defaultCat)) {
                etCustomCategory.setVisibility(View.VISIBLE);
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
                isExpense[0] = false;
                categoryAdapter.updateData(incomeCategories);
            } else {
                rgType.check(R.id.rb_expense);
                isExpense[0] = true;
                categoryAdapter.updateData(expenseCategories);
            }

            // 回显分类
            String currentCat = existingTransaction.category;
            List<String> currentList = isExpense[0] ? expenseCategories : incomeCategories;
            
            if (currentList.contains(currentCat)) {
                categoryAdapter.setSelectedCategory(currentCat);
                selectedCategory[0] = currentCat;
                etCustomCategory.setVisibility(View.GONE);
            } else {
                categoryAdapter.setSelectedCategory("自定义");
                selectedCategory[0] = "自定义";
                etCustomCategory.setVisibility(View.VISIBLE);
                etCustomCategory.setText(currentCat);
            }

            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext()).setTitle("确认删除").setMessage("确定要删除这条记录吗？").setPositiveButton("删除", (d, w) -> {
                    viewModel.deleteTransaction(existingTransaction);
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
            });

            tvRevoke.setVisibility(View.VISIBLE);
            tvRevoke.setOnClickListener(v -> {
                showRevokeDialog(existingTransaction, dialog);
            });

        } else {
            btnSave.setText("保存");
            btnDelete.setVisibility(View.GONE);
            tvRevoke.setVisibility(View.GONE);
            SimpleDateFormat noteSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            etNote.setText(noteSdf.format(calendar.getTime()) + " manual");
        }

        btnSave.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString();
            if (!amountStr.isEmpty()) {
                double amount = Double.parseDouble(amountStr);
                int type = rgType.getCheckedRadioButtonId() == R.id.rb_income ? 1 : 0;
                
                // 获取分类
                String category = selectedCategory[0];
                if ("自定义".equals(category)) {
                    category = etCustomCategory.getText().toString().trim();
                    if (category.isEmpty()) {
                        Toast.makeText(getContext(), "请输入自定义分类", Toast.LENGTH_SHORT).show();
                        return;
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
        AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
        noAsset.id = 0;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spRevokeAsset.setAdapter(adapter);

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