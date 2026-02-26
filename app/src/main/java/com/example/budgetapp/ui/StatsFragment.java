package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.InputFilter;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatsFragment extends Fragment {

    // ... (成员变量部分保持不变) ...
    private FinanceViewModel viewModel;
    private LineChart lineChart;
    private PieChart expensePieChart;
    private PieChart incomePieChart;
    private TextView tvIncomeTitle;
    private LinearLayout layoutTrend;
    private LinearLayout layoutExpense;
    private LinearLayout layoutSummary;
    private RadioGroup rgTimeScope;
    private TextView tvDateRange;
    private TextView tvSummaryTitle;
    private TextView tvSummaryContent;
    private View dividerOvertime;
    private TextView tvOvertimeContent;
    private ScrollView scrollView;
    private GestureDetector gestureDetector;
    private float touchStartX, touchStartY;
    private boolean isDirectionLocked = false;
    private boolean isHorizontalSwipe = false;
    private int touchSlop;
    private int currentMode = 2; // 0=Year, 1=Month, 2=Week
    private LocalDate selectedDate = LocalDate.now();
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<AssetAccount> assetList = new ArrayList<>();
    private CustomMarkerView markerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);
        initViews(view);
        setupGestures();
        setupLineChart();
        setupPieCharts();
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), list -> {
            this.allTransactions = list;
            refreshData();
        });
        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            this.assetList = assets;
        });
        setupListeners(view);
        updateDateRangeDisplay();
        return view;
    }

    private void initViews(View view) {
        layoutTrend = view.findViewById(R.id.layout_trend_section);
        layoutExpense = view.findViewById(R.id.layout_expense_section);
        layoutSummary = view.findViewById(R.id.layout_summary_section);
        lineChart = view.findViewById(R.id.chart_line);
        expensePieChart = view.findViewById(R.id.chart_pie);
        incomePieChart = view.findViewById(R.id.chart_pie_income);
        tvIncomeTitle = view.findViewById(R.id.tv_income_title);
        rgTimeScope = view.findViewById(R.id.rg_time_scope);
        tvDateRange = view.findViewById(R.id.tv_current_date_range);
        tvSummaryTitle = view.findViewById(R.id.tv_summary_title);
        tvSummaryContent = view.findViewById(R.id.tv_summary_content);
        dividerOvertime = view.findViewById(R.id.divider_overtime);
        tvOvertimeContent = view.findViewById(R.id.tv_overtime_content);
        scrollView = view.findViewById(R.id.scroll_view_stats);
        touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
    }

    // ... (中间的 setupGestures, setupListeners, changeDate, updateDateRangeDisplay 等未修改方法省略，保持原样) ...
    // 为了篇幅，这里假设 setupGestures, setupListeners, changeDate, updateDateRangeDisplay, showCustomDatePicker, updatePreviewText, refreshData, processYearlyData, processMonthlyData, processWeeklyData, aggregateData, updateCharts, updateSinglePieChart, updateSummarySection, checkHasOvertime, calculateAndShowOvertime, generateThemeColors, createLineDataSet, setupLineChart, setupPieCharts, initPieChartStyle 均保持原样。
    // 请确保您的代码中包含这些方法。

    // 【修改】showCategoryDetailDialog 方法
    private void showCategoryDetailDialog(String category, int type) {
        if (allTransactions == null) return;
        long startMillis;
        long endMillis;
        String dateRangeStr = "";
        ZoneId zone = ZoneId.systemDefault();

        // 1. 确定时间范围
        if (currentMode == 0) {
            LocalDate start = LocalDate.of(selectedDate.getYear(), 1, 1);
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = start.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli();
            dateRangeStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年"));
        } else if (currentMode == 1) {
            LocalDate start = LocalDate.of(selectedDate.getYear(), selectedDate.getMonthValue(), 1);
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = start.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli();
            dateRangeStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
        } else {
            LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            startMillis = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli();
            endMillis = endOfWeek.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M.d");
            dateRangeStr = startOfWeek.format(fmt) + " - " + endOfWeek.format(fmt);
        }

        // 2. 预计算总额，用于判断"其他"
        Map<String, Double> categorySums = new HashMap<>();
        double totalScopeAmount = 0;
        for (Transaction t : allTransactions) {
            if (t.date >= startMillis && t.date < endMillis && t.type == type) {
                if (type == 1 && "加班".equals(t.category)) continue;
                categorySums.put(t.category, categorySums.getOrDefault(t.category, 0.0) + t.amount);
                totalScopeAmount += t.amount;
            }
        }
        double threshold = totalScopeAmount * 0.05;

        // 3. 筛选当前分类的所有账单（基础数据）
        // 这里使用 final 列表，方便 Lambda 表达式使用
        final List<Transaction> baseList = new ArrayList<>();
        for (Transaction t : allTransactions) {
            if (t.date >= startMillis && t.date < endMillis && t.type == type) {
                if (type == 1 && "加班".equals(t.category)) continue;

                boolean isMatch = false;
                if ("其他".equals(category)) {
                    Double catSum = categorySums.get(t.category);
                    if (catSum != null && catSum < threshold) {
                        isMatch = true;
                    } else if ("其他".equals(t.category)) {
                        isMatch = true;
                    }
                } else {
                    if (t.category.equals(category)) {
                        isMatch = true;
                    }
                }

                if (isMatch) {
                    baseList.add(t);
                }
            }
        }

        // 4. 显示弹窗
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transaction_list, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        String typeStr = (type == 1) ? "收入" : "消费";
        tvTitle.setText(dateRangeStr + " " + category + " - " + typeStr + "清单");

        RecyclerView rv = dialogView.findViewById(R.id.rv_detail_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        TransactionListAdapter adapter = new TransactionListAdapter(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(t, date);
            dialog.dismiss(); // 编辑后关闭列表，避免数据不同步
        });
        adapter.setAssets(assetList);
        // 初始显示全部
        adapter.setTransactions(baseList);
        rv.setAdapter(adapter);

        // --- 新增：二级分类筛选逻辑 ---
        View hsvSubCategories = dialogView.findViewById(R.id.hsv_sub_categories);
        ChipGroup cgSubCategories = dialogView.findViewById(R.id.cg_dialog_sub_categories);

        // 判断是否开启了二级分类
        if (CategoryManager.isSubCategoryEnabled(requireContext())) {
            // 提取该分类下实际存在的所有二级分类
            Set<String> subCats = new HashSet<>();
            for (Transaction t : baseList) {
                if (t.subCategory != null && !t.subCategory.isEmpty()) {
                    subCats.add(t.subCategory);
                }
            }

            if (!subCats.isEmpty()) {
                hsvSubCategories.setVisibility(View.VISIBLE);
                cgSubCategories.removeAllViews();

                // 准备样式颜色
                int bgDefault = ContextCompat.getColor(requireContext(), R.color.cat_unselected_bg);
                int bgChecked = ContextCompat.getColor(requireContext(), R.color.app_yellow);
                int textDefault = ContextCompat.getColor(requireContext(), R.color.text_primary);
                int textChecked = ContextCompat.getColor(requireContext(), R.color.cat_selected_text);
                int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
                ColorStateList bgStateList = new ColorStateList(states, new int[]{bgChecked, bgDefault});
                ColorStateList textStateList = new ColorStateList(states, new int[]{textChecked, textDefault});

                // 动态添加胶囊
                for (String subCat : subCats) {
                    Chip chip = new Chip(requireContext());
                    chip.setText(subCat);
                    chip.setCheckable(true);
                    chip.setClickable(true);

                    // 样式设置
                    chip.setChipBackgroundColor(bgStateList);
                    chip.setTextColor(textStateList);
                    chip.setChipStrokeWidth(0);
                    chip.setCheckedIconVisible(false);

                    // 点击筛选逻辑
                    chip.setOnClickListener(v -> {
                        List<Transaction> filteredList = new ArrayList<>();

                        // 检查是否有 Chip 被选中
                        int checkedId = cgSubCategories.getCheckedChipId();
                        if (checkedId == View.NO_ID) {
                            // 没有选中 -> 显示该大类下的全部
                            filteredList.addAll(baseList);
                        } else {
                            // 有选中 -> 筛选特定二级分类
                            for (Transaction t : baseList) {
                                if (subCat.equals(t.subCategory)) {
                                    filteredList.add(t);
                                }
                            }
                        }
                        adapter.setTransactions(filteredList);
                    });

                    cgSubCategories.addView(chip);
                }
            } else {
                hsvSubCategories.setVisibility(View.GONE);
            }
        } else {
            hsvSubCategories.setVisibility(View.GONE);
        }
        // ---------------------------

        // 隐藏不必要的按钮 (因为这是查看统计详情，不是记账)
        View btnOvertime = dialogView.findViewById(R.id.btn_add_overtime);
        View btnAdd = dialogView.findViewById(R.id.btn_add_transaction);
        if (btnOvertime != null) btnOvertime.setVisibility(View.GONE);
        if (btnAdd != null) btnAdd.setVisibility(View.GONE);

        Button btnClose = dialogView.findViewById(R.id.btn_close_dialog);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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
        EditText etCustomCategory = dialogView.findViewById(R.id.et_custom_category);
        EditText etRemark = dialogView.findViewById(R.id.et_remark);
        EditText etNote = dialogView.findViewById(R.id.et_note);
        Spinner spAsset = dialogView.findViewById(R.id.sp_asset);

        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        TextView tvRevoke = dialogView.findViewById(R.id.tv_revoke);

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        List<String> expenseCategories = CategoryManager.getExpenseCategories(getContext());
        List<String> incomeCategories = CategoryManager.getIncomeCategories(getContext());

        rvCategory.setLayoutManager(new GridLayoutManager(getContext(), 5));

        final boolean[] isExpense = {true}; // 默认支出
        final String[] selectedCategory = {expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0)};
        final String[] selectedSubCategory = {""}; // 【新增】保存选中的二级分类

        CategoryAdapter categoryAdapter = new CategoryAdapter(getContext(), expenseCategories, selectedCategory[0], category -> {
            selectedCategory[0] = category;
            selectedSubCategory[0] = ""; // 【新增】切换主分类时清空二级分类
            if ("自定义".equals(category)) {
                etCustomCategory.setVisibility(View.VISIBLE);
                etCustomCategory.requestFocus();
            } else {
                etCustomCategory.setVisibility(View.GONE);
            }
        });

        // 【新增】长按显示二级分类逻辑
        categoryAdapter.setOnCategoryLongClickListener(category -> {
            if (CategoryManager.isSubCategoryEnabled(getContext()) && !"自定义".equals(category)) {

                // 长按时立刻选中该一级分类并重置状态
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

        // === 资产配置 ===
        AssistantConfig config = new AssistantConfig(requireContext());
        boolean isAssetEnabled = config.isAssetsEnabled();

        List<AssetAccount> assetList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.item_spinner_dropdown);

        if (isAssetEnabled) {
            spAsset.setVisibility(View.VISIBLE);
            AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
            noAsset.id = 0;
            assetList.add(noAsset);

            adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            spAsset.setAdapter(adapter);

            viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
                assetList.clear();
                assetList.add(noAsset);
                if (assets != null) {
                    for (AssetAccount a : assets) {
                        if (a.type == 0) {
                            assetList.add(a);
                        }
                    }
                }
                List<String> names = new ArrayList<>();
                for (AssetAccount a : assetList) {
                    names.add(a.name);
                }
                adapter.clear();
                adapter.addAll(names);
                adapter.notifyDataSetChanged();

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
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            tvDate.setText(sdf.format(calendar.getTime()));
        };
        updateDateDisplay.run();

        tvDate.setClickable(false);
        tvDate.setFocusable(false);

        rgType.setOnCheckedChangeListener((g, id) -> {
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
        });

        if (existingTransaction != null) {
            btnSave.setText("保存修改");
            etAmount.setText(String.valueOf(existingTransaction.amount));
            if (existingTransaction.remark != null) etRemark.setText(existingTransaction.remark);
            if (existingTransaction.note != null) etNote.setText(existingTransaction.note);

            // 【新增】回显二级分类
            if (existingTransaction.subCategory != null) {
                selectedSubCategory[0] = existingTransaction.subCategory;
            }

            if (existingTransaction.type == 1) {
                rgType.check(R.id.rb_income);
                isExpense[0] = false;
                categoryAdapter.updateData(incomeCategories);
            } else {
                rgType.check(R.id.rb_expense);
                isExpense[0] = true;
                categoryAdapter.updateData(expenseCategories);
            }

            String currentCat = existingTransaction.category;
            List<String> currentList = isExpense[0] ? expenseCategories : incomeCategories;

            if (currentList.contains(currentCat)) {
                categoryAdapter.setSelectedCategory(currentCat);
                selectedCategory[0] = currentCat;
                etCustomCategory.setVisibility(View.GONE);
            } else {
                categoryAdapter.setSelectedCategory("自定义");
                selectedCategory[0] = "自定义";
                etCustomCategory.setVisibility(View.VISIBLE);
                etCustomCategory.setText(currentCat);
            }

            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext()).setTitle("确认删除").setMessage("确定要删除这条记录吗？").setPositiveButton("删除", (d, w) -> {
                    viewModel.deleteTransaction(existingTransaction);
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
            });

            tvRevoke.setVisibility(View.VISIBLE);
            tvRevoke.setOnClickListener(v -> {
                showRevokeDialog(existingTransaction, dialog);
            });

        } else {
            btnSave.setText("保存");
            btnDelete.setVisibility(View.GONE);
            tvRevoke.setVisibility(View.GONE);
            SimpleDateFormat noteSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            etNote.setText(noteSdf.format(calendar.getTime()) + " manual");
        }

        btnSave.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString();
            if (!amountStr.isEmpty()) {
                double amount = Double.parseDouble(amountStr);
                int type = rgType.getCheckedRadioButtonId() == R.id.rb_income ? 1 : 0;

                String category = selectedCategory[0];
                if ("自定义".equals(category)) {
                    category = etCustomCategory.getText().toString().trim();
                    if (category.isEmpty()) {
                        Toast.makeText(getContext(), "请输入自定义分类", Toast.LENGTH_SHORT).show();
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

                // 【修改】保存时带上二级分类数据
                if (existingTransaction == null) {
                    Transaction t = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    t.assetId = selectedAssetId;
                    t.subCategory = selectedSubCategory[0]; // 【新增】保存二级分类
                    viewModel.addTransaction(t);

                    if (selectedAssetId != 0) {
                        for (AssetAccount asset : assetList) {
                            if (asset.id == selectedAssetId) {
                                if (type == 1) asset.amount += amount;
                                else asset.amount -= amount;
                                viewModel.updateAsset(asset);
                                break;
                            }
                        }
                    }
                } else {
                    Transaction updateT = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    updateT.id = existingTransaction.id;
                    updateT.assetId = selectedAssetId;
                    updateT.subCategory = selectedSubCategory[0]; // 【新增】更新二级分类
                    viewModel.updateTransaction(updateT);
                }
                dialog.dismiss();
            }
        });
        dialog.show();
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

        List<AssetAccount> assetList = new ArrayList<>();
        AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
        noAsset.id = 0;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spRevokeAsset.setAdapter(adapter);

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            assetList.clear();
            assetList.add(noAsset);
            if (assets != null) {
                for (AssetAccount a : assets) {
                    if (a.type == 0) {
                        assetList.add(a);
                    }
                }
            }
            List<String> names = new ArrayList<>();
            for (AssetAccount a : assetList) {
                names.add(a.name);
            }
            adapter.clear();
            adapter.addAll(names);
            adapter.notifyDataSetChanged();

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
                android.widget.Toast.makeText(getContext(), "已撤回", android.widget.Toast.LENGTH_SHORT).show();
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
        public CharSequence filter(CharSequence source, int start, int end, android.text.Spanned dest, int dstart, int dend) {
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

    // ... 其他辅助方法保持不变 (setupGestures, setupListeners, changeDate 等)
    private void setupGestures() {
        if (scrollView == null) return;
        gestureDetector = new GestureDetector(requireContext(), new SwipeGestureListener());

        scrollView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getX();
                    touchStartY = event.getY();
                    isDirectionLocked = false;
                    isHorizontalSwipe = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!isDirectionLocked) {
                        float dx = Math.abs(event.getX() - touchStartX);
                        float dy = Math.abs(event.getY() - touchStartY);
                        if (dx > touchSlop || dy > touchSlop) {
                            isDirectionLocked = true;
                            if (dx > dy) isHorizontalSwipe = true;
                            else isHorizontalSwipe = false;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDirectionLocked = false;
                    isHorizontalSwipe = false;
                    break;
            }
            return isDirectionLocked && isHorizontalSwipe;
        });

        View.OnTouchListener chartTouchListener = (v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        };

        if (lineChart != null) lineChart.setOnTouchListener(chartTouchListener);
        if (expensePieChart != null) expensePieChart.setOnTouchListener(chartTouchListener);
        if (incomePieChart != null) incomePieChart.setOnTouchListener(chartTouchListener);
    }

    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 140;
        private static final int SWIPE_VELOCITY_THRESHOLD = 200;

        @Override
        public boolean onDown(@NonNull MotionEvent e) { return true; }
        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();

            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) changeDate(-1);
                    else changeDate(1);
                    return true;
                }
            }
            return false;
        }
    }

    private void setupListeners(View view) {
        ImageButton btnPrev = view.findViewById(R.id.btn_prev);
        ImageButton btnNext = view.findViewById(R.id.btn_next);

        rgTimeScope.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_year) currentMode = 0;
            else if (checkedId == R.id.rb_month) currentMode = 1;
            else if (checkedId == R.id.rb_week) currentMode = 2;

            updateDateRangeDisplay();
            refreshData();
        });

        btnPrev.setOnClickListener(v -> changeDate(-1));
        btnNext.setOnClickListener(v -> changeDate(1));

        tvDateRange.setOnClickListener(v -> {
            // 添加 CLOCK_TICK 清脆振动反馈
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            showCustomDatePicker();
        });
    }

    private void changeDate(int offset) {
        if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
        else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
        else selectedDate = selectedDate.plusWeeks(offset);
        updateDateRangeDisplay();
        refreshData();
    }

    private void updateDateRangeDisplay() {
        if (currentMode == 0) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年")));
        } else if (currentMode == 1) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        } else {
            WeekFields weekFields = WeekFields.of(Locale.CHINA);
            String yearMonthStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月"));
            int weekOfMonth = selectedDate.get(weekFields.weekOfMonth());

            LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            DateTimeFormatter rangeFormatter = DateTimeFormatter.ofPattern("M月d日");
            String startStr = startOfWeek.format(rangeFormatter);
            String endStr = endOfWeek.format(rangeFormatter);

            String title = String.format(Locale.CHINA, "%s 第%d周", yearMonthStr, weekOfMonth);
            String subtitle = String.format(Locale.CHINA, "%s - %s", startStr, endStr);

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append(title);
            ssb.append("\n");

            int startSubtitle = ssb.length();
            ssb.append(subtitle);

            if (getContext() != null) {
                int secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
                ssb.setSpan(new ForegroundColorSpan(secondaryColor), startSubtitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new AbsoluteSizeSpan(13, true), startSubtitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            tvDateRange.setText(ssb);
        }
    }

    private void showCustomDatePicker() {
        if (getContext() == null) return;
        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
        });
        int curYear = selectedDate.getYear();
        int curMonth = selectedDate.getMonthValue();
        int curDay = selectedDate.getDayOfMonth();
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
            // 新增：在数值发生变化（即滚动）时触发清脆的滴答振动
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
            int year = npYear.getValue();
            int month = npMonth.getValue();
            int day = npDay.getValue();
            selectedDate = LocalDate.of(year, month, day);
            updateDateRangeDisplay();
            refreshData();
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

    private void refreshData() {
        if (allTransactions == null) return;
        if (currentMode == 0) processYearlyData();
        else if (currentMode == 1) processMonthlyData();
        else processWeeklyData();
    }

    private void processYearlyData() {
        int year = selectedDate.getYear();
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            return date.getYear() == year ? date.getMonthValue() : -1;
        }, 12, "月", null);
    }

    private void processMonthlyData() {
        int year = selectedDate.getYear();
        int month = selectedDate.getMonthValue();
        int daysInMonth = selectedDate.lengthOfMonth();
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            return (date.getYear() == year && date.getMonthValue() == month) ? date.getDayOfMonth() : -1;
        }, daysInMonth, "日", null);
    }

    private void processWeeklyData() {
        LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        aggregateData(t -> {
            LocalDate date = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)) {
                return date.getDayOfWeek().getValue();
            }
            return -1;
        }, 7, "", new String[]{"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"});
    }

    interface IndexExtractor { int getIndex(Transaction t); }

    private void aggregateData(IndexExtractor extractor, int maxX, String suffix, String[] customLabels) {
        Map<Integer, Double> incomeMap = new HashMap<>();
        Map<Integer, Double> expenseMap = new HashMap<>();
        Map<String, Double> expensePieCats = new HashMap<>();
        Map<String, Double> incomePieCats = new HashMap<>();

        for (Transaction t : allTransactions) {
            int index = extractor.getIndex(t);
            if (index != -1) {
                if (t.type == 1) { // 收入
                    if (!"加班".equals(t.category)) {
                        incomeMap.put(index, incomeMap.getOrDefault(index, 0.0) + t.amount);
                        incomePieCats.put(t.category, incomePieCats.getOrDefault(t.category, 0.0) + t.amount);
                    }
                } else { // 支出
                    expenseMap.put(index, expenseMap.getOrDefault(index, 0.0) + t.amount);
                    expensePieCats.put(t.category, expensePieCats.getOrDefault(t.category, 0.0) + t.amount);
                }
            }
        }
        updateCharts(incomeMap, expenseMap, expensePieCats, incomePieCats, maxX, suffix, customLabels);
    }

    private void updateCharts(Map<Integer, Double> incomeMap, Map<Integer, Double> expenseMap,
                              Map<String, Double> expensePieMap, Map<String, Double> incomePieMap,
                              int maxX, String suffix, String[] customLabels) {

        boolean hasTrendData = !incomeMap.isEmpty() || !expenseMap.isEmpty();
        if (layoutTrend != null) {
            layoutTrend.setVisibility(hasTrendData ? View.VISIBLE : View.GONE);
        }

        if (hasTrendData) {
            List<Entry> inEntries = new ArrayList<>();
            List<Entry> outEntries = new ArrayList<>();
            List<Entry> netEntries = new ArrayList<>();

            for (int i = 1; i <= maxX; i++) {
                double in = incomeMap.getOrDefault(i, 0.0);
                double out = expenseMap.getOrDefault(i, 0.0);

                if (incomeMap.containsKey(i)) inEntries.add(new Entry(i, (float) in));
                if (expenseMap.containsKey(i)) outEntries.add(new Entry(i, (float) out));
                if (incomeMap.containsKey(i) || expenseMap.containsKey(i)) {
                    netEntries.add(new Entry(i, (float) (in - out)));
                }
            }

            LineDataSet setIn = createLineDataSet(inEntries, "收入", R.color.income_red);
            LineDataSet setOut = createLineDataSet(outEntries, "支出", R.color.expense_green);
            LineDataSet setNet = createLineDataSet(netEntries, "净收支", R.color.app_yellow);
            setNet.enableDashedLine(10f, 5f, 0f);

            LineData lineData = new LineData(setIn, setOut, setNet);
            lineChart.setData(lineData);

            XAxis xAxis = lineChart.getXAxis();
            xAxis.setAxisMinimum(1f);
            xAxis.setAxisMaximum((float) maxX);

            if (maxX <= 12) xAxis.setLabelCount(maxX);
            else xAxis.setLabelCount(6);

            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int index = (int) value;
                    if (index < 1 || index > maxX) return "";
                    if (customLabels != null) {
                        if (index >= 1 && index < customLabels.length) return customLabels[index];
                        return "";
                    }
                    return index + suffix;
                }
            });

            if (markerView != null) {
                markerView.setSourceData(incomeMap, expenseMap, customLabels != null ? "" : suffix, customLabels);
            }

            lineChart.animateX(600);
            lineChart.invalidate();
        }

        boolean hasExpenseData = !expensePieMap.isEmpty();
        if (layoutExpense != null) {
            layoutExpense.setVisibility(hasExpenseData ? View.VISIBLE : View.GONE);
        }
        if (hasExpenseData) {
            updateSinglePieChart(expensePieChart, expensePieMap);
        }

        double totalIncome = 0;
        for (Double val : incomePieMap.values()) totalIncome += val;

        if (totalIncome > 0) {
            tvIncomeTitle.setVisibility(View.VISIBLE);
            incomePieChart.setVisibility(View.VISIBLE);
            updateSinglePieChart(incomePieChart, incomePieMap);
        } else {
            tvIncomeTitle.setVisibility(View.GONE);
            incomePieChart.setVisibility(View.GONE);
        }

        double totalExpense = 0;
        for (Double val : expensePieMap.values()) totalExpense += val;
        updateSummarySection(expensePieMap, totalExpense);
    }

    private void updateSinglePieChart(PieChart chart, Map<String, Double> pieMap) {
        List<PieEntry> finalEntries = new ArrayList<>();
        List<Integer> finalColors = new ArrayList<>();

        double totalAmount = 0;
        for (Double val : pieMap.values()) totalAmount += val;

        double threshold = totalAmount * 0.05;
        double otherAmount = 0;

        List<Map.Entry<String, Double>> largeEntries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : pieMap.entrySet()) {
            if (entry.getValue() >= threshold) {
                largeEntries.add(entry);
            } else {
                otherAmount += entry.getValue();
            }
        }

        List<Integer> generatedColors = generateThemeColors(largeEntries.size());

        for (int i = 0; i < largeEntries.size(); i++) {
            Map.Entry<String, Double> entry = largeEntries.get(i);
            finalEntries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
            finalColors.add(generatedColors.get(i));
        }

        if (otherAmount > 0) {
            finalEntries.add(new PieEntry((float) otherAmount, "其他"));
            finalColors.add(Color.LTGRAY);
        }

        PieDataSet pieSet = new PieDataSet(finalEntries, "");
        pieSet.setColors(finalColors);

        int primaryTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int secondaryLineColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        pieSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieSet.setValueLineColor(secondaryLineColor);
        pieSet.setValueLineWidth(1.0f);
        pieSet.setSliceSpace(2f);
        pieSet.setValueLinePart1OffsetPercentage(80.f);
        pieSet.setValueLinePart1Length(0.3f);
        pieSet.setValueLinePart2Length(0.8f);

        pieSet.setValueTextSize(11f);
        pieSet.setValueTextColor(primaryTextColor);

        pieSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f%%", value);
            }
        });

        PieData pieData = new PieData(pieSet);
        pieData.setValueTextSize(11f);
        pieData.setValueTextColor(primaryTextColor);

        chart.setData(pieData);
        chart.setUsePercentValues(true);
        chart.setExtraOffsets(25f, 10f, 25f, 10f);
        chart.setCenterTextColor(primaryTextColor);
        chart.getLegend().setTextColor(primaryTextColor);

        chart.animateY(800);
        chart.invalidate();
    }

    private void updateSummarySection(Map<String, Double> pieMap, double totalAmount) {
        String scopeStr;
        if (currentMode == 0) scopeStr = "本年";
        else if (currentMode == 1) scopeStr = "本月";
        else scopeStr = "本周";
        tvSummaryTitle.setText(scopeStr + "消费");

        LocalDate startOfPeriod;
        LocalDate endOfPeriod;
        if (currentMode == 0) {
            startOfPeriod = selectedDate.with(TemporalAdjusters.firstDayOfYear());
            endOfPeriod = selectedDate.with(TemporalAdjusters.lastDayOfYear());
        } else if (currentMode == 1) {
            startOfPeriod = selectedDate.with(TemporalAdjusters.firstDayOfMonth());
            endOfPeriod = selectedDate.with(TemporalAdjusters.lastDayOfMonth());
        } else {
            startOfPeriod = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            endOfPeriod = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        }

        boolean hasOvertime = checkHasOvertime(startOfPeriod, endOfPeriod);
        boolean hasExpense = !pieMap.isEmpty() && totalAmount > 0;

        if (!hasExpense && !hasOvertime) {
            if (layoutSummary != null) layoutSummary.setVisibility(View.GONE);
            return;
        }

        if (layoutSummary != null) layoutSummary.setVisibility(View.VISIBLE);

        if (dividerOvertime != null) dividerOvertime.setVisibility(View.GONE);
        if (tvOvertimeContent != null) tvOvertimeContent.setVisibility(View.GONE);

        if (!hasExpense) {
            tvSummaryContent.setText("暂无消费记录");
        } else {
            List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(pieMap.entrySet());
            Collections.sort(sortedEntries, (e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            String[] prefixes = {"最多是", "其次是", "然后是"};

            int yellowColor = ContextCompat.getColor(requireContext(), R.color.app_yellow);
            int greenColor = ContextCompat.getColor(requireContext(), R.color.expense_green);
            int redColor = ContextCompat.getColor(requireContext(), R.color.income_red);

            int count = Math.min(sortedEntries.size(), 3);
            for (int i = 0; i < count; i++) {
                if (i > 0) ssb.append("\n");

                Map.Entry<String, Double> e = sortedEntries.get(i);
                double percent = (e.getValue() / totalAmount) * 100;

                ssb.append(prefixes[i]);

                String category = e.getKey();
                int startCat = ssb.length();
                ssb.append(category);
                ssb.setSpan(new ForegroundColorSpan(yellowColor), startCat, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                ssb.append(", ");
                ssb.append("占比");

                String percentStr = String.format(Locale.CHINA, "%.1f%%", percent);
                int startPer = ssb.length();
                ssb.append(percentStr);
                ssb.setSpan(new ForegroundColorSpan(greenColor), startPer, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                ssb.append(", ");
                ssb.append("消费");

                String amountStr = String.format(Locale.CHINA, "%.2f", e.getValue());
                int startAmt = ssb.length();
                ssb.append(amountStr);
                ssb.setSpan(new ForegroundColorSpan(redColor), startAmt, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                ssb.append("元");
            }

            ssb.append("\n");

            long days = 1;
            LocalDate today = LocalDate.now();

            if (!today.isBefore(startOfPeriod) && !today.isAfter(endOfPeriod)) {
                days = ChronoUnit.DAYS.between(startOfPeriod, today) + 1;
            } else {
                days = ChronoUnit.DAYS.between(startOfPeriod, endOfPeriod) + 1;
            }
            if (days < 1) days = 1;
            double dailyAvg = totalAmount / days;

            ssb.append("共计消费");
            String totalStr = String.format(Locale.CHINA, "%.2f", totalAmount);
            int startTotal = ssb.length();
            ssb.append(totalStr);
            ssb.setSpan(new ForegroundColorSpan(redColor), startTotal, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            ssb.append("元, ");
            ssb.append("日均消费");

            String avgStr = String.format(Locale.CHINA, "%.2f", dailyAvg);
            int startAvg = ssb.length();
            ssb.append(avgStr);
            ssb.setSpan(new ForegroundColorSpan(redColor), startAvg, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append("元");

            tvSummaryContent.setText(ssb);
        }

        if (hasOvertime) {
            calculateAndShowOvertime(startOfPeriod, endOfPeriod, scopeStr);
        }
    }

    private boolean checkHasOvertime(LocalDate start, LocalDate end) {
        long startMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (Transaction t : allTransactions) {
            if (t.date >= startMillis && t.date < endMillis && t.type == 1 && "加班".equals(t.category)) {
                return true;
            }
        }
        return false;
    }

    private void calculateAndShowOvertime(LocalDate start, LocalDate end, String scopeStr) {
        long startMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        double totalOvertimeHours = 0;
        double weekdayOvertimeHours = 0;
        double holidayOvertimeHours = 0;
        double totalOvertimeIncome = 0;

        Pattern pattern = Pattern.compile("时长:\\s*([0-9.]+)\\s*小时");

        for (Transaction t : allTransactions) {
            if (t.date >= startMillis && t.date < endMillis && t.type == 1 && "加班".equals(t.category)) {
                totalOvertimeIncome += t.amount;

                if (t.note != null) {
                    Matcher matcher = pattern.matcher(t.note);
                    if (matcher.find()) {
                        try {
                            double hours = Double.parseDouble(matcher.group(1));
                            totalOvertimeHours += hours;

                            LocalDate transDate = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
                            DayOfWeek dayOfWeek = transDate.getDayOfWeek();
                            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                                holidayOvertimeHours += hours;
                            } else {
                                weekdayOvertimeHours += hours;
                            }
                        } catch (NumberFormatException e) {
                            // ignore error
                        }
                    }
                }
            }
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int redColor = ContextCompat.getColor(requireContext(), R.color.income_red);
        int primaryColor = ContextCompat.getColor(requireContext(), R.color.text_primary);

        String title = scopeStr + "加班";
        int startTitle = ssb.length();
        ssb.append(title);
        ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startTitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new AbsoluteSizeSpan(16, true), startTitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(primaryColor), startTitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("\n");

        ssb.append("共计");
        String totalHoursStr = String.format(Locale.CHINA, "%.1f", totalOvertimeHours);
        int startTotalH = ssb.length();
        ssb.append(totalHoursStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startTotalH, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("小时, 共计获得");
        String amountStr = String.format(Locale.CHINA, "%.2f", totalOvertimeIncome);
        int startAmount = ssb.length();
        ssb.append(amountStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startAmount, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("元\n");

        ssb.append("工作日加班");
        String weekdayHoursStr = String.format(Locale.CHINA, "%.1f", weekdayOvertimeHours);
        int startWeekday = ssb.length();
        ssb.append(weekdayHoursStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startWeekday, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("小时, 节假日加班");
        String holidayHoursStr = String.format(Locale.CHINA, "%.1f", holidayOvertimeHours);
        int startHoliday = ssb.length();
        ssb.append(holidayHoursStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startHoliday, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append("小时");

        if (currentMode == 1) {
            AssistantConfig config = new AssistantConfig(requireContext());
            float baseSalary = config.getMonthlyBaseSalary();
            if (baseSalary > 0) {
                double totalSalary = baseSalary + totalOvertimeIncome;
                ssb.append("\n");
                ssb.append("本月工资");
                String salaryStr = String.format(Locale.CHINA, "%.2f", totalSalary);
                int startSalary = ssb.length();
                ssb.append(salaryStr);
                ssb.setSpan(new ForegroundColorSpan(redColor), startSalary, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.append("元");
            }
        }

        tvOvertimeContent.setText(ssb);

        if (dividerOvertime != null) dividerOvertime.setVisibility(View.VISIBLE);
        if (tvOvertimeContent != null) tvOvertimeContent.setVisibility(View.VISIBLE);
    }

    private List<Integer> generateThemeColors(int count) {
        List<Integer> colors = new ArrayList<>();
        int uiMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNightMode = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        float goldenRatio = 0.618033988749895f;
        float currentHue = (float) (Math.random() * 360f);
        int generated = 0;
        int iter = 0;
        while (generated < count && iter < 1000) {
            float h = (currentHue + (iter * goldenRatio * 360f)) % 360f;
            iter++;
            boolean isGreen = (h >= 80f && h < 160f);
            boolean isCyan = (h >= 160f && h < 200f);
            boolean isPurple = (h >= 250f && h < 320f);
            if (isGreen || isCyan || isPurple) { continue; }
            float s, v;
            if (isNightMode) { s = 0.40f + (generated % 3) * 0.1f; v = 0.70f + (generated % 2) * 0.15f; } else { s = 0.35f + (generated % 3) * 0.1f; v = 0.80f + (generated % 2) * 0.15f; }
            colors.add(Color.HSVToColor(new float[]{h, s, v}));
            generated++;
        }
        while (colors.size() < count) { colors.add(Color.LTGRAY); }
        return colors;
    }

    private LineDataSet createLineDataSet(List<Entry> entries, String label, int colorResId) {
        LineDataSet set = new LineDataSet(entries, label);
        int color = ContextCompat.getColor(requireContext(), colorResId);
        set.setColor(color);
        set.setCircleColor(color);
        set.setLineWidth(2f);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.LINEAR);
        return set;
    }

    private void setupLineChart() {
        int textColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        lineChart.getDescription().setEnabled(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setTextColor(textColor);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(textColor);
        lineChart.getLegend().setTextColor(textColor);
        lineChart.setExtraBottomOffset(10f);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setHighlightPerDragEnabled(false);
        if (getContext() != null) {
            markerView = new CustomMarkerView(getContext(), R.layout.view_chart_marker);
            markerView.setChartView(lineChart);
            lineChart.setMarker(markerView);
        }
    }

    private void setupPieCharts() {
        initPieChartStyle(expensePieChart, 0);
        initPieChartStyle(incomePieChart, 1);
    }

    private void initPieChartStyle(PieChart chart, int type) {
        if (chart == null) return;
        int textColor = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int holeColor = ContextCompat.getColor(requireContext(), R.color.bar_background);
        chart.getDescription().setEnabled(false);
        chart.setHoleRadius(40f);
        chart.setTransparentCircleRadius(0);
        chart.setHoleColor(holeColor);
        chart.setEntryLabelColor(textColor);
        chart.setEntryLabelTextSize(10f);
        chart.setRotationEnabled(false);
        Legend l = chart.getLegend();
        l.setTextColor(textColor);
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                String category = ((PieEntry) e).getLabel();
                showCategoryDetailDialog(category, type);
            }
            @Override
            public void onNothingSelected() { }
        });
    }
}