package com.example.budgetapp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BudgetHistoryActivity extends AppCompatActivity {

    private RecyclerView rvTimeline;
    private TimelineAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_budget_history);

        View rootLayout = findViewById(R.id.root_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        rvTimeline = findViewById(R.id.rv_timeline);
        rvTimeline.setLayoutManager(new LinearLayoutManager(this));

        FinanceViewModel viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        
        // 观察数据并生成时间轴
        viewModel.getAllTransactions().observe(this, transactions -> {
            viewModel.getAllGoals().observe(this, goals -> {
                generateTimeline(transactions != null ? transactions : new ArrayList<>(), 
                                 goals != null ? goals : new ArrayList<>());
            });
        });
    }

    /**
     * 核心算法：情景重现，推演历史记录
     */
    private void generateTimeline(List<Transaction> transactions, List<Goal> goals) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        float defaultBudget = prefs.getFloat("monthly_budget", 0f);

        // 获取首次开启预算的记录时间
        long startTs = prefs.getLong("budget_start_time", 0);
        if (startTs == 0) {
            // 兜底逻辑：如果是老用户第一次进这个页面，以最早的目标创建时间作为起点
            long earliest = System.currentTimeMillis();
            for (Goal g : goals) if (g.createdAt < earliest) earliest = g.createdAt;
            startTs = earliest;
            prefs.edit().putLong("budget_start_time", startTs).apply();
        }

        // 核心修改：时间轴的起点固定为首次开启功能的【当月1号】
        LocalDate earliestDate = Instant.ofEpochMilli(startTs).atZone(ZoneId.systemDefault()).toLocalDate();
        earliestDate = earliestDate.withDayOfMonth(1);

        List<TimelineItem> timeline = new ArrayList<>();
        List<Goal> pendingGoals = new ArrayList<>(goals);
        pendingGoals.sort((g1, g2) -> {
            if (g1.isPriority && !g2.isPriority) return -1;
            if (!g1.isPriority && g2.isPriority) return 1;
            return Long.compare(g1.createdAt, g2.createdAt);
        });

        double pool = 0;
        LocalDate today = LocalDate.now();
        YearMonth currentProcessingMonth = YearMonth.from(earliestDate);
        double currentMonthExpense = 0;

        // 读取当月独立预算
        float activeMonthBudget = prefs.getFloat("budget_" + currentProcessingMonth.getYear() + "_" + currentProcessingMonth.getMonthValue(), defaultBudget);

        for (LocalDate d = earliestDate; !d.isAfter(today); d = d.plusDays(1)) {
            YearMonth ym = YearMonth.from(d);

            // 跨月结算逻辑
            if (!ym.equals(currentProcessingMonth)) {
                double surplus = activeMonthBudget - currentMonthExpense;
                LocalDate lastDayOfPrevMonth = currentProcessingMonth.atEndOfMonth();
                long ts = lastDayOfPrevMonth.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                timeline.add(new MonthItem(ts, currentProcessingMonth.getYear(), currentProcessingMonth.getMonthValue(), surplus));

                // 进入新月份，重新读取新月份的独立预算
                currentProcessingMonth = ym;
                activeMonthBudget = prefs.getFloat("budget_" + ym.getYear() + "_" + ym.getMonthValue(), defaultBudget);
                currentMonthExpense = 0;
            }

            double dailyBudget = (double) activeMonthBudget / d.lengthOfMonth();
            double expenseToday = 0;
            long startOfDay = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endOfDay = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

            for (Transaction t : transactions) {
                if (t.date >= startOfDay && t.date < endOfDay && t.type == 0) {
                    expenseToday += t.amount;
                }
            }

            currentMonthExpense += expenseToday;
            pool += (dailyBudget - expenseToday);

            java.util.Iterator<Goal> it = pendingGoals.iterator();
            while (it.hasNext()) {
                Goal g = it.next();
                LocalDate goalCreateDate = Instant.ofEpochMilli(g.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
                if (d.isBefore(goalCreateDate)) continue;

                double needed = Math.max(0, g.targetAmount - g.savedAmount);
                if (needed <= 0) {
                    long ts = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    timeline.add(new GoalItem(ts, g.name, d));
                    it.remove();
                } else if (pool >= needed) {
                    pool -= needed;
                    long ts = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    timeline.add(new GoalItem(ts, g.name, d));
                    it.remove();
                } else {
                    break;
                }
            }
        }

        double currentSurplus = activeMonthBudget - currentMonthExpense;
        long nowTs = System.currentTimeMillis();
        timeline.add(new MonthItem(nowTs, currentProcessingMonth.getYear(), currentProcessingMonth.getMonthValue(), currentSurplus));

        Collections.sort(timeline, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        adapter = new TimelineAdapter(timeline);
        rvTimeline.setAdapter(adapter);
    }

    // --- 适配器结构 ---

    interface TimelineItem {
        long getTimestamp();
        int getType();
    }

    static class MonthItem implements TimelineItem {
        long timestamp; int year; int month; double surplus;
        MonthItem(long ts, int y, int m, double s) { timestamp = ts; year = y; month = m; surplus = s; }
        @Override public long getTimestamp() { return timestamp; }
        @Override public int getType() { return 0; }
    }

    static class GoalItem implements TimelineItem {
        long timestamp; String goalName; LocalDate achievedDate;
        GoalItem(long ts, String name, LocalDate date) { timestamp = ts; goalName = name; achievedDate = date; }
        @Override public long getTimestamp() { return timestamp; }
        @Override public int getType() { return 1; }
    }

    // ... TimelineItem 接口和实体类保持不变 ...

    private class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<TimelineItem> items;
        TimelineAdapter(List<TimelineItem> items) { this.items = items; }

        @Override public int getItemViewType(int position) { return items.get(position).getType(); }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline_month, parent, false)) {};
            } else {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline_goal, parent, false)) {};
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TimelineItem item = items.get(position);
            if (item.getType() == 0) {
                MonthItem mItem = (MonthItem) item;
                TextView tvTitle = holder.itemView.findViewById(R.id.tv_month_title);
                TextView tvSurplus = holder.itemView.findViewById(R.id.tv_month_surplus);

                tvTitle.setText(mItem.year + "年 " + mItem.month + "月");

                // 需求：1月份高亮为主题色作为新年标记，其余月份保持默认主文本色
                if (mItem.month == 1) {
                    tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.app_yellow));
                } else {
                    tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
                }

                String sign = mItem.surplus >= 0 ? "+ " : "";
                tvSurplus.setText(String.format("%s%.2f", sign, mItem.surplus));
                tvSurplus.setTextColor(ContextCompat.getColor(BudgetHistoryActivity.this,
                        mItem.surplus >= 0 ? R.color.app_yellow : R.color.budget_progress_exceed));
            } else {
                GoalItem gItem = (GoalItem) item;
                TextView tvName = holder.itemView.findViewById(R.id.tv_goal_name);
                TextView tvDate = holder.itemView.findViewById(R.id.tv_goal_date);

                tvName.setText("🎉 实现了目标：" + gItem.goalName);
                tvDate.setText(gItem.achievedDate.toString());
            }
        }
        @Override public int getItemCount() { return items.size(); }
    }
}