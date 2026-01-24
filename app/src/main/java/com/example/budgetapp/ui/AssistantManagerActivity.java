package com.example.budgetapp.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat; // 关键导入
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AssistantManagerActivity extends AppCompatActivity {

    private AssistantConfig config;
    
    // 使用 SwitchCompat
    private SwitchCompat switchAutoTrack;
    private SwitchCompat switchRefundMonitor;
    private SwitchCompat switchAssets;
    
    private RecyclerView rvKeywords;
    private KeywordAdapter adapter;
    private List<KeywordItem> dataList = new ArrayList<>();

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
        
        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData(); 
        if (switchRefundMonitor != null) {
            if (config.isRefundEnabled() && !isNotificationListenerEnabled()) {
                // 可选：提醒用户权限缺失
            }
        }
    }

    private void initViews() {
        switchAutoTrack = findViewById(R.id.switchAutoTrack);
        switchRefundMonitor = findViewById(R.id.switchRefundMonitor);
        switchAssets = findViewById(R.id.switchAssets);

        switchAutoTrack.setChecked(config.isEnabled());
        switchRefundMonitor.setChecked(config.isRefundEnabled());
        switchAssets.setChecked(config.isAssetsEnabled());

        switchAutoTrack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setEnabled(isChecked);
            if (isChecked) {
                checkAccessibilityPermission();
                Toast.makeText(this, "已开启屏幕自动记账", Toast.LENGTH_SHORT).show();
            }
        });

        switchRefundMonitor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setRefundEnabled(isChecked);
            if (isChecked) {
                checkNotificationPermission(); 
                Toast.makeText(this, "已开启退款监听", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已关闭退款监听", Toast.LENGTH_SHORT).show();
            }
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
    }

    private void checkAccessibilityPermission() {
        if (!isAccessibilitySettingsOn()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要开启辅助服务")
                    .setMessage("屏幕自动记账需要开启'记账屏幕同步助手'服务。")
                    .setPositiveButton("去开启", (d, w) -> {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    })
                    .setNegativeButton("取消", (d, w) -> switchAutoTrack.setChecked(false))
                    .show();
        }
    }

    private boolean isAccessibilitySettingsOn() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + com.example.budgetapp.service.AutoTrackAccessibilityService.class.getCanonicalName();
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

    private void checkNotificationPermission() {
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("为了监听微信/支付宝的退款通知，请授予“通知使用权”。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (Exception e) {
                        Toast.makeText(this, "无法打开设置页，请手动前往设置", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", (d, w) -> switchRefundMonitor.setChecked(false))
                .show();
        }
    }

    private boolean isNotificationListenerEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
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

    private void deleteItem(KeywordItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("删除");
        builder.setMessage("确定删除 \"" + item.appName + "\" 的关键字 [" + item.text + "] 吗？");
        builder.setPositiveButton("删除", (d, w) -> {
            // 使用 KeywordManager 的新方法
            if (item.type == KeywordManager.TYPE_INCOME) {
                KeywordManager.removeIncomeKeyword(this, item.packageName, item.text);
            } else {
                KeywordManager.removeExpenseKeyword(this, item.packageName, item.text);
            }
            loadData();
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
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