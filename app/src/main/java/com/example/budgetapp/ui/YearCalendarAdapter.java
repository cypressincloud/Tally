package com.example.budgetapp.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import java.util.List;

public class YearCalendarAdapter extends RecyclerView.Adapter<YearCalendarAdapter.MonthViewHolder> {

    private int year;
    // 🌟 优化：不再存储全量金额数据，只存储有记账记录的月份列表 (例如 [1, 3, 5])
    private final List<Integer> monthsWithData;
    private final RecyclerView.RecycledViewPool viewPool;
    private OnMonthClickListener listener;

    private Integer cachedThemeColor = null;
    private Integer cachedDefaultTextColor = null;

    public interface OnMonthClickListener {
        void onMonthClick(int year, int month);
    }

    public void setOnMonthClickListener(OnMonthClickListener listener) {
        this.listener = listener;
    }

    // 🌟 构造函数同步修改为接收 List<Integer>
    public YearCalendarAdapter(int year, List<Integer> monthsWithData, RecyclerView.RecycledViewPool viewPool) {
        this.year = year;
        this.monthsWithData = monthsWithData != null ? monthsWithData : new ArrayList<>();
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

        // 🌟 1. 核心逻辑：检查当前月份是否在有数据的列表中，并修改字体颜色
        if (monthsWithData.contains(month)) {
            holder.tvMonthName.setTextColor(cachedThemeColor);
        } else {
            holder.tvMonthName.setTextColor(cachedDefaultTextColor);
        }

        // 2. 事件处理：点击月份跳转
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMonthClick(year, month);
        });

        // 拦截子网格触摸，使其响应父布局点击
        holder.rvMonthGrid.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                holder.itemView.performClick();
            }
            return true;
        });

        // 3. 加载日期网格
        List<LocalDate> days = generateDaysForMonth(year, month);
        MonthGridAdapter gridAdapter = (MonthGridAdapter) holder.rvMonthGrid.getAdapter();
        if (gridAdapter == null) {
            gridAdapter = new MonthGridAdapter(days, cachedThemeColor, cachedDefaultTextColor);
            holder.rvMonthGrid.setLayoutManager(new GridLayoutManager(holder.itemView.getContext(), 7));
            holder.rvMonthGrid.setAdapter(gridAdapter);
        } else {
            gridAdapter.updateData(days);
        }
    }

    @Override
    public int getItemCount() {
        return 12;
    }

    private List<LocalDate> generateDaysForMonth(int year, int month) {
        List<LocalDate> list = new ArrayList<>();
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue();
        int offset = dayOfWeek - 1;

        for (int i = 0; i < offset; i++) list.add(null);
        int length = yearMonth.lengthOfMonth();
        for (int i = 1; i <= length; i++) list.add(yearMonth.atDay(i));
        while (list.size() < 42) list.add(null);
        return list;
    }

    static class MonthViewHolder extends RecyclerView.ViewHolder {
        TextView tvMonthName;
        // 🌟 移除 monthIndicator 变量
        RecyclerView rvMonthGrid;

        public MonthViewHolder(@NonNull View itemView, RecyclerView.RecycledViewPool pool) {
            super(itemView);
            tvMonthName = itemView.findViewById(R.id.tv_month_name);
            // 🌟 移除对 R.id.view_month_indicator 的绑定
            rvMonthGrid = itemView.findViewById(R.id.rv_month_grid);
            rvMonthGrid.setRecycledViewPool(pool);
            rvMonthGrid.setNestedScrollingEnabled(false);
        }
    }

    // 🌟 内部日期适配器精简：去除了所有的统计数据引用
    static class MonthGridAdapter extends RecyclerView.Adapter<MonthGridAdapter.DayViewHolder> {
        private final List<LocalDate> days;
        private final int themeColor;
        private final int defaultTextColor;

        public MonthGridAdapter(List<LocalDate> days, int themeColor, int defaultTextColor) {
            this.days = new ArrayList<>(days);
            this.themeColor = themeColor;
            this.defaultTextColor = defaultTextColor;
        }

        public void updateData(List<LocalDate> newDays) {
            this.days.clear();
            this.days.addAll(newDays);
            notifyDataSetChanged();
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
            if (date == null) {
                holder.tvDayNum.setText("");
                return;
            }
            holder.tvDayNum.setText(String.valueOf(date.getDayOfMonth()));
            DayOfWeek dow = date.getDayOfWeek();
            holder.tvDayNum.setTextColor((dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) ? themeColor : defaultTextColor);
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        static class DayViewHolder extends RecyclerView.ViewHolder {
            TextView tvDayNum;
            public DayViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDayNum = itemView.findViewById(R.id.tv_day_num);
            }
        }
    }
}