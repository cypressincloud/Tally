package com.example.budgetapp.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.budgetapp.R;

public class ThemeSettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private View cardCustomBg;
    private Button btnPickDayBg, btnClearDayBg;
    private Button btnPickNightBg, btnClearNightBg;

    // 1 代表正在选日间，2 代表正在选夜间
    private int pickingMode = 0;

    private final ActivityResultLauncher<String[]> pickCustomBgLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        
                        if (pickingMode == 1) {
                            prefs.edit().putString("custom_bg_day_uri", uri.toString()).apply();
                            Toast.makeText(this, "日间背景已保存", Toast.LENGTH_SHORT).show();
                        } else if (pickingMode == 2) {
                            prefs.edit().putString("custom_bg_night_uri", uri.toString()).apply();
                            Toast.makeText(this, "夜间背景已保存", Toast.LENGTH_SHORT).show();
                        }
                        updateCustomBgButtons();
                        applyThemeInstantly(); // 刷新一下全局状态
                    } catch (SecurityException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "获取图片权限失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🌟 【新增 1】：允许内容延伸到系统状态栏和导航栏（小白条）区域
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_theme_settings);

        // 🌟 【新增 2】：处理沉浸式带来的遮挡，动态增加 Padding
        View rootView = findViewById(R.id.theme_settings_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    originalPaddingLeft + insets.left,
                    originalPaddingTop + insets.top,
                    originalPaddingRight + insets.right,
                    originalPaddingBottom + insets.bottom
            );
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        // ================= 下面是你原本的逻辑 =================
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        cardCustomBg = findViewById(R.id.card_custom_bg);
        btnPickDayBg = findViewById(R.id.btn_pick_day_bg);
        btnClearDayBg = findViewById(R.id.btn_clear_day_bg);
        btnPickNightBg = findViewById(R.id.btn_pick_night_bg);
        btnClearNightBg = findViewById(R.id.btn_clear_night_bg);

        Spinner spThemeMode = findViewById(R.id.sp_theme_mode);
        String[] themeOptions = {"跟随系统", "日间模式", "夜间模式", "自定义主题"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dropdown, themeOptions);
        spThemeMode.setAdapter(adapter);

        // 初始化当前选中的模式
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int selectionIndex = 0;
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) selectionIndex = 1;
        else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) selectionIndex = 2;
        else if (currentMode == 3) selectionIndex = 3;

        spThemeMode.setSelection(selectionIndex, false);

        spThemeMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedMode;
                if (position == 1) selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                else if (position == 2) selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                else if (position == 3) selectedMode = 3;
                else selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

                prefs.edit().putInt("theme_mode", selectedMode).apply();
                updateCustomBgVisibility(selectedMode);
                applyThemeInstantly();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateCustomBgVisibility(currentMode);
        updateCustomBgButtons();

        // 按钮事件
        btnPickDayBg.setOnClickListener(v -> { pickingMode = 1; pickCustomBgLauncher.launch(new String[]{"image/*"}); });
        btnPickNightBg.setOnClickListener(v -> { pickingMode = 2; pickCustomBgLauncher.launch(new String[]{"image/*"}); });

        btnClearDayBg.setOnClickListener(v -> {
            prefs.edit().remove("custom_bg_day_uri").apply();
            updateCustomBgButtons();
            applyThemeInstantly();
        });

        btnClearNightBg.setOnClickListener(v -> {
            prefs.edit().remove("custom_bg_night_uri").apply();
            updateCustomBgButtons();
            applyThemeInstantly();
        });
    }

    private void updateCustomBgVisibility(int mode) {
        cardCustomBg.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
    }

    private void updateCustomBgButtons() {
        boolean hasDayBg = prefs.contains("custom_bg_day_uri");
        boolean hasNightBg = prefs.contains("custom_bg_night_uri");

        btnPickDayBg.setText(hasDayBg ? "重新选择日间背景" : "选择日间背景");
        btnClearDayBg.setVisibility(hasDayBg ? View.VISIBLE : View.GONE);

        btnPickNightBg.setText(hasNightBg ? "重新选择夜间背景" : "选择夜间背景");
        btnClearNightBg.setVisibility(hasNightBg ? View.VISIBLE : View.GONE);
    }

    private void applyThemeInstantly() {
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int delegateMode = themeMode;

        if (themeMode == 3) {
            String dayUri = prefs.getString("custom_bg_day_uri", null);
            String nightUri = prefs.getString("custom_bg_night_uri", null);
            
            if (dayUri != null && nightUri == null) {
                delegateMode = AppCompatDelegate.MODE_NIGHT_NO; // 只有日间，强行锁死日间模式
            } else if (nightUri != null && dayUri == null) {
                delegateMode = AppCompatDelegate.MODE_NIGHT_YES; // 只有夜间，强行锁死夜间模式
            } else {
                delegateMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; // 都设置了或都没设置，跟随系统
            }
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode);
    }
}