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

    private FinanceViewModel viewModel; // 【新增】全局 ViewModel 变量

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

        // 【修改这里】：将原本的局部变量改为全局变量
        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);

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

        // 1. 获取首次开启预算的记录时间
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

        // 按优先级和时间排序
        pendingGoals.sort((g1, g2) -> {
            if (g1.isPriority && !g2.isPriority) return -1;
            if (!g1.isPriority && g2.isPriority) return 1;
            return Long.compare(g1.createdAt, g2.createdAt);
        });

        double pool = 0;
        LocalDate today = LocalDate.now();

        // 如果起点晚于今天，说明数据异常，直接返回
        if (earliestDate.isAfter(today)) return;

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

            double dailyBudget = (activeMonthBudget > 0) ? ((double) activeMonthBudget / d.lengthOfMonth()) : 0;
            double expenseToday = 0;
            long startOfDay = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endOfDay = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

            for (Transaction t : transactions) {
                if (t.date >= startOfDay && t.date < endOfDay && t.type == 0) {
                    expenseToday += t.amount;
                }
            }

            currentMonthExpense += expenseToday;

            // 【新增修复】：判断这一天是否存在尚未完成的目标
            boolean hasActiveGoal = false;
            for (Goal g : pendingGoals) {
                LocalDate createDate = Instant.ofEpochMilli(g.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
                if (!d.isBefore(createDate)) {
                    hasActiveGoal = true;
                    break;
                }
            }

            // 只有存在激活目标时，才把当天的净余存入资金池
            if (hasActiveGoal) {
                pool += (dailyBudget - expenseToday);
            } else {
                pool = 0; // 防止历史结余变成死水，瞬间填满未来的新目标
            }

            // 检查目标是否达成
            java.util.Iterator<Goal> it = pendingGoals.iterator();
            while (it.hasNext()) {
                Goal g = it.next();
                LocalDate goalCreateDate = Instant.ofEpochMilli(g.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
                if (d.isBefore(goalCreateDate)) continue;

                // 劫持手动完成的目标
                if (g.isFinished) {
                    LocalDate finishDate = Instant.ofEpochMilli(g.finishedDate).atZone(ZoneId.systemDefault()).toLocalDate();
                    if (!d.isBefore(finishDate)) { // 当推演到用户点击完成的那一天
                        long ts = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        timeline.add(new GoalItem(ts, g.name, d, g)); // 【修改】：加上参数 g
                        it.remove();
                        continue; // 手动完成的不消耗资金池
                    }
                }

                // 计算是否自动达标
                double needed = Math.max(0, g.targetAmount - g.savedAmount);
                if (needed <= 0) {
                    // 仅靠基础资金就完成了
                    long ts = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    timeline.add(new GoalItem(ts, g.name, d, g)); // 【修改】：加上参数 g
                    it.remove();
                } else if (pool >= needed) {
                    // 资金池填满了目标
                    pool -= needed;
                    long ts = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    timeline.add(new GoalItem(ts, g.name, d, g)); // 【修改】：加上参数 g
                    it.remove();
                } else {
                    break; // 资金不够填目前的最高优目标，停止当天的顺延分配
                }
            }
        }

        // 添加当前进行中月份的结余
        double currentSurplus = activeMonthBudget - currentMonthExpense;
        long nowTs = System.currentTimeMillis();
        timeline.add(new MonthItem(nowTs, currentProcessingMonth.getYear(), currentProcessingMonth.getMonthValue(), currentSurplus));

        // 按时间正序排列（最早的在最上面，符合你的阅读习惯）
        Collections.sort(timeline, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        adapter = new TimelineAdapter(timeline);
        rvTimeline.setAdapter(adapter);
    }

    /**
     * 弹出确认删除目标记录的对话框
     */
    private void showDeleteGoalDialog(Goal goal) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);

        android.app.AlertDialog confirmDialog = builder.create();
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvMessage = view.findViewById(R.id.tv_dialog_message);
        if (tvMessage != null) {
            tvMessage.setText("确定要删除历史记录中的“" + goal.name + "”吗？\n删除后将无法恢复。");
        }

        View btnConfirm = view.findViewById(R.id.btn_dialog_confirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                viewModel.deleteGoal(goal);
                confirmDialog.dismiss();
                android.widget.Toast.makeText(this, "已删除目标记录", android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        View btnCancel = view.findViewById(R.id.btn_dialog_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> confirmDialog.dismiss());
        }

        confirmDialog.show();
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
        long timestamp;
        String goalName;
        LocalDate achievedDate;
        Goal goal; // 【新增】：保存目标对象本身

        GoalItem(long ts, String name, LocalDate date, Goal goal) {
            timestamp = ts;
            goalName = name;
            achievedDate = date;
            this.goal = goal; // 【新增】
        }
        @Override public long getTimestamp() {
            return timestamp;
        }
        @Override public int getType() {
            return 1;
        }
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

                // 【新增】：给目标卡片绑定点击事件
                holder.itemView.setOnClickListener(v -> showDeleteGoalDialog(gItem.goal));
            }
        }
        @Override public int getItemCount() { return items.size(); }
    }
}