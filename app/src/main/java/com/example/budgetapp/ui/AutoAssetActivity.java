package com.example.budgetapp.ui;

import android.content.Context;
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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
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

    private Switch switchAutoAsset;
    private RecyclerView rvRules;
    private RuleAdapter adapter;
    private List<AutoAssetManager.AssetRule> ruleList = new ArrayList<>();
    
    // 缓存数据用于弹窗
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
        setContentView(R.layout.activity_auto_asset);

        LinearLayout rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            final int originalPaddingTop = rootLayout.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), originalPaddingTop + insets.top, v.getPaddingRight(), v.getPaddingBottom());
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
        // 1. 先加载规则 (同步操作)
        ruleList = AutoAssetManager.getRules(this);
        adapter.notifyDataSetChanged();

        // 2. 加载应用列表
        Map<String, String> apps = KeywordManager.getSupportedApps();
        cachedApps.clear();
        for (Map.Entry<String, String> entry : apps.entrySet()) {
            cachedApps.add(new AppItem(entry.getKey(), entry.getValue()));
        }

        // 3. 异步加载资产数据
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<AssetAccount> assets = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(0); // 只取资产账户
            
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
        // 复用构建视图的逻辑
        buildDialogView(builder, null);
    }

    // 新增：编辑规则弹窗
    private void showEditRuleDialog(AutoAssetManager.AssetRule oldRule) {
        if (cachedAssets.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑关联规则");
        buildDialogView(builder, oldRule);
    }

    // 统一构建弹窗视图（用于新增和编辑）
    private void buildDialogView(AlertDialog.Builder builder, AutoAssetManager.AssetRule oldRule) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(60, 40, 60, 20);

        // 应用选择
        TextView labelApp = new TextView(this);
        labelApp.setText("生效应用:");
        labelApp.setTextColor(Color.GRAY);
        container.addView(labelApp);

        // ... (应用选择 Spinner)
        final Spinner spinnerApp = new Spinner(this);
        // 【修改】只设置弹出背景
        spinnerApp.setPopupBackgroundResource(R.drawable.bg_input_field);

        ArrayAdapter<AppItem> appAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cachedApps);
        // 如果想让下拉里的 Item 样式更好看，建议保留这行；如果只想改框体圆角，这行保持原样即可
        appAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerApp.setAdapter(appAdapter);
        container.addView(spinnerApp);

        // 资产选择
        TextView labelAsset = new TextView(this);
        labelAsset.setText("\n关联资产:");
        labelAsset.setTextColor(Color.GRAY);
        container.addView(labelAsset);

        final Spinner spinnerAsset = new Spinner(this);

        spinnerAsset.setPopupBackgroundResource(R.drawable.bg_input_field);

        List<String> assetNames = new ArrayList<>();
        for(AssetAccount a : cachedAssets) assetNames.add(a.name);
        
        ArrayAdapter<String> assetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, assetNames);
        assetAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerAsset.setAdapter(assetAdapter);
        container.addView(spinnerAsset);

        // 关键字输入
        TextView labelKw = new TextView(this);
        labelKw.setText("\n屏幕关键字 (包含即触发):");
        labelKw.setTextColor(Color.GRAY);
        container.addView(labelKw);

        final EditText etKeyword = new EditText(this);
        etKeyword.setHint("例如: 招商银行");
        container.addView(etKeyword);

        // --- 回显数据 (如果是编辑模式) ---
        if (oldRule != null) {
            // 1. 回显关键字
            etKeyword.setText(oldRule.keyword);
            
            // 2. 回显应用
            for (int i = 0; i < cachedApps.size(); i++) {
                if (cachedApps.get(i).packageName.equals(oldRule.packageName)) {
                    spinnerApp.setSelection(i);
                    break;
                }
            }

            // 3. 回显资产
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
            
            // 如果是编辑，先删除旧的
            if (oldRule != null) {
                AutoAssetManager.removeRule(this, oldRule);
            }
            
            AutoAssetManager.addRule(this, newRule);
            
            loadData(); // 刷新列表
            String msg = (oldRule != null) ? "规则已更新" : "规则已添加";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
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

    // --- Adapter ---
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

            // 单击编辑
            holder.itemView.setOnClickListener(v -> showEditRuleDialog(rule));

            // 长按删除
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