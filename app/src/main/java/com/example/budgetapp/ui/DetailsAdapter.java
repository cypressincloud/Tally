package com.example.budgetapp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssetIconHelper;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 恢复为标准的 RecyclerView.Adapter
public class DetailsAdapter extends RecyclerView.Adapter<DetailsAdapter.ViewHolder> {

    private List<Transaction> transactions = new ArrayList<>();
    private Map<Integer, AssetAccount> assetMap = new HashMap<>();
    private OnTransactionClickListener listener;

    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA);
    private final SimpleDateFormat compareFormat = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    // 🌟 新增：用于接收常规的 List 数据并刷新
    public void setTransactions(List<Transaction> newList) {
        this.transactions = newList == null ? new ArrayList<>() : newList;
        notifyDataSetChanged();
    }

    public void setAssets(List<AssetAccount> assets) {
        assetMap.clear();
        if (assets != null) {
            for (AssetAccount asset : assets) assetMap.put(asset.id, asset);
        }
        notifyDataSetChanged();
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();

        // 动态创建一个 LinearLayout 包裹器，避免修改 XML
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 动态创建一个日期头部 TextView
        TextView tvHeader = new TextView(context);
        tvHeader.setTextSize(13);
        tvHeader.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        tvHeader.setPadding(dpToPx(context, 20), dpToPx(context, 16), dpToPx(context, 20), dpToPx(context, 8));
        tvHeader.setVisibility(View.GONE);

        // 加载原本的卡片布局
        View cardView = LayoutInflater.from(context).inflate(R.layout.item_transaction_detail, wrapper, false);

        wrapper.addView(tvHeader);
        wrapper.addView(cardView);

        return new ViewHolder(wrapper, tvHeader, cardView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= transactions.size()) return;

        Transaction current = transactions.get(position);

        Context context = holder.itemView.getContext();
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
        boolean showCurrency = prefs.getBoolean("enable_currency", false);

        // ================= 1. 动态头部逻辑 =================
        Transaction previous = position > 0 ? transactions.get(position - 1) : null;
        Transaction next = position < getItemCount() - 1 ? transactions.get(position + 1) : null;

        String currentDateStr = compareFormat.format(new Date(current.date));
        String previousDateStr = previous != null ? compareFormat.format(new Date(previous.date)) : "";
        String nextDateStr = next != null ? compareFormat.format(new Date(next.date)) : "";

        // 判断当前元素是否处于一天的顶部或底部
        boolean isTop = (previous == null || !currentDateStr.equals(previousDateStr));
        boolean isBottom = (next == null || !currentDateStr.equals(nextDateStr));

        if (isTop) {
            holder.tvHeader.setVisibility(View.VISIBLE);
            holder.tvHeader.setText(displayFormat.format(new Date(current.date)));
        } else {
            holder.tvHeader.setVisibility(View.GONE);
        }

        // ================= 2. 完美还原动态圆角与间距 =================
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        float radius = dpToPx(context, 16);

        if (isTop && isBottom) shape.setCornerRadii(new float[]{radius, radius, radius, radius, radius, radius, radius, radius});
        else if (isTop) shape.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        else if (isBottom) shape.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        else shape.setCornerRadii(new float[]{0, 0, 0, 0, 0, 0, 0, 0});

        int surfaceColor = ContextCompat.getColor(context, R.color.white);
        if (isCustomBg) surfaceColor = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230);
        shape.setColor(surfaceColor);
        holder.cardView.setBackground(shape);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.cardView.getLayoutParams();
        params.setMargins(dpToPx(context, 16), isTop ? dpToPx(context, 4) : 0, dpToPx(context, 16), isBottom ? dpToPx(context, 12) : 0);
        holder.cardView.setLayoutParams(params);

        // ================= 3. 数据绑定 =================
        String symbol = (current.currencySymbol != null && !current.currencySymbol.isEmpty()) ? current.currencySymbol : "¥";
        double shownAmount = current.displayAmount != null ? current.displayAmount : current.amount;
        String amountStr = String.format(Locale.CHINA, "%.2f", shownAmount);
        String displayAmount = showCurrency ? (symbol + " " + amountStr) : amountStr;

        if (current.type == 2) {
            holder.tvAmount.setTextColor(context.getColor(R.color.app_blue));
            holder.tvAmount.setText(displayAmount);
        } else if (current.type == 1) {
            holder.tvAmount.setTextColor(context.getColor(R.color.income_red));
            holder.tvAmount.setText("+" + displayAmount);
        } else {
            holder.tvAmount.setTextColor(context.getColor(R.color.expense_green));
            holder.tvAmount.setText("-" + displayAmount);
        }

        holder.tvDate.setText(current.category); // 注意原XML中ID是tv_detail_date，但存的是分类名

        if (!TextUtils.isEmpty(current.subCategory)) {
            holder.tvSubCategory.setText(current.subCategory);
            holder.tvSubCategory.setVisibility(View.VISIBLE);
        } else {
            holder.tvSubCategory.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(current.note)) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(current.note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }
        holder.tvAmortization.setVisibility(View.GONE);
        if (current.displayAmount != null && current.spreadStartDate > 0
                && current.spreadEndDate >= current.spreadStartDate) {
            LocalDate start = Instant.ofEpochMilli(current.spreadStartDate).atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate end = Instant.ofEpochMilli(current.spreadEndDate).atZone(ZoneId.systemDefault()).toLocalDate();
            long spreadDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            if (spreadDays > 1) {
                holder.tvAmortization.setVisibility(View.VISIBLE);
                holder.tvAmortization.setText(String.format(Locale.CHINA, "%d天均摊 %.2f",
                        spreadDays, current.amount));
            }
        }

        String assetName = (current.assetId != 0 && assetMap != null) ? assetMap.get(current.assetId) != null ? assetMap.get(current.assetId).name : null : null;
        AssetAccount assetAccount = (current.assetId != 0 && assetMap != null) ? assetMap.get(current.assetId) : null;
        boolean hasRemarkOrPhoto = !TextUtils.isEmpty(current.remark) || !TextUtils.isEmpty(current.photoPath);
        int statusColor = hasRemarkOrPhoto ? context.getColor(R.color.expense_green) : context.getColor(R.color.income_red);

        if (assetAccount != null && assetName != null) {
            holder.viewIndicator.setVisibility(View.GONE);
            holder.llAssetInfo.setVisibility(View.VISIBLE);
            holder.tvAssetName.setText(assetName);
            // 资产名称颜色表达交易类型，不应因自动记账没有备注而变成红色。
            holder.tvAssetName.setTextColor(current.type == 1
                    ? context.getColor(R.color.income_red)
                    : current.type == 2 ? context.getColor(R.color.app_blue)
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

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) listener.onTransactionClick(current.displaySource != null ? current.displaySource : current);
        });
    }

    // 🌟 新增：标准 Adapter 必须实现的方法
    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        View cardView;
        TextView tvDate, tvSubCategory, tvAmount, tvNote, tvAssetName, tvAmortization;
        View viewIndicator;
        android.widget.ImageView ivAssetIcon;
        android.widget.LinearLayout llAssetInfo;

        ViewHolder(View wrapper, TextView header, View card) {
            super(wrapper);
            tvHeader = header;
            cardView = card;
            tvDate = card.findViewById(R.id.tv_detail_date);
            tvSubCategory = card.findViewById(R.id.tv_detail_sub_category);
            tvAmortization = card.findViewById(R.id.tv_amortization);
            tvAmount = card.findViewById(R.id.tv_detail_amount);
            tvNote = card.findViewById(R.id.tv_detail_note);
            tvAssetName = card.findViewById(R.id.tv_asset_name);
            viewIndicator = card.findViewById(R.id.view_remark_indicator);
            ivAssetIcon = card.findViewById(R.id.iv_asset_icon);
            llAssetInfo = card.findViewById(R.id.ll_asset_info);
        }
    }
}
