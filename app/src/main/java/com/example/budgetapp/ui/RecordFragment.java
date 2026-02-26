package com.example.budgetapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
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
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.budgetapp.database.RenewalItem;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.view.ContextThemeWrapper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;
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
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RecordFragment extends Fragment {
    private AssistantConfig assistantConfig;
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
        assistantConfig = new AssistantConfig(requireContext()); // 初始化配置类

        if (currentMonth == null) {
            currentMonth = YearMonth.now();
        }

        initGestureDetector();

        // 初始化设置菜单按钮
        ImageButton btnSettings = view.findViewById(R.id.btn_settings_menu);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), SettingsActivity.class);
                startActivity(intent);
            });
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

        FloatingActionButton btnQuickRecord = view.findViewById(R.id.btn_quick_record);
        if (btnQuickRecord != null) {
            btnQuickRecord.setOnClickListener(v -> showDateDetailDialog(LocalDate.now()));
        }

        RecyclerView recyclerView = view.findViewById(R.id.calendar_recycler);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 7) {
            @Override
            public boolean canScrollVertically() { return false; }
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

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int defaultMode = prefs.getInt("default_record_mode", 0);
        switchFilterMode(defaultMode);

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

        // 确保实时观察所有交易记录，包含自动生成的账单
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), list -> {
            // 强制刷新日历统计，确保“自动续费”或“关键字记账”产生的记录立即显示
            updateCalendar();

            // 如果当前打开了详情对话框，需要实时刷新对话框内部的列表适配器
            if (currentDetailAdapter != null && selectedDate != null) {
                long start = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long end = selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                List<Transaction> dayList = list.stream()
                        .filter(t -> t.date >= start && t.date < end)
                        .collect(Collectors.toList());
                currentDetailAdapter.setTransactions(dayList); // 关键：更新详情列表
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
        // 检查并执行自动扣费
        checkAutoRenewalDeduction();
    }

    private void checkAutoRenewalDeduction() {
        List<RenewalItem> renewalList = assistantConfig.getRenewalList(); // 获取多项续费列表
        if (renewalList.isEmpty()) return;

        LocalDate today = LocalDate.now();
        String todayStr = today.toString();
        String lastCheckDate = assistantConfig.getLastRenewalDate();

        // 防止当日重复执行
        if (todayStr.equals(lastCheckDate)) return;

        int defaultAssetId = assistantConfig.getDefaultAssetId();
        boolean hasExecuted = false;

        for (RenewalItem item : renewalList) {
            boolean shouldDeduct = false;
            if ("Month".equals(item.period)) {
                shouldDeduct = (today.getDayOfMonth() == item.day);
            } else if ("Year".equals(item.period)) {
                shouldDeduct = (today.getMonthValue() == item.month && today.getDayOfMonth() == item.day);
            }

            if (shouldDeduct) {
                executeAutoDeduction(item, defaultAssetId, todayStr);
                hasExecuted = true;
            }
        }

        // 标记今日已完成扣费检查
        assistantConfig.setLastRenewalDate(todayStr);
    }

    private void executeAutoDeduction(RenewalItem item, int assetId, String todayStr) {
        double amount = item.amount;
        String object = item.object;
        long timestamp = System.currentTimeMillis();

        Transaction t = new Transaction(timestamp, 0, "自动续费", amount, object, "系统自动扣费");
        t.assetId = assetId != -1 ? assetId : 0;

        viewModel.addTransaction(t);

        // 如果关联了资产，更新资产/负债余额
        if (t.assetId != 0) {
            viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
                if (assets != null) {
                    for (AssetAccount a : assets) {
                        if (a.id == t.assetId) {
                            if (a.type == 0) {
                                a.amount -= amount;
                            } else if (a.type == 1) { // 若关联信用卡等负债，自动扣费属于支出，负债增加
                                a.amount += amount;
                            }
                            viewModel.updateAsset(a);
                            break;
                        }
                    }
                }
            });
        }

        Toast.makeText(getContext(), "已自动扣除: " + object, Toast.LENGTH_SHORT).show();
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

        // 核心修改：将续费项目列表同步给日历适配器，用于渲染红框标识
        adapter.setRenewalItems(assistantConfig.getRenewalList());

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
            tvTitle.setText(date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA)));
        }

        RecyclerView rvList = dialogView.findViewById(R.id.rv_detail_list);
        rvList.setLayoutManager(new LinearLayoutManager(getContext()));

        TransactionListAdapter listAdapter = new TransactionListAdapter(transaction -> {
            // 如果是预览账单，点击提示不可编辑
            if ("PREVIEW_BILL".equals(transaction.remark)) {
                Toast.makeText(getContext(), "待扣费账单：到达日期后将自动执行", Toast.LENGTH_SHORT).show();
                return;
            }
            LocalDate transDate = Instant.ofEpochMilli(transaction.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(transaction, transDate);
        });

        listAdapter.setAssets(cachedAssets);
        currentDetailAdapter = listAdapter;
        rvList.setAdapter(listAdapter);

        // 1. 获取数据库中当天的真实记录
        List<Transaction> all = viewModel.getAllTransactions().getValue();
        List<Transaction> dayList = new ArrayList<>();
        if (all != null) {
            long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            dayList = all.stream()
                    .filter(t -> t.date >= start && t.date < end)
                    .collect(Collectors.toList());
        }

        // 2. 核心逻辑：匹配续费项并生成预览账单
        List<RenewalItem> renewals = assistantConfig.getRenewalList();
        for (RenewalItem item : renewals) {
            boolean matchesDate = false;
            if ("Month".equals(item.period)) {
                matchesDate = (date.getDayOfMonth() == item.day);
            } else if ("Year".equals(item.period)) {
                matchesDate = (date.getMonthValue() == item.month && date.getDayOfMonth() == item.day);
            }

            if (matchesDate) {
                // 检查数据库中是否已存在该笔自动续费的正式记录
                String objectName = item.object;
                boolean alreadyExecuted = dayList.stream().anyMatch(t ->
                        "自动续费".equals(t.category) && objectName.equals(t.note));

                if (!alreadyExecuted) {
                    // 创建虚拟预览账单
                    Transaction preview = new Transaction();
                    preview.amount = item.amount;
                    preview.type = 0; // 支出
                    preview.category = "自动续费";
                    preview.note = objectName;
                    preview.remark = "PREVIEW_BILL"; // 预览标识
                    preview.date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    dayList.add(preview);
                }
            }
        }

        // === 新增：计算当日汇总并设置彩色文本 ===
        TextView tvSummary = dialogView.findViewById(R.id.tv_dialog_summary);
        if (tvSummary != null) {
            double dayIncome = 0;
            double dayExpense = 0;

            // 遍历真实的记录计算收支 (不包含虚拟的预览账单)
            for (Transaction t : dayList) {
                if ("PREVIEW_BILL".equals(t.remark)) continue;
                if (t.type == 1) {
                    dayIncome += t.amount;
                } else {
                    dayExpense += t.amount;
                }
            }

            if (dayIncome == 0 && dayExpense == 0) {
                // 没有数据，隐藏
                tvSummary.setVisibility(View.GONE);
            } else {
                tvSummary.setVisibility(View.VISIBLE);
                double dayBalance = dayIncome - dayExpense;

                // 准备颜色 (主题色为 app_yellow)
                int colorExpense = ContextCompat.getColor(getContext(), R.color.expense_green);
                int colorIncome = ContextCompat.getColor(getContext(), R.color.income_red);
                int colorBalance = ContextCompat.getColor(getContext(), R.color.app_yellow);

                // 构建 Spannable 文本
                android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();

                // 支出 (绿色)
                if (dayExpense > 0) {
                    String expStr = String.format(Locale.CHINA, "支出: %.2f", dayExpense);
                    int start = ssb.length();
                    ssb.append(expStr);
                    ssb.setSpan(new android.text.style.ForegroundColorSpan(colorExpense), start, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.append("    "); // 增加间距
                }

                // 收入 (红色)
                if (dayIncome > 0) {
                    String incStr = String.format(Locale.CHINA, "收入: %.2f", dayIncome);
                    int start = ssb.length();
                    ssb.append(incStr);
                    ssb.setSpan(new android.text.style.ForegroundColorSpan(colorIncome), start, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.append("    "); // 增加间距
                }

                // 结余 (整体主题色)
                String balStr = String.format(Locale.CHINA, "结余: %.2f", dayBalance);
                int startBal = ssb.length();
                ssb.append(balStr);
                ssb.setSpan(new android.text.style.ForegroundColorSpan(colorBalance), startBal, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);


                tvSummary.setText(ssb);
            }
        }
        // ===========================================

        listAdapter.setTransactions(dayList);

        dialogView.findViewById(R.id.btn_add_transaction).setOnClickListener(v -> showAddOrEditDialog(null, date));
        dialogView.findViewById(R.id.btn_add_overtime).setOnClickListener(v -> { dialog.dismiss(); showOvertimeDialog(date); });
        dialogView.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
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

    // 覆写 showAddOrEditDialog 方法
    private void showAddOrEditDialog(Transaction existingTransaction, LocalDate date) {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvDate = dialogView.findViewById(R.id.tv_dialog_date);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        RecyclerView rvCategory = dialogView.findViewById(R.id.rv_category);
        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        Button btnCurrency = dialogView.findViewById(R.id.btn_currency);

        EditText etCustomCategory = dialogView.findViewById(R.id.et_custom_category);
        EditText etRemark = dialogView.findViewById(R.id.et_remark);
        EditText etNote = dialogView.findViewById(R.id.et_note);
        Spinner spAsset = dialogView.findViewById(R.id.sp_asset);

        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        TextView tvRevoke = dialogView.findViewById(R.id.tv_revoke);

        com.google.android.material.button.MaterialButton btnTakePhoto = dialogView.findViewById(R.id.btn_take_photo);
        com.google.android.material.button.MaterialButton btnViewPhoto = dialogView.findViewById(R.id.btn_view_photo);

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
        boolean isPhotoBackupEnabled = prefs.getBoolean("enable_photo_backup", false);

        if (isCurrencyEnabled) {
            btnCurrency.setVisibility(View.VISIBLE);
            if (existingTransaction != null && existingTransaction.currencySymbol != null && !existingTransaction.currencySymbol.isEmpty()) {
                btnCurrency.setText(existingTransaction.currencySymbol);
            } else {
                String defaultSymbol = prefs.getString("default_currency_symbol", "¥");
                btnCurrency.setText(defaultSymbol);
            }
            btnCurrency.setOnClickListener(v -> showCurrencySelectDialog(btnCurrency));
        } else {
            btnCurrency.setVisibility(View.GONE);
        }

        final String[] currentPhotoPath = { existingTransaction != null ? existingTransaction.photoPath : "" };

        Runnable updatePhotoButtons = () -> {
            if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                btnViewPhoto.setVisibility(View.VISIBLE);
            } else {
                btnViewPhoto.setVisibility(View.GONE);
            }
        };

        if (isPhotoBackupEnabled) {
            btnTakePhoto.setVisibility(View.VISIBLE);
            updatePhotoButtons.run();

            btnTakePhoto.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), PhotoActionActivity.class);
                intent.putExtra(PhotoActionActivity.EXTRA_RECEIVER, new android.os.ResultReceiver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode == 1 && resultData != null) {
                            String uri = resultData.getString(PhotoActionActivity.KEY_RESULT_URI);
                            currentPhotoPath[0] = uri;
                            updatePhotoButtons.run();

                            if (existingTransaction != null) {
                                existingTransaction.photoPath = uri;
                                viewModel.updateTransaction(existingTransaction);
                                Toast.makeText(getContext(), "照片已添加并保存", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
                startActivity(intent);
            });

            btnViewPhoto.setOnClickListener(v -> {
                if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                    showPhotoDialog(currentPhotoPath[0]);
                }
            });

            btnViewPhoto.setOnLongClickListener(v -> {
                android.app.AlertDialog.Builder deleteBuilder = new android.app.AlertDialog.Builder(getContext());
                View deleteView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_delete_photo, null);
                deleteBuilder.setView(deleteView);

                android.app.AlertDialog deleteDialog = deleteBuilder.create();
                if (deleteDialog.getWindow() != null) {
                    deleteDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                deleteView.findViewById(R.id.btn_cancel_delete).setOnClickListener(view -> deleteDialog.dismiss());

                deleteView.findViewById(R.id.btn_confirm_delete).setOnClickListener(view -> {
                    if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                        try {
                            Uri uri = Uri.parse(currentPhotoPath[0]);
                            DocumentFile file = DocumentFile.fromSingleUri(requireContext(), uri);
                            if (file != null && file.exists()) {
                                if (file.delete()) {
                                    Toast.makeText(getContext(), "照片已彻底删除", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getContext(), "文件删除失败，仅移除引用", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(getContext(), "删除出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    currentPhotoPath[0] = "";
                    updatePhotoButtons.run();

                    if (existingTransaction != null) {
                        existingTransaction.photoPath = "";
                        viewModel.updateTransaction(existingTransaction);
                    }
                    deleteDialog.dismiss();
                });
                deleteDialog.show();
                return true;
            });

        } else {
            btnTakePhoto.setVisibility(View.GONE);
            btnViewPhoto.setVisibility(View.GONE);
        }

        List<String> expenseCategories = CategoryManager.getExpenseCategories(getContext());
        List<String> incomeCategories = CategoryManager.getIncomeCategories(getContext());

        rvCategory.setLayoutManager(new GridLayoutManager(getContext(), 5));

        final boolean[] isExpense = {true};
        final String[] selectedCategory = {expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0)};
        final String[] selectedSubCategory = {""};

        CategoryAdapter categoryAdapter = new CategoryAdapter(getContext(), expenseCategories, selectedCategory[0], category -> {
            selectedCategory[0] = category;
            selectedSubCategory[0] = "";
            if ("自定义".equals(category)) {
                etCustomCategory.setVisibility(View.VISIBLE);
                etCustomCategory.requestFocus();
            } else {
                etCustomCategory.setVisibility(View.GONE);
            }
        });

        categoryAdapter.setOnCategoryLongClickListener(category -> {
            if (CategoryManager.isSubCategoryEnabled(getContext()) && !"自定义".equals(category)) {
                if (!category.equals(selectedCategory[0])) {
                    categoryAdapter.setSelectedCategory(category);
                    selectedCategory[0] = category;
                    selectedSubCategory[0] = "";
                    etCustomCategory.setVisibility(View.GONE);
                }

                List<String> subCats = CategoryManager.getSubCategories(getContext(), category);
                AlertDialog.Builder subBuilder = new AlertDialog.Builder(getContext());
                View subCatView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_sub_category, null);
                subBuilder.setView(subCatView);
                AlertDialog subCatDialog = subBuilder.create();

                if (subCatDialog.getWindow() != null) {
                    subCatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                TextView tvTitle = subCatView.findViewById(R.id.tv_title);
                tvTitle.setText(category + " - 选择细分");
                ChipGroup cgSubCategories = subCatView.findViewById(R.id.cg_sub_categories);
                Button btnCancel = subCatView.findViewById(R.id.btn_cancel);
                TextView tvEmpty = subCatView.findViewById(R.id.tv_empty);
                View nsvContainer = subCatView.findViewById(R.id.nsv_container);

                if (subCats.isEmpty()) {
                    cgSubCategories.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    nsvContainer.setMinimumHeight(150);
                } else {
                    cgSubCategories.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);

                    String currentSelectedSub = selectedSubCategory[0];
                    int bgDefault = ContextCompat.getColor(getContext(), R.color.cat_unselected_bg);
                    int bgChecked = ContextCompat.getColor(getContext(), R.color.app_yellow);
                    int textDefault = ContextCompat.getColor(getContext(), R.color.text_primary);
                    int textChecked = ContextCompat.getColor(getContext(), R.color.cat_selected_text);

                    int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] { } };
                    ColorStateList bgStateList = new ColorStateList(states, new int[] { bgChecked, bgDefault });
                    ColorStateList textStateList = new ColorStateList(states, new int[] { textChecked, textDefault });

                    for (String subCatName : subCats) {
                        Chip chip = new Chip(getContext());
                        chip.setText(subCatName);
                        chip.setCheckable(true);
                        chip.setClickable(true);
                        chip.setChipBackgroundColor(bgStateList);
                        chip.setTextColor(textStateList);
                        chip.setChipStrokeWidth(0);
                        chip.setCheckedIconVisible(false);

                        if (subCatName.equals(currentSelectedSub)) {
                            chip.setChecked(true);
                        }

                        chip.setOnClickListener(v -> {
                            if (subCatName.equals(selectedSubCategory[0])) {
                                selectedSubCategory[0] = null;
                                Toast.makeText(getContext(), "已取消细分", Toast.LENGTH_SHORT).show();
                            } else {
                                selectedSubCategory[0] = subCatName;
                                Toast.makeText(getContext(), "已选择: " + subCatName, Toast.LENGTH_SHORT).show();
                            }
                            categoryAdapter.setSelectedCategory(category);
                            selectedCategory[0] = category;
                            etCustomCategory.setVisibility(View.GONE);
                            subCatDialog.dismiss();
                        });
                        cgSubCategories.addView(chip);
                    }
                }
                btnCancel.setOnClickListener(v -> subCatDialog.dismiss());
                subCatDialog.show();
                return true;
            }
            return false;
        });

        rvCategory.setAdapter(categoryAdapter);

        if (existingTransaction != null && existingTransaction.subCategory != null) {
            selectedSubCategory[0] = existingTransaction.subCategory;
        }

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
                        // 【修改】包含资产(0)和负债(1)
                        if (a.type == 0 || a.type == 1) {
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

        tvDate.setClickable(false);
        tvDate.setFocusable(false);

        rgType.setOnCheckedChangeListener((g, id) -> {
            boolean switchToExpense = (id == R.id.rb_expense);
            isExpense[0] = switchToExpense;
            List<String> targetCategories = switchToExpense ? expenseCategories : incomeCategories;
            String defaultCat = targetCategories.isEmpty() ? "自定义" : targetCategories.get(0);
            categoryAdapter.updateData(targetCategories);
            categoryAdapter.setSelectedCategory(defaultCat);
            selectedCategory[0] = defaultCat;
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
                AlertDialog.Builder delBuilder = new AlertDialog.Builder(getContext());
                View delView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
                delBuilder.setView(delView);
                AlertDialog delDialog = delBuilder.create();
                if (delDialog.getWindow() != null) {
                    delDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                TextView tvMsg = delView.findViewById(R.id.tv_dialog_message);
                if (tvMsg != null) {
                    tvMsg.setText("确定要删除这条记录吗？\n删除后将无法恢复。");
                }

                delView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(dv -> delDialog.dismiss());
                delView.findViewById(R.id.btn_dialog_confirm).setOnClickListener(dv -> {
                    viewModel.deleteTransaction(existingTransaction);
                    delDialog.dismiss();
                    dialog.dismiss();
                });
                delDialog.show();
            });

            tvRevoke.setVisibility(View.VISIBLE);
            tvRevoke.setOnClickListener(v -> showRevokeDialog(existingTransaction, dialog));
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

                String currencySymbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

                if (existingTransaction == null) {
                    Transaction t = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    t.assetId = selectedAssetId;
                    t.currencySymbol = currencySymbol;
                    t.subCategory = selectedSubCategory[0];
                    t.photoPath = currentPhotoPath[0];
                    viewModel.addTransaction(t);

                    if (selectedAssetId != 0) {
                        for (AssetAccount asset : assetList) {
                            if (asset.id == selectedAssetId) {
                                // 【修改】处理资产与负债的增减逻辑
                                if (asset.type == 0) { // 资产
                                    if (type == 1) asset.amount += amount;
                                    else asset.amount -= amount;
                                } else if (asset.type == 1) { // 负债
                                    if (type == 1) asset.amount -= amount; // 收入还款，负债降低
                                    else asset.amount += amount; // 支出刷卡，负债增加
                                }
                                viewModel.updateAsset(asset);
                                break;
                            }
                        }
                    }
                } else {
                    Transaction updateT = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    updateT.id = existingTransaction.id;
                    updateT.assetId = selectedAssetId;
                    updateT.currencySymbol = currencySymbol;
                    updateT.subCategory = selectedSubCategory[0];
                    updateT.photoPath = currentPhotoPath[0];
                    viewModel.updateTransaction(updateT);
                }
                dialog.dismiss();
            }
        });
        dialog.show();
    }
    // 新增：显示大图的 Dialog
    private void showPhotoDialog(String uriStr) {
        if (getContext() == null || uriStr == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        android.widget.ImageView iv = new android.widget.ImageView(getContext());
        try {
            iv.setImageURI(android.net.Uri.parse(uriStr));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            builder.setView(iv);
            builder.show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法加载图片", Toast.LENGTH_SHORT).show();
        }
    }
    // 在 showCurrencySelectDialog 方法中，直接调用工具类：
    private void showCurrencySelectDialog(Button btn) {
        // 传入 false，因为 Fragment 依附于 Activity，不是 Overlay
        com.example.budgetapp.util.CurrencyUtils.showCurrencyDialog(getContext(), btn, false);
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
                    // 【修改】同时包含资产和负债
                    if (a.type == 0 || a.type == 1) {
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

                // 1. 执行数据库撤回操作
                viewModel.revokeTransaction(transaction, selectedAsset.id);

                // 2. 如果有照片，删除物理文件
                if (transaction.photoPath != null && !transaction.photoPath.isEmpty()) {
                    try {
                        Uri uri = Uri.parse(transaction.photoPath);
                        DocumentFile file = DocumentFile.fromSingleUri(requireContext(), uri);
                        if (file != null && file.exists()) {
                            boolean deleted = file.delete();
                            if (!deleted) {
                                // 如果删除失败，可以打日志
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                String msg = selectedAsset.id == 0 ? "已撤回记录（无资产变动）" : "已撤回并退回至 " + selectedAsset.name;
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