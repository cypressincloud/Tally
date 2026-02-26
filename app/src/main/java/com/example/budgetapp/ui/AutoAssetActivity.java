package com.example.budgetapp.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
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

        // 点击新增规则
        findViewById(R.id.btnAddRule).setOnClickListener(v -> showRuleDialog(null));
    }

    private void loadData() {
        ruleList = AutoAssetManager.getRules(this);
        adapter.notifyDataSetChanged();
        Map<String, String> apps = KeywordManager.getSupportedApps();
        cachedApps.clear();
        for (Map.Entry<String, String> entry : apps.entrySet()) {
            cachedApps.add(new AppItem(entry.getKey(), entry.getValue()));
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 【修改】同时查询资产(0)和负债(1)
            List<AssetAccount> assets = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(0);
            List<AssetAccount> liabilities = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(1);

            List<AssetAccount> mergedList = new ArrayList<>();
            if (assets != null) mergedList.addAll(assets);
            if (liabilities != null) mergedList.addAll(liabilities);

            runOnUiThread(() -> {
                cachedAssets.clear();
                cachedAssets.addAll(mergedList);
                adapter.notifyDataSetChanged();
            });
        });
    }
    // 统一的新增/编辑弹窗方法
    private void showRuleDialog(AutoAssetManager.AssetRule oldRule) {
        if (cachedAssets.isEmpty()) {
            Toast.makeText(this, "暂无资产或资产数据加载中...", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 加载自定义布局
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_asset_rule, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        // 关键：背景透明，显示圆角
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_title);
        tvTitle.setText(oldRule == null ? "新增关联规则" : "编辑关联规则");

        Spinner spApp = view.findViewById(R.id.sp_app);
        Spinner spAsset = view.findViewById(R.id.sp_asset);
        EditText etKeyword = view.findViewById(R.id.et_keyword);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSave = view.findViewById(R.id.btn_save);

        // 1. 设置 App Spinner
        ArrayAdapter<AppItem> appAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cachedApps);
        appAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spApp.setAdapter(appAdapter);

        // 2. 设置 Asset Spinner
        List<String> assetNames = new ArrayList<>();
        for(AssetAccount a : cachedAssets) assetNames.add(a.name);
        ArrayAdapter<String> assetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, assetNames);
        assetAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spAsset.setAdapter(assetAdapter);

        // 3. 回显旧数据
        if (oldRule != null) {
            etKeyword.setText(oldRule.keyword);
            for (int i = 0; i < cachedApps.size(); i++) {
                if (cachedApps.get(i).packageName.equals(oldRule.packageName)) {
                    spApp.setSelection(i);
                    break;
                }
            }
            for (int i = 0; i < cachedAssets.size(); i++) {
                if (cachedAssets.get(i).id == oldRule.assetId) {
                    spAsset.setSelection(i);
                    break;
                }
            }
        }

        // 4. 按钮事件
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String keyword = etKeyword.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入关键字", Toast.LENGTH_SHORT).show();
                return;
            }
            AppItem selectedApp = (AppItem) spApp.getSelectedItem();
            if (selectedApp == null) return;

            int selectedAssetIndex = spAsset.getSelectedItemPosition();
            if (selectedAssetIndex < 0 || selectedAssetIndex >= cachedAssets.size()) return;
            AssetAccount selectedAsset = cachedAssets.get(selectedAssetIndex);

            AutoAssetManager.AssetRule newRule = new AutoAssetManager.AssetRule(selectedApp.packageName, keyword, selectedAsset.id);
            
            // 先删旧的，再加新的（因为没有ID，靠equals判断）
            if (oldRule != null) {
                AutoAssetManager.removeRule(this, oldRule);
            }
            AutoAssetManager.addRule(this, newRule);
            loadData();
            
            String msg = (oldRule != null) ? "规则已更新" : "规则已添加";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
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

            // 点击编辑 -> 调用统一弹窗
            holder.itemView.setOnClickListener(v -> showRuleDialog(rule));

            holder.itemView.setOnLongClickListener(v -> {
                // --- 修改开始: 使用自定义弹窗 ---
                AlertDialog.Builder builder = new AlertDialog.Builder(AutoAssetActivity.this);
                View dialogView = LayoutInflater.from(AutoAssetActivity.this).inflate(R.layout.dialog_confirm_delete, null);
                builder.setView(dialogView);
                AlertDialog dialog = builder.create();

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
                TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_message);

                tvTitle.setText("删除规则");
                tvMsg.setText("确定删除对 [" + appName + "] 的这条关联规则吗？");

                dialogView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(dv -> dialog.dismiss());
                dialogView.findViewById(R.id.btn_dialog_confirm).setOnClickListener(dv -> {
                    AutoAssetManager.removeRule(AutoAssetActivity.this, rule);
                    loadData();
                    dialog.dismiss();
                });

                dialog.show();
                // --- 修改结束 ---
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