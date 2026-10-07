package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.RecurringRule;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.RecurringTransactionManager;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 周期记账管理页面
 */
public class RecurringTransactionActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View layoutEmptyState;
    private TextView tvEmptyHint;
    private FloatingActionButton fabAdd;
    private RecurringRuleAdapter adapter;
    private FinanceViewModel viewModel;
    private List<AssetAccount> allAssets = new ArrayList<>();

    // =========================================================================
    // 参考 CurrencySettingsActivity.java 的完整 49 种全球币种定义
    // =========================================================================
    private static final String[] CURRENCY_CODES = {
            "CNY", "USD", "EUR", "GBP", "HKD", "TWD",
            "JPY", "KRW", "CAD", "AUD", "SGD", "NZD",
            "INR", "RUB", "THB", "VND", "PHP", "BRL",
            "IDR", "MYR", "CHF", "TRY", "ILS",
            "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON", "BGN", "RSD", "ISK",
            "BYN", "UAH", "MDL", "ALL", "BAM", "MKD", "GEL", "AMD", "AZN",
            "KWD", "SAR", "AED", "ZAR", "NGN", "EGP"
    };

    private static final String[] CURRENCY_SYMBOLS = {
            "¥", "$", "€", "£", "HK$", "NT$",
            "JP¥", "₩", "C$", "A$", "S$", "NZ$",
            "₹", "₽", "฿", "₫", "₱", "R$",
            "Rp", "RM", "CHF", "₺", "₪",
            "kr", "kr", "kr", "zł", "Kč", "Ft", "lei", "лв", "RSD", "kr",
            "BYN", "₴", "L", "Lek", "KM", "den", "₾", "֏", "₼",
            "KD", "SR", "DH", "R", "₦", "E£"
    };

    private static final String[] CURRENCY_NAMES = {
            "人民币", "美元", "欧元", "英镑", "港币", "新台币",
            "日元", "韩元", "加元", "澳元", "新加坡元", "新西兰元",
            "印度卢比", "俄罗斯卢布", "泰铢", "越南盾", "菲律宾比索", "巴西雷亚尔",
            "印尼卢比", "马来西亚林吉特", "瑞士法郎", "土耳其里拉", "以色列新谢克尔",
            "瑞典克朗", "挪威克朗", "丹麦克朗", "波兰兹罗提", "捷克克朗", "匈牙利福林", "罗马尼亚列伊", "保加利亚列弗", "塞尔维亚第纳尔", "冰岛克朗",
            "白俄罗斯卢布", "乌克兰格里夫纳", "摩尔多瓦列伊", "阿尔巴尼亚列克", "波黑可兑换马克", "北马其顿第纳尔", "格鲁吉亚拉里", "亚美尼亚德拉姆", "阿塞拜疆马纳特",
            "科威特第纳尔", "沙特里亚尔", "阿联酋迪拉姆", "南非兰特", "尼日利亚奈拉", "埃及镑"
    };

    private static class CurrencyItem {
        final String code;
        final String symbol;
        final String name;
        final boolean isSelected;

        CurrencyItem(String code, String symbol, String name, boolean isSelected) {
            this.code = code;
            this.symbol = symbol;
            this.name = name;
            this.isSelected = isSelected;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_recurring_transaction);

        View rootView = findViewById(R.id.recurring_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(originalPaddingLeft + insets.left, originalPaddingTop + insets.top,
                    originalPaddingRight + insets.right, originalPaddingBottom + insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        recyclerView = findViewById(R.id.recycler_recurring_rules);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        tvEmptyHint = findViewById(R.id.tv_empty_hint);
        fabAdd = findViewById(R.id.fab_add_rule);

        // 参考资产模块对悬浮按钮的滑动隐藏/显示处理
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 10 && fabAdd.isShown()) {
                    // 向上滚动列表（手指向上推），隐藏悬浮按钮
                    fabAdd.hide();
                } else if (dy < -10 && !fabAdd.isShown()) {
                    // 向下滚动列表（手指向下拉），显示悬浮按钮
                    fabAdd.show();
                }
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecurringRuleAdapter(this);
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);

        // 监听资产列表
        viewModel.getAllAssets().observe(this, assets -> {
            allAssets = assets != null ? assets : new ArrayList<>();
            if (adapter.getItemCount() > 0) {
                adapter.notifyDataSetChanged();
            }
        });

        // 监听周期规则列表
        AppDatabase.getDatabase(this).recurringRuleDao().getAllRules().observe(this, rules -> {
            boolean isEmpty = (rules == null || rules.isEmpty());
            updateEmptyState(isEmpty);
            if (!isEmpty) {
                adapter.setRules(rules, allAssets);
            }
        });

        // 点击添加规则
        fabAdd.setOnClickListener(v -> showRuleDialog(null));

        // 设置 adapter 的交互回调
        adapter.setOnItemClickListener(new RecurringRuleAdapter.OnItemClickListener() {
            @Override
            public void onEditClick(RecurringRule rule) {
                showRuleDialog(rule);
            }

            @Override
            public void onDeleteClick(RecurringRule rule) {
                showDeleteConfirmDialog(rule);
            }

            @Override
            public void onToggleChange(RecurringRule rule, boolean isEnabled) {
                rule.isEnabled = isEnabled;
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    AppDatabase.getDatabase(RecurringTransactionActivity.this)
                            .recurringRuleDao().update(rule);
                });
            }
        });
    }

    private void updateEmptyState(boolean isEmpty) {
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (tvEmptyHint != null) {
            tvEmptyHint.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /**
     * 显示规则编辑对话框
     */
    private void showRuleDialog(RecurringRule existingRule) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recurring_rule, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_type);
        RadioButton rbExpense = dialogView.findViewById(R.id.rb_expense);
        RadioButton rbIncome = dialogView.findViewById(R.id.rb_income);
        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        TextView tvCurrency = dialogView.findViewById(R.id.tv_currency);
        TextView tvCategory = dialogView.findViewById(R.id.tv_category);
        Spinner spinnerAsset = dialogView.findViewById(R.id.spinner_asset);
        EditText etInterval = dialogView.findViewById(R.id.et_interval);
        Spinner spinnerPeriodUnit = dialogView.findViewById(R.id.spinner_period_unit);
        TextView tvStartDate = dialogView.findViewById(R.id.tv_start_date);
        SwitchCompat switchHasEndDate = dialogView.findViewById(R.id.switch_has_end_date);
        TextView tvEndDate = dialogView.findViewById(R.id.tv_end_date);
        TextView tvTriggerTime = dialogView.findViewById(R.id.tv_trigger_time);
        EditText etNote = dialogView.findViewById(R.id.et_note);

        tvTitle.setText(existingRule == null ? "新建周期记账" : "编辑周期记账");

        // 1. 周期单位下拉
        String[] periodUnits = {"天", "周", "月", "年"};
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, periodUnits);
        periodAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerPeriodUnit.setAdapter(periodAdapter);

        // 2. 使用原项目的 AssistantConfig 精准获取“资产模块”设定的默认资产 ID
        com.example.budgetapp.util.AssistantConfig assistantConfig =
                new com.example.budgetapp.util.AssistantConfig(this);
        int defaultAssetId = assistantConfig.getDefaultAssetId();

        int defaultAssetIndex = 0;
        List<String> assetNames = new ArrayList<>();
        for (int i = 0; i < allAssets.size(); i++) {
            AssetAccount asset = allAssets.get(i);
            assetNames.add(asset.name + " (" + asset.currencySymbol + String.format(Locale.getDefault(), "%.2f", asset.amount) + ")");

            // 匹配用户在“资产模块”中长按卡片设定的默认资产 ID
            if (defaultAssetId != -1 && asset.id == defaultAssetId) {
                defaultAssetIndex = i;
            }
        }

        ArrayAdapter<String> assetAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, assetNames);
        assetAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerAsset.setAdapter(assetAdapter);

        final String[] selectedCurrency = {getDefaultCurrency()};
        final String[] selectedCategory = {null};
        final String[] selectedSubCategory = {""};
        final long[] startDateMillis = {System.currentTimeMillis()};
        final long[] endDateMillis = {0};

        if (existingRule != null) {
            // 编辑模式：回填规则对应数据
            if (existingRule.type == 0) {
                rbExpense.setChecked(true);
            } else {
                rbIncome.setChecked(true);
            }
            etAmount.setText(String.format(Locale.getDefault(), "%.2f", existingRule.amount));
            selectedCurrency[0] = existingRule.currencySymbol;
            tvCurrency.setText(selectedCurrency[0] + " " + getCurrencyName(selectedCurrency[0]));
            selectedCategory[0] = existingRule.category;
            selectedSubCategory[0] = existingRule.subCategory != null ? existingRule.subCategory : "";
            tvCategory.setText(existingRule.category + (TextUtils.isEmpty(selectedSubCategory[0]) ? "" : " - " + selectedSubCategory[0]));
            etInterval.setText(String.valueOf(existingRule.periodInterval));
            spinnerPeriodUnit.setSelection(getPeriodUnitIndex(existingRule.periodUnit));
            startDateMillis[0] = existingRule.startDate;
            tvStartDate.setText(formatDate(existingRule.startDate));

            if (existingRule.endDate > 0) {
                switchHasEndDate.setChecked(true);
                tvEndDate.setVisibility(View.VISIBLE);
                endDateMillis[0] = existingRule.endDate;
                tvEndDate.setText(formatDate(existingRule.endDate));
            } else {
                switchHasEndDate.setChecked(false);
                tvEndDate.setVisibility(View.GONE);
            }

            tvTriggerTime.setText(!TextUtils.isEmpty(existingRule.triggerTime) ? existingRule.triggerTime : "00:00");
            etNote.setText(existingRule.note);

            // 选中已有规则的资产
            int targetAssetIndex = 0;
            for (int i = 0; i < allAssets.size(); i++) {
                if (allAssets.get(i).id == existingRule.assetId) {
                    targetAssetIndex = i;
                    break;
                }
            }
            final int finalTarget = targetAssetIndex;
            spinnerAsset.post(() -> spinnerAsset.setSelection(finalTarget, false));
        } else {
            // 新建模式：每次新建均强制选中资产模块的“默认资产”
            Calendar calendar = Calendar.getInstance();
            startDateMillis[0] = calendar.getTimeInMillis();
            tvStartDate.setText(formatDate(startDateMillis[0]));
            tvTriggerTime.setText("00:00");

            final int targetDefault = defaultAssetIndex;
            if (!allAssets.isEmpty()) {
                spinnerAsset.post(() -> spinnerAsset.setSelection(targetDefault, false));

                // 币种同步为该默认资产所配的币种符号
                AssetAccount defaultAccount = allAssets.get(targetDefault);
                if (!TextUtils.isEmpty(defaultAccount.currencySymbol)) {
                    selectedCurrency[0] = defaultAccount.currencySymbol;
                }
            }
            tvCurrency.setText(selectedCurrency[0] + " " + getCurrencyName(selectedCurrency[0]));
        }

        // 切换收支时清空已选分类
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            selectedCategory[0] = null;
            selectedSubCategory[0] = "";
            tvCategory.setText("选择分类");
        });

        // 币种选择弹窗
        tvCurrency.setOnClickListener(v -> showCurrencySelectDialog(selectedCurrency[0], item -> {
            selectedCurrency[0] = item.symbol;
            tvCurrency.setText(item.symbol + " " + item.name);
        }));

        // 分类选择：支持长按选择二级分类
        tvCategory.setOnClickListener(v -> {
            int currentType = rbExpense.isChecked() ? 0 : 1;
            showCategorySelectDialog(currentType, selectedCategory[0], selectedSubCategory[0], (primary, sub) -> {
                selectedCategory[0] = primary;
                selectedSubCategory[0] = sub;
                if (TextUtils.isEmpty(sub)) {
                    tvCategory.setText(primary);
                } else {
                    tvCategory.setText(primary + " - " + sub);
                }
            });
        });

        // 日期与时间选择
        tvStartDate.setOnClickListener(v -> showDatePicker(tvStartDate, startDateMillis));

        switchHasEndDate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvEndDate.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked && endDateMillis[0] == 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(startDateMillis[0]);
                cal.add(Calendar.MONTH, 1);
                endDateMillis[0] = cal.getTimeInMillis();
                tvEndDate.setText(formatDate(endDateMillis[0]));
            }
        });

        tvEndDate.setOnClickListener(v -> showDatePicker(tvEndDate, endDateMillis));
        tvTriggerTime.setOnClickListener(v -> showTimePicker(tvTriggerTime));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            if (TextUtils.isEmpty(amountStr)) {
                Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入正确的金额格式", Toast.LENGTH_SHORT).show();
                return;
            }

            if (amount <= 0) {
                Toast.makeText(this, "金额必须大于0", Toast.LENGTH_SHORT).show();
                return;
            }

            if (TextUtils.isEmpty(selectedCategory[0])) {
                Toast.makeText(this, "请选择分类", Toast.LENGTH_SHORT).show();
                return;
            }

            if (allAssets.isEmpty() || spinnerAsset.getSelectedItemPosition() < 0) {
                Toast.makeText(this, "请先创建资产账户", Toast.LENGTH_SHORT).show();
                return;
            }

            String intervalStr = etInterval.getText().toString().trim();
            int interval;
            try {
                interval = TextUtils.isEmpty(intervalStr) ? 1 : Integer.parseInt(intervalStr);
            } catch (NumberFormatException e) {
                interval = 1;
            }

            if (interval <= 0) {
                Toast.makeText(this, "周期步长必须大于0", Toast.LENGTH_SHORT).show();
                return;
            }

            if (switchHasEndDate.isChecked() && endDateMillis[0] < startDateMillis[0]) {
                Toast.makeText(this, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show();
                return;
            }

            RecurringRule rule = existingRule != null ? existingRule : new RecurringRule();
            rule.type = rbExpense.isChecked() ? 0 : 1;
            rule.amount = amount;
            rule.currencySymbol = selectedCurrency[0];
            rule.category = selectedCategory[0];
            rule.subCategory = selectedSubCategory[0];
            rule.assetId = allAssets.get(spinnerAsset.getSelectedItemPosition()).id;
            rule.periodInterval = interval;
            rule.periodUnit = getPeriodUnitFromIndex(spinnerPeriodUnit.getSelectedItemPosition());
            rule.startDate = startDateMillis[0];
            rule.endDate = switchHasEndDate.isChecked() ? endDateMillis[0] : 0;
            rule.triggerTime = tvTriggerTime.getText().toString().trim();
            rule.note = etNote.getText().toString().trim();
            if (existingRule == null) {
                rule.isEnabled = true;
            }

            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
                if (existingRule == null) {
                    db.recurringRuleDao().insert(rule);
                } else {
                    db.recurringRuleDao().update(rule);
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, existingRule == null ? "规则已创建" : "规则已更新",
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();

                    Calendar cal = Calendar.getInstance();
                    int year = cal.get(Calendar.YEAR);
                    int month = cal.get(Calendar.MONTH);
                    AppDatabase.databaseWriteExecutor.execute(() ->
                            RecurringTransactionManager.generateMonthlyRecurringTransactions(this, year, month)
                    );
                });
            });
        });

        dialog.show();
    }

    /**
     * 参考 CurrencySettingsActivity.java 的币种选择网格弹窗
     * 采用 4 列网格展示 49 种货币，复用 item_currency.xml 布局
     */
    private void showCurrencySelectDialog(String currentSymbol, OnCurrencySelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_currency_select, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            tvTitle.setText("选择交易币种");
        }

        RecyclerView recyclerView = view.findViewById(R.id.rv_currency_list);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 4));

            List<CurrencyItem> items = new ArrayList<>();
            for (int i = 0; i < CURRENCY_CODES.length; i++) {
                boolean isSelected = CURRENCY_SYMBOLS[i].equals(currentSymbol);
                items.add(new CurrencyItem(CURRENCY_CODES[i], CURRENCY_SYMBOLS[i], CURRENCY_NAMES[i], isSelected));
            }

            CurrencyGridAdapter gridAdapter = new CurrencyGridAdapter(items, item -> {
                if (listener != null) {
                    listener.onSelected(item);
                }
                dialog.dismiss();
            });
            recyclerView.setAdapter(gridAdapter);
        }
        dialog.show();
    }

    private interface OnCurrencySelectedListener {
        void onSelected(CurrencyItem item);
    }

    /**
     * 币种网格 Adapter（样式与 CurrencySettingsActivity 完全一致）
     */
    private static class CurrencyGridAdapter extends RecyclerView.Adapter<CurrencyGridAdapter.ViewHolder> {
        private final List<CurrencyItem> items;
        private final OnCurrencySelectedListener listener;

        public CurrencyGridAdapter(List<CurrencyItem> items, OnCurrencySelectedListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_currency, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CurrencyItem item = items.get(position);
            Context context = holder.itemView.getContext();
            CardView cardRoot = (CardView) holder.itemView;
            TextView tvSymbol = holder.itemView.findViewById(R.id.tv_symbol);
            TextView tvName = holder.itemView.findViewById(R.id.tv_name);

            if (tvSymbol != null) tvSymbol.setText(item.symbol);
            if (tvName != null) tvName.setText(item.name);

            if (item.isSelected) {
                cardRoot.setCardBackgroundColor(ContextCompat.getColor(context, R.color.currency_item_bg_selected));
                if (tvSymbol != null) tvSymbol.setTextColor(ContextCompat.getColor(context, R.color.currency_symbol_text_selected));
                if (tvName != null) tvName.setTextColor(ContextCompat.getColor(context, R.color.currency_symbol_text_selected));
            } else {
                cardRoot.setCardBackgroundColor(ContextCompat.getColor(context, R.color.currency_item_bg_normal));
                if (tvSymbol != null) tvSymbol.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                if (tvName != null) tvName.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }

            holder.itemView.setOnClickListener(v -> listener.onSelected(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    /**
     * 弹出分类选择弹窗（支持长按选择二级分类）
     */
    private void showCategorySelectDialog(int transactionType, String currentCategory, String currentSubCategory,
                                          OnCategorySelectedListener listener) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recurring_category_select, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        RecyclerView rvCategory = dialogView.findViewById(R.id.rv_category);

        List<String> categories = (transactionType == 0)
                ? CategoryManager.getExpenseCategories(this)
                : CategoryManager.getIncomeCategories(this);

        boolean isDetailed = CategoryManager.isDetailedCategoryEnabled(this);
        if (isDetailed) {
            FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(this);
            flexboxLayoutManager.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
            flexboxLayoutManager.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
            flexboxLayoutManager.setJustifyContent(com.google.android.flexbox.JustifyContent.FLEX_START);
            rvCategory.setLayoutManager(flexboxLayoutManager);
        } else {
            rvCategory.setLayoutManager(new GridLayoutManager(this, 5));
        }

        final String[] selectedCat = {currentCategory != null ? currentCategory : (categories.isEmpty() ? "" : categories.get(0))};
        final String[] selectedSub = {currentSubCategory != null ? currentSubCategory : ""};

        CategoryAdapter categoryAdapter = new CategoryAdapter(this, categories, selectedCat[0], cat -> {
            selectedCat[0] = cat;
            selectedSub[0] = "";
            if (listener != null) {
                listener.onSelected(selectedCat[0], selectedSub[0]);
            }
            dialog.dismiss();
        });

        categoryAdapter.setOnCategoryLongClickListener(cat -> {
            if (CategoryManager.isSubCategoryEnabled(this) && !"其他".equals(cat)) {
                selectedCat[0] = cat;
                categoryAdapter.setSelectedCategory(cat);

                List<String> subCats = CategoryManager.getSubCategories(this, cat);
                if (subCats == null || subCats.isEmpty()) {
                    Toast.makeText(this, "该分类暂无二级分类", Toast.LENGTH_SHORT).show();
                    return false;
                }

                AlertDialog.Builder subBuilder = new AlertDialog.Builder(this);
                View subCatView = LayoutInflater.from(this).inflate(R.layout.dialog_select_sub_category, null);
                subBuilder.setView(subCatView);
                AlertDialog subCatDialog = subBuilder.create();
                if (subCatDialog.getWindow() != null) {
                    subCatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                TextView tvTitle = subCatView.findViewById(R.id.tv_title);
                tvTitle.setText(cat + " - 二级分类");

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

                    int bgDefault = ContextCompat.getColor(this, R.color.cat_unselected_bg);
                    int bgChecked = ContextCompat.getColor(this, R.color.app_blue);
                    int textDefault = ContextCompat.getColor(this, R.color.text_primary);
                    int textChecked = ContextCompat.getColor(this, R.color.cat_selected_text);
                    int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
                    ColorStateList bgStateList = new ColorStateList(states, new int[]{bgChecked, bgDefault});
                    ColorStateList textStateList = new ColorStateList(states, new int[]{textChecked, textDefault});

                    for (String subCatName : subCats) {
                        Chip chip = new Chip(this);
                        chip.setText(subCatName);
                        chip.setCheckable(true);
                        chip.setClickable(true);
                        chip.setChipBackgroundColor(bgStateList);
                        chip.setTextColor(textStateList);
                        chip.setChipStrokeWidth(0);
                        chip.setCheckedIconVisible(false);

                        if (subCatName.equals(selectedSub[0])) {
                            chip.setChecked(true);
                        }

                        chip.setOnClickListener(v -> {
                            selectedSub[0] = subCatName;
                            if (listener != null) {
                                listener.onSelected(selectedCat[0], selectedSub[0]);
                            }
                            subCatDialog.dismiss();
                            dialog.dismiss();
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
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private interface OnCategorySelectedListener {
        void onSelected(String primaryCategory, String subCategory);
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(RecurringRule rule) {
        new AlertDialog.Builder(this)
                .setTitle("删除周期记账")
                .setMessage("确定要删除该周期记账规则吗？")
                .setPositiveButton("仅删除规则", (d, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase.getDatabase(this).recurringRuleDao().delete(rule);
                        runOnUiThread(() -> Toast.makeText(this, "规则已删除", Toast.LENGTH_SHORT).show());
                    });
                })
                .setNegativeButton("删除规则及未来账单", (d, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase db = AppDatabase.getDatabase(this);
                        db.transactionDao().deleteUnsettledTransactionsByRule(rule.id);
                        db.recurringRuleDao().delete(rule);
                        runOnUiThread(() -> Toast.makeText(this, "规则及未生效账单已删除", Toast.LENGTH_SHORT).show());
                    });
                })
                .setNeutralButton("取消", null)
                .show();
    }

    /**
     * 显示日期选择器
     */
    private void showDatePicker(TextView textView, long[] dateMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis[0]);

        try {
            View dateView = LayoutInflater.from(this).inflate(R.layout.dialog_bottom_date_picker, null);
            AlertDialog dateDialog = new AlertDialog.Builder(this).setView(dateView).create();
            if (dateDialog.getWindow() != null) {
                dateDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            NumberPicker npYear = dateView.findViewById(R.id.np_year);
            NumberPicker npMonth = dateView.findViewById(R.id.np_month);
            NumberPicker npDay = dateView.findViewById(R.id.np_day);

            int currentYear = calendar.get(Calendar.YEAR);
            int currentMonth = calendar.get(Calendar.MONTH) + 1;
            int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

            npYear.setMinValue(2000);
            npYear.setMaxValue(2050);
            npYear.setValue(currentYear);

            npMonth.setMinValue(1);
            npMonth.setMaxValue(12);
            npMonth.setValue(currentMonth);

            Calendar tempCal = Calendar.getInstance();
            tempCal.set(currentYear, currentMonth - 1, 1);
            npDay.setMinValue(1);
            npDay.setMaxValue(tempCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            npDay.setValue(currentDay);

            npMonth.setOnValueChangedListener((picker, oldVal, newVal) -> {
                tempCal.set(npYear.getValue(), newVal - 1, 1);
                npDay.setMaxValue(tempCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            });

            dateView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dateDialog.dismiss());
            dateView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
                calendar.set(npYear.getValue(), npMonth.getValue() - 1, npDay.getValue(), 0, 0, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                dateMillis[0] = calendar.getTimeInMillis();
                textView.setText(formatDate(dateMillis[0]));
                dateDialog.dismiss();
            });

            dateDialog.show();
        } catch (Exception e) {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth, 0, 0, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                dateMillis[0] = calendar.getTimeInMillis();
                textView.setText(formatDate(dateMillis[0]));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        }
    }

    /**
     * 显示时间选择器
     */
    private void showTimePicker(TextView textView) {
        String[] parts = textView.getText().toString().split(":");
        int initHour = 0;
        int initMinute = 0;
        if (parts.length == 2) {
            try {
                initHour = Integer.parseInt(parts[0]);
                initMinute = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}
        }

        try {
            View timeView = LayoutInflater.from(this).inflate(R.layout.dialog_bottom_time_picker, null);
            AlertDialog timeDialog = new AlertDialog.Builder(this).setView(timeView).create();
            if (timeDialog.getWindow() != null) {
                timeDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            NumberPicker npHour = timeView.findViewById(R.id.np_hour);
            NumberPicker npMinute = timeView.findViewById(R.id.np_minute);

            npHour.setMinValue(0);
            npHour.setMaxValue(23);
            npHour.setValue(initHour);

            npMinute.setMinValue(0);
            npMinute.setMaxValue(59);
            npMinute.setValue(initMinute);

            timeView.findViewById(R.id.btn_cancel).setOnClickListener(v -> timeDialog.dismiss());
            timeView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
                String timeStr = String.format(Locale.getDefault(), "%02d:%02d", npHour.getValue(), npMinute.getValue());
                textView.setText(timeStr);
                timeDialog.dismiss();
            });

            timeDialog.show();
        } catch (Exception e) {
            new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
                String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                textView.setText(time);
            }, initHour, initMinute, true).show();
        }
    }

    private String formatDate(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(millis);
    }

    private String getDefaultCurrency() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        return prefs.getString("default_currency", "¥");
    }

    private String getCurrencyName(String symbol) {
        for (int i = 0; i < CURRENCY_SYMBOLS.length; i++) {
            if (CURRENCY_SYMBOLS[i].equals(symbol)) {
                return CURRENCY_NAMES[i];
            }
        }
        return "人民币";
    }

    private int getPeriodUnitIndex(String unit) {
        if (unit == null) return 2;
        switch (unit.toUpperCase()) {
            case "DAY": return 0;
            case "WEEK": return 1;
            case "MONTH": return 2;
            case "YEAR": return 3;
            default: return 2;
        }
    }

    private String getPeriodUnitFromIndex(int index) {
        switch (index) {
            case 0: return "DAY";
            case 1: return "WEEK";
            case 2: return "MONTH";
            case 3: return "YEAR";
            default: return "MONTH";
        }
    }
}