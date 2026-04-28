package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.util.AiCategoryRuleManager;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.CategoryRule;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class AiCategoryRuleActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private Button btnAddRule;
    private RuleListAdapter adapter;
    private List<CategoryRule> ruleList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 沉浸式状态栏设置
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        
        setContentView(R.layout.activity_ai_category_rule);

        // 适配内边距
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            final int originalPaddingTop = rootLayout.getPaddingTop();
            final int originalPaddingBottom = rootLayout.getPaddingBottom();
            
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        v.getPaddingLeft(),
                        originalPaddingTop + insets.top,
                        v.getPaddingRight(),
                        originalPaddingBottom + insets.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        initViews();
        loadRules();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.rv_rules);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        btnAddRule = findViewById(R.id.btn_add_rule);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RuleListAdapter();
        recyclerView.setAdapter(adapter);

        btnAddRule.setOnClickListener(v -> showAddRuleDialog());
    }

    private void loadRules() {
        ruleList = AiCategoryRuleManager.getRules(this);
        refreshList();
    }

    private void refreshList() {
        adapter.notifyDataSetChanged();
        
        // 显示/隐藏空状态
        if (ruleList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showAddRuleDialog() {
        showRuleDialog(null, -1, "添加规则");
    }

    private void showEditRuleDialog(CategoryRule rule, int position) {
        showRuleDialog(rule, position, "编辑规则");
    }

    private void showRuleDialog(CategoryRule existingRule, int position, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_rule, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        EditText etKeyword = view.findViewById(R.id.et_keyword);
        android.widget.RadioGroup rgType = view.findViewById(R.id.rg_type);
        android.widget.RadioButton rbExpense = view.findViewById(R.id.rb_expense);
        android.widget.RadioButton rbIncome = view.findViewById(R.id.rb_income);
        RecyclerView rvCategory = view.findViewById(R.id.rv_category);
        TextView tvSelectedCategory = view.findViewById(R.id.tv_selected_category);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        tvTitle.setText(title);
        tvSelectedCategory.setVisibility(View.VISIBLE);

        // 如果是编辑模式，预填充数据
        if (existingRule != null) {
            etKeyword.setText(existingRule.getKeyword());
        }

        // 获取分类列表
        List<String> expenseCategories = CategoryManager.getExpenseCategories(this);
        List<String> incomeCategories = CategoryManager.getIncomeCategories(this);

        // 默认选择支出分类
        final boolean[] isExpense = {existingRule == null || existingRule.getType() == 0};
        final String[] selectedCategory = {
            existingRule != null ? existingRule.getCategory() : 
            (isExpense[0] ? 
                (expenseCategories.isEmpty() ? "" : expenseCategories.get(0)) :
                (incomeCategories.isEmpty() ? "" : incomeCategories.get(0)))
        };
        final String[] selectedSubCategory = {
            existingRule != null ? existingRule.getSubCategory() : ""
        };

        // 设置RadioButton初始状态
        if (isExpense[0]) {
            rbExpense.setChecked(true);
        } else {
            rbIncome.setChecked(true);
        }

        // 设置布局管理器
        boolean isDetailed = CategoryManager.isDetailedCategoryEnabled(this);
        if (isDetailed) {
            com.google.android.flexbox.FlexboxLayoutManager flexboxLayoutManager = 
                new com.google.android.flexbox.FlexboxLayoutManager(this);
            flexboxLayoutManager.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
            flexboxLayoutManager.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
            flexboxLayoutManager.setJustifyContent(com.google.android.flexbox.JustifyContent.FLEX_START);
            rvCategory.setLayoutManager(flexboxLayoutManager);
        } else {
            rvCategory.setLayoutManager(new GridLayoutManager(this, 5));
        }

        // 初始化CategoryAdapter
        List<String> currentCategories = isExpense[0] ? expenseCategories : incomeCategories;
        CategoryAdapter categoryAdapter = new CategoryAdapter(this, currentCategories, selectedCategory[0], category -> {
            selectedCategory[0] = category;
            selectedSubCategory[0] = "";
            updateSelectedCategoryText(tvSelectedCategory, selectedCategory[0], selectedSubCategory[0], isExpense[0]);
        });

        // 设置长按监听器（选择子分类）
        categoryAdapter.setOnCategoryLongClickListener(category -> {
            if (CategoryManager.isSubCategoryEnabled(this)) {
                // 如果长按的不是当前选中的分类，先选中它
                if (!category.equals(selectedCategory[0])) {
                    categoryAdapter.setSelectedCategory(category);
                    selectedCategory[0] = category;
                    selectedSubCategory[0] = "";
                    updateSelectedCategoryText(tvSelectedCategory, selectedCategory[0], selectedSubCategory[0], isExpense[0]);
                }

                // 显示子分类选择对话框
                showSubCategoryDialog(category, selectedSubCategory, () -> {
                    updateSelectedCategoryText(tvSelectedCategory, selectedCategory[0], selectedSubCategory[0], isExpense[0]);
                });
                return true;
            }
            return false;
        });

        rvCategory.setAdapter(categoryAdapter);

        // RadioGroup切换监听
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            // 触发震动反馈
            group.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            
            isExpense[0] = (checkedId == R.id.rb_expense);
            
            // 切换分类列表
            List<String> newCategories = isExpense[0] ? expenseCategories : incomeCategories;
            categoryAdapter.updateData(newCategories);
            
            // 重置选中的分类和子分类
            if (!newCategories.isEmpty()) {
                selectedCategory[0] = newCategories.get(0);
                categoryAdapter.setSelectedCategory(selectedCategory[0]);
            } else {
                selectedCategory[0] = "";
            }
            selectedSubCategory[0] = "";
            
            // 更新已选择提示
            updateSelectedCategoryText(tvSelectedCategory, selectedCategory[0], selectedSubCategory[0], isExpense[0]);
        });

        // 更新已选择提示
        updateSelectedCategoryText(tvSelectedCategory, selectedCategory[0], selectedSubCategory[0], isExpense[0]);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnConfirm.setOnClickListener(v -> {
            String keyword = etKeyword.getText().toString().trim();
            
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入关键字", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedCategory[0] == null || selectedCategory[0].isEmpty()) {
                Toast.makeText(this, "请选择分类", Toast.LENGTH_SHORT).show();
                return;
            }

            int type = isExpense[0] ? 0 : 1;
            CategoryRule rule = new CategoryRule(keyword, selectedCategory[0], selectedSubCategory[0], type);
            
            if (position >= 0) {
                // 编辑模式
                AiCategoryRuleManager.updateRule(this, position, rule);
                Toast.makeText(this, "规则已更新", Toast.LENGTH_SHORT).show();
            } else {
                // 添加模式
                AiCategoryRuleManager.addRule(this, rule);
                Toast.makeText(this, "规则已添加", Toast.LENGTH_SHORT).show();
            }
            
            loadRules();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateSelectedCategoryText(TextView tvSelectedCategory, String category, String subCategory, boolean isExpense) {
        String typeStr = isExpense ? "支出" : "收入";
        if (subCategory != null && !subCategory.isEmpty()) {
            tvSelectedCategory.setText("已选择：" + typeStr + " - " + category + " > " + subCategory);
        } else {
            tvSelectedCategory.setText("已选择：" + typeStr + " - " + category);
        }
    }

    private void showSubCategoryDialog(String category, String[] selectedSubCategory, Runnable onUpdate) {
        List<String> subCats = CategoryManager.getSubCategories(this, category);
        
        AlertDialog.Builder subBuilder = new AlertDialog.Builder(this);
        View subCatView = LayoutInflater.from(this).inflate(R.layout.dialog_select_sub_category, null);
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
            int bgDefault = ContextCompat.getColor(this, R.color.cat_unselected_bg);
            int bgChecked = ContextCompat.getColor(this, R.color.app_yellow);
            int textDefault = ContextCompat.getColor(this, R.color.text_primary);
            int textChecked = ContextCompat.getColor(this, R.color.cat_selected_text);

            int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] { } };
            ColorStateList bgStateList = new ColorStateList(states, new int[] { bgChecked, bgDefault });
            ColorStateList textStateList = new ColorStateList(states, new int[] { textChecked, textDefault });

            for (String subCatName : subCats) {
                Chip chip = new Chip(this);
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
                        selectedSubCategory[0] = "";
                        Toast.makeText(this, "已取消细分", Toast.LENGTH_SHORT).show();
                    } else {
                        selectedSubCategory[0] = subCatName;
                        Toast.makeText(this, "已选择: " + subCatName, Toast.LENGTH_SHORT).show();
                    }
                    if (onUpdate != null) {
                        onUpdate.run();
                    }
                    subCatDialog.dismiss();
                });
                cgSubCategories.addView(chip);
            }
        }
        
        btnCancel.setOnClickListener(v -> subCatDialog.dismiss());
        subCatDialog.show();
    }

    private void showDeleteConfirmDialog(CategoryRule rule, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvMessage = view.findViewById(R.id.tv_dialog_message);
        if (tvMessage != null) {
            tvMessage.setText("确定要删除规则 \"" + rule.getKeyword() + "\" 吗？");
        }

        View btnConfirm = view.findViewById(R.id.btn_dialog_confirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                AiCategoryRuleManager.deleteRule(this, position);
                loadRules();
                Toast.makeText(this, "规则已删除", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        View btnCancel = view.findViewById(R.id.btn_dialog_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    // RecyclerView适配器
    private class RuleListAdapter extends RecyclerView.Adapter<RuleListAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_rule, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CategoryRule rule = ruleList.get(position);
            holder.tvKeyword.setText(rule.getKeyword());
            
            String typeStr = rule.getType() == 0 ? "支出" : "收入";
            String categoryText;
            if (rule.getSubCategory() != null && !rule.getSubCategory().isEmpty()) {
                categoryText = typeStr + " - " + rule.getCategory() + " > " + rule.getSubCategory();
            } else {
                categoryText = typeStr + " - " + rule.getCategory();
            }
            holder.tvCategory.setText(categoryText);

            holder.ivEdit.setOnClickListener(v -> showEditRuleDialog(rule, position));
            
            holder.itemView.setOnLongClickListener(v -> {
                showDeleteConfirmDialog(rule, position);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return ruleList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvKeyword, tvCategory;
            View ivEdit;

            ViewHolder(View itemView) {
                super(itemView);
                tvKeyword = itemView.findViewById(R.id.tv_keyword);
                tvCategory = itemView.findViewById(R.id.tv_category);
                ivEdit = itemView.findViewById(R.id.iv_edit);
            }
        }
    }
}
