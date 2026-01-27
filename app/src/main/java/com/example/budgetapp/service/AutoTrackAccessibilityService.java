package com.example.budgetapp.service;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.MainActivity;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import com.example.budgetapp.ui.CategoryAdapter;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.AutoAssetManager;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.util.KeywordManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoTrackAccessibilityService extends AccessibilityService {

    private static final String TAG = "AutoTrackService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "monitor_channel";

    private TransactionDao dao;
    private AssistantConfig config;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isWindowShowing = false;
    private View keepAliveView;
    private long lastRecordTime = 0;
    private String lastContentSignature = "";
    
    private long lastWindowDismissTime = 0;

    // 【新增】二级分类变量
    private String selectedSubCategory = null;

    private List<AssetAccount> loadedAssets = new ArrayList<>();

    private final Pattern amountPattern = Pattern.compile("(\\d+(\\.\\d{1,2})?)");
    private final Pattern quantityPattern = Pattern.compile("\\[?\\d+\\s*[件个笔条单]\\s*\\]?");

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (config != null && !config.isEnabled()) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode == null) return;
                
                String packageName = "unknown";
                if (rootNode.getPackageName() != null) {
                    packageName = rootNode.getPackageName().toString();
                }
                
                scanAndAnalyze(rootNode, packageName);
            } catch (Exception e) {
                Log.e(TAG, "Scan error", e);
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        config = new AssistantConfig(this);
        KeywordManager.initDefaults(this);

        try {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            dao = db.transactionDao();
            startForegroundNotification();
            setupKeepAliveWindow();
        } catch (Exception e) {
            Log.e(TAG, "Service init failed", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (config == null) config = new AssistantConfig(this);
        if (!config.isEnabled()) return;

        handler.removeCallbacks(scanRunnable);
        if (isWindowShowing) return;
        
        handler.postDelayed(scanRunnable, 300);
    }

    private void scanAndAnalyze(AccessibilityNodeInfo node, String currentPackageName) {
        if (node == null) return;

        String text = getTextOrDescription(node);
        
        if (text != null && !text.isEmpty()) {
            Set<String> expenseKeywords = KeywordManager.getKeywords(this, currentPackageName, KeywordManager.TYPE_EXPENSE);
            Set<String> incomeKeywords = KeywordManager.getKeywords(this, currentPackageName, KeywordManager.TYPE_INCOME);

            int autoAssetId = AutoAssetManager.matchAsset(this, currentPackageName, text);

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                for (String kw : expenseKeywords) {
                    if (text.contains(kw)) {
                        findAmountRecursive(root, 0, getAppNameReadable(currentPackageName), autoAssetId);
                        return;
                    }
                }

                for (String kw : incomeKeywords) {
                    if (text.contains(kw)) {
                        findAmountRecursive(root, 1, getAppNameReadable(currentPackageName), autoAssetId);
                        return;
                    }
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            scanAndAnalyze(node.getChild(i), currentPackageName);
        }
    }

    private String getAppNameReadable(String packageName) {
        if (packageName.contains("tencent.mm")) return "微信";
        if (packageName.contains("Alipay")) return "支付宝";
        if (packageName.contains("taobao")) return "淘宝";
        if (packageName.contains("jingdong")) return "京东";
        if (packageName.contains("pinduoduo")) return "拼多多";
        if (packageName.contains("aweme")) return "抖音";
        if (packageName.contains("meituan")) return "美团";
        return "自动记账";
    }

    private void findAmountRecursive(AccessibilityNodeInfo root, int type, String defaultCategory, int matchedAssetId) {
        if (root == null) return;
        List<Double> candidates = new ArrayList<>();
        collectAllNumbers(root, candidates); 

        double bestAmount = -1;
        for (Double amount : candidates) {
            String strAmt = String.valueOf(amount);
            if (strAmt.contains(".") && !strAmt.endsWith(".0")) { 
                bestAmount = amount;
                break; 
            }
            if (bestAmount == -1 && amount > 0) {
                bestAmount = amount; 
            }
        }

        if (bestAmount > 0) {
            triggerConfirmWindow(bestAmount, type, defaultCategory, matchedAssetId);
        }
    }

    private void collectAllNumbers(AccessibilityNodeInfo node, List<Double> list) {
        if (node == null) return;
        String text = getTextOrDescription(node);

        if (text != null && !text.isEmpty()) {
            if (!text.contains(":")) {
                String cleanText = quantityPattern.matcher(text).replaceAll("");
                
                Matcher matcher = amountPattern.matcher(cleanText);
                while (matcher.find()) {
                    try {
                        String numStr = matcher.group(1).replace(",", "");
                        double val = Double.parseDouble(numStr);
                        if (val > 0 && val < 200000 && !(val >= 2020 && val <= 2035 && val % 1 == 0)) {
                            list.add(val);
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            collectAllNumbers(node.getChild(i), list);
        }
    }

    private String getTextOrDescription(AccessibilityNodeInfo node) {
        if (node.getText() != null) return node.getText().toString();
        if (node.getContentDescription() != null) return node.getContentDescription().toString();
        return null;
    }

    private void triggerConfirmWindow(double amount, int type, String category, int assetId) {
        long now = System.currentTimeMillis();

        if (now - lastWindowDismissTime < 2500) {
            return;
        }

        String signature = amount + "-" + type;
        if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return;

        lastRecordTime = now;
        lastContentSignature = signature;

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        String timeNote = sdf.format(new Date(now)) + " auto";

        handler.post(() -> showConfirmWindow(amount, type, category, timeNote, assetId));
    }

    private void showConfirmWindow(double amount, int type, String category, String note, int matchedAssetId) {
        if (isWindowShowing) return;
        
        // 重置二级分类
        selectedSubCategory = null;

        if (!Settings.canDrawOverlays(this)) {
            int finalAssetId = (matchedAssetId > 0) ? matchedAssetId : 0;
            saveToDatabase(amount, type, category, null, note + " (后台)", "", finalAssetId, "¥");
            return;
        }

        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();

            params.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            params.format = PixelFormat.TRANSLUCENT;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.5f;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            params.gravity = Gravity.CENTER;
            params.y = -350;

            ContextThemeWrapper themeContext = new ContextThemeWrapper(this, R.style.Theme_BudgetApp);
            LayoutInflater inflater = LayoutInflater.from(themeContext);
            View floatView = inflater.inflate(R.layout.window_confirm_transaction, null);

            View rootView = floatView.findViewById(R.id.window_root);
            if (rootView != null) {
                rootView.setOnClickListener(v -> closeWindow(windowManager, floatView));
            }
            View cardContent = floatView.findViewById(R.id.window_card_content);
            if (cardContent != null) {
                cardContent.setOnClickListener(v -> {});
            }

            isWindowShowing = true;

            EditText etAmount = floatView.findViewById(R.id.et_window_amount);
            Button btnCurrency = floatView.findViewById(R.id.btn_window_currency);
            RadioGroup rgType = floatView.findViewById(R.id.rg_window_type);
            RecyclerView rvCategory = floatView.findViewById(R.id.rv_window_category); 
            EditText etCategory = floatView.findViewById(R.id.et_window_category);
            EditText etNote = floatView.findViewById(R.id.et_window_note);
            EditText etRemark = floatView.findViewById(R.id.et_window_remark);
            Spinner spAsset = floatView.findViewById(R.id.sp_asset);

            Button btnSave = floatView.findViewById(R.id.btn_window_save);
            Button btnCancel = floatView.findViewById(R.id.btn_window_cancel);

            etAmount.setText(String.valueOf(amount));
            etNote.setText(note);

            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

            if (isCurrencyEnabled) {
                btnCurrency.setVisibility(View.VISIBLE);
                btnCurrency.setText("¥"); 
                btnCurrency.setOnClickListener(v -> {
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
                 if (type == 0 && (category.equals("淘宝") || category.equals("京东") || category.equals("拼多多"))) {
                     selectedCategory[0] = "购物";
                 } else if (type == 0 && category.equals("美团")) {
                     selectedCategory[0] = "餐饮"; 
                 } else {
                     selectedCategory[0] = "自定义";
                     etCategory.setText(category);
                     etCategory.setVisibility(View.VISIBLE);
                 }
            } else {
                etCategory.setVisibility(View.GONE);
            }

            CategoryAdapter categoryAdapter = new CategoryAdapter(themeContext, currentList, selectedCategory[0], cat -> {
                selectedCategory[0] = cat;
                selectedSubCategory = null; // 重置
                if ("自定义".equals(cat)) {
                    etCategory.setVisibility(View.VISIBLE);
                    etCategory.requestFocus();
                } else {
                    etCategory.setVisibility(View.GONE);
                }
            });

            // 【新增】长按监听
            categoryAdapter.setOnCategoryLongClickListener(cat -> {
                if (CategoryManager.isSubCategoryEnabled(this) && !"自定义".equals(cat)) {
                    showSubCategoryDialog(themeContext, cat, categoryAdapter);
                    return true;
                }
                return false;
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
                    selectedSubCategory = null;
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                } else {
                    categoryAdapter.updateData(expenseCategories);
                    String first = expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0);
                    categoryAdapter.setSelectedCategory(first);
                    selectedCategory[0] = first;
                    selectedSubCategory = null;
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                }
            });

            if (config == null) config = new AssistantConfig(this);
            boolean isAssetEnabled = config.isAssetsEnabled();

            if (isAssetEnabled) {
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
                    int targetAssetId = (matchedAssetId > 0) ? matchedAssetId : defaultAssetId;
                    
                    handler.post(() -> {
                        adapter.clear();
                        adapter.addAll(names);
                        adapter.notifyDataSetChanged();
                        if (targetAssetId != -1) {
                            for (int i = 0; i < loadedAssets.size(); i++) {
                                if (loadedAssets.get(i).id == targetAssetId) {
                                    spAsset.setSelection(i);
                                    break;
                                }
                            }
                        }
                    });
                });
            } else {
                spAsset.setVisibility(View.GONE);
            }

            btnSave.setOnClickListener(v -> {
                try {
                    double finalAmount = Double.parseDouble(etAmount.getText().toString());
                    String finalNote = etNote.getText().toString();
                    String finalRemark = etRemark.getText().toString().trim();
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
                    if (isAssetEnabled) {
                        int selectedPos = spAsset.getSelectedItemPosition();
                        if (selectedPos >= 0 && selectedPos < loadedAssets.size()) {
                            assetId = loadedAssets.get(selectedPos).id;
                        }
                    }

                    String symbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

                    // 【修改】保存 selectedSubCategory
                    saveToDatabase(finalAmount, finalType, finalCat, selectedSubCategory, finalNote, finalRemark, assetId, symbol);
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
        finally { 
            isWindowShowing = false;
            lastWindowDismissTime = System.currentTimeMillis();
        }
    }

    private void setupKeepAliveWindow() {
        if (!Settings.canDrawOverlays(this) || keepAliveView != null) return;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        keepAliveView = new View(this);
        keepAliveView.setBackgroundColor(Color.TRANSPARENT);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = 1; params.height = 1;
        params.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.TOP | Gravity.START;
        try { wm.addView(keepAliveView, params); } catch (Exception e) {}
    }

    private void startForegroundNotification() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "自动记账服务监控", NotificationManager.IMPORTANCE_LOW);
                if (manager != null) manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                    new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
            Notification notification = builder.setSmallIcon(R.drawable.ic_app_logo)
                    .setContentTitle("Tally").setContentText("招财进宝 财源广进").setContentIntent(pendingIntent).setOngoing(true).build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) { Log.e(TAG, "Foreground service failed", e); }
    }

    // 【修改】显示二级分类对话框 (逻辑与 RecordFragment 保持一致)
    private void showSubCategoryDialog(Context context, String parentCategory, CategoryAdapter adapter) {
        // ... (代码与上方 QuickAddTileService 中的完全一致) ...
        // 为了方便您复制，这里再次提供完整代码块

        List<String> subCats = CategoryManager.getSubCategories(this, parentCategory);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View subCatView = LayoutInflater.from(context).inflate(R.layout.dialog_select_sub_category, null);
        builder.setView(subCatView);

        AlertDialog subCatDialog = builder.create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            subCatDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            subCatDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }

        subCatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = subCatView.findViewById(R.id.tv_title);
        tvTitle.setText(parentCategory + " - 选择细分");

        ChipGroup cgSubCategories = subCatView.findViewById(R.id.cg_sub_categories);
        TextView tvEmpty = subCatView.findViewById(R.id.tv_empty);
        View nsvContainer = subCatView.findViewById(R.id.nsv_container);
        Button btnCancel = subCatView.findViewById(R.id.btn_cancel);

        if (subCats.isEmpty()) {
            cgSubCategories.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            nsvContainer.setMinimumHeight(150);
        } else {
            cgSubCategories.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            int bgDefault = ContextCompat.getColor(context, R.color.cat_unselected_bg);
            int bgChecked = ContextCompat.getColor(context, R.color.app_yellow);
            int textDefault = ContextCompat.getColor(context, R.color.text_primary);
            int textChecked = ContextCompat.getColor(context, R.color.cat_selected_text);

            int[][] states = new int[][] {
                    new int[] { android.R.attr.state_checked },
                    new int[] { }
            };
            ColorStateList bgStateList = new ColorStateList(states, new int[] { bgChecked, bgDefault });
            ColorStateList textStateList = new ColorStateList(states, new int[] { textChecked, textDefault });

            for (String subCatName : subCats) {
                // ... (Chip 创建和样式设置保持不变)
                Chip chip = new Chip(context);
                chip.setText(subCatName);
                chip.setCheckable(true);
                chip.setClickable(true);
                chip.setChipBackgroundColor(bgStateList);
                chip.setTextColor(textStateList);
                chip.setChipStrokeWidth(0);
                chip.setCheckedIconVisible(false);

                if (subCatName.equals(selectedSubCategory)) {
                    chip.setChecked(true);
                }

                // 【修改】点击事件：支持取消选择
                chip.setOnClickListener(v -> {
                    if (subCatName.equals(selectedSubCategory)) {
                        // 取消选择
                        selectedSubCategory = null;
                        Toast.makeText(this, "已取消细分", Toast.LENGTH_SHORT).show();
                    } else {
                        // 选中
                        selectedSubCategory = subCatName;
                        Toast.makeText(this, "已选择: " + subCatName, Toast.LENGTH_SHORT).show();
                    }

                    if (adapter != null) {
                        adapter.setSelectedCategory(parentCategory);
                    }
                    subCatDialog.dismiss();
                });

                cgSubCategories.addView(chip);
            }
        }

        btnCancel.setOnClickListener(v -> subCatDialog.dismiss());
        subCatDialog.show();
    }

    // 【修改】添加 subCategory 参数
    private void saveToDatabase(double amount, int type, String category, String subCategory, String note, String remark, int assetId, String currencySymbol) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            
            Transaction t = new Transaction();
            t.date = System.currentTimeMillis();
            t.type = type;
            t.category = category;
            t.subCategory = subCategory; // 保存二级分类
            t.amount = amount;
            t.note = note;
            t.remark = remark;
            t.assetId = assetId;
            t.currencySymbol = currencySymbol; 
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

    @Override public void onInterrupt() {}
}