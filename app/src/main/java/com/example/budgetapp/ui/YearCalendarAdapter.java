package com.example.budgetapp.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YearCalendarAdapter extends RecyclerView.Adapter<YearCalendarAdapter.MonthViewHolder> {

    private final int year;
    private final Map<Integer, Map<Integer, Double>> yearData;
    private final RecyclerView.RecycledViewPool viewPool; // 共享池
    private OnMonthClickListener listener;
    
    // 颜色缓存
    private Integer cachedThemeColor = null;
    private Integer cachedDefaultTextColor = null;

    public interface OnMonthClickListener {
        void onMonthClick(int year, int month);
    }

    public void setOnMonthClickListener(OnMonthClickListener listener) {
        this.listener = listener;
    }

    public YearCalendarAdapter(int year, Map<Integer, Map<Integer, Double>> yearData, RecyclerView.RecycledViewPool viewPool) {
        this.year = year;
        this.yearData = yearData;
        this.viewPool = viewPool;
    }

    @NonNull
    @Override
    public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_year_month, parent, false);
        
        if (cachedThemeColor == null) {
            initColors(parent.getContext());
        }
        
        return new MonthViewHolder(view, viewPool);
    }
    
    private void initColors(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        cachedThemeColor = (typedValue.resourceId != 0) ? 
                context.getColor(typedValue.resourceId) : Color.parseColor("#6200EE");

        TypedArray ta = context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        cachedDefaultTextColor = ta.getColor(0, Color.BLACK);
        ta.recycle();
    }

    @Override
    public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
        int month = position + 1;
        holder.tvMonthName.setText(month + "月");

        // 1. 父布局点击
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMonthClick(year, month);
        });

        // 2. 拦截子View触摸，手动触发父布局点击
        holder.rvMonthGrid.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                holder.itemView.performClick();
            }
            return true;
        });

        Map<Integer, Double> monthStats = yearData.getOrDefault(month, new HashMap<>());
        
        // 生成固定 42 个格子的数据
        List<LocalDate> days = generateDaysForMonth(year, month);

        MonthGridAdapter gridAdapter = new MonthGridAdapter(days, monthStats, cachedThemeColor, cachedDefaultTextColor);
        holder.rvMonthGrid.setLayoutManager(new GridLayoutManager(holder.itemView.getContext(), 7));
        holder.rvMonthGrid.setAdapter(gridAdapter);
    }

    @Override
    public int getItemCount() {
        return 12;
    }

    // 修改：填充至固定 42 个格子 (6行)
    private List<LocalDate> generateDaysForMonth(int year, int month) {
        List<LocalDate> list = new ArrayList<>();
        YearMonth yearMonth = YearMonth.of(year, month);
        
        // 计算月初偏移
        LocalDate firstDay = yearMonth.atDay(1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue(); 
        int offset = dayOfWeek - 1; // Mon=1 -> 0, Sun=7 -> 6

        // 1. 填充月初空白
        for (int i = 0; i < offset; i++) {
            list.add(null);
        }
        
        // 2. 填充实际日期
        int length = yearMonth.lengthOfMonth();
        for (int i = 1; i <= length; i++) {
            list.add(yearMonth.atDay(i));
        }

        // 3. 【关键修改】填充剩余空白，直到总数达到 42 (6行 * 7列)
        // 这样可以确保每个月份的高度完全一致
        while (list.size() < 42) {
            list.add(null);
        }
        
        return list;
    }

    static class MonthViewHolder extends RecyclerView.ViewHolder {
        TextView tvMonthName;
        RecyclerView rvMonthGrid;

        public MonthViewHolder(@NonNull View itemView, RecyclerView.RecycledViewPool pool) {
            super(itemView);
            tvMonthName = itemView.findViewById(R.id.tv_month_name);
            rvMonthGrid = itemView.findViewById(R.id.rv_month_grid);
            
            rvMonthGrid.setRecycledViewPool(pool);
            
            // 【修复】移除了 setHasFixedSize(true)，因为布局中高度是 wrap_content，这会导致 Lint 报错
            // rvMonthGrid.setHasFixedSize(true);
            
            rvMonthGrid.setNestedScrollingEnabled(false);
        }
    }

    static class MonthGridAdapter extends RecyclerView.Adapter<MonthGridAdapter.DayViewHolder> {
        private final List<LocalDate> days;
        private final Map<Integer, Double> dailyStats;
        private final int themeColor;
        private final int defaultTextColor;

        public MonthGridAdapter(List<LocalDate> days, Map<Integer, Double> dailyStats, int themeColor, int defaultTextColor) {
            this.days = days;
            this.dailyStats = dailyStats;
            this.themeColor = themeColor;
            this.defaultTextColor = defaultTextColor;
        }

        @NonNull
        @Override
        public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_year_day, parent, false);
            return new DayViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
            LocalDate date = days.get(position);
            
            // 如果是空白占位
            if (date == null) {
                holder.tvDayNum.setText("");
                holder.indicator.setVisibility(View.INVISIBLE);
                return;
            }

            int day = date.getDayOfMonth();
            holder.tvDayNum.setText(String.valueOf(day));

            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                holder.tvDayNum.setTextColor(themeColor);
            } else {
                holder.tvDayNum.setTextColor(defaultTextColor);
            }

            if (dailyStats.containsKey(day)) {
                double net = dailyStats.get(day);
                holder.indicator.setVisibility(View.VISIBLE);
                if (net > 0) {
                    holder.indicator.setBackgroundColor(Color.parseColor("#F44336")); 
                } else if (net < 0) {
                    holder.indicator.setBackgroundColor(Color.parseColor("#4CAF50")); 
                } else {
                    holder.indicator.setVisibility(View.INVISIBLE);
                }
            } else {
                holder.indicator.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        static class DayViewHolder extends RecyclerView.ViewHolder {
            TextView tvDayNum;
            View indicator;

            public DayViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDayNum = itemView.findViewById(R.id.tv_day_num);
                indicator = itemView.findViewById(R.id.view_indicator);
            }
        }
    }
}