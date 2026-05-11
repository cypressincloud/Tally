package com.example.budgetapp.ui;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;

public class DefaultPageActivity extends AppCompatActivity {

    private RadioGroup radioGroupDefaultPage;
    private SwitchCompat switchDetailsQuickButton;
    private SharedPreferences prefs;
    
    public static final String PREF_NAME = "app_prefs";
    public static final String KEY_DEFAULT_PAGE = "default_page";
    public static final String KEY_DETAILS_QUICK_BUTTON = "details_quick_button";
    public static final int PAGE_RECORD = 0;
    public static final int PAGE_DETAILS = 1;
    public static final int PAGE_BUDGET = 2;
    public static final int PAGE_ASSETS = 3;
    public static final int PAGE_STATS = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 沉浸式设置
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_default_page);

        // 适配内边距
        android.view.View rootLayout = findViewById(R.id.root_layout);
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
    }

    private void initViews() {
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        radioGroupDefaultPage = findViewById(R.id.radioGroupDefaultPage);
        switchDetailsQuickButton = findViewById(R.id.switchDetailsQuickButton);

        // 读取当前设置
        int currentPage = prefs.getInt(KEY_DEFAULT_PAGE, PAGE_RECORD);
        boolean showQuickButton = prefs.getBoolean(KEY_DETAILS_QUICK_BUTTON, true);
        
        // 设置开关状态
        switchDetailsQuickButton.setChecked(showQuickButton);
        
        // 监听开关变化
        switchDetailsQuickButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DETAILS_QUICK_BUTTON, isChecked).apply();
            String status = isChecked ? "显示" : "隐藏";
            Toast.makeText(this, "明细模块快捷按钮已设置为：" + status, Toast.LENGTH_SHORT).show();
        });
        
        // 设置选中状态
        switch (currentPage) {
            case PAGE_RECORD:
                ((RadioButton) findViewById(R.id.radioRecord)).setChecked(true);
                break;
            case PAGE_DETAILS:
                ((RadioButton) findViewById(R.id.radioDetails)).setChecked(true);
                break;
            case PAGE_BUDGET:
                ((RadioButton) findViewById(R.id.radioBudget)).setChecked(true);
                break;
            case PAGE_ASSETS:
                ((RadioButton) findViewById(R.id.radioAssets)).setChecked(true);
                break;
            case PAGE_STATS:
                ((RadioButton) findViewById(R.id.radioStats)).setChecked(true);
                break;
        }

        // 监听选择变化
        radioGroupDefaultPage.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedPage = PAGE_RECORD;
            String pageName = "记账";

            if (checkedId == R.id.radioRecord) {
                selectedPage = PAGE_RECORD;
                pageName = "记账";
            } else if (checkedId == R.id.radioDetails) {
                selectedPage = PAGE_DETAILS;
                pageName = "明细";
            } else if (checkedId == R.id.radioBudget) {
                selectedPage = PAGE_BUDGET;
                pageName = "预算";
            } else if (checkedId == R.id.radioAssets) {
                selectedPage = PAGE_ASSETS;
                pageName = "资产";
            } else if (checkedId == R.id.radioStats) {
                selectedPage = PAGE_STATS;
                pageName = "统计";
            }

            // 保存设置
            prefs.edit().putInt(KEY_DEFAULT_PAGE, selectedPage).apply();
            Toast.makeText(this, "已设置默认页面为：" + pageName, Toast.LENGTH_SHORT).show();
        });
    }
}
