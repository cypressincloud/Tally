package com.example.budgetapp.service;

import android.app.Notification;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner; // 导入 Spinner
import android.widget.Toast;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.KeywordManager;

import java.text.SimpleDateFormat;
import java.util.Date;
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
            saveToDatabase(amount, type, category, note + " (后台)");
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

            LayoutInflater inflater = LayoutInflater.from(this);
            View floatView = inflater.inflate(R.layout.window_confirm_transaction, null);

            isWindowShowing = true;

            EditText etAmount = floatView.findViewById(R.id.et_window_amount);
            RadioGroup rgType = floatView.findViewById(R.id.rg_window_type);
            RadioGroup rgCategory = floatView.findViewById(R.id.rg_window_category);
            EditText etCategory = floatView.findViewById(R.id.et_window_category);
            EditText etNote = floatView.findViewById(R.id.et_window_note);
            Button btnSave = floatView.findViewById(R.id.btn_window_save);
            Button btnCancel = floatView.findViewById(R.id.btn_window_cancel);
            Spinner spAsset = floatView.findViewById(R.id.sp_asset);
            
            // 暂时隐藏资产下拉框，直到此服务也适配资产逻辑
            if (spAsset != null) spAsset.setVisibility(View.GONE);

            etAmount.setText(String.valueOf(amount));
            etNote.setText(note);

            if (type == 1) {
                rgType.check(R.id.rb_window_income);
                rgCategory.setVisibility(View.GONE);
                etCategory.setVisibility(View.GONE);
            } else {
                rgType.check(R.id.rb_window_expense);
                rgCategory.setVisibility(View.VISIBLE);
                
                // 初始化分类显示逻辑
                if ("餐饮".equals(category)) {
                    rgCategory.check(R.id.rb_cat_food);
                    etCategory.setVisibility(View.GONE);
                } else if ("娱乐".equals(category)) {
                    rgCategory.check(R.id.rb_cat_ent);
                    etCategory.setVisibility(View.GONE);
                } else if ("购物".equals(category)) {
                    rgCategory.check(R.id.rb_cat_shop);
                    etCategory.setVisibility(View.GONE);
                } else {
                    // 交通或其他分类，统一归为自定义
                    rgCategory.check(R.id.rb_window_custom);
                    etCategory.setVisibility(View.VISIBLE);
                    etCategory.setText(category);
                }
            }

            // 监听收支切换，控制分类栏显隐
            rgType.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_window_income) {
                    rgCategory.setVisibility(View.GONE);
                    etCategory.setVisibility(View.GONE);
                } else {
                    rgCategory.setVisibility(View.VISIBLE);
                    // 切换回支出时，检查是否选中了自定义
                    if (rgCategory.getCheckedRadioButtonId() == R.id.rb_window_custom) {
                        etCategory.setVisibility(View.VISIBLE);
                    } else {
                        etCategory.setVisibility(View.GONE);
                    }
                }
            });

            // 监听分类切换，控制输入框显隐
            rgCategory.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_window_custom) {
                    etCategory.setVisibility(View.VISIBLE);
                } else {
                    etCategory.setVisibility(View.GONE);
                }
            });

            btnSave.setOnClickListener(v -> {
                try {
                    double finalAmount = Double.parseDouble(etAmount.getText().toString());
                    String finalNote = etNote.getText().toString();
                    int finalType = (rgType.getCheckedRadioButtonId() == R.id.rb_window_income) ? 1 : 0;

                    String finalCat = "其他";
                    if (finalType == 1) {
                        finalCat = "收入";
                    } else {
                        int checkedId = rgCategory.getCheckedRadioButtonId();
                        // 修复：使用 rb_window_custom 代替 rb_cat_trans
                        if (checkedId == R.id.rb_window_custom) {
                            String customInput = etCategory.getText().toString().trim();
                            if (!customInput.isEmpty()) {
                                finalCat = customInput;
                            } else {
                                finalCat = "其他"; 
                            }
                        } else if (checkedId == R.id.rb_cat_food) {
                            finalCat = "餐饮";
                        } else if (checkedId == R.id.rb_cat_ent) {
                            finalCat = "娱乐";
                        } else if (checkedId == R.id.rb_cat_shop) {
                            finalCat = "购物";
                        }
                    }

                    saveToDatabase(finalAmount, finalType, finalCat, finalNote);
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

    private void saveToDatabase(double amount, int type, String category, String note) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Transaction t = new Transaction();
            t.date = System.currentTimeMillis();
            t.type = type;
            t.category = category;
            t.amount = amount;
            t.note = note;
            dao.insert(t);
        });
    }
}