// src/main/java/com/example/budgetapp/ui/AutoRenewalActivity.java
package com.example.budgetapp.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.RenewalItem;
import com.example.budgetapp.util.AssistantConfig;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AutoRenewalActivity extends AppCompatActivity {
    private AssistantConfig config;
    private RecyclerView rvRenewalList;
    private View layoutEmpty;
    private RenewalAdapter adapter;
    private List<RenewalItem> renewalList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupImmersion();
        setContentView(R.layout.activity_auto_renewal);

        View rootView = findViewById(R.id.root_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        config = new AssistantConfig(this);
        rvRenewalList = findViewById(R.id.rv_renewal_list);
        layoutEmpty = findViewById(R.id.layout_empty);

        findViewById(R.id.btn_add_renewal).setOnClickListener(v -> showRenewalEditDialog(null, -1));

        setupRecyclerView();
    }

    private void setupImmersion() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightStatusBars(true);
    }

    private void showRenewalEditDialog(RenewalItem item, int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_renewal, null);
        builder.setView(view);

        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        RadioGroup rgPeriod = view.findViewById(R.id.rg_period);
        TextView tvDateLabel = view.findViewById(R.id.tv_date_label);
        TextView tvDateSelect = view.findViewById(R.id.tv_date_select);
        EditText etObject = view.findViewById(R.id.et_renewal_object);
        EditText etAmount = view.findViewById(R.id.et_renewal_amount);
        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);

        // 自定义时长组件
        LinearLayout llCustomDuration = view.findViewById(R.id.ll_custom_duration);
        EditText etDurationValue = view.findViewById(R.id.et_duration_value);
        Spinner spDurationUnit = view.findViewById(R.id.sp_duration_unit);

        // 配置 Spinner 适配器 (使用统一的下拉框样式)
        String[] units = {"天", "周", "月", "年"};
        String[] unitKeys = {"Day", "Week", "Month", "Year"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_dropdown, units);
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spDurationUnit.setAdapter(spinnerAdapter);

        // 监听 RadioGroup 切换 UI
        rgPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_custom) {
                tvDateLabel.setText("起算日期");
                llCustomDuration.setVisibility(View.VISIBLE);
            } else {
                tvDateLabel.setText("扣费日期");
                llCustomDuration.setVisibility(View.GONE);
            }
            // 切换时刷新日期预览文本
            String currentPeriod = checkedId == R.id.rb_year ? "Year" : (checkedId == R.id.rb_custom ? "Custom" : "Month");
            updateDateText(tvDateSelect, currentPeriod, (int)tvDateSelect.getTag(R.id.np_year), (int)tvDateSelect.getTag(R.id.np_month), (int)tvDateSelect.getTag(R.id.np_day));
        });

        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);

        if (item != null) {
            tvTitle.setText("修改续费提醒");
            etObject.setText(item.object);
            etAmount.setText(String.format(Locale.CHINA, "%.2f", item.amount));

            if ("Custom".equals(item.period)) {
                rgPeriod.check(R.id.rb_custom);
                etDurationValue.setText(String.valueOf(item.durationValue));
                for (int i = 0; i < unitKeys.length; i++) {
                    if (unitKeys[i].equals(item.durationUnit)) {
                        spDurationUnit.setSelection(i);
                        break;
                    }
                }
            } else if ("Year".equals(item.period)) {
                rgPeriod.check(R.id.rb_year);
            } else {
                rgPeriod.check(R.id.rb_month);
            }
        }

        // 使用 Tag 暂存日期数据 [0:year, 1:month, 2:day]
        final int[] date = {
                item != null && item.year > 0 ? item.year : currentYear,
                item != null ? item.month : cal.get(Calendar.MONTH) + 1,
                item != null ? item.day : cal.get(Calendar.DAY_OF_MONTH)
        };

        tvDateSelect.setTag(R.id.np_year, date[0]);
        tvDateSelect.setTag(R.id.np_month, date[1]);
        tvDateSelect.setTag(R.id.np_day, date[2]);

        String initPeriod = rgPeriod.getCheckedRadioButtonId() == R.id.rb_year ? "Year" : (rgPeriod.getCheckedRadioButtonId() == R.id.rb_custom ? "Custom" : "Month");
        updateDateText(tvDateSelect, initPeriod, date[0], date[1], date[2]);

        tvDateSelect.setOnClickListener(v -> {
            String period = rgPeriod.getCheckedRadioButtonId() == R.id.rb_year ? "Year" : (rgPeriod.getCheckedRadioButtonId() == R.id.rb_custom ? "Custom" : "Month");
            showDatePicker(period, date, tvDateSelect);
        });

        view.findViewById(R.id.btn_save_config).setOnClickListener(v -> {
            String objStr = etObject.getText().toString().trim();
            String amtStr = etAmount.getText().toString().trim();
            if (objStr.isEmpty() || amtStr.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                return;
            }

            RenewalItem saveItem = (item != null) ? item : new RenewalItem();
            saveItem.object = objStr;
            saveItem.amount = Float.parseFloat(amtStr);

            int checkedId = rgPeriod.getCheckedRadioButtonId();
            if (checkedId == R.id.rb_custom) {
                saveItem.period = "Custom";
                String durStr = etDurationValue.getText().toString().trim();
                saveItem.durationValue = durStr.isEmpty() ? 1 : Integer.parseInt(durStr);
                saveItem.durationUnit = unitKeys[spDurationUnit.getSelectedItemPosition()];
            } else {
                saveItem.period = checkedId == R.id.rb_year ? "Year" : "Month";
            }

            saveItem.year = date[0];
            saveItem.month = date[1];
            saveItem.day = date[2];

            if (position == -1) renewalList.add(saveItem);
            else renewalList.set(position, saveItem);

            config.saveRenewalList(renewalList);
            adapter.notifyDataSetChanged();
            updateUIState();
            dialog.dismiss();
        });
        dialog.show();
    }
    private void showDatePicker(String periodType, int[] date, TextView target) {
        final BottomSheetDialog dateDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_date_picker, null);
        dateDialog.setContentView(view);

        dateDialog.setCanceledOnTouchOutside(true);
        dateDialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                int margin = (int) (16 * getResources().getDisplayMetrics().density);
                bottomSheet.setPadding(margin, 0, margin, margin);
            }
        });

        View containerYear = view.findViewById(R.id.container_year);
        View containerMonth = view.findViewById(R.id.container_month);
        NumberPicker npYear = view.findViewById(R.id.np_year);
        NumberPicker npMonth = view.findViewById(R.id.np_month);
        NumberPicker npDay = view.findViewById(R.id.np_day);
        TextView tvPreview = view.findViewById(R.id.tv_date_preview);

        boolean isCustom = "Custom".equals(periodType);
        boolean showMonth = isCustom || "Year".equals(periodType);

        if (containerYear != null) containerYear.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        if (containerMonth != null) containerMonth.setVisibility(showMonth ? View.VISIBLE : View.GONE);

        if (npYear != null) {
            npYear.setMinValue(2000);
            npYear.setMaxValue(2100);
            npYear.setValue(date[0]);
        }
        npMonth.setMinValue(1); npMonth.setMaxValue(12); npMonth.setValue(date[1]);
        npDay.setMinValue(1); npDay.setMaxValue(31); npDay.setValue(date[2]);

        NumberPicker.OnValueChangeListener listener = (p, oldV, newV) -> {
            if (tvPreview != null) {
                int y = npYear != null ? npYear.getValue() : date[0];
                updatePreviewText(tvPreview, periodType, y, npMonth.getValue(), npDay.getValue());
            }
        };

        if (npYear != null) npYear.setOnValueChangedListener(listener);
        npMonth.setOnValueChangedListener(listener);
        npDay.setOnValueChangedListener(listener);
        updatePreviewText(tvPreview, periodType, date[0], date[1], date[2]);

        view.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            if (npYear != null) date[0] = npYear.getValue();
            date[1] = npMonth.getValue();
            date[2] = npDay.getValue();

            target.setTag(R.id.np_year, date[0]);
            target.setTag(R.id.np_month, date[1]);
            target.setTag(R.id.np_day, date[2]);

            updateDateText(target, periodType, date[0], date[1], date[2]);
            dateDialog.dismiss();
        });

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dateDialog.dismiss());
        dateDialog.show();
    }

    private void updatePreviewText(TextView tv, String periodType, int y, int m, int d) {
        if (tv == null) return;
        if ("Custom".equals(periodType)) {
            tv.setText(String.format(Locale.CHINA, "%d年%d月%d日 起算", y, m, d));
        } else if ("Year".equals(periodType)) {
            tv.setText(String.format(Locale.CHINA, "每年 %d月%d日", m, d));
        } else {
            tv.setText(String.format(Locale.CHINA, "每月 %d日", d));
        }
    }

    private void updateDateText(TextView tv, String periodType, int y, int m, int d) {
        if ("Custom".equals(periodType)) {
            tv.setText(String.format(Locale.CHINA, "%d年%d月%d日", y, m, d));
        } else if ("Year".equals(periodType)) {
            tv.setText(String.format(Locale.CHINA, "%d月%d日", m, d));
        } else {
            tv.setText(String.format(Locale.CHINA, "每月%d日", d));
        }
    }

    private void setupRecyclerView() {
        renewalList = config.getRenewalList();
        adapter = new RenewalAdapter();
        rvRenewalList.setLayoutManager(new LinearLayoutManager(this));
        rvRenewalList.setAdapter(adapter);
        updateUIState();
    }

    private void updateUIState() {
        boolean isEmpty = renewalList.isEmpty();
        rvRenewalList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void showUnitPicker(int[] selectedIndex, String[] units, TextView target) {
        BottomSheetDialog unitDialog = new BottomSheetDialog(this);

        // 动态创建布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 48, 0, 48);
        layout.setBackgroundResource(R.drawable.bg_bottom_sheet_rounded);

        TextView title = new TextView(this);
        title.setText("选择时间单位");
        title.setTextSize(18);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, 48);
        layout.addView(title);

        // 循环添加选项
        for (int i = 0; i < units.length; i++) {
            int index = i;
            TextView item = new TextView(this);
            item.setText(units[i]);
            item.setTextSize(16);
            item.setGravity(android.view.Gravity.CENTER);
            item.setPadding(0, 32, 0, 32);

            // 选中项高亮显示 (黄色)
            if (index == selectedIndex[0]) {
                item.setTextColor(getResources().getColor(R.color.app_yellow));
                item.getPaint().setFakeBoldText(true);
            } else {
                item.setTextColor(getResources().getColor(R.color.text_primary));
                item.getPaint().setFakeBoldText(false);
            }

            item.setOnClickListener(v -> {
                selectedIndex[0] = index;
                target.setText(units[index]); // 更新UI上的文字
                unitDialog.dismiss();
            });
            layout.addView(item);
        }

        unitDialog.setContentView(layout);

        // 保持与日期选择器一致的悬浮透明背景效果
        View bottomSheet = (View) layout.getParent();
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(android.R.color.transparent);
            int margin = (int) (16 * getResources().getDisplayMetrics().density);
            bottomSheet.setPadding(margin, 0, margin, margin);
        }

        unitDialog.show();
    }

    private class RenewalAdapter extends RecyclerView.Adapter<RenewalAdapter.ViewHolder> {
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_renewal_card, p, false));
        }

        private String getUnitTranslation(String unit) {
            switch(unit) {
                case "Day": return "天";
                case "Week": return "周";
                case "Month": return "月"; // 之前是 "个月"
                case "Year": return "年";
                default: return "";
            }
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RenewalItem item = renewalList.get(position);
            String cycle;
            String dateStr;

            if ("Custom".equals(item.period)) {
                cycle = "每" + item.durationValue + getUnitTranslation(item.durationUnit);
                dateStr = item.year + "-" + item.month + "-" + item.day + " 起算";
            } else if ("Year".equals(item.period)) {
                cycle = "每年";
                dateStr = item.month + "月" + item.day + "日";
            } else {
                cycle = "每月";
                dateStr = item.day + "日";
            }

            holder.tvInfo.setText(String.format(Locale.CHINA, "%s\n金额: %.2f\n周期: %s (%s)", item.object, item.amount, cycle, dateStr));
            holder.itemView.setOnClickListener(v -> showRenewalEditDialog(item, position));
            holder.btnDel.setOnClickListener(v -> {
                renewalList.remove(position);
                config.saveRenewalList(renewalList);
                notifyDataSetChanged();
                updateUIState();
            });
        }
        @Override public int getItemCount() { return renewalList.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvInfo; ImageButton btnDel;
            ViewHolder(View v) { super(v); tvInfo = v.findViewById(R.id.tv_info); btnDel = v.findViewById(R.id.btn_del); }
        }
    }
}