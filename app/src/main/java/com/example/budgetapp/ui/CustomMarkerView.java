package com.example.budgetapp.ui;

import android.content.Context;
import android.widget.TextView;

import com.example.budgetapp.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.HashMap;
import java.util.Map;

public class CustomMarkerView extends MarkerView {
    private final TextView tvDate;
    private final TextView tvIncome;
    private final TextView tvExpense;
    private final TextView tvNet;

    private String suffix = ""; // 单位 (月/日)
    private String[] customLabels = null; // 用于周视图 (周一, 周二...)

    // 数据源缓存
    private Map<Integer, Double> incomeMap = new HashMap<>();
    private Map<Integer, Double> expenseMap = new HashMap<>();

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvDate = findViewById(R.id.tv_marker_date);
        tvIncome = findViewById(R.id.tv_marker_income);
        tvExpense = findViewById(R.id.tv_marker_expense);
        tvNet = findViewById(R.id.tv_marker_net);
    }

    // 关键方法：接收外部传入的数据源
    public void setSourceData(Map<Integer, Double> income, Map<Integer, Double> expense, String suffix, String[] customLabels) {
        this.incomeMap = income;
        this.expenseMap = expense;
        this.suffix = suffix;
        this.customLabels = customLabels;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        // e.getX() 得到的是 X 轴的索引 (1, 2, 3...)
        int index = (int) e.getX();

        // 1. 设置日期标题
        if (customLabels != null && index >= 1 && index < customLabels.length) {
            tvDate.setText(customLabels[index]); // 显示 "周一"
        } else {
            tvDate.setText(index + suffix); // 显示 "1月" 或 "15日"
        }

        // 2. 从 Map 中查找全部数据 (无论点击的是哪条线)
        double in = incomeMap.getOrDefault(index, 0.0);
        double out = expenseMap.getOrDefault(index, 0.0);
        double net = in - out;

        // 3. 设置数值显示 (【修改点】改为保留两位小数)
        tvIncome.setText(String.format("收入: +%.2f", in));
        tvExpense.setText(String.format("支出: -%.2f", out));

        String netSign = net >= 0 ? "+" : "";
        tvNet.setText(String.format("净收支: %s%.2f", netSign, net));

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // 让气泡居中显示在点的上方
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}