package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssetIconHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionListAdapter extends RecyclerView.Adapter<TransactionListAdapter.ViewHolder> {
    private List<Transaction> list = new ArrayList<>();
    private Map<Integer, AssetAccount> assetMap = new HashMap<>();
    private OnItemClickListener listener;
    private LocalDate displayDate;

    public interface OnItemClickListener {
        void onItemClick(Transaction transaction);
    }

    public TransactionListAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setTransactions(List<Transaction> list) {
        this.list = list;
        notifyDataSetChanged();
    }

    public void setDisplayDate(LocalDate date) {
        displayDate = date;
        notifyDataSetChanged();
    }

    // 设置资产数据
    public void setAssets(List<AssetAccount> assets) {
        assetMap.clear();
        if (assets != null) {
            for (AssetAccount asset : assets) {
                assetMap.put(asset.id, asset);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_detail, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction t = list.get(position);
        Context context = holder.itemView.getContext();

        // 基础配置：货币单位与符号
        boolean showCurrency = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("enable_currency", false);

        String symbol = (t.currencySymbol != null && !t.currencySymbol.isEmpty()) ? t.currencySymbol : "¥";
        double shownAmount = t.displayAmount != null ? t.displayAmount
                : (displayDate == null ? t.amount
                : com.example.budgetapp.util.BudgetCalculator.amountForDay(t, displayDate));
        String amountStr = String.format("%.2f", shownAmount);
        String displayAmount = showCurrency ? (symbol + " " + amountStr) : amountStr;

        // --- 核心逻辑：自动续费预览账单处理 ---
        boolean isPreview = "PREVIEW_BILL".equals(t.remark);

        if (isPreview) {
            // 获取账单对应的日期
            LocalDate billDate = java.time.Instant.ofEpochMilli(t.date)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            LocalDate today = LocalDate.now();

            if (billDate.isAfter(today)) {
                // 没到这个日期的时候，金额颜色是灰色，不显示边框
                holder.tvAmount.setTextColor(Color.LTGRAY);
                holder.itemView.setBackgroundResource(0); // 清除背景，防止复用错乱
            } else {
                // 到了(过了)这个日期后，变为正常颜色（由于续费是支出，使用绿色），并加上红边框提醒
                holder.tvAmount.setTextColor(context.getColor(R.color.expense_green));
                holder.itemView.setBackgroundResource(R.drawable.bg_budget_exceed);
            }
            holder.tvAmount.setText("-" + displayAmount);
            holder.tvNote.setAlpha(0.6f); // 预览项文字稍微淡化以示区分
        } else {
            // 正常入库记录清除背景，防止复用错乱
            holder.itemView.setBackgroundResource(0);

            // 正常入库记录的颜色逻辑
            holder.tvNote.setAlpha(1.0f);
            if (t.type == 2) {
                // 🌟 资产转移
                holder.tvAmount.setTextColor(context.getColor(R.color.app_blue));
                holder.tvAmount.setText(displayAmount);
            } else if (t.type == 1) {
                // 收入
                holder.tvAmount.setTextColor(context.getColor(R.color.income_red));
                holder.tvAmount.setText("+" + displayAmount);
            } else {
                // 支出
                holder.tvAmount.setTextColor(context.getColor(R.color.expense_green));
                holder.tvAmount.setText("-" + displayAmount);
            }
        }

        // 分类与二级分类显示
        holder.tvDate.setText(t.category);
        if (t.subCategory != null && !t.subCategory.isEmpty()) {
            holder.tvSubCategory.setText(t.subCategory);
            holder.tvSubCategory.setVisibility(View.VISIBLE);
        } else {
            holder.tvSubCategory.setVisibility(View.GONE);
        }

        // 备注标识
        if (t.note != null && !t.note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(t.note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
            holder.tvNote.setTextColor(context.getColor(R.color.text_secondary));
        }
        if (holder.tvAmortization != null) holder.tvAmortization.setVisibility(View.GONE);
        if ((displayDate != null || t.displayAmount != null)
                && t.spreadStartDate > 0 && t.spreadEndDate >= t.spreadStartDate) {
            LocalDate start = java.time.Instant.ofEpochMilli(t.spreadStartDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            LocalDate end = java.time.Instant.ofEpochMilli(t.spreadEndDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            long spreadDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            if (holder.tvAmortization != null && spreadDays > 1) {
                holder.tvAmortization.setVisibility(View.VISIBLE);
                holder.tvAmortization.setText(String.format("%d天均摊 %.2f", spreadDays, t.amount));
            }
        }

        // --- 右下角状态指示器 ---
        // 逻辑：如果有文字备注（非预览标识）或有照片，显示绿色，否则红色
        boolean hasRemark = !TextUtils.isEmpty(t.remark) && !isPreview;
        boolean hasPhoto = !TextUtils.isEmpty(t.photoPath);

        int statusColor = (hasRemark || hasPhoto)
                ? context.getColor(R.color.expense_green)
                : context.getColor(R.color.income_red);

        // 资产名称映射处理
        AssetAccount assetAccount = (t.assetId != 0 && assetMap != null) ? assetMap.get(t.assetId) : null;
        String assetName = assetAccount != null ? assetAccount.name : null;

        if (assetName != null) {
            holder.viewIndicator.setVisibility(View.GONE);
            holder.llAssetInfo.setVisibility(View.VISIBLE);
            holder.tvAssetName.setText(assetName);
            // 资产名称颜色表达交易类型；备注/照片状态只用于右侧指示点。
            holder.tvAssetName.setTextColor(t.type == 1
                    ? context.getColor(R.color.income_red)
                    : t.type == 2 ? context.getColor(R.color.app_blue)
                    : context.getColor(R.color.expense_green));
            
            // 显示资产图标
            if (AssetIconHelper.bindSvgIcon(holder.ivAssetIcon, assetAccount.svgIcon)) {
                holder.ivAssetIcon.setVisibility(View.VISIBLE);
            } else {
                holder.ivAssetIcon.setVisibility(View.GONE);
            }
        } else {
            holder.llAssetInfo.setVisibility(View.GONE);
            holder.viewIndicator.setVisibility(View.VISIBLE);
            holder.viewIndicator.setBackgroundColor(statusColor);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(t.displaySource != null ? t.displaySource : t);
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvAmount, tvNote;
        TextView tvAmortization;
        View viewIndicator;

        TextView tvSubCategory;
        TextView tvAssetName;
        android.widget.ImageView ivAssetIcon;
        android.widget.LinearLayout llAssetInfo;

        ViewHolder(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tv_detail_date);
            tvSubCategory = v.findViewById(R.id.tv_detail_sub_category);
            tvAmortization = v.findViewById(R.id.tv_amortization);
            tvAmount = v.findViewById(R.id.tv_detail_amount);
            tvNote = v.findViewById(R.id.tv_detail_note);
            viewIndicator = v.findViewById(R.id.view_remark_indicator);
            tvAssetName = v.findViewById(R.id.tv_asset_name);
            ivAssetIcon = v.findViewById(R.id.iv_asset_icon);
            llAssetInfo = v.findViewById(R.id.ll_asset_info);
        }
    }
}
