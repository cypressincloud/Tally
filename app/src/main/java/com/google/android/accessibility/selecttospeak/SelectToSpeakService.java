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
                    // 传入 this 作为上下文去读取本地状态
                    if (com.example.budgetapp.util.AutoTrackLogManager.isLogEnabled(SelectToSpeakService.this)) {
                        String appName = getAppNameReadable(packageName);
                        com.example.budgetapp.util.AutoTrackLogManager.addLog(packageName, "►►► 捕获到 [" + appName + "] 页面刷新 ◄◄◄");
                        printNodeToManager(rootNode, 0, packageName);

                        if (packageName != null && !packageName.isEmpty() && !packageName.equals("com.example.budgetapp")) {
                            if (com.example.budgetapp.util.AutoTrackLogManager.isLogEnabled(SelectToSpeakService.this)) {
// 【核心修复】：在抓取并写入新日志前，必须先从本地加载历史记录，防止新数据覆盖清空历史！
                                com.example.budgetapp.util.AutoTrackLogManager.loadLogsIfNeeded(SelectToSpeakService.this);

                                String logAppName = getAppNameReadable(packageName);
                                com.example.budgetapp.util.AutoTrackLogManager.addLog(packageName, "►►► 捕获到 [" + logAppName + "] 页面刷新 ◄◄◄");
                                printNodeToManager(rootNode, 0, packageName);

                                // 【新增】：整棵节点树扫描并记录完毕后，统一打包保存到本地存储
                                com.example.budgetapp.util.AutoTrackLogManager.saveLogsToDisk(SelectToSpeakService.this);
                            }
                        }
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
                    // 0. 新增：【专版专杀】支付宝个人转账账单详情页 (优先级最高)
                    if (handleAlipayTransferBillDetailPage(rootNode)) return;

                    // 1. 新增：优先适配支付宝历史账单详情页面 / 免密支付页面（支持同步历史时间）
                    if (handleAlipayBillDetailPage(rootNode)) return;

                    // 2. 尝试适配支付宝刚支付成功的页面
                    if (handleAlipayPaySuccessPage(rootNode)) return;
                }
                // ============================

                // ======= 微信专属页面拦截 =======
                if ("com.tencent.mm".equals(packageName)) {
                    // 0. 专门适配微信红包/支付特殊页面
                    if (handleWeChatRedPacketSpecialPage(rootNode)) return;

                    //    【新增】微信个人转账“已收款”页面适配
                    if (handleWeChatTransferReceivedPage(rootNode)) return;

                    // 1. 【防误杀：必须排在绝对第一位】待确认收款页面适配
                    if (handleWeChatTransferPendingPage(rootNode)) return;

                    // 2. 微信扫二维码付款 / 个人转账账单详情
                    if (handleWeChatQRCodeTransferPage(rootNode)) return;

                    // 3. 适配微信内第三方小程序/服务商的支付成功页 (全 Desc 结构)
                    if (handleWeChatMerchantAppPaySuccessPage(rootNode)) return;

                    // 4. 优先尝试适配红包页面
                    if (handleWeChatRedPacketPage(rootNode)) return;

                    // 5. 尝试适配常规支付成功页面
                    if (handleWeChatPaySuccessPage(rootNode)) return;

                    // 6. 微信商家转账 / 提现 / 退款页面
                    if (handleWeChatMerchantTransferPage(rootNode)) return;

                    // 7. 普通历史账单详情页
                    if (handleWeChatBillDetailPage(rootNode)) return;
                }
                // ==============================

                // ======= 拼多多专属逻辑 =======
                if ("com.xunmeng.pinduoduo".equals(packageName)) {
                    // 1. 【新增】适配拼多多订单详情/支付成功页
                    if (handlePinduoduoOrderDetailPage(rootNode)) return;

                    // 2. 适配拼多多多多钱包支付弹窗页面
                    if (handlePinduoduoPaymentPage(rootNode)) return;
                }
                // ============================

                // ======= 抖音专属逻辑 =======
                if ("com.ss.android.ugc.aweme".equals(packageName)) {
                    if (handleDouyinPaymentPage(rootNode)) return;
                }
                // ============================

                // ======= 美团专属逻辑 =======
                if (packageName != null && packageName.contains("meituan")) {
                    if (handleMeituanPaySuccessPage(rootNode)) return;
                }
                // ============================

                // ======= 京东专属逻辑 =======
                if ("com.jingdong.app.mall".equals(packageName)) {
                    if (handleJDPaySuccessPage(rootNode)) return;
                }
                // ============================

                // ======= 通义千问 / AI充值专属逻辑 =======
                if ("com.aliyun.tongyi".equals(packageName)) {
                    if (handleQwenPaySuccessPage(rootNode)) return;
                }

                // ======= 云闪付专属逻辑 =======
                if ("com.unionpay".equals(packageName)) {
                    // 1. 适配刚支付成功的页面
                    if (handleUnionPayPaySuccessPage(rootNode)) return;

                    // 2. 适配历史交易详情页面
                    if (handleUnionPayBillDetailPage(rootNode)) return;
                }

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
        if (packageName == null) return "自动记账";
        String pkg = packageName.toLowerCase();
        if (pkg.contains("tencent.mm")) return "微信";
        if (pkg.contains("alipay")) return "支付宝";
        if (pkg.contains("taobao")) return "淘宝";
        if (pkg.equals("com.jingdong.app.mall")) return "京东"; // 【修改】：使用真实的京东全包名
        if (pkg.contains("pinduoduo")) return "拼多多";
        if (pkg.equals("com.ss.android.ugc.aweme")) return "抖音";
        if (pkg.contains("meituan")) return "美团";
        if (pkg.equals("com.aliyun.tongyi")) return "通义千问";
        if (pkg.equals("com.unionpay")) return "云闪付";
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

            // 加上全屏文本指纹，防止不同页面但金额相同的账单被误杀
            String signature = finalAmount + "-" + finalType + "-" + fullText.hashCode();
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return;

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
     * 专门适配支付宝“账单详情”页面 (包含常规账单与免密支付/外卖账单)
     * 提取实际的交易时间、商品说明/收款方、付款方式(资产)，并自动识别支出/收入，将账单记录在实际发生的时间
     */
    private boolean handleAlipayBillDetailPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isBillDetail = false;
        String merchantInfo = "";
        String timeString = "";
        String paymentMethod = "";
        double amount = -1;
        int type = 0; // 默认 0 为支出，1 为收入

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面特征
            if ("账单详情".equals(content) || "商家订单号".equals(content) || "订单号".equals(content) || "交易详情".equals(content)) {
                isBillDetail = true;
            }

            // 2. 提取金额和收支类型 (例如："支出19.3元")
            if (content.startsWith("支出") && content.endsWith("元")) {
                try {
                    String cleanAmount = content.replace("支出", "").replace("元", "").replace(",", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                    type = 0;
                } catch (Exception e) {}
            } else if (content.startsWith("收入") && content.endsWith("元")) {
                try {
                    String cleanAmount = content.replace("收入", "").replace("元", "").replace(",", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                    type = 1;
                } catch (Exception e) {}
            } else if (content.matches("^[+-]?\\d+(\\.\\d+)?元$") && amount == -1) {
                // 适配 "0元", "1.5元" 这种纯数字+元的格式
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

            // 3. 提取支付方式 (用于模糊匹配资产)
            if ("付款方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }

            // 4. 提取商品说明 或 收款方全称 (商品说明的优先级更高，因为它包含点外卖的详细菜品)
            if ("商品说明".equals(content) || "收款方全称".equals(content) || "管理自动扣款".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        // 如果之前抓到了收款方全称(如北京三快...)，现在又遇到了商品说明(隆江猪脚饭...)，我们优先使用商品说明
                        if (merchantInfo.isEmpty() || "商品说明".equals(content)) {
                            merchantInfo = nextContent;
                        }
                        break;
                    }
                }
            }

            // 5. 提取支付时间 (格式如：2026-03-27 13:33:54)
            if ("支付时间".equals(content) || "创建时间".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        timeString = nextContent;
                        break;
                    }
                }
            }

            // 兜底：免密支付按钮
            if (content.endsWith("免密支付") && merchantInfo.isEmpty()) {
                merchantInfo = content;
            }
        }

        // 判定：确认是账单详情页且金额提取成功
        if (isBillDetail && amount >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底默认值
            if (merchantInfo.isEmpty()) merchantInfo = "支付宝账单";

            // 防重复录入签名
            String signature = "alipay_bill-" + amount + "-" + merchantInfo + "-" + timeString;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 解析抓取到的支付宝真实时间，计算真实的时间戳
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    // 支付宝的时间格式是 "yyyy-MM-dd HH:mm:ss"
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    java.util.Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        parsedTimestamp = date.getTime();
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {}
            }

            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new java.util.Date(now));
            }

            // 构造记录标识，如："03-27 13:33 隆江猪脚饭（塘联店）..."
            final String recordIdentifier = displayTime + " " + merchantInfo;

            final double finalAmount = amount;
            final int finalType = type;
            final long finalTimestamp = parsedTimestamp;

            // 智能选择默认分类
            String defaultCategory = (finalType == 0) ? "购物" : "支付宝";
            if (merchantInfo.contains("美团") || merchantInfo.contains("饿了么") || merchantInfo.contains("餐饮") || merchantInfo.contains("饭")) {
                defaultCategory = "餐饮";
            }

            // 自动匹配资产 (使用提取到的“中国银行储蓄卡(4260)”等进行模糊匹配)
            String assetKeyword = paymentMethod.isEmpty() ? "支付宝" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.eg.android.AlipayGphone", assetKeyword);

            // 触发记账确认窗口
            String finalDefaultCategory = defaultCategory;
            handler.post(() -> showConfirmWindow(finalAmount, finalType, finalDefaultCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

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
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

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
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

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
     * 【专版专杀】专门适配微信“待确认收款”页面
     * 提取“待xxx确认收款”作为记录标识，并自动匹配微信资产
     */
    private boolean handleWeChatTransferPendingPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPendingPage = false;
        String pendingInfo = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 捕捉核心特征节点：只要包含"待"和"确认收款"即锁定该页面
            if (content.contains("待") && content.contains("确认收款")) {
                isPendingPage = true;
                pendingInfo = content;
            }

            // 2. 提取金额（格式通常为 ￥0.01）
            if ((content.contains("￥") || content.contains("¥")) && amount == -1) {
                try {
                    String cleanAmount = content.replace("￥", "").replace("¥", "").replace(",", "").trim();
                    double parsed = Double.parseDouble(cleanAmount);
                    if (parsed > 0) {
                        amount = parsed;
                    }
                } catch (Exception e) {
                    // 解析失败继续寻找
                }
            }
        }

        // 3. 判定：只要锁定核心特征且抓到了金额，100%是待确认收款页面
        if (isPendingPage && amount > 0 && !pendingInfo.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 防抖签名：加入 5分钟(300000ms)屏蔽
            String signature = "wx_transfer_pending-" + amount + "-" + pendingInfo;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识：时间 + 待...确认收款
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + pendingInfo;

            final double finalAmount = amount;
            final String finalCategory = "转账"; // 明确为转账行为，默认分类锁定为"转账"

            // 该页面未提供具体银行卡信息，统一使用"微信"作为关键词进行资产模糊匹配
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", "微信");

            // 触发记账（支出类型 0）
            handler.post(() -> showConfirmWindow(finalAmount, 0, finalCategory, recordIdentifier, autoAssetId, "¥"));

            return true;
        }

        return false;
    }

    /**
     * 专门适配微信“普通历史账单详情页”及“各种交易详情变体页”
     * 增加对金额正下方商户名（如拼多多平台商户）的向下嗅探捕获，并过滤无效商品标签
     */
    private boolean handleWeChatBillDetailPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isBillDetailPage = false;
        String note = "";
        String fallbackNote = "";
        String directBelowNote = ""; // 新增：金额正下方的紧邻文本
        String productNote = "";
        String merchantNote = "";
        String timeString = "";
        String paymentMethod = "";
        double amount = -1;
        int type = 0; // 0代表支出, 1代表收入

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面特征
            if ("交易单号".equals(content) || "商户单号".equals(content)) {
                isBillDetailPage = true;
            }

            // 2. 提取真实金额与收支状态
            if (content.matches("^[-+]?\\d+\\.\\d{2}$") && amount == -1) {
                try {
                    double parsedAmount = Double.parseDouble(content);
                    if (parsedAmount < 0) {
                        type = 0;
                        amount = Math.abs(parsedAmount);
                    } else if (parsedAmount > 0) {
                        type = 1;
                        amount = parsedAmount;
                    }

                    // A. 向上寻找兜底备注 (跨越空节点)
                    for (int j = i - 1; j >= 0; j--) {
                        AccessibilityNodeInfo prevNode = allNodes.get(j);
                        String prevText = prevNode.getText() != null ? prevNode.getText().toString().trim() : "";
                        String prevDesc = prevNode.getContentDescription() != null ? prevNode.getContentDescription().toString().trim() : "";
                        String prevContent = !prevText.isEmpty() ? prevText : prevDesc;

                        if (!prevContent.isEmpty() && !prevContent.contains("支出") && !prevContent.contains("收入")) {
                            fallbackNote = prevContent;
                            break;
                        }
                    }

                    // B. 【核心修复】：向下寻找紧跟在金额后面的备注 (如：拼多多平台商户)
                    for (int j = i + 1; j < allNodes.size(); j++) {
                        AccessibilityNodeInfo nextNode = allNodes.get(j);
                        String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                        String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                        String nextContent = !nextText.isEmpty() ? nextText : nextDesc;

                        // 找到第一个有内容的节点，只要不是"原价"、"优惠"等干扰项，就当做商户名
                        if (!nextContent.isEmpty()) {
                            if (!nextContent.contains("原价") && !nextContent.contains("优惠") && !nextContent.contains("￥") && !nextContent.contains("¥")) {
                                directBelowNote = nextContent;
                            }
                            break; // 只看第一个实质节点，看完就撤
                        }
                    }
                } catch (Exception e) {}
            }

            // 3. 定向提取 [商品] 后面的明细
            if ("商品".equals(content) || "商品名称".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        productNote = nextContent;
                        break;
                    }
                }
            }

            // 4. 定向提取 [商户全称]
            if ("商户全称".equals(content) || "收款方".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        merchantNote = nextContent;
                        break;
                    }
                }
            }

            // 5. 提取支付方式
            if ("支付方式".equals(content) || "收款方式".equals(content) || "退款方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }

            // 6. 提取支付时间
            if ("支付时间".equals(content) || "交易时间".equals(content) || "退款时间".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        timeString = nextContent;
                        break;
                    }
                }
            }
        }

        // 7. 最终判定并触发
        if (isBillDetailPage && amount >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 【核心决策树】：层层递进，防忽悠拦截
            // 如果商品备注存在，且不是微信敷衍塞进来的单号，则首选它 (如美团外卖的菜品)
            if (!productNote.isEmpty() && !productNote.startsWith("商户单号") && !productNote.startsWith("交易单号")) {
                note = productNote;
            }
            // 否则，优先选用金额正下方的名称 (如：拼多多平台商户)
            else if (!directBelowNote.isEmpty() && !directBelowNote.contains("交易详情") && !directBelowNote.contains("账单详情")) {
                note = directBelowNote;
            }
            // 兜底方案
            else if (!merchantNote.isEmpty()) {
                note = merchantNote;
            } else if (!fallbackNote.isEmpty() && !fallbackNote.contains("交易详情") && !fallbackNote.contains("账单详情")) {
                note = fallbackNote;
            }

            if (note.isEmpty()) note = "微信账单";
            if (note.length() > 50) note = note.substring(0, 48) + "...";

            // 防重复签名
            String signature = "wx_bill_detail-" + amount + "-" + note + "-" + timeString;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 解析真实的微信时间戳
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault());
                    java.util.Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        parsedTimestamp = date.getTime();
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {}
            }
            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new java.util.Date(now));
            }

            // 构造记录标识
            final String recordIdentifier = displayTime + " " + note;
            final double finalAmount = amount;
            final int finalType = type;
            final long finalTimestamp = parsedTimestamp;

            // 智能分类
            String defaultCategory = "购物";
            if (note.contains("烧烤") || note.contains("餐饮") || note.contains("面") || note.contains("饭") || note.contains("吃") || note.contains("汉堡") || note.contains("外卖")) {
                defaultCategory = "餐饮";
            } else if (note.contains("拼多多") || note.contains("淘宝") || note.contains("京东") || note.contains("超市")) {
                defaultCategory = "购物";
            }

            String assetKeyword = paymentMethod.isEmpty() ? "微信" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", assetKeyword);

            final String finalCategory = defaultCategory;
            handler.post(() -> showConfirmWindow(finalAmount, finalType, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

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
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

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
     * 稳健版：锁定 "付款方式" 和 "微信红包"，全局提取金额，并通过 Desc 提取精准资产
     */
    private boolean handleWeChatRedPacketSpecialPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean hasWeChatRedPacket = false;
        boolean hasPaymentMethod = false;   // 核心页面特征锁
        double amount = -1;
        String assetName = "";

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";

            // 1. 页面特征锁：必须存在 "付款方式" (这是支付确认弹窗最稳定的标志)
            if ("付款方式".equals(text) || "付款方式".equals(desc) || desc.contains("付款方式,已选择")) {
                hasPaymentMethod = true;
            }

            // 2. 核心触发节点：严格匹配 "微信红包"
            if ("微信红包".equals(text) || "微信红包".equals(desc)) {
                hasWeChatRedPacket = true;
            }

            // 3. 独立提取金额：支持从 Text 和 Desc 中抓取 ￥ 或 ¥
            if (text.startsWith("￥") || text.startsWith("¥") || desc.startsWith("￥") || desc.startsWith("¥")) {
                try {
                    String cleanAmount = !text.isEmpty() ? text : desc;
                    cleanAmount = cleanAmount.replace("￥", "").replace("¥", "").trim();
                    double parsed = Double.parseDouble(cleanAmount);
                    if (parsed > 0 && amount == -1) {
                        amount = parsed;
                    }
                } catch (Exception e) {
                    // 解析失败忽略
                }
            }

            // 4. 【核心优化】：提取付款方式（如 "零钱" 或 "中国银行储蓄卡"）
            // 方案 A：从 Button 的 Desc 中精准剥离 (如 "付款方式,已选择零钱,更改")
            if (desc.contains("付款方式") && desc.contains("已选择") && desc.contains("更改")) {
                try {
                    String[] parts = desc.split(",");
                    for (String part : parts) {
                        if (part.startsWith("已选择")) {
                            assetName = part.replace("已选择", "").trim();
                        }
                    }
                } catch (Exception e) {}
            }

            // 方案 B：降级方案，寻找 "更改" 节点后面的文本
            if (("更改".equals(text) || "更改".equals(desc)) && assetName.isEmpty()) {
                if (i + 1 < allNodes.size()) {
                    AccessibilityNodeInfo nextNode = allNodes.get(i + 1);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    if (!nextText.isEmpty()) {
                        assetName = nextText; // 抓取到 "零钱"
                    }
                }
            }
        }

        // 【触发条件】：只要是支付弹窗(有付款方式)，且是发红包(有微信红包)，且抓到了金额，就触发
        if (hasPaymentMethod && hasWeChatRedPacket && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            if (assetName.isEmpty()) assetName = "微信支付";

            // 防抖签名：加入刚才设置的 5分钟(300000)屏蔽，并加入资产名指纹
            String signature = "confirm-" + amount + "-0-微信红包-" + assetName;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识，替换 "auto" 为 "微信红包"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String timeNote = sdf.format(new Date(now)) + " 微信红包";

            final double finalAmount = amount;

            // 自动匹配资产 (将提取出的 "零钱" 传入模糊匹配引擎)
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

    // ================= 支付宝适配测试代码 结束 =================

    /**
     * 【专版专杀】专门适配支付宝“转账账单详情”页面 (如：转账给个人)
     * 提取实际的交易时间、对方账户、付款方式(资产)
     */
    private boolean handleAlipayTransferBillDetailPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isBillDetail = false;
        boolean isTransferPage = false; // 专属页面锁：必须含有"对方账户"
        String targetAccount = "";
        String timeString = "";
        String paymentMethod = "";
        double amount = -1;
        int type = 0; // 默认 0 为支出，1 为收入

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面基础特征
            if ("账单详情".equals(content) || "交易详情".equals(content)) {
                isBillDetail = true;
            }

            // 2. 核心特征：识别“对方账户”并向下抓取 (如：酷到爆 136******39)
            if ("对方账户".equals(content)) {
                isTransferPage = true;
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        targetAccount = nextContent;
                        break;
                    }
                }
            }

            // 3. 提取金额 (如："支出0.01元")
            if (content.startsWith("支出") && content.endsWith("元")) {
                try {
                    String cleanAmount = content.replace("支出", "").replace("元", "").replace(",", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                    type = 0; // 支出
                } catch (Exception e) {}
            } else if (content.startsWith("收入") && content.endsWith("元")) {
                try {
                    String cleanAmount = content.replace("收入", "").replace("元", "").replace(",", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                    type = 1; // 收入
                } catch (Exception e) {}
            }

            // 4. 提取支付方式 (如：中国银行储蓄卡(4260))
            if ("付款方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }

            // 5. 提取交易时间 (如：2026-03-30 00:13:28)
            if ("创建时间".equals(content) || "支付时间".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        timeString = nextContent;
                        break;
                    }
                }
            }
        }

        // 判定：既是账单页，又存在“对方账户”，且抓到了金额
        if (isBillDetail && isTransferPage && amount >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            if (targetAccount.isEmpty()) targetAccount = "支付宝转账";

            // 防重复签名
            String signature = "alipay_transfer-" + amount + "-" + targetAccount + "-" + timeString;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 解析真实时间戳
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    java.util.Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        parsedTimestamp = date.getTime();
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {}
            }
            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new java.util.Date(now));
            }

            // 构造记录标识，例如："03-30 00:13 酷到爆 136******39"
            final String recordIdentifier = displayTime + " " + targetAccount;

            final double finalAmount = amount;
            final int finalType = type;
            final long finalTimestamp = parsedTimestamp;

            // 因为是明确的转账给个人，默认分类固定给“转账”
            final String defaultCategory = "转账";

            // 资产模糊匹配
            String assetKeyword = paymentMethod.isEmpty() ? "支付宝" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.eg.android.AlipayGphone", assetKeyword);

            // 触发记账窗口
            handler.post(() -> showConfirmWindow(finalAmount, finalType, defaultCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 专门适配支付宝支付成功页面（基于最新节点树结构）
     * 提取实际支付金额、收款方（作为记录标识），以及付款方式（模糊匹配资产）
     * 【升级版】：修复无“收款方”标签时抓取失败的问题，直接从金额下方拦截商户名
     */
    private boolean handleAlipayPaySuccessPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaySuccess = false;
        String payeeName = "";
        String paymentMethod = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";

            String content = !text.isEmpty() ? text : desc;

            // 1. 识别“支付成功”页面特征
            if ("支付成功".equals(content) || content.startsWith("支付成功")) {
                isPaySuccess = true;
            }

            // 2. 提取金额（在“支付成功”后出现的纯数字或带小数的数字，如 "4.83"）
            if (isPaySuccess && amount == -1) {
                // 正则匹配：纯数字，可带1到2位小数
                if (content.matches("^\\d+(\\.\\d{1,2})?$")) {
                    try {
                        amount = Double.parseDouble(content);

                        // 【核心修复】：没有“收款方”标签时，商户名紧跟在纯数字金额的后面
                        for (int j = i + 1; j < allNodes.size(); j++) {
                            AccessibilityNodeInfo nextNode = allNodes.get(j);
                            String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                            String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                            String nextContent = !nextText.isEmpty() ? nextText : nextDesc;

                            // 排除空节点、以及带有 ￥、¥ 或 - 符号的优惠券金额干扰项
                            if (!nextContent.isEmpty() && !nextContent.contains("￥") && !nextContent.contains("¥") && !nextContent.startsWith("-")) {
                                payeeName = nextContent;
                                break; // 抓到商户名立刻跳出
                            }
                        }
                    } catch (Exception e) {
                        // 解析失败继续找
                    }
                }
            }

            // 3. 提取资产来源 (付款方式)
            if ("付款方式".equals(content)) {
                // 向下遍历，跨过不可见的排版空节点，寻找第一个带有文字的真实节点
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }

            // 4. 兼容老版本提取收款方 (如果页面有明确的“收款方”标签)
            if ("收款方".equals(content) && payeeName.isEmpty()) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        payeeName = nextContent;
                        break;
                    }
                }
            }
        }

        // 判定：识别到支付成功且金额合法
        if (isPaySuccess && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 确定备注内容（如果没抓到收款方，兜底使用"支付宝支付"）
            final String finalPayee = payeeName.isEmpty() ? "支付宝支付" : payeeName;
            final double finalAmount = amount;

            // 防止在页面停留时重复触发弹窗 (5分钟防抖)
            String signature = "alipay_success-" + amount + "-" + finalPayee;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 【构造记录标识】：如 "04-08 14:17 佛山市南海区仙溪村麻辣烫店"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + finalPayee;

            // 智能分类：带有餐饮相关字眼的自动判定为"餐饮"
            String defaultCategory = "购物";
            if (finalPayee.contains("麻辣烫") || finalPayee.contains("店") || finalPayee.contains("餐饮") || finalPayee.contains("吃") || finalPayee.contains("食") || finalPayee.contains("外卖")) {
                defaultCategory = "餐饮";
            }

            // 【资产匹配】：使用抓取到的付款方式去模糊匹配
            String assetKeyword = paymentMethod.isEmpty() ? "支付宝" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.eg.android.AlipayGphone", assetKeyword);

            // 触发记账弹窗 (type: 0 代表支出)
            final String finalCategory = defaultCategory;
            long finalTimestamp = now;
            handler.post(() -> showConfirmWindow(finalAmount, 0, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }
    /**
     * 【专版专杀】专门适配拼多多“多多钱包”支付密码弹窗页面
     * 提取实际支付金额、付款方式(将拆分的银行卡和尾号拼接)
     */
    private boolean handlePinduoduoPaymentPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPddPayment = false;
        String paymentMethod = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面核心特征：拼多多密码输入框
            if ("请输入多多钱包密码".equals(content) || content.contains("多多钱包密码") || content.contains("密码输入框")) {
                isPddPayment = true;
            }

            // 2. 提取金额 (匹配 Text: [40.04] 或 Desc: [人民币40.04元])
            if (desc.startsWith("人民币") && desc.endsWith("元")) {
                try {
                    String cleanAmount = desc.replace("人民币", "").replace("元", "").replace(",", "").trim();
                    double parsed = Double.parseDouble(cleanAmount);
                    if (parsed > 0 && amount == -1) amount = parsed;
                } catch (Exception e) {}
            } else if (text.matches("^\\d+\\.\\d{2}$") && amount == -1) {
                try {
                    amount = Double.parseDouble(text);
                } catch (Exception e) {}
            }

            // 3. 提取并拼接支付方式 (跨越节点将 "中国银行储蓄卡" 和 "(4260)" 拼接)
            if ("支付方式".equals(content)) {
                StringBuilder pmBuilder = new StringBuilder();
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;

                    if (!nextContent.isEmpty()) {
                        // 遇到单字符的字体图标（如 ）或者无关文字则停止拼接
                        if (nextContent.length() == 1 || nextContent.equals("找回密码") || nextContent.contains("密码")) {
                            break;
                        }
                        pmBuilder.append(nextContent);
                        // 拼多多支付方式通常分为两段，第二段包含尾号右括号，遇到则视为拼接完成
                        if (nextContent.contains(")")) {
                            break;
                        }
                    }
                }
                paymentMethod = pmBuilder.toString().trim();
            }
        }

        // 判定：确认是拼多多支付页且成功抓取到金额
        if (isPddPayment && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 防重复录入签名
            String signature = "pdd_pay-" + amount + "-" + paymentMethod;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识，例如："03-30 10:12 拼多多购物"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            String displayTime = sdf.format(new Date(now));
            final String recordIdentifier = displayTime + " 拼多多购物";

            final double finalAmount = amount;
            final long finalTimestamp = now;

            // 自动匹配资产：如果成功拼接到了“中国银行储蓄卡(4260)”，就用它去模糊匹配，否则兜底搜索“多多钱包”
            String assetKeyword = paymentMethod.isEmpty() ? "多多钱包" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.xunmeng.pinduoduo", assetKeyword);

            // 触发记账确认窗口（type: 0 为支出，分类默认给“购物”）
            handler.post(() -> showConfirmWindow(finalAmount, 0, "购物", recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配通义千问等AI应用的“充值/支付已完成”页面
     * 提取实际支付金额(如 45.00)、充值说明作为备注
     */
    private boolean handleQwenPaySuccessPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isQwenPay = false;
        double amount = -1;
        String note = "千问消费";

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别核心特征：支付已完成
            if ("支付已完成".equals(content) || "已完成支付".equals(content)) {
                isQwenPay = true;
            }

            // 2. 提取金额：寻找 "小计 ¥" 或者类似标识，金额在它的下一个节点
            if ("小计 ¥".equals(content) || "小计¥".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;

                    if (!nextContent.isEmpty()) {
                        try {
                            amount = Double.parseDouble(nextContent.trim());
                            break;
                        } catch (Exception e) {}
                    }
                }
            }

            // 3. 提取备注说明 (如: 已成功充值话费50元...)
            if (content.startsWith("已成功充值") || content.contains("充值话费")) {
                // 如果文字太长，做个截断处理，保持记账界面的清爽
                if (content.length() > 20) {
                    note = content.substring(0, 18) + "...";
                } else {
                    note = content;
                }
            }
        }

        // 判定：确认是支付完成页，且成功抓取到了 45.00 这笔金额
        if (isQwenPay && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 防重复录入签名
            String signature = "qwen_pay-" + amount + "-" + note;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识，例如："03-30 10:10 已成功充值话费50元..."
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            String displayTime = sdf.format(new Date(now));
            final String recordIdentifier = displayTime + " " + note;

            final double finalAmount = amount;
            final long finalTimestamp = now;

            // 智能分类：充话费默认分到“通讯”，其他则设为“购物”
            String defaultCategory = note.contains("话费") ? "通讯" : "购物";

            // 资产模糊匹配 (考虑到通常是拉起支付宝完成的充值，这里默认搜支付宝)
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.alibaba.android.qwen", "支付宝");

            // 触发记账确认窗口
            handler.post(() -> showConfirmWindow(finalAmount, 0, defaultCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配微信“扫二维码付款”或“个人转账”的账单详情页
     * 核心特征为含有“转账单号”、“收款方备注”等标签
     */
    private boolean handleWeChatQRCodeTransferPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isTargetPage = false;
        String targetAccount = "";
        String timeString = "";
        String paymentMethod = "";
        double amount = -1;
        int type = 0; // 默认 0 为支出，1 为收入

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面特征：包含“转账单号” (扫个人码或者转账都会有这个)
            if ("转账单号".equals(content)) {
                isTargetPage = true;
            }

            // 2. 提取金额和收支类型 (精准锁定格式如 "-2.00")
            if (content.matches("^[-+]?\\d+\\.\\d{2}$") && amount == -1) {
                try {
                    double parsedAmount = Double.parseDouble(content);
                    if (parsedAmount < 0) {
                        type = 0;
                        amount = Math.abs(parsedAmount);
                    } else if (parsedAmount > 0) {
                        type = 1;
                        amount = parsedAmount;
                    }

                    // 3. 提取交易对象 (就在金额节点的正上方，例如 "扫二维码付款-给郭宝生")
                    if (i > 0) {
                        AccessibilityNodeInfo prevNode = allNodes.get(i - 1);
                        String prevText = prevNode.getText() != null ? prevNode.getText().toString().trim() : "";
                        String prevDesc = prevNode.getContentDescription() != null ? prevNode.getContentDescription().toString().trim() : "";
                        String prevContent = !prevText.isEmpty() ? prevText : prevDesc;

                        if (!prevContent.isEmpty() && !prevContent.matches("^[-+]?\\d+\\.\\d{2}$")) {
                            targetAccount = prevContent;
                        }
                    }
                } catch (Exception e) {}
            }

            // 4. 提取支付方式 (例如：零钱、某某银行卡)
            if ("支付方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }

            // 5. 提取转账时间 (格式如：2026年3月29日 11:12:16)
            if ("转账时间".equals(content) || "支付时间".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        timeString = nextContent;
                        break;
                    }
                }
            }
        }

        // 判定：确认是目标账单详情页且金额提取成功
        if (isTargetPage && amount >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底默认值
            if (targetAccount.isEmpty()) targetAccount = "微信扫码付款";

            // 防重复录入签名
            String signature = "wx_qr_transfer-" + amount + "-" + targetAccount + "-" + timeString;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 核心：解析抓取到的微信真实时间
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    // 按照微信时间格式解析
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault());
                    java.util.Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        parsedTimestamp = date.getTime();
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {}
            }
            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new java.util.Date(now));
            }

            // 构造记录标识，如："03-29 11:12 扫二维码付款-给郭宝生"
            final String recordIdentifier = displayTime + " " + targetAccount;

            final double finalAmount = amount;
            final int finalType = type;
            final long finalTimestamp = parsedTimestamp;

            // 动态选择分类：针对个人的扫码通常发生在小摊贩/餐饮店，默认给"餐饮"或"购物"
            String defaultCategory = targetAccount.contains("付款") ? "购物" : "转账";

            // 使用抓取到的支付方式（如“零钱”）去模糊匹配资产
            String assetKeyword = paymentMethod.isEmpty() ? "微信" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", assetKeyword);

            // 触发弹窗
            handler.post(() -> showConfirmWindow(finalAmount, finalType, defaultCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配微信“商家转账/提现”账单详情页 (如：QQ音乐金币提现)
     * 核心特征为含有“付款单号”、“收款方式”和“付款备注”等标签
     */
    private boolean handleWeChatMerchantTransferPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isTargetPage = false;
        String note = "";
        String fallbackTitle = "";
        String timeString = "";
        String paymentMethod = "";
        double amount = -1;
        int type = 1; // 通常这种页面是提现/收入，默认设为1

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面特征：包含“付款单号” (商家转账特有标签)
            if ("付款单号".equals(content) || "商家单号".equals(content)) {
                isTargetPage = true;
            }

            // 2. 提取金额和收支类型 (精准锁定格式如 "+0.50" 或 "-0.50")
            if (content.matches("^[-+]?\\d+\\.\\d{2}$") && amount == -1) {
                try {
                    double parsedAmount = Double.parseDouble(content);
                    if (parsedAmount < 0) {
                        type = 0; // 支出
                        amount = Math.abs(parsedAmount);
                    } else if (parsedAmount > 0) {
                        type = 1; // 收入
                        amount = parsedAmount;
                    }

                    // 顺便抓一下金额上方的标题作为兜底备注 (例如："商家转账-来自QQ音乐")
                    if (i > 0) {
                        AccessibilityNodeInfo prevNode = allNodes.get(i - 1);
                        String prevText = prevNode.getText() != null ? prevNode.getText().toString().trim() : "";
                        String prevDesc = prevNode.getContentDescription() != null ? prevNode.getContentDescription().toString().trim() : "";
                        String prevContent = !prevText.isEmpty() ? prevText : prevDesc;
                        if (!prevContent.isEmpty() && !prevContent.matches("^[-+]?\\d+\\.\\d{2}$")) {
                            fallbackTitle = prevContent;
                        }
                    }
                } catch (Exception e) {}
            }

            // 3. 提取收款/付款方式 (作为资产名，如："零钱")
            if ("收款方式".equals(content) || "退款方式".equals(content) || "支付方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }

            // 4. 提取转账/到账时间 (格式如：2026年3月27日 12:56:00)
            if ("转账时间".equals(content) || "到账时间".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        timeString = nextContent;
                        break;
                    }
                }
            }

            // 5. 提取最高优先级的详细备注 (如："QQ音乐金币提现")
            if ("付款备注".equals(content) || "退款原因".equals(content) || "收款理由".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        note = nextContent;
                        break;
                    }
                }
            }
        }

        // 判定：确认是目标页面，且提取到了有效的金额
        if (isTargetPage && amount >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 确定最终备注：如果有"付款备注"优先用，没有就用标题兜底
            String finalNote = note.isEmpty() ? fallbackTitle : note;
            if (finalNote.isEmpty()) finalNote = "微信商家转账";

            // 防重复签名
            String signature = "wx_merchant_transfer-" + amount + "-" + finalNote + "-" + timeString;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 解析真实微信时间戳
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault());
                    java.util.Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        parsedTimestamp = date.getTime();
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {}
            }
            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new java.util.Date(now));
            }

            // 构造记录标识，如："03-27 12:56 QQ音乐金币提现"
            final String recordIdentifier = displayTime + " " + finalNote;

            final double finalAmount = amount;
            final int finalType = type;
            final long finalTimestamp = parsedTimestamp;

            // 动态选择分类：针对提现、退款等操作通常默认分配到"其他"或"理财"等类目
            String defaultCategory = "其他";
            if (finalNote.contains("提现") || finalNote.contains("退款") || finalNote.contains("返现")) {
                defaultCategory = (finalType == 1) ? "退款" : "其他";
            }

            // 使用抓取到的收款方式（如“零钱”）去模糊匹配资产
            String assetKeyword = paymentMethod.isEmpty() ? "微信" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", assetKeyword);

            // 【核心修复】：增加 final 关键字进行定格，满足 Lambda 表达式的语法要求
            final String finalCategory = defaultCategory;
            handler.post(() -> showConfirmWindow(finalAmount, finalType, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));
            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配云闪付的“交易详情”页面
     * 提取金额(如 -¥10.02)、商户名、付款方式以及准确的交易时间
     */
    private boolean handleUnionPayBillDetailPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isTargetPage = false;
        String note = "";
        String timeString = "";
        String paymentMethod = "";
        double amount = -1;
        int type = 0; // 0代表支出, 1代表收入

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面核心特征：包含“云闪付交易详情”或“云闪付APP”
            if ("云闪付交易详情".equals(content) || "云闪付APP".equals(content)) {
                isTargetPage = true;
            }

            // 2. 提取金额和收支类型 (精准锁定格式，如 "-¥10.02" 或 "+¥10.02")
            if (content.matches("^[-+]?¥?\\d+\\.\\d{2}$") && amount == -1) {
                try {
                    // 去除可能的 ¥ 符号
                    String cleanAmount = content.replace("¥", "").trim();
                    double parsedAmount = Double.parseDouble(cleanAmount);
                    if (parsedAmount < 0) {
                        type = 0;
                        amount = Math.abs(parsedAmount);
                    } else if (parsedAmount > 0) {
                        type = 1;
                        amount = parsedAmount;
                    }

                    // 3. 提取交易商户 (云闪付的商户名正好就在大字金额的正上方一个节点)
                    if (i > 0) {
                        AccessibilityNodeInfo prevNode = allNodes.get(i - 1);
                        String prevText = prevNode.getText() != null ? prevNode.getText().toString().trim() : "";
                        String prevDesc = prevNode.getContentDescription() != null ? prevNode.getContentDescription().toString().trim() : "";
                        String prevContent = !prevText.isEmpty() ? prevText : prevDesc;

                        if (!prevContent.isEmpty() && !prevContent.matches("^[-+]?¥?\\d+\\.\\d{2}$")) {
                            note = prevContent;
                        }
                    }
                } catch (Exception e) {}
            }

            // 4. 提取付款方式 (如：广发银行银联信用卡[9620])
            if ("付款方式".equals(content) || "支付方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }

            // 5. 提取订单时间 (如：2026年3月29日 00:27:21)
            if ("订单时间".equals(content) || "交易时间".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        timeString = nextContent;
                        break;
                    }
                }
            }
        }

        // 判定：确认是云闪付交易详情页，并且成功解析到了金额
        if (isTargetPage && amount >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底值
            if (note.isEmpty()) note = "云闪付交易";

            // 防重复录入签名
            String signature = "unionpay-" + amount + "-" + note + "-" + timeString;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 解析真实的时间戳
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault());
                    java.util.Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        parsedTimestamp = date.getTime();
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {}
            }
            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new java.util.Date(now));
            }

            // 构造记录标识，例如："03-29 00:27 拼多多平台商户"
            final String recordIdentifier = displayTime + " " + note;
            final double finalAmount = amount;
            final int finalType = type;
            final long finalTimestamp = parsedTimestamp;

            // 动态选择默认分类
            String defaultCategory = note.contains("拼多多") || note.contains("淘宝") || note.contains("京东") ? "购物" : "云闪付";

            // 资产模糊匹配，用“广发银行银联信用卡[9620]”这种全称去撞击用户的数据库
            String assetKeyword = paymentMethod.isEmpty() ? "云闪付" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.unionpay", assetKeyword);

            // 触发弹窗
            handler.post(() -> showConfirmWindow(finalAmount, finalType, defaultCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配第三方应用/小程序调用微信支付后的“支付成功”页面
     * 针对该页面信息全在 ContentDescription (Desc) 中，且带有“原价/优惠”干扰项的特征进行抓取
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

            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !desc.isEmpty() ? desc : text;

            // 1. 识别“支付成功”标志
            if ("支付成功".equals(content)) {
                isPaySuccessPage = true;
            }

            // 2. 提取金额（寻找带有 ￥ 或 ¥ 的节点）
            // 【过滤干扰】：排除掉包含"原价"、"优惠"的节点，只抓取真正的实付金额
            if ((content.contains("￥") || content.contains("¥")) && !content.contains("原价") && !content.contains("优惠")) {
                try {
                    String cleanAmount = content.replace("￥", "").replace("¥", "").replace(",", "").trim();
                    double parsedAmount = Double.parseDouble(cleanAmount);

                    // 确保金额有效，且只抓取第一次出现的有效金额
                    if (parsedAmount > 0 && amount == -1) {
                        amount = parsedAmount;

                        // 3. 提取商户名（中国移动）
                        // 【核心修复】：向上倒序遍历，跨过所有不可见的排版空节点，寻找真正的商户名称
                        for (int j = i - 1; j >= 0; j--) {
                            AccessibilityNodeInfo prevNode = allNodes.get(j);
                            String prevDesc = prevNode.getContentDescription() != null ? prevNode.getContentDescription().toString().trim() : "";
                            String prevText = prevNode.getText() != null ? prevNode.getText().toString().trim() : "";
                            String prevContent = !prevDesc.isEmpty() ? prevDesc : prevText;

                            // 排除掉空节点和“支付成功”这个标题节点，剩下的就是完美商户名
                            if (!prevContent.isEmpty() && !"支付成功".equals(prevContent)) {
                                merchantName = prevContent;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 解析失败忽略
                }
            }
        }

        // 4. 判定：如果是支付成功页，且成功避开干扰项抓取到真实金额
        if (isPaySuccessPage && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            if (merchantName.isEmpty()) merchantName = "微信支付";

            // 防抖，防止界面停留时重复弹窗
            String signature = "merchant_app_pay-" + amount + "-" + merchantName;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识，例如："03-21 16:21 中国移动"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + merchantName;

            final double finalAmount = amount;
            final long finalTimestamp = now;

            // 智能分类
            String defaultCategory = "购物";
            if (merchantName.contains("移动") || merchantName.contains("联通") || merchantName.contains("电信") || merchantName.contains("话费")) {
                defaultCategory = "通讯";
            } else if (merchantName.contains("美团") || merchantName.contains("外卖")) {
                defaultCategory = "餐饮";
            }

            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", "支付成功");

            final String finalCategory = defaultCategory;
            handler.post(() -> showConfirmWindow(finalAmount, 0, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配京东“支付成功”页面
     * 从合并节点 (如 "京东白条付款¥7.7") 中同时剥离出付款方式和金额
     */
    private boolean handleJDPaySuccessPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaySuccess = false;
        double amount = -1;
        String paymentMethod = "";

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面核心特征
            if ("支付成功".equals(content)) {
                isPaySuccess = true;
            }

            // 2. 核心拆解：提取“京东白条付款¥7.7”或“微信支付￥12.00”这类组合结构
            if ((content.contains("付款¥") || content.contains("付款￥") || content.contains("支付¥") || content.contains("支付￥")) && amount == -1) {
                try {
                    // 正则手术刀：匹配前面任意字符(组1) + 付款/支付 + ¥/￥ + 数字或小数(组2)
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.*?)(?:付款|支付)[¥￥](\\d+(?:\\.\\d{1,2})?)").matcher(content);
                    if (m.find()) {
                        paymentMethod = m.group(1).trim(); // 剥离出 "京东白条"
                        amount = Double.parseDouble(m.group(2)); // 剥离出 "7.7"
                    }
                } catch (Exception e) {
                    // 解析失败忽略
                }
            }
        }

        // 3. 判定：确认是支付成功页，并且成功剖析出了金额
        if (isPaySuccess && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 防重复录入签名
            String signature = "jd_pay-" + amount + "-" + paymentMethod;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造统一备注
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " 京东购物";

            final double finalAmount = amount;
            final long finalTimestamp = now;
            final String finalCategory = "购物";

            // 资产模糊匹配：自动拿着刚才切出来的“京东白条”去找你的数据库资产
            String assetKeyword = paymentMethod.isEmpty() ? "京东" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.jingdong.app.mall", assetKeyword);

            // 触发记账弹窗（type: 0 为支出）
            handler.post(() -> showConfirmWindow(finalAmount, 0, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配抖音支付确认弹窗页面 (如: 输入支付密码)
     * 精准提取金额，并将“付款方式”传递给底层资产引擎
     */
    private boolean handleDouyinPaymentPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaymentPage = false;
        String paymentMethod = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别核心特征：输入支付密码 或 免密支付协议
            if ("输入支付密码".equals(content) || content.contains("免密支付协议")) {
                isPaymentPage = true;
            }

            // 2. 提取金额：精准锁定带有两位小数的纯数字格式 (如 "16.90")
            if (content.matches("^\\d+\\.\\d{2}$") && amount == -1) {
                try {
                    amount = Double.parseDouble(content);
                } catch (Exception e) {}
            }

            // 3. 提取支付方式：寻找 "支付方式" 节点，紧接着的下一个有效节点就是银行卡信息
            if ("支付方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }
        }

        // 4. 判定：确认是抖音支付页且成功抓取到金额
        if (isPaymentPage && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 防重复录入签名 (采用刚更新的 5分钟/300000ms 防抖)
            String signature = "douyin_pay-" + amount + "-" + paymentMethod;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造统一备注
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " 抖音购物";

            final double finalAmount = amount;
            final long finalTimestamp = now;
            final String finalCategory = "购物";

            // 资产模糊匹配：将提取出的 "中国银行储蓄卡 (4260)" 传入模糊匹配引擎
            String assetKeyword = paymentMethod.isEmpty() ? "抖音" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.ss.android.ugc.aweme", assetKeyword);

            // 触发记账确认窗口（type: 0 为支出）
            handler.post(() -> showConfirmWindow(finalAmount, 0, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配美团“支付成功”页面
     * 解决金额与标题合并 (如 "支付成功 ¥25.38") 的问题，并精准提取支付方式
     */
    private boolean handleMeituanPaySuccessPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaySuccess = false;
        double amount = -1;
        String paymentMethod = "";

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面特征并提取合并的金额
            // 美团节点特征: [支付成功 ¥25.38]
            if (content.contains("支付成功")) {
                isPaySuccess = true;

                // 尝试从该节点直接剥离金额
                if (content.contains("¥") || content.contains("￥")) {
                    try {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[¥￥](\\d+(?:\\.\\d{1,2})?)").matcher(content);
                        if (m.find()) {
                            amount = Double.parseDouble(m.group(1));
                        }
                    } catch (Exception e) {}
                }
            }

            // 2. 提取支付方式：寻找 "支付方式" 节点，紧接着的下一个节点就是资产名
            if ("支付方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;

                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }
        }

        // 3. 判定：确认是美团支付成功页且成功抓取到金额
        if (isPaySuccess && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 防重复录入签名 (使用 5分钟/300000ms 防抖)
            String signature = "meituan_pay-" + amount + "-" + paymentMethod;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造统一备注
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " 美团消费";

            final double finalAmount = amount;
            final long finalTimestamp = now;
            // 美团消费大概率是吃饭、外卖或买菜，分类默认打上"餐饮"
            final String finalCategory = "餐饮";

            // 资产模糊匹配：将提取出的 "中国银行储蓄卡(4260)" 传入模糊匹配引擎
            String assetKeyword = paymentMethod.isEmpty() ? "美团" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.sankuai.meituan", assetKeyword);

            // 触发记账确认窗口（type: 0 为支出）
            handler.post(() -> showConfirmWindow(finalAmount, 0, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配拼多多“订单详情 / 支付完成”页面
     * 从长段落中精准剥离商品名称和最终实付金额，避开优惠券干扰
     */
    private boolean handlePinduoduoOrderDetailPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPddOrderPage = false;
        String note = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 页面特征锁：包含订单编号
            if (content.contains("订单编号")) {
                isPddOrderPage = true;
            }

            // 2. 提取实付金额 (从类似 "实付使用...16.34元,实付,16.35元" 的文本中提取)
            if (content.contains("实付") && content.contains("元") && amount == -1) {
                try {
                    // 正则寻找 "实付" 后面跟着的金额
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("实付.*?(\\d+(?:\\.\\d{1,2})?)\\s*元").matcher(content);
                    // 【核心逻辑】：使用 while 循环获取最后一次匹配的结果，完美避开前面的抵扣/减免金额
                    while (m.find()) {
                        amount = Double.parseDouble(m.group(1));
                    }
                } catch (Exception e) {}
            }

            // 3. 提取商品名称作为备注
            if (content.contains("商品名称：")) {
                try {
                    // 以“单价”作为切分点，把前面的商品名称完整切下来
                    String[] parts = content.split("[,，]单价");
                    if (parts.length > 0) {
                        String namePart = parts[0].replace("商品名称：", "").trim();
                        // 名字太长会让记账界面不美观，做个截断
                        if (namePart.length() > 18) {
                            note = namePart.substring(0, 16) + "...";
                        } else {
                            note = namePart;
                        }
                    }
                } catch (Exception e) {}
            }
        }

        // 4. 判定：确认是拼多多订单页，并且成功解析到了金额
        if (isPddOrderPage && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            if (note.isEmpty()) note = "拼多多购物";

            // 防抖签名 (使用最新的 5分钟/300000ms 防重复)
            String signature = "pdd_order-" + amount + "-" + note;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识：时间 + 商品名称
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + note;

            final double finalAmount = amount;
            final long finalTimestamp = now;
            final String finalCategory = "购物";

            // 订单页通常不显示具体的银行卡，默认用 "拼多多" 传递给底层去挂载资产
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.xunmeng.pinduoduo", "拼多多");

            // 触发弹窗
            handler.post(() -> showConfirmWindow(finalAmount, 0, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配微信“个人转账已收款”页面
     * 提取存入零钱的金额(收入)、转账说明以及真实的收款时间
     */
    private boolean handleWeChatTransferReceivedPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isTransferReceived = false;
        double amount = -1;
        String timeString = "";
        String note = "";

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面特征：你已收款，资金已存入零钱
            if (content.contains("你已收款") && content.contains("已存入零钱")) {
                isTransferReceived = true;
            }

            // 2. 提取金额 (如：¥12.00)
            if ((content.startsWith("¥") || content.startsWith("￥")) && amount == -1) {
                try {
                    String cleanAmount = content.replace("¥", "").replace("￥", "").replace(",", "").trim();
                    amount = Double.parseDouble(cleanAmount);
                } catch (Exception e) {}
            }

            // 3. 提取转账说明 (如：捐赠支持)
            if ("转账说明".equals(content) || "收款备注".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        note = nextContent;
                        break;
                    }
                }
            }

            // 4. 提取收款时间 (如：2026年04月08日 20:42:01)
            if ("收款时间".equals(content) || "转账时间".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;

                    // 避免抓到无关文本，时间格式必然带有 "年" 和 "月"
                    if (!nextContent.isEmpty() && nextContent.contains("年") && nextContent.contains("月")) {
                        timeString = nextContent;
                        break;
                    }
                }
            }
        }

        // 5. 判定触发
        if (isTransferReceived && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底备注
            if (note.isEmpty()) note = "微信转账收款";

            // 防抖
            String signature = "wx_transfer_received-" + amount + "-" + note + "-" + timeString;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 解析真实的微信时间戳
            long parsedTimestamp = now;
            String displayTime = "";
            if (!timeString.isEmpty()) {
                try {
                    SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault());
                    java.util.Date date = parseFormat.parse(timeString);
                    if (date != null) {
                        parsedTimestamp = date.getTime();
                        SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                        displayTime = displayFormat.format(date);
                    }
                } catch (Exception e) {}
            }
            if (displayTime.isEmpty()) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                displayTime = displayFormat.format(new java.util.Date(now));
            }

            // 构造记录标识，如："04-08 20:42 捐赠支持"
            final String recordIdentifier = displayTime + " " + note;
            final double finalAmount = amount;
            final long finalTimestamp = parsedTimestamp;
            final String finalCategory = "转账"; // 收入类的默认分类可以设为转账或红包

            // 明确是存入零钱，自动匹配微信/零钱资产
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.tencent.mm", "零钱");

            // 注意：type = 1 代表这是“收入”
            handler.post(() -> showConfirmWindow(finalAmount, 1, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    /**
     * 【专版专杀】专门适配云闪付“支付成功”页面
     * 锁定首个带 ¥ 的有效金额，向上抓取商户名，向下跳过原价/优惠等干扰项
     */
    private boolean handleUnionPayPaySuccessPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        flattenNodes(root, allNodes); // 展平节点树

        boolean isPaySuccess = false;
        String merchantName = "";
        String paymentMethod = "";
        double amount = -1;

        for (int i = 0; i < allNodes.size(); i++) {
            AccessibilityNodeInfo node = allNodes.get(i);
            String text = node.getText() != null ? node.getText().toString().trim() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            String content = !text.isEmpty() ? text : desc;

            // 1. 识别页面特征
            if ("支付成功".equals(content)) {
                isPaySuccess = true;
            }

            // 2. 提取实付金额（锁定第一个带有 ¥ 或 ￥ 的节点，避开负数）
            if ((content.contains("¥") || content.contains("￥")) && !content.contains("-") && !content.contains("优惠") && amount == -1) {
                try {
                    String cleanAmount = content.replace("¥", "").replace("￥", "").replace(",", "").trim();
                    amount = Double.parseDouble(cleanAmount);

                    // 3. 顺藤摸瓜提取商户名 (商户名通常紧挨在金额的上方一个节点)
                    if (i > 0) {
                        AccessibilityNodeInfo prevNode = allNodes.get(i - 1);
                        String prevText = prevNode.getText() != null ? prevNode.getText().toString().trim() : "";
                        String prevDesc = prevNode.getContentDescription() != null ? prevNode.getContentDescription().toString().trim() : "";
                        String prevContent = !prevText.isEmpty() ? prevText : prevDesc;

                        // 只要上面的节点不是空，且不是"支付成功"这四个字，就认为是商户名
                        if (!prevContent.isEmpty() && !"支付成功".equals(prevContent)) {
                            merchantName = prevContent;
                        }
                    }
                } catch (Exception e) {
                    // 解析失败忽略
                }
            }

            // 4. 提取支付方式 (例如：广发银行银联信用卡 [9620])
            if ("付款方式".equals(content)) {
                for (int j = i + 1; j < allNodes.size(); j++) {
                    AccessibilityNodeInfo nextNode = allNodes.get(j);
                    String nextText = nextNode.getText() != null ? nextNode.getText().toString().trim() : "";
                    String nextDesc = nextNode.getContentDescription() != null ? nextNode.getContentDescription().toString().trim() : "";
                    String nextContent = !nextText.isEmpty() ? nextText : nextDesc;
                    if (!nextContent.isEmpty()) {
                        paymentMethod = nextContent;
                        break;
                    }
                }
            }
        }

        // 5. 判定触发条件
        if (isPaySuccess && amount > 0) {
            long now = System.currentTimeMillis();
            if (now - lastWindowDismissTime < 2500) return true;

            // 兜底默认值
            if (merchantName.isEmpty()) merchantName = "云闪付消费";

            // 5分钟防重复录入防抖
            String signature = "unionpay_success-" + amount + "-" + merchantName;
            if (now - lastRecordTime < 300000 && signature.equals(lastContentSignature)) return true;

            lastRecordTime = now;
            lastContentSignature = signature;

            // 构造记录标识，如："04-08 20:20 中国联通APP"
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            final String recordIdentifier = sdf.format(new Date(now)) + " " + merchantName;

            final double finalAmount = amount;
            final long finalTimestamp = now;

            // 智能分类
            String defaultCategory = "购物";
            if (merchantName.contains("联通") || merchantName.contains("移动") || merchantName.contains("电信") || merchantName.contains("话费") || merchantName.contains("充值")) {
                defaultCategory = "通讯";
            } else if (merchantName.contains("餐饮") || merchantName.contains("饭") || merchantName.contains("外卖")) {
                defaultCategory = "餐饮";
            }

            // 资产模糊匹配，用“广发银行银联信用卡 [9620]”去撞击你的本地数据库配置
            String assetKeyword = paymentMethod.isEmpty() ? "云闪付" : paymentMethod;
            int autoAssetId = AutoAssetManager.matchAsset(this, "com.unionpay", assetKeyword);

            // 【防止编译报错】：将类别提取为 final 变量以供 Lambda 使用
            final String finalCategory = defaultCategory;

            // 触发记账弹窗（type = 0 为支出）
            handler.post(() -> showConfirmWindow(finalAmount, 0, finalCategory, recordIdentifier, autoAssetId, "¥", finalTimestamp));

            return true;
        }

        return false;
    }

    @Override public void onInterrupt() {}
}