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
import android.text.TextWatcher;
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
import android.widget.ImageView;
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
import com.example.budgetapp.MainActivity;
import com.example.budgetapp.ai.AiConfig;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import android.widget.NumberPicker;
import android.text.Editable;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class DetailsFragment extends Fragment {

    private FinanceViewModel viewModel;
    private RecyclerView recyclerView;
    private DetailsAdapter adapter;
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<AssetAccount> assetList = new ArrayList<>();
    private TextView tvDateRange;

    // 🌟 修改点：使用常规的 List 替代 PagingData
    private androidx.lifecycle.LiveData<List<Transaction>> currentFilteredDataLive;

    private GestureDetector gestureDetector;
    private LocalDate selectedDate = LocalDate.now();
    private int currentMode = 1; // 0:年, 1:月, 2:周

    private static final String PREFS_NAME = "details_prefs";
    private static final String KEY_TIME_MODE = "time_mode";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault());

    private int touchSlop;

    // FAB 滚动隐藏相关
    private FloatingActionButton btnQuickRecord;
    private FabScrollListener fabScrollListener;
    private FabGestureListener fabGestureListener;
    private boolean isFabVisible = true;
    private boolean isFabAnimating = false;
    
    // 快捷按钮相关（照搬自RecordFragment）
    private AlertDialog currentDetailDialog;
    private TextView currentDetailSummaryTextView;
    private TransactionListAdapter currentDetailAdapter;
    private List<AssetAccount> cachedAssets = new ArrayList<>();
    private AssistantConfig assistantConfig;

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
                        initialX = e.getRawX();
                        initialY = e.getRawY();
                        isHorizontalSwipe = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - initialX;
                        float dy = e.getRawY() - initialY;
                        if (!isHorizontalSwipe && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            isHorizontalSwipe = true;
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
                        rv.setTranslationX(dx * 0.6f);
                        rv.setAlpha(1f - (Math.abs(dx) / screenWidth) * 0.5f);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dx > screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, screenWidth, -1);
                        } else if (dx < -screenWidth * 0.2f) {
                            finishSwipeAnimation(rv, -screenWidth, 1);
                        } else {
                            rv.animate().translationX(0f).alpha(1f).setDuration(250)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                        }
                        isHorizontalSwipe = false;
                        break;
                }
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }

    private void finishSwipeAnimation(RecyclerView rv, float targetTranslationX, int offset) {
        rv.animate()
                .translationX(targetTranslationX)
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
                    else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
                    else selectedDate = selectedDate.plusWeeks(offset);
                    updateDateRangeDisplay();

                    rv.setTranslationX(-targetTranslationX * 0.5f);

                    processAndDisplayData(0);

                    rv.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(300)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private static class FilterCriteria {
        Float minAmount, maxAmount;
        String category, assetName;
        Integer type;
        Long startTime, endTime; // 添加时间筛选字段
        void clear() {
            minAmount = null;
            maxAmount = null;
            category = null;
            assetName = null;
            type = null;
            startTime = null;
            endTime = null;
        }
    }
    private final FilterCriteria currentFilter = new FilterCriteria();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_details, container, false);

        tvDateRange = view.findViewById(R.id.tv_current_date_range);

        tvDateRange.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
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
        recyclerView.setItemAnimator(null);

        adapter = new DetailsAdapter();
        adapter.setOnTransactionClickListener(t -> {
            boolean isTransfer = (t.type == 2) || "资产互转".equals(t.category);
            if (isTransfer) {
                showDeleteTransferDialog(t);
                return;
            }

            LocalDate date = java.time.Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
            showAddOrEditDialog(t, date);
        });

        recyclerView.setAdapter(adapter);
        setupFollowHandSwipe(recyclerView);

        // 初始化 AssistantConfig
        assistantConfig = new AssistantConfig(requireContext());

        // 初始化快捷按钮（完全照搬RecordFragment）
        btnQuickRecord = view.findViewById(R.id.btn_quick_record_details);
        SharedPreferences appPrefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean showQuickButton = appPrefs.getBoolean("details_quick_button", true);
        
        if (showQuickButton && btnQuickRecord != null) {
            btnQuickRecord.setVisibility(View.VISIBLE);
            
            // 点击事件（完全照搬RecordFragment）
            btnQuickRecord.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                int quickMode = appPrefs.getInt("quick_record_mode", 0);
                if (quickMode == 1) {
                    showAddOrEditDialog(null, LocalDate.now());
                } else if (quickMode == 2) {
                    // 直接进入AI记账助手
                    AiConfig config = AiConfig.load(requireContext());
                    if (config.isEnabledAndReady()) {
                        startActivity(new Intent(requireContext(), com.example.budgetapp.ui.AiChatActivity.class));
                    } else {
                        Toast.makeText(requireContext(), "请先在设置中启用 AI 记账，并至少填写 Base URL、API Key、文本模型。", Toast.LENGTH_LONG).show();
                    }
                } else {
                    showDateDetailDialog(LocalDate.now());
                }
            });
            
            // 长按事件（完全照搬RecordFragment）
            btnQuickRecord.setOnLongClickListener(v -> {
                AiConfig config = AiConfig.load(requireContext());
                if (config.isEnabledAndReady()) {
                    startActivity(new Intent(requireContext(), com.example.budgetapp.ui.AiChatActivity.class));
                    return true;
                } else {
                    Toast.makeText(requireContext(), "请先在设置中启用 AI 记账，并至少填写 Base URL、API Key、文本模型。", Toast.LENGTH_LONG).show();
                    return false;
                }
            });
            
            // 初始化滚动监听器
            fabScrollListener = new FabScrollListener();
            recyclerView.addOnScrollListener(fabScrollListener);
            
            // 添加手势监听器（即使列表内容少也能响应滑动）
            fabGestureListener = new FabGestureListener();
            recyclerView.addOnItemTouchListener(fabGestureListener);
        } else if (btnQuickRecord != null) {
            btnQuickRecord.setVisibility(View.GONE);
        }

        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        processAndDisplayData(0);

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            this.assetList = assets;
            this.cachedAssets = assets; // 同时更新cachedAssets供快捷按钮使用
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
            viewModel.deleteTransaction(transaction);
            android.widget.Toast.makeText(getContext(), "转移记录已删除", android.widget.Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void changeDate(int offset, int direction) {
        if (recyclerView != null) {
            float screenWidth = recyclerView.getWidth();
            if (screenWidth == 0) screenWidth = 1080;
            finishSwipeAnimation(recyclerView, direction == 1 ? -screenWidth : screenWidth, offset);
        }
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
            picker.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            int y = npYear.getValue();
            int m = npMonth.getValue();
            int newMaxDays = java.time.YearMonth.of(y, m).lengthOfMonth();
            if (npDay.getMaxValue() != newMaxDays) {
                npDay.setMaxValue(newMaxDays);
                if (npDay.getValue() > newMaxDays) npDay.setValue(newMaxDays);
            }
            updatePreviewText(tvPreview, y, m, npDay.getValue());
        };

        npYear.setOnValueChangedListener(dateChangeListener);
        npMonth.setOnValueChangedListener(dateChangeListener);
        npDay.setOnValueChangedListener(dateChangeListener);

        updatePreviewText(tvPreview, curYear, curMonth, curDay);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            int year = npYear.getValue();
            int month = npMonth.getValue();
            int day = npDay.getValue();

            selectedDate = LocalDate.of(year, month, day);
            updateDateRangeDisplay();
            processAndDisplayData(0);

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

    /**
     * 显示日期选择器，选择完日期后自动弹出时间选择器
     */
    private void showTransactionDatePicker(java.util.Calendar calendar, Runnable updateDisplay) {
        if (getContext() == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = ((BottomSheetDialog) dialogInterface).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
        });

        NumberPicker npYear = dialog.findViewById(R.id.np_year);
        NumberPicker npMonth = dialog.findViewById(R.id.np_month);
        NumberPicker npDay = dialog.findViewById(R.id.np_day);
        TextView tvPreview = dialog.findViewById(R.id.tv_date_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        if (npYear == null || npMonth == null || npDay == null || btnConfirm == null || btnCancel == null) return;

        int curYear = calendar.get(java.util.Calendar.YEAR);
        int curMonth = calendar.get(java.util.Calendar.MONTH) + 1;
        int curDay = calendar.get(java.util.Calendar.DAY_OF_MONTH);

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
            picker.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            int y = npYear.getValue();
            int m = npMonth.getValue();
            int newMaxDays = java.time.YearMonth.of(y, m).lengthOfMonth();
            if (npDay.getMaxValue() != newMaxDays) {
                npDay.setMaxValue(newMaxDays);
                if (npDay.getValue() > newMaxDays) npDay.setValue(newMaxDays);
            }
            updatePreviewText(tvPreview, y, m, npDay.getValue());
        };

        npYear.setOnValueChangedListener(dateChangeListener);
        npMonth.setOnValueChangedListener(dateChangeListener);
        npDay.setOnValueChangedListener(dateChangeListener);
        updatePreviewText(tvPreview, curYear, curMonth, curDay);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            calendar.set(java.util.Calendar.YEAR, npYear.getValue());
            calendar.set(java.util.Calendar.MONTH, npMonth.getValue() - 1);
            calendar.set(java.util.Calendar.DAY_OF_MONTH, npDay.getValue());
            dialog.dismiss();
            showTransactionTimePicker(calendar, updateDisplay);
        });

        dialog.show();
    }

    /**
     * 显示时间选择器（小时 + 分钟）
     */
    private void showTransactionTimePicker(java.util.Calendar calendar, Runnable updateDisplay) {
        if (getContext() == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_bottom_time_picker);

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = ((BottomSheetDialog) dialogInterface).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
        });

        NumberPicker npHour = dialog.findViewById(R.id.np_hour);
        NumberPicker npMinute = dialog.findViewById(R.id.np_minute);
        TextView tvPreview = dialog.findViewById(R.id.tv_time_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        if (npHour == null || npMinute == null || btnConfirm == null || btnCancel == null) return;

        int curHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int curMinute = calendar.get(java.util.Calendar.MINUTE);

        npHour.setMinValue(0);
        npHour.setMaxValue(23);
        npHour.setValue(curHour);
        npHour.setFormatter(value -> String.format(Locale.CHINA, "%02d", value));

        npMinute.setMinValue(0);
        npMinute.setMaxValue(59);
        npMinute.setValue(curMinute);
        npMinute.setFormatter(value -> String.format(Locale.CHINA, "%02d", value));

        Runnable updateTimePreview = () -> {
            if (tvPreview != null) {
                tvPreview.setText(String.format(Locale.CHINA, "%02d:%02d", npHour.getValue(), npMinute.getValue()));
            }
        };
        updateTimePreview.run();

        npHour.setOnValueChangedListener((picker, oldVal, newVal) -> {
            picker.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            updateTimePreview.run();
        });
        npMinute.setOnValueChangedListener((picker, oldVal, newVal) -> {
            picker.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            updateTimePreview.run();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            calendar.set(java.util.Calendar.HOUR_OF_DAY, npHour.getValue());
            calendar.set(java.util.Calendar.MINUTE, npMinute.getValue());
            calendar.set(java.util.Calendar.SECOND, 0);
            updateDisplay.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void processAndDisplayData(int direction) {
        long[] range = getTimeRange();

        if (currentFilteredDataLive != null) {
            currentFilteredDataLive.removeObservers(getViewLifecycleOwner());
        }

        String keyword = "";
        if (!TextUtils.isEmpty(currentFilter.category)) keyword += currentFilter.category + " ";
        keyword = keyword.trim();
        if (keyword.isEmpty()) keyword = null;
        
        // 资产名称单独处理
        String assetName = null;
        if (!TextUtils.isEmpty(currentFilter.assetName)) {
            assetName = currentFilter.assetName.trim();
        }

        // 🌟 修改点：调用 ViewModel 获取常规的 List 流，添加 assetName 参数
        currentFilteredDataLive = viewModel.getFilteredTransactions(
                range[0], range[1],
                currentFilter.type,
                currentFilter.minAmount,
                currentFilter.maxAmount,
                keyword,
                assetName
        );

        currentFilteredDataLive.observe(getViewLifecycleOwner(), list -> {
            if (list != null) {
                // 🌟 修改点：将分页流替换为常规的 List 更新
                adapter.setTransactions(list);
            }

            if (getContext() != null && recyclerView != null && direction != 0) {
                Animation anim = AnimationUtils.loadAnimation(getContext(), direction == 1 ? R.anim.slide_in_right : R.anim.slide_in_left);
                recyclerView.startAnimation(anim);
            }
        });
    }

    private long[] getTimeRange() {
        // 如果筛选器中设置了自定义时间范围，优先使用
        if (currentFilter.startTime != null && currentFilter.endTime != null) {
            return new long[]{currentFilter.startTime, currentFilter.endTime + 86400000L - 1}; // 结束时间加一天减1毫秒（到23:59:59.999）
        }
        if (currentFilter.startTime != null) {
            // 只设置了起始时间，结束时间使用当前时间
            return new long[]{currentFilter.startTime, System.currentTimeMillis()};
        }
        if (currentFilter.endTime != null) {
            // 只设置了结束时间，起始时间使用2000年1月1日
            return new long[]{946656000000L, currentFilter.endTime + 86400000L - 1}; // 2000-01-01 00:00:00
        }
        
        // 没有设置自定义时间，使用原有的年/月/周逻辑
        ZoneId zone = ZoneId.systemDefault();
        if (currentMode == 0) return new long[]{selectedDate.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.lastDayOfYear()).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
        if (currentMode == 2) return new long[]{selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
        return new long[]{selectedDate.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli(), selectedDate.with(TemporalAdjusters.lastDayOfMonth()).atTime(23,59,59).atZone(zone).toInstant().toEpochMilli()};
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

        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);
        TextView tvRevoke = dialogView.findViewById(R.id.tv_revoke);

        com.google.android.material.button.MaterialButton btnTakePhoto = dialogView.findViewById(R.id.btn_take_photo);
        com.google.android.material.button.MaterialButton btnViewPhoto = dialogView.findViewById(R.id.btn_view_photo);

        etAmount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
        boolean isPhotoBackupEnabled = prefs.getBoolean("enable_photo_backup", false);

        // ================= 不计入预算逻辑开始 =================
        ImageView ivExcludeBudget = dialogView.findViewById(R.id.iv_exclude_budget);
        boolean isBudgetFeatureEnabled = prefs.getBoolean("is_budget_enabled", false);
        final boolean[] isExcludedFromBudget = {
                existingTransaction != null && existingTransaction.excludeFromBudget
        };

        if (isBudgetFeatureEnabled && ivExcludeBudget != null) {
            ivExcludeBudget.setVisibility(View.VISIBLE);
            Runnable updateDotUi = () -> {
                if (isExcludedFromBudget[0]) {
                    ivExcludeBudget.setColorFilter(ContextCompat.getColor(getContext(), R.color.app_blue));
                    ivExcludeBudget.setImageResource(R.drawable.ic_dot_filled);
                } else {
                    ivExcludeBudget.setColorFilter(android.graphics.Color.parseColor("#888888"));
                    ivExcludeBudget.setImageResource(R.drawable.ic_dot_outline);
                }
            };
            updateDotUi.run();

            ivExcludeBudget.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                isExcludedFromBudget[0] = !isExcludedFromBudget[0];
                updateDotUi.run();
                Toast.makeText(getContext(), isExcludedFromBudget[0] ? "该笔账单将不计入预算" : "该笔账单正常计入预算", Toast.LENGTH_SHORT).show();
            });
        } else if (ivExcludeBudget != null) {
            ivExcludeBudget.setVisibility(View.GONE);
        }
        // ================= 不计入预算逻辑结束 =================
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
                    int bgChecked = ContextCompat.getColor(getContext(), R.color.app_blue);
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
        com.example.budgetapp.util.AssetSpinnerAdapter assetAdapter = new com.example.budgetapp.util.AssetSpinnerAdapter(getContext());

        if (isAssetEnabled) {
            spAsset.setVisibility(View.VISIBLE);
            AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
            noAsset.id = 0;
            localAssetList.add(noAsset);

            spAsset.setAdapter(assetAdapter);
            com.example.budgetapp.util.AssetSpinnerAdapter.limitDropDownHeight(spAsset);

            viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
                localAssetList.clear();
                localAssetList.add(noAsset);
                if (assets != null) {
                    for (AssetAccount a : assets) {
                        if (a.type == 0 || a.type == 1 || a.type == 2) localAssetList.add(a);
                    }
                }
                assetAdapter.clear();
                assetAdapter.addAll(localAssetList);
                assetAdapter.notifyDataSetChanged();

                if (existingTransaction != null && existingTransaction.assetId != 0) {
                    for (int i = 0; i < localAssetList.size(); i++) {
                        if (localAssetList.get(i).id == existingTransaction.assetId) {
                            spAsset.setSelection(i);
                            break;
                        }
                    }
                } else if (existingTransaction == null) {
                    // 【新增】新建记录时，自动选择默认资产
                    int defaultAssetId = config.getDefaultAssetId();
                    if (defaultAssetId != -1) {
                        for (int i = 0; i < localAssetList.size(); i++) {
                            if (localAssetList.get(i).id == defaultAssetId) {
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
            // 【新增】新建记录时，使用当前时间，但日期设置为传入的date参数
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

        // 点击日期可修改
        tvDate.setClickable(true);
        tvDate.setFocusable(true);
        tvDate.setOnClickListener(v -> showTransactionDatePicker(calendar, updateDateDisplay));

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

                Transaction tempTx = new Transaction();
                tempTx.id = existingTransaction.id;
                tempTx.amount = Double.parseDouble(amountStr);
                tempTx.type = rgType.getCheckedRadioButtonId() == R.id.rb_income ? 1 : 0;
                tempTx.photoPath = currentPhotoPath[0];

                int selectedAssetId = 0;
                if (isAssetEnabled) {
                    int selectedPos = spAsset.getSelectedItemPosition();
                    if (selectedPos >= 0 && selectedPos < localAssetList.size()) {
                        selectedAssetId = localAssetList.get(selectedPos).id;
                    }
                }
                tempTx.assetId = selectedAssetId;

                showRevokeDialog(tempTx, dialog);
            });
        } else {
            // 【新增】新建记录时，自动在备注字段填充默认时间标识
            btnSave.setText("保 存");
            btnDelete.setVisibility(View.GONE);
            tvRevoke.setVisibility(View.GONE);
            SimpleDateFormat noteSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            etNote.setText(noteSdf.format(calendar.getTime()));
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
                    updateT.excludeFromBudget = isExcludedFromBudget[0];

                    viewModel.updateTransactionWithAssetSync(existingTransaction, updateT);
                } else {
                    // 新增交易
                    Transaction newT = new Transaction(ts, type, category, amount, noteContent, userRemark);
                    newT.assetId = selectedAssetId;
                    newT.currencySymbol = currencySymbol;
                    newT.subCategory = selectedSubCategory[0];
                    newT.photoPath = currentPhotoPath[0];
                    newT.excludeFromBudget = isExcludedFromBudget[0];

                    viewModel.addTransactionWithAssetSync(newT);
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

        com.example.budgetapp.util.AssetSpinnerAdapter assetAdapter = new com.example.budgetapp.util.AssetSpinnerAdapter(requireContext());
        spRevokeAsset.setAdapter(assetAdapter);
        com.example.budgetapp.util.AssetSpinnerAdapter.limitDropDownHeight(spRevokeAsset);

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), assets -> {
            localAssetList.clear();
            localAssetList.add(noAsset);
            if (assets != null) {
                for (AssetAccount a : assets) {
                    if (a.type == 0 || a.type == 1) localAssetList.add(a);
                }
            }
            assetAdapter.clear();
            assetAdapter.addAll(localAssetList);
            assetAdapter.notifyDataSetChanged();

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

        EditText etMin = v.findViewById(R.id.et_min_amount);
        EditText etMax = v.findViewById(R.id.et_max_amount);
        EditText etCategory = v.findViewById(R.id.et_category);
        EditText etAsset = v.findViewById(R.id.et_asset);
        TextView btnStartDate = v.findViewById(R.id.btn_start_date);
        TextView btnEndDate = v.findViewById(R.id.btn_end_date);

        Spinner spType = v.findViewById(R.id.sp_filter_type);
        String[] types = {"全部", "支出", "收入", "加班"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown, types);
        typeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spType.setAdapter(typeAdapter);

        // 数据回显
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

        if (currentFilter.type == null) {
            spType.setSelection(0);
        } else if (currentFilter.type == 0) {
            spType.setSelection(1);
        } else if (currentFilter.type == 1) {
            spType.setSelection(2);
        } else if (currentFilter.type == 2) {
            spType.setSelection(3);
        }

        // 时间回显
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        if (currentFilter.startTime != null) {
            btnStartDate.setText(dateFormat.format(new java.util.Date(currentFilter.startTime)));
        }
        if (currentFilter.endTime != null) {
            btnEndDate.setText(dateFormat.format(new java.util.Date(currentFilter.endTime)));
        }

        // 起始时间选择
        btnStartDate.setOnClickListener(view -> {
            showDateTimePicker(currentFilter.startTime, selectedMillis -> {
                currentFilter.startTime = selectedMillis;
                btnStartDate.setText(dateFormat.format(new java.util.Date(selectedMillis)));
            });
        });

        // 结束时间选择
        btnEndDate.setOnClickListener(view -> {
            showDateTimePicker(currentFilter.endTime, selectedMillis -> {
                currentFilter.endTime = selectedMillis;
                btnEndDate.setText(dateFormat.format(new java.util.Date(selectedMillis)));
            });
        });

        v.findViewById(R.id.btn_apply).setOnClickListener(view -> {
            String minStr = etMin.getText().toString();
            String maxStr = etMax.getText().toString();

            currentFilter.minAmount = minStr.isEmpty() ? null : Float.parseFloat(minStr);
            currentFilter.maxAmount = maxStr.isEmpty() ? null : Float.parseFloat(maxStr);
            currentFilter.category = etCategory.getText().toString().trim();
            currentFilter.assetName = etAsset.getText().toString().trim();

            int selectedPos = spType.getSelectedItemPosition();
            if (selectedPos == 1) {
                currentFilter.type = 0;
            } else if (selectedPos == 2) {
                currentFilter.type = 1;
            } else if (selectedPos == 3) {
                currentFilter.type = 2;
            } else {
                currentFilter.type = null;
            }

            processAndDisplayData(0);
            dialog.dismiss();
        });

        v.findViewById(R.id.btn_reset).setOnClickListener(view -> {
            currentFilter.clear();
            processAndDisplayData(0);
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 显示日期时间选择器
     */
    private void showDateTimePicker(Long currentMillis, java.util.function.Consumer<Long> onDateSelected) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        if (currentMillis != null) {
            calendar.setTimeInMillis(currentMillis);
        }

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        dialog.setContentView(R.layout.dialog_bottom_date_picker);

        dialog.setOnShowListener(d -> {
            View bottomSheet = ((com.google.android.material.bottomsheet.BottomSheetDialog) d).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
        });

        NumberPicker npYear = dialog.findViewById(R.id.np_year);
        NumberPicker npMonth = dialog.findViewById(R.id.np_month);
        NumberPicker npDay = dialog.findViewById(R.id.np_day);
        TextView tvPreview = dialog.findViewById(R.id.tv_date_preview);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        npYear.setMinValue(2000);
        npYear.setMaxValue(2050);
        npYear.setValue(calendar.get(java.util.Calendar.YEAR));

        npMonth.setMinValue(1);
        npMonth.setMaxValue(12);
        npMonth.setValue(calendar.get(java.util.Calendar.MONTH) + 1);

        npDay.setMinValue(1);
        int maxDay = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        npDay.setMaxValue(maxDay);
        npDay.setValue(calendar.get(java.util.Calendar.DAY_OF_MONTH));

        java.util.function.BiConsumer<Integer, Integer> updateDayRange = (year, month) -> {
            java.util.Calendar temp = java.util.Calendar.getInstance();
            temp.set(year, month - 1, 1);
            int maxDays = temp.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
            if (npDay.getMaxValue() != maxDays) {
                npDay.setMaxValue(maxDays);
                if (npDay.getValue() > maxDays) npDay.setValue(maxDays);
            }
        };

        NumberPicker.OnValueChangeListener listener = (picker, oldVal, newVal) -> {
            updateDayRange.accept(npYear.getValue(), npMonth.getValue());
            updatePreview(tvPreview, npYear.getValue(), npMonth.getValue(), npDay.getValue());
        };

        npYear.setOnValueChangedListener(listener);
        npMonth.setOnValueChangedListener(listener);
        npDay.setOnValueChangedListener(listener);

        updatePreview(tvPreview, npYear.getValue(), npMonth.getValue(), npDay.getValue());

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            java.util.Calendar selected = java.util.Calendar.getInstance();
            selected.set(npYear.getValue(), npMonth.getValue() - 1, npDay.getValue(), 0, 0, 0);
            selected.set(java.util.Calendar.MILLISECOND, 0);
            onDateSelected.accept(selected.getTimeInMillis());
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updatePreview(TextView tvPreview, int year, int month, int day) {
        if (tvPreview != null) {
            tvPreview.setText(String.format(Locale.getDefault(), "%d年%02d月%02d日", year, month, day));
        }
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
        
        // 重置 FAB 按钮状态
        if (btnQuickRecord != null && btnQuickRecord.getVisibility() == View.VISIBLE) {
            isFabVisible = true;
            isFabAnimating = false;
            btnQuickRecord.setTranslationY(0f);
            btnQuickRecord.setAlpha(1f);
        }
    }

    @Override
    public void onDestroyView() {
        // 移除监听器，防止内存泄漏
        if (recyclerView != null && fabScrollListener != null) {
            recyclerView.removeOnScrollListener(fabScrollListener);
        }
        if (recyclerView != null && fabGestureListener != null) {
            recyclerView.removeOnItemTouchListener(fabGestureListener);
        }
        // 清空引用
        fabScrollListener = null;
        fabGestureListener = null;
        btnQuickRecord = null;
        super.onDestroyView();
    }

    private void updateFragmentTransparency(boolean isCustomBg) {
        View view = getView();
        if (view == null) return;

        View rootLayout = view.findViewById(R.id.root_layout_details);
        TextView tvTopTitle = view.findViewById(R.id.tv_top_title);
        RadioGroup rgTimeMode = view.findViewById(R.id.rg_time_mode);
        RecyclerView recyclerDetails = view.findViewById(R.id.recycler_details);

        if (isCustomBg) {
            if (rootLayout != null) rootLayout.setBackgroundColor(Color.TRANSPARENT);
            if (tvTopTitle != null) tvTopTitle.setBackgroundColor(Color.TRANSPARENT);
            if (recyclerDetails != null) recyclerDetails.setBackgroundColor(Color.TRANSPARENT);

            if (rgTimeMode != null) {
                if (rgTimeMode.getBackground() != null) {
                    rgTimeMode.getBackground().mutate().setAlpha(230);
                }
                for (int i = 0; i < rgTimeMode.getChildCount(); i++) {
                    View child = rgTimeMode.getChildAt(i);
                    if (child.getBackground() != null) {
                        child.getBackground().mutate().setAlpha(242);
                    }
                }
            }
        } else {
            if (rootLayout != null) rootLayout.setBackgroundResource(R.color.bar_background);
            if (tvTopTitle != null) tvTopTitle.setBackgroundResource(R.color.bar_background);
            if (recyclerDetails != null) recyclerDetails.setBackgroundResource(R.color.bar_background);

            if (rgTimeMode != null) {
                if (rgTimeMode.getBackground() != null) {
                    rgTimeMode.getBackground().mutate().setAlpha(255);
                }
                for (int i = 0; i < rgTimeMode.getChildCount(); i++) {
                    View child = rgTimeMode.getChildAt(i);
                    if (child.getBackground() != null) {
                        child.getBackground().mutate().setAlpha(255);
                    }
                }
            }
        }

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

    /**
     * 显示 FAB 按钮
     */
    private void showFab() {
        if (btnQuickRecord == null || isFabVisible || isFabAnimating) return;
        isFabAnimating = true;
        btnQuickRecord.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(200)
                .withStartAction(() -> btnQuickRecord.setVisibility(View.VISIBLE))
                .withEndAction(() -> {
                    isFabVisible = true;
                    isFabAnimating = false;
                })
                .start();
    }

    /**
     * 隐藏 FAB 按钮
     */
    private void hideFab() {
        if (btnQuickRecord == null || !isFabVisible || isFabAnimating) return;
        isFabAnimating = true;
        float translationY = btnQuickRecord.getHeight() + 100f;
        btnQuickRecord.animate()
                .translationY(translationY)
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    isFabVisible = false;
                    isFabAnimating = false;
                    btnQuickRecord.setVisibility(View.GONE);
                })
                .start();
    }

    /**
     * 监听 RecyclerView 的滚动事件，根据滚动方向自动显示/隐藏浮动按钮
     */
    private class FabScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (dy > 5) {
                // 向下滚动，隐藏按钮
                hideFab();
            } else if (dy < -5) {
                // 向上滚动，显示按钮
                showFab();
            }
        }
    }

    /**
     * 监听触摸手势，即使列表内容少不需要滚动，也能响应上下滑动手势
     */
    private class FabGestureListener implements RecyclerView.OnItemTouchListener {
        private android.view.GestureDetector gestureDetector;
        
        FabGestureListener() {
            gestureDetector = new android.view.GestureDetector(getContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if (Math.abs(distanceY) > Math.abs(distanceX)) {
                        if (distanceY > 5) {
                            // 向下滑动，隐藏按钮
                            hideFab();
                        } else if (distanceY < -5) {
                            // 向上滑动，显示按钮
                            showFab();
                        }
                    }
                    return false;
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            gestureDetector.onTouchEvent(e);
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        }
    }

    /**
     * 显示单日详情对话框（完全照搬自RecordFragment）
     */
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

            boolean isTransfer = (transaction.type == 2) || "资产互转".equals(transaction.category);
            if (isTransfer) {
                showDeleteTransferDialogFromQuickButton(transaction);
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

    /**
     * 显示删除资产转移记录对话框（从快捷按钮调用）
     */
    private void showDeleteTransferDialogFromQuickButton(Transaction transaction) {
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
            viewModel.deleteTransaction(transaction);
            Toast.makeText(getContext(), "转移记录已删除", Toast.LENGTH_SHORT).show();
            dialog.dismiss();

            if (currentDetailDialog != null && currentDetailDialog.isShowing()) {
                updateDetailDialogData(LocalDate.now());
            }
        });
        dialog.show();
    }

    /**
     * 更新单日详情对话框数据（完全照搬自RecordFragment）
     */
    private void updateDetailDialogData(LocalDate date) {
        if (currentDetailAdapter == null) return;
        
        // 从ViewModel获取所有交易记录
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), allTransactions -> {
            List<Transaction> dayList = new ArrayList<>();
            if (allTransactions != null) {
                long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                dayList = allTransactions.stream()
                        .filter(t -> t.date >= start && t.date < end)
                        .collect(Collectors.toList());
            }

            // 添加自动续费预览
            List<com.example.budgetapp.database.RenewalItem> renewals = assistantConfig.getRenewalList();
            for (com.example.budgetapp.database.RenewalItem item : renewals) {
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

            // 更新汇总信息
            if (currentDetailSummaryTextView != null) {
                double dayIncome = 0;
                double dayExpense = 0;

                for (Transaction t : dayList) {
                    if ("PREVIEW_BILL".equals(t.remark)) continue;

                    boolean isTransfer = (t.type == 2) || "资产互转".equals(t.category);
                    if (isTransfer) continue;

                    if (t.type == 1) {
                        dayIncome += t.amount;
                    } else if (t.type == 0) {
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
                    int colorBalance = ContextCompat.getColor(getContext(), R.color.app_blue);

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
        });
    }

    /**
     * 显示加班记录对话框（完全照搬自RecordFragment）
     */
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

        float defaultRate = 0f;
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            defaultRate = assistantConfig.getHolidayOvertimeRate();
        } else {
            defaultRate = assistantConfig.getWeekdayOvertimeRate();
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
                double amount = rate * duration;

                Transaction t = new Transaction();
                t.date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                t.type = 2;
                t.category = "加班";
                t.amount = amount;
                t.note = String.format(Locale.CHINA, "时薪%.2f × %.1f小时", rate, duration);
                viewModel.addTransaction(t);

                Toast.makeText(getContext(), "加班记录已添加", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * 计算加班费用
     */
    private void calculateOvertime(EditText etRate, EditText etDuration, TextView tvResult) {
        String rateStr = etRate.getText().toString();
        String durationStr = etDuration.getText().toString();
        if (!rateStr.isEmpty() && !durationStr.isEmpty()) {
            try {
                double rate = Double.parseDouble(rateStr);
                double duration = Double.parseDouble(durationStr);
                double amount = rate * duration;
                tvResult.setText(String.format(Locale.CHINA, "计算结果: %.2f", amount));
            } catch (Exception e) {
                tvResult.setText("计算结果: --");
            }
        } else {
            tvResult.setText("计算结果: --");
        }
    }

    /**
     * 判断是否为续费日期（完全照搬自RecordFragment）
     */
    private boolean isRenewalDate(com.example.budgetapp.database.RenewalItem item, LocalDate targetDate) {
        if ("Month".equals(item.period)) {
            return targetDate.getDayOfMonth() == item.day;
        } else if ("Year".equals(item.period)) {
            return targetDate.getMonthValue() == item.month && targetDate.getDayOfMonth() == item.day;
        } else if ("Custom".equals(item.period)) {
            int startYear = item.year > 2000 ? item.year : targetDate.getYear();
            LocalDate startDate;
            try {
                startDate = LocalDate.of(startYear, item.month, item.day);
            } catch (Exception e) {
                return false;
            }

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