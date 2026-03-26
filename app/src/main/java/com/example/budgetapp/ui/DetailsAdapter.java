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

// 在 DetailsAdapter.java 中修改 HeaderItem 类
class HeaderItem extends DetailsItem {
    String dateStr;
    float income;
    float expense;
    float balance; // 🌟 新增

    HeaderItem(String dateStr, float income, float expense, float balance) {
        this.dateStr = dateStr;
        this.income = income;
        this.expense = expense;
        this.balance = balance; // 🌟 新增
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

        // 🌟 新增：获取当前是否为自定义背景模式
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;

        int surfaceColor = ContextCompat.getColor(context, R.color.white);

        if (getItemViewType(position) == DetailsItem.TYPE_HEADER) {
            HeaderItem header = (HeaderItem) items.get(position);
            HeaderViewHolder hvh = (HeaderViewHolder) holder;
            hvh.tvDate.setText(header.dateStr);
            hvh.tvIncome.setText(header.income > 0 ? "收: " + String.format("%.2f", header.income) : "");
            hvh.tvExpense.setText(header.expense > 0 ? "支: " + String.format("%.2f", header.expense) : "");
            hvh.tvBalance.setText("结: " + String.format("%.2f", header.balance));

            // 🌟 1. 设置日期头透明度 (90% 透明度, Alpha: 230)
            if (isCustomBg) {
                int translucentSurface = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230);
                hvh.itemView.setBackgroundColor(translucentSurface);
            } else {
                hvh.itemView.setBackgroundResource(R.color.bar_background);
            }

        } else {
            TransactionItem item = (TransactionItem) items.get(position);
            Transaction t = item.transaction;
            TransactionViewHolder tvh = (TransactionViewHolder) holder;

            // ... (这里保留你原来所有的金额、颜色、分类、备注等 UI 赋值逻辑) ...
            String amountStr = String.format("%.2f", t.amount);
            if (t.type == 2) {
                // 🌟 资产转移：主题色 (黄色)，不带正负号
                tvh.tvAmount.setText(amountStr);
                tvh.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.app_yellow));
            } else if (t.type == 1) {
                // 收入：红色，带加号
                tvh.tvAmount.setText("+" + amountStr);
                tvh.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_red));
            } else {
                // 支出：绿色，带减号
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
            // ... (上面是保留的原代码) ...

            // 🌟 2. 为明细账单设置 80% (Alpha: 204) 透明度淡灰色卡片背景
            if (isCustomBg) {
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                int lightGray = androidx.core.content.ContextCompat.getColor(context, R.color.white);
                int translucentGray = androidx.core.graphics.ColorUtils.setAlphaComponent(lightGray, 230);
                shape.setColor(translucentGray);

                // 增加一点圆角 (12dp) 让它更高级
                float radius = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 0, context.getResources().getDisplayMetrics());
                shape.setCornerRadius(radius);

                // 增加缩进间距 (上下 4dp, 左右 12dp)，让列表项独立成卡片，底层图片从缝隙透出
                int insetV = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 0, context.getResources().getDisplayMetrics());
                int insetH = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 0, context.getResources().getDisplayMetrics());
                android.graphics.drawable.InsetDrawable insetDrawable = new android.graphics.drawable.InsetDrawable(shape, insetH, insetV, insetH, insetV);

                tvh.itemView.setBackground(insetDrawable);
            } else {
                // 恢复系统默认：无背景
                tvh.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            // 🌟 解除拦截，将转移记录的点击事件也透传给 Fragment 处理
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTransactionClick(t);
            });
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // 在 DetailsAdapter.java 的 HeaderViewHolder 中添加变量
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvIncome, tvExpense, tvBalance; // 🌟 新增 tvBalance
        HeaderViewHolder(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tv_date);
            tvIncome = v.findViewById(R.id.tv_income);
            tvExpense = v.findViewById(R.id.tv_expense);
            tvBalance = v.findViewById(R.id.tv_balance); // 🌟 新增
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