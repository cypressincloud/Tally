package com.example.budgetapp.service;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
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
import android.widget.Toast;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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

    private List<AssetAccount> loadedAssets = new ArrayList<>();

    // 匹配金额的正则：支持整数和小数
    private final Pattern amountPattern = Pattern.compile("(\\d+(\\.\\d{1,2})?)");
    // 匹配数量单位的正则：用于过滤 "1件"、"2个" 等非金额数字
    private final Pattern quantityPattern = Pattern.compile("\\[?\\d+\\s*[件个笔条单]\\]?");

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

        // 防抖动：移除之前的任务，延迟执行新的扫描
        handler.removeCallbacks(scanRunnable);
        if (isWindowShowing) return;
        
        // 界面变动后延迟 300ms 再扫描，确保界面元素已加载完毕（如金额数字）
        handler.postDelayed(scanRunnable, 300);
    }

    private void scanAndAnalyze(AccessibilityNodeInfo node, String currentPackageName) {
        if (node == null) return;

        String text = getTextOrDescription(node);
        
        if (text != null && !text.isEmpty()) {
            Set<String> expenseKeywords = KeywordManager.getKeywords(this, currentPackageName, KeywordManager.TYPE_EXPENSE);
            Set<String> incomeKeywords = KeywordManager.getKeywords(this, currentPackageName, KeywordManager.TYPE_INCOME);

            int autoAssetId = AutoAssetManager.matchAsset(this, currentPackageName, text);

            for (String kw : expenseKeywords) {
                if (text.contains(kw)) {
                    // 发现关键字，开始全屏搜索金额
                    findAmountRecursive(getRootInActiveWindow(), 0, getAppNameReadable(currentPackageName), autoAssetId);
                    return;
                }
            }

            for (String kw : incomeKeywords) {
                if (text.contains(kw)) {
                    findAmountRecursive(getRootInActiveWindow(), 1, getAppNameReadable(currentPackageName), autoAssetId);
                    return;
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
        // 简单策略：优先取带小数点的数字（更像金额），否则取第一个合理的数字
        for (Double amount : candidates) {
            String strAmt = String.valueOf(amount);
            // 如果包含小数点且不以.0结尾（例如 12.50），优先级最高
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

    // 【核心修复】修复了之前因包含冒号而被过滤的 bug
    private void collectAllNumbers(AccessibilityNodeInfo node, List<Double> list) {
        if (node == null) return;
        String text = getTextOrDescription(node);

        if (text != null && !text.isEmpty()) {
            // 过滤掉纯时间格式 (如 12:30)，但保留 "金额: 20.00" 这种格式
            boolean isStrictTime = text.matches("^\\d{1,2}:\\d{2}(:\\d{2})?$");
            
            if (!isStrictTime) {
                // 先移除数量单位（如 1件），避免误判
                String cleanText = quantityPattern.matcher(text).replaceAll("");
                Matcher matcher = amountPattern.matcher(cleanText);
                while (matcher.find()) {
                    try {
                        String numStr = matcher.group(1).replace(",", "");
                        double val = Double.parseDouble(numStr);
                        
                        // 过滤逻辑：
                        // 1. 必须大于0
                        // 2. 必须小于20万（避免提取到订单号等长数字）
                        // 3. 过滤掉可能是年份的数字（2020-2035之间的整数）
                        if (val > 0 && val < 200000 && !(val >= 2020 && val <= 2035 && val % 1 == 0)) {
                            list.add(val);
                        }
                    } catch (Exception e) {
                        // ignore parsing errors
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
        // 防重复弹窗：5秒内金额和类型相同则忽略
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

        if (!Settings.canDrawOverlays(this)) {
            int finalAssetId = (matchedAssetId > 0) ? matchedAssetId : 0;
            saveToDatabase(amount, type, category, note + " (后台)", "", finalAssetId);
            return;
        }

        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();

            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            params.format = PixelFormat.TRANSLUCENT;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.5f;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            params.gravity = Gravity.CENTER;
            params.y = -350;

            android.content.Context themeContext = new android.view.ContextThemeWrapper(this, R.style.Theme_BudgetApp);
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

            // 使用 CategoryManager 获取最新分类
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
                            Toast.makeText(this, "请输入自定义分类", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    int assetId = 0;
                    if (isAssetEnabled) {
                        int selectedPos = spAsset.getSelectedItemPosition();
                        if (selectedPos >= 0 && selectedPos < loadedAssets.size()) {
                            assetId = loadedAssets.get(selectedPos).id;
                        }
                    }
                    saveToDatabase(finalAmount, finalType, finalCat, finalNote, finalRemark, assetId);
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

    private void saveToDatabase(double amount, int type, String category, String note, String remark, int assetId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            
            Transaction t = new Transaction();
            t.date = System.currentTimeMillis();
            t.type = type;
            t.category = category;
            t.amount = amount;
            t.note = note;
            t.remark = remark;
            t.assetId = assetId;
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