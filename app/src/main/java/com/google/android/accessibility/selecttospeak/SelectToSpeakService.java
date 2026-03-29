package com.google.android.accessibility.selecttospeak;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
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
import com.example.budgetapp.ui.PhotoActionActivity;
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

public class SelectToSpeakService extends AccessibilityService {

    private static final String TAG = "AutoTrackService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "monitor_channel";

    private TransactionDao dao;
    private AssistantConfig config;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isWindowShowing = false;
    private View windowRootView;
    private View keepAliveView;
    private long lastRecordTime = 0;
    private String lastContentSignature = "";

    private long lastWindowDismissTime = 0;

    private String selectedSubCategory = null;

    private List<AssetAccount> loadedAssets = new ArrayList<>();

    private final Pattern amountPattern = Pattern.compile("(\\d+(\\.\\d{1,2})?)");
    private final Pattern quantityPattern = Pattern.compile("\\[?\\d+\\s*[件个笔条单]\\s*\\]?");

    // 更新金额匹配正则：捕获金额前的1-3位非数字符号
    private final Pattern amountWithSymbolPattern = Pattern.compile("([^0-9\\s]{1,3})?\\s*([0-9,]+(\\.\\d{1,2})?)");

    // 内部类，用于同时记录数值和识别到的符号
    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (config != null && !config.isEnabled()) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode == null) return;

                String packageName = rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : "";


                // ======= 全局无差别节点树日志捕获 =======
                // 只有在日志页面的“开启抓取”被打开时才会进行记录，并且排除了本应用避免套娃
                if (packageName != null && !packageName.isEmpty() && !packageName.equals("com.example.budgetapp")) {
                    if (com.example.budgetapp.util.AutoTrackLogManager.isLogEnabled) {
                        String appName = getAppNameReadable(packageName);
                        com.example.budgetapp.util.AutoTrackLogManager.addLog(packageName, "►►► 捕获到 [" + appName + "] 页面刷新 ◄◄◄");
                        printNodeToManager(rootNode, 0, packageName);
                    }
                }
                // =====================================

//                // ==========================================
//                // 【测试阶段临时添加】如果是微信，则打印整棵节点树
//                if ("com.tencent.mm".equals(packageName)) {
//                    debugWeChatNodeTree(rootNode);
//                }
//                // ==========================================
//
//                // ======= 支付宝调试入口 =======
//                if ("com.eg.android.AlipayGphone".equals(packageName)) {
//                    debugAlipayNodeTree(rootNode);
//                    // 如果已经写好了支付宝的特定适配方法，也可以在这里调用
//                    // if (handleAlipaySpecificPage(rootNode)) return;
//                }
//                // ============================

                // ======= 支付宝专属逻辑 =======
                if ("com.eg.android.AlipayGphone".equals(packageName)) {
                    // 1. 新增：优先适配支付宝历史账单详情页面 / 免密支付页面（支持同步历史时间）
                    if (handleAlipayBillDetailPage(rootNode)) return;

                    // 2. 尝试适配支付宝刚支付成功的页面
                    if (handleAlipayPaySuccessPage(rootNode)) return;
                }
                // ============================

                // ======= 微信专属页面拦截 =======
                if ("com.tencent.mm".equals(packageName)) {
                    // 0. 新增：专门适配你日志中提供的这种特定的微信红包/支付页面
                    if (handleWeChatRedPacketSpecialPage(rootNode)) return;

                    // 1. 优先尝试适配红包页面
                    if (handleWeChatRedPacketPage(rootNode)) return;

                    // 2. 尝试适配支付成功页面
                    if (handleWeChatPaySuccessPage(rootNode)) return;

                    // 3. 待确认收款页面适配 (新增)
                    if (handleWeChatTransferPendingPage(rootNode)) return;

                    // 4. 历史账单详情页 (新增)
                    if (handleWeChatBillDetailPage(rootNode)) return;

                    // 5. 历史账单详情页 (新增)
                    if (handleWeChatBillDetailPage(rootNode)) return;
                }
                // ==============================

                scanAndAnalyze(rootNode, packageName);
            } catch (Exception e) {
                Log.e(TAG, "Scan error", e);
            }
        }
    };

    // 新增：向界面输出Logcat同款节点树日志 (去除视觉噪音版)
    private void printNodeToManager(AccessibilityNodeInfo node, int depth, String packageName) {
        if (node == null) return;

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("    "); // 使用纯空格替代圆点，保持等宽对齐
        }

        String text = node.getText() != null ? node.getText().toString() : "null";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "null";
        String className = node.getClassName() != null ? node.getClassName().toString() : "null";
        String viewId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "null";

        // 过滤无意义空节点
        if (!"null".equals(text) || !"null".equals(desc) || !"null".equals(viewId)) {
            String shortClass = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;

            // 非根节点加一个小巧的折线箭头，视觉更清晰
            String prefix = depth == 0 ? "" : "↳ ";

            StringBuilder logMsg = new StringBuilder(indent.toString() + prefix + shortClass);
            if (!"null".equals(text)) logMsg.append(" | Text: [").append(text).append("]");
            if (!"null".equals(desc)) logMsg.append(" | Desc: [").append(desc).append("]");
            if (!"null".equals(viewId)) {
                String shortId = viewId.contains("/") ? viewId.substring(viewId.indexOf('/') + 1) : viewId;
                logMsg.append(" | ID: ").append(shortId);
            }

            com.example.budgetapp.util.AutoTrackLogManager.addLog(packageName, logMsg.toString());
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            printNodeToManager(node.getChild(i), depth + 1, packageName);
        }
    }

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
        List<AmountResult> candidates = new ArrayList<>();

        // 1. 获取全屏拼接文本（解决整数和小数被拆分到不同节点的问题）
        String fullText = getAllTextFromNode(root);

        if (fullText != null && !fullText.isEmpty()) {
            // 2. 预处理：去掉常见的时间格式(如 12:34 或 12:34:56)，防止其被误认为金额
            fullText = fullText.replaceAll("\\d{1,2}:\\d{2}(:\\d{2})?", "");
            // 去掉件数/笔数等干扰
            String cleanText = quantityPattern.matcher(fullText).replaceAll("");

            // 3. 全局匹配金额
            Matcher matcher = amountWithSymbolPattern.matcher(cleanText);
            while (matcher.find()) {
                try {
                    String capturedSymbol = matcher.group(1);
                    // 移除千分位逗号，确保 Double.parseDouble 能正常解析
                    String numStr = matcher.group(2).replace(",", "");
                    double val = Double.parseDouble(numStr);

                    // 过滤掉异常数值和年份（如 2023.0 不会被记为金额）
                    if (val > 0 && val < 200000 && !(val >= 2020 && val <= 2035 && val % 1 == 0)) {
                        String validSymbol = null;
                        if (capturedSymbol != null) {
                            for (String s : com.example.budgetapp.util.CurrencyUtils.CURRENCY_SYMBOLS) {
                                if (capturedSymbol.trim().contains(s)) {
                                    validSymbol = s;
                                    break;
                                }
                            }
                        }
                        candidates.add(new AmountResult(val, validSymbol));
                    }
                } catch (Exception e) {
                    // 忽略格式解析异常，继续匹配下一个
                }
            }
        }

        AmountResult bestResult = null;
        for (AmountResult res : candidates) {
            // 优先选择带小数点且不是整除格式的金额（这能更准确命中实际交易金额）
            if (String.valueOf(res.value).contains(".") && !String.valueOf(res.value).endsWith(".0")) {
                bestResult = res;
                break;
            }
            if (bestResult == null && res.value > 0) {
                bestResult = res;
            }
        }

        if (bestResult != null) {
            String detectedSymbol = bestResult.symbol;
            if (detectedSymbol == null) {
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                detectedSymbol = prefs.getString("default_currency_symbol", "¥");
            }

            final double finalAmount = bestResult.value;
            final String finalCurrency = detectedSymbol;
            final int finalType = type;
            final String finalCategory = defaultCategory;
            final int finalAssetId = matchedAssetId;

            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return;

            String signature = finalAmount + "-" + finalType;
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return;

            lastRecordTime = now;
            lastContentSignature = signature;

            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String timeNote = sdf.format(new Date(now)) + " auto";

            handler.post(() -> showConfirmWindow(finalAmount, finalType, finalCategory, timeNote, finalAssetId, finalCurrency));
        }
    }

    // 新增：递归遍历节点树，并将所有文本无缝拼接成一整句话
    private String getAllTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        String text = getTextOrDescription(node);
        if (text != null && !text.isEmpty()) {
            // 去除头尾空格后拼接，确保 "12" 和 ".34" 拼装成 "12.34"
            sb.append(text.trim());
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            sb.append(getAllTextFromNode(node.getChild(i)));
        }
        return sb.toString();
    }
    private void collectAllNumbers(AccessibilityNodeInfo node, List<AmountResult> list) {
        if (node == null) return;
        String text = getTextOrDescription(node);
        if (text != null && !text.isEmpty()) {
            if (!text.contains(":")) {
                String cleanText = quantityPattern.matcher(text).replaceAll("");
                Matcher matcher = amountWithSymbolPattern.matcher(cleanText);
                while (matcher.find()) {
                    try {
                        String capturedSymbol = matcher.group(1); // 捕获到的符号部分
                        String numStr = matcher.group(2).replace(",", "");
                        double val = Double.parseDouble(numStr);

                        if (val > 0 && val < 200000 && !(val >= 2020 && val <= 2035 && val % 1 == 0)) {
                            String validSymbol = null;
                            if (capturedSymbol != null) {
                                // 遍历工具类中的货币符号列表进行匹配
                                for (String s : com.example.budgetapp.util.CurrencyUtils.CURRENCY_SYMBOLS) {
                                    if (capturedSymbol.trim().contains(s)) {
                                        validSymbol = s;
                                        break;
                                    }
                                }
                            }
                            list.add(new AmountResult(val, validSymbol));
                        }
                    } catch (Exception e) {}
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
        if (now - lastWindowDismissTime < 2500) return;

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        final String timeNote = sdf.format(new Date(now)) + " auto";

        // 获取默认货币符号
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        final String defaultSymbol = prefs.getString("default_currency_symbol", "¥");

        handler.post(() -> showConfirmWindow(amount, type, category, timeNote, assetId, defaultSymbol));
    }

    // 1. 新增兼容方法（给红包、刚支付成功等不需要历史时间的场景使用）
    // 1. 新增兼容方法（给红包、刚支付成功等不需要历史时间的场景使用）
    private void showConfirmWindow(double amount, int type, String category, String note, int matchedAssetId, String initialSymbol) {
        showConfirmWindow(amount, type, category, note, matchedAssetId, initialSymbol, System.currentTimeMillis());
    }

    // 2. 核心主方法（必须加上 long transactionTime）
    private void showConfirmWindow(double amount, int type, String category, String note, int matchedAssetId, String initialSymbol, long transactionTime) {
        if (isWindowShowing) return;
        selectedSubCategory = null;

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
        boolean isPhotoBackupEnabled = prefs.getBoolean("enable_photo_backup", false);

        // 【修改点 A】：如果是后台直接记账（无悬浮窗权限），传入 transactionTime
        if (!Settings.canDrawOverlays(this)) {
            int finalAssetId = (matchedAssetId > 0) ? matchedAssetId : 0;
            saveToDatabase(amount, type, category, null, note + " (后台)", "", finalAssetId, initialSymbol, "", transactionTime);
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
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.5f;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            params.gravity = Gravity.CENTER;
            params.y = -350;

            ContextThemeWrapper themeContext = new ContextThemeWrapper(this, R.style.Theme_BudgetApp);
            LayoutInflater inflater = LayoutInflater.from(themeContext);
            View floatView = inflater.inflate(R.layout.window_confirm_transaction, null);

            this.windowRootView = floatView;
            android.widget.FrameLayout windowContentRoot = floatView.findViewById(R.id.window_root);

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
            Button btnTakePhoto = floatView.findViewById(R.id.btn_window_take_photo);
            Button btnViewPhoto = floatView.findViewById(R.id.btn_window_view_photo);

            etAmount.setText(String.valueOf(amount));
            etNote.setText(note);

            if (isCurrencyEnabled) {
                btnCurrency.setVisibility(View.VISIBLE);
                btnCurrency.setText(initialSymbol);
                btnCurrency.setOnClickListener(v -> {
                    com.example.budgetapp.util.CurrencyUtils.showCurrencyDialog(themeContext, btnCurrency, true);
                });
            } else {
                btnCurrency.setVisibility(View.GONE);
            }

            final String[] currentPhotoPath = {null};
            if (isPhotoBackupEnabled) {
                btnTakePhoto.setVisibility(View.VISIBLE);
                btnTakePhoto.setOnClickListener(v -> {
                    showLocalPhotoDialog(themeContext, windowContentRoot, actionType -> {
                        hideWindowAndStartPhotoActivity(actionType, null, currentPhotoPath);
                    });
                });
                btnViewPhoto.setOnClickListener(v -> {
                    if (currentPhotoPath[0] != null) {
                        hideWindowAndStartPhotoActivity(PhotoActionActivity.ACTION_VIEW, currentPhotoPath[0], currentPhotoPath);
                    }
                });
            }

            List<String> expenseCategories = CategoryManager.getExpenseCategories(this);
            List<String> incomeCategories = CategoryManager.getIncomeCategories(this);

            rvCategory.setLayoutManager(new GridLayoutManager(themeContext, 5));// 动态判断：如果是详细分类，则使用弹性流式布局；否则恢复 5 列网格布局
            boolean isDetailed = com.example.budgetapp.util.CategoryManager.isDetailedCategoryEnabled(this);
            if (isDetailed) {
                com.google.android.flexbox.FlexboxLayoutManager flexboxLayoutManager = new com.google.android.flexbox.FlexboxLayoutManager(themeContext);
                flexboxLayoutManager.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
                flexboxLayoutManager.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
                flexboxLayoutManager.setJustifyContent(com.google.android.flexbox.JustifyContent.FLEX_START);
                rvCategory.setLayoutManager(flexboxLayoutManager);
            } else {
                rvCategory.setLayoutManager(new GridLayoutManager(themeContext, 5));
            }
            final String[] selectedCategory = {category};
            List<String> currentList = (type == 1) ? incomeCategories : expenseCategories;

            if (!currentList.contains(category)) {
                if (type == 0 && (category.equals("微信") || category.equals("支付宝") || category.equals("淘宝") || category.equals("京东") || category.equals("拼多多"))) {
                    selectedCategory[0] = "购物";
                } else if (type == 0 && category.equals("美团")) {
                    selectedCategory[0] = "餐饮";
                } else {
                    selectedCategory[0] = "自定义";
                    etCategory.setText(category);
                    etCategory.setVisibility(View.VISIBLE);
                }
            }

            CategoryAdapter categoryAdapter = new CategoryAdapter(themeContext, currentList, selectedCategory[0], cat -> {
                selectedCategory[0] = cat;
                selectedSubCategory = null;
                etCategory.setVisibility("自定义".equals(cat) ? View.VISIBLE : View.GONE);
            });

            categoryAdapter.setOnCategoryLongClickListener(cat -> {
                if (CategoryManager.isSubCategoryEnabled(this) && !"自定义".equals(cat)) {
                    if (!cat.equals(selectedCategory[0])) {
                        categoryAdapter.setSelectedCategory(cat);
                        selectedCategory[0] = cat;
                        selectedSubCategory = null;
                        etCategory.setVisibility(View.GONE);
                    }
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
                List<String> newList = (checkedId == R.id.rb_window_income) ? incomeCategories : expenseCategories;
                categoryAdapter.updateData(newList);
                String first = newList.isEmpty() ? "自定义" : newList.get(0);
                categoryAdapter.setSelectedCategory(first);
                selectedCategory[0] = first;
                selectedSubCategory = null;
                etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
            });

            if (config == null) config = new AssistantConfig(this);
            if (config.isAssetsEnabled()) {
                spAsset.setVisibility(View.VISIBLE);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dropdown);
                adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
                spAsset.setAdapter(adapter);

                AppDatabase.databaseWriteExecutor.execute(() -> {
                    // 【修改】同时加载资产(0)和负债(1)
                    List<AssetAccount> assets = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(0);
                    List<AssetAccount> liabilities = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(1);

                    loadedAssets.clear();
                    AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
                    noAsset.id = 0;
                    loadedAssets.add(noAsset);

                    if (assets != null) loadedAssets.addAll(assets);
                    if (liabilities != null) loadedAssets.addAll(liabilities);

                    int targetAssetId = (matchedAssetId > 0) ? matchedAssetId : config.getDefaultAssetId();
                    List<String> names = new ArrayList<>();
                    for (AssetAccount a : loadedAssets) names.add(a.name);

                    handler.post(() -> {
                        adapter.clear();
                        adapter.addAll(names);
                        adapter.notifyDataSetChanged();
                        for (int i = 0; i < loadedAssets.size(); i++) {
                            if (loadedAssets.get(i).id == targetAssetId) {
                                spAsset.setSelection(i);
                                break;
                            }
                        }
                    });
                });
            } else {
                spAsset.setVisibility(View.GONE);
            }

            btnSave.setOnClickListener(v -> {
                try {
                    double finalAmountValue = Double.parseDouble(etAmount.getText().toString());
                    String finalNoteText = etNote.getText().toString();
                    String finalRemarkText = etRemark.getText().toString().trim();
                    int finalTypeInt = (rgType.getCheckedRadioButtonId() == R.id.rb_window_income) ? 1 : 0;

                    String finalCatName = selectedCategory[0];
                    if ("自定义".equals(finalCatName)) {
                        String customInput = etCategory.getText().toString().trim();
                        finalCatName = !customInput.isEmpty() ? customInput : (finalTypeInt == 1 ? "退款" : "其他");
                    }

                    int assetIdInt = 0;
                    if (config.isAssetsEnabled() && spAsset.getSelectedItemPosition() < loadedAssets.size()) {
                        assetIdInt = loadedAssets.get(spAsset.getSelectedItemPosition()).id;
                    }

                    String finalSymbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

                    // 【修改点 B】：点击保存时，将 transactionTime 传给底层数据库
                    saveToDatabase(finalAmountValue, finalTypeInt, finalCatName, selectedSubCategory, finalNoteText, finalRemarkText, assetIdInt, finalSymbol, currentPhotoPath[0], transactionTime);

                    closeWindow(windowManager, floatView);
                    Toast.makeText(this, "已记账", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "金额错误", Toast.LENGTH_SHORT).show();
                }
            });

            btnCancel.setOnClickListener(v -> closeWindow(windowManager, floatView));
            windowManager.addView(floatView, params);

        } catch (Exception e) {
            Log.e("AutoTrackService", "Window show failed", e);
            isWindowShowing = false;
        }
    }
    private void closeWindow(WindowManager wm, View view) {
        try { wm.removeView(view); } catch (Exception e) {}
        finally {
            isWindowShowing = false;
            lastWindowDismissTime = System.currentTimeMillis();
            windowRootView = null;
        }
    }

    // 内部类，用于同时记录数值和识别到的符号
    private static class AmountResult {
        double value;
        String symbol;
        AmountResult(double value, String symbol) {
            this.value = value;
            this.symbol = symbol;
        }
    }
    interface PhotoActionResult {
        void onAction(int type);
    }

    private void showLocalPhotoDialog(Context context, android.widget.FrameLayout root, PhotoActionResult listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_photo_action, root, false);
        View mask = new View(context);
        mask.setBackgroundColor(Color.parseColor("#80000000"));
        mask.setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
        });
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(mask, params);
        android.view.ViewGroup.LayoutParams lp = dialogView.getLayoutParams();
        android.widget.FrameLayout.LayoutParams dialogParams = new android.widget.FrameLayout.LayoutParams(
                lp.width,
                lp.height);
        dialogParams.gravity = Gravity.CENTER;
        root.addView(dialogView, dialogParams);

        dialogView.findViewById(R.id.btn_action_camera).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
            listener.onAction(PhotoActionActivity.ACTION_CAMERA);
        });
        dialogView.findViewById(R.id.btn_action_gallery).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
            listener.onAction(PhotoActionActivity.ACTION_GALLERY);
        });
        dialogView.findViewById(R.id.btn_action_cancel).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
        });
    }

    private void hideWindowAndStartPhotoActivity(int actionType, String uri, String[] currentPhotoPathRef) {
        if (windowRootView != null) windowRootView.setVisibility(View.GONE);
        Intent intent = new Intent(this, PhotoActionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PhotoActionActivity.EXTRA_ACTION_TYPE, actionType);
        if (uri != null) intent.putExtra(PhotoActionActivity.EXTRA_IMAGE_URI, uri);
        intent.putExtra(PhotoActionActivity.EXTRA_RECEIVER, new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (windowRootView != null) windowRootView.setVisibility(View.VISIBLE);
                if (resultCode == 1 && resultData != null) {
                    String resultUri = resultData.getString(PhotoActionActivity.KEY_RESULT_URI);
                    if (currentPhotoPathRef != null) currentPhotoPathRef[0] = resultUri;
                    if (resultUri != null && windowRootView != null) {
                        Button btnView = windowRootView.findViewById(R.id.btn_window_view_photo);
                        if (btnView != null) btnView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        startActivity(intent);
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

    private void showSubCategoryDialog(Context context, String parentCategory, CategoryAdapter adapter) {
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
            int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] { } };
            ColorStateList bgStateList = new ColorStateList(states, new int[] { bgChecked, bgDefault });
            ColorStateList textStateList = new ColorStateList(states, new int[] { textChecked, textDefault });
            for (String subCatName : subCats) {
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
                chip.setOnClickListener(v -> {
                    if (subCatName.equals(selectedSubCategory)) {
                        selectedSubCategory = null;
                        Toast.makeText(this, "已取消细分", Toast.LENGTH_SHORT).show();
                    } else {
                        selectedSubCategory = subCatName;
                        Toast.makeText(this, "已选择: " + subCatName, Toast.LENGTH_SHORT).show();
                    }
                    if (adapter != null) adapter.setSelectedCategory(parentCategory);
                    subCatDialog.dismiss();
                });
                cgSubCategories.addView(chip);
            }
        }
        btnCancel.setOnClickListener(v -> subCatDialog.dismiss());
        subCatDialog.show();
    }


    // 1. 新增的兼容方法（供其他实时记账功能使用，默认当前时间）
    private void saveToDatabase(double amount, int type, String category, String subCategory, String note, String remark, int assetId, String currencySymbol, String photoPath) {
        saveToDatabase(amount, type, category, subCategory, note, remark, assetId, currencySymbol, photoPath, System.currentTimeMillis());
    }

    // 2. 修改现有的 saveToDatabase 方法（增加 long transactionTime 参数）
    private void saveToDatabase(double amount, int type, String category, String subCategory, String note, String remark, int assetId, String currencySymbol, String photoPath, long transactionTime) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());

            Transaction t = new Transaction();
            // 【关键修改】：使用传入的真实账单时间，不再使用 System.currentTimeMillis()
            t.date = transactionTime;
            t.type = type;
            t.category = category;
            t.subCategory = subCategory;
            t.amount = amount;
            t.note = note;
            t.remark = remark;
            t.assetId = assetId;
            t.currencySymbol = currencySymbol;
            t.photoPath = photoPath;
            dao.insert(t);

            // ... 下面的资产扣除逻辑保持不变 ...
            if (assetId != 0) {
                AssetAccount asset = db.assetAccountDao().getAssetByIdSync(assetId);
                if (asset != null) {
                    if (asset.type == 0) {
                        if (type == 1) asset.amount += amount;
                        else asset.amount -= amount;
                    } else if (asset.type == 1) {
                        if (type == 1) asset.amount -= amount;
                        else asset.amount += amount;
                    }
                    db.assetAccountDao().update(asset);
                }
            }
        });
    }

    /**
     * 专门适配支付宝“账单详情”页面
     * 提取实际的交易时间、收款方全称，并自动识别支出/收入，将账单记录在实际发生的时间
     */
    /**
     * 专门适配支付宝“账单详情”页面 (包含常规账单与免密支付/0元账单)
     * 提取实际的交易时间、收款方全称，并自动识别支出/收入，将账单记录在实际发生的时间
     */
    private boolean handleAlipayBillDetailPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isBillDetail = false;
        String merchantInfo = "";
        String timeString = "";
        double amount = -1;
        int type = 0; // 默认 0 为支出，1 为收入

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";

            // 合并判断，防止某些奇怪情况节点文字在 Desc 中
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面特征：包含“账单详情”或“订单号”或“交易详情”
            if ("账单详情".equals(content) || "商家订单号".equals(content) || "订单号".equals(content) || "交易详情".equals(content)) {
                isBillDetail = true;
            }

            // 2. 提取金额和收支类型 (例如："支出21.9元", "收入100.00元", "0元")
            if (content.startsWith("支出") && content.endsWith("元")) {
                try {
                    String cleanAmount = content.replace("支出", "").replace("元", "").replace(",", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                    type = 0; // 0 代表支出
                } catch (Exception e) {}
            } else if (content.startsWith("收入") && content.endsWith("元")) {
                try {
                    String cleanAmount = content.replace("收入", "").replace("元", "").replace(",", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                    type = 1; // 1 代表收入
                } catch (Exception e) {}
            } else if (content.matches("^[+-]?\\d+(\\.\\d+)?元$") && amount == -1) {
                // 适配 "0元", "1.5元" 这种纯数字+元的格式 (免密支付等场景)
                try {
                    String cleanAmount = content.replace("元", "").replace("+", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                    type = content.startsWith("+") ? 1 : 0;
                    if (amount < 0) {
                        amount = Math.abs(amount);
                        type = 0;
                    }
                } catch (Exception e) {}
            }

            // 3. 提取商户全称 / 收款方全称 / 免密支付备注 (紧跟在对应标签后的节点)
            if ("收款方全称".equals(content) || "商品说明".equals(content) || "管理自动扣款".equals(content)) {
                // 向下找第一个非空节点
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        merchantInfo = nextContent;
                        break;
                    }
                }
            }

            // 兜底补充：如果直接扫到了带“免密支付”字样的按钮，且还没抓到商户名，也可直接用作备注
            if (content.endsWith("免密支付") && merchantInfo.isEmpty()) {
                merchantInfo = content;
            }

            // 4. 提取支付时间 (格式如：2026-03-20 21:32:12)
            if ("支付时间".equals(content) || "创建时间".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    // 过滤掉空节点，拿到真实的时间字符串
                    if (!nextContent.isEmpty()) {
                        timeString = nextContent;
                        break;
                    }
                }
            }
        }

        // 判定：确认是账单详情页且金额提取成功 (改为 amount >= 0，以便允许 0元 记账)
        if (isBillDetail && amount >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底默认值
            if (merchantInfo.isEmpty()) merchantInfo = "支付宝账单";

            // 防重复录入签名
            String signature = "alipay_bill-" + amount + "-" + merchantInfo + "-" + timeString;
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 解析抓取到的支付宝真实时间，计算真实的时间戳
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    // 按照支付宝的时间格式解析 "yyyy-MM-dd HH:mm:ss"
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        parsedTimestamp = date.getTime();

                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {}
            }

            // 如果没抓到时间或解析失败，降级使用当前时间
            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new Date(now));
            }

            // 构造记录标识，如："03-20 21:32 广东泓铖新能源科技有限公司免密支付"
            final String recordIdentifier = displayTime + " " + merchantInfo;

            // 解决 Lambda 变量 final 限制
            final double finalAmount = amount;
            final int finalType = type;
            final long finalTimestamp = parsedTimestamp;

            final String defaultCategory = (finalType == 0) ? "购物" : "支付宝";

            // 自动匹配资产
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.eg.android.AlipayGphone", "账单详情");

            // 触发记账确认窗口
            handler.post(() -> showConfirmWindow(finalAmount, finalType, defaultCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    // ================= 微信适配测试代码 开始 =================
    private void debugWeChatNodeTree(AccessibilityNodeInfo root) {
        if (root == null) return;
        // 确保只处理微信
        if (root.getPackageName() == null || !"com.tencent.mm".equals(root.getPackageName().toString())) {
            return;
        }

        Log.d("WeChatDebug", "========== 开始打印微信节点树 ==========");
        printNodeRecursive(root, 0);
        Log.d("WeChatDebug", "========== 结束打印微信节点树 ==========");
    }

    private void printNodeRecursive(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;

        // 生成缩进以体现层级关系
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("----");
        }

        // 提取节点关键信息
        String text = node.getText() != null ? node.getText().toString() : "null";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "null";
        String className = node.getClassName() != null ? node.getClassName().toString() : "null";
        String viewId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "null";

        // 过滤掉完全没有实质内容的布局节点，减少日志噪音（可视情况注释掉这一步）
        if (!"null".equals(text) || !"null".equals(desc) || !"null".equals(viewId)) {
            Log.d("WeChatDebug", indent.toString()
                    + " Class: " + className.substring(className.lastIndexOf('.') + 1) // 简写类名
                    + " | Text: [" + text + "]"
                    + " | Desc: [" + desc + "]"
                    + " | ViewId: " + viewId);
        }

        // 递归遍历子节点
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            printNodeRecursive(node.getChild(i), depth + 1);
        }
    }
    // ================= 微信适配测试代码 结束 =================

    // 展平节点树，方便我们通过前后节点关系寻找数据（例如找 "元" 前面的数字）
    private void flattenNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) {
        if (node == null) return;
        list.add(node);
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            flattenNodes(node.getChild(i), list);
        }
    }

    /**
     * 专门适配微信红包领取详情页
     * 识别“已存入零钱”特征并自动记账，分类自动设为“红包”
     */
    private boolean handleWeChatRedPacketPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isRedPacketPage = false;
        String redPacketName = "微信红包";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

            // 1. 识别红包领取成功的核心标志
            if (desc.contains("已存入零钱")) {
                isRedPacketPage = true;
            }

            // 2. 提取“xxx的红包”作为标识
            if (text.endsWith("的红包")) {
                redPacketName = text;
            }

            // 3. 提取金额（寻找“元”字前面的数字节点）
            if ("元".equals(text) && i > 0) {
                AccessibilityNodeInfo prevNode = allNodes.get(i - 1);
                if (prevNode.getText() != null) {
                    try {
                        amount = Double.parseDouble(prevNode.getText().toString());
                    } catch (Exception e) {
                        // 忽略解析错误
                    }
                }
            }
        }

        // 判定：如果是收红包页面且成功识别到金额
        if (isRedPacketPage && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 解决 Lambda 变量 final 要求
            final double finalAmount = amount;
            final String finalIdentifier = redPacketName;

            String signature = amount + "-1-" + finalIdentifier; // type=1 为收入
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识：时间 + 红包名 (如: 03-19 14:35 丰的红包)
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String timeNote = sdf.format(new Date(now)) + " " + finalIdentifier;

            // 自动匹配资产账户
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", "红包已存入零钱");

            // 【核心修改点】：将分类参数从 "微信" 改为 "红包"
            // type=1 代表收入
            handler.post(() -> showConfirmWindow(finalAmount, 1, "红包", timeNote, autoAssetId, "¥"));

            return true;
        }

        return false;
    }

    /**
     * 专门适配微信支付成功页面
     * 提取商户/收款人信息作为记录标识
     */
    private boolean handleWeChatPaySuccessPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaySuccessPage = false;
        String merchantInfo = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";

            // 1. 识别“支付成功”标志
            if ("支付成功".equals(text) || "支付成功".equals(desc)) {
                isPaySuccessPage = true;

                // 2. 提取商户或收款人名称
                // 向后搜索第一个符合条件的文本节点
                if (merchantInfo.isEmpty()) {
                    for (int j = i + 1; j < allNodes.size(); j++) {
                        AccessibilityNodeInfo nextNode = allNodes.get(j);
                        String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";

                        // 排除空节点、重复的"支付成功"，以及带金钱符号的金额节点
                        if (!nextText.isEmpty() && !"支付成功".equals(nextText)
                                && !nextText.contains("¥") && !nextText.contains("￥")) {
                            merchantInfo = nextText; // 这里将完美抓取到“广东轻工职业技术大学”
                            break;
                        }
                    }
                }
            }

            // 3. 提取金额（兼容日志中的全角 ￥ 和半角 ¥）
            if (text.contains("¥") || text.contains("￥")) {
                try {
                    String cleanAmount = text.replace("¥", "").replace("￥", "").replace(",", "").trim();
                    double parsedAmount = Double.parseDouble(cleanAmount);
                    if (parsedAmount > 0) {
                        amount = parsedAmount;
                    }
                } catch (Exception e) {
                    // 解析失败则忽略，继续寻找下一个
                }
            }
        }

        // 4. 判定：确认是支付成功页且识别到合法金额
        if (isPaySuccessPage && amount > 0) {
            long now = System.currentTimeMillis();
            // 防抖
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底值
            if (merchantInfo.isEmpty()) merchantInfo = "微信支付";

            // 防重复录入标识
            String signature = amount + "-0-" + merchantInfo;
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 【核心处理】：解决 Lambda 变量 final 限制，并构造记录标识
            final double finalAmount = amount;

            // 拼接时间戳与商户名，如：03-20 17:59 广东轻工职业技术大学
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + merchantInfo;

            // 自动匹配资产账户
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", "支付成功");

            // 触发记账弹窗（type=0 为支出，默认分类这里设为“购物”或“餐饮”）
            handler.post(() -> showConfirmWindow(finalAmount, 0, "购物", recordIdentifier, autoAssetId, "¥"));

            return true;
        }

        return false;
    }
    /**
     * 专门适配微信“待确认收款”页面
     * 提取“待xxx确认收款”作为记录标识
     */
    private boolean handleWeChatTransferPendingPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaySuccessPage = false;
        String pendingInfo = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

            // 1. 识别“支付成功”顶部标志
            if ("支付成功".equals(text) || "支付成功".equals(desc)) {
                isPaySuccessPage = true;
            }

            // 2. 捕捉关键节点：待xxxx确认收款
            if (text.contains("确认收款")) {
                pendingInfo = text;
            }

            // 3. 提取金额（格式通常为 ￥0.01）
            if (text.contains("￥")) {
                try {
                    // 兼容中文全角 ￥ 和英文半角 ¥
                    String cleanAmount = text.replace("￥", "").replace("¥", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                } catch (Exception e) {
                    // 解析失败继续寻找
                }
            }
        }

        // 判定：如果是支付成功页，且识别到了“待确认”信息和金额
        if (isPaySuccessPage && amount > 0 && !pendingInfo.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 解决 Lambda 变量 final 要求
            final double finalAmount = amount;
            final String finalInfo = pendingInfo;

            String signature = amount + "-0-" + finalInfo;
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识：时间 + 待陈勇13929622781确认收款
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + finalInfo;

            // 触发记账（支出类型 0，分类默认“转账”或“其他”）
            handler.post(() -> showConfirmWindow(finalAmount, 0, "转账", recordIdentifier, 0, "¥"));

            return true;
        }

        return false;
    }

    /**
     * 专门适配微信“账单详情”页面
     * 提取实际的交易时间、商户全称（或收款人），并自动识别支出/收入，将账单记录在实际发生的时间
     */
    private boolean handleWeChatBillDetailPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isBillDetail = false;
        String merchantInfo = "";
        String timeString = "";
        double amount = -1;
        int type = 0; // 默认 0 为支出，1 为收入

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";

            // 1. 识别页面特征：包含“交易单号”或“商户单号”或“账单服务”
            if ("交易单号".equals(text) || "商户单号".equals(text) || "经营单号".equals(text)) {
                isBillDetail = true;
            }

            // 2. 提取商户全称（兼容带有明确标签的老版本或特定商家账单）
            if ("商户全称".equals(text) || "收款方".equals(text)) {
                if (i + 1 < allNodes.size()) {
                    AccessibilityNodeInfo nextNode = allNodes.get(i + 1);
                    if (nextNode.getText() != null) {
                        merchantInfo = nextNode.getText().toString().trim();
                    }
                }
            }

            // 3. 提取支付时间 (例如：2026年3月10日 16:42:42)
            if ("支付时间".equals(text) || "转账时间".equals(text) || "创建时间".equals(text)) {
                if (i + 1 < allNodes.size()) {
                    AccessibilityNodeInfo nextNode = allNodes.get(i + 1);
                    if (nextNode.getText() != null) {
                        timeString = nextNode.getText().toString().trim();
                    }
                }
            }

            // 4. 提取金额并判断收支 (正则匹配带正负号的金额，如 "-10.00" 或 "+10.00")
            if (text.matches("^[+-]?\\d+\\.\\d{2}$")) {
                try {
                    double parsedAmount = Double.parseDouble(text);
                    if (parsedAmount < 0) {
                        type = 0; // 负数代表支出
                        amount = Math.abs(parsedAmount);
                    } else if (parsedAmount > 0) {
                        type = 1; // 正数代表收入
                        amount = parsedAmount;
                    }

                    // 【新增绝杀逻辑】：由于“淡然”这种个人转账没有“收款方”标签，
                    // 但我们发现收款人名字永远出现在金额（-10.00）的上一个节点！
                    if (merchantInfo.isEmpty() && i > 0) {
                        AccessibilityNodeInfo prevNode = allNodes.get(i - 1);
                        if (prevNode.getText() != null) {
                            String prevText = prevNode.getText().toString().trim();
                            // 排除掉由于外层容器组合文字带来的干扰（如 "淡然,支出10.00元"）
                            if (!prevText.isEmpty() && !prevText.contains("支出") && !prevText.contains("收入")) {
                                merchantInfo = prevText;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 解析失败忽略
                }
            }
        }

        // 判定：确认是账单详情页且金额提取成功
        if (isBillDetail && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底：如果上面的多重提取还是没抓到，才使用默认文字
            if (merchantInfo.isEmpty()) merchantInfo = "微信账单";

            // 防止在同一个页面停留时疯狂弹窗
            String signature = "bill-" + amount + "-" + merchantInfo + "-" + timeString;
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 【核心逻辑】：解析抓取到的真实时间，计算真实的时间戳
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    // 按照微信时间格式解析
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault());
                    Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        // 【关键】：提取真实账单时间戳，以便插入到对应的历史日期
                        parsedTimestamp = date.getTime();
                        // 生成弹窗第一栏显示的格式
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {
                    // 解析失败时的降级方案
                }
            }

            // 如果没抓到时间或解析失败，降级使用当前时间
            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new Date(now));
            }

            // 构造记录标识，如："03-10 16:42 淡然"
            final String recordIdentifier = displayTime + " " + merchantInfo;

            // 解决 Lambda 变量 final 限制
            final double finalAmount = amount;
            final int finalType = type;
            final long finalTimestamp = parsedTimestamp;

            // 动态选择默认分类：如果是支出为"转账"或"购物"，这里默认给个"转账"，因为通常没有“商户全称”的都是个人转账
            final String defaultCategory = (finalType == 0) ? "转账" : "微信";

            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", "账单详情");

            // 弹出记账确认窗口（将真实的时间戳 finalTimestamp 传给底层）
            handler.post(() -> showConfirmWindow(finalAmount, finalType, defaultCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 专门适配微信“支付确认”弹窗（输入密码前）
     * 提取商户名、金额，并将“付款方式”（如：零钱）作为关联资产的模糊搜索关键词
     */
    private boolean handleWeChatPaymentConfirmPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaymentConfirm = false;
        String merchantInfo = "";
        String paymentMethod = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";

            // 1. 识别页面特征：包含“付款方式”文本
            if ("付款方式".equals(text)) {
                isPaymentConfirm = true;
            }

            // 2. 提取付款方式（资产名称），规律：紧跟在“更改”节点之后
            if ("更改".equals(text)) {
                if (i + 1 < allNodes.size()) {
                    AccessibilityNodeInfo nextNode = allNodes.get(i + 1);
                    if (nextNode.getText() != null) {
                        paymentMethod = nextNode.getText().toString().trim();
                    }
                }
            }

            // 3. 提取金额和商户名
            if (text.contains("￥") || text.contains("¥")) {
                try {
                    String cleanAmount = text.replace("￥", "").replace("¥", "").replace(",", "").trim();
                    double parsedAmount = Double.parseDouble(cleanAmount);

                    // 确保金额合法，并且只抓取一次
                    if (parsedAmount > 0 && amount == -1) {
                        amount = parsedAmount;

                        // 4. 商户名（或交易标题）通常在金额的上一个节点
                        if (i > 0) {
                            AccessibilityNodeInfo prevNode = allNodes.get(i - 1);
                            if (prevNode.getText() != null) {
                                merchantInfo = prevNode.getText().toString().trim();
                            }
                        }
                    }
                } catch (Exception e) {
                    // 解析失败忽略
                }
            }
        }

        // 判定：确认是支付确认页且金额提取成功
        if (isPaymentConfirm && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底值
            if (merchantInfo.isEmpty()) merchantInfo = "微信支付";

            // 防止在这个界面停留时重复弹出
            String signature = "confirm-" + amount + "-" + merchantInfo;
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识，如："03-20 18:30 微信红包"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + merchantInfo;

            // 解决 Lambda 变量 final 限制
            final double finalAmount = amount;

            // 【核心资产匹配】：使用提取到的付款方式（如“零钱”）去模糊匹配资产
            String assetKeyword = paymentMethod.isEmpty() ? "微信支付" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", assetKeyword);

            // 弹出记账确认窗口（type=0 为支出，默认分类这里设为“购物”）
            handler.post(() -> showConfirmWindow(finalAmount, 0, "购物", recordIdentifier, autoAssetId, "¥"));

            return true;
        }

        return false;
    }

    /**
     * 专门适配微信发红包时的“支付确认”弹窗页面
     * 稳健版：锁定 "付款方式" 和 "微信红包"，全局提取金额
     */
    private boolean handleWeChatRedPacketSpecialPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean hasWeChatRedPacket = false;
        boolean hasPaymentMethod = false;   // 核心页面特征锁
        double amount = -1;
        String assetName = "微信支付"; // 默认资产

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";

            // 页面特征锁：必须存在 "付款方式" 文本（这是支付确认弹窗最稳定的标志）
            if ("付款方式".equals(text) || "付款方式".equals(desc)) {
                hasPaymentMethod = true;
            }

            // 核心触发节点：严格匹配 "微信红包"
            if ("微信红包".equals(text) || "微信红包".equals(desc)) {
                hasWeChatRedPacket = true;
            }

            // 独立提取金额：不再强求必须紧跟在"微信红包"后面，只要页面上有合法的 ￥ 金额即可
            if (text.startsWith("￥") || text.startsWith("¥")) {
                try {
                    String cleanAmount = text.replace("￥", "").replace("¥", "").trim();
                    double parsed = Double.parseDouble(cleanAmount);
                    if (parsed > 0 && amount == -1) {
                        amount = parsed;
                    }
                } catch (Exception e) {
                    // 解析失败忽略
                }
            }

            // 提取付款方式（如 "零钱"），它通常紧跟在 "更改" 节点之后
            if ("更改".equals(text) || "更改".equals(desc)) {
                if (i + 1 < allNodes.size()) {
                    AccessibilityNodeInfo nextNode = allNodes.get(i + 1);
                    if (nextNode.getText() != null && !nextNode.getText().toString().trim().isEmpty()) {
                        assetName = nextNode.getText().toString().trim(); // 这里会抓取到 "零钱" 等资产
                    }
                }
            }
        }

        // 【触发条件】：只要是支付弹窗(有付款方式)，且是发红包(有微信红包)，且抓到了金额，就触发
        if (hasPaymentMethod && hasWeChatRedPacket && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 防抖签名
            String signature = "confirm-" + amount + "-0-微信红包";
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识，替换 "auto" 为 "微信红包"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String timeNote = sdf.format(new Date(now)) + " 微信红包";

            final double finalAmount = amount;

            // 自动匹配资产
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", assetName);

            // 触发记账弹窗（type: 0 代表支出，分类默认给 "红包"）
            handler.post(() -> showConfirmWindow(finalAmount, 0, "红包", timeNote, autoAssetId, "¥"));

            return true;
        }

        return false;
    }


    // ================= 支付宝适配测试代码 开始 =================
    private void debugAlipayNodeTree(AccessibilityNodeInfo root) {
        if (root == null) return;
        // 校验是否为支付宝页面
        if (root.getPackageName() == null || !"com.eg.android.AlipayGphone".equals(root.getPackageName().toString())) {
            return;
        }

        Log.d("AlipayDebug", "========== 开始打印支付宝节点树 ==========");
        printNodeRecursiveForAlipay(root, 0);
        Log.d("AlipayDebug", "========== 结束打印支付宝节点树 ==========");
    }

    private void printNodeRecursiveForAlipay(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("----");
        }

        String text = node.getText() != null ? node.getText().toString() : "null";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "null";
        String className = node.getClassName() != null ? node.getClassName().toString() : "null";
        String viewId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "null";

        // 过滤无意义的空节点，只打印有信息的节点
        if (!"null".equals(text) || !"null".equals(desc) || !"null".equals(viewId)) {
            Log.d("AlipayDebug", indent.toString()
                    + " Class: " + className.substring(className.lastIndexOf('.') + 1)
                    + " | Text: [" + text + "]"
                    + " | Desc: [" + desc + "]"
                    + " | ViewId: " + viewId);
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            printNodeRecursiveForAlipay(node.getChild(i), depth + 1);
        }
    }

    /**
     * 专门适配第三方应用/小程序调用微信支付后的“支付成功”页面（带有返回商家按钮）
     * 针对该页面信息全在 ContentDescription (Desc) 中的特征进行抓取
     */
    private boolean handleWeChatMerchantAppPaySuccessPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaySuccessPage = false;
        String merchantName = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);

            // 针对该界面，核心数据在 ContentDescription 中
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";

            // 优先使用 desc，若无则降级看 text
            String content = !desc.isEmpty() ? desc : text;

            // 1. 识别“支付成功”标志
            if ("支付成功".equals(content)) {
                isPaySuccessPage = true;
            }

            // 2. 提取金额（寻找带有 ￥ 或 ¥ 的节点）
            if (content.contains("￥") || content.contains("¥")) {
                try {
                    String cleanAmount = content.replace("￥", "").replace("¥", "").replace(",", "").trim();
                    double parsedAmount = Double.parseDouble(cleanAmount);

                    // 确保金额有效，且只抓取第一次出现的金额
                    if (parsedAmount > 0 && amount == -1) {
                        amount = parsedAmount;

                        // 3. 提取商户名（商户名称节点通常紧挨在金额节点的正上方）
                        if (i > 0) {
                            AccessibilityNodeInfo prevNode = allNodes.get(i - 1);
                            String prevDesc = prevNode.getContentDescription() != null ? prevNode.getContentDescription().toString().trim() : "";
                            String prevText = prevNode.getText() != null ? prevNode.getText().toString().trim() : "";
                            String prevContent = !prevDesc.isEmpty() ? prevDesc : prevText;

                            // 排除掉“支付成功”这个标题节点，剩下的就是商户名
                            if (!prevContent.isEmpty() && !"支付成功".equals(prevContent)) {
                                merchantName = prevContent;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 解析失败忽略
                }
            }
        }

        // 4. 判定：如果是支付成功页，且成功抓取到金额
        if (isPaySuccessPage && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            if (merchantName.isEmpty()) merchantName = "微信支付";

            // 防抖，防止界面停留时重复弹窗
            String signature = "merchant_app_pay-" + amount + "-" + merchantName;
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识，例如："03-21 18:18 美团"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + merchantName;

            final double finalAmount = amount;

            // 如果商户名包含"美团"，自动将默认分类设为"餐饮"，否则设为"购物"
            final String defaultCategory = merchantName.contains("美团") ? "餐饮" : "购物";

            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", "支付成功");

            // 触发记账窗口（type: 0 代表支出，note 替换为 recordIdentifier）
            handler.post(() -> showConfirmWindow(finalAmount, 0, defaultCategory, recordIdentifier, autoAssetId, "¥"));

            return true;
        }

        return false;
    }

    // ================= 支付宝适配测试代码 结束 =================

    /**
     * 专门适配支付宝支付成功页面
     * 提取收款人（如 *勇）作为记录标识
     */
    private boolean handleAlipayPaySuccessPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaySuccess = false;
        String payeeName = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

            // 1. 识别“支付成功”页面特征
            if (text.equals("支付成功") || desc.contains("支付成功")) {
                isPaySuccess = true;
            }

            // 2. 提取备注（收款人）：寻找类似 [*勇] 的节点
            // 根据日志，它通常在“支付成功”和“金额”之间，且不含货币符号
            if (isPaySuccess && payeeName.isEmpty()) {
                if (!text.isEmpty() && !text.equals("支付成功") && !text.contains("￥")
                        && !text.contains("¥") && !text.equals("完成") && !text.equals("回首页")) {
                    // 排除掉纯数字或类似 0.00 的干扰项
                    if (!text.matches("\\d+\\.\\d+")) {
                        payeeName = text;
                    }
                }
            }

            // 3. 提取实际支付金额（￥0.01）
            // 注意：排除掉带负号的优惠金额（-￥0.01）
            if (text.contains("￥") || text.contains("¥")) {
                if (!text.startsWith("-")) {
                    try {
                        String cleanAmount = text.replace("￥", "").replace("¥", "").trim();
                        double parsed = Double.parseDouble(cleanAmount);
                        if (parsed > 0) {
                            amount = parsed;
                        }
                    } catch (Exception e) {
                        // 解析失败继续找
                    }
                }
            }
        }

        // 判定：识别到支付成功且金额合法
        if (isPaySuccess && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 确定备注内容
            final String finalPayee = payeeName.isEmpty() ? "支付宝支付" : payeeName;
            final double finalAmount = amount;

            // 防止重复触发
            String signature = amount + "-0-" + finalPayee;
            if (now - lastRecordTime < 5000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 【核心修改】：构造记录标识，替换 "auto"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + finalPayee;

            // 自动匹配支付宝相关的资产账户
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.eg.android.AlipayGphone", "支付成功");

            // 触发记账弹窗 (type: 0 支出)
            handler.post(() -> showConfirmWindow(finalAmount, 0, "购物", recordIdentifier, autoAssetId, "¥"));

            return true;
        }

        return false;
    }

    @Override public void onInterrupt() {}
}