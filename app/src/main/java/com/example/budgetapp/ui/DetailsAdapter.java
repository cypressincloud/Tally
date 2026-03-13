package com.example.budgetapp.ui;

import android.content.Context;
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
        if (getItemViewType(position) == DetailsItem.TYPE_HEADER) {
            HeaderItem header = (HeaderItem) items.get(position);
            HeaderViewHolder hvh = (HeaderViewHolder) holder;
            hvh.tvDate.setText(header.dateStr);
            hvh.tvIncome.setText(header.income > 0 ? "收: " + String.format("%.2f", header.income) : "");
            hvh.tvExpense.setText(header.expense > 0 ? "支: " + String.format("%.2f", header.expense) : "");
            // 🌟 设置结余文字
            hvh.tvBalance.setText("结: " + String.format("%.2f", header.balance));
        } else {
            TransactionItem item = (TransactionItem) items.get(position);
            Transaction t = item.transaction;
            TransactionViewHolder tvh = (TransactionViewHolder) holder;

            // 1. 金额与颜色 (对齐记账模块)
            String amountStr = String.format("%.2f", t.amount);
            if (t.type == 1) { // 收入
                tvh.tvAmount.setText("+" + amountStr);
                tvh.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_red));
            } else { // 支出
                tvh.tvAmount.setText("-" + amountStr);
                tvh.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_green));
            }

            // 2. 分类与二级分类
            tvh.tvCategory.setText(t.category);
            if (!TextUtils.isEmpty(t.subCategory)) {
                tvh.tvSubCategory.setText(t.subCategory);
                tvh.tvSubCategory.setVisibility(View.VISIBLE);
            } else {
                tvh.tvSubCategory.setVisibility(View.GONE);
            }

            // 3. 备注与记录标识
            if (!TextUtils.isEmpty(t.note)) {
                tvh.tvNote.setVisibility(View.VISIBLE);
                tvh.tvNote.setText(t.note);
            } else {
                tvh.tvNote.setVisibility(View.GONE);
            }

            // 4. 资产名称与备注指示器
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