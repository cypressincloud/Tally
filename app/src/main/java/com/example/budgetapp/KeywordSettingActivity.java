package com.example.budgetapp;

import android.content.Context;
import android.graphics.Color;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat; // 新增
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.util.KeywordManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeywordSettingActivity extends AppCompatActivity {

    private View rootLayout;
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

        // 1. 沉浸式设置
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_keyword_setting);

        rootLayout = findViewById(R.id.root_layout);
        spinnerAppSelector = findViewById(R.id.spinner_app_selector);
        rgKeywordType = findViewById(R.id.rg_keyword_type);
        etKeywordInput = findViewById(R.id.et_keyword_input);
        btnAddKeyword = findViewById(R.id.btn_add_keyword);
        listKeywords = findViewById(R.id.list_keywords);
        tvCurrentAppHint = findViewById(R.id.tv_current_app_hint);

        // 2. 适配内边距
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
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerAppSelector.setAdapter(spinnerAdapter);
    }

    private void setupListeners() {
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
        Set<String> expenses = KeywordManager.getKeywords(this, currentSelectedPackage, KeywordManager.TYPE_EXPENSE);
        for (String k : expenses) displayList.add(new KeywordDisplayItem(k, KeywordManager.TYPE_EXPENSE));

        Set<String> incomes = KeywordManager.getKeywords(this, currentSelectedPackage, KeywordManager.TYPE_INCOME);
        for (String k : incomes) displayList.add(new KeywordDisplayItem(k, KeywordManager.TYPE_INCOME));

        listAdapter.notifyDataSetChanged();
    }

    private static class AppItem {
        String packageName;
        String appName;
        AppItem(String pkg, String name) { this.packageName = pkg; this.appName = name; }
        @Override public String toString() { return appName; }
    }

    private static class KeywordDisplayItem {
        String keyword;
        int type;
        KeywordDisplayItem(String k, int t) { this.keyword = k; this.type = t; }
    }

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

            // 修改列表文字颜色以适配浅色卡片背景
            if (item != null) {
                text1.setText(item.keyword);
                text1.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary)); // 主文字色
                
                if (item.type == KeywordManager.TYPE_EXPENSE) {
                    text2.setText("支出触发词");
                    text2.setTextColor(ContextCompat.getColor(getContext(), R.color.expense_green));
                } else {
                    text2.setText("收入触发词");
                    text2.setTextColor(ContextCompat.getColor(getContext(), R.color.income_red));
                }
            }
            return convertView;
        }
    }
}