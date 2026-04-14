package com.example.budgetapp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// --- 数据包装类 ---
abstract class DetailsItem {
    static final int TYPE_HEADER = 0;
    static final int TYPE_TRANSACTION = 1;
    abstract int getType();
}

class HeaderItem extends DetailsItem {
    String dateStr;
    float income;
    float expense;
    float balance;

    HeaderItem(String dateStr, float income, float expense, float balance) {
        this.dateStr = dateStr;
        this.income = income;
        this.expense = expense;
        this.balance = balance;
    }
    @Override int getType() { return TYPE_HEADER; }
}

class TransactionItem extends DetailsItem {
    Transaction transaction;
    TransactionItem(Transaction t) { this.transaction = t; }
    @Override int getType() { return TYPE_TRANSACTION; }
}

// --- 适配器实现 ---
public class DetailsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<DetailsItem> items = new ArrayList<>();
    private Map<Integer, String> assetMap = new HashMap<>();
    private OnTransactionClickListener listener;

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    public void setAssets(List<AssetAccount> assets) {
        assetMap.clear();
        if (assets != null) {
            for (AssetAccount asset : assets) assetMap.put(asset.id, asset.name);
        }
        notifyDataSetChanged();
    }

    public void setData(List<DetailsItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == DetailsItem.TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_details_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_detail, parent, false);
            return new TransactionViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;

        // 🌟 1. 定位当前项在“当天独立卡片”中的位置 (顶部、底部、或中间)
        boolean isTop = (getItemViewType(position) == DetailsItem.TYPE_HEADER);
        boolean isBottom = false;

        if (position == items.size() - 1) {
            isBottom = true;
        } else {
            // 如果下一项是新的 Header，说明当前项是今天卡片的收尾
            isBottom = (items.get(position + 1).getType() == DetailsItem.TYPE_HEADER);
        }

        // 🌟 2. 动态生成对标 assets_field 的卡片背景
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);

        // 严格遵循 assets_field 的 16dp 圆角规范
        float radius = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics());

        // 根据卡片位置动态分配圆角 (拼接逻辑：左上, 右上, 右下, 左下)
        if (isTop && isBottom) {
            shape.setCornerRadii(new float[]{radius, radius, radius, radius, radius, radius, radius, radius});
        } else if (isTop) {
            shape.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        } else if (isBottom) {
            shape.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        } else {
            shape.setCornerRadii(new float[]{0, 0, 0, 0, 0, 0, 0, 0}); // 夹在中间的账单没有圆角，做到无缝衔接
        }

        // 严格遵循 assets_field 的 @color/white 背景色
        int surfaceColor = ContextCompat.getColor(context, R.color.white);
        if (isCustomBg) {
            // 如果是透明背景模式，使用 90% 透明度的白色
            surfaceColor = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230);
        }
        shape.setColor(surfaceColor);
        holder.itemView.setBackground(shape);

        // 🌟 3. 设置卡片间距 (与统计板块对齐)
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
        int marginH = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics());
        // 每天卡片顶部留 16dp 间距，底部留 8dp 间距
        int marginTop = isTop ? (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics()) : 0;
        int marginBottom = isBottom ? (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics()) : 0;

        params.setMargins(marginH, marginTop, marginH, marginBottom);
        holder.itemView.setLayoutParams(params);

        // ==========================================
        // 以下为视图数据绑定逻辑
        // ==========================================
        if (getItemViewType(position) == DetailsItem.TYPE_HEADER) {
            HeaderItem header = (HeaderItem) items.get(position);
            HeaderViewHolder hvh = (HeaderViewHolder) holder;
            hvh.tvDate.setText(header.dateStr);
            hvh.tvIncome.setText(header.income > 0 ? "收: " + String.format("%.2f", header.income) : "");
            hvh.tvExpense.setText(header.expense > 0 ? "支: " + String.format("%.2f", header.expense) : "");
            hvh.tvBalance.setText("结: " + String.format("%.2f", header.balance));

        } else {
            TransactionItem item = (TransactionItem) items.get(position);
            Transaction t = item.transaction;
            TransactionViewHolder tvh = (TransactionViewHolder) holder;

            String amountStr = String.format("%.2f", t.amount);
            if (t.type == 2) {
                tvh.tvAmount.setText(amountStr);
                tvh.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.app_yellow));
            } else if (t.type == 1) {
                tvh.tvAmount.setText("+" + amountStr);
                tvh.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_red));
            } else {
                tvh.tvAmount.setText("-" + amountStr);
                tvh.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_green));
            }

            tvh.tvCategory.setText(t.category);
            if (!TextUtils.isEmpty(t.subCategory)) {
                tvh.tvSubCategory.setText(t.subCategory);
                tvh.tvSubCategory.setVisibility(View.VISIBLE);
            } else {
                tvh.tvSubCategory.setVisibility(View.GONE);
            }
            if (!TextUtils.isEmpty(t.note)) {
                tvh.tvNote.setVisibility(View.VISIBLE);
                tvh.tvNote.setText(t.note);
            } else {
                tvh.tvNote.setVisibility(View.GONE);
            }

            String assetName = (t.assetId != 0) ? assetMap.get(t.assetId) : null;
            boolean hasRemarkOrPhoto = !TextUtils.isEmpty(t.remark) || !TextUtils.isEmpty(t.photoPath);
            int statusColor = hasRemarkOrPhoto ? ContextCompat.getColor(context, R.color.expense_green) : ContextCompat.getColor(context, R.color.income_red);

            if (assetName != null) {
                tvh.viewIndicator.setVisibility(View.GONE);
                tvh.tvAssetName.setVisibility(View.VISIBLE);
                tvh.tvAssetName.setText(assetName);
                tvh.tvAssetName.setTextColor(statusColor);
            } else {
                tvh.tvAssetName.setVisibility(View.GONE);
                tvh.viewIndicator.setVisibility(View.VISIBLE);
                tvh.viewIndicator.setBackgroundColor(statusColor);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTransactionClick(t);
            });
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvIncome, tvExpense, tvBalance;
        HeaderViewHolder(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tv_date);
            tvIncome = v.findViewById(R.id.tv_income);
            tvExpense = v.findViewById(R.id.tv_expense);
            tvBalance = v.findViewById(R.id.tv_balance);
        }
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvSubCategory, tvAmount, tvNote, tvAssetName;
        View viewIndicator;
        TransactionViewHolder(View v) {
            super(v);
            tvCategory = v.findViewById(R.id.tv_detail_date);
            tvSubCategory = v.findViewById(R.id.tv_detail_sub_category);
            tvAmount = v.findViewById(R.id.tv_detail_amount);
            tvNote = v.findViewById(R.id.tv_detail_note);
            tvAssetName = v.findViewById(R.id.tv_asset_name);
            viewIndicator = v.findViewById(R.id.view_remark_indicator);
        }
    }
}