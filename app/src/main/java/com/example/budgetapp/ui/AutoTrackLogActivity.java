package com.example.budgetapp.ui;

import android.content.ClipData;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.util.AutoTrackLogManager;
import java.util.ArrayList;
import java.util.List;

import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

public class AutoTrackLogActivity extends AppCompatActivity {
    private RecyclerView rvLogs;
    private Spinner spinnerPackage;
    private Switch switchCapture;
    private LogAdapter adapter;
    private String currentFilter = "全部";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 优化1：实现状态栏和底部小白条的全面沉浸
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_auto_track_log);

        // 动态处理沉浸式边距 (保证16dp间距的基础上，不被刘海和小白条遮挡)
        View rootLayout = findViewById(R.id.root_layout);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            int padding16 = (int) (16 * getResources().getDisplayMetrics().density); // 16dp 转 px
            v.setPadding(
                    systemBars.left + padding16,
                    systemBars.top + padding16,
                    systemBars.right + padding16,
                    systemBars.bottom + padding16
            );
            return insets;
        });

        rvLogs = findViewById(R.id.rv_logs);
        spinnerPackage = findViewById(R.id.spinner_package);
        switchCapture = findViewById(R.id.switch_capture);

        // 注意：这里已经删掉了 btn_back 的点击事件绑定 (优化2)

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            AutoTrackLogManager.clearLogs();
            currentFilter = "全部";
            refreshData();
        });

        findViewById(R.id.btn_copy).setOnClickListener(v -> copyCurrentLogs());

        // 绑定抓取开关
        switchCapture.setChecked(AutoTrackLogManager.isLogEnabled);
        switchCapture.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AutoTrackLogManager.isLogEnabled = isChecked;
        });

        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogAdapter();
        rvLogs.setAdapter(adapter);

        setupSpinner();

        AutoTrackLogManager.setObserver(() -> runOnUiThread(this::refreshData));
        refreshData();
    }

    // 增加：用于下拉框显示通俗应用名的包装类
    private static class AppSpinnerItem {
        String packageName;
        String appName;

        AppSpinnerItem(String packageName, String appName) {
            this.packageName = packageName;
            this.appName = appName;
        }

        @Override
        public String toString() {
            return appName; // 下拉框会自动调用 toString() 来显示文字
        }
    }

    // 增加：通过包名获取应用真实名称的方法
    private String getAppName(String packageName) {
        if ("全部".equals(packageName)) return "全部应用";
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return packageName; // 如果找不到，退回显示包名
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AutoTrackLogManager.setObserver(null);
    }

    private void setupSpinner() {
        // 将 ArrayAdapter 的泛型从 String 改为 AppSpinnerItem
        ArrayAdapter<AppSpinnerItem> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPackage.setAdapter(spinnerAdapter);

        spinnerPackage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AppSpinnerItem selectedItem = (AppSpinnerItem) parent.getItemAtPosition(position);
                if (selectedItem != null && !selectedItem.packageName.equals(currentFilter)) {
                    currentFilter = selectedItem.packageName; // 底层依然使用包名过滤
                    refreshData();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void refreshData() {
        List<String> rawPackages = AutoTrackLogManager.getPackages();

        // 将包名列表转换为包含通俗应用名的包装类列表
        List<AppSpinnerItem> newItems = new ArrayList<>();
        for (String pkg : rawPackages) {
            newItems.add(new AppSpinnerItem(pkg, getAppName(pkg)));
        }

        ArrayAdapter<AppSpinnerItem> spinnerAdapter = (ArrayAdapter<AppSpinnerItem>) spinnerPackage.getAdapter();

        if (spinnerAdapter.getCount() != newItems.size()) {
            spinnerAdapter.clear();
            spinnerAdapter.addAll(newItems);
            spinnerAdapter.notifyDataSetChanged();

            // 确保更新下拉列表后，依然选中用户正在看的那个 App
            int selIndex = 0;
            for (int i = 0; i < newItems.size(); i++) {
                if (newItems.get(i).packageName.equals(currentFilter)) {
                    selIndex = i;
                    break;
                }
            }
            spinnerPackage.setSelection(selIndex);
        }

        // 获取过滤后的日志
        List<AutoTrackLogManager.LogEntry> logs = AutoTrackLogManager.getLogs(currentFilter);
        adapter.setLogs(logs);

        if (!logs.isEmpty()) {
            rvLogs.scrollToPosition(logs.size() - 1);
        }
    }

    private void copyCurrentLogs() {
        List<AutoTrackLogManager.LogEntry> logs = AutoTrackLogManager.getLogs(currentFilter);
        if (logs == null || logs.isEmpty()) {
            Toast.makeText(this, "当前没有可复制的日志", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- Tally 自动记账适配日志 ---\n");
        sb.append("筛选应用: ").append(getAppName(currentFilter)).append("\n\n");

        for (AutoTrackLogManager.LogEntry entry : logs) {
            // 保留原本的格式，方便粘贴给其他人看
            sb.append("[").append(entry.time).append("] ").append(entry.message).append("\n");
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Tally_AutoTrack_Log", sb.toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制 " + logs.size() + " 条日志到剪贴板", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "复制失败，无法获取剪贴板服务", Toast.LENGTH_SHORT).show();
        }
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {
        private List<AutoTrackLogManager.LogEntry> logs = new ArrayList<>();

        public void setLogs(List<AutoTrackLogManager.LogEntry> logs) {
            this.logs = logs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 使用 LinearLayout 包裹，方便动态设置大间距
            android.widget.LinearLayout layout = new android.widget.LinearLayout(parent.getContext());
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            layout.addView(tv);
            return new LogViewHolder(layout, tv);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            AutoTrackLogManager.LogEntry entry = logs.get(position);

            // 【新增】：动态检测当前是否为夜间模式 (Dark Mode)
            boolean isNightMode = (holder.tv.getContext().getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;

            if (entry.message.startsWith("►►►") || entry.message.startsWith("==========")) {
                // 【页面分割线】
                holder.tv.setText(String.format("%s\n(点击复制本页节点)", entry.message));
                // 夜间模式用浅蓝色 (0xFF66B2FF)，白天用系统蓝 (0xFF007FFF)
                holder.tv.setTextColor(isNightMode ? 0xFF66B2FF : 0xFF007FFF);
                holder.tv.setTextSize(13f);
                holder.tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                holder.tv.setGravity(android.view.Gravity.CENTER);
                // 夜间模式背景也要做对应的浅蓝色透明处理
                holder.tv.setBackgroundColor(isNightMode ? 0x2666B2FF : 0x1A007FFF);
                holder.tv.setPadding(16, 16, 16, 16);

                holder.layout.setPadding(0, 24, 0, 8);
                holder.layout.setBackgroundColor(android.graphics.Color.TRANSPARENT);

                // 点击提取并复制单页日志
                holder.tv.setOnClickListener(v -> {
                    int currentPos = holder.getAdapterPosition();
                    if (currentPos == RecyclerView.NO_POSITION) return;

                    StringBuilder sb = new StringBuilder();
                    sb.append("--- Tally 单页适配日志 ---\n");
                    sb.append("[").append(entry.time).append("] ").append(entry.message).append("\n\n");

                    int count = 0;
                    for (int i = currentPos + 1; i < logs.size(); i++) {
                        AutoTrackLogManager.LogEntry subEntry = logs.get(i);
                        if (subEntry.message.startsWith("►►►") || subEntry.message.startsWith("==========")) break;
                        sb.append("[").append(subEntry.time).append("] ").append(subEntry.message).append("\n");
                        count++;
                    }

                    if (count == 0) {
                        android.widget.Toast.makeText(v.getContext(), "该页面未抓取到有效节点", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) v.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Tally_Page_Log", sb.toString());
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        android.widget.Toast.makeText(v.getContext(), "已复制本页 " + count + " 条节点(含时间戳)", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });

            } else {
                // 【普通的节点树日志】
                holder.tv.setText(entry.message);
                // 夜间模式用浅灰偏白色文字，白天用深灰色文字
                holder.tv.setTextColor(isNightMode ? 0xFFDDDDDD : 0xFF333333);
                holder.tv.setTextSize(12f);
                holder.tv.setTypeface(android.graphics.Typeface.DEFAULT);
                holder.tv.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                holder.tv.setPadding(16, 6, 16, 6);

                // 【斑马纹交替背景色】夜间模式使用极浅的透明白色，白天使用极浅的透明黑色
                if (position % 2 == 0) {
                    holder.tv.setBackgroundColor(isNightMode ? 0x0AFFFFFF : 0x0A000000);
                } else {
                    holder.tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }

                holder.layout.setPadding(0, 0, 0, 0);
                holder.tv.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() { return logs.size(); }

        static class LogViewHolder extends RecyclerView.ViewHolder {
            android.widget.LinearLayout layout;
            TextView tv;
            LogViewHolder(android.widget.LinearLayout layout, TextView tv) {
                super(layout);
                this.layout = layout;
                this.tv = tv;
            }
        }
    }
}