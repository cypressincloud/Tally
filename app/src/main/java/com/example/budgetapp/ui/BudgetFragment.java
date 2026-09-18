package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Goal;
import com.example.budgetapp.database.BudgetPlan;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.BudgetCalculator;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class BudgetFragment extends Fragment {

    private FinanceViewModel viewModel;
    private TextView tvTotalSurplus, tvHeaderMonthlyBudget, tvHeaderDailyAvailable;

    // ViewPager 与 页面组件
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private GoalAdapter goalAdapter;
    private DetailedBudgetAdapter detailedAdapter;
    private PlanAdapter planAdapter;
    private ActivePlanAdapter activePlanAdapter;
    private boolean isDetailedEnabled = false;

    private double currentMonthSurplus = 0;
    
    // FAB 滚动隐藏相关
    private LinearLayout fabContainer;
    private FabScrollListener fabScrollListener;
    private FabGestureListener fabGestureListener;
    private boolean isFabVisible = true;
    private boolean isFabAnimating = false;
    private RecyclerView currentRecyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        viewModel.settleExpiredBudgetPlans();

        tvTotalSurplus = view.findViewById(R.id.tv_total_surplus);
        tvHeaderMonthlyBudget = view.findViewById(R.id.tv_header_monthly_budget);
        tvHeaderDailyAvailable = view.findViewById(R.id.tv_header_daily_available);

        // 计划金额和日期统一在“计划”页维护；顶部汇总卡仅用于展示，避免继续打开旧月度预算弹窗。
        view.findViewById(R.id.layout_monthly_budget).setOnClickListener(null);
        view.findViewById(R.id.btn_budget_history).setOnClickListener(v -> startActivity(new Intent(getContext(), BudgetHistoryActivity.class)));

        // 初始化 FAB 容器
        fabContainer = view.findViewById(R.id.fab_container_budget);
        fabScrollListener = new FabScrollListener();
        fabGestureListener = new FabGestureListener();

        goalAdapter = new GoalAdapter();
        detailedAdapter = new DetailedBudgetAdapter();
        planAdapter = new PlanAdapter();
        activePlanAdapter = new ActivePlanAdapter();

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        isDetailedEnabled = prefs.getBoolean("is_detailed_budget_enabled", false);

        viewPager = view.findViewById(R.id.vp_budget);
        tabLayout = view.findViewById(R.id.tab_layout_budget);
        TextView tvGoalsTitle = view.findViewById(R.id.tv_goals_title); // 新增的标题

        BudgetPagerAdapter pagerAdapter = new BudgetPagerAdapter(false);
        viewPager.setAdapter(pagerAdapter);

        View btnAddGoal = view.findViewById(R.id.btn_add_goal);
        View btnHistory = view.findViewById(R.id.btn_budget_history);

        {
            tabLayout.setVisibility(View.VISIBLE);
            tvGoalsTitle.setVisibility(View.GONE);

            new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                tab.setText(position == 0 ? "预算" : "计划");
            }).attach();

            int lastTab = prefs.getInt("last_budget_tab", 0);
            viewPager.setCurrentItem(lastTab, false);

            // 更新按钮初始状态
                    updateActionButtonsVisibility(lastTab, btnAddGoal, btnHistory);
                    btnAddGoal.setContentDescription(lastTab == 0 ? "添加储蓄目标" : "添加预算计划");
                    btnAddGoal.setOnClickListener(v -> {
                        if (lastTab == 0) showAddGoalDialog(); else showAddPlanDialog();
                    });

            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    prefs.edit().putInt("last_budget_tab", position).apply();
                    // 核心逻辑：切换页面时更新按钮可见性
                    updateActionButtonsVisibility(position, btnAddGoal, btnHistory);
                    btnAddGoal.setContentDescription(position == 0 ? "添加储蓄目标" : "添加预算计划");
                    btnAddGoal.setOnClickListener(v -> {
                        if (position == 0) showAddGoalDialog(); else showAddPlanDialog();
                    });
                    // 切换页面时重新附加滚动监听器
                    attachScrollListeners(position);
                }
            });
        }

        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), transactions -> {
            if (viewModel.getAllBudgetPlans().getValue() == null || viewModel.getAllBudgetPlans().getValue().isEmpty()) {
                calculateMonthHeader(transactions);
            } else {
                updatePlanSummary(viewModel.getAllBudgetPlans().getValue(), transactions);
            }
            goalAdapter.setTransactions(transactions);
            // Plan cards also derive their progress from transactions. Refresh
            // both adapters when the asynchronous transaction query completes.
            planAdapter.notifyDataSetChanged();
            activePlanAdapter.notifyDataSetChanged();
            if (isDetailedEnabled) calculateDetailedBudgets(transactions);
        });

        viewModel.getAllGoals().observe(getViewLifecycleOwner(), goals -> {
            if (goals != null) goalAdapter.setGoals(goals);
        });
        viewModel.getAllBudgetPlans().observe(getViewLifecycleOwner(), plans -> {
            if (plans != null) {
                planAdapter.setPlans(plans);
                List<BudgetPlan> active = new ArrayList<>();
                LocalDate today = LocalDate.now();
                for (BudgetPlan plan : plans) {
                    LocalDate start = java.time.Instant.ofEpochMilli(plan.startDate).atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate end = java.time.Instant.ofEpochMilli(plan.endDate).atZone(ZoneId.systemDefault()).toLocalDate();
                    if (plan.enabled && !today.isBefore(start) && !today.isAfter(end)) active.add(plan);
                }
                activePlanAdapter.setPlans(active);
                updatePlanSummary(plans, viewModel.getAllTransactions().getValue());
            }
        });

        return view;
    }

    private void updatePlanSummary(List<BudgetPlan> plans, List<Transaction> transactions) {
        LocalDate today = LocalDate.now();
        double total = 0;
        double remaining = 0;
        for (BudgetPlan plan : plans) {
            LocalDate start = java.time.Instant.ofEpochMilli(plan.startDate).atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate end = java.time.Instant.ofEpochMilli(plan.endDate).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!plan.enabled || today.isBefore(start) || today.isAfter(end)) continue;
            total += plan.totalAmount;
            remaining += Math.max(0, plan.totalAmount - com.example.budgetapp.util.BudgetCalculator.expenseBetween(
                    transactions, plan.startDate, today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        }
        tvTotalSurplus.setText(String.format("%.2f", remaining));
        tvHeaderMonthlyBudget.setText(String.format("%.2f", total));
        tvHeaderDailyAvailable.setText(String.format("%.2f", remaining));
    }

    /**
     * 根据当前页面位置控制按钮显示
     * The left page owns savings goals; the right page owns budget plans.
     */
    private void updateActionButtonsVisibility(int position, View btnAdd, View btnHistory) {
        btnAdd.setVisibility(View.VISIBLE);
        btnHistory.setVisibility(View.VISIBLE);
    }
    
    /**
     * 附加滚动监听器到当前页面的 RecyclerView
     * @param position 当前页面位置（0=预算与储蓄目标，1=预算计划）
     */
    private void attachScrollListeners(int position) {
        // 延迟获取 RecyclerView，确保 ViewPager2 已经创建了子视图
        viewPager.postDelayed(() -> {
            RecyclerView rv = findRecyclerViewInViewPager(position);
            if (rv != null && rv != currentRecyclerView) {
                // 移除旧的监听器
                detachScrollListeners();
                
                // 附加新的监听器
                currentRecyclerView = rv;
                currentRecyclerView.addOnScrollListener(fabScrollListener);
                currentRecyclerView.addOnItemTouchListener(fabGestureListener);
            }
        }, 100);
    }
    
    /**
     * 移除滚动监听器
     */
    private void detachScrollListeners() {
        if (currentRecyclerView != null) {
            currentRecyclerView.removeOnScrollListener(fabScrollListener);
            currentRecyclerView.removeOnItemTouchListener(fabGestureListener);
            currentRecyclerView = null;
        }
    }
    
    /**
     * 在 ViewPager2 中查找指定位置的 RecyclerView
     */
    private RecyclerView findRecyclerViewInViewPager(int position) {
        if (viewPager.getChildCount() == 0) return null;
        
        // ViewPager2 内部有一个 RecyclerView
        View vpChild = viewPager.getChildAt(0);
        if (vpChild instanceof RecyclerView) {
            RecyclerView vpRecyclerView = (RecyclerView) vpChild;
            // 查找当前显示的页面
            RecyclerView.ViewHolder holder = vpRecyclerView.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView instanceof RecyclerView) {
                return (RecyclerView) holder.itemView;
            }
        }
        return null;
    }
    
    /**
     * 隐藏 FAB 按钮（带动画）
     */
    private void hideFab() {
        if (!isFabVisible || isFabAnimating || fabContainer == null) return;
        
        isFabAnimating = true;
        fabContainer.animate()
                .translationY(fabContainer.getHeight() + 20)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    fabContainer.setVisibility(View.GONE);
                    isFabVisible = false;
                    isFabAnimating = false;
                })
                .start();
    }
    
    /**
     * 显示 FAB 按钮（带动画）
     */
    private void showFab() {
        if (isFabVisible || isFabAnimating || fabContainer == null) return;
        
        isFabAnimating = true;
        fabContainer.setVisibility(View.VISIBLE);
        fabContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    isFabVisible = true;
                    isFabAnimating = false;
                })
                .start();
    }

    /**
     * 计算详细分类预算的已用进度
     */
    private void calculateDetailedBudgets(List<Transaction> transactions) {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        List<CategoryBudgetModel> list = new ArrayList<>();

        LocalDate today = LocalDate.now();
        long startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // 【修复】将 endOfNow 改为月底，保证逻辑一致
        long endOfMonth = today.withDayOfMonth(today.lengthOfMonth()).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

        List<String> expenseCategories = CategoryManager.getExpenseCategories(requireContext());
        for (String cat : expenseCategories) {
            float limit = prefs.getFloat("budget_cat_" + cat, 0f);
            if (limit > 0) {
                double spent = 0;
                for (Transaction t : transactions) {
                    // 【修改点】：增加 !t.excludeFromBudget 判断
                    if (t.type == 0 && t.date >= startOfMonth && t.date <= endOfMonth && cat.equals(t.category) && !t.excludeFromBudget) {
                        spent += t.amount;
                    }
                }
                list.add(new CategoryBudgetModel(cat, limit, spent));
            }
        }
        detailedAdapter.setData(list);
    }

    // --- 页面滑动适配器 ---
    private class BudgetPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private boolean isDetailed;
        public BudgetPagerAdapter(boolean detailed) { this.isDetailed = detailed; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RecyclerView rv = new RecyclerView(parent.getContext());
            rv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            rv.setClipToPadding(false);
            rv.setPadding(0, 0, 0, 300); // 留出底部悬浮按钮的空间
            rv.setLayoutManager(new LinearLayoutManager(parent.getContext()));
            return new RecyclerView.ViewHolder(rv) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RecyclerView rv = (RecyclerView) holder.itemView;
            rv.setAdapter(position == 0
                    ? new androidx.recyclerview.widget.ConcatAdapter(activePlanAdapter, goalAdapter)
                    : planAdapter);

        }

        @Override
        public int getItemCount() { return 2; }

    }

    /**
     * 弹出对话框修改特定分类的预算
     */
    private void showEditCategoryBudgetDialog(CategoryBudgetModel item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_set_month_budget, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etBudget = view.findViewById(R.id.et_month_budget);

        tvTitle.setText("修改 " + item.name + " 预算");
        etBudget.setText(String.valueOf(item.limit));

        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            try {
                float newLimit = Float.parseFloat(etBudget.getText().toString());
                if (newLimit < 0) throw new Exception();

                SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                // 保存分类预算：使用与 calculateDetailedBudgets 一致的 Key
                prefs.edit().putFloat("budget_cat_" + item.name, newLimit).apply();

                // 刷新数据
                if (viewModel.getAllTransactions().getValue() != null) {
                    calculateMonthHeader(viewModel.getAllTransactions().getValue());
                    calculateDetailedBudgets(viewModel.getAllTransactions().getValue());
                }

                dialog.dismiss();
                Toast.makeText(getContext(), "预算已更新", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "请输入有效的金额", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // --- 详细预算的数据模型与适配器 ---
    static class CategoryBudgetModel {
        String name; float limit; double spent;
        CategoryBudgetModel(String n, float l, double s) { name = n; limit = l; spent = s; }
    }

    private class DetailedBudgetAdapter extends RecyclerView.Adapter<DetailedBudgetAdapter.ViewHolder> {
        private List<CategoryBudgetModel> items = new ArrayList<>();
        public void setData(List<CategoryBudgetModel> newItems) {
            this.items = newItems; notifyDataSetChanged();
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detailed_budget_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CategoryBudgetModel item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvProgress.setText(String.format("已用 %.2f / %.2f", item.spent, item.limit));

            int percent = (int) ((item.spent / item.limit) * 100);
            holder.pb.setProgress(Math.min(percent, 100));

            // 超出预算变红，否则为绿色
            if (item.spent > item.limit) {
                holder.pb.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.budget_progress_exceed)));
                holder.tvProgress.setTextColor(ContextCompat.getColor(requireContext(), R.color.budget_progress_exceed));
            } else {
                holder.pb.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.budget_progress_safe)));
                holder.tvProgress.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }

            // 【新增】：给详细预算子卡片增加 90% 透明度
            SharedPreferences prefs = holder.itemView.getContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
            if (holder.itemView instanceof androidx.cardview.widget.CardView) {
                androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) holder.itemView;
                int surfaceColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.white);
                card.setCardBackgroundColor(isCustomBg ?
                        androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230) : surfaceColor);
            }

            // 添加点击监听
            holder.itemView.setOnClickListener(v -> showEditCategoryBudgetDialog(item));
        }
        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvProgress; ProgressBar pb;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_cat_name);
                tvProgress = v.findViewById(R.id.tv_cat_progress);
                pb = v.findViewById(R.id.pb_cat_budget);
            }
        }
    }

    // ================= 以下为原封不动保留的代码 =================

    private void calculateMonthHeader(List<Transaction> transactions) {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        LocalDate today = LocalDate.now();

        float monthlyBudget = 0;

        // 获取当月总预算
        if (isDetailedEnabled) {
            List<String> expenseCategories = CategoryManager.getExpenseCategories(requireContext());
            for (String cat : expenseCategories) {
                monthlyBudget += prefs.getFloat("budget_cat_" + cat, 0f);
            }
        } else {
            float defaultBudget = prefs.getFloat("monthly_budget", 0f);
            String monthKey = "budget_" + today.getYear() + "_" + today.getMonthValue();
            monthlyBudget = prefs.getFloat(monthKey, defaultBudget);
        }

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

        double actualExpenseSoFar = 0;

        // 【修复1】修正时间边界：涵盖整个月，防止丢失未来日期（但属本月）的账单
        long startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // 下个月零点作为结束边界，支持跨日摊销并排除不计入预算的流水。
        long endOfMonthExclusive = today.withDayOfMonth(today.lengthOfMonth()).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        actualExpenseSoFar = BudgetCalculator.expenseBetween(
                transactions, startOfMonth, endOfMonthExclusive);

        // 【修复3】总结余应该是真实剩余预算 = 总预算 - 实际总支出
        double monthRemaining = monthlyBudget - actualExpenseSoFar;
        String sign = monthRemaining >= 0 ? "+" : ""; // 按照原逻辑保留加号
        tvTotalSurplus.setText(String.format("%s%.2f", sign, monthRemaining));
        tvTotalSurplus.setTextColor(ContextCompat.getColor(requireContext(),
                monthRemaining >= 0 ? R.color.app_blue : R.color.budget_progress_exceed));

        // 【修复2】更合理的“今日可用”计算：推荐采用“剩余平摊法”
        int remainingDays = today.lengthOfMonth() - today.getDayOfMonth() + 1;
        // 如果整体预算超支，今日可用为0；否则平摊到剩余天数
        double dailyAvailable = monthRemaining > 0 ? (monthRemaining / remainingDays) : 0;

        // 如果你坚持要用原作者的“进度结余法”，可以替换为这句:
        // double expectedExpenseSoFar = today.getDayOfMonth() * ((double) monthlyBudget / today.lengthOfMonth());
        // double dailyAvailable = expectedExpenseSoFar - actualExpenseSoFar;

        tvHeaderDailyAvailable.setText(String.format("%.2f", Math.max(0, dailyAvailable)));
    }

    private void showSetMonthBudgetDialog() {
        // 【核心修改】：开启详细预算时，拦截手动修改单月总预算
        if (isDetailedEnabled) {
            Toast.makeText(getContext(), "开启详细预算时，请在设置中修改分类金额", Toast.LENGTH_SHORT).show();
            return;
        }

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
                if (!Float.isFinite(newBudget) || newBudget <= 0) throw new IllegalArgumentException();
                prefs.edit().putFloat(monthKey, newBudget).apply();
                if (prefs.getLong("budget_start_time", 0) == 0) {
                    prefs.edit().putLong("budget_start_time", System.currentTimeMillis()).apply();
                }
                if (viewModel.getAllTransactions().getValue() != null) {
                    calculateMonthHeader(viewModel.getAllTransactions().getValue());
                    goalAdapter.setTransactions(viewModel.getAllTransactions().getValue());
                }
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(getContext(), "请输入有效的金额", Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

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
                try {
                    double target = Double.parseDouble(targetStr);
                    if (!Double.isFinite(target) || target <= 0) throw new IllegalArgumentException();
                    long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    Goal goal = new Goal(name, target, 0, false, startOfDay);
                    viewModel.insertGoal(goal);
                    dialog.dismiss();
                } catch (Exception e) {
                    Toast.makeText(getContext(), "目标金额必须大于 0", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "请输入完整信息", Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.btn_cancel_goal).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showAddPlanDialog() {
        showPlanDialog(null);
    }

    private void showPlanDialog(@Nullable BudgetPlan existing) {
        View content = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_budget_plan, null);
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(content).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        EditText name = content.findViewById(R.id.et_plan_name);
        EditText start = content.findViewById(R.id.et_plan_start);
        EditText end = content.findViewById(R.id.et_plan_end);
        EditText amount = content.findViewById(R.id.et_plan_amount);
        TextView title = content.findViewById(R.id.tv_plan_dialog_title);
        String today = LocalDate.now().toString();
        if (existing == null) {
            start.setText(today);
            end.setText(today);
        } else {
            title.setText("编辑预算计划");
            name.setText(existing.name);
            start.setText(java.time.Instant.ofEpochMilli(existing.startDate).atZone(ZoneId.systemDefault()).toLocalDate().toString());
            end.setText(java.time.Instant.ofEpochMilli(existing.endDate).atZone(ZoneId.systemDefault()).toLocalDate().toString());
            amount.setText(String.valueOf(existing.totalAmount));
        }
        start.setFocusable(false);
        end.setFocusable(false);
        start.setOnClickListener(v -> showPlanDatePicker(start));
        end.setOnClickListener(v -> showPlanDatePicker(end));
        content.findViewById(R.id.btn_save_plan).setOnClickListener(v -> {
            try {
                LocalDate s = LocalDate.parse(start.getText().toString().trim());
                LocalDate e = LocalDate.parse(end.getText().toString().trim());
                double total = Double.parseDouble(amount.getText().toString().trim());
                if (name.getText().toString().trim().isEmpty() || e.isBefore(s)
                        || !Double.isFinite(total) || total <= 0) {
                    throw new IllegalArgumentException();
                }
                long sm = s.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long em = e.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (existing == null) {
                    viewModel.insertBudgetPlan(new BudgetPlan(name.getText().toString().trim(), sm, em, total));
                } else {
                    existing.name = name.getText().toString().trim();
                    existing.startDate = sm;
                    existing.endDate = em;
                    existing.totalAmount = total;
                    viewModel.updateBudgetPlan(existing);
                }
                dialog.dismiss();
            } catch (Exception ex) {
                Toast.makeText(getContext(), "请输入有效的名称、日期和金额", Toast.LENGTH_SHORT).show();
            }
        });
        content.findViewById(R.id.btn_cancel_plan).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showPlanDatePicker(EditText field) {
        LocalDate initial;
        try {
            initial = LocalDate.parse(field.getText().toString());
        } catch (Exception ignored) {
            initial = LocalDate.now();
        }
        new android.app.DatePickerDialog(requireContext(), (picker, year, month, day) ->
                field.setText(LocalDate.of(year, month + 1, day).toString()),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.Holder> {
        private List<BudgetPlan> plans = new ArrayList<>();
        void setPlans(List<BudgetPlan> value) { plans = value; notifyDataSetChanged(); }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_budget_plan, p, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
            BudgetPlan plan = plans.get(pos);
            h.name.setText(plan.name);
            LocalDate s = java.time.Instant.ofEpochMilli(plan.startDate).atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate e = java.time.Instant.ofEpochMilli(plan.endDate).atZone(ZoneId.systemDefault()).toLocalDate();
            h.range.setText(s + " 至 " + e);
            h.amount.setText(String.format("%.2f", plan.totalAmount));
            if (plan.settled) {
                h.allocated.setVisibility(View.VISIBLE);
                h.allocated.setText(String.format("已分配给储蓄目标：%.2f", plan.allocatedToGoals));
            } else {
                h.allocated.setVisibility(View.GONE);
            }
            h.effective.setText(plan.settled ? "已结束" :
                    (plan.enabled && !LocalDate.now().isBefore(s) && !LocalDate.now().isAfter(e) ? "生效中" : "未生效"));
            List<Transaction> transactions = viewModel.getAllTransactions().getValue();
            long endExclusive = e.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            double spent = com.example.budgetapp.util.BudgetCalculator.expenseBetween(
                    transactions, plan.startDate, Math.min(System.currentTimeMillis(), endExclusive));
            h.amount.setText(String.format("已消耗 %.2f / 总预算 %.2f", spent, plan.totalAmount));
            h.progress.setProgress(com.example.budgetapp.util.BudgetCalculator.progress(spent, plan.totalAmount));
            h.progress.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                    spent > plan.totalAmount ? R.color.budget_progress_exceed : R.color.app_blue)));
            h.itemView.setOnClickListener(v -> {
                if (plan.settled) Toast.makeText(getContext(), "已结束的预算计划不能修改", Toast.LENGTH_SHORT).show();
                else showPlanDialog(plan);
            });
            h.itemView.setOnLongClickListener(v -> { showConfirmDeletePlanDialog(plan); return true; });
        }
        @Override public int getItemCount() { return plans.size(); }
        class Holder extends RecyclerView.ViewHolder {
            TextView name, range, amount, effective, allocated; ProgressBar progress;
            Holder(View v) { super(v); name=v.findViewById(R.id.tv_plan_name); range=v.findViewById(R.id.tv_plan_range); amount=v.findViewById(R.id.tv_plan_amount); effective=v.findViewById(R.id.tv_plan_effective); allocated=v.findViewById(R.id.tv_plan_allocated); progress=v.findViewById(R.id.pb_plan); }
        }
    }

    private void showConfirmDeletePlanDialog(BudgetPlan plan) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        TextView title = view.findViewById(R.id.tv_dialog_title);
        TextView message = view.findViewById(R.id.tv_dialog_message);
        if (title != null) title.setText("删除预算计划");
        if (message != null) message.setText("确定要删除“" + plan.name + "”吗？删除后无法恢复。");
        view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            viewModel.deleteBudgetPlan(plan);
            dialog.dismiss();
        });
        dialog.show();
    }

    private class ActivePlanAdapter extends RecyclerView.Adapter<ActivePlanAdapter.Holder> {
        private List<BudgetPlan> plans = new ArrayList<>();
        void setPlans(List<BudgetPlan> value) { plans = value; notifyDataSetChanged(); }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_active_budget_card, p, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
            BudgetPlan plan = plans.get(pos);
            LocalDate today = LocalDate.now();
            LocalDate end = java.time.Instant.ofEpochMilli(plan.endDate).atZone(ZoneId.systemDefault()).toLocalDate();
            long endExclusive = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            List<Transaction> transactions = viewModel.getAllTransactions().getValue();
            double spent = com.example.budgetapp.util.BudgetCalculator.expenseBetween(transactions, plan.startDate, Math.min(System.currentTimeMillis(), endExclusive));
            double remaining = Math.max(0, plan.totalAmount - spent);
            h.name.setText(plan.name);
            h.effective.setText("生效至 " + end);
            h.remaining.setText(String.format("%.2f", remaining));
            h.spent.setText(String.format("已消耗 %.2f", spent));
            h.total.setText(String.format("%.2f", plan.totalAmount));
            h.daily.setText(String.format("%.2f",
                    BudgetCalculator.remainingDailyBudget(plan, today, transactions)));
            h.progress.setProgress(com.example.budgetapp.util.BudgetCalculator.progress(spent, plan.totalAmount));
            h.progress.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                    spent > plan.totalAmount ? R.color.budget_progress_exceed : R.color.app_blue)));
            h.itemView.setOnClickListener(v -> showPlanDialog(plan));
        }
        @Override public int getItemCount() { return plans.size(); }
        class Holder extends RecyclerView.ViewHolder {
            TextView name,effective,remaining,spent,total,daily; ProgressBar progress;
            Holder(View v) { super(v); name=v.findViewById(R.id.tv_active_plan_name); effective=v.findViewById(R.id.tv_active_plan_effective); remaining=v.findViewById(R.id.tv_active_plan_remaining); spent=v.findViewById(R.id.tv_active_plan_spent); total=v.findViewById(R.id.tv_active_plan_total); daily=v.findViewById(R.id.tv_active_plan_daily); progress=v.findViewById(R.id.pb_active_plan); }
        }
    }

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
                if (!Double.isFinite(goal.targetAmount) || goal.targetAmount <= 0
                        || !Double.isFinite(goal.savedAmount) || goal.savedAmount < 0) {
                    throw new IllegalArgumentException();
                }
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

        view.findViewById(R.id.btn_finish_goal).setOnClickListener(v -> {
            goal.isFinished = true;
            goal.finishedDate = System.currentTimeMillis();
            goal.isPriority = false;
            viewModel.updateGoal(goal);
            dialog.dismiss();
            Toast.makeText(getContext(), "🎉 目标已完成！已归档至历史记录", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btn_delete_goal).setOnClickListener(v -> showConfirmDeleteGoalDialog(goal, dialog));
        dialog.show();
    }

    private void showConfirmDeleteGoalDialog(Goal goal, AlertDialog editDialog) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);

        AlertDialog confirmDialog = builder.create();
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvMessage = view.findViewById(R.id.tv_dialog_message);
        if (tvMessage != null) {
            tvMessage.setText("确定要删除“" + goal.name + "”这个目标吗？\n删除后进度数据将无法找回。");
        }

        View btnConfirm = view.findViewById(R.id.btn_dialog_confirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                viewModel.deleteGoal(goal);
                confirmDialog.dismiss();
                if (editDialog != null) editDialog.dismiss();
                Toast.makeText(getContext(), "已删除目标", Toast.LENGTH_SHORT).show();
            });
        }
        View btnCancel = view.findViewById(R.id.btn_dialog_cancel);
        if (btnCancel != null) btnCancel.setOnClickListener(v -> confirmDialog.dismiss());

        confirmDialog.show();
    }

    private class GoalAdapter extends RecyclerView.Adapter<GoalAdapter.GoalViewHolder> {
        private List<Goal> goals = new ArrayList<>();
        private List<Transaction> allTransactions = new ArrayList<>();
        private java.util.Map<Integer, Double> surplusAllocationMap = new java.util.HashMap<>();

        public void setGoals(List<Goal> goals) {
            List<Goal> activeGoals = new ArrayList<>();
            for (Goal g : goals) if (!g.isFinished) activeGoals.add(g);
            this.goals = activeGoals;
            calculateAndDistributeSurplus();
            notifyDataSetChanged();
        }

        public void setTransactions(List<Transaction> list) {
            this.allTransactions = list;
            calculateAndDistributeSurplus();
            notifyDataSetChanged();
        }

        private void calculateAndDistributeSurplus() {
            surplusAllocationMap.clear();
            if (goals.isEmpty()) return;
            for (Goal g : goals) surplusAllocationMap.put(g.id, 0.0);

            List<Goal> sortedGoals = new ArrayList<>(goals);
            sortedGoals.sort((g1, g2) -> {
                if (g1.isPriority && !g2.isPriority) return -1;
                if (!g1.isPriority && g2.isPriority) return 1;
                return Long.compare(g1.createdAt, g2.createdAt);
            });

            SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            float defaultBudget = prefs.getFloat("monthly_budget", 0f);

            long earliestGoal = Long.MAX_VALUE;
            for (Goal g : goals) {
                if (g.createdAt < earliestGoal) earliestGoal = g.createdAt;
            }
            long startTs = prefs.getLong("budget_start_time", earliestGoal);
            LocalDate start = java.time.Instant.ofEpochMilli(startTs).atZone(ZoneId.systemDefault()).toLocalDate();
            start = start.withDayOfMonth(1);

            LocalDate today = LocalDate.now();
            if (!start.isBefore(today)) return;

            double pool = 0;

            for (LocalDate d = start; d.isBefore(today); d = d.plusDays(1)) {
                String key = "budget_" + d.getYear() + "_" + d.getMonthValue();
                float monthBudget = prefs.getFloat(key, defaultBudget);
                double dailyBudget = monthBudget > 0
                        ? BudgetCalculator.distributeEvenly(monthBudget, d.lengthOfMonth())
                        .get(d.getDayOfMonth() - 1) : 0;

                long startOfDay = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long endOfDay = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                double expenseToday = BudgetCalculator.expenseBetween(
                        allTransactions, startOfDay, endOfDay);

                boolean hasActiveGoal = false;
                for (Goal g : sortedGoals) {
                    if (g.isFinished) continue;
                    LocalDate createDate = java.time.Instant.ofEpochMilli(g.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
                    if (!d.isBefore(createDate)) {
                        hasActiveGoal = true;
                        break;
                    }
                }

                if (hasActiveGoal) {
                    pool += (dailyBudget - expenseToday);
                    if (pool > 0) {
                        for (Goal g : sortedGoals) {
                            if (g.isFinished) continue;
                            LocalDate createDate = java.time.Instant.ofEpochMilli(g.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
                            if (d.isBefore(createDate)) continue;

                            double allocated = surplusAllocationMap.get(g.id);
                            double needed = g.targetAmount - g.savedAmount - allocated;
                            if (needed > 0) {
                                double take = Math.min(pool, needed);
                                surplusAllocationMap.put(g.id, allocated + take);
                                pool -= take;
                            }
                            if (pool <= 0) break;
                        }
                    }
                } else {
                    pool = 0;
                }
            }
        }

        @NonNull @Override
        public GoalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new GoalViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goal_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull GoalViewHolder holder, int position) {
            Goal goal = goals.get(position);
            holder.tvName.setText(goal.name);

            // savedAmount is updated when an expired budget plan is settled.
            // Do not add the legacy projection again or the card double-counts it.
            double finalSaved = goal.savedAmount;

            holder.viewPriorityDot.setVisibility(goal.isPriority ? View.VISIBLE : View.GONE);
            holder.tvProgressText.setText(String.format("已实现 %.2f / %.2f", finalSaved, goal.targetAmount));
            int percent = goal.targetAmount > 0 ? (int) ((finalSaved / goal.targetAmount) * 100) : 0;
            holder.tvPercent.setText(Math.max(0, percent) + "%");
            holder.pbGoal.setProgress(Math.max(0, Math.min(percent, 100)));


            // 自定义背景模式下保持储蓄目标卡片可读。
            SharedPreferences prefs = holder.itemView.getContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
            if (holder.itemView instanceof androidx.cardview.widget.CardView) {
                androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) holder.itemView;
                int surfaceColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.white);
                card.setCardBackgroundColor(isCustomBg ?
                        androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230) : surfaceColor);
            }

            holder.itemView.setOnClickListener(v -> showEditGoalDialog(goal));
        }
        @Override public int getItemCount() { return goals.size(); }

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

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
        updateFragmentTransparency(isCustomBg);
        
        // 重置 FAB 按钮状态
        if (fabContainer != null) {
            fabContainer.setVisibility(View.VISIBLE);
            fabContainer.setAlpha(1f);
            fabContainer.setTranslationY(0f);
            isFabVisible = true;
            isFabAnimating = false;
        }
        
        // 重新附加滚动监听器
        if (viewPager != null) {
            int currentPosition = viewPager.getCurrentItem();
            viewPager.post(() -> attachScrollListeners(currentPosition));
        }
    }
    
    @Override
    public void onDestroyView() {
        // 移除监听器，防止内存泄漏
        detachScrollListeners();
        fabScrollListener = null;
        fabGestureListener = null;
        fabContainer = null;
        super.onDestroyView();
    }

    private void updateFragmentTransparency(boolean isCustomBg) {
        View view = getView();
        if (view == null) return;

        // 之前让你在顶部加过的标题 ID
        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout_budget);

        // 获取我们需要改质感的卡片和按钮
        androidx.cardview.widget.CardView cardMonthSummary = view.findViewById(R.id.card_month_summary);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnAddGoal = view.findViewById(R.id.btn_add_goal);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnHistory = view.findViewById(R.id.btn_budget_history);

        if (isCustomBg) {
            // 1. 基础背景全透明
            view.setBackgroundColor(Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(Color.TRANSPARENT);
            if (tabLayout != null) tabLayout.setBackgroundColor(Color.TRANSPARENT);

            // 2. 顶部总计卡片：90%透明度 (230)
            if (cardMonthSummary != null) {
                int surfaceColor = ContextCompat.getColor(requireContext(), R.color.white);
                int translucentSurface = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230);
                cardMonthSummary.setCardBackgroundColor(translucentSurface);
            }

            // 3. 添加按钮 (黄底白字)：90%透明度
            if (btnAddGoal != null) {
                int fabColor = ContextCompat.getColor(requireContext(), R.color.app_blue);
                int translucentFab = androidx.core.graphics.ColorUtils.setAlphaComponent(fabColor, 230);
                btnAddGoal.setBackgroundTintList(ColorStateList.valueOf(translucentFab));
            }

            // 4. 历史记录按钮 (白底黄字)：90%透明度
            if (btnHistory != null) {
                int fabColor = ContextCompat.getColor(requireContext(), R.color.white);
                int translucentFab = androidx.core.graphics.ColorUtils.setAlphaComponent(fabColor, 230);
                btnHistory.setBackgroundTintList(ColorStateList.valueOf(translucentFab));
            }

        } else {
            // ================= 恢复系统默认模式 =================
            view.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);
            if (tabLayout != null) tabLayout.setBackgroundResource(R.color.bar_background);

            if (cardMonthSummary != null) {
                cardMonthSummary.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white));
            }
            if (btnAddGoal != null) {
                btnAddGoal.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.app_blue)));
            }
            if (btnHistory != null) {
                btnHistory.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white)));
            }
        }

        // 通知下面的列表适配器也刷新自己的背景卡片
        if (goalAdapter != null) goalAdapter.notifyDataSetChanged();
        if (detailedAdapter != null) detailedAdapter.notifyDataSetChanged();
    }

    private void applyThemeBackground() {
        View view = getView();
        if (view == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout_budget);

        if (prefs.getInt("theme_mode", -1) == 3) {
            // 自定义图片背景模式，设置原背景完全透明
            view.setBackgroundColor(Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(Color.TRANSPARENT);
            if (tabLayout != null) tabLayout.setBackgroundColor(Color.TRANSPARENT);
        } else {
            // 日间/夜间模式，恢复原背景
            view.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);
            if (tabLayout != null) tabLayout.setBackgroundResource(R.color.bar_background);
        }
    }
    
    // ========== FAB 滚动隐藏功能 ==========
    
    /**
     * FAB 滚动监听器内部类
     * 监听 RecyclerView 的滚动事件，根据滚动方向自动显示/隐藏浮动按钮
     */
    private class FabScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            
            // 向上滚动（dy > 0）隐藏按钮
            if (dy > 0) {
                hideFab();
            }
            // 向下滚动（dy < 0）显示按钮
            else if (dy < 0) {
                showFab();
            }
        }
    }
    
    /**
     * FAB 手势监听器内部类
     * 监听触摸手势，即使列表内容少不需要滚动，也能响应上下滑动手势
     */
    private class FabGestureListener implements RecyclerView.OnItemTouchListener {
        private android.view.GestureDetector gestureDetector;
        
        FabGestureListener() {
            gestureDetector = new android.view.GestureDetector(getContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(android.view.MotionEvent e1, android.view.MotionEvent e2, float distanceX, float distanceY) {
                    // distanceY > 0 表示向上滑动，< 0 表示向下滑动
                    if (distanceY > 0) {
                        // 向上滑动，隐藏按钮
                        hideFab();
                    } else if (distanceY < 0) {
                        // 向下滑动，显示按钮
                        showFab();
                    }
                    return false;
                }
            });
        }
        
        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
            gestureDetector.onTouchEvent(e);
            return false;
        }
        
        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
        }
        
        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        }
    }

}
