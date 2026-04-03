package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
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
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import android.widget.NumberPicker;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class DetailsFragment extends Fragment {

    private FinanceViewModel viewModel; // 统一名称以对齐记账模块
    private RecyclerView recyclerView;
    private DetailsAdapter adapter;
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<AssetAccount> assetList = new ArrayList<>();
    private TextView tvDateRange;

    // 手势检测与状态管理
    private GestureDetector gestureDetector;
    private LocalDate selectedDate = LocalDate.now();
    private int currentMode = 1; // 0:年, 1:月, 2:周

    private static final String PREFS_NAME = "details_prefs";
    private static final String KEY_TIME_MODE = "time_mode";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault());

    private static class FilterCriteria {
        Float minAmount, maxAmount;
        String category, assetName;
        Integer type; // 新增：null表示全部，0表示支出，1表示收入，2表示加班
        void clear() {
            minAmount = null;
            maxAmount = null;
            category = null;
            assetName = null;
            type = null; // 清空类型
        }
    }
    private final FilterCriteria currentFilter = new FilterCriteria();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_details, container, false);

        tvDateRange = view.findViewById(R.id.tv_current_date_range);

        // 🌟 新增：绑定点击事件，唤出自定义日期选择器
        tvDateRange.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            showCustomDatePicker();
        });

        RadioGroup rgTimeMode = view.findViewById(R.id.rg_time_mode);
        ImageButton btnPrev = view.findViewById(R.id.btn_prev);
        ImageButton btnNext = view.findViewById(R.id.btn_next);
        ImageButton btnFilter = view.findViewById(R.id.btn_filter);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentMode = prefs.getInt(KEY_TIME_MODE, 1);
        if (currentMode == 0) rgTimeMode.check(R.id.rb_year);
        else if (currentMode == 2) rgTimeMode.check(R.id.rb_week);
        else rgTimeMode.check(R.id.rb_month);

        initGestureDetector();

        rgTimeMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_year) currentMode = 0;
            else if (checkedId == R.id.rb_month) currentMode = 1;
            else if (checkedId == R.id.rb_week) currentMode = 2;
            prefs.edit().putInt(KEY_TIME_MODE, currentMode).apply();
            updateDateRangeDisplay();
            processAndDisplayData(0);
        });

        btnPrev.setOnClickListener(v -> changeDate(-1, -1));
        btnNext.setOnClickListener(v -> changeDate(1, 1));
        btnFilter.setOnClickListener(v -> showFilterDialog());

        recyclerView = view.findViewById(R.id.recycler_details);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 🌟 正确的写法：先无参实例化，再设置监听器
        adapter = new DetailsAdapter();
        adapter.setOnTransactionClickListener(t -> {
            // 拦截点击事件：如果是资产互转，呼出删除弹窗
            boolean isTransfer = (t.type == 2) || "资产互转".equals(t.category);
            if (isTransfer) {
                showDeleteTransferDialog(t);
                return;
            }

            // 正常记录：呼出原有的编辑详情弹窗
            LocalDate date = java.time.Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(t, date);
        });

        recyclerView.setAdapter(adapter);
        adapter.setOnTransactionClickListener(t -> {
            LocalDate date = java.time.Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(t, date);
        });
        recyclerView.setAdapter(adapter);

        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                return gestureDetector.onTouchEvent(e);
            }
        });

        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), list -> {
            allTransactions = list;
            processAndDisplayData(0);
        });
        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            this.assetList = assets;
            adapter.setAssets(assets);
        });

        updateDateRangeDisplay();
        return view;
    }

    private void showDeleteTransferDialog(Transaction transaction) {
        if (getContext() == null) return;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        android.view.View view = android.view.LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        android.widget.TextView tvMsg = view.findViewById(R.id.tv_dialog_message);
        if (tvMsg != null) {
            tvMsg.setText("确定要删除这条资产转移记录吗？");
        }

        view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            viewModel.deleteTransaction(transaction); // 删除记录，列表由于 LiveData 会自动刷新
            android.widget.Toast.makeText(getContext(), "转移记录已删除", android.widget.Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
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
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) changeDate(-1, -1);
                    else changeDate(1, 1);
                    return true;
                }
                return false;
            }
        });
    }

    private void changeDate(int offset, int direction) {
        if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
        else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
        else selectedDate = selectedDate.plusWeeks(offset);
        updateDateRangeDisplay();
        processAndDisplayData(direction);
    }

    private void updateDateRangeDisplay() {
        if (currentMode == 0) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年")));
        } else if (currentMode == 1) {
            tvDateRange.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        } else {
            WeekFields weekFields = WeekFields.of(Locale.CHINA);
            String title = String.format(Locale.CHINA, "%s 第%d周", selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月")), selectedDate.get(weekFields.weekOfMonth()));
            LocalDate start = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            String subtitle = String.format(Locale.CHINA, "%s - %s", start.format(DateTimeFormatter.ofPattern("M月d日")), end.format(DateTimeFormatter.ofPattern("M月d日")));

            SpannableStringBuilder ssb = new SpannableStringBuilder(title + "\n" + subtitle);
            int startSub = title.length() + 1;
            if (getContext() != null) {
                ssb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_secondary)), startSub, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new AbsoluteSizeSpan(12, true), startSub, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tvDateRange.setText(ssb);
        }
    }

    /**
     * 唤出底部日期选择器
     */
    private void showCustomDatePicker() {
        if (getContext() == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);

        // 设置透明背景以显示圆角
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        NumberPicker npYear = dialog.findViewById(R.id.np_year);
        NumberPicker npMonth = dialog.findViewById(R.id.np_month);
        NumberPicker npDay = dialog.findViewById(R.id.np_day);
        TextView tvPreview = dialog.findViewById(R.id.tv_date_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        if (npYear == null || npMonth == null || npDay == null || btnConfirm == null || btnCancel == null) return;

        int curYear = selectedDate.getYear();
        int curMonth = selectedDate.getMonthValue();
        int curDay = selectedDate.getDayOfMonth();

        npYear.setMinValue(2000);
        npYear.setMaxValue(2050);
        npYear.setValue(curYear);

        npMonth.setMinValue(1);
        npMonth.setMaxValue(12);
        npMonth.setValue(curMonth);

        npDay.setMinValue(1);
        int maxDays = java.time.YearMonth.of(curYear, curMonth).lengthOfMonth();
        npDay.setMaxValue(maxDays);
        npDay.setValue(curDay);

        NumberPicker.OnValueChangeListener dateChangeListener = (picker, oldVal, newVal) -> {
            picker.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            int y = npYear.getValue();
            int m = npMonth.getValue();
            int newMaxDays = java.time.YearMonth.of(y, m).lengthOfMonth();
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

            // 🌟 确认后更新当前选中的日期，并刷新数据
            selectedDate = LocalDate.of(year, month, day);
            updateDateRangeDisplay();
            processAndDisplayData(0);

            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 更新日期选择器的预览文本
     */
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

    private void processAndDisplayData(int direction) {
        if (allTransactions == null) return;
        long[] range = getTimeRange();

        List<Transaction> filtered = allTransactions.stream().filter(t -> {
            // 1. 时间范围筛选
            if (t.date < range[0] || t.date > range[1]) return false;
            // 2. 金额范围筛选
            if (currentFilter.minAmount != null && t.amount < currentFilter.minAmount) return false;
            if (currentFilter.maxAmount != null && t.amount > currentFilter.maxAmount) return false;

            // 🌟 新增：账单类型筛选
            if (currentFilter.type != null && t.type != currentFilter.type) {
                return false;
            }

            // 🌟 3. 分类筛选 (修改这里：同时匹配一级和二级分类)
            if (!TextUtils.isEmpty(currentFilter.category)) {
                String searchKeyword = currentFilter.category;
                boolean matchMainCategory = t.category != null && t.category.contains(searchKeyword);
                boolean matchSubCategory = t.subCategory != null && t.subCategory.contains(searchKeyword);

                // 如果一级分类和二级分类都没有包含关键字，则过滤掉
                if (!matchMainCategory && !matchSubCategory) {
                    return false;
                }
            }
            // 🌟 4. 备注与记录标识筛选 (修改这部分)
            // currentFilter.assetName 变量存储的是"备注包含"输入框的关键字
            if (!TextUtils.isEmpty(currentFilter.assetName)) {
                boolean matchRemark = t.remark != null && t.remark.contains(currentFilter.assetName);
                boolean matchNote = t.note != null && t.note.contains(currentFilter.assetName);
                // 如果备注和记录标识都没有包含该关键字，则过滤掉该账单
                if (!matchRemark && !matchNote) {
                    return false;
                }
            }

            return true;
        }).collect(Collectors.toList());
        Map<String, List<Transaction>> grouped = new TreeMap<>(Collections.reverseOrder());
        for (Transaction t : filtered) {
            String key = dateFormat.format(new Date(t.date));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<DetailsItem> displayItems = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> dayList = entry.getValue();
            float income = 0, expense = 0;
            dayList.sort((t1, t2) -> Long.compare(t2.date, t1.date));// 严格区分：只有 type 0 才是支出，type 1 是收入，type 2 (转移) 被忽略
            for (Transaction t : dayList) {
                if (t.type == 1) {
                    income += t.amount;
                } else if (t.type == 0) {
                    expense += t.amount;
                }
            }
            float dailyBalance = income - expense;
            try {
                displayItems.add(new HeaderItem(displayDateFormat.format(dateFormat.parse(entry.getKey())), income, expense, dailyBalance));
            } catch (Exception ignored) {}
            for (Transaction t : dayList) displayItems.add(new TransactionItem(t));
        }

        adapter.setData(displayItems);

        if (getContext() != null && recyclerView != null && direction != 0) {
            Animation anim = AnimationUtils.loadAnimation(getContext(), direction == 1 ? R.anim.slide_in_right : R.anim.slide_in_left);
            recyclerView.startAnimation(anim);
        }
    }

    private long[] getTimeRange() {
        ZoneId zone = ZoneId.systemDefault();
        if (currentMode == 0) return new long[]{selectedDate.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.lastDayOfYear()).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
        if (currentMode == 2) return new long[]{selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
        return new long[]{selectedDate.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.lastDayOfMonth()).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
    }

    // =========================================================================
    // 完全复刻自 RecordFragment 的完整账单详情操作逻辑
    // =========================================================================

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

        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        TextView tvRevoke = dialogView.findViewById(R.id.tv_revoke);

        com.google.android.material.button.MaterialButton btnTakePhoto = dialogView.findViewById(R.id.btn_take_photo);
        com.google.android.material.button.MaterialButton btnViewPhoto = dialogView.findViewById(R.id.btn_view_photo);

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
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
                if (deleteDialog.getWindow() != null) deleteDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                deleteView.findViewById(R.id.btn_cancel_delete).setOnClickListener(view -> deleteDialog.dismiss());
                deleteView.findViewById(R.id.btn_confirm_delete).setOnClickListener(view -> {
                    if (currentPhotoPath[0] != null && !currentPhotoPath[0].isEmpty()) {
                        try {
                            Uri uri = Uri.parse(currentPhotoPath[0]);
                            DocumentFile file = DocumentFile.fromSingleUri(requireContext(), uri);
                            if (file != null && file.exists()) file.delete();
                        } catch (Exception e) {}
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
                if (subCatDialog.getWindow() != null) subCatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

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

                        if (subCatName.equals(currentSelectedSub)) chip.setChecked(true);

                        chip.setOnClickListener(v -> {
                            if (subCatName.equals(selectedSubCategory[0])) {
                                selectedSubCategory[0] = null;
                            } else {
                                selectedSubCategory[0] = subCatName;
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

        List<AssetAccount> localAssetList = new ArrayList<>();
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), R.layout.item_spinner_dropdown);

        if (isAssetEnabled) {
            spAsset.setVisibility(View.VISIBLE);
            AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
            noAsset.id = 0;
            localAssetList.add(noAsset);

            arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            spAsset.setAdapter(arrayAdapter);

            viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
                localAssetList.clear();
                localAssetList.add(noAsset);
                if (assets != null) {
                    for (AssetAccount a : assets) {
                        if (a.type == 0 || a.type == 1) localAssetList.add(a);
                    }
                }
                List<String> names = localAssetList.stream().map(a -> a.name).collect(Collectors.toList());
                arrayAdapter.clear();
                arrayAdapter.addAll(names);
                arrayAdapter.notifyDataSetChanged();

                if (existingTransaction != null && existingTransaction.assetId != 0) {
                    for (int i = 0; i < localAssetList.size(); i++) {
                        if (localAssetList.get(i).id == existingTransaction.assetId) {
                            spAsset.setSelection(i);
                            break;
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
        }
        Runnable updateDateDisplay = () -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA);
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
                AlertDialog.Builder delBuilder = new AlertDialog.Builder(getContext());
                View delView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
                delBuilder.setView(delView);
                AlertDialog delDialog = delBuilder.create();
                if (delDialog.getWindow() != null) delDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                TextView tvMsg = delView.findViewById(R.id.tv_dialog_message);
                if (tvMsg != null) tvMsg.setText("确定要删除这条记录吗？\n删除后将无法恢复。");

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
                Transaction tempTx = new Transaction();
                tempTx.id = existingTransaction.id;
                tempTx.amount = Double.parseDouble(amountStr);
                tempTx.type = rgType.getCheckedRadioButtonId() == R.id.rb_income ? 1 : 0;
                tempTx.photoPath = currentPhotoPath[0];

                // 实时读取当前在下拉框中选择的资产（注意这里使用的是 localAssetList）
                int selectedAssetId = 0;
                if (isAssetEnabled) {
                    int selectedPos = spAsset.getSelectedItemPosition();
                    if (selectedPos >= 0 && selectedPos < localAssetList.size()) {
                        selectedAssetId = localAssetList.get(selectedPos).id;
                    }
                }
                tempTx.assetId = selectedAssetId;

                // 将包含了最新数据的临时对象传给撤回确认弹窗
                showRevokeDialog(tempTx, dialog);
            });
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
                    if (selectedPos >= 0 && selectedPos < localAssetList.size()) {
                        selectedAssetId = localAssetList.get(selectedPos).id;
                    }
                }

                String currencySymbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

                if (existingTransaction != null) {
                    Transaction updateT = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    updateT.id = existingTransaction.id;
                    updateT.assetId = selectedAssetId;
                    updateT.currencySymbol = currencySymbol;
                    updateT.subCategory = selectedSubCategory[0];
                    updateT.photoPath = currentPhotoPath[0];

                    // 改为调用同步资产的方法
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

        List<AssetAccount> localAssetList = new ArrayList<>();
        AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
        noAsset.id = 0;

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown);
        arrayAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spRevokeAsset.setAdapter(arrayAdapter);

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            localAssetList.clear();
            localAssetList.add(noAsset);
            if (assets != null) {
                for (AssetAccount a : assets) {
                    if (a.type == 0 || a.type == 1) localAssetList.add(a);
                }
            }
            List<String> names = localAssetList.stream().map(a -> a.name).collect(Collectors.toList());
            arrayAdapter.clear();
            arrayAdapter.addAll(names);
            arrayAdapter.notifyDataSetChanged();

            int targetIndex = 0;
            if (transaction.assetId != 0) {
                for (int i = 0; i < localAssetList.size(); i++) {
                    if (localAssetList.get(i).id == transaction.assetId) {
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
            if (selectedPos >= 0 && selectedPos < localAssetList.size()) {
                AssetAccount selectedAsset = localAssetList.get(selectedPos);

                viewModel.revokeTransaction(transaction, selectedAsset.id);

                if (transaction.photoPath != null && !transaction.photoPath.isEmpty()) {
                    try {
                        Uri uri = Uri.parse(transaction.photoPath);
                        DocumentFile file = DocumentFile.fromSingleUri(requireContext(), uri);
                        if (file != null && file.exists()) file.delete();
                    } catch (Exception e) {}
                }

                String msg = selectedAsset.id == 0 ? "已撤回记录（无资产变动）" : "已撤回并退回至 " + selectedAsset.name;
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                revokeDialog.dismiss();
                if (parentDialog != null && parentDialog.isShowing()) parentDialog.dismiss();
            }
        });

        revokeDialog.show();
    }

    private void showFilterDialog() {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_details_filter, null);
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(v).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // 1. 获取输入框引用
        EditText etMin = v.findViewById(R.id.et_min_amount);
        EditText etMax = v.findViewById(R.id.et_max_amount);
        EditText etCategory = v.findViewById(R.id.et_category);
        EditText etAsset = v.findViewById(R.id.et_asset);

        // 🌟 新增：获取类型下拉框并设置适配器
        Spinner spType = v.findViewById(R.id.sp_filter_type);
        String[] types = {"全部", "支出", "收入", "加班"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown, types);
        typeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spType.setAdapter(typeAdapter);

        // 2. 🌟 数据回显逻辑：如果之前有筛选条件，重新打开弹窗时将其回填
        if (currentFilter.minAmount != null) {
            etMin.setText(String.valueOf(currentFilter.minAmount).replaceAll("\\.0$", ""));
        }
        if (currentFilter.maxAmount != null) {
            etMax.setText(String.valueOf(currentFilter.maxAmount).replaceAll("\\.0$", ""));
        }
        if (!TextUtils.isEmpty(currentFilter.category)) {
            etCategory.setText(currentFilter.category);
            etCategory.setSelection(currentFilter.category.length());
        }
        if (!TextUtils.isEmpty(currentFilter.assetName)) {
            etAsset.setText(currentFilter.assetName);
        }

        // 🌟 新增：回显账单类型 (0:全部, 1:支出, 2:收入, 3:加班)
        if (currentFilter.type == null) {
            spType.setSelection(0);
        } else if (currentFilter.type == 0) {
            spType.setSelection(1);
        } else if (currentFilter.type == 1) {
            spType.setSelection(2);
        } else if (currentFilter.type == 2) {
            spType.setSelection(3);
        }

        // 3. 确认按钮逻辑：保存条件并刷新数据
        v.findViewById(R.id.btn_apply).setOnClickListener(view -> {
            String minStr = etMin.getText().toString();
            String maxStr = etMax.getText().toString();

            currentFilter.minAmount = minStr.isEmpty() ? null : Float.parseFloat(minStr);
            currentFilter.maxAmount = maxStr.isEmpty() ? null : Float.parseFloat(maxStr);
            currentFilter.category = etCategory.getText().toString().trim();
            currentFilter.assetName = etAsset.getText().toString().trim();

            // 🌟 新增：获取选中的账单类型
            int selectedPos = spType.getSelectedItemPosition();
            if (selectedPos == 1) {
                currentFilter.type = 0; // 支出
            } else if (selectedPos == 2) {
                currentFilter.type = 1; // 收入
            } else if (selectedPos == 3) {
                currentFilter.type = 2; // 加班
            } else {
                currentFilter.type = null; // 全部
            }

            processAndDisplayData(0);
            dialog.dismiss();
        });

        // 4. 重置按钮逻辑：清空条件并刷新数据
        v.findViewById(R.id.btn_reset).setOnClickListener(view -> {
            currentFilter.clear();
            processAndDisplayData(0);
            dialog.dismiss();
        });

        dialog.show();
    }

    class DecimalDigitsInputFilter implements InputFilter {
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

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
        updateFragmentTransparency(isCustomBg);
    }

    private void updateFragmentTransparency(boolean isCustomBg) {
        View view = getView();
        if (view == null) return;

        View rootLayout = view.findViewById(R.id.root_layout_details);
        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);
        RadioGroup rgTimeMode = view.findViewById(R.id.rg_time_mode);
        RecyclerView recyclerDetails = view.findViewById(R.id.recycler_details);

        if (isCustomBg) {
            // 1. 基础背景全透明，完全透出底层自定义图片
            if (rootLayout != null) rootLayout.setBackgroundColor(Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(Color.TRANSPARENT);
            if (recyclerDetails != null) recyclerDetails.setBackgroundColor(Color.TRANSPARENT);

            // 2. 年月日切换栏：同时处理外框和内部按钮的透明度
            if (rgTimeMode != null) {
                // 处理 RadioGroup 外框的透明度 (90%)
                if (rgTimeMode.getBackground() != null) {
                    rgTimeMode.getBackground().mutate().setAlpha(230);
                }
                // 遍历里面的 "年", "月", "周" 按钮
                for (int i = 0; i < rgTimeMode.getChildCount(); i++) {
                    View child = rgTimeMode.getChildAt(i);
                    // 将按钮被选中时的背景块透明度改为 80% (Alpha: 204)，与外框形成层次感
                    if (child.getBackground() != null) {
                        child.getBackground().mutate().setAlpha(242);
                    }
                }
            }
        } else {
            // ================= 恢复系统默认模式 =================
            if (rootLayout != null) rootLayout.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);
            if (recyclerDetails != null) recyclerDetails.setBackgroundResource(R.color.bar_background);

            if (rgTimeMode != null) {
                if (rgTimeMode.getBackground() != null) {
                    rgTimeMode.getBackground().mutate().setAlpha(255); // 恢复不透明
                }
                for (int i = 0; i < rgTimeMode.getChildCount(); i++) {
                    View child = rgTimeMode.getChildAt(i);
                    if (child.getBackground() != null) {
                        child.getBackground().mutate().setAlpha(255); // 恢复不透明
                    }
                }
            }
        }

        // 通知列表刷新，以更新具体账单和日期头的透明度
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void applyThemeBackground() {
        View view = getView();
        if (view == null) return;

        SharedPreferences appPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);
        RecyclerView recyclerDetails = view.findViewById(R.id.recycler_details);

        if (appPrefs.getInt("theme_mode", -1) == 3) {
            view.setBackgroundColor(Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(Color.TRANSPARENT);
            if (recyclerDetails != null) recyclerDetails.setBackgroundColor(Color.TRANSPARENT);
        } else {
            view.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);
            if (recyclerDetails != null) recyclerDetails.setBackgroundResource(R.color.bar_background);
        }
    }

}