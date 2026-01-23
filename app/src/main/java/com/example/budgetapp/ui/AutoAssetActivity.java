package com.example.budgetapp.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.appcompat.widget.SwitchCompat; // 关键导入
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.util.AutoAssetManager;
import com.example.budgetapp.util.KeywordManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AutoAssetActivity extends AppCompatActivity {

    private SwitchCompat switchAutoAsset;
    private RecyclerView rvRules;
    private RuleAdapter adapter;
    private List<AutoAssetManager.AssetRule> ruleList = new ArrayList<>();

    private List<AssetAccount> cachedAssets = new ArrayList<>();
    private List<AppItem> cachedApps = new ArrayList<>();

    private static class AppItem {
        String packageName;
        String appName;
        AppItem(String pkg, String name) { this.packageName = pkg; this.appName = name; }
        @Override public String toString() { return appName; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 沉浸式设置
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_auto_asset);

        // 2. 适配内边距 (防止被状态栏/导航栏遮挡)
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

        initViews();
        loadData();
    }

    private void initViews() {
        switchAutoAsset = findViewById(R.id.switchAutoAsset);
        switchAutoAsset.setChecked(AutoAssetManager.isEnabled(this));
        switchAutoAsset.setOnCheckedChangeListener((v, isChecked) -> {
            AutoAssetManager.setEnabled(this, isChecked);
            String msg = isChecked ? "已开启自动资产关联" : "已关闭自动资产关联";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        rvRules = findViewById(R.id.rvRules);
        rvRules.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RuleAdapter();
        rvRules.setAdapter(adapter);

        findViewById(R.id.btnAddRule).setOnClickListener(v -> showAddRuleDialog());
    }

    private void loadData() {
        ruleList = AutoAssetManager.getRules(this);
        adapter.notifyDataSetChanged();
        Map<String, String> apps = KeywordManager.getSupportedApps();
        cachedApps.clear();
        for (Map.Entry<String, String> entry : apps.entrySet()) {
            cachedApps.add(new AppItem(entry.getKey(), entry.getValue()));
        }

        // 异步加载资产数据
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<AssetAccount> assets = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(0);
            runOnUiThread(() -> {
                cachedAssets.clear();
                if (assets != null) cachedAssets.addAll(assets);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void showAddRuleDialog() {
        if (cachedAssets.isEmpty()) {
            Toast.makeText(this, "暂无资产或资产数据加载中...", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新增关联规则");
        buildDialogView(builder, null);
    }

    private void showEditRuleDialog(AutoAssetManager.AssetRule oldRule) {
        if (cachedAssets.isEmpty()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑关联规则");
        buildDialogView(builder, oldRule);
    }

    private void buildDialogView(AlertDialog.Builder builder, AutoAssetManager.AssetRule oldRule) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(60, 40, 60, 20);

        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.topMargin = 30;

        TextView labelApp = new TextView(this);
        labelApp.setText("生效应用:");
        labelApp.setTextColor(Color.GRAY);
        container.addView(labelApp);

        final Spinner spinnerApp = new Spinner(this);
        spinnerApp.setBackground(null);
        // 如果你有 bg_input_field 资源
        spinnerApp.setPopupBackgroundResource(R.drawable.bg_input_field);
        spinnerApp.setPadding(0, 0, 0, 0);
        container.addView(spinnerApp, spinnerParams);

        ArrayAdapter<AppItem> appAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cachedApps);
        appAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerApp.setAdapter(appAdapter);

        TextView labelAsset = new TextView(this);
        labelAsset.setText("\n关联资产:");
        labelAsset.setTextColor(Color.GRAY);
        container.addView(labelAsset);

        final Spinner spinnerAsset = new Spinner(this);
        spinnerAsset.setBackground(null);
        spinnerAsset.setPopupBackgroundResource(R.drawable.bg_input_field);
        spinnerAsset.setPadding(0, 0, 0, 0);
        container.addView(spinnerAsset, spinnerParams);

        List<String> assetNames = new ArrayList<>();
        for(AssetAccount a : cachedAssets) assetNames.add(a.name);

        ArrayAdapter<String> assetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, assetNames);
        assetAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerAsset.setAdapter(assetAdapter);

        TextView labelKw = new TextView(this);
        labelKw.setText("\n屏幕关键字 (包含即触发):");
        labelKw.setTextColor(Color.GRAY);
        container.addView(labelKw);

        final EditText etKeyword = new EditText(this);
        etKeyword.setHint("例如: 招商银行");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.topMargin = 20;
        etKeyword.setPadding(24, 24, 24, 24);
        container.addView(etKeyword, inputParams);

        if (oldRule != null) {
            etKeyword.setText(oldRule.keyword);
            for (int i = 0; i < cachedApps.size(); i++) {
                if (cachedApps.get(i).packageName.equals(oldRule.packageName)) {
                    spinnerApp.setSelection(i);
                    break;
                }
            }
            for (int i = 0; i < cachedAssets.size(); i++) {
                if (cachedAssets.get(i).id == oldRule.assetId) {
                    spinnerAsset.setSelection(i);
                    break;
                }
            }
        }

        builder.setView(container);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String keyword = etKeyword.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入关键字", Toast.LENGTH_SHORT).show();
                return;
            }
            AppItem selectedApp = (AppItem) spinnerApp.getSelectedItem();
            if (selectedApp == null || cachedAssets.isEmpty()) return;

            int selectedAssetIndex = spinnerAsset.getSelectedItemPosition();
            if (selectedAssetIndex < 0 || selectedAssetIndex >= cachedAssets.size()) return;
            AssetAccount selectedAsset = cachedAssets.get(selectedAssetIndex);

            AutoAssetManager.AssetRule newRule = new AutoAssetManager.AssetRule(selectedApp.packageName, keyword, selectedAsset.id);
            if (oldRule != null) {
                AutoAssetManager.removeRule(this, oldRule);
            }
            AutoAssetManager.addRule(this, newRule);
            loadData();
            String msg = (oldRule != null) ? "规则已更新" : "规则已添加";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // 适配对话框按钮颜色
        int primaryColor = ContextCompat.getColor(this, R.color.text_primary);
        Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (btnPos != null) btnPos.setTextColor(primaryColor);
        if (btnNeg != null) btnNeg.setTextColor(primaryColor);
    }

    private String getAssetNameById(int id) {
        for (AssetAccount a : cachedAssets) {
            if (a.id == id) return a.name;
        }
        return "未知资产(ID:" + id + ")";
    }

    private String getAppNameByPkg(String pkg) {
        for (AppItem item : cachedApps) {
            if (item.packageName.equals(pkg)) return item.appName;
        }
        return "未知应用";
    }

    class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AutoAssetManager.AssetRule rule = ruleList.get(position);
            String appName = getAppNameByPkg(rule.packageName);
            holder.text1.setText("[" + appName + "] 关键字: " + rule.keyword);
            holder.text1.setTextColor(ContextCompat.getColor(AutoAssetActivity.this, R.color.text_primary));
            holder.text1.setTextSize(16);
            String assetName = getAssetNameById(rule.assetId);
            holder.text2.setText("自动关联 -> " + assetName);
            holder.text2.setTextColor(ContextCompat.getColor(AutoAssetActivity.this, R.color.app_yellow));

            holder.itemView.setOnClickListener(v -> showEditRuleDialog(rule));
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(AutoAssetActivity.this)
                        .setTitle("删除规则")
                        .setMessage("确定删除该条规则吗？")
                        .setPositiveButton("删除", (d, w) -> {
                            AutoAssetManager.removeRule(AutoAssetActivity.this, rule);
                            loadData();
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });
        }
        @Override
        public int getItemCount() { return ruleList.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView text1, text2;
            VH(View v) { super(v); text1 = v.findViewById(android.R.id.text1); text2 = v.findViewById(android.R.id.text2); }
        }
    }
}