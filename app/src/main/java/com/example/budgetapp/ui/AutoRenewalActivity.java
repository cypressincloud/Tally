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
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.RenewalItem;
import com.example.budgetapp.util.AssistantConfig;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
        setupImmersion(); // 实现状态栏与小白条沉浸
        setContentView(R.layout.activity_auto_renewal);

        config = new AssistantConfig(this);
        rvRenewalList = findViewById(R.id.rv_renewal_list);
        layoutEmpty = findViewById(R.id.layout_empty);

        // 绑定“添加续费提醒”按钮
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

    /**
     * 新增/修改续费提醒弹窗 (居中悬浮窗样式，参考 RecordFragment)
     */
    private void showRenewalEditDialog(RenewalItem item, int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_renewal, null);
        builder.setView(view);

        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        RadioGroup rgPeriod = view.findViewById(R.id.rg_period);
        TextView tvDateSelect = view.findViewById(R.id.tv_date_select);
        EditText etObject = view.findViewById(R.id.et_renewal_object);
        EditText etAmount = view.findViewById(R.id.et_renewal_amount);
        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);

        if (item != null) {
            tvTitle.setText("修改续费提醒");
            etObject.setText(item.object);
            etAmount.setText(String.format(Locale.CHINA, "%.2f", item.amount));
            rgPeriod.check("Year".equals(item.period) ? R.id.rb_year : R.id.rb_month);
            updateDateText(tvDateSelect, "Year".equals(item.period), item.month, item.day);
        }

        final int[] date = {item != null ? item.month : 1, item != null ? item.day : 1};
        tvDateSelect.setOnClickListener(v -> showDatePicker(rgPeriod.getCheckedRadioButtonId() == R.id.rb_year, date, tvDateSelect));

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
            saveItem.period = rgPeriod.getCheckedRadioButtonId() == R.id.rb_year ? "Year" : "Month";
            saveItem.month = date[0];
            saveItem.day = date[1];

            if (position == -1) renewalList.add(saveItem);
            else renewalList.set(position, saveItem);

            config.saveRenewalList(renewalList);
            adapter.notifyDataSetChanged();
            updateUIState();
            dialog.dismiss();
        });
        dialog.show();
    }

    /**
     * 日期选择器：复刻统计/记账页面的强制边距实现方式
     */
    private void showDatePicker(boolean isYearly, int[] date, TextView target) {
        final BottomSheetDialog dateDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_date_picker, null);
        dateDialog.setContentView(view);

        // 1. 强制在代码中实现外边距并支持点击空白退出
        dateDialog.setCanceledOnTouchOutside(true);
        dateDialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // 彻底移除原生容器背景
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                // 强制注入 Padding 以确保 XML 中的 16dp 悬浮边距可见
                int margin = (int) (16 * getResources().getDisplayMetrics().density);
                bottomSheet.setPadding(margin, 0, margin, margin);
            }
        });

        // 2. 引用不可修改布局中的 ID
        View containerYear = view.findViewById(R.id.container_year);
        View containerMonth = view.findViewById(R.id.container_month);
        NumberPicker npMonth = view.findViewById(R.id.np_month);
        NumberPicker npDay = view.findViewById(R.id.np_day);
        TextView tvPreview = view.findViewById(R.id.tv_date_preview);

        // 3. 容器显隐逻辑：始终隐藏年；周期为月时隐藏月（包含单位文字）
        if (containerYear != null) containerYear.setVisibility(View.GONE);
        if (containerMonth != null) {
            containerMonth.setVisibility(isYearly ? View.VISIBLE : View.GONE);
        }

        // 4. 初始化数值与数值改变监听 (同步统计页预览风格)
        npMonth.setMinValue(1); npMonth.setMaxValue(12); npMonth.setValue(date[0]);
        npDay.setMinValue(1); npDay.setMaxValue(31); npDay.setValue(date[1]);

        NumberPicker.OnValueChangeListener listener = (p, oldV, newV) -> {
            if (tvPreview != null) {
                updatePreviewText(tvPreview, isYearly, npMonth.getValue(), npDay.getValue());
            }
        };
        npMonth.setOnValueChangedListener(listener);
        npDay.setOnValueChangedListener(listener);
        updatePreviewText(tvPreview, isYearly, date[0], date[1]);

        // 5. 绑定按钮点击事件
        view.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            date[0] = npMonth.getValue();
            date[1] = npDay.getValue();
            updateDateText(target, isYearly, date[0], date[1]);
            dateDialog.dismiss();
        });

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dateDialog.dismiss());
        dateDialog.show();
    }

    private void updatePreviewText(TextView tv, boolean isYearly, int month, int day) {
        if (tv == null) return;
        String text = isYearly ?
                String.format(Locale.CHINA, "每年 %d月%d日", month, day) :
                String.format(Locale.CHINA, "每月 %d日", day);
        tv.setText(text);
    }

    private void updateDateText(TextView tv, boolean isYear, int m, int d) {
        tv.setText(isYear ? String.format(Locale.CHINA, "%d月%d日", m, d) : String.format(Locale.CHINA, "每月%d日", d));
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

    private class RenewalAdapter extends RecyclerView.Adapter<RenewalAdapter.ViewHolder> {
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_renewal_card, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RenewalItem item = renewalList.get(position);
            String cycle = "Year".equals(item.period) ? "每年" : "每月";
            String dateStr = "Year".equals(item.period) ? item.month + "月" + item.day + "日" : item.day + "日";
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