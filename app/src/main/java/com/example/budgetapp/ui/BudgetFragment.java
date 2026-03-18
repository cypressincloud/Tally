package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class BudgetFragment extends Fragment {

    private FinanceViewModel viewModel;
    private TextView tvTotalSurplus, tvHeaderMonthlyBudget, tvHeaderDailyAvailable;
    private RecyclerView rvGoals;
    private GoalAdapter adapter;
    private double currentMonthSurplus = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);

        // 1. 绑定概览视图
        tvTotalSurplus = view.findViewById(R.id.tv_total_surplus);
        tvHeaderMonthlyBudget = view.findViewById(R.id.tv_header_monthly_budget);

        // 新增：点击月总预算可单独设置当月预算
        tvHeaderMonthlyBudget.setOnClickListener(v -> showSetMonthBudgetDialog());

        tvHeaderDailyAvailable = view.findViewById(R.id.tv_header_daily_available);

        // 2. 初始化列表
        rvGoals = view.findViewById(R.id.rv_goals);
        rvGoals.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GoalAdapter();
        rvGoals.setAdapter(adapter);

        // 3. 悬浮添加按钮
        view.findViewById(R.id.btn_add_goal).setOnClickListener(v -> showAddGoalDialog());

        // 4. 数据观察与实时刷新
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), transactions -> {
            calculateMonthHeader(transactions);
            adapter.setTransactions(transactions);
        });

        view.findViewById(R.id.btn_budget_history).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), BudgetHistoryActivity.class));
        });

        viewModel.getAllGoals().observe(getViewLifecycleOwner(), goals -> {
            if (goals != null) adapter.setGoals(goals);
        });

        return view;
    }

    /**
     * 新增：设置独立月份预算的弹窗
     */
    private void showSetMonthBudgetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_set_month_budget, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etBudget = view.findViewById(R.id.et_month_budget);

        LocalDate today = LocalDate.now();
        tvTitle.setText("设置 " + today.getYear() + "年" + today.getMonthValue() + "月 预算");

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        float defaultBudget = prefs.getFloat("monthly_budget", 0f);
        String monthKey = "budget_" + today.getYear() + "_" + today.getMonthValue();
        float currentMonthBudget = prefs.getFloat(monthKey, defaultBudget);
        etBudget.setText(currentMonthBudget > 0 ? String.valueOf(currentMonthBudget) : "");

        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            try {
                float newBudget = Float.parseFloat(etBudget.getText().toString());
                prefs.edit().putFloat(monthKey, newBudget).apply();

                // 记录首次使用预算功能的时间
                if (prefs.getLong("budget_start_time", 0) == 0) {
                    prefs.edit().putLong("budget_start_time", System.currentTimeMillis()).apply();
                }

                // 手动触发一次刷新
                if (viewModel.getAllTransactions().getValue() != null) {
                    calculateMonthHeader(viewModel.getAllTransactions().getValue());
                    adapter.setTransactions(viewModel.getAllTransactions().getValue());
                }
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(getContext(), "请输入有效的金额", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * 计算顶部概览卡片的数据
     */
    /**
     * 修改 calculateMonthHeader，支持动态读取当月预算
     */
    private void calculateMonthHeader(List<Transaction> transactions) {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        float defaultBudget = prefs.getFloat("monthly_budget", 0f);

        LocalDate today = LocalDate.now();
        String monthKey = "budget_" + today.getYear() + "_" + today.getMonthValue();
        float monthlyBudget = prefs.getFloat(monthKey, defaultBudget); // 优先读取当月独立预算

        // 记录首次开启功能的时间
        if (monthlyBudget > 0 && prefs.getLong("budget_start_time", 0) == 0) {
            prefs.edit().putLong("budget_start_time", System.currentTimeMillis()).apply();
        }

        tvHeaderMonthlyBudget.setText(String.format("%.2f", monthlyBudget));

        if (monthlyBudget <= 0) {
            currentMonthSurplus = 0;
            tvTotalSurplus.setText("未设置预算");
            tvHeaderDailyAvailable.setText("0.00");
            return;
        }

        double dailyBudget = (double) monthlyBudget / today.lengthOfMonth();
        double expectedExpenseSoFar = today.getDayOfMonth() * dailyBudget;
        // ... 其余逻辑保持不变 (actualExpenseSoFar的计算等) ...
        double actualExpenseSoFar = 0;
        long startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endOfNow = System.currentTimeMillis();

        if (transactions != null) {
            for (Transaction t : transactions) {
                if (t.date >= startOfMonth && t.date <= endOfNow && t.type == 0) {
                    actualExpenseSoFar += t.amount;
                }
            }
        }

        currentMonthSurplus = expectedExpenseSoFar - actualExpenseSoFar;
        String sign = currentMonthSurplus >= 0 ? "+" : "";
        tvTotalSurplus.setText(String.format("%s%.2f", sign, currentMonthSurplus));
        tvTotalSurplus.setTextColor(ContextCompat.getColor(requireContext(),
                currentMonthSurplus >= 0 ? R.color.app_yellow : R.color.budget_progress_exceed));

        tvHeaderDailyAvailable.setText(String.format("%.2f", dailyBudget + currentMonthSurplus));
    }

    /**
     * 显示添加目标弹窗 (设置创建时间)
     */
    private void showAddGoalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_goal, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etName = view.findViewById(R.id.et_goal_name);
        EditText etTarget = view.findViewById(R.id.et_target_amount);

        view.findViewById(R.id.btn_save_goal).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String targetStr = etTarget.getText().toString().trim();
            if (!name.isEmpty() && !targetStr.isEmpty()) {
                // 关键点：记录今日凌晨的时间戳作为起点
                long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                Goal goal = new Goal(name, Double.parseDouble(targetStr), 0, false, startOfDay);
                viewModel.insertGoal(goal);
                dialog.dismiss();
            } else {
                Toast.makeText(getContext(), "请输入完整信息", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_cancel_goal).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * 显示编辑目标弹窗 (支持修改已存基础值)
     */
    private void showEditGoalDialog(Goal goal) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_goal, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etName = view.findViewById(R.id.et_edit_goal_name);
        EditText etTarget = view.findViewById(R.id.et_edit_target_amount);
        EditText etSaved = view.findViewById(R.id.et_edit_saved_amount);
        CheckBox cbPriority = view.findViewById(R.id.cb_is_priority);

        etName.setText(goal.name);
        etTarget.setText(String.valueOf(goal.targetAmount));
        etSaved.setText(String.valueOf(goal.savedAmount));
        cbPriority.setChecked(goal.isPriority);

        view.findViewById(R.id.btn_update_goal).setOnClickListener(v -> {
            try {
                goal.name = etName.getText().toString();
                goal.targetAmount = Double.parseDouble(etTarget.getText().toString());
                goal.savedAmount = Double.parseDouble(etSaved.getText().toString());

                // 优化：如果勾选了优先，调用 ViewModel 里的专用方法处理单选逻辑
                if (cbPriority.isChecked()) {
                    viewModel.setPriorityGoal(goal);
                } else {
                    goal.isPriority = false;
                    viewModel.updateGoal(goal);
                }
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(getContext(), "数据格式有误", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_delete_goal).setOnClickListener(v -> {
            showConfirmDeleteGoalDialog(goal, dialog);
        });

        dialog.show();
    }

    /**
     * 删除目标的二次确认弹窗 - 已修正 ID 映射
     */
    private void showConfirmDeleteGoalDialog(Goal goal, AlertDialog editDialog) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        // 1. 使用项目现有的确认删除布局
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);

        AlertDialog confirmDialog = builder.create();
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // 2. 【关键修正】：ID 从 tv_message 改为 tv_dialog_message
        TextView tvMessage = view.findViewById(R.id.tv_dialog_message);
        if (tvMessage != null) {
            tvMessage.setText("确定要删除“" + goal.name + "”这个目标吗？\n删除后进度数据将无法找回。");
        }

        // 3. 【关键修正】：ID 匹配布局中的 btn_dialog_confirm
        View btnConfirm = view.findViewById(R.id.btn_dialog_confirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                viewModel.deleteGoal(goal);
                confirmDialog.dismiss();
                if (editDialog != null) editDialog.dismiss();
                Toast.makeText(getContext(), "已删除目标", Toast.LENGTH_SHORT).show();
            });
        }

        // 4. 【关键修正】：ID 匹配布局中的 btn_dialog_cancel
        View btnCancel = view.findViewById(R.id.btn_dialog_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> confirmDialog.dismiss());
        }

        confirmDialog.show();
    }

    /**
     * 内部适配器类
     */
    /**
     * 完整覆写的 GoalAdapter：支持结余自动顺延逻辑
     */
    private class GoalAdapter extends RecyclerView.Adapter<GoalAdapter.GoalViewHolder> {
        private List<Goal> goals = new ArrayList<>();
        private List<Transaction> allTransactions = new ArrayList<>();
        // 核心缓存：存储每个目标 ID 实时分配到的“动态结余”
        private java.util.Map<Integer, Double> surplusAllocationMap = new java.util.HashMap<>();

        public void setGoals(List<Goal> goals) {
            this.goals = goals;
            calculateAndDistributeSurplus(); // 重新分配资金
            notifyDataSetChanged();
        }

        public void setTransactions(List<Transaction> list) {
            this.allTransactions = list;
            calculateAndDistributeSurplus(); // 重新分配资金
            notifyDataSetChanged();
        }

        /**
         * 资金池顺延分配算法
         */
        private void calculateAndDistributeSurplus() {
            surplusAllocationMap.clear();
            if (goals.isEmpty()) return;

            // 1. 确定计算起点：所有目标中最晚（新）创建的时间，作为结余积攒的开始
            // (注：也可以用最早创建时间，取决于你想让用户从哪天开始“攒钱”)
            long earliestStart = Long.MAX_VALUE;
            for (Goal g : goals) {
                if (g.createdAt < earliestStart) earliestStart = g.createdAt;
            }
            if (earliestStart == Long.MAX_VALUE) return;

            // 2. 计算从起点到昨天的总结余池 (Total Surplus Pool)
            double totalPool = calculateTotalPool(earliestStart);
            if (totalPool <= 0) return;

            // 3. 第一顺位：分配给“优先目标”
            Goal priorityGoal = null;
            for (Goal g : goals) {
                if (g.isPriority) {
                    priorityGoal = g;
                    break;
                }
            }

            if (priorityGoal != null) {
                double stillNeeded = Math.max(0, priorityGoal.targetAmount - priorityGoal.savedAmount);
                double allocated = Math.min(totalPool, stillNeeded);
                surplusAllocationMap.put(priorityGoal.id, allocated);
                totalPool -= allocated; // 剩余资金流向下一级
            }

            // 4. 第二顺位：按顺序顺延给其他未完成的目标
            if (totalPool > 0) {
                for (Goal g : goals) {
                    if (g.isPriority) continue; // 优先目标已处理

                    double stillNeeded = Math.max(0, g.targetAmount - g.savedAmount);
                    if (stillNeeded > 0) {
                        double allocated = Math.min(totalPool, stillNeeded);
                        surplusAllocationMap.put(g.id, allocated);
                        totalPool -= allocated;
                    }
                    if (totalPool <= 0) break; // 没钱了，停止分配
                }
            }
        }

        // 在 GoalAdapter 内部
        private double calculateTotalPool(long earliestGoalDate) {
            SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            float defaultBudget = prefs.getFloat("monthly_budget", 0f);

            // 使用系统首次记录的预算时间，如果没记录，使用目标创建时间兜底
            long startTs = prefs.getLong("budget_start_time", earliestGoalDate);
            LocalDate start = Instant.ofEpochMilli(startTs).atZone(ZoneId.systemDefault()).toLocalDate();
            // 规定：资金池从那个月的1号开始算起
            start = start.withDayOfMonth(1);

            LocalDate today = LocalDate.now();
            if (!today.isAfter(start)) return 0;

            // 1. 逐日计算应有的预算结余总额（因为每个月预算可能不同）
            double totalExpected = 0;
            for (LocalDate d = start; d.isBefore(today); d = d.plusDays(1)) {
                String key = "budget_" + d.getYear() + "_" + d.getMonthValue();
                float monthBudget = prefs.getFloat(key, defaultBudget);
                totalExpected += (double) monthBudget / d.lengthOfMonth();
            }

            // 2. 统计这段期间内的总支出
            double actualExpense = 0;
            long startOfCalculation = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

            for (Transaction t : allTransactions) {
                if (t.date >= startOfCalculation && t.date < startOfToday && t.type == 0) {
                    actualExpense += t.amount;
                }
            }
            return totalExpected - actualExpense;
        }

        @NonNull
        @Override
        public GoalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goal_card, parent, false);
            return new GoalViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GoalViewHolder holder, int position) {
            Goal goal = goals.get(position);
            holder.tvName.setText(goal.name);

            // 最终显示的已存金额 = 用户手动填写的基准值 + 系统分配的结余
            double allocatedSurplus = surplusAllocationMap.containsKey(goal.id) ? surplusAllocationMap.get(goal.id) : 0;
            double finalSaved = goal.savedAmount + allocatedSurplus;

            // 优化点：使用主题色小圆点代表优先级
            holder.viewPriorityDot.setVisibility(goal.isPriority ? View.VISIBLE : View.GONE);

            // 更新进度文字和进度条
            holder.tvProgressText.setText(String.format("已实现 %.2f / %.2f", finalSaved, goal.targetAmount));
            int percent = goal.targetAmount > 0 ? (int) ((finalSaved / goal.targetAmount) * 100) : 0;
            holder.tvPercent.setText(Math.max(0, percent) + "%");
            holder.pbGoal.setProgress(Math.max(0, Math.min(percent, 100)));

            holder.itemView.setOnClickListener(v -> showEditGoalDialog(goal));
        }

        @Override
        public int getItemCount() { return goals.size(); }

        class GoalViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvProgressText, tvPercent;
            View viewPriorityDot;
            android.widget.ProgressBar pbGoal;

            public GoalViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_goal_name);
                viewPriorityDot = itemView.findViewById(R.id.view_priority_dot);
                tvProgressText = itemView.findViewById(R.id.tv_goal_progress_text);
                tvPercent = itemView.findViewById(R.id.tv_goal_percent);
                pbGoal = itemView.findViewById(R.id.pb_goal);
            }
        }
    }
}