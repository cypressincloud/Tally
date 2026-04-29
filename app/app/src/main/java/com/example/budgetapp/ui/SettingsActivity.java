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

import java.util.Map;

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

    // 新增：用于选择自定义背景图片的 Launcher
    private final ActivityResultLauncher<String[]> pickCustomBgLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        // 获取持久化读取权限，保证应用重启后依然可以读取该图片
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                        prefs.edit()
                                .putInt("theme_mode", 3) // 使用 3 代表自定义背景模式
                                .putString("custom_bg_uri", uri.toString())
                                .apply();

                        Toast.makeText(this, "自定义背景已保存，请返回主页查看", Toast.LENGTH_SHORT).show();
                    } catch (SecurityException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "获取图片权限失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

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

    private final ActivityResultLauncher<String[]> importBeeCountLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        BackupData data = BackupManager.importFromBeeCount(this, uri, allAssets);

                        int recordCount = 0;
                        int newAssetCount = 0;

                        // 添加资产
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

                        // 保存更新后的一级分类
                        if (data.expenseCategories != null && !data.expenseCategories.isEmpty()) {
                            CategoryManager.saveExpenseCategories(this, data.expenseCategories);
                        }
                        if (data.incomeCategories != null && !data.incomeCategories.isEmpty()) {
                            CategoryManager.saveIncomeCategories(this, data.incomeCategories);
                        }
                        // 保存更新后的二级分类（如果没有则自动生成后会存储到这里）
                        if (data.subCategoryMap != null && !data.subCategoryMap.isEmpty()) {
                            for (Map.Entry<String, List<String>> entry : data.subCategoryMap.entrySet()) {
                                CategoryManager.saveSubCategories(this, entry.getKey(), entry.getValue());
                            }
                        }

                        // 添加账单记录
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
                            String msg = "成功从蜜蜂记账导入 " + recordCount + " 条账单 (已过滤重复)";
                            if (newAssetCount > 0) {
                                msg += "\n自动创建了 " + newAssetCount + " 个新资产账户";
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "所有账单均已存在，或未找到有效数据", Toast.LENGTH_LONG).show();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "蜜蜂记账导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

        // 新增：跳转到预算管理页面
        findViewById(R.id.btn_budget_management).setOnClickListener(v -> startActivity(new Intent(this, BudgetManagementActivity.class)));

        findViewById(R.id.btn_backup_restore).setOnClickListener(v -> showBackupOptions());
        findViewById(R.id.btn_auto_asset).setOnClickListener(v -> startActivity(new Intent(this, AutoAssetActivity.class)));
        findViewById(R.id.btn_toggle_night_mode).setOnClickListener(v -> startActivity(new Intent(this, ThemeSettingsActivity.class)));
        findViewById(R.id.btn_assistant_setting).setOnClickListener(v -> startActivity(new Intent(this, AssistantManagerActivity.class)));
        findViewById(R.id.btn_overtime_setting).setOnClickListener(v -> showSetOvertimeRateDialog());
        findViewById(R.id.btn_default_record_display).setOnClickListener(v -> showDefaultRecordDisplayDialog());

        findViewById(R.id.btn_auto_track_log).setOnClickListener(v -> {
            startActivity(new Intent(this, AutoTrackLogActivity.class));
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

        // 新增：快捷按钮设置
        findViewById(R.id.btn_quick_record_setting).setOnClickListener(v -> showQuickRecordSettingDialog());

        // 新增：点击进入密码与生物识别页面
        findViewById(R.id.btn_security_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SecuritySettingsActivity.class));
        });

        // 新增：点击进入关于页面
        findViewById(R.id.btn_about).setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
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

    private final ActivityResultLauncher<String[]> importYimuLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        BackupData data = BackupManager.importFromYimu(this, uri, allAssets);

                        int recordCount = 0;
                        int newAssetCount = 0;

                        // 1. 添加并保存资产
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

                        // 2. 保存更新后的分类
                        if (data.expenseCategories != null && !data.expenseCategories.isEmpty()) {
                            CategoryManager.saveExpenseCategories(this, data.expenseCategories);
                        }
                        if (data.incomeCategories != null && !data.incomeCategories.isEmpty()) {
                            CategoryManager.saveIncomeCategories(this, data.incomeCategories);
                        }
                        if (data.subCategoryMap != null && !data.subCategoryMap.isEmpty()) {
                            for (Map.Entry<String, List<String>> entry : data.subCategoryMap.entrySet()) {
                                CategoryManager.saveSubCategories(this, entry.getKey(), entry.getValue());
                            }
                        }

                        // 3. 添加账单记录
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
                            String msg = "成功从一木记账导入 " + recordCount + " 条账单 (已过滤重复)";
                            if (newAssetCount > 0) {
                                msg += "\n自动创建了 " + newAssetCount + " 个新资产账户";
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "所有账单均已存在，或未找到有效数据", Toast.LENGTH_LONG).show();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "一木记账导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

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

        view.findViewById(R.id.tv_import_yimu).setOnClickListener(v -> {
            importYimuLauncher.launch(new String[]{
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

        // ============ 新增 ============
        view.findViewById(R.id.tv_import_beecount).setOnClickListener(v -> {
            importBeeCountLauncher.launch(new String[]{
                    "text/csv",
                    "text/plain",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "*/*"
            });
            dialog.dismiss();
        });
        // ==============================

        // --- 新增：导出分类预设 JSON ---
        View tvExportCategory = view.findViewById(R.id.tv_export_category_json);
        if (tvExportCategory != null) {
            tvExportCategory.setOnClickListener(v -> {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                String fileName = "Tally_分类预设_" + sdf.format(new Date()) + ".json";
                exportCategoryJsonLauncher.launch(fileName);
                dialog.dismiss();
            });
        }

        // --- 新增：导入分类预设 JSON ---
        View tvImportCategory = view.findViewById(R.id.tv_import_category_json);
        if (tvImportCategory != null) {
            tvImportCategory.setOnClickListener(v -> {
                importCategoryJsonLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
                dialog.dismiss();
            });
        }

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

        // 新增对 mode == 3 (自定义背景) 的判断
        if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
            rgTheme.check(R.id.rb_day_mode);
        } else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            rgTheme.check(R.id.rb_night_mode);
        } else if (currentMode == 3) {
            rgTheme.check(R.id.rb_custom_bg);
        } else {
            rgTheme.check(R.id.rb_follow_system);
        }

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            // 如果用户点击了自定义背景，触发选择图片并直接返回
            if (checkedId == R.id.rb_custom_bg) {
                pickCustomBgLauncher.launch(new String[]{"image/*"});
                view.postDelayed(dialog::dismiss, 200);
                return;
            }

            int selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (checkedId == R.id.rb_day_mode) {
                selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rb_night_mode) {
                selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
            }

            // 用户如果切回了普通的主题模式，清除自定义背景标识
            prefs.edit()
                    .putInt("theme_mode", selectedMode)
                    // 可以选择保留之前选中的 URI，也可以不处理，只要 theme_mode 变了就行
                    .apply();

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

    private void showQuickRecordSettingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_quick_record_settings, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        // 设置弹窗背景为透明，以便显示 CardView 的圆角
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        android.widget.RadioGroup rgQuickRecord = view.findViewById(R.id.rg_quick_record);
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int currentMode = prefs.getInt("quick_record_mode", 0);

        // 初始化选中状态
        if (currentMode == 1) {
            rgQuickRecord.check(R.id.rb_quick_add);
        } else if (currentMode == 2) {
            rgQuickRecord.check(R.id.rb_quick_ai_assistant);
        } else {
            rgQuickRecord.check(R.id.rb_quick_default);
        }

        // 监听选项改变
        rgQuickRecord.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = 0;
            String text = "进入账单详情页";

            if (checkedId == R.id.rb_quick_add) {
                selectedMode = 1;
                text = "直接进入记一笔";
            } else if (checkedId == R.id.rb_quick_ai_assistant) {
                selectedMode = 2;
                text = "直接进入AI记账助手";
            }

            prefs.edit().putInt("quick_record_mode", selectedMode).apply();
            Toast.makeText(this, "已设置为: " + text, Toast.LENGTH_SHORT).show();

            // 延迟一点关闭,让用户能看到选中的反馈效果
            view.postDelayed(dialog::dismiss, 200);
        });

        // 取消按钮点击事件
        view.findViewById(R.id.btn_cancel_quick_record).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // --- 新增：导出分类预设为 JSON ---
    private final ActivityResultLauncher<String> exportCategoryJsonLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    try {
                        org.json.JSONObject root = new org.json.JSONObject();
                        root.put("version", 1);

                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        root.put("backup_date", sdf.format(new Date()));

                        // 获取当前的一级分类（请确保 CategoryManager 中有对应的 get 方法）
                        // 注意：如果你的获取方法名不同，请在此处修改
                        List<String> expenses = CategoryManager.getExpenseCategories(this);
                        List<String> incomes = CategoryManager.getIncomeCategories(this);

                        if (expenses == null) expenses = new ArrayList<>();
                        if (incomes == null) incomes = new ArrayList<>();

                        root.put("expenseCategories", new org.json.JSONArray(expenses));
                        root.put("incomeCategories", new org.json.JSONArray(incomes));

                        // 遍历一级分类，获取对应的二级分类并组装
                        org.json.JSONObject subCatsObj = new org.json.JSONObject();
                        for (String exp : expenses) {
                            List<String> subs = CategoryManager.getSubCategories(this, exp);
                            if (subs != null && !subs.isEmpty()) {
                                subCatsObj.put(exp, new org.json.JSONArray(subs));
                            }
                        }
                        for (String inc : incomes) {
                            List<String> subs = CategoryManager.getSubCategories(this, inc);
                            if (subs != null && !subs.isEmpty()) {
                                subCatsObj.put(inc, new org.json.JSONArray(subs));
                            }
                        }
                        root.put("subCategoryMap", subCatsObj);

                        // 将 JSON 字符串写入文件
                        try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                // 使用 4 个空格缩进格式化 JSON 以提高可读性
                                os.write(root.toString(4).getBytes(StandardCharsets.UTF_8));
                                os.flush();
                                Toast.makeText(this, "分类预设导出成功", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    // --- 新增：从 JSON 导入分类预设 ---
    private final ActivityResultLauncher<String[]> importCategoryJsonLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        // 读取文件内容
                        java.io.InputStream is = getContentResolver().openInputStream(uri);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        if (is != null) is.close();

                        // 解析 JSON
                        org.json.JSONObject root = new org.json.JSONObject(sb.toString());

                        // 恢复支出分类
                        if (root.has("expenseCategories")) {
                            org.json.JSONArray expArray = root.getJSONArray("expenseCategories");
                            List<String> expenses = new ArrayList<>();
                            for (int i = 0; i < expArray.length(); i++) {
                                expenses.add(expArray.getString(i));
                            }
                            CategoryManager.saveExpenseCategories(this, expenses);
                        }

                        // 恢复收入分类
                        if (root.has("incomeCategories")) {
                            org.json.JSONArray incArray = root.getJSONArray("incomeCategories");
                            List<String> incomes = new ArrayList<>();
                            for (int i = 0; i < incArray.length(); i++) {
                                incomes.add(incArray.getString(i));
                            }
                            CategoryManager.saveIncomeCategories(this, incomes);
                        }

                        // 恢复二级分类映射
                        if (root.has("subCategoryMap")) {
                            org.json.JSONObject subCatsObj = root.getJSONObject("subCategoryMap");
                            java.util.Iterator<String> keys = subCatsObj.keys();
                            while (keys.hasNext()) {
                                String parentCategory = keys.next();
                                org.json.JSONArray subArray = subCatsObj.getJSONArray(parentCategory);
                                List<String> subCategories = new ArrayList<>();
                                for (int i = 0; i < subArray.length(); i++) {
                                    subCategories.add(subArray.getString(i));
                                }
                                CategoryManager.saveSubCategories(this, parentCategory, subCategories);
                            }
                        }

                        Toast.makeText(this, "分类预设导入成功", Toast.LENGTH_SHORT).show();

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "分类预设导入失败: 请检查文件格式", Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

}