// src/main/java/com/example/budgetapp/ui/CalendarAdapter.java
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
import com.example.budgetapp.database.RenewalItem;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

    private List<LocalDate> days = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private List<RenewalItem> renewalItems = new ArrayList<>(); // 支持多项续费项目
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

    /**
     * 更新续费项目列表
     */
    public void setRenewalItems(List<RenewalItem> items) {
        this.renewalItems = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
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

        // 基础颜色
        int colorPrimaryText = getThemeColor(context, android.R.attr.textColorPrimary);
        int colorSecondaryText = getThemeColor(context, android.R.attr.textColorSecondary);
        int themeColor = context.getColor(R.color.app_yellow);

        int incomeRed = context.getColor(R.color.income_red);
        int expenseGreen = context.getColor(R.color.expense_green);

        boolean isCurrentMonth = currentMonth != null &&
                date.getYear() == currentMonth.getYear() &&
                date.getMonth() == currentMonth.getMonth();

        // 1. 计算默认字体颜色
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

        // 2. 统计金额及颜色处理
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
            if (filterMode == 2) {
                defaultNetColor = expenseGreen;
            } else if (filterMode == 3) {
                defaultNetColor = Color.parseColor("#FF9800");
            } else {
                defaultNetColor = dailySum > 0 ? incomeRed : expenseGreen;
            }
        }

        // 3. 样式应用逻辑核心
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);

        // --- 核心优化：检测多项自动续费日期 ---
        boolean isRenewalDay = false;
        for (RenewalItem item : renewalItems) {
            if ("Month".equals(item.period)) {
                if (date.getDayOfMonth() == item.day) {
                    isRenewalDay = true;
                    break;
                }
            } else if ("Year".equals(item.period)) {
                if (date.getMonthValue() == item.month && date.getDayOfMonth() == item.day) {
                    isRenewalDay = true;
                    break;
                }
            }
        }

        // --- 样式优先级：选中 > 今天 > 续费日期 > 普通 ---
        if (isSelected) {
            // [被选中状态]：显示黄色边框
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(themeColor);

            holder.tvDay.setTextColor(defaultDayColor);
            holder.tvDay.setAlpha(1.0f);
            holder.itemView.setSelected(true);

        } else if (isToday) {
            // [今天状态]：黄色实心背景 + 白色文字
            holder.itemView.setBackgroundResource(R.drawable.bg_calendar_today);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(themeColor);

            holder.tvDay.setTextColor(Color.WHITE);
            holder.tvDay.setAlpha(1.0f);
            holder.itemView.setSelected(false);

        } else if (isRenewalDay) {
            // [自动续费状态]：显示红色边框
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(incomeRed); // 设置为红色边框适配“自动续费”标识

            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);

        } else {
            // [普通状态]
            holder.itemView.setBackgroundResource(0);
            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);
        }

        // 设置下方金额文字
        holder.tvNet.setText(netText);
        if (!netText.isEmpty()) {
            // 如果是今天且未被选中，金额显示白色以适配黄色背景
            if (isToday && !isSelected) {
                holder.tvNet.setTextColor(Color.WHITE);
            } else {
                holder.tvNet.setTextColor(defaultNetColor);
            }
            holder.tvNet.setAlpha(isCurrentMonth ? 1.0f : 0.3f);
        } else {
            holder.tvNet.setText("");
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