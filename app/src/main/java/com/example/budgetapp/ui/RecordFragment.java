package com.example.budgetapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.budgetapp.ai.AiConfig;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.RenewalItem;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RecordFragment extends Fragment {
    private AssistantConfig assistantConfig;
    private FinanceViewModel viewModel;
    private CalendarAdapter adapter;
    private YearMonth currentMonth;
    private LocalDate selectedDate;

    // UI 控件
    private TextView tvMonthTitle;
    private TextView tvMonthLabel;

    private TextView tvIncome, tvExpense, tvBalance, tvOvertime;
    private LinearLayout layoutIncome, layoutExpense, layoutBalance, layoutOvertime;

    // 提取全局 RecyclerView 以便执行动画
    private RecyclerView calendarRecycler;

    private List<AssetAccount> cachedAssets = new ArrayList<>();
    private TransactionListAdapter currentDetailAdapter;

    // 手势检测器
    private GestureDetector gestureDetector;

    // Activity Result Launcher
    private ActivityResultLauncher<Intent> yearCalendarLauncher;

    // 新增的成员变量
    private AlertDialog currentDetailDialog;
    private TextView currentDetailSummaryTextView;

    // 新增：记录当前的过滤模式
    private int currentFilterMode = 0;

    private androidx.cardview.widget.CardView cardBudgetStatus;
    private TextView tvBudgetText;
    private android.widget.ProgressBar pbBudget;

    // 新增：用于记录当前请求的时间范围，防止无限循环查询
    private long currentStartMillis = 0;
    private long currentEndMillis = 0;

    private int touchSlop;

    /**
     * 核心跟手引擎：接管日历的水平方向滑动
     */
    private void setupFollowHandSwipe(RecyclerView recyclerView) {
        touchSlop = android.view.ViewConfiguration.get(requireContext()).getScaledTouchSlop();

        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float initialX = 0f;
            private float initialY = 0f;
            private boolean isHorizontalSwipe = false;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 🌟 核心修复 1：必须使用屏幕绝对坐标 getRawX()，否则视图移动会导致坐标疯狂抵消
                        initialX = e.getRawX();
                        initialY = e.getRawY();
                        isHorizontalSwipe = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - initialX;
                        float dy = e.getRawY() - initialY;
                        if (!isHorizontalSwipe && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            isHorizontalSwipe = true;
                            // 🌟 核心修复 2：强制剥夺父容器（如外层 ScrollView）的滑动权，保证不被外层打断
                            if (rv.getParent() != null) {
                                rv.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            return true;
                        }
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (!isHorizontalSwipe) return;

                float dx = e.getRawX() - initialX;
                float screenWidth = rv.getWidth();
                if (screenWidth == 0) screenWidth = 1080;

                switch (e.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        // 跟手阻尼移动
                        rv.setTranslationX(dx * 0.6f);
                        rv.setAlpha(1f - (Math.abs(dx) / screenWidth) * 0.5f);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dx > screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, screenWidth, 0, -1); // 拉出上个月
                        } else if (dx < -screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, -screenWidth, 0, 1); // 拉出下个月
                        } else {
                            // 距离不够，平滑恢复原位（删除了多余的 Overshoot 回弹效果）
                            rv.animate().translationX(0f).alpha(1f).setDuration(250)
                                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                        }
                        isHorizontalSwipe = false;
                        break;
                }
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }

    /**
     * 供按钮点击触发的自动滑动封装
     */
    private void changeCalendarPage(int yearOffset, int monthOffset) {
        if (calendarRecycler != null) {
            float screenWidth = calendarRecycler.getWidth();
            if (screenWidth == 0) screenWidth = 1080;
            // 右滑拉出过去，左滑拉出未来
            float targetX = (yearOffset > 0 || monthOffset > 0) ? -screenWidth : screenWidth;
            finishSwipeAnimation(calendarRecycler, targetX, yearOffset, monthOffset);
        }
    }

    /**
     * 结算动画：滑出 -> 切换底层数据 -> 从另一侧滑入
     */
    private void finishSwipeAnimation(RecyclerView rv, float targetTranslationX, int yearOffset, int monthOffset) {
        rv.animate()
                .translationX(targetTranslationX)
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    // 1. 在屏幕外悄悄修改时间
                    if (yearOffset != 0) currentMonth = currentMonth.plusYears(yearOffset);
                    if (monthOffset != 0) currentMonth = currentMonth.plusMonths(monthOffset);

                    // 2. 将列表瞬移到屏幕的另一侧，准备入场
                    rv.setTranslationX(-targetTranslationX * 0.5f);

                    // 3. 触发数据库查询与日历数据重绘（传 0 禁用原有的旧动画）
                    updateCalendar(0);

                    // 4. 减速滑入并恢复全不透明
                    rv.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(300)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private void fetchDataForCurrentMonth() {
        LocalDate firstDay = currentMonth.atDay(1);
        int offset = firstDay.getDayOfWeek().getValue() - 1;
        LocalDate startOfGrid = firstDay.minusDays(offset);
        LocalDate endOfGrid = currentMonth.atEndOfMonth().plusDays(14); // 留点缓冲天数

        long startMillis = startOfGrid.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = endOfGrid.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

        // 如果时间范围发生变化，才通知 ViewModel 去数据库查询
        if (currentStartMillis != startMillis || currentEndMillis != endMillis) {
            currentStartMillis = startMillis;
            currentEndMillis = endMillis;
            viewModel.setDateRange(startMillis, endMillis);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        yearCalendarLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        int year = result.getData().getIntExtra("year", -1);
                        int month = result.getData().getIntExtra("month", -1);
                        if (year != -1 && month != -1) {
                            currentMonth = YearMonth.of(year, month);
                            updateCalendar(0);
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        assistantConfig = new AssistantConfig(requireContext());

        if (currentMonth == null) {
            currentMonth = YearMonth.now();
        }

//        initGestureDetector();

        // 初始化设置菜单按钮
        ImageButton btnSettings = view.findViewById(R.id.btn_settings_menu);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), SettingsActivity.class);
                startActivity(intent);
            });
            SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isMinimalist = prefs.getBoolean("minimalist_mode", false);
            btnSettings.setVisibility(isMinimalist ? View.GONE : View.VISIBLE);
        }

        tvMonthTitle = view.findViewById(R.id.tv_month_title);
        tvMonthLabel = view.findViewById(R.id.tv_month_label);
        tvIncome = view.findViewById(R.id.tv_month_income);
        tvExpense = view.findViewById(R.id.tv_month_expense);
        tvBalance = view.findViewById(R.id.tv_month_balance);
        tvOvertime = view.findViewById(R.id.tv_month_overtime);

        layoutIncome = view.findViewById(R.id.layout_stat_income);
        layoutExpense = view.findViewById(R.id.layout_stat_expense);
        layoutBalance = view.findViewById(R.id.layout_stat_balance);
        layoutOvertime = view.findViewById(R.id.layout_stat_overtime);

        // 在 onCreateView 中绑定视图
        cardBudgetStatus = view.findViewById(R.id.card_budget_status);
        tvBudgetText = view.findViewById(R.id.tv_budget_text);
        pbBudget = view.findViewById(R.id.pb_budget);

        FloatingActionButton btnQuickRecord = view.findViewById(R.id.btn_quick_record);
        if (btnQuickRecord != null) {
            btnQuickRecord.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                int quickMode = prefs.getInt("quick_record_mode", 0);
                if (quickMode == 1) {
                    showAddOrEditDialog(null, LocalDate.now());
                } else {
                    showDateDetailDialog(LocalDate.now());
                }
            });
        }

        // 👇 新增：长按开启AI对话 👇
        btnQuickRecord.setOnLongClickListener(v -> {
            AiConfig config = AiConfig.load(requireContext());
            if (config.isEnabledAndReady()) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                startActivity(new Intent(requireContext(), AiChatActivity.class));
                return true;
            } else {
                Toast.makeText(requireContext(), "请先在设置中启用 AI 记账，并至少填写 Base URL、API Key、文本模型。", Toast.LENGTH_LONG).show();
                return false;
            }
        });
        // 👆 新增结束 👆

        calendarRecycler = view.findViewById(R.id.calendar_recycler);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 7) {
            @Override
            public boolean canScrollVertically() { return false; }
        };
        calendarRecycler.setLayoutManager(layoutManager);
        calendarRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // 🌟 新增 1：关闭日历刷新时的自带闪烁动画
        calendarRecycler.setItemAnimator(null);

        adapter = new CalendarAdapter(date -> {
            // 如果点击的是已经选中的日期（即第二次点击），则弹出账单详情弹窗
            if (date.equals(selectedDate)) {
                showDateDetailDialog(date);
            } else {
                // 如果点击的是新日期（即第一次点击），只做选中高亮和刷新面板
                selectedDate = date;
                adapter.setSelectedDate(date);

                // 立即刷新预算卡片 (保留高性能的 getRangeTransactions)
                List<Transaction> currentList = viewModel.getRangeTransactions().getValue();
                if (currentList != null) {
                    updateBudgetCard(currentList);
                }
            }
        });
        calendarRecycler.setAdapter(adapter);

        // 🌟 新增 2：挂载丝滑物理跟手引擎
        setupFollowHandSwipe(calendarRecycler);

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int defaultMode = prefs.getInt("default_record_mode", 0);

        // 新增：如果当前默认模式是加班，则读取用户上次记忆的是工资(3)还是工时(4)
        if (defaultMode == 3 || defaultMode == 4) {
            defaultMode = prefs.getInt("overtime_display_mode", 3);
        }
        switchFilterMode(defaultMode);

//        calendarRecycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
//            @Override
//            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
//                return gestureDetector.onTouchEvent(e);
//            }
//        });

        if (tvMonthLabel != null) {
            tvMonthLabel.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), YearCalendarActivity.class);
                intent.putExtra("year", currentMonth.getYear());
                yearCalendarLauncher.launch(intent);
            });
//            tvMonthLabel.setOnTouchListener((v, event) -> {
//                gestureDetector.onTouchEvent(event);
//                return false;
//            });
        }

        layoutBalance.setOnClickListener(v -> switchFilterMode(0));
        layoutIncome.setOnClickListener(v -> switchFilterMode(1));
        layoutExpense.setOnClickListener(v -> switchFilterMode(2));

        // 新增：带有持久化记忆功能的加班切换逻辑
        layoutOvertime.setOnClickListener(v -> {
            SharedPreferences prefsEdit = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

            if (currentFilterMode == 3 || currentFilterMode == 4) {
                // 1. 如果当前已经在加班标签下，互相切换并保存记忆
                int newMode = (currentFilterMode == 3) ? 4 : 3;
                switchFilterMode(newMode);
                prefsEdit.edit().putInt("overtime_display_mode", newMode).apply(); // 保存记忆
                Toast.makeText(getContext(), newMode == 4 ? "已切换为显示加班工时" : "已切换为显示加班工资", Toast.LENGTH_SHORT).show();
            } else {
                // 2. 如果是从其他标签（结余/收入/支出）切换过来，读取上次记忆的模式
                int savedMode = prefsEdit.getInt("overtime_display_mode", 3);
                switchFilterMode(savedMode);
            }
        });

        tvMonthTitle.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            showCustomDatePicker();
        });

        // 🌟 替换：使用物理动画引擎来切换年份
        view.findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            changeCalendarPage(-1, 0); // 往左滑出，减 1 年
        });
        view.findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            changeCalendarPage(1, 0);  // 往右滑出，加 1 年
        });

        // 【优化】不再全量观察，只观察当前月份按需请求的数据
        viewModel.getRangeTransactions().observe(getViewLifecycleOwner(), list -> {
            updateCalendar(0); // 数据刷新不播放滑动动画
            if (currentDetailDialog != null && currentDetailDialog.isShowing() && selectedDate != null) {
                updateDetailDialogData(selectedDate);
            }
        });

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            if (assets != null) {
                cachedAssets = assets;
                if (currentDetailAdapter != null) {
                    currentDetailAdapter.setAssets(assets);
                }
            }
        });

        updateCalendar(0);
        return view;
    }

    private void updateBudgetCard(List<Transaction> transactions) {
        if (transactions == null || getContext() == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isBudgetEnabled = prefs.getBoolean("is_budget_enabled", false);

        // --- 新增：判断是否开启了详细预算 ---
        boolean isDetailedEnabled = prefs.getBoolean("is_detailed_budget_enabled", false);

        // 1. 检查当前浏览的月份是否有效
        long budgetStartTime = prefs.getLong("budget_start_time", 0);
        boolean isEffectiveMonth = true;
        if (budgetStartTime > 0) {
            YearMonth startYM = YearMonth.from(Instant.ofEpochMilli(budgetStartTime).atZone(ZoneId.systemDefault()).toLocalDate());
            if (currentMonth.isBefore(startYM)) {
                isEffectiveMonth = false;
            }
        }

        boolean finalBudgetEnabled = isBudgetEnabled && isEffectiveMonth;

        // --- 2. 核心修改：统一预算获取逻辑 ---
        float monthlyBudget = 0;
        if (isDetailedEnabled) {
            // 如果开启了详细预算，月总预算等于各个分类预算之和
            List<String> expenseCategories = CategoryManager.getExpenseCategories(requireContext());
            for (String cat : expenseCategories) {
                monthlyBudget += prefs.getFloat("budget_cat_" + cat, 0f);
            }
        } else {
            // 普通模式：读取当月独立设置的预算
            String monthKey = "budget_" + currentMonth.getYear() + "_" + currentMonth.getMonthValue();
            float defaultBudget = prefs.getFloat("monthly_budget", 0f);
            monthlyBudget = prefs.getFloat(monthKey, defaultBudget);
        }
        // ------------------------------------

        if (finalBudgetEnabled && monthlyBudget > 0) {
            cardBudgetStatus.setVisibility(View.VISIBLE);

            // 获取需要计算的日期（如果没有选定日期，默认用今天）
            LocalDate targetDate = selectedDate != null ? selectedDate : LocalDate.now();

            // 使用当前浏览月份的天数来计算平均预算，保证精准
            int daysInMonth = currentMonth.lengthOfMonth();
            double dailyBudget = monthlyBudget / daysInMonth;

            // 计算目标日期的总支出
            double targetExpense = 0;
            long startOfDay = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endOfDay = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // 计算目标日期的总支出
            for (Transaction t : transactions) {
                boolean isTransfer = (t.type == 2) || "资产互转".equals(t.category);

                // 【修改点】：增加 !t.excludeFromBudget 判断
                if (t.date >= startOfDay && t.date < endOfDay && t.type == 0 && !isTransfer && !t.excludeFromBudget) {
                    targetExpense += t.amount;
                }
            }

            // 更新金额和进度条
            tvBudgetText.setText(String.format("%.2f / %.2f", targetExpense, dailyBudget));
            int progress = (int) ((targetExpense / dailyBudget) * 100);
            pbBudget.setProgress(Math.min(progress, 100)); // 最大到100防越界

            // 超出预算进度条变红
            if (targetExpense > dailyBudget) {
                pbBudget.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.budget_progress_exceed)));
                tvBudgetText.setTextColor(ContextCompat.getColor(requireContext(), R.color.budget_progress_exceed));
            } else {
                pbBudget.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.budget_progress_safe)));
                tvBudgetText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }

            // 动态修改卡片标题，如果是今天显示"今日预算"，否则显示"X月X日预算"
            try {
                LinearLayout budgetTextContainer = (LinearLayout) tvBudgetText.getParent();
                TextView tvBudgetTitle = (TextView) budgetTextContainer.getChildAt(0);
                if (targetDate.equals(LocalDate.now())) {
                    tvBudgetTitle.setText("今日预算");
                } else {
                    tvBudgetTitle.setText(targetDate.format(DateTimeFormatter.ofPattern("M月d日预算")));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            cardBudgetStatus.setVisibility(View.GONE);
        }

        // 核心：把过滤后的状态传给日历适配器。
        // 如果 finalBudgetEnabled 为 false（比如查看去年的账单），日历背景色就不会变
        adapter.setBudgetConfig(finalBudgetEnabled, monthlyBudget);
    }

    @Override
    public void onResume() {
        super.onResume();
        List<Transaction> currentList = viewModel.getRangeTransactions().getValue();
        if (currentList != null) {
            updateBudgetCard(currentList);
        }
        View view = getView();
        if (view != null) {
            ImageButton btnSettings = view.findViewById(R.id.btn_settings_menu);
            if (btnSettings != null) {
                SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                boolean isMinimalist = prefs.getBoolean("minimalist_mode", false);
                btnSettings.setVisibility(isMinimalist ? View.GONE : View.VISIBLE);
            }
        }
        checkAutoRenewalDeduction();

        // 【新增】：根据模式动态调整本界面透明度
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        updateFragmentTransparency(themeMode == 3);
    }

    // 【新增方法】：动态控制界面透明度，不破坏 XML 默认结构
    // 【修改或替换】现有的 updateFragmentTransparency 方法
    private void updateFragmentTransparency(boolean isCustomBg) {
        View view = getView();
        if (view == null) return;

        View topBar = view.findViewById(R.id.layout_top_bar);
        View monthLabel = view.findViewById(R.id.tv_month_label);
        View weekHeader = view.findViewById(R.id.layout_week_header);

        // 获取需要调整质感的卡片和按钮
        androidx.cardview.widget.CardView cardStats = view.findViewById(R.id.card_stats); // 收入支出结余卡片
        com.google.android.material.floatingactionbutton.FloatingActionButton btnQuickRecord = view.findViewById(R.id.btn_quick_record);

        if (isCustomBg) {
            // 1. 顶部基础框架全透明，让底层的图片完全透出来
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (topBar != null) topBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (monthLabel != null) monthLabel.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (weekHeader != null) weekHeader.setBackgroundColor(android.graphics.Color.TRANSPARENT);

            // 2. 今日预算卡片：80%透明度 (204) + 去除阴影
            if (cardBudgetStatus != null) {
                int surfaceColor = ContextCompat.getColor(requireContext(), R.color.white);
                int translucentSurface = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230);
                cardBudgetStatus.setCardBackgroundColor(translucentSurface);
                cardBudgetStatus.setCardElevation(0f);
            }

            // 3. 【新增】收支统计卡片 (收入/支出/结余/加班)：80%透明度 (204) + 去除阴影
            if (cardStats != null) {
                int surfaceColor = ContextCompat.getColor(requireContext(), R.color.white);
                int translucentSurface = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230);
                cardStats.setCardBackgroundColor(translucentSurface);
                cardStats.setCardElevation(0f);
            }

            // 4. 快捷记账按钮：85%透明度 (216) + 去除阴影
            if (btnQuickRecord != null) {
                int fabColor = ContextCompat.getColor(requireContext(), R.color.app_yellow);
                int translucentFab = androidx.core.graphics.ColorUtils.setAlphaComponent(fabColor, 230);
                btnQuickRecord.setBackgroundTintList(ColorStateList.valueOf(translucentFab));
                btnQuickRecord.setCompatElevation(0f);
            }

        } else {
            // ================= 恢复普通系统/日间/夜间模式 =================
            view.setBackgroundResource(R.color.bar_background);
            if (topBar != null) topBar.setBackgroundResource(R.color.bar_background);
            if (monthLabel != null) monthLabel.setBackgroundResource(R.color.bar_background);
            if (weekHeader != null) weekHeader.setBackgroundResource(R.color.bar_background);

            if (cardBudgetStatus != null) {
                int surfaceColor = ContextCompat.getColor(requireContext(), R.color.white);
                cardBudgetStatus.setCardBackgroundColor(surfaceColor);
                // 【修改】：恢复为 0f，去掉普通模式下的卡片阴影
                cardBudgetStatus.setCardElevation(0f);
            }

            if (cardStats != null) {
                int surfaceColor = ContextCompat.getColor(requireContext(), R.color.white);
                cardStats.setCardBackgroundColor(surfaceColor);
                // 【修改】：恢复为 0f，去掉普通模式下的卡片阴影
                cardStats.setCardElevation(0f);
            }

            if (btnQuickRecord != null) {
                int fabColor = ContextCompat.getColor(requireContext(), R.color.app_yellow);
                btnQuickRecord.setBackgroundTintList(ColorStateList.valueOf(fabColor));
                // 【修改】：恢复为 0f，去掉普通模式下的按钮阴影
                btnQuickRecord.setCompatElevation(0f);
            }
        }
    }

    private void checkAutoRenewalDeduction() {
        List<RenewalItem> renewalList = assistantConfig.getRenewalList();
        if (renewalList.isEmpty()) return;

        LocalDate today = LocalDate.now();
        String todayStr = today.toString();
        String lastCheckDate = assistantConfig.getLastRenewalDate();

        if (todayStr.equals(lastCheckDate)) return;

        int defaultAssetId = assistantConfig.getDefaultAssetId();

        // 替换 checkAutoRenewalDeduction 中的循环逻辑
        for (RenewalItem item : renewalList) {
            // 【关键修复】：调用新的辅助方法
            if (isRenewalDate(item, today)) {
                executeAutoDeduction(item, defaultAssetId, todayStr);
            }
        }

        assistantConfig.setLastRenewalDate(todayStr);
    }

    private void executeAutoDeduction(RenewalItem item, int assetId, String todayStr) {
        double amount = item.amount;
        String object = item.object;
        long timestamp = System.currentTimeMillis();

        Transaction t = new Transaction(timestamp, 0, "自动续费", amount, object, "系统自动扣费");
        t.assetId = assetId != -1 ? assetId : 0;

        viewModel.addTransaction(t);

        if (t.assetId != 0) {
            viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
                if (assets != null) {
                    for (AssetAccount a : assets) {
                        if (a.id == t.assetId) {
                            if (a.type == 0) {
                                a.amount -= amount;
                            } else if (a.type == 1 || a.type == 2) { // 【修改这里】兼容借出
                                a.amount += amount;
                            }
                            viewModel.updateAsset(a);
                            break;
                        }
                    }
                }
            });
        }
        Toast.makeText(getContext(), "已自动扣除: " + object, Toast.LENGTH_SHORT).show();
    }

    private void showCustomDatePicker() {
        if (getContext() == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        LocalDate baseDate = selectedDate != null ? selectedDate : LocalDate.now();
        int curYear = baseDate.getYear();
        int curMonth = baseDate.getMonthValue();
        int curDay = baseDate.getDayOfMonth();

        NumberPicker npYear = dialog.findViewById(R.id.np_year);
        NumberPicker npMonth = dialog.findViewById(R.id.np_month);
        NumberPicker npDay = dialog.findViewById(R.id.np_day);
        TextView tvPreview = dialog.findViewById(R.id.tv_date_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        if (npYear == null || npMonth == null || npDay == null || btnConfirm == null || btnCancel == null) return;

        npYear.setMinValue(2000);
        npYear.setMaxValue(2050);
        npYear.setValue(curYear);

        npMonth.setMinValue(1);
        npMonth.setMaxValue(12);
        npMonth.setValue(curMonth);

        npDay.setMinValue(1);
        int maxDays = YearMonth.of(curYear, curMonth).lengthOfMonth();
        npDay.setMaxValue(maxDays);
        npDay.setValue(curDay);

        NumberPicker.OnValueChangeListener dateChangeListener = (picker, oldVal, newVal) -> {
            picker.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            int y = npYear.getValue();
            int m = npMonth.getValue();
            int newMaxDays = YearMonth.of(y, m).lengthOfMonth();
            if (npDay.getMaxValue() != newMaxDays) {
                int currentD = npDay.getValue();
                npDay.setMaxValue(newMaxDays);
                if (currentD > newMaxDays) npDay.setValue(newMaxDays);
            }
            updatePreviewText(tvPreview, y, m, npDay.getValue());
        };

        npYear.setOnValueChangedListener(dateChangeListener);
        npMonth.setOnValueChangedListener(dateChangeListener);
        npDay.setOnValueChangedListener(dateChangeListener);

        updatePreviewText(tvPreview, curYear, curMonth, curDay);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            int year = npYear.getValue();
            int month = npMonth.getValue();
            int day = npDay.getValue();

            currentMonth = YearMonth.of(year, month);
            selectedDate = LocalDate.of(year, month, day);
            updateCalendar(0);

            adapter.setSelectedDate(selectedDate);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updatePreviewText(TextView tv, int year, int month, int day) {
        if (tv == null) return;
        try {
            LocalDate date = LocalDate.of(year, month, day);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);
            tv.setText(date.format(formatter));
        } catch (Exception e) {
            tv.setText(year + "年" + month + "月" + day + "日");
        }
    }

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) &&
                        Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    if (diffX > 0) {
                        currentMonth = currentMonth.minusMonths(1);
                        updateCalendar(-1); // 从左侧滑入
                    } else {
                        currentMonth = currentMonth.plusMonths(1);
                        updateCalendar(1);  // 从右侧滑入
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void switchFilterMode(int mode) {
        currentFilterMode = mode;
        adapter.setFilterMode(mode);
        // 重新计算并刷新顶部统计数据和日历显示
        List<Transaction> currentList = viewModel.getRangeTransactions().getValue();
        if (currentList != null) {
            calculateMonthTotals(currentList);
        }
    }

    // 更新日历，带有方向参数：-1 左滑入，1 右滑入，0 不执行动画
    private void updateCalendar(int direction) {
        tvMonthTitle.setText(currentMonth.format(DateTimeFormatter.ofPattern("yyyy")));
        if (tvMonthLabel != null) {
            tvMonthLabel.setText(currentMonth.getMonthValue() + "月");
        }

        // 🌟 核心优化：每次日历刷新前，触发按需加载本月数据
        fetchDataForCurrentMonth();

        List<LocalDate> days = new ArrayList<>();
        LocalDate firstDay = currentMonth.atDay(1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue();
        int offset = dayOfWeek - 1;

        LocalDate startOfGrid = firstDay.minusDays(offset);
        for (int i = 0; i < offset; i++) {
            days.add(startOfGrid.plusDays(i));
        }

        int length = currentMonth.lengthOfMonth();
        for (int i = 1; i <= length; i++) {
            days.add(currentMonth.atDay(i));
        }

        // 🌟 读取刚刚请求到的本月数据 (取代全量获取)
        List<Transaction> allList = viewModel.getRangeTransactions().getValue();
        List<Transaction> currentList = allList != null ? allList : new ArrayList<>();

        adapter.setRenewalItems(assistantConfig.getRenewalList());
        adapter.setCurrentMonth(currentMonth);
        adapter.updateData(days, currentList);

        if (selectedDate != null && YearMonth.from(selectedDate).equals(currentMonth)) {
            adapter.setSelectedDate(selectedDate);
        }

        calculateMonthTotals(currentList);

        if (getContext() != null && calendarRecycler != null) {
            if (direction == 1) {
                Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_in_right);
                calendarRecycler.startAnimation(anim);
            } else if (direction == -1) {
                Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.slide_in_left);
                calendarRecycler.startAnimation(anim);
            }
        }
    }

    private void calculateMonthTotals(List<Transaction> transactions) {
        double totalIncome = 0;
        double totalExpense = 0;
        double totalOvertimeAmount = 0;
        double totalOvertimeHours = 0; // 计算加班总工时
        int year = currentMonth.getYear();
        int month = currentMonth.getMonthValue();

        for (Transaction t : transactions) {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            if (date.getYear() == year && date.getMonthValue() == month) {
                boolean isTransfer = (t.type == 2) || "资产互转".equals(t.category);
                if (isTransfer) {
                    continue; // 🌟 1. 彻底跳过资产互转，不计入月度收支
                } else if (t.type == 1) {
                    if ("加班".equals(t.category)) {
                        totalOvertimeAmount += t.amount;
                        // 提取工时数据
                        if (t.note != null) {
                            Matcher m = Pattern.compile("时长:\\s*([0-9.]+)\\s*小时").matcher(t.note);
                            if (m.find()) {
                                try {
                                    totalOvertimeHours += Double.parseDouble(m.group(1));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    } else {
                        totalIncome += t.amount;
                    }
                } else if (t.type == 0) { // 🌟 严格限制必须是 type == 0 才是支出
                    totalExpense += t.amount;
                }
            }
        }
        double balance = totalIncome - totalExpense;
        tvIncome.setText(String.format("+%.2f", totalIncome));
        tvExpense.setText(String.format("-%.2f", totalExpense));

        // 根据当前的模式决定顶部面板显示加班工资还是加班工时
        if (currentFilterMode == 4) {
            tvOvertime.setText(String.format("%.1fh", totalOvertimeHours));
        } else {
            tvOvertime.setText(String.format("+%.2f", totalOvertimeAmount));
        }

        String sign = balance >= 0 ? "+" : "";
        tvBalance.setText(String.format("%s%.2f", sign, balance));

        // 调用统一的方法处理顶部卡片显示和日历背景渲染
        updateBudgetCard(transactions);
    }
    private void showDateDetailDialog(LocalDate date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_transaction_list, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        currentDetailDialog = dialog;

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            tvTitle.setText(date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA)));
        }

        currentDetailSummaryTextView = dialogView.findViewById(R.id.tv_dialog_summary);

        RecyclerView rvList = dialogView.findViewById(R.id.rv_detail_list);
        rvList.setLayoutManager(new LinearLayoutManager(getContext()));

        TransactionListAdapter listAdapter = new TransactionListAdapter(transaction -> {
            if ("PREVIEW_BILL".equals(transaction.remark)) {
                Toast.makeText(getContext(), "待扣费账单：到达日期后将自动执行", Toast.LENGTH_SHORT).show();
                return;
            }

            // 🌟 如果是资产互转记录，则呼出删除确认弹窗
            boolean isTransfer = (transaction.type == 2) || "资产互转".equals(transaction.category);
            if (isTransfer) {
                showDeleteTransferDialog(transaction);
                return;
            }

            LocalDate transDate = Instant.ofEpochMilli(transaction.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(transaction, transDate);
        });

        listAdapter.setAssets(cachedAssets);
        currentDetailAdapter = listAdapter;
        rvList.setAdapter(listAdapter);

        updateDetailDialogData(date);

        dialogView.findViewById(R.id.btn_add_transaction).setOnClickListener(v -> showAddOrEditDialog(null, date));
        dialogView.findViewById(R.id.btn_add_overtime).setOnClickListener(v -> { dialog.dismiss(); showOvertimeDialog(date); });
        dialogView.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            currentDetailAdapter = null;
            currentDetailDialog = null;
            currentDetailSummaryTextView = null;
        });
        dialog.show();
    }

    private void showDeleteTransferDialog(Transaction transaction) {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvMsg = view.findViewById(R.id.tv_dialog_message);
        if (tvMsg != null) {
            tvMsg.setText("确定要删除这条资产转移记录吗？");
        }

        view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            viewModel.deleteTransaction(transaction); // 从数据库删除记录
            Toast.makeText(getContext(), "转移记录已删除", Toast.LENGTH_SHORT).show();
            dialog.dismiss();

            // 刷新当前弹出的单日详情列表
            if (currentDetailDialog != null && currentDetailDialog.isShowing() && selectedDate != null) {
                updateDetailDialogData(selectedDate);
            }
        });
        dialog.show();
    }

    private void updateDetailDialogData(LocalDate date) {
        if (currentDetailAdapter == null) return;
        List<Transaction> all = viewModel.getRangeTransactions().getValue();
        List<Transaction> dayList = new ArrayList<>();
        if (all != null) {
            long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            dayList = all.stream()
                    .filter(t -> t.date >= start && t.date < end)
                    .collect(Collectors.toList());
        }

        // 替换 updateDetailDialogData 中的循环逻辑
        List<RenewalItem> renewals = assistantConfig.getRenewalList();
        for (RenewalItem item : renewals) {
            // 【关键修复】：调用新的辅助方法
            if (isRenewalDate(item, date)) {
                String objectName = item.object;
                boolean alreadyExecuted = dayList.stream().anyMatch(t ->
                        "自动续费".equals(t.category) && objectName.equals(t.note));

                if (!alreadyExecuted) {
                    Transaction preview = new Transaction();
                    preview.amount = item.amount;
                    preview.type = 0;
                    preview.category = "自动续费";
                    preview.note = objectName;
                    preview.remark = "PREVIEW_BILL";
                    preview.date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    dayList.add(preview);
                }
            }
        }

        if (currentDetailSummaryTextView != null) {
            double dayIncome = 0;
            double dayExpense = 0;

            for (Transaction t : dayList) {
                if ("PREVIEW_BILL".equals(t.remark)) continue;

                boolean isTransfer = (t.type == 2) || "资产互转".equals(t.category);
                if (isTransfer) continue; // 🌟 3. 在单日账单总计中忽略资产转移

                if (t.type == 1) {
                    dayIncome += t.amount;
                } else if (t.type == 0) { // 🌟 严格限制 type == 0
                    dayExpense += t.amount;
                }
            }

            if (dayIncome == 0 && dayExpense == 0) {
                currentDetailSummaryTextView.setVisibility(View.GONE);
            } else {
                currentDetailSummaryTextView.setVisibility(View.VISIBLE);
                double dayBalance = dayIncome - dayExpense;

                int colorExpense = ContextCompat.getColor(getContext(), R.color.expense_green);
                int colorIncome = ContextCompat.getColor(getContext(), R.color.income_red);
                int colorBalance = ContextCompat.getColor(getContext(), R.color.app_yellow);

                android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();

                if (dayExpense > 0) {
                    String expStr = String.format(Locale.CHINA, "支出: %.2f", dayExpense);
                    int start = ssb.length();
                    ssb.append(expStr);
                    ssb.setSpan(new android.text.style.ForegroundColorSpan(colorExpense), start, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.append("    ");
                }
                if (dayIncome > 0) {
                    String incStr = String.format(Locale.CHINA, "收入: %.2f", dayIncome);
                    int start = ssb.length();
                    ssb.append(incStr);
                    ssb.setSpan(new android.text.style.ForegroundColorSpan(colorIncome), start, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.append("    ");
                }
                String balStr = String.format(Locale.CHINA, "结余: %.2f", dayBalance);
                int startBal = ssb.length();
                ssb.append(balStr);
                ssb.setSpan(new android.text.style.ForegroundColorSpan(colorBalance), startBal, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                currentDetailSummaryTextView.setText(ssb);
            }
        }
        currentDetailAdapter.setTransactions(dayList);
    }

    private void showOvertimeDialog(LocalDate date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_add_overtime, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etRate = view.findViewById(R.id.et_hourly_rate);
        EditText etDuration = view.findViewById(R.id.et_duration);
        TextView tvResult = view.findViewById(R.id.tv_calculated_amount);
        Button btnSave = view.findViewById(R.id.btn_save_overtime);
        Button btnCancel = view.findViewById(R.id.btn_cancel_overtime);

        AssistantConfig config = new AssistantConfig(requireContext());
        float defaultRate = 0f;
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            defaultRate = config.getHolidayOvertimeRate();
        } else {
            defaultRate = config.getWeekdayOvertimeRate();
        }

        if (defaultRate > 0) {
            etRate.setText(String.valueOf(defaultRate));
        }

        etRate.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                calculateOvertime(etRate, etDuration, tvResult);
            }
        };
        etRate.addTextChangedListener(watcher);
        etDuration.addTextChangedListener(watcher);

        btnSave.setOnClickListener(v -> {
            String rateStr = etRate.getText().toString();
            String durationStr = etDuration.getText().toString();

            if (!rateStr.isEmpty() && !durationStr.isEmpty()) {
                double rate = Double.parseDouble(rateStr);
                double duration = Double.parseDouble(durationStr);
                double totalAmount = rate * duration;

                long ts = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

                Transaction transaction = new Transaction(ts, 1, "加班", totalAmount);
                transaction.note = String.format("时长: %s小时, 时薪: %s", durationStr, rateStr);

                viewModel.addTransaction(transaction);
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            if (getContext() != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null && view != null && view.getWindowToken() != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
        });

        dialog.show();
    }

    private void calculateOvertime(EditText etRate, EditText etDuration, TextView tvResult) {
        try {
            String r = etRate.getText().toString();
            String d = etDuration.getText().toString();
            if (!r.isEmpty() && !d.isEmpty()) {
                double rate = Double.parseDouble(r);
                double duration = Double.parseDouble(d);
                tvResult.setText(String.format("预计收入: %.2f", rate * duration));
            } else {
                tvResult.setText("预计收入: 0.00");
            }
        } catch (Exception e) {
            tvResult.setText("预计收入: 0.00");
        }
    }

    private void showAddOrEditDialog(Transaction existingTransaction, LocalDate date) {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvDate = dialogView.findViewById(R.id.tv_dialog_date);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        RecyclerView rvCategory = dialogView.findViewById(R.id.rv_category);
        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        Button btnCurrency = dialogView.findViewById(R.id.btn_currency);

        EditText etCustomCategory = dialogView.findViewById(R.id.et_custom_category);
        EditText etRemark = dialogView.findViewById(R.id.et_remark);
        EditText etNote = dialogView.findViewById(R.id.et_note);
        Spinner spAsset = dialogView.findViewById(R.id.sp_asset);

        // 【新增】绑定对象输入框
        EditText etTargetObject = dialogView.findViewById(R.id.et_target_object);

        // ================= 【新增】不计入预算逻辑开始 =================
        ImageView ivExcludeBudget = dialogView.findViewById(R.id.iv_exclude_budget);
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isBudgetFeatureEnabled = prefs.getBoolean("is_budget_enabled", false);

        // 记录状态
        final boolean[] isExcludedFromBudget = { existingTransaction != null && existingTransaction.excludeFromBudget };

        if (isBudgetFeatureEnabled) {
            ivExcludeBudget.setVisibility(View.VISIBLE);

            // 刷新圆点UI的闭包
            Runnable updateDotUi = () -> {
                if (isExcludedFromBudget[0]) {
                    // 主题色填充（假设你的主题色是 app_yellow）
                    ivExcludeBudget.setColorFilter(ContextCompat.getColor(getContext(), R.color.app_yellow));
                    ivExcludeBudget.setImageResource(R.drawable.ic_dot_filled);
                } else {
                    // 灰色空心
                    ivExcludeBudget.setColorFilter(android.graphics.Color.parseColor("#888888"));
                    ivExcludeBudget.setImageResource(R.drawable.ic_dot_outline);
                }
            };
            updateDotUi.run(); // 初始化状态

            // 点击事件
            ivExcludeBudget.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                isExcludedFromBudget[0] = !isExcludedFromBudget[0];
                updateDotUi.run();
                Toast.makeText(getContext(), isExcludedFromBudget[0] ? "该笔账单将不计入预算" : "该笔账单正常计入预算", Toast.LENGTH_SHORT).show();
            });
        } else {
            ivExcludeBudget.setVisibility(View.GONE);
        }
        // ================= 【新增】不计入预算逻辑结束 =================

        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        TextView tvRevoke = dialogView.findViewById(R.id.tv_revoke);

        com.google.android.material.button.MaterialButton btnTakePhoto = dialogView.findViewById(R.id.btn_take_photo);
        com.google.android.material.button.MaterialButton btnViewPhoto = dialogView.findViewById(R.id.btn_view_photo);

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});
        
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
        boolean isPhotoBackupEnabled = prefs.getBoolean("enable_photo_backup", false);

        if (isCurrencyEnabled) {
            btnCurrency.setVisibility(View.VISIBLE);
            if (existingTransaction != null && existingTransaction.currencySymbol != null && !existingTransaction.currencySymbol.isEmpty()) {
                btnCurrency.setText(existingTransaction.currencySymbol);
            } else {
                String defaultSymbol = prefs.getString("default_currency_symbol", "¥");
                btnCurrency.setText(defaultSymbol);
            }
            btnCurrency.setOnClickListener(v -> showCurrencySelectDialog(btnCurrency));
        } else {
            btnCurrency.setVisibility(View.GONE);
        }

        final String[] currentPhotoPath = { existingTransaction != null ? existingTransaction.photoPath : "" };

        Runnable updatePhotoButtons = () -> {
            if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                btnViewPhoto.setVisibility(View.VISIBLE);
            } else {
                btnViewPhoto.setVisibility(View.GONE);
            }
        };

        if (isPhotoBackupEnabled) {
            btnTakePhoto.setVisibility(View.VISIBLE);
            updatePhotoButtons.run();

            btnTakePhoto.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), PhotoActionActivity.class);
                intent.putExtra(PhotoActionActivity.EXTRA_RECEIVER, new android.os.ResultReceiver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode == 1 && resultData != null) {
                            String uri = resultData.getString(PhotoActionActivity.KEY_RESULT_URI);
                            currentPhotoPath[0] = uri;
                            updatePhotoButtons.run();

                            if (existingTransaction != null) {
                                existingTransaction.photoPath = uri;
                                viewModel.updateTransaction(existingTransaction);
                                Toast.makeText(getContext(), "照片已添加并保存", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
                startActivity(intent);
            });

            btnViewPhoto.setOnClickListener(v -> {
                if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                    showPhotoDialog(currentPhotoPath[0]);
                }
            });

            btnViewPhoto.setOnLongClickListener(v -> {
                android.app.AlertDialog.Builder deleteBuilder = new android.app.AlertDialog.Builder(getContext());
                View deleteView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_delete_photo, null);
                deleteBuilder.setView(deleteView);

                android.app.AlertDialog deleteDialog = deleteBuilder.create();
                if (deleteDialog.getWindow() != null) {
                    deleteDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                deleteView.findViewById(R.id.btn_cancel_delete).setOnClickListener(view -> deleteDialog.dismiss());

                deleteView.findViewById(R.id.btn_confirm_delete).setOnClickListener(view -> {
                    if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                        try {
                            Uri uri = Uri.parse(currentPhotoPath[0]);
                            DocumentFile file = DocumentFile.fromSingleUri(requireContext(), uri);
                            if (file != null && file.exists()) {
                                if (file.delete()) {
                                    Toast.makeText(getContext(), "照片已彻底删除", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getContext(), "文件删除失败，仅移除引用", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(getContext(), "删除出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    currentPhotoPath[0] = "";
                    updatePhotoButtons.run();

                    if (existingTransaction != null) {
                        existingTransaction.photoPath = "";
                        viewModel.updateTransaction(existingTransaction);
                    }
                    deleteDialog.dismiss();
                });
                deleteDialog.show();
                return true;
            });

        } else {
            btnTakePhoto.setVisibility(View.GONE);
            btnViewPhoto.setVisibility(View.GONE);
        }

        List<String> expenseCategories = CategoryManager.getExpenseCategories(getContext());
        List<String> incomeCategories = CategoryManager.getIncomeCategories(getContext());

        boolean isDetailed = com.example.budgetapp.util.CategoryManager.isDetailedCategoryEnabled(getContext());
        if (isDetailed) {
            com.google.android.flexbox.FlexboxLayoutManager flexboxLayoutManager = new com.google.android.flexbox.FlexboxLayoutManager(getContext());
            flexboxLayoutManager.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
            flexboxLayoutManager.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
            flexboxLayoutManager.setJustifyContent(com.google.android.flexbox.JustifyContent.FLEX_START);

            rvCategory.setLayoutManager(flexboxLayoutManager);
        } else {
            rvCategory.setLayoutManager(new GridLayoutManager(getContext(), 5));
        }

        final boolean[] isExpense = {true};
        final String[] selectedCategory = {expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0)};
        final String[] selectedSubCategory = {""};

        CategoryAdapter categoryAdapter = new CategoryAdapter(getContext(), expenseCategories, selectedCategory[0], category -> {
            selectedCategory[0] = category;
            selectedSubCategory[0] = "";
            if ("自定义".equals(category)) {
                etCustomCategory.setVisibility(View.VISIBLE);
                etCustomCategory.requestFocus();
            } else {
                etCustomCategory.setVisibility(View.GONE);
            }
        });

        categoryAdapter.setOnCategoryLongClickListener(category -> {
            if (CategoryManager.isSubCategoryEnabled(getContext()) && !"自定义".equals(category)) {
                if (!category.equals(selectedCategory[0])) {
                    categoryAdapter.setSelectedCategory(category);
                    selectedCategory[0] = category;
                    selectedSubCategory[0] = "";
                    etCustomCategory.setVisibility(View.GONE);
                }

                List<String> subCats = CategoryManager.getSubCategories(getContext(), category);
                AlertDialog.Builder subBuilder = new AlertDialog.Builder(getContext());
                View subCatView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_sub_category, null);
                subBuilder.setView(subCatView);
                AlertDialog subCatDialog = subBuilder.create();

                if (subCatDialog.getWindow() != null) {
                    subCatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                TextView tvTitle = subCatView.findViewById(R.id.tv_title);
                tvTitle.setText(category + " - 选择细分");
                ChipGroup cgSubCategories = subCatView.findViewById(R.id.cg_sub_categories);
                Button btnCancel = subCatView.findViewById(R.id.btn_cancel);
                TextView tvEmpty = subCatView.findViewById(R.id.tv_empty);
                View nsvContainer = subCatView.findViewById(R.id.nsv_container);

                if (subCats.isEmpty()) {
                    cgSubCategories.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    nsvContainer.setMinimumHeight(150);
                } else {
                    cgSubCategories.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);

                    String currentSelectedSub = selectedSubCategory[0];
                    int bgDefault = ContextCompat.getColor(getContext(), R.color.cat_unselected_bg);
                    int bgChecked = ContextCompat.getColor(getContext(), R.color.app_yellow);
                    int textDefault = ContextCompat.getColor(getContext(), R.color.text_primary);
                    int textChecked = ContextCompat.getColor(getContext(), R.color.cat_selected_text);

                    int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] { } };
                    ColorStateList bgStateList = new ColorStateList(states, new int[] { bgChecked, bgDefault });
                    ColorStateList textStateList = new ColorStateList(states, new int[] { textChecked, textDefault });

                    for (String subCatName : subCats) {
                        Chip chip = new Chip(getContext());
                        chip.setText(subCatName);
                        chip.setCheckable(true);
                        chip.setClickable(true);
                        chip.setChipBackgroundColor(bgStateList);
                        chip.setTextColor(textStateList);
                        chip.setChipStrokeWidth(0);
                        chip.setCheckedIconVisible(false);

                        if (subCatName.equals(currentSelectedSub)) {
                            chip.setChecked(true);
                        }

                        chip.setOnClickListener(v -> {
                            if (subCatName.equals(selectedSubCategory[0])) {
                                selectedSubCategory[0] = null;
                                Toast.makeText(getContext(), "已取消细分", Toast.LENGTH_SHORT).show();
                            } else {
                                selectedSubCategory[0] = subCatName;
                                Toast.makeText(getContext(), "已选择: " + subCatName, Toast.LENGTH_SHORT).show();
                            }
                            categoryAdapter.setSelectedCategory(category);
                            selectedCategory[0] = category;
                            etCustomCategory.setVisibility(View.GONE);
                            subCatDialog.dismiss();
                        });
                        cgSubCategories.addView(chip);
                    }
                }
                btnCancel.setOnClickListener(v -> subCatDialog.dismiss());
                subCatDialog.show();
                return true;
            }
            return false;
        });

        rvCategory.setAdapter(categoryAdapter);

        if (existingTransaction != null && existingTransaction.subCategory != null) {
            selectedSubCategory[0] = existingTransaction.subCategory;
        }

        AssistantConfig config = new AssistantConfig(requireContext());
        boolean isAssetEnabled = config.isAssetsEnabled();

        List<AssetAccount> assetList = new ArrayList<>();
        ArrayAdapter<AssetAccount> arrayAdapter = new ArrayAdapter<AssetAccount>(getContext(), R.layout.item_spinner_dropdown) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                applyColor(view, getItem(position));
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                applyColor(view, getItem(position));
                return view;
            }
            private void applyColor(View view, AssetAccount asset) {
                if (view instanceof TextView && asset != null) {
                    TextView tv = (TextView) view;
                    tv.setText(asset.name);

                    // 1. 取消背景色，保持默认透明 (如果在Asset列表里，保留圆角逻辑即可)
                    tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);

                    // 2. 根据用户的设置，单独修改字体颜色
                    if (asset.colorType == 1) { // 红色
                        tv.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.income_red));
                    } else if (asset.colorType == 2) { // 绿色
                        tv.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.expense_green));
                    } else if (asset.colorType == 3 && asset.customColorHex != null && !asset.customColorHex.isEmpty()) { // 自定义颜色
                        try {
                            tv.setTextColor(android.graphics.Color.parseColor(asset.customColorHex));
                        } catch (Exception e) {
                            // 格式错误时回退到默认颜色
                            tv.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.text_primary));
                        }
                    } else { // 默认颜色
                        try {
                            tv.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.text_primary));
                        } catch (Exception e) {
                            tv.setTextColor(android.graphics.Color.BLACK);
                        }
                    }
                }
            }
        };
        if (isAssetEnabled) {
            spAsset.setVisibility(View.VISIBLE);
            AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
            noAsset.id = 0;
            assetList.add(noAsset);

            arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            spAsset.setAdapter(arrayAdapter);

            viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
                assetList.clear();
                assetList.add(noAsset);
                if (assets != null) {
                    for (AssetAccount a : assets) {
                        // 【修改这里】加入 a.type == 2
                        if (a.type == 0 || a.type == 1 || a.type == 2) {
                            assetList.add(a);
                        }
                    }
                }
//                List<String> names = assetList.stream().map(a -> a.name).collect(Collectors.toList());
                arrayAdapter.clear();
                arrayAdapter.addAll(assetList); // 修改：直接传入 assetList
                arrayAdapter.notifyDataSetChanged();
                if (existingTransaction != null && existingTransaction.assetId != 0) {
                    for (int i = 0; i < assetList.size(); i++) {
                        if (assetList.get(i).id == existingTransaction.assetId) {
                            spAsset.setSelection(i);
                            break;
                        }
                    }
                } else if (existingTransaction == null) {
                    int defaultAssetId = config.getDefaultAssetId();
                    if (defaultAssetId != -1) {
                        for (int i = 0; i < assetList.size(); i++) {
                            if (assetList.get(i).id == defaultAssetId) {
                                spAsset.setSelection(i);
                                break;
                            }
                        }
                    }
                }
            });
        } else {
            spAsset.setVisibility(View.GONE);
        }

        final java.util.Calendar calendar = java.util.Calendar.getInstance();
        if (existingTransaction != null) {
            calendar.setTimeInMillis(existingTransaction.date);
        } else {
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(java.util.Calendar.YEAR, date.getYear());
            calendar.set(java.util.Calendar.MONTH, date.getMonthValue() - 1);
            calendar.set(java.util.Calendar.DAY_OF_MONTH, date.getDayOfMonth());
        }

        Runnable updateDateDisplay = () -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA);
            tvDate.setText(sdf.format(calendar.getTime()));
        };
        updateDateDisplay.run();

        tvDate.setClickable(false);
        tvDate.setFocusable(false);

        // 【修改】RadioGroup 切换监听逻辑，加入震动反馈与动态显示隐藏
        rgType.setOnCheckedChangeListener((g, id) -> {
            // 触发清脆震动反馈
            g.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);

            if (id == R.id.rb_liability || id == R.id.rb_lend) {
                // 选择负债或借出时，显示对象输入框，隐藏分类选择
                etTargetObject.setVisibility(View.VISIBLE);
                rvCategory.setVisibility(View.GONE);
                etCustomCategory.setVisibility(View.GONE);

                if (id == R.id.rb_liability) {
                    etTargetObject.setHint("负债对象");
                    selectedCategory[0] = "借入";
                } else {
                    etTargetObject.setHint("借出对象");
                    selectedCategory[0] = "借出";
                }
            } else {
                // 选择收入或支出时，恢复普通的分类选择界面
                etTargetObject.setVisibility(View.GONE);
                etTargetObject.setText(""); // 清空输入
                rvCategory.setVisibility(View.VISIBLE);

                boolean switchToExpense = (id == R.id.rb_expense);
                isExpense[0] = switchToExpense;
                List<String> targetCategories = switchToExpense ? expenseCategories : incomeCategories;
                String defaultCat = targetCategories.isEmpty() ? "自定义" : targetCategories.get(0);
                categoryAdapter.updateData(targetCategories);
                categoryAdapter.setSelectedCategory(defaultCat);
                selectedCategory[0] = defaultCat;
                if ("自定义".equals(defaultCat)) {
                    etCustomCategory.setVisibility(View.VISIBLE);
                } else {
                    etCustomCategory.setVisibility(View.GONE);
                }
            }
        });

        if (existingTransaction != null) {
            btnSave.setText("保存修改");
            etAmount.setText(String.valueOf(existingTransaction.amount));
            if (existingTransaction.remark != null) etRemark.setText(existingTransaction.remark);
            if (existingTransaction.note != null) etNote.setText(existingTransaction.note);

            // 【完全修复：合并类型判断，防止后续代码覆盖“负债/借出”的选中状态和输入框内容】
            if (existingTransaction.type == 1) {
                rgType.check(R.id.rb_income);
                isExpense[0] = false;
                categoryAdapter.updateData(incomeCategories);
                String currentCat = existingTransaction.category;
                if (incomeCategories.contains(currentCat)) {
                    categoryAdapter.setSelectedCategory(currentCat);
                    selectedCategory[0] = currentCat;
                    etCustomCategory.setVisibility(View.GONE);
                } else {
                    categoryAdapter.setSelectedCategory("自定义");
                    selectedCategory[0] = "自定义";
                    etCustomCategory.setVisibility(View.VISIBLE);
                    etCustomCategory.setText(currentCat);
                }
            } else if (existingTransaction.type == 3) {
                // 负债回显，不再走下方的分类逻辑以免导致UI串台
                rgType.check(R.id.rb_liability);
                if (existingTransaction.targetObject != null) {
                    etTargetObject.setText(existingTransaction.targetObject);
                }
            } else if (existingTransaction.type == 4) {
                // 借出回显，不再走下方的分类逻辑
                rgType.check(R.id.rb_lend);
                if (existingTransaction.targetObject != null) {
                    etTargetObject.setText(existingTransaction.targetObject);
                }
            } else {
                rgType.check(R.id.rb_expense);
                isExpense[0] = true;
                categoryAdapter.updateData(expenseCategories);
                String currentCat = existingTransaction.category;
                if (expenseCategories.contains(currentCat)) {
                    categoryAdapter.setSelectedCategory(currentCat);
                    selectedCategory[0] = currentCat;
                    etCustomCategory.setVisibility(View.GONE);
                } else {
                    categoryAdapter.setSelectedCategory("自定义");
                    selectedCategory[0] = "自定义";
                    etCustomCategory.setVisibility(View.VISIBLE);
                    etCustomCategory.setText(currentCat);
                }
            }

            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                AlertDialog.Builder delBuilder = new AlertDialog.Builder(getContext());
                View delView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
                delBuilder.setView(delView);
                AlertDialog delDialog = delBuilder.create();
                if (delDialog.getWindow() != null) {
                    delDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                TextView tvMsg = delView.findViewById(R.id.tv_dialog_message);
                if (tvMsg != null) {
                    tvMsg.setText("确定要删除这条记录吗？\n删除后将无法恢复。");
                }

                delView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(dv -> delDialog.dismiss());
                delView.findViewById(R.id.btn_dialog_confirm).setOnClickListener(dv -> {
                    viewModel.deleteTransaction(existingTransaction);
                    delDialog.dismiss();
                    dialog.dismiss();
                });
                delDialog.show();
            });

            tvRevoke.setVisibility(View.VISIBLE);
            tvRevoke.setOnClickListener(v -> {
                String amountStr = etAmount.getText().toString();
                if (amountStr.isEmpty()) {
                    Toast.makeText(getContext(), "金额不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 构建一个临时账单对象，实时读取当前输入框里的最新数据
                // 【修复】构建临时账单对象时，精准识别 4 种类型并携带对象名称
                Transaction tempTx = new Transaction();
                tempTx.id = existingTransaction.id;
                tempTx.amount = Double.parseDouble(amountStr);

                int checkedId = rgType.getCheckedRadioButtonId();
                if (checkedId == R.id.rb_income) tempTx.type = 1;
                else if (checkedId == R.id.rb_expense) tempTx.type = 0;
                else if (checkedId == R.id.rb_liability) tempTx.type = 3;
                else if (checkedId == R.id.rb_lend) tempTx.type = 4;

                // 只有当类型是负债(3)或借出(4)时，才赋值对象名称，保证底层能精准删库
                if (tempTx.type == 3 || tempTx.type == 4) {
                    tempTx.targetObject = etTargetObject.getText().toString().trim();
                }

                tempTx.photoPath = currentPhotoPath[0]; // 同步最新的照片状态

                // 实时读取当前在下拉框中选择的资产
                int selectedAssetId = 0;
                if (isAssetEnabled) {
                    int selectedPos = spAsset.getSelectedItemPosition();
                    if (selectedPos >= 0 && selectedPos < assetList.size()) {
                        selectedAssetId = assetList.get(selectedPos).id;
                    }
                }
                tempTx.assetId = selectedAssetId;

                // 将包含了最新数据的临时对象传给撤回确认弹窗
                showRevokeDialog(tempTx, dialog);
            });
        } else {
            btnSave.setText("保 存");
            btnDelete.setVisibility(View.GONE);
            tvRevoke.setVisibility(View.GONE);
            SimpleDateFormat noteSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            etNote.setText(noteSdf.format(calendar.getTime()));
        }

        btnSave.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);

            String amountStr = etAmount.getText().toString();
            if (!amountStr.isEmpty()) {
                double amount = Double.parseDouble(amountStr);

                // 1. 判断类型
                int type = 0;
                int checkedId = rgType.getCheckedRadioButtonId();
                if (checkedId == R.id.rb_income) type = 1;
                else if (checkedId == R.id.rb_expense) type = 0;
                else if (checkedId == R.id.rb_liability) type = 3;
                else if (checkedId == R.id.rb_lend) type = 4;

                // 2. 判断分类与对象名
                String category = selectedCategory[0];
                if (type == 0 || type == 1) {
                    if ("自定义".equals(category)) {
                        category = etCustomCategory.getText().toString().trim();
                        if (category.isEmpty()) {
                            Toast.makeText(getContext(), "请输入自定义分类", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }

                String targetObj = "";
                if (type == 3 || type == 4) {
                    targetObj = etTargetObject.getText().toString().trim();
                    if (targetObj.isEmpty()) {
                        Toast.makeText(getContext(), "请输入负债/借出对象", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                String userRemark = etRemark.getText().toString().trim();
                String noteContent = etNote.getText().toString().trim();
                long ts = calendar.getTimeInMillis();

                int selectedAssetId = 0;
                if (isAssetEnabled) {
                    int selectedPos = spAsset.getSelectedItemPosition();
                    if (selectedPos >= 0 && selectedPos < assetList.size()) {
                        selectedAssetId = assetList.get(selectedPos).id;
                    }
                }
                String currencySymbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

                // ================== 新增/保存逻辑 ==================
                if (existingTransaction == null) {
                    Transaction t = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    t.assetId = selectedAssetId;
                    t.currencySymbol = currencySymbol;
                    t.subCategory = selectedSubCategory[0];
                    t.photoPath = currentPhotoPath[0];
                    t.targetObject = targetObj; // 保存对象名称

                    // 【新增】保存不计入预算的状态
                    t.excludeFromBudget = isExcludedFromBudget[0];

                    // 使用事务保证账单和多资产同步更新
                    final int finalAssetId = selectedAssetId;
                    final String finalTargetObj = targetObj;
                    final int finalType = type;

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase db = AppDatabase.getDatabase(getContext());
                        db.runInTransaction(() -> {
                            // a. 插入流水
                            db.transactionDao().insert(t);

                            // b. 更新原资产金额 (例如：微信/支付宝)
                            if (finalAssetId != 0) {
                                AssetAccount originalAsset = db.assetAccountDao().getAssetByIdSync(finalAssetId);
                                if (originalAsset != null) {
                                    if (finalType == 0 || finalType == 4) {
                                        // 支出 / 借出：原资产余额减少
                                        originalAsset.amount -= amount;
                                    } else if (finalType == 1 || finalType == 3) {
                                        // 收入 / 借入(负债)：原资产余额增加
                                        originalAsset.amount += amount;
                                    }
                                    originalAsset.updateTime = System.currentTimeMillis();
                                    db.assetAccountDao().update(originalAsset);
                                }
                            }

                            // c. 同步到资产模块对应的【负债】或【借出】板块
                            if (finalType == 3 || finalType == 4) {
                                int targetAssetType = (finalType == 3) ? 1 : 2; // 1:负债板块, 2:借出板块
                                AssetAccount targetAccount = db.assetAccountDao().getAssetByNameAndType(finalTargetObj, targetAssetType);

                                if (targetAccount == null) {
                                    // 该对象尚未建立资产账户，自动创建
                                    targetAccount = new AssetAccount(finalTargetObj, amount, targetAssetType);
                                    targetAccount.updateTime = System.currentTimeMillis();
                                    db.assetAccountDao().insert(targetAccount);
                                } else {
                                    // 对象已存在，直接累加欠款/借出金额
                                    targetAccount.amount += amount;
                                    targetAccount.updateTime = System.currentTimeMillis();
                                    db.assetAccountDao().update(targetAccount);
                                }
                            }
                        });

                        // 通知ViewModel刷新UI
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                viewModel.setDateRange(currentStartMillis, currentEndMillis); // 触发列表刷新
                            });

                            // 👇👇👇 一键刷新所有桌面小组件 👇👇👇
                            if (getContext() != null) {
                                com.example.budgetapp.widget.WidgetUtils.updateAllWidgets(getContext());
                            }
                            // 👆👆👆 新增结束 👆👆👆
                        }
                    });

                } else {
                    // [修改模式原有逻辑]
                    Transaction updateT = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    updateT.id = existingTransaction.id;
                    updateT.assetId = selectedAssetId;
                    updateT.currencySymbol = currencySymbol;
                    updateT.subCategory = selectedSubCategory[0];
                    updateT.photoPath = currentPhotoPath[0];
                    updateT.targetObject = targetObj;

                    // 【新增】更新不计入预算的状态
                    updateT.excludeFromBudget = isExcludedFromBudget[0];

                    viewModel.updateTransactionWithAssetSync(existingTransaction, updateT);
                }
                dialog.dismiss();
            }
        });

        dialog.setOnDismissListener(d -> {
            if (getContext() != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null && dialogView != null && dialogView.getWindowToken() != null) {
                    imm.hideSoftInputFromWindow(dialogView.getWindowToken(), 0);
                }
            }
        });

        dialog.show();
    }

    private void showPhotoDialog(String uriStr) {
        if (getContext() == null || uriStr == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        android.widget.ImageView iv = new android.widget.ImageView(getContext());
        try {
            iv.setImageURI(android.net.Uri.parse(uriStr));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            builder.setView(iv);
            builder.show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法加载图片", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCurrencySelectDialog(Button btn) {
        com.example.budgetapp.util.CurrencyUtils.showCurrencyDialog(getContext(), btn, false);
    }

    private void showRevokeDialog(Transaction transaction, AlertDialog parentDialog) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_revoke_transaction, null);
        builder.setView(view);
        AlertDialog revokeDialog = builder.create();
        if (revokeDialog.getWindow() != null) revokeDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Spinner spRevokeAsset = view.findViewById(R.id.sp_revoke_asset);
        Button btnCancel = view.findViewById(R.id.btn_revoke_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_revoke_confirm);

        List<AssetAccount> assetList = new ArrayList<>(); // 保留第 1618 行这一个定义即可
        AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
        noAsset.id = 0;

        // 这里直接开始初始化适配器
        ArrayAdapter<AssetAccount> arrayAdapter = new ArrayAdapter<AssetAccount>(getContext(), R.layout.item_spinner_dropdown) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                applyColor(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                applyColor(view, getItem(position));
                return view;
            }

            private void applyColor(View view, AssetAccount asset) {
                if (view instanceof TextView && asset != null) {
                    TextView tv = (TextView) view;
                    tv.setText(asset.name);

                    // 1. 取消背景色，保持默认透明 (如果在Asset列表里，保留圆角逻辑即可)
                    tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);

                    // 2. 根据用户的设置，单独修改字体颜色
                    if (asset.colorType == 1) { // 红色
                        tv.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.income_red));
                    } else if (asset.colorType == 2) { // 绿色
                        tv.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.expense_green));
                    } else if (asset.colorType == 3 && asset.customColorHex != null && !asset.customColorHex.isEmpty()) { // 自定义颜色
                        try {
                            tv.setTextColor(android.graphics.Color.parseColor(asset.customColorHex));
                        } catch (Exception e) {
                            // 格式错误时回退到默认颜色
                            tv.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.text_primary));
                        }
                    } else { // 默认颜色
                        try {
                            tv.setTextColor(androidx.core.content.ContextCompat.getColor(view.getContext(), R.color.text_primary));
                        } catch (Exception e) {
                            tv.setTextColor(android.graphics.Color.BLACK);
                        }
                    }
                }
            }
        };

        // ========== 【补上这两行代码】 ==========
        arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spRevokeAsset.setAdapter(arrayAdapter);
        // ========================================

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            assetList.clear();
            assetList.add(noAsset);
            if (assets != null) {
                for (AssetAccount a : assets) {
                    // 【修改这里】加入 a.type == 2
                    if (a.type == 0 || a.type == 1 || a.type == 2) {
                        assetList.add(a);
                    }
                }
            }
            List<String> names = assetList.stream().map(a -> a.name).collect(Collectors.toList());
            arrayAdapter.clear();
            arrayAdapter.addAll(assetList);
            arrayAdapter.notifyDataSetChanged();

            int targetIndex = 0;
            if (transaction.assetId != 0) {
                for (int i = 0; i < assetList.size(); i++) {
                    if (assetList.get(i).id == transaction.assetId) {
                        targetIndex = i;
                        break;
                    }
                }
            }
            spRevokeAsset.setSelection(targetIndex);
        });

        btnCancel.setOnClickListener(v -> revokeDialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            int selectedPos = spRevokeAsset.getSelectedItemPosition();
            if (selectedPos >= 0 && selectedPos < assetList.size()) {
                AssetAccount selectedAsset = assetList.get(selectedPos);

                viewModel.revokeTransaction(transaction, selectedAsset.id);

                if (transaction.photoPath != null && !transaction.photoPath.isEmpty()) {
                    try {
                        Uri uri = Uri.parse(transaction.photoPath);
                        DocumentFile file = DocumentFile.fromSingleUri(requireContext(), uri);
                        if (file != null && file.exists()) {
                            file.delete();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                String msg = selectedAsset.id == 0 ? "已撤回记录（无资产变动）" : "已撤回并退回至 " + selectedAsset.name;
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                revokeDialog.dismiss();
                if (parentDialog != null && parentDialog.isShowing()) {
                    parentDialog.dismiss();
                }
            }
        });

        revokeDialog.show();
    }

    private static class DecimalDigitsInputFilter implements InputFilter {
        private final Pattern mPattern;
        public DecimalDigitsInputFilter(int digitsAfterZero) {
            mPattern = Pattern.compile("[0-9]*+((\\.[0-9]{0," + (digitsAfterZero - 1) + "})?)||(\\.)?");
        }
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            String replacement = source.subSequence(start, end).toString();
            String newVal = dest.subSequence(0, dstart).toString() + replacement + dest.subSequence(dend, dest.length()).toString();
            Matcher matcher = mPattern.matcher(newVal);
            if (!matcher.matches()) {
                if (newVal.contains(".")) {
                    int index = newVal.indexOf(".");
                    if (newVal.length() - index - 1 > 2) return "";
                }
            }
            return null;
        }
    }

    // 新增：统一定义计算扣费日的逻辑
    private boolean isRenewalDate(RenewalItem item, LocalDate targetDate) {
        if ("Month".equals(item.period)) {
            return targetDate.getDayOfMonth() == item.day;
        } else if ("Year".equals(item.period)) {
            return targetDate.getMonthValue() == item.month && targetDate.getDayOfMonth() == item.day;
        } else if ("Custom".equals(item.period)) {
            // 安全检查，兼容旧数据
            int startYear = item.year > 2000 ? item.year : targetDate.getYear();
            LocalDate startDate;
            try {
                startDate = LocalDate.of(startYear, item.month, item.day);
            } catch (Exception e) {
                return false;
            }

            // 如果当前查看的日期在起算日期之前，则不触发
            if (targetDate.isBefore(startDate)) {
                return false;
            }

            int value = item.durationValue > 0 ? item.durationValue : 1;

            if ("Day".equals(item.durationUnit)) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, targetDate);
                return days % value == 0;
            } else if ("Week".equals(item.durationUnit)) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, targetDate);
                return days % (7L * value) == 0;
            } else if ("Month".equals(item.durationUnit)) {
                // 计算相差的自然月数
                int diffMonths = (targetDate.getYear() - startDate.getYear()) * 12 + (targetDate.getMonthValue() - startDate.getMonthValue());
                if (diffMonths >= 0 && diffMonths % value == 0) {
                    return startDate.plusMonths(diffMonths).equals(targetDate);
                }
                return false;
            } else if ("Year".equals(item.durationUnit)) {
                int diffYears = targetDate.getYear() - startDate.getYear();
                if (diffYears >= 0 && diffYears % value == 0) {
                    return startDate.plusYears(diffYears).equals(targetDate);
                }
                return false;
            }
        }
        return false;
    }

}
