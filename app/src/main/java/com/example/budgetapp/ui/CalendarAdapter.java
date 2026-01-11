package com.example.budgetapp.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
    
    private List<LocalDate> days = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private LocalDate selectedDate;
    private final OnDateClickListener listener;
    private int filterMode = 0;

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

    public void updateData(List<LocalDate> days, List<Transaction> transactions) {
        this.days = days;
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
        notifyDataSetChanged();
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
            holder.tvWeek.setText("");
            holder.tvNet.setText("");
            holder.itemView.setBackgroundResource(0);
            return;
        }

        holder.tvDay.setText(String.valueOf(date.getDayOfMonth()));

        String weekStr = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.CHINA);
        holder.tvWeek.setText(weekStr);

        double dailySum = 0;
        long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        for (Transaction t : transactions) {
            if (t.date >= start && t.date < end) {
                switch (filterMode) {
                    case 0: // 结余模式
                        if (t.type == 1) {
                            // 【修改】收入中剔除加班费
                            if (!"加班".equals(t.category)) {
                                dailySum += t.amount;
                            }
                        } else {
                            dailySum -= t.amount;
                        }
                        break;
                    case 1: // 收入模式
                        // 【修改】收入中剔除加班费
                        if (t.type == 1 && !"加班".equals(t.category)) {
                            dailySum += t.amount;
                        }
                        break;
                    case 2: // 支出模式
                        if (t.type == 0) dailySum += t.amount; 
                        break;
                    case 3: // 加班模式 (单独统计加班)
                        if ("加班".equals(t.category)) dailySum += t.amount; 
                        break;
                }
            }
        }

        if (Math.abs(dailySum) > 0.001) { 
            holder.tvNet.setText(String.format("%.2f", dailySum));
            
            if (filterMode == 2) {
                holder.tvNet.setTextColor(Color.parseColor("#4CAF50")); // 支出绿色
            } else if (filterMode == 3) {
                holder.tvNet.setTextColor(Color.parseColor("#FF9800")); // 加班橙色
            } else {
                holder.tvNet.setTextColor(dailySum > 0 ? Color.parseColor("#FF5252") : Color.parseColor("#4CAF50"));
            }
        } else {
            holder.tvNet.setText("");
        }

        if (date.equals(selectedDate)) {
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
        } else {
            holder.itemView.setBackgroundResource(0);
        }

        holder.itemView.setOnClickListener(v -> listener.onDateClick(date));
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvNet, tvWeek;

        ViewHolder(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_day);
            tvWeek = v.findViewById(R.id.tv_week);
            tvNet = v.findViewById(R.id.tv_net_amount);
        }
    }
}