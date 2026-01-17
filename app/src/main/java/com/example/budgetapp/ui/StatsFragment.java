package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
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

    private FinanceViewModel viewModel;
    private LineChart lineChart;
    private PieChart pieChart;
    private RadioGroup rgTimeScope;
    private TextView tvDateRange;
    
    // 总结板块
    private TextView tvSummaryTitle;
    private TextView tvSummaryContent;

    // 0=年, 1=月, 2=周
    private int currentMode = 2;
    private LocalDate selectedDate = LocalDate.now();
    private List<Transaction> allTransactions = new ArrayList<>();
    // 【新增】缓存资产列表，用于传给弹窗Adapter
    private List<AssetAccount> assetList = new ArrayList<>();
    private CustomMarkerView markerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        initViews(view);
        setupLineChart();
        setupPieChart();

        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        
        // 观察交易记录
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), list -> {
            this.allTransactions = list;
            refreshData();
        });

        // 【新增】观察资产列表，并更新本地缓存
        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            this.assetList = assets;
        });

        setupListeners(view);
        updateDateRangeDisplay();

        return view;
    }

    private void initViews(View view) {
        lineChart = view.findViewById(R.id.chart_line);
        pieChart = view.findViewById(R.id.chart_pie);
        rgTimeScope = view.findViewById(R.id.rg_time_scope);
        tvDateRange = view.findViewById(R.id.tv_current_date_range);
        
        // 初始化总结板块的 View
        tvSummaryTitle = view.findViewById(R.id.tv_summary_title);
        tvSummaryContent = view.findViewById(R.id.tv_summary_content);
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

        tvDateRange.setOnClickListener(v -> showDatePicker());

        tvDateRange.setOnLongClickListener(v -> {
            Intent intent = new Intent(requireContext(), AssistantManagerActivity.class);
            startActivity(intent);
            return true;
        });
    }

    private void changeDate(int offset) {
        if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
        else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
        else selectedDate = selectedDate.plusWeeks(offset);
        updateDateRangeDisplay();
        refreshData();
    }

    private void updateDateRangeDisplay() {
        if (currentMode == 0) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年")));
        } else if (currentMode == 1) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        } else {
            WeekFields weekFields = WeekFields.of(Locale.CHINA);
            String yearMonthStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月"));
            int weekOfMonth = selectedDate.get(weekFields.weekOfMonth());

            LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            DateTimeFormatter rangeFormatter = DateTimeFormatter.ofPattern("M.d");
            String startStr = startOfWeek.format(rangeFormatter);
            String endStr = endOfWeek.format(rangeFormatter);

            String finalStr = String.format(Locale.CHINA, "%s 第%d周 (%s - %s)",
                    yearMonthStr, weekOfMonth, startStr, endStr);

            tvDateRange.setText(finalStr);
        }
    }

    private void showDatePicker() {
        long selection = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日期")
                .setSelection(selection)
                .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
                .setTheme(R.style.ThemeOverlay_App_DatePicker)
                .build();
        datePicker.addOnPositiveButtonClickListener(selectionMillis -> {
            selectedDate = Instant.ofEpochMilli(selectionMillis).atZone(ZoneOffset.UTC).toLocalDate();
            updateDateRangeDisplay();
            refreshData();
        });
        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    // --- 图表数据处理和绘制逻辑 ---

    private void refreshData() {
        if (allTransactions == null) return;
        if (currentMode == 0) processYearlyData();
        else if (currentMode == 1) processMonthlyData();
        else processWeeklyData();
    }

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
        LineDataSet setNet = createLineDataSet(netEntries, "净收支", R.color.fixed_yellow);
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
        
        // --- 总结板块逻辑更新 ---
        updateSummarySection(pieMap, totalPieAmount);
    }
    
    private void updateSummarySection(Map<String, Double> pieMap, double totalAmount) {
        // 更新标题为 “本X消费”
        String scopeStr;
        if (currentMode == 0) scopeStr = "本年";
        else if (currentMode == 1) scopeStr = "本月";
        else scopeStr = "本周";
        tvSummaryTitle.setText(scopeStr + "消费");

        if (pieMap.isEmpty() || totalAmount == 0) {
            tvSummaryContent.setText("暂无消费记录");
            return;
        }

        // 排序取出前三名
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(pieMap.entrySet());
        // 降序排序
        Collections.sort(sortedEntries, (e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] prefixes = {"最多是", "其次是", "然后是"};
        
        // 获取颜色资源
        int yellowColor = ContextCompat.getColor(requireContext(), R.color.fixed_yellow);
        int greenColor = ContextCompat.getColor(requireContext(), R.color.expense_green);
        int redColor = ContextCompat.getColor(requireContext(), R.color.income_red);

        int count = Math.min(sortedEntries.size(), 3);
        for (int i = 0; i < count; i++) {
            if (i > 0) ssb.append("\n"); // 换行

            Map.Entry<String, Double> e = sortedEntries.get(i);
            double percent = (e.getValue() / totalAmount) * 100;

            // 前缀
            ssb.append(prefixes[i]);
            
            // 类别名称 (黄色字体)
            String category = e.getKey();
            int startCat = ssb.length();
            ssb.append(category);
            ssb.setSpan(new ForegroundColorSpan(yellowColor), startCat, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            ssb.append(", ");

            // 占比标签
            ssb.append("占比");
            
            // 百分比数值 (绿色字体)
            String percentStr = String.format(Locale.CHINA, "%.1f%%", percent);
            int startPer = ssb.length();
            ssb.append(percentStr);
            ssb.setSpan(new ForegroundColorSpan(greenColor), startPer, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            ssb.append(", ");

            // 消费标签
            ssb.append("消费");

            // 金额数值 (红色字体)
            String amountStr = String.format(Locale.CHINA, "%.2f", e.getValue());
            int startAmt = ssb.length();
            ssb.append(amountStr);
            ssb.setSpan(new ForegroundColorSpan(redColor), startAmt, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            // 单位
            ssb.append("元");
        }

        // --- NEW: 共计消费和日均消费 (动态逻辑) ---
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

        // 判断当前日期是否在选定周期内
        // 注意：isAfter/isBefore 不包含边界，所以要用 !isBefore(start) && !isAfter(end)
        if (!today.isBefore(startOfPeriod) && !today.isAfter(endOfPeriod)) {
             // 如果是当前周期，计算从开始到今天的天数（含今天）
             days = ChronoUnit.DAYS.between(startOfPeriod, today) + 1;
        } else {
             // 如果是过去或未来的完整周期，计算总天数
             days = ChronoUnit.DAYS.between(startOfPeriod, endOfPeriod) + 1;
        }

        if (days < 1) days = 1;

        double dailyAvg = totalAmount / days;

        // "共计消费"
        ssb.append("共计消费");
        
        // 总金额 (红色字体)
        String totalStr = String.format(Locale.CHINA, "%.2f", totalAmount);
        int startTotal = ssb.length();
        ssb.append(totalStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startTotal, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        // "元, "
        ssb.append("元, ");
        
        // "日均消费"
        ssb.append("日均消费");
        
        // 日均金额 (红色字体)
        String avgStr = String.format(Locale.CHINA, "%.2f", dailyAvg);
        int startAvg = ssb.length();
        ssb.append(avgStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startAvg, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        // "元"
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
        // 【关键修复】: 将缓存的资产列表传给 Adapter
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

    private void showEditDialog(Transaction t, AlertDialog parentDialog) {
        // ... (保持不变)
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
        Spinner spAsset = dialogView.findViewById(R.id.sp_asset); // 关联资产 Spinner
        
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