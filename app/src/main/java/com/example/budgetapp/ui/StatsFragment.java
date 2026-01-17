package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.NumberPicker; // 新增
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.bottomsheet.BottomSheetDialog; // 新增
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth; // 新增
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StatsFragment extends Fragment {

    // ... (成员变量保持不变)
    private FinanceViewModel viewModel;
    private LineChart lineChart;
    private PieChart pieChart;
    private RadioGroup rgTimeScope;
    private TextView tvDateRange;
    
    private TextView tvSummaryTitle;
    private TextView tvSummaryContent;

    private ScrollView scrollView;
    private GestureDetector gestureDetector;
    private float touchStartX, touchStartY;
    private boolean isDirectionLocked = false;
    private boolean isHorizontalSwipe = false;
    private int touchSlop;

    private int currentMode = 2;
    private LocalDate selectedDate = LocalDate.now();
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<AssetAccount> assetList = new ArrayList<>();
    private CustomMarkerView markerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        initViews(view);
        setupGestures(); 
        setupLineChart();
        setupPieChart();

        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), list -> {
            this.allTransactions = list;
            refreshData();
        });

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            this.assetList = assets;
        });

        setupListeners(view);
        updateDateRangeDisplay();

        return view;
    }

    // ... (initViews, setupGestures, SwipeGestureListener 保持不变)

    private void initViews(View view) {
        lineChart = view.findViewById(R.id.chart_line);
        pieChart = view.findViewById(R.id.chart_pie);
        rgTimeScope = view.findViewById(R.id.rg_time_scope);
        tvDateRange = view.findViewById(R.id.tv_current_date_range);
        tvSummaryTitle = view.findViewById(R.id.tv_summary_title);
        tvSummaryContent = view.findViewById(R.id.tv_summary_content);
        scrollView = view.findViewById(R.id.scroll_view_stats);
        touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
    }
    
    private void setupGestures() {
        if (scrollView == null) return;
        gestureDetector = new GestureDetector(requireContext(), new SwipeGestureListener());
        scrollView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getX();
                    touchStartY = event.getY();
                    isDirectionLocked = false;
                    isHorizontalSwipe = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!isDirectionLocked) {
                        float dx = Math.abs(event.getX() - touchStartX);
                        float dy = Math.abs(event.getY() - touchStartY);
                        if (dx > touchSlop || dy > touchSlop) {
                            isDirectionLocked = true;
                            if (dx > dy) isHorizontalSwipe = true;
                            else isHorizontalSwipe = false;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDirectionLocked = false;
                    isHorizontalSwipe = false;
                    break;
            }
            return isDirectionLocked && isHorizontalSwipe;
        });
    }

    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;
        @Override
        public boolean onDown(@NonNull MotionEvent e) { return true; }
        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) changeDate(-1);
                    else changeDate(1);
                    return true;
                }
            }
            return false;
        }
    }

    private void setupListeners(View view) {
        ImageButton btnPrev = view.findViewById(R.id.btn_prev);
        ImageButton btnNext = view.findViewById(R.id.btn_next);

        rgTimeScope.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_year) currentMode = 0;
            else if (checkedId == R.id.rb_month) currentMode = 1;
            else if (checkedId == R.id.rb_week) currentMode = 2;

            updateDateRangeDisplay();
            refreshData();
        });

        btnPrev.setOnClickListener(v -> changeDate(-1));
        btnNext.setOnClickListener(v -> changeDate(1));

        // 【修改】这里改为调用自定义的 showCustomDatePicker
        tvDateRange.setOnClickListener(v -> showCustomDatePicker());

        tvDateRange.setOnLongClickListener(v -> {
            Intent intent = new Intent(requireContext(), AssistantManagerActivity.class);
            startActivity(intent);
            return true;
        });
    }

    // ... (changeDate, updateDateRangeDisplay 保持不变)
    
    private void changeDate(int offset) {
        if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
        else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
        else selectedDate = selectedDate.plusWeeks(offset);
        updateDateRangeDisplay();
        refreshData();
    }

    private void updateDateRangeDisplay() {
        if (currentMode == 0) {
            // 年视图保持不变
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年")));
        } else if (currentMode == 1) {
            // 月视图保持不变
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        } else {
            // === 修改周视图的显示逻辑 ===
            WeekFields weekFields = WeekFields.of(Locale.CHINA);
            String yearMonthStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月"));
            int weekOfMonth = selectedDate.get(weekFields.weekOfMonth());

            LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            // 格式化为 "M月d日" (例如: 5月20日)
            DateTimeFormatter rangeFormatter = DateTimeFormatter.ofPattern("M月d日");
            String startStr = startOfWeek.format(rangeFormatter);
            String endStr = endOfWeek.format(rangeFormatter);

            // 第一行标题
            String title = String.format(Locale.CHINA, "%s 第%d周", yearMonthStr, weekOfMonth);
            // 第二行日期范围
            String subtitle = String.format(Locale.CHINA, "%s - %s", startStr, endStr);

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append(title);
            ssb.append("\n"); // 换行

            int startSubtitle = ssb.length();
            ssb.append(subtitle);

            // 设置第二行为浅色 (text_secondary)
            if (getContext() != null) {
                int secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
                ssb.setSpan(new ForegroundColorSpan(secondaryColor), startSubtitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                // (可选) 稍微缩小第二行字体，例如 13sp，让层次更分明
                ssb.setSpan(new AbsoluteSizeSpan(13, true), startSubtitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            tvDateRange.setText(ssb);
        }
    }

    // 【新增】替换原有的 showDatePicker
    private void showCustomDatePicker() {
        if (getContext() == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);

        // 设置悬浮背景透明 (关键：去除默认白色方块背景，显示圆角和间距)
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        // 获取初始日期
        int curYear = selectedDate.getYear();
        int curMonth = selectedDate.getMonthValue();
        int curDay = selectedDate.getDayOfMonth();

        NumberPicker npYear = dialog.findViewById(R.id.np_year);
        NumberPicker npMonth = dialog.findViewById(R.id.np_month);
        NumberPicker npDay = dialog.findViewById(R.id.np_day);
        TextView tvPreview = dialog.findViewById(R.id.tv_date_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        if (npYear == null || npMonth == null || npDay == null || btnConfirm == null || btnCancel == null) return;

        // 配置滚轮
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

        // 联动逻辑
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
        
        // 初始化预览
        updatePreviewText(tvPreview, curYear, curMonth, curDay);

        // 按钮事件
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            int year = npYear.getValue();
            int month = npMonth.getValue();
            int day = npDay.getValue();
            
            selectedDate = LocalDate.of(year, month, day);
            
            // 更新显示和图表
            updateDateRangeDisplay();
            refreshData();
            
            dialog.dismiss();
        });

        dialog.show();
    }

    // 【新增】辅助方法：更新日期预览文字
    private void updatePreviewText(TextView tv, int year, int month, int day) {
        if (tv == null) return;
        try {
            LocalDate date = LocalDate.of(year, month, day);
            // 修改点：同样改为单数字格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);
            tv.setText(date.format(formatter));
        } catch (Exception e) {
            tv.setText(year + "年" + month + "月" + day + "日");
        }
    }

    // --- 图表数据处理和绘制逻辑 (后续代码保持不变) ---
    // ...
    private void refreshData() {
        if (allTransactions == null) return;
        if (currentMode == 0) processYearlyData();
        else if (currentMode == 1) processMonthlyData();
        else processWeeklyData();
    }
    
    // ... processYearlyData, processMonthlyData, processWeeklyData, IndexExtractor, aggregateData ...
    private void processYearlyData() {
        int year = selectedDate.getYear();
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            return date.getYear() == year ? date.getMonthValue() : -1;
        }, 12, "月", null);
    }
    
    private void processMonthlyData() {
        int year = selectedDate.getYear();
        int month = selectedDate.getMonthValue();
        int daysInMonth = selectedDate.lengthOfMonth();
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            return (date.getYear() == year && date.getMonthValue() == month) ? date.getDayOfMonth() : -1;
        }, daysInMonth, "日", null);
    }

    private void processWeeklyData() {
        LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)) {
                return date.getDayOfWeek().getValue();
            }
            return -1;
        }, 7, "", new String[]{"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"});
    }

    interface IndexExtractor { int getIndex(Transaction t); }

    private void aggregateData(IndexExtractor extractor, int maxX, String suffix, String[] customLabels) {
        Map<Integer, Double> incomeMap = new HashMap<>();
        Map<Integer, Double> expenseMap = new HashMap<>();
        Map<String, Double> pieCats = new HashMap<>();

        for (Transaction t : allTransactions) {
            int index = extractor.getIndex(t);
            if (index != -1) {
                if (t.type == 1) { // 收入
                    if (!"加班".equals(t.category)) {
                        incomeMap.put(index, incomeMap.getOrDefault(index, 0.0) + t.amount);
                    }
                } else { // 支出
                    expenseMap.put(index, expenseMap.getOrDefault(index, 0.0) + t.amount);
                    pieCats.put(t.category, pieCats.getOrDefault(t.category, 0.0) + t.amount);
                }
            }
        }
        updateCharts(incomeMap, expenseMap, pieCats, maxX, suffix, customLabels);
    }

    // ... (updateCharts, updateSummarySection, generateLowSaturationColors, createLineDataSet 等其余方法保持不变)
    
    private void updateCharts(Map<Integer, Double> incomeMap, Map<Integer, Double> expenseMap,
                              Map<String, Double> pieMap, int maxX, String suffix, String[] customLabels) {
        List<Entry> inEntries = new ArrayList<>();
        List<Entry> outEntries = new ArrayList<>();
        List<Entry> netEntries = new ArrayList<>();

        for (int i = 1; i <= maxX; i++) {
            double in = incomeMap.getOrDefault(i, 0.0);
            double out = expenseMap.getOrDefault(i, 0.0);

            if (incomeMap.containsKey(i)) inEntries.add(new Entry(i, (float) in));
            if (expenseMap.containsKey(i)) outEntries.add(new Entry(i, (float) out));
            if (incomeMap.containsKey(i) || expenseMap.containsKey(i)) {
                netEntries.add(new Entry(i, (float) (in - out)));
            }
        }

        LineDataSet setIn = createLineDataSet(inEntries, "收入", R.color.income_red);
        LineDataSet setOut = createLineDataSet(outEntries, "支出", R.color.expense_green);
        LineDataSet setNet = createLineDataSet(netEntries, "净收支", R.color.app_yellow);
        setNet.enableDashedLine(10f, 5f, 0f);

        LineData lineData = new LineData(setIn, setOut, setNet);
        lineChart.setData(lineData);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setAxisMinimum(1f);
        xAxis.setAxisMaximum((float) maxX);

        if (maxX <= 12) xAxis.setLabelCount(maxX);
        else xAxis.setLabelCount(6);

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index < 1 || index > maxX) return "";
                if (customLabels != null) {
                    if (index >= 1 && index < customLabels.length) return customLabels[index];
                    return "";
                }
                return index + suffix;
            }
        });

        if (markerView != null) {
            markerView.setSourceData(incomeMap, expenseMap, customLabels != null ? "" : suffix, customLabels);
        }

        lineChart.animateX(600);
        lineChart.invalidate();

        // --- Pie Chart ---
        List<PieEntry> finalEntries = new ArrayList<>();
        List<Integer> finalColors = new ArrayList<>();

        double totalPieAmount = 0;
        for (Double val : pieMap.values()) totalPieAmount += val;

        double threshold = totalPieAmount * 0.05;
        double otherAmount = 0;

        List<Map.Entry<String, Double>> largeEntries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : pieMap.entrySet()) {
            if (entry.getValue() >= threshold) {
                largeEntries.add(entry);
            } else {
                otherAmount += entry.getValue();
            }
        }

        List<Integer> generatedColors = generateLowSaturationColors(largeEntries.size());

        for (int i = 0; i < largeEntries.size(); i++) {
            Map.Entry<String, Double> entry = largeEntries.get(i);
            finalEntries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
            finalColors.add(generatedColors.get(i));
        }

        if (otherAmount > 0) {
            finalEntries.add(new PieEntry((float) otherAmount, "其他"));
            finalColors.add(Color.LTGRAY);
        }

        PieDataSet pieSet = new PieDataSet(finalEntries, "");
        pieSet.setColors(finalColors);

        int primaryTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int secondaryLineColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        pieSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieSet.setValueLineColor(secondaryLineColor);
        pieSet.setValueLineWidth(1.0f);
        pieSet.setSliceSpace(2f);
        pieSet.setValueLinePart1OffsetPercentage(80.f);
        pieSet.setValueLinePart1Length(0.3f);
        pieSet.setValueLinePart2Length(0.8f);

        pieSet.setValueTextSize(11f);
        pieSet.setValueTextColor(primaryTextColor);

        pieSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f%%", value);
            }
        });

        PieData pieData = new PieData(pieSet);
        pieData.setValueTextSize(11f);
        pieData.setValueTextColor(primaryTextColor);

        pieChart.setData(pieData);
        pieChart.setUsePercentValues(true);
        pieChart.setExtraOffsets(25f, 10f, 25f, 10f);
        pieChart.setCenterTextColor(primaryTextColor);
        pieChart.getLegend().setTextColor(primaryTextColor);

        pieChart.animateY(800);
        pieChart.invalidate();
        
        updateSummarySection(pieMap, totalPieAmount);
    }
    
    private void updateSummarySection(Map<String, Double> pieMap, double totalAmount) {
        String scopeStr;
        if (currentMode == 0) scopeStr = "本年";
        else if (currentMode == 1) scopeStr = "本月";
        else scopeStr = "本周";
        tvSummaryTitle.setText(scopeStr + "消费");

        if (pieMap.isEmpty() || totalAmount == 0) {
            tvSummaryContent.setText("暂无消费记录");
            return;
        }

        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(pieMap.entrySet());
        Collections.sort(sortedEntries, (e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] prefixes = {"最多是", "其次是", "然后是"};
        
        int yellowColor = ContextCompat.getColor(requireContext(), R.color.app_yellow);
        int greenColor = ContextCompat.getColor(requireContext(), R.color.expense_green);
        int redColor = ContextCompat.getColor(requireContext(), R.color.income_red);

        int count = Math.min(sortedEntries.size(), 3);
        for (int i = 0; i < count; i++) {
            if (i > 0) ssb.append("\n"); 

            Map.Entry<String, Double> e = sortedEntries.get(i);
            double percent = (e.getValue() / totalAmount) * 100;

            ssb.append(prefixes[i]);
            
            String category = e.getKey();
            int startCat = ssb.length();
            ssb.append(category);
            ssb.setSpan(new ForegroundColorSpan(yellowColor), startCat, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            ssb.append(", ");
            ssb.append("占比");
            
            String percentStr = String.format(Locale.CHINA, "%.1f%%", percent);
            int startPer = ssb.length();
            ssb.append(percentStr);
            ssb.setSpan(new ForegroundColorSpan(greenColor), startPer, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            ssb.append(", ");
            ssb.append("消费");

            String amountStr = String.format(Locale.CHINA, "%.2f", e.getValue());
            int startAmt = ssb.length();
            ssb.append(amountStr);
            ssb.setSpan(new ForegroundColorSpan(redColor), startAmt, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            ssb.append("元");
        }

        ssb.append("\n");
        
        long days = 1;
        LocalDate today = LocalDate.now();
        LocalDate startOfPeriod;
        LocalDate endOfPeriod;

        if (currentMode == 0) { // Year
            startOfPeriod = selectedDate.with(TemporalAdjusters.firstDayOfYear());
            endOfPeriod = selectedDate.with(TemporalAdjusters.lastDayOfYear());
        } else if (currentMode == 1) { // Month
            startOfPeriod = selectedDate.with(TemporalAdjusters.firstDayOfMonth());
            endOfPeriod = selectedDate.with(TemporalAdjusters.lastDayOfMonth());
        } else { // Week
            startOfPeriod = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            endOfPeriod = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        }

        if (!today.isBefore(startOfPeriod) && !today.isAfter(endOfPeriod)) {
             days = ChronoUnit.DAYS.between(startOfPeriod, today) + 1;
        } else {
             days = ChronoUnit.DAYS.between(startOfPeriod, endOfPeriod) + 1;
        }

        if (days < 1) days = 1;

        double dailyAvg = totalAmount / days;

        ssb.append("共计消费");
        
        String totalStr = String.format(Locale.CHINA, "%.2f", totalAmount);
        int startTotal = ssb.length();
        ssb.append(totalStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startTotal, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        ssb.append("元, ");
        
        ssb.append("日均消费");
        
        String avgStr = String.format(Locale.CHINA, "%.2f", dailyAvg);
        int startAvg = ssb.length();
        ssb.append(avgStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startAvg, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        ssb.append("元");

        tvSummaryContent.setText(ssb);
    }

    private List<Integer> generateLowSaturationColors(int count) {
        List<Integer> colors = new ArrayList<>();
        Random random = new Random();
        float goldenRatioConjugate = 0.618033988749895f;
        float currentHue = random.nextFloat();

        for (int i = 0; i < count; i++) {
            currentHue += goldenRatioConjugate;
            currentHue %= 1.0f;
            float h = currentHue * 360f;
            float s = 0.10f + random.nextFloat() * 0.25f;
            float v = 0.60f + random.nextFloat() * 0.25f;
            colors.add(Color.HSVToColor(new float[]{h, s, v}));
        }
        return colors;
    }

    private LineDataSet createLineDataSet(List<Entry> entries, String label, int colorResId) {
        LineDataSet set = new LineDataSet(entries, label);
        int color = ContextCompat.getColor(requireContext(), colorResId);
        set.setColor(color);
        set.setCircleColor(color);
        set.setLineWidth(2f);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.LINEAR);
        return set;
    }

    private void setupLineChart() {
        int textColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        lineChart.getDescription().setEnabled(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setTextColor(textColor);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(textColor);

        lineChart.getLegend().setTextColor(textColor);
        lineChart.setExtraBottomOffset(10f);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setHighlightPerDragEnabled(false);

        if (getContext() != null) {
            markerView = new CustomMarkerView(getContext(), R.layout.view_chart_marker);
            markerView.setChartView(lineChart);
            lineChart.setMarker(markerView);
        }
    }

    private void setupPieChart() {
        int textColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int holeColor = ContextCompat.getColor(requireContext(), R.color.bar_background);

        pieChart.getDescription().setEnabled(false);
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(0);
        pieChart.setHoleColor(holeColor);
        pieChart.setEntryLabelColor(textColor);
        pieChart.setEntryLabelTextSize(10f);
        pieChart.setRotationEnabled(false);

        Legend l = pieChart.getLegend();
        l.setTextColor(textColor);
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);

        pieChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                String category = ((PieEntry) e).getLabel();
                showCategoryDetailDialog(category);
            }
            @Override
            public void onNothingSelected() { }
        });
    }

    private void showCategoryDetailDialog(String category) {
        if (allTransactions == null) return;

        long startMillis;
        long endMillis;
        String dateRangeStr = ""; 
        ZoneId zone = ZoneId.systemDefault();

        if (currentMode == 0) { 
            LocalDate start = LocalDate.of(selectedDate.getYear(), 1, 1);
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = start.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli();
            dateRangeStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年"));
        } else if (currentMode == 1) { 
            LocalDate start = LocalDate.of(selectedDate.getYear(), selectedDate.getMonthValue(), 1);
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = start.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli();
            dateRangeStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
        } else { 
            LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            startMillis = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = endOfWeek.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M.d");
            dateRangeStr = startOfWeek.format(fmt) + " - " + endOfWeek.format(fmt);
        }

        Map<String, Double> catTotals = new HashMap<>();
        double totalAmount = 0;
        for (Transaction t : allTransactions) {
             if (t.type == 0 && t.date >= startMillis && t.date < endMillis) {
                 catTotals.put(t.category, catTotals.getOrDefault(t.category, 0.0) + t.amount);
                 totalAmount += t.amount;
             }
        }
        double threshold = totalAmount * 0.05;
        List<String> smallCategories = new ArrayList<>();
        for (Map.Entry<String, Double> entry : catTotals.entrySet()) {
            if (entry.getValue() < threshold) {
                smallCategories.add(entry.getKey());
            }
        }

        List<Transaction> filteredList = new ArrayList<>();
        for (Transaction t : allTransactions) {
            if (t.type == 0 && t.date >= startMillis && t.date < endMillis) {
                boolean match = false;
                if ("其他".equals(category)) {
                    if ("其他".equals(t.category) || smallCategories.contains(t.category)) {
                        match = true;
                    }
                } else {
                    if (t.category.equals(category)) {
                        match = true;
                    }
                }
                if (match) filteredList.add(t);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transaction_list, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        tvTitle.setText(dateRangeStr + " " + category + " - 消费清单");

        RecyclerView rv = dialogView.findViewById(R.id.rv_detail_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        TransactionListAdapter adapter = new TransactionListAdapter(t -> {
            dialog.dismiss();
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(t, date);
        });
        
        adapter.setTransactions(filteredList);
        adapter.setAssets(assetList); 
        
        rv.setAdapter(adapter);

        View btnOvertime = dialogView.findViewById(R.id.btn_add_overtime);
        View btnAdd = dialogView.findViewById(R.id.btn_add_transaction);
        if (btnOvertime != null) btnOvertime.setVisibility(View.GONE);
        if (btnAdd != null) btnAdd.setVisibility(View.GONE);

        Button btnClose = dialogView.findViewById(R.id.btn_close_dialog);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
    
    // ... showAddOrEditDialog 和 DecimalDigitsInputFilter 保持不变
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

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        AssistantConfig config = new AssistantConfig(requireContext());
        boolean isAssetEnabled = config.isAssetsEnabled();

        List<AssetAccount> assetList = new ArrayList<>();
        ArrayAdapter<String> assetAdapter = new ArrayAdapter<>(getContext(), R.layout.item_spinner_dropdown);

        if (isAssetEnabled) {
            spAsset.setVisibility(View.VISIBLE);

            AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
            noAsset.id = 0;
            assetList.add(noAsset);

            assetAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            spAsset.setAdapter(assetAdapter);

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
                assetAdapter.clear();
                assetAdapter.addAll(names);
                assetAdapter.notifyDataSetChanged();

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
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
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
        } else {
            btnSave.setText("保存");
            btnDelete.setVisibility(View.GONE);
            SimpleDateFormat noteSdf = new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA);
            etNote.setText(noteSdf.format(calendar.getTime()) + " 手动");
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
                String userRemark = "";
                if (etRemark != null) userRemark = etRemark.getText().toString().trim();

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