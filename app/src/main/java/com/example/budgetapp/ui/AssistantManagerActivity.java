package com.example.budgetapp.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.KeywordSettingActivity;
import com.example.budgetapp.R;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.KeywordManager;
import com.example.budgetapp.util.ShizukuManager;
import com.google.android.accessibility.selecttospeak.SelectToSpeakService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rikka.shizuku.Shizuku;

public class AssistantManagerActivity extends AppCompatActivity {

    private AssistantConfig config;

    // 使用 SwitchCompat
    private SwitchCompat switchAutoTrack;

    //    private SwitchCompat switchRefundMonitor;
    private SwitchCompat switchAssets;

    private RecyclerView rvKeywords;
    private KeywordAdapter adapter;

    // 声明变量
    private SwitchCompat switchDetails;
    private List<KeywordItem> dataList = new ArrayList<>();

    // ===== 新增 Shizuku 相关常量与视图变量 =====
    private static final int REQUEST_CODE_SHIZUKU = 1001;
    private static final String PREF_NAME = "assistant_settings";
    private static final String KEY_SHIZUKU_ENABLED = "shizuku_keep_alive_enabled";

    private SwitchCompat switchShizukuKeepAlive;
    private TextView tvShizukuStatus;
    private SharedPreferences sp;

    // 监听 Shizuku 权限申请结果
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            runOnUiThread(() -> {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Shizuku 授权成功", Toast.LENGTH_SHORT).show();
                    enableKeepAliveAndAccessibility();
                } else {
                    Toast.makeText(this, "用户拒绝了 Shizuku 授权", Toast.LENGTH_SHORT).show();
                    if (switchShizukuKeepAlive != null) {
                        switchShizukuKeepAlive.setChecked(false);
                    }
                    sp.edit().putBoolean(KEY_SHIZUKU_ENABLED, false).apply();
                    updateShizukuStatusDisplay();
                }
            });
        }
    };

    // 监听 Shizuku 服务绑定成功
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        ShizukuManager.setBinderReady(true);
        runOnUiThread(this::updateShizukuStatusDisplay);
    };

    // 监听 Shizuku 服务断开
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        ShizukuManager.setBinderReady(false);
        runOnUiThread(this::updateShizukuStatusDisplay);
    };

    private static class KeywordItem implements Comparable<KeywordItem> {
        String packageName;
        String appName;
        String text;
        int type;

        KeywordItem(String pkg, String appName, String text, int type) {
            this.packageName = pkg;
            this.appName = appName;
            this.text = text;
            this.type = type;
        }

        @Override
        public int compareTo(KeywordItem o) {
            int appCompare = this.appName.compareTo(o.appName);
            if (appCompare != 0) return appCompare;
            if (this.type != o.type) return Integer.compare(this.type, o.type);
            return this.text.compareTo(o.text);
        }
    }

    private static class AppSpinnerItem {
        String packageName;
        String appName;
        AppSpinnerItem(String pkg, String name) { this.packageName = pkg; this.appName = name; }
        @Override public String toString() { return appName; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 沉浸式设置
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_assistant_manager);

        // 2. 适配内边距
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            final int originalPaddingTop = rootLayout.getPaddingTop();
            final int originalPaddingBottom = rootLayout.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        v.getPaddingLeft(),
                        originalPaddingTop + insets.top,
                        v.getPaddingRight(),
                        originalPaddingBottom + insets.bottom
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        config = new AssistantConfig(this);
        // 确保默认关键字已初始化
        KeywordManager.initDefaults(this);

        sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // 关键：在 super.onCreate 之后立即注册 Sticky 监听器
        registerShizukuListeners();

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();

        // ===== 新增：页面回到前台时更新 Shizuku 状态，并在已配置时尝试自愈拉起 =====
        updateShizukuStatusDisplay();
        if (sp != null && sp.getBoolean(KEY_SHIZUKU_ENABLED, false) && ShizukuManager.hasShizukuPermission()) {
            if (!isAccessibilitySettingsOn()) {
                enableKeepAliveAndAccessibility();
            }
        }
    }

    private void initViews() {
        switchAutoTrack = findViewById(R.id.switchAutoTrack);
//        switchRefundMonitor = findViewById(R.id.switchRefundMonitor);
        switchAssets = findViewById(R.id.switchAssets);
        switchDetails = findViewById(R.id.switchDetails); // 新增

        switchDetails.setChecked(config.isDetailsEnabled()); // 新增

        switchAutoTrack.setChecked(config.isEnabled());
//        switchRefundMonitor.setChecked(config.isRefundEnabled());
        switchAssets.setChecked(config.isAssetsEnabled());

        switchAutoTrack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setEnabled(isChecked);
            if (isChecked) {
                // 如果开启了 Shizuku 自动保活且拥有权限，则静默拉起；否则执行原系统弹窗逻辑
                if (sp.getBoolean(KEY_SHIZUKU_ENABLED, false) && ShizukuManager.hasShizukuPermission()) {
                    enableKeepAliveAndAccessibility();
                } else {
                    checkAccessibilityPermission();
                }
                Toast.makeText(this, "已开启屏幕自动记账", Toast.LENGTH_SHORT).show();
            }
        });

        // 新增开关监听
        switchDetails.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setDetailsEnabled(isChecked);
            Toast.makeText(this, "已" + (isChecked ? "开启" : "关闭") + "明细功能，重启应用后生效", Toast.LENGTH_LONG).show();
        });

        switchAssets.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setAssetsEnabled(isChecked);
            if (isChecked) {
                Toast.makeText(this, "已开启资产功能，重启应用后生效", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "已关闭资产功能，重启应用后生效", Toast.LENGTH_LONG).show();
            }
        });

        rvKeywords = findViewById(R.id.rvKeywords);
        rvKeywords.setLayoutManager(new LinearLayoutManager(this));
        adapter = new KeywordAdapter();
        rvKeywords.setAdapter(adapter);

        findViewById(R.id.btnAddKeyword).setOnClickListener(v -> {
            Intent intent = new Intent(AssistantManagerActivity.this, KeywordSettingActivity.class);
            startActivity(intent);
        });

        // ===== 新增 Shizuku 视图绑定与事件监听 =====
        switchShizukuKeepAlive = findViewById(R.id.switchShizukuKeepAlive);
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);

        if (switchShizukuKeepAlive != null) {
            boolean isConfigEnabled = sp.getBoolean(KEY_SHIZUKU_ENABLED, false);
            switchShizukuKeepAlive.setChecked(isConfigEnabled);

            switchShizukuKeepAlive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!buttonView.isPressed()) return; // 避免代码触发引发循环

                if (isChecked) {
                    handleEnableShizukuKeepAlive();
                } else {
                    sp.edit().putBoolean(KEY_SHIZUKU_ENABLED, false).apply();
                    Toast.makeText(this, "已关闭 Shizuku 自动保活", Toast.LENGTH_SHORT).show();
                    updateShizukuStatusDisplay();
                }
            });
        }
    }

    // ===== 新增 Shizuku 业务控制逻辑 =====
    private void registerShizukuListeners() {
        // 使用 Sticky 监听，即使在页面打开前 Binder 就已经连上了也能收到回调
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
    }

    private void updateShizukuStatusDisplay() {
        if (tvShizukuStatus == null) return;

        boolean isServiceAvailable = ShizukuManager.isShizukuAvailable();
        boolean hasPermission = ShizukuManager.hasShizukuPermission();
        boolean isAccessRunning = isAccessibilitySettingsOn();

        // 同步无障碍开关显示
        if (switchAutoTrack != null) {
            switchAutoTrack.setChecked(isAccessRunning);
        }

        if (!isServiceAvailable) {
            tvShizukuStatus.setText("Shizuku 未运行 (点击开关查看引导)");
            tvShizukuStatus.setTextColor(0xFFE53935);
            if (switchShizukuKeepAlive != null) {
                switchShizukuKeepAlive.setChecked(false);
            }
            return;
        }

        if (!hasPermission) {
            tvShizukuStatus.setText("Shizuku 运行中，尚未授予应用权限");
            tvShizukuStatus.setTextColor(0xFFFB8C00);
            if (switchShizukuKeepAlive != null) {
                switchShizukuKeepAlive.setChecked(false);
            }
            return;
        }

        if (isAccessRunning) {
            tvShizukuStatus.setText("保护已激活：后台豁免生效，无障碍运行正常");
            tvShizukuStatus.setTextColor(0xFF43A047);
        } else {
            tvShizukuStatus.setText("已获得 Shizuku 权限，无障碍待激活");
            tvShizukuStatus.setTextColor(0xFFFB8C00);
        }
    }

    private void handleEnableShizukuKeepAlive() {
        if (!ShizukuManager.isShizukuAvailable()) {
            if (switchShizukuKeepAlive != null) {
                switchShizukuKeepAlive.setChecked(false);
            }
            showShizukuNotRunningDialog();
            return;
        }

        if (!ShizukuManager.hasShizukuPermission()) {
            ShizukuManager.requestPermission(REQUEST_CODE_SHIZUKU);
            return;
        }

        enableKeepAliveAndAccessibility();
    }

    private void enableKeepAliveAndAccessibility() {
        new Thread(() -> {
            ShizukuManager.grantBackgroundKeepAlive(this);
            boolean success = ShizukuManager.enableAccessibilityService(this);

            runOnUiThread(() -> {
                if (success) {
                    sp.edit().putBoolean(KEY_SHIZUKU_ENABLED, true).apply();
                    if (switchShizukuKeepAlive != null) {
                        switchShizukuKeepAlive.setChecked(true);
                    }
                    if (switchAutoTrack != null) {
                        switchAutoTrack.setChecked(true);
                    }
                    config.setEnabled(true);
                    Toast.makeText(this, "后台保护与无障碍权限已静默激活！", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "激活失败，请检查 Shizuku 权限及 ADB 权限", Toast.LENGTH_SHORT).show();
                    if (switchShizukuKeepAlive != null) {
                        switchShizukuKeepAlive.setChecked(false);
                    }
                }
                updateShizukuStatusDisplay();
            });
        }).start();
    }

    private void showShizukuNotRunningDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Shizuku 未运行")
                .setMessage("Shizuku 是一个能通过 ADB 无线调试或 Root 赋予应用系统权限的工具。\n\n如需免跳转自动开启无障碍并保活后台，请先安装并启动 Shizuku。")
                .setPositiveButton("打开/下载 Shizuku", (dialog, which) -> {
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        if (intent != null) {
                            startActivity(intent);
                        } else {
                            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"));
                            startActivity(marketIntent);
                        }
                    } catch (Exception e) {
                        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"));
                        startActivity(webIntent);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void checkAccessibilityPermission() {
        if (!isAccessibilitySettingsOn()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_permission_request, null);
            builder.setView(view);
            AlertDialog dialog = builder.create();

            if (dialog.getWindow() != null) {
                // 背景透明以显示圆角
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            // 绑定数据
            TextView tvTitle = view.findViewById(R.id.tv_permission_title);
            TextView tvMessage = view.findViewById(R.id.tv_permission_message);
            tvTitle.setText("需要开启辅助服务");
            tvMessage.setText("屏幕自动记账需要开启'记账屏幕同步助手'服务。");

            // 取消按钮：不仅关闭弹窗，还要把开关关掉
            view.findViewById(R.id.btn_cancel_permission).setOnClickListener(v -> {
                switchAutoTrack.setChecked(false);
                dialog.dismiss();
            });

            // 去授权按钮
            view.findViewById(R.id.btn_grant_permission).setOnClickListener(v -> {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                dialog.dismiss();
            });

            dialog.show();
        }
    }

    private boolean isAccessibilitySettingsOn() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + SelectToSpeakService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) { }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                return settingValue.contains(service);
            }
        }
        return false;
    }

    private void loadData() {
        dataList.clear();
        // 使用 KeywordManager 的新方法
        Map<String, Set<String>> incomeMap = KeywordManager.getIncomeKeywords(this);
        Map<String, Set<String>> expenseMap = KeywordManager.getExpenseKeywords(this);
        Map<String, String> apps = KeywordManager.getSupportedApps();

        // 整理收入关键字
        for (Map.Entry<String, Set<String>> entry : incomeMap.entrySet()) {
            String pkg = entry.getKey();
            String appName = apps.getOrDefault(pkg, pkg);
            for (String kw : entry.getValue()) {
                dataList.add(new KeywordItem(pkg, appName, kw, KeywordManager.TYPE_INCOME));
            }
        }
        // 整理支出关键字
        for (Map.Entry<String, Set<String>> entry : expenseMap.entrySet()) {
            String pkg = entry.getKey();
            String appName = apps.getOrDefault(pkg, pkg);
            for (String kw : entry.getValue()) {
                dataList.add(new KeywordItem(pkg, appName, kw, KeywordManager.TYPE_EXPENSE));
            }
        }
        Collections.sort(dataList);
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(KeywordItem oldItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 加载更新后的布局
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_keyword, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        // 设置透明背景，显示圆角
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 获取控件
        Spinner spApp = view.findViewById(R.id.sp_app);
        RadioGroup rgType = view.findViewById(R.id.rg_type);
        EditText etKeyword = view.findViewById(R.id.et_keyword);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSave = view.findViewById(R.id.btn_save);

        // 设置 Spinner 数据
        List<AppSpinnerItem> spinnerItems = new ArrayList<>();
        Map<String, String> apps = KeywordManager.getSupportedApps();

        int selectedIndex = 0;
        int i = 0;
        for (Map.Entry<String, String> entry : apps.entrySet()) {
            spinnerItems.add(new AppSpinnerItem(entry.getKey(), entry.getValue()));
            if (oldItem != null && entry.getKey().equals(oldItem.packageName)) {
                selectedIndex = i;
            }
            i++;
        }
        ArrayAdapter<AppSpinnerItem> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerItems);
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spApp.setAdapter(spinnerAdapter);
        spApp.setSelection(selectedIndex);

        // 回显数据
        if (oldItem.type == KeywordManager.TYPE_EXPENSE) {
            rgType.check(R.id.rb_expense);
        } else {
            rgType.check(R.id.rb_income);
        }
        etKeyword.setText(oldItem.text);

        // 按钮事件
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newText = etKeyword.getText().toString().trim();
            if (newText.isEmpty()) {
                Toast.makeText(this, "请输入关键字", Toast.LENGTH_SHORT).show();
                return;
            }
            AppSpinnerItem selectedApp = (AppSpinnerItem) spApp.getSelectedItem();
            int newType = (rgType.getCheckedRadioButtonId() == R.id.rb_expense)
                    ? KeywordManager.TYPE_EXPENSE : KeywordManager.TYPE_INCOME;

            // 先移除旧的
            if (oldItem.type == KeywordManager.TYPE_INCOME) {
                KeywordManager.removeIncomeKeyword(this, oldItem.packageName, oldItem.text);
            } else {
                KeywordManager.removeExpenseKeyword(this, oldItem.packageName, oldItem.text);
            }

            // 添加新的
            KeywordManager.addKeyword(this, selectedApp.packageName, newType, newText);
            loadData();
            Toast.makeText(this, "修改成功", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // 替换原有的 deleteItem 方法
    private void deleteItem(KeywordItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        // 设置背景透明，以便显示圆角
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 初始化控件
        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        TextView tvMsg = view.findViewById(R.id.tv_dialog_message);

        tvTitle.setText("删除关键字");
        tvMsg.setText("确定删除 \"" + item.appName + "\" 的关键字 [" + item.text + "] 吗？");

        // 绑定按钮事件
        view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            // 执行删除逻辑
            if (item.type == KeywordManager.TYPE_INCOME) {
                KeywordManager.removeIncomeKeyword(this, item.packageName, item.text);
            } else {
                KeywordManager.removeExpenseKeyword(this, item.packageName, item.text);
            }
            loadData();
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ===== 新增：注销 Shizuku 监听，防止内存泄露 =====
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (Exception ignored) {}
    }

    class KeywordAdapter extends RecyclerView.Adapter<KeywordAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            KeywordItem item = dataList.get(position);
            holder.text1.setText(item.text);
            holder.text1.setTextSize(16);
            holder.text1.setTextColor(ContextCompat.getColor(AssistantManagerActivity.this, R.color.text_primary));
            String typeStr = (item.type == KeywordManager.TYPE_EXPENSE) ? "支出触发词" : "收入触发词";
            String info = "[" + item.appName + "]  " + typeStr;
            holder.text2.setText(info);
            if (item.type == KeywordManager.TYPE_EXPENSE) {
                holder.text2.setTextColor(ContextCompat.getColor(AssistantManagerActivity.this, R.color.expense_green));
            } else {
                holder.text2.setTextColor(ContextCompat.getColor(AssistantManagerActivity.this, R.color.income_red));
            }
            holder.itemView.setOnClickListener(v -> showEditDialog(item));
            holder.itemView.setOnLongClickListener(v -> {
                deleteItem(item);
                return true;
            });
        }
        @Override
        public int getItemCount() { return dataList.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView text1, text2;
            VH(View v) { super(v); text1 = v.findViewById(android.R.id.text1); text2 = v.findViewById(android.R.id.text2); }
        }
    }
}