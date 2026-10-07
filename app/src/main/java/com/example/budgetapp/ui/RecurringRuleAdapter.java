package com.example.budgetapp.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.RecurringRule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 周期记账规则列表适配器
 */
public class RecurringRuleAdapter extends RecyclerView.Adapter<RecurringRuleAdapter.ViewHolder> {

    private final Context context;
    private List<RecurringRule> rules = new ArrayList<>();
    private List<AssetAccount> assets = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onEditClick(RecurringRule rule);
        void onDeleteClick(RecurringRule rule);
        void onToggleChange(RecurringRule rule, boolean isEnabled);
    }

    public RecurringRuleAdapter(Context context) {
        this.context = context;
    }

    public void setRules(List<RecurringRule> rules, List<AssetAccount> assets) {
        this.rules = rules != null ? rules : new ArrayList<>();
        this.assets = assets != null ? assets : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recurring_rule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecurringRule rule = rules.get(position);

        // 1. 设置分类名称
        String categoryText = rule.category;
        if (rule.subCategory != null && !rule.subCategory.trim().isEmpty()) {
            categoryText += " - " + rule.subCategory;
        }
        holder.tvCategoryName.setText(categoryText);

        // 2. 周期频次 Badge (如: 每 1 个月 / 每 2 周)
        String periodDesc = "每 " + rule.periodInterval + " " + getPeriodUnitText(rule.periodUnit);
        holder.tvCycleBadge.setText(periodDesc);

        // 3. 左侧指示条、金额符号与色彩
        String currencySymbol = !TextUtils.isEmpty(rule.currencySymbol) ? rule.currencySymbol : "¥";
        if (rule.type == 1) { // 收入
            int incomeColor = ContextCompat.getColor(context, R.color.income_red);
            holder.viewTypeIndicator.setBackgroundColor(incomeColor);
            holder.tvAmount.setTextColor(incomeColor);
            holder.tvAmount.setText("+" + currencySymbol + String.format(Locale.getDefault(), "%.2f", rule.amount));
        } else { // 支出
            int expenseColor = ContextCompat.getColor(context, R.color.expense_green);
            holder.viewTypeIndicator.setBackgroundColor(expenseColor);
            holder.tvAmount.setTextColor(expenseColor);
            holder.tvAmount.setText("-" + currencySymbol + String.format(Locale.getDefault(), "%.2f", rule.amount));
        }

        // 4. 账户名称匹配
        String assetName = "未知账户";
        for (AssetAccount asset : assets) {
            if (asset.id == rule.assetId) {
                assetName = asset.name;
                break;
            }
        }
        holder.tvAccountName.setText(assetName);

        // 5. 备注处理
        if (rule.note != null && !rule.note.trim().isEmpty()) {
            holder.tvRemarkDivider.setVisibility(View.VISIBLE);
            holder.tvRemark.setVisibility(View.VISIBLE);
            holder.tvRemark.setText(rule.note);
        } else {
            holder.tvRemarkDivider.setVisibility(View.GONE);
            holder.tvRemark.setVisibility(View.GONE);
        }

        // 6. 下次执行日期及生效时间
        long nextDate = calculateNextDate(rule);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String nextDateStr = sdf.format(nextDate);
        String triggerTimeStr = !TextUtils.isEmpty(rule.triggerTime) ? rule.triggerTime : "00:00";
        holder.tvTriggerInfo.setText("下次: " + nextDateStr + " (" + triggerTimeStr + " 生效)");

        // 7. 启用状态与卡片透明度 (停用时置灰弱化)
        holder.itemView.setAlpha(rule.isEnabled ? 1.0f : 0.45f);
        holder.switchRuleActive.setOnCheckedChangeListener(null);
        holder.switchRuleActive.setChecked(rule.isEnabled);
        holder.switchRuleActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rule.isEnabled = isChecked;
            holder.itemView.setAlpha(isChecked ? 1.0f : 0.45f);
            if (listener != null) {
                listener.onToggleChange(rule, isChecked);
            }
        });

        // 8. 交互事件绑定
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(rule);
            }
        });

        holder.btnEditRule.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(rule);
            }
        });

        holder.btnDeleteRule.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(rule);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(rule);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    /**
     * 计算下次生效日期
     */
    private long calculateNextDate(RecurringRule rule) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(rule.startDate);
        long now = System.currentTimeMillis();

        // 如果开始日期在未来, 直接返回
        if (rule.startDate > now) {
            return rule.startDate;
        }

        // 向后推进直到找到第一个未来的时间点
        while (calendar.getTimeInMillis() <= now) {
            advanceToNextPeriod(calendar, rule.periodUnit, rule.periodInterval);
        }

        // 检查是否超过结束日期
        if (rule.endDate > 0 && calendar.getTimeInMillis() > rule.endDate) {
            return rule.endDate;
        }

        return calendar.getTimeInMillis();
    }

    /**
     * 前进到下一个周期
     */
    private void advanceToNextPeriod(Calendar calendar, String periodUnit, int interval) {
        if (periodUnit == null) return;
        switch (periodUnit.toUpperCase()) {
            case "DAY":
                calendar.add(Calendar.DAY_OF_MONTH, interval);
                break;
            case "WEEK":
                calendar.add(Calendar.WEEK_OF_YEAR, interval);
                break;
            case "MONTH":
                calendar.add(Calendar.MONTH, interval);
                break;
            case "YEAR":
                calendar.add(Calendar.YEAR, interval);
                break;
        }
    }

    /**
     * 获取周期单位文本
     */
    private String getPeriodUnitText(String unit) {
        if (unit == null) return "";
        switch (unit.toUpperCase()) {
            case "DAY": return "天";
            case "WEEK": return "周";
            case "MONTH": return "个月";
            case "YEAR": return "年";
            default: return unit;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View viewTypeIndicator;
        TextView tvCategoryName;
        TextView tvCycleBadge;
        TextView tvAmount;
        ImageView ivAssetIcon;
        TextView tvAccountName;
        TextView tvRemarkDivider;
        TextView tvRemark;
        TextView tvTriggerInfo;
        ImageButton btnEditRule;
        ImageButton btnDeleteRule;
        SwitchCompat switchRuleActive;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewTypeIndicator = itemView.findViewById(R.id.view_type_indicator);
            tvCategoryName = itemView.findViewById(R.id.tv_category_name);
            tvCycleBadge = itemView.findViewById(R.id.tv_cycle_badge);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            ivAssetIcon = itemView.findViewById(R.id.iv_asset_icon);
            tvAccountName = itemView.findViewById(R.id.tv_account_name);
            tvRemarkDivider = itemView.findViewById(R.id.tv_remark_divider);
            tvRemark = itemView.findViewById(R.id.tv_remark);
            tvTriggerInfo = itemView.findViewById(R.id.tv_trigger_info);
            btnEditRule = itemView.findViewById(R.id.btn_edit_rule);
            btnDeleteRule = itemView.findViewById(R.id.btn_delete_rule);
            switchRuleActive = itemView.findViewById(R.id.switch_rule_active);
        }
    }
}