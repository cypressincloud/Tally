package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.budgetapp.BackupData;
import com.example.budgetapp.BackupManager;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.ExternalImportHelper;
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private FinanceViewModel financeViewModel;
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<AssetAccount> allAssets = new ArrayList<>();
    private SwitchCompat switchMinimalist;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            uri -> {
                if (uri != null) {
                    try {
                        BackupManager.exportToZip(this, uri, allTransactions, allAssets);
                        Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        BackupData data = BackupManager.importFromZip(this, uri);
                        int recordCount = 0;
                        int assetCount = 0;

                        if (data.records != null && !data.records.isEmpty()) {
                            for (Transaction t : data.records) {
                                Transaction newT = t;
                                newT.id = 0;
                                financeViewModel.addTransaction(newT);
                            }
                            recordCount = data.records.size();
                        }

                        if (data.assets != null && !data.assets.isEmpty()) {
                            for (AssetAccount a : data.assets) {
                                AssetAccount newA = a;
                                newA.id = 0;
                                financeViewModel.addAsset(newA);
                            }
                            assetCount = data.assets.size();
                        }
                        Toast.makeText(this, String.format("成功导入: %d条账单, %d个资产", recordCount, assetCount), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String[]> importExternalJsonLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        inputStream.close();

                        String jsonContent = sb.toString();
                        List<Transaction> externalTransactions = ExternalImportHelper.parseExternalData(jsonContent);

                        if (!externalTransactions.isEmpty()) {
                            for (Transaction t : externalTransactions) {
                                financeViewModel.addTransaction(t);
                            }
                            Toast.makeText(this, "成功导入 " + externalTransactions.size() + " 条外部数据", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "未解析到有效数据，请检查文件格式", Toast.LENGTH_LONG).show();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "外部导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);

        View rootView = findViewById(R.id.settings_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
             androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
             v.setPadding(originalPaddingLeft + insets.left, originalPaddingTop + insets.top, originalPaddingRight + insets.right, originalPaddingBottom + insets.bottom);
             return WindowInsetsCompat.CONSUMED;
        });

        financeViewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        financeViewModel.getAllTransactions().observe(this, list -> allTransactions = list);
        financeViewModel.getAllAssets().observe(this, list -> allAssets = list);

        findViewById(R.id.btn_category_setting).setOnClickListener(v -> startActivity(new Intent(this, CategorySettingsActivity.class)));
        findViewById(R.id.btn_backup_restore).setOnClickListener(v -> showBackupOptions());
        findViewById(R.id.btn_auto_asset).setOnClickListener(v -> startActivity(new Intent(this, AutoAssetActivity.class)));
        findViewById(R.id.btn_toggle_night_mode).setOnClickListener(v -> showThemeSettingDialog());
        findViewById(R.id.btn_assistant_setting).setOnClickListener(v -> startActivity(new Intent(this, AssistantManagerActivity.class)));
        findViewById(R.id.btn_overtime_setting).setOnClickListener(v -> showSetOvertimeRateDialog());

        // 修改：点击按钮直接跳转到 DonateActivity
        findViewById(R.id.btn_donate).setOnClickListener(v -> {
            startActivity(new Intent(this, DonateActivity.class));
        });

        switchMinimalist = findViewById(R.id.switch_minimalist);
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isMinimalist = prefs.getBoolean("minimalist_mode", false);
        switchMinimalist.setChecked(isMinimalist);
        switchMinimalist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("minimalist_mode", isChecked).apply();
            Toast.makeText(this, isChecked ? "极简模式已开启" : "极简模式已关闭", Toast.LENGTH_SHORT).show();
        });
    }

    private void showBackupOptions() {
        String[] options = {"导出数据 (Zip)", "导入数据 (Zip)", "导入外部账单 (JSON)"};
        new AlertDialog.Builder(this)
                .setTitle("数据备份与恢复")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        String timeStr = sdf.format(new Date()).replace(":", "-");
                        String fileName = "Tally " + timeStr + ".zip";
                        exportLauncher.launch(fileName);
                    } else if (which == 1) {
                        importLauncher.launch(new String[]{"application/zip"});
                    } else if (which == 2) {
                        importExternalJsonLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
                    }
                })
                .show();
    }

    private void showThemeSettingDialog() {
        String[] themes = {"跟随系统", "日间模式", "夜间模式"};
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        
        int checkedItem = 0;
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            checkedItem = 1;
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            checkedItem = 2;
        }

        new AlertDialog.Builder(this)
                .setTitle("主题设置")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    int selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    if (which == 1) {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                    } else if (which == 2) {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                    }

                    prefs.edit().putInt("theme_mode", selectedMode).apply();
                    AppCompatDelegate.setDefaultNightMode(selectedMode);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSetOvertimeRateDialog() {
         AlertDialog.Builder builder = new AlertDialog.Builder(this);
         View view = LayoutInflater.from(this).inflate(R.layout.dialog_set_overtime_rate, null);
         builder.setView(view);

         EditText etBaseSalary = view.findViewById(R.id.et_base_salary);
         EditText etWeekday = view.findViewById(R.id.et_weekday_rate);
         EditText etHoliday = view.findViewById(R.id.et_holiday_rate);

         AssistantConfig config = new AssistantConfig(this);
         float currentBase = config.getMonthlyBaseSalary();
         float currentWeekday = config.getWeekdayOvertimeRate();
         float currentHoliday = config.getHolidayOvertimeRate();

         if (currentBase > 0) etBaseSalary.setText(String.valueOf(currentBase));
         if (currentWeekday > 0) etWeekday.setText(String.valueOf(currentWeekday));
         if (currentHoliday > 0) etHoliday.setText(String.valueOf(currentHoliday));

         builder.setTitle("设置加班薪资标准")
                 .setPositiveButton("保存", (dialog, which) -> {
                     String bStr = etBaseSalary.getText().toString();
                     String wStr = etWeekday.getText().toString();
                     String hStr = etHoliday.getText().toString();
                     try {
                         float bRate = bStr.isEmpty() ? 0f : Float.parseFloat(bStr);
                         float wRate = wStr.isEmpty() ? 0f : Float.parseFloat(wStr);
                         float hRate = hStr.isEmpty() ? 0f : Float.parseFloat(hStr);
                         config.setMonthlyBaseSalary(bRate);
                         config.setWeekdayOvertimeRate(wRate);
                         config.setHolidayOvertimeRate(hRate);
                         Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
                     } catch (NumberFormatException e) {
                         Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show();
                     }
                 })
                 .setNegativeButton("取消", null)
                 .show();
    }
}