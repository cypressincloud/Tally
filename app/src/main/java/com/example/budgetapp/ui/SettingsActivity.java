package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
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
import com.example.budgetapp.util.CategoryManager;
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
import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    private FinanceViewModel financeViewModel;
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<AssetAccount> allAssets = new ArrayList<>();
    private SwitchCompat switchMinimalist;

    // --- 查重辅助方法 开始 ---
    private boolean isDuplicateAsset(AssetAccount a, List<AssetAccount> existingList) {
        if (existingList == null || existingList.isEmpty()) return false;
        for (AssetAccount ext : existingList) {
            // 资产名称和类型一致即认为重复
            if (Objects.equals(ext.name, a.name) && ext.type == a.type) {
                return true;
            }
        }
        return false;
    }

    private boolean isDuplicateTransaction(Transaction newTx, List<Transaction> existingList) {
        if (existingList == null || existingList.isEmpty()) return false;
        for (Transaction ext : existingList) {
            // 时间、类型、金额、分类、备注完全一致视为重复
            if (ext.date == newTx.date &&
                    ext.type == newTx.type &&
                    Math.abs(ext.amount - newTx.amount) < 0.01 &&
                    Objects.equals(ext.category, newTx.category) &&
                    Objects.equals(ext.subCategory, newTx.subCategory) &&
                    Objects.equals(ext.note, newTx.note) &&
                    Objects.equals(ext.remark, newTx.remark)) {
                return true;
            }
        }
        return false;
    }
    // --- 查重辅助方法 结束 ---

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

    private final ActivityResultLauncher<String> exportExcelLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri != null) {
                    try {
                        BackupManager.exportToExcel(this, uri, allTransactions, allAssets);
                        Toast.makeText(this, "Excel 导出成功", Toast.LENGTH_SHORT).show();
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

                        List<AssetAccount> currentAssets = new ArrayList<>(allAssets);
                        if (data.assets != null && !data.assets.isEmpty()) {
                            for (AssetAccount a : data.assets) {
                                if (!isDuplicateAsset(a, currentAssets)) {
                                    a.id = 0;
                                    financeViewModel.addAsset(a);
                                    currentAssets.add(a);
                                    assetCount++;
                                }
                            }
                        }

                        if (data.expenseCategories != null && !data.expenseCategories.isEmpty()) {
                            CategoryManager.saveExpenseCategories(this, data.expenseCategories);
                        }
                        if (data.incomeCategories != null && !data.incomeCategories.isEmpty()) {
                            CategoryManager.saveIncomeCategories(this, data.incomeCategories);
                        }

                        List<Transaction> currentTransactions = new ArrayList<>(allTransactions);
                        if (data.records != null && !data.records.isEmpty()) {
                            for (Transaction t : data.records) {
                                if (!isDuplicateTransaction(t, currentTransactions)) {
                                    t.id = 0;
                                    financeViewModel.addTransaction(t);
                                    currentTransactions.add(t);
                                    recordCount++;
                                }
                            }
                        }

                        Toast.makeText(this, String.format("成功导入: %d条账单, %d个资产 (已过滤重复)", recordCount, assetCount), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String[]> importExcelLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        BackupData data = BackupManager.importFromExcel(this, uri);
                        int recordCount = 0;
                        int assetCount = 0;

                        List<AssetAccount> currentAssets = new ArrayList<>(allAssets);
                        if (data.assets != null && !data.assets.isEmpty()) {
                            for (AssetAccount a : data.assets) {
                                if (!isDuplicateAsset(a, currentAssets)) {
                                    a.id = 0;
                                    financeViewModel.addAsset(a);
                                    currentAssets.add(a);
                                    assetCount++;
                                }
                            }
                        }

                        if (data.expenseCategories != null && !data.expenseCategories.isEmpty()) {
                            CategoryManager.saveExpenseCategories(this, data.expenseCategories);
                        }
                        if (data.incomeCategories != null && !data.incomeCategories.isEmpty()) {
                            CategoryManager.saveIncomeCategories(this, data.incomeCategories);
                        }

                        List<Transaction> currentTransactions = new ArrayList<>(allTransactions);
                        if (data.records != null && !data.records.isEmpty()) {
                            for (Transaction t : data.records) {
                                if (!isDuplicateTransaction(t, currentTransactions)) {
                                    t.id = 0;
                                    financeViewModel.addTransaction(t);
                                    currentTransactions.add(t);
                                    recordCount++;
                                }
                            }
                        }

                        Toast.makeText(this, String.format("Excel导入成功: %d条账单, %d个资产 (已过滤重复)", recordCount, assetCount), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Excel导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                            int recordCount = 0;
                            List<Transaction> currentTransactions = new ArrayList<>(allTransactions);
                            for (Transaction t : externalTransactions) {
                                if (!isDuplicateTransaction(t, currentTransactions)) {
                                    t.id = 0;
                                    financeViewModel.addTransaction(t);
                                    currentTransactions.add(t);
                                    recordCount++;
                                }
                            }
                            Toast.makeText(this, "成功导入 " + recordCount + " 条外部数据 (已过滤重复)", Toast.LENGTH_SHORT).show();
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
        findViewById(R.id.btn_default_record_display).setOnClickListener(v -> showDefaultRecordDisplayDialog());
        findViewById(R.id.btn_user_notice_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, UserNoticeActivity.class));
        });

        findViewById(R.id.btn_donate).setOnClickListener(v -> {
            startActivity(new Intent(this, DonateActivity.class));
        });


        View btnPhotoBackup = findViewById(R.id.btn_photo_backup_setting);
        if (btnPhotoBackup != null) {
            btnPhotoBackup.setOnClickListener(v -> startActivity(new Intent(this, PhotoBackupSettingsActivity.class)));
        }

        findViewById(R.id.btn_currency_setting).setOnClickListener(v -> {
            startActivity(new Intent(this, CurrencySettingsActivity.class));
        });

        findViewById(R.id.btn_auto_renewal_setting).setOnClickListener(v -> {
            startActivity(new Intent(this, AutoRenewalActivity.class));
        });

        // 极简模式逻辑
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        switchMinimalist = findViewById(R.id.switch_minimalist);
        boolean isMinimalist = prefs.getBoolean("minimalist_mode", false);
        switchMinimalist.setChecked(isMinimalist);
        switchMinimalist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("minimalist_mode", isChecked).apply();
            Toast.makeText(this, isChecked ? "极简模式已开启" : "极简模式已关闭", Toast.LENGTH_SHORT).show();
        });

        // 检查是否已激活
        boolean isActivated = prefs.getBoolean("is_premium_activated", false);
        if (!isActivated) {
            showActivationDialog(prefs);
        }
    }

    private void showSaveQrConfirmDialog(int resId, String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_save_qr, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View btnCancel = view.findViewById(R.id.btn_dialog_cancel);
        if (btnCancel == null) btnCancel = view.findViewById(R.id.btn_cancel);

        View btnConfirm = view.findViewById(R.id.btn_dialog_confirm);
        if (btnConfirm == null) btnConfirm = view.findViewById(R.id.btn_confirm);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                saveImageToGallery(resId, fileName);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private final ActivityResultLauncher<String[]> importWeChatLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        BackupData data = BackupManager.importFromWeChat(this, uri, allAssets);

                        int recordCount = 0;
                        int newAssetCount = 0;

                        List<AssetAccount> currentAssets = new ArrayList<>(allAssets);
                        if (data.assets != null && !data.assets.isEmpty()) {
                            for (AssetAccount a : data.assets) {
                                if (!isDuplicateAsset(a, currentAssets)) {
                                    a.id = 0;
                                    financeViewModel.addAsset(a);
                                    currentAssets.add(a);
                                    newAssetCount++;
                                }
                            }
                        }

                        if (data.expenseCategories != null && !data.expenseCategories.isEmpty()) {
                            CategoryManager.saveExpenseCategories(this, data.expenseCategories);
                        }
                        if (data.incomeCategories != null && !data.incomeCategories.isEmpty()) {
                            CategoryManager.saveIncomeCategories(this, data.incomeCategories);
                        }

                        List<Transaction> currentTransactions = new ArrayList<>(allTransactions);
                        if (data.records != null && !data.records.isEmpty()) {
                            for (Transaction t : data.records) {
                                if (!isDuplicateTransaction(t, currentTransactions)) {
                                    t.id = 0;
                                    financeViewModel.addTransaction(t);
                                    currentTransactions.add(t);
                                    recordCount++;
                                }
                            }
                        }

                        if (recordCount > 0) {
                            String msg = "成功导入 " + recordCount + " 条账单 (已过滤重复)";
                            if (newAssetCount > 0) {
                                msg += "\n自动创建了 " + newAssetCount + " 个新资产账户";
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "所有账单均已存在，或未找到有效数据", Toast.LENGTH_LONG).show();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "微信导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String[]> importAlipayLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        BackupData data = BackupManager.importFromAlipay(this, uri, allAssets);

                        int recordCount = 0;
                        int newAssetCount = 0;

                        List<AssetAccount> currentAssets = new ArrayList<>(allAssets);
                        if (data.assets != null && !data.assets.isEmpty()) {
                            for (AssetAccount a : data.assets) {
                                if (!isDuplicateAsset(a, currentAssets)) {
                                    a.id = 0;
                                    financeViewModel.addAsset(a);
                                    currentAssets.add(a);
                                    newAssetCount++;
                                }
                            }
                        }

                        if (data.expenseCategories != null && !data.expenseCategories.isEmpty()) {
                            CategoryManager.saveExpenseCategories(this, data.expenseCategories);
                        }
                        if (data.incomeCategories != null && !data.incomeCategories.isEmpty()) {
                            CategoryManager.saveIncomeCategories(this, data.incomeCategories);
                        }

                        List<Transaction> currentTransactions = new ArrayList<>(allTransactions);
                        if (data.records != null && !data.records.isEmpty()) {
                            for (Transaction t : data.records) {
                                if (!isDuplicateTransaction(t, currentTransactions)) {
                                    t.id = 0;
                                    financeViewModel.addTransaction(t);
                                    currentTransactions.add(t);
                                    recordCount++;
                                }
                            }
                        }

                        if (recordCount > 0) {
                            String msg = "成功从支付宝导入 " + recordCount + " 条账单 (已过滤重复)";
                            if (newAssetCount > 0) {
                                msg += "\n自动创建了 " + newAssetCount + " 个新资产账户";
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "所有账单均已存在，或未找到有效数据", Toast.LENGTH_LONG).show();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "支付宝导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    private void showActivationDialog(SharedPreferences prefs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_activate_premium, null);
        builder.setView(view);

        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.setOnCancelListener(dialogInterface -> {
            finish();
        });

        view.findViewById(R.id.iv_pay_alipay).setOnClickListener(v -> showSaveQrConfirmDialog(R.drawable.pay, "alipay_qr"));
        view.findViewById(R.id.iv_pay_wechat).setOnClickListener(v -> showSaveQrConfirmDialog(R.drawable.wechat, "wechat_qr"));

        view.findViewById(R.id.btn_user_notice).setOnClickListener(v -> {
            startActivity(new Intent(this, UserNoticeActivity.class));
        });

        view.findViewById(R.id.btn_already_paid).setOnClickListener(v -> {
            prefs.edit().putBoolean("is_premium_activated", true).apply();
            dialog.dismiss();
            Toast.makeText(this, "高级设置已激活，感谢支持", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void saveImageToGallery(int resId, String fileName) {
        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeResource(getResources(), resId);
            String savedUri = android.provider.MediaStore.Images.Media.insertImage(
                    getContentResolver(), bitmap, fileName, "Scan to pay");
            if (savedUri != null) {
                Toast.makeText(this, "二维码已保存到相册", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackupOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_backup_options, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        view.findViewById(R.id.tv_export).setOnClickListener(v -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String timeStr = sdf.format(new Date()).replace(":", "-");
            String fileName = "Tally " + timeStr + ".zip";
            exportLauncher.launch(fileName);
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_export_excel).setOnClickListener(v -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String timeStr = sdf.format(new Date());
            String fileName = "Tally_账单_" + timeStr + ".csv";
            exportExcelLauncher.launch(fileName);
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import).setOnClickListener(v -> {
            importLauncher.launch(new String[]{"application/zip"});
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import_excel).setOnClickListener(v -> {
            importExcelLauncher.launch(new String[]{
                    "text/csv",
                    "text/plain",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "*/*"
            });
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import_external).setOnClickListener(v -> {
            importExternalJsonLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import_wechat).setOnClickListener(v -> {
            importWeChatLauncher.launch(new String[]{
                    "text/csv",
                    "text/plain",
                    "application/vnd.ms-excel",
                    "*/*"
            });
            dialog.dismiss();
        });

        view.findViewById(R.id.tv_import_alipay).setOnClickListener(v -> {
            importAlipayLauncher.launch(new String[]{
                    "text/csv",
                    "text/plain",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "*/*"
            });
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_cancel_backup).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showThemeSettingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_theme_settings, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        android.widget.RadioGroup rgTheme = view.findViewById(R.id.rg_theme);
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            rgTheme.check(R.id.rb_day_mode);
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            rgTheme.check(R.id.rb_night_mode);
        } else {
            rgTheme.check(R.id.rb_follow_system);
        }

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (checkedId == R.id.rb_day_mode) {
                selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rb_night_mode) {
                selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
            }

            prefs.edit().putInt("theme_mode", selectedMode).apply();
            AppCompatDelegate.setDefaultNightMode(selectedMode);
            view.postDelayed(dialog::dismiss, 200);
        });

        view.findViewById(R.id.btn_cancel_theme).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showSetOvertimeRateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_set_overtime_rate, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

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

        view.findViewById(R.id.btn_save_overtime).setOnClickListener(v -> {
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
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_cancel_overtime).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showDefaultRecordDisplayDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_default_display_settings, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        android.widget.RadioGroup rgDisplay = view.findViewById(R.id.rg_default_display);
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int currentMode = prefs.getInt("default_record_mode", 0);

        switch (currentMode) {
            case 1: rgDisplay.check(R.id.rb_display_income); break;
            case 2: rgDisplay.check(R.id.rb_display_expense); break;
            case 3: rgDisplay.check(R.id.rb_display_overtime); break;
            default: rgDisplay.check(R.id.rb_display_balance); break;
        }

        rgDisplay.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = 0;
            String text = "结余";
            if (checkedId == R.id.rb_display_income) {
                selectedMode = 1;
                text = "收入";
            } else if (checkedId == R.id.rb_display_expense) {
                selectedMode = 2;
                text = "支出";
            } else if (checkedId == R.id.rb_display_overtime) {
                selectedMode = 3;
                text = "加班";
            }

            prefs.edit().putInt("default_record_mode", selectedMode).apply();
            Toast.makeText(this, "已设置为默认显示: " + text, Toast.LENGTH_SHORT).show();

            view.postDelayed(dialog::dismiss, 200);
        });

        view.findViewById(R.id.btn_cancel_display).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}