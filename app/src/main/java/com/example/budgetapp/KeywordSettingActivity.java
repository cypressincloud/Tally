package com.example.budgetapp;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.util.KeywordManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeywordSettingActivity extends AppCompatActivity {

    private View rootLayout; // 引用根布局
    private Spinner spinnerAppSelector;
    private RadioGroup rgKeywordType;
    private EditText etKeywordInput;
    private Button btnAddKeyword;
    private ListView listKeywords;
    private TextView tvCurrentAppHint;

    private KeywordListAdapter listAdapter;
    private List<KeywordDisplayItem> displayList;

    private List<AppItem> appItems;
    private String currentSelectedPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyword_setting);

        // 1. 获取视图引用
        rootLayout = findViewById(R.id.root_layout);
        spinnerAppSelector = findViewById(R.id.spinner_app_selector);
        rgKeywordType = findViewById(R.id.rg_keyword_type);
        etKeywordInput = findViewById(R.id.et_keyword_input);
        btnAddKeyword = findViewById(R.id.btn_add_keyword);
        listKeywords = findViewById(R.id.list_keywords);
        tvCurrentAppHint = findViewById(R.id.tv_current_app_hint);

        // 2. 【核心修改】设置沉浸式状态栏 Padding
        // 记录原始 Padding (即 XML 中写的 16dp)
        final int originalPaddingTop = rootLayout.getPaddingTop();
        final int originalPaddingBottom = rootLayout.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    originalPaddingTop + insets.top, // 顶部增加状态栏高度
                    v.getPaddingRight(),
                    originalPaddingBottom + insets.bottom // 底部增加导航栏高度(可选)
            );
            return WindowInsetsCompat.CONSUMED;
        });

        // 3. 初始化默认数据
        KeywordManager.initDefaults(this);

        setupAppSpinner();

        displayList = new ArrayList<>();
        listAdapter = new KeywordListAdapter(this, displayList);
        listKeywords.setAdapter(listAdapter);

        setupListeners();
    }

    private void setupAppSpinner() {
        appItems = new ArrayList<>();
        Map<String, String> supportedApps = KeywordManager.getSupportedApps();
        for (Map.Entry<String, String> entry : supportedApps.entrySet()) {
            appItems.add(new AppItem(entry.getKey(), entry.getValue()));
        }

        ArrayAdapter<AppItem> spinnerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                appItems
        );

        // 【修改这里】使用自定义的布局文件
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);

        spinnerAppSelector.setAdapter(spinnerAdapter);
    }

    private void setupListeners() {
        // 下拉选择应用
        spinnerAppSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AppItem selected = appItems.get(position);
                currentSelectedPackage = selected.packageName;
                tvCurrentAppHint.setText("正在编辑 [" + selected.appName + "] 的关键字");
                refreshKeywordList();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 添加按钮
        btnAddKeyword.setOnClickListener(v -> {
            String keyword = etKeywordInput.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入关键字", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentSelectedPackage == null) return;

            int type = (rgKeywordType.getCheckedRadioButtonId() == R.id.rb_type_income)
                    ? KeywordManager.TYPE_INCOME : KeywordManager.TYPE_EXPENSE;

            KeywordManager.addKeyword(this, currentSelectedPackage, type, keyword);

            etKeywordInput.setText("");
            refreshKeywordList();
            Toast.makeText(this, "已添加: " + keyword, Toast.LENGTH_SHORT).show();
        });

        // 长按删除
        listKeywords.setOnItemLongClickListener((parent, view, position, id) -> {
            KeywordDisplayItem item = displayList.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("删除关键字")
                    .setMessage("确定要删除 “" + item.keyword + "” 吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        KeywordManager.removeKeyword(this, currentSelectedPackage, item.type, item.keyword);
                        refreshKeywordList();
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    private void refreshKeywordList() {
        if (currentSelectedPackage == null) return;

        displayList.clear();
        // 加载支出关键字
        Set<String> expenses = KeywordManager.getKeywords(this, currentSelectedPackage, KeywordManager.TYPE_EXPENSE);
        for (String k : expenses) displayList.add(new KeywordDisplayItem(k, KeywordManager.TYPE_EXPENSE));

        // 加载收入关键字
        Set<String> incomes = KeywordManager.getKeywords(this, currentSelectedPackage, KeywordManager.TYPE_INCOME);
        for (String k : incomes) displayList.add(new KeywordDisplayItem(k, KeywordManager.TYPE_INCOME));

        listAdapter.notifyDataSetChanged();
    }

    // --- 内部辅助类 ---

    // 1. 下拉菜单项
    private static class AppItem {
        String packageName;
        String appName;
        AppItem(String pkg, String name) { this.packageName = pkg; this.appName = name; }
        @Override public String toString() { return appName; }
    }

    // 2. 列表显示项
    private static class KeywordDisplayItem {
        String keyword;
        int type;
        KeywordDisplayItem(String k, int t) { this.keyword = k; this.type = t; }
    }

    // 3. 自定义列表适配器 (为了显示颜色区分)
    private class KeywordListAdapter extends ArrayAdapter<KeywordDisplayItem> {
        public KeywordListAdapter(@NonNull Context context, List<KeywordDisplayItem> list) {
            super(context, 0, list);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            KeywordDisplayItem item = getItem(position);
            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);

            if (item != null) {
                text1.setText(item.keyword);
                if (item.type == KeywordManager.TYPE_EXPENSE) {
                    text2.setText("支出触发词");
                    text2.setTextColor(getContext().getColor(R.color.expense_green));
                } else {
                    text2.setText("收入触发词");
                    text2.setTextColor(getContext().getColor(R.color.income_red));
                }
            }
            return convertView;
        }
    }
}