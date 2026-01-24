package com.example.budgetapp.service;

import android.app.AlertDialog;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import com.example.budgetapp.ui.CategoryAdapter;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.KeywordManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationMonitorService extends NotificationListenerService {

    private static final String TAG = "NotiMonitor";
    private TransactionDao dao;
    private AssistantConfig config;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isWindowShowing = false;

    private long lastRecordTime = 0;
    private String lastContentSignature = "";

    private final Pattern amountPattern = Pattern.compile("(\\d+(\\.\\d{1,2})?)");
    
    private List<AssetAccount> loadedAssets = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        config = new AssistantConfig(this);
        KeywordManager.initDefaults(this);
        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        dao = db.transactionDao();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (config == null) config = new AssistantConfig(this);

        if (!config.isRefundEnabled()) return;

        String packageName = sbn.getPackageName();

        if (!KeywordManager.PKG_WECHAT.equals(packageName) &&
            !KeywordManager.PKG_ALIPAY.equals(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text = extras.getString(Notification.EXTRA_TEXT, "");
        String content = (title + " " + text); 

        if (content.isEmpty()) return;

        if (!content.contains("退款")) {
            return;
        }

        Log.d(TAG, "检测到退款消息: " + content);

        Set<String> incomeKeywords = KeywordManager.getKeywords(this, packageName, KeywordManager.TYPE_INCOME);

        boolean keywordMatched = false;
        for (String kw : incomeKeywords) {
            if (content.contains(kw)) {
                keywordMatched = true;
                break;
            }
        }

        if (content.contains("退款")) {
            keywordMatched = true;
        }

        if (keywordMatched) {
            double amount = extractAmount(content);
            if (amount > 0) {
                triggerConfirmWindow(amount, 1, "退款");
            }
        }
    }

    private double extractAmount(String text) {
        String cleanText = text.replaceAll("\\[?\\d+[条件个笔条]\\]?", "");
        Matcher matcher = amountPattern.matcher(cleanText);
        double bestAmount = 0;
        while (matcher.find()) {
            try {
                String numStr = matcher.group(1).replace(",", "");
                double val = Double.parseDouble(numStr);
                if (val > 0 && val < 200000 && !(val >= 2020 && val <= 2035 && val % 1 == 0)) {
                    bestAmount = val;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return bestAmount;
    }

    private void triggerConfirmWindow(double amount, int type, String category) {
        long now = System.currentTimeMillis();
        String signature = amount + "-" + type + "-" + category;
        if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return;

        lastRecordTime = now;
        lastContentSignature = signature;

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        String timeNote = sdf.format(new Date(now)) + " 退款";

        handler.post(() -> showConfirmWindow(amount, type, category, timeNote));
    }

    private void showConfirmWindow(double amount, int type, String category, String note) {
        if (isWindowShowing) return;

        if (!Settings.canDrawOverlays(this)) {
            saveToDatabase(amount, type, category, note + " (后台)", 0, "¥");
            return;
        }

        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            params.format = PixelFormat.TRANSLUCENT;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;

            ContextThemeWrapper themeContext = new ContextThemeWrapper(this, R.style.Theme_BudgetApp);
            LayoutInflater inflater = LayoutInflater.from(themeContext);
            View floatView = inflater.inflate(R.layout.window_confirm_transaction, null);

            isWindowShowing = true;

            EditText etAmount = floatView.findViewById(R.id.et_window_amount);
            Button btnCurrency = floatView.findViewById(R.id.btn_window_currency);
            RadioGroup rgType = floatView.findViewById(R.id.rg_window_type);
            RecyclerView rvCategory = floatView.findViewById(R.id.rv_window_category);
            EditText etCategory = floatView.findViewById(R.id.et_window_category);
            EditText etNote = floatView.findViewById(R.id.et_window_note);
            EditText etRemark = floatView.findViewById(R.id.et_window_remark);
            Button btnSave = floatView.findViewById(R.id.btn_window_save);
            Button btnCancel = floatView.findViewById(R.id.btn_window_cancel);
            Spinner spAsset = floatView.findViewById(R.id.sp_asset);

            etAmount.setText(String.valueOf(amount));
            etNote.setText(note);

            // 【新增】处理货币单位
            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

            if (isCurrencyEnabled) {
                btnCurrency.setVisibility(View.VISIBLE);
                btnCurrency.setText("¥"); // 默认值
                btnCurrency.setOnClickListener(v -> {
                    // 传入 true，因为这是在悬浮窗服务中
                    // 注意：Context 应该使用 themeContext (在这些 service 代码中通常已经定义了 ContextThemeWrapper themeContext = ...)
                    com.example.budgetapp.util.CurrencyUtils.showCurrencyDialog(themeContext, btnCurrency, true);
                });
            } else {
                btnCurrency.setVisibility(View.GONE);
            }

            List<String> expenseCategories = CategoryManager.getExpenseCategories(this);
            List<String> incomeCategories = CategoryManager.getIncomeCategories(this);

            rvCategory.setLayoutManager(new GridLayoutManager(themeContext, 5));
            
            final String[] selectedCategory = {category};
            List<String> currentList = (type == 1) ? incomeCategories : expenseCategories;

            if (!currentList.contains(category)) {
                selectedCategory[0] = "自定义";
                etCategory.setText(category);
                etCategory.setVisibility(View.VISIBLE);
            } else {
                etCategory.setVisibility(View.GONE);
            }

            CategoryAdapter categoryAdapter = new CategoryAdapter(themeContext, currentList, selectedCategory[0], cat -> {
                selectedCategory[0] = cat;
                if ("自定义".equals(cat)) {
                    etCategory.setVisibility(View.VISIBLE);
                    etCategory.requestFocus();
                } else {
                    etCategory.setVisibility(View.GONE);
                }
            });
            rvCategory.setAdapter(categoryAdapter);

            if (type == 1) {
                rgType.check(R.id.rb_window_income);
            } else {
                rgType.check(R.id.rb_window_expense);
            }

            rgType.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_window_income) {
                    categoryAdapter.updateData(incomeCategories);
                    String first = incomeCategories.isEmpty() ? "自定义" : incomeCategories.get(0);
                    categoryAdapter.setSelectedCategory(first);
                    selectedCategory[0] = first;
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                } else {
                    categoryAdapter.updateData(expenseCategories);
                    String first = expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0);
                    categoryAdapter.setSelectedCategory(first);
                    selectedCategory[0] = first;
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                }
            });

            if (config == null) config = new AssistantConfig(this);
            boolean isAssetEnabled = config.isAssetsEnabled();

            if (isAssetEnabled && spAsset != null) {
                spAsset.setVisibility(View.VISIBLE);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dropdown);
                adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
                spAsset.setAdapter(adapter);

                AppDatabase.databaseWriteExecutor.execute(() -> {
                    List<AssetAccount> assets = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(0);
                    loadedAssets.clear();
                    AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
                    noAsset.id = 0;
                    loadedAssets.add(noAsset);
                    if (assets != null) loadedAssets.addAll(assets);
                    
                    List<String> names = new ArrayList<>();
                    for (AssetAccount a : loadedAssets) names.add(a.name);
                    
                    int defaultAssetId = config.getDefaultAssetId();
                    
                    handler.post(() -> {
                        adapter.clear();
                        adapter.addAll(names);
                        adapter.notifyDataSetChanged();
                        if (defaultAssetId != -1) {
                            for (int i = 0; i < loadedAssets.size(); i++) {
                                if (loadedAssets.get(i).id == defaultAssetId) {
                                    spAsset.setSelection(i);
                                    break;
                                }
                            }
                        }
                    });
                });
            } else if (spAsset != null) {
                spAsset.setVisibility(View.GONE);
            }

            btnSave.setOnClickListener(v -> {
                try {
                    double finalAmount = Double.parseDouble(etAmount.getText().toString());
                    String finalNote = etNote.getText().toString();
                    int finalType = (rgType.getCheckedRadioButtonId() == R.id.rb_window_income) ? 1 : 0;

                    String finalCat = selectedCategory[0];
                    if ("自定义".equals(finalCat)) {
                        String customInput = etCategory.getText().toString().trim();
                        if (!customInput.isEmpty()) {
                            finalCat = customInput;
                        } else {
                            finalCat = (finalType == 1) ? "退款" : "其他"; 
                        }
                    }

                    int assetId = 0;
                    if (isAssetEnabled && spAsset != null) {
                        int selectedPos = spAsset.getSelectedItemPosition();
                        if (selectedPos >= 0 && selectedPos < loadedAssets.size()) {
                            assetId = loadedAssets.get(selectedPos).id;
                        }
                    }
                    
                    // 获取货币符号
                    String symbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

                    saveToDatabase(finalAmount, finalType, finalCat, finalNote, assetId, symbol);
                    closeWindow(windowManager, floatView);
                    Toast.makeText(this, "已记账", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "金额错误", Toast.LENGTH_SHORT).show();
                }
            });

            btnCancel.setOnClickListener(v -> closeWindow(windowManager, floatView));
            windowManager.addView(floatView, params);

        } catch (Exception e) {
            Log.e(TAG, "Window show failed", e);
            isWindowShowing = false;
        }
    }

    private void closeWindow(WindowManager wm, View view) {
        try { wm.removeView(view); } catch (Exception e) {}
        finally { isWindowShowing = false; }
    }

    private void saveToDatabase(double amount, int type, String category, String note, int assetId, String currencySymbol) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            Transaction t = new Transaction();
            t.date = System.currentTimeMillis();
            t.type = type;
            t.category = category;
            t.amount = amount;
            t.note = note;
            t.assetId = assetId;
            t.currencySymbol = currencySymbol; // 保存
            dao.insert(t);

            if (assetId != 0) {
                AssetAccount asset = db.assetAccountDao().getAssetByIdSync(assetId);
                if (asset != null) {
                    if (type == 1) {
                        asset.amount += amount;
                    } else {
                        asset.amount -= amount;
                    }
                    db.assetAccountDao().update(asset);
                }
            }
        });
    }
}