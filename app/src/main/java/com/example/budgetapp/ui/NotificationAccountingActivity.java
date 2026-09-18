package com.example.budgetapp.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.util.NotificationRuleManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Settings for notification accounting rules, using the same card/list pattern as other settings pages. */
public class NotificationAccountingActivity extends AppCompatActivity {
    private LinearLayout rulesContainer;
    private final List<AppItem> apps = new ArrayList<>();
    private final List<AssetAccount> assets = new ArrayList<>();

    private static final class AppItem {
        final String packageName;
        final String label;
        AppItem(String packageName, String label) { this.packageName = packageName; this.label = label; }
        @Override public String toString() { return label; }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_notification_accounting);

        View root = findViewById(R.id.root_layout);
        final int top = root.getPaddingTop();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), top + bars.top, view.getPaddingRight(), bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        SwitchCompat enabled = findViewById(R.id.switch_notification_accounting);
        enabled.setChecked(NotificationRuleManager.isEnabled(this));
        enabled.setOnCheckedChangeListener((button, checked) -> NotificationRuleManager.setEnabled(this, checked));
        SwitchCompat directPostNotification = findViewById(R.id.switch_direct_post_notification);
        directPostNotification.setChecked(NotificationRuleManager.isDirectPostNotificationEnabled(this));
        directPostNotification.setOnCheckedChangeListener((button, checked) ->
                NotificationRuleManager.setDirectPostNotificationEnabled(this, checked));
        findViewById(R.id.btn_notification_permission).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        View addRuleButton = findViewById(R.id.btn_add_notification_rule);
        addRuleButton.setEnabled(false);
        addRuleButton.setOnClickListener(v -> showRuleDialog());
        rulesContainer = findViewById(R.id.notification_rules_container);

        loadApps(addRuleButton);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<AssetAccount> loaded = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(0);
            runOnUiThread(() -> {
                if (loaded != null) assets.addAll(loaded);
                addRuleButton.setEnabled(!apps.isEmpty() && !assets.isEmpty());
                refreshRules();
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (rulesContainer != null) refreshRules();
    }

    private void loadApps(View addRuleButton) {
        android.content.pm.PackageManager pm = getPackageManager();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<AppItem> loadedApps = new ArrayList<>();
            List<android.content.pm.ApplicationInfo> installed = pm.getInstalledApplications(0);
            for (android.content.pm.ApplicationInfo info : installed) {
                if (info.packageName.equals(getPackageName()) || pm.getLaunchIntentForPackage(info.packageName) == null) continue;
                loadedApps.add(new AppItem(info.packageName, pm.getApplicationLabel(info).toString()));
            }
            Collections.sort(loadedApps, Comparator.comparing(item -> item.label));
            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(loadedApps);
                addRuleButton.setEnabled(!apps.isEmpty() && !assets.isEmpty());
            });
        });
    }

    private void refreshRules() {
        if (rulesContainer == null) return;
        rulesContainer.removeAllViews();
        List<NotificationRuleManager.Rule> rules = NotificationRuleManager.getRules(this);
        if (rules.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有通知记账规则");
            empty.setTextColor(Color.GRAY);
            empty.setPadding(0, 12, 0, 12);
            rulesContainer.addView(empty);
            return;
        }
        for (NotificationRuleManager.Rule rule : rules) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_notification_rule, rulesContainer, false);
            TextView title = row.findViewById(R.id.tv_rule_title);
            TextView summary = row.findViewById(R.id.tv_rule_summary);
            title.setText(rule.appName + " · " + (rule.type == 1 ? "收入" : "支出"));
            String mode = rule.directPost ? "直接记账" : "确认后记账";
            summary.setText(rule.regex + "\n" + mode + " · 防误触 " + (rule.delayMs / 1000L) + " 秒");
            row.setOnClickListener(v -> showRuleDialog(rule));
            row.setOnLongClickListener(v -> {
                View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
                AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
                TextView dialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
                TextView dialogMessage = dialogView.findViewById(R.id.tv_dialog_message);
                dialogTitle.setText("删除通知规则");
                dialogMessage.setText("确定删除“" + rule.appName + "”的这条规则吗？");
                dialogView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(view -> dialog.dismiss());
                dialogView.findViewById(R.id.btn_dialog_confirm).setOnClickListener(view -> {
                    List<NotificationRuleManager.Rule> current = NotificationRuleManager.getRules(this);
                    current.removeIf(item -> item.id.equals(rule.id));
                    NotificationRuleManager.saveRules(this, current);
                    dialog.dismiss();
                    refreshRules();
                });
                dialog.show();
                return true;
            });
            rulesContainer.addView(row);
        }
    }

    private void showRuleDialog() { showRuleDialog(null); }

    private void showRuleDialog(NotificationRuleManager.Rule oldRule) {
        if (apps.isEmpty() || assets.isEmpty()) {
            Toast.makeText(this, "应用或资产数据加载中，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_notification_rule, null);
        Spinner app = view.findViewById(R.id.sp_notification_app);
        Spinner type = view.findViewById(R.id.sp_notification_type);
        Spinner asset = view.findViewById(R.id.sp_notification_asset);
        EditText regex = view.findViewById(R.id.et_notification_regex);
        EditText delay = view.findViewById(R.id.et_notification_delay);
        SwitchCompat direct = view.findViewById(R.id.switch_notification_direct);
        TextView title = view.findViewById(R.id.tv_title);
        Button cancel = view.findViewById(R.id.btn_notification_cancel);
        Button save = view.findViewById(R.id.btn_notification_save);
        title.setText(oldRule == null ? "添加通知规则" : "编辑通知规则");

        ArrayAdapter<AppItem> appAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_dropdown, apps);
        appAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        app.setAdapter(appAdapter);
        type.setAdapter(new ArrayAdapter<>(this, R.layout.item_spinner_dropdown, new String[]{"支出", "收入"}));
        ArrayAdapter<AssetAccount> assetAdapter = new ArrayAdapter<AssetAccount>(this, R.layout.item_spinner_dropdown, assets) {
            private View bindName(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                View result = super.getView(position, convertView, parent);
                if (result instanceof TextView) ((TextView) result).setText(getItem(position).name);
                return result;
            }
            @NonNull @Override public View getView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                return bindName(position, convertView, parent);
            }
            @NonNull @Override public View getDropDownView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                return bindName(position, convertView, parent);
            }
        };
        assetAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        asset.setAdapter(assetAdapter);

        if (oldRule != null) {
            regex.setText(oldRule.regex);
            delay.setText(String.valueOf(oldRule.delayMs / 1000L));
            direct.setChecked(oldRule.directPost);
            type.setSelection(oldRule.type);
            for (int i = 0; i < apps.size(); i++) if (apps.get(i).packageName.equals(oldRule.packageName)) app.setSelection(i);
            for (int i = 0; i < assets.size(); i++) if (assets.get(i).id == oldRule.assetId) asset.setSelection(i);
        } else {
            delay.setText("5");
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String expression = regex.getText().toString().trim();
            if (expression.isEmpty()) { regex.setError("请输入金额正则表达式"); return; }
            try { java.util.regex.Pattern.compile(expression); } catch (Exception e) { regex.setError("正则表达式无效"); return; }
            NotificationRuleManager.Rule rule = oldRule == null ? new NotificationRuleManager.Rule() : oldRule.copy();
            AppItem selectedApp = (AppItem) app.getSelectedItem();
            rule.id = oldRule == null ? String.valueOf(System.nanoTime()) : oldRule.id;
            rule.packageName = selectedApp.packageName; rule.appName = selectedApp.label; rule.regex = expression;
            rule.type = type.getSelectedItemPosition(); rule.assetId = ((AssetAccount) asset.getSelectedItem()).id; rule.directPost = direct.isChecked();
            try { rule.delayMs = Math.max(0L, Long.parseLong(delay.getText().toString().trim())) * 1000L; } catch (Exception e) { delay.setError("请输入秒数"); return; }
            List<NotificationRuleManager.Rule> rules = NotificationRuleManager.getRules(this);
            if (oldRule != null) rules.removeIf(item -> item.id.equals(oldRule.id));
            rules.add(rule); NotificationRuleManager.saveRules(this, rules); refreshRules(); dialog.dismiss();
        });
        dialog.show();
    }
}
