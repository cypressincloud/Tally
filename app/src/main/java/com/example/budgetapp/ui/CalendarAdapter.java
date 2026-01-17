package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
    
    private List<LocalDate> days = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private LocalDate selectedDate;
    private final OnDateClickListener listener;
    private int filterMode = 0;
    private YearMonth currentMonth;

    public interface OnDateClickListener {
        void onDateClick(LocalDate date);
    }

    public CalendarAdapter(OnDateClickListener listener) {
        this.listener = listener;
    }

    public void setFilterMode(int mode) {
        this.filterMode = mode;
        notifyDataSetChanged();
    }
    
    public void setCurrentMonth(YearMonth month) {
        this.currentMonth = month;
    }

    public void updateData(List<LocalDate> days, List<Transaction> transactions) {
        this.days = days;
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
        notifyDataSetChanged();
    }

    private int getThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return context.getColor(typedValue.resourceId);
            }
            return typedValue.data;
        }
        return Color.GRAY;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocalDate date = days.get(position);
        if (date == null) {
            holder.tvDay.setText("");
            holder.tvNet.setText("");
            holder.itemView.setBackgroundResource(0);
            holder.itemView.setSelected(false);
            return;
        }

        Context context = holder.itemView.getContext();
        holder.tvDay.setText(String.valueOf(date.getDayOfMonth()));

        // 获取各种基础颜色
        int colorPrimaryText = getThemeColor(context, android.R.attr.textColorPrimary);
        int colorSecondaryText = getThemeColor(context, android.R.attr.textColorSecondary);
        int themeColor = context.getColor(R.color.app_yellow); // 获取当前应用主题色

        boolean isCurrentMonth = true;
        if (currentMonth != null) {
            isCurrentMonth = date.getYear() == currentMonth.getYear() &&
                             date.getMonth() == currentMonth.getMonth();
        }

        // --- 1. 先计算默认状态下的字体颜色 (未选中时) ---
        int defaultDayColor;
        if (isCurrentMonth) {
            holder.tvDay.setAlpha(1.0f);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            defaultDayColor = isWeekend ? themeColor : colorPrimaryText;
        } else {
            holder.tvDay.setAlpha(0.3f);
            defaultDayColor = colorSecondaryText;
        }

        // --- 2. 统计金额并计算默认金额颜色 ---
        double dailySum = 0;
        long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        for (Transaction t : transactions) {
            if (t.date >= start && t.date < end) {
                switch (filterMode) {
                    case 0: // 结余
                        if (t.type == 1) {
                            if (!"加班".equals(t.category)) dailySum += t.amount;
                        } else {
                            dailySum -= t.amount;
                        }
                        break;
                    case 1: // 收入
                        if (t.type == 1 && !"加班".equals(t.category)) dailySum += t.amount;
                        break;
                    case 2: // 支出
                        if (t.type == 0) dailySum += t.amount;
                        break;
                    case 3: // 加班
                        if ("加班".equals(t.category)) dailySum += t.amount;
                        break;
                }
            }
        }

        int defaultNetColor = 0;
        String netText = "";
        if (Math.abs(dailySum) > 0.001) {
            netText = String.format("%.2f", dailySum);
            holder.tvNet.setAlpha(isCurrentMonth ? 1.0f : 0.3f);

            if (filterMode == 2) {
                defaultNetColor = Color.parseColor("#4CAF50");
            } else if (filterMode == 3) {
                defaultNetColor = Color.parseColor("#FF9800");
            } else {
                defaultNetColor = dailySum > 0 ? Color.parseColor("#FF5252") : Color.parseColor("#4CAF50");
            }
        }

        // --- 3. 【核心逻辑】根据选中状态应用最终样式 ---
        if (date.equals(selectedDate)) {
            // A. 设置背景及主题色Tint
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
            Drawable background = holder.itemView.getBackground();
            if (background != null) {
                // 将背景着色为当前主题色
                background.setTint(themeColor);
            }
            holder.itemView.setSelected(true);

            // B. 强制将所有文字颜色改为 selector_text (通常是白色)
            int selectedTextColor = context.getColor(R.color.selector_text);
            
            holder.tvDay.setTextColor(selectedTextColor);
            holder.tvDay.setAlpha(1.0f); // 选中时完全不透明

            holder.tvNet.setText(netText);
            if (!netText.isEmpty()) {
                holder.tvNet.setTextColor(selectedTextColor);
                holder.tvNet.setAlpha(1.0f);
            } else {
                holder.tvNet.setText("");
            }

        } else {
            // 未选中状态：清除背景，恢复默认计算的颜色
            holder.itemView.setBackgroundResource(0);
            holder.itemView.setSelected(false);
            
            holder.tvDay.setTextColor(defaultDayColor);
            
            holder.tvNet.setText(netText);
            if (!netText.isEmpty()) {
                holder.tvNet.setTextColor(defaultNetColor);
            }
        }

        holder.itemView.setOnClickListener(v -> listener.onDateClick(date));
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvNet;

        ViewHolder(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_day);
            tvNet = v.findViewById(R.id.tv_net_amount);
        }
    }
}