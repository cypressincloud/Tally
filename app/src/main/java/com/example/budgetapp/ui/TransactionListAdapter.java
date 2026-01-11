package com.example.budgetapp.ui;

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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionListAdapter extends RecyclerView.Adapter<TransactionListAdapter.ViewHolder> {
    private List<Transaction> list = new ArrayList<>();
    private Map<Integer, String> assetMap = new HashMap<>(); 
    private OnItemClickListener listener;

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

    // 设置资产数据
    public void setAssets(List<AssetAccount> assets) {
        assetMap.clear();
        if (assets != null) {
            for (AssetAccount asset : assets) {
                assetMap.put(asset.id, asset.name);
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

        // 1. 金额
        if (t.type == 1) { // 收入
            holder.tvAmount.setTextColor(holder.itemView.getContext().getColor(R.color.income_red));
            holder.tvAmount.setText("+" + String.format("%.2f", t.amount));
        } else { // 支出
            holder.tvAmount.setTextColor(holder.itemView.getContext().getColor(R.color.expense_green));
            holder.tvAmount.setText("-" + String.format("%.2f", t.amount));
        }

        // 2. 分类
        holder.tvDate.setText(t.category);

        // 3. Note
        if (t.note != null && !t.note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(t.note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // 4. 【核心逻辑修改】右下角状态
        boolean hasRemark = !TextUtils.isEmpty(t.remark);
        int statusColor;
        if (hasRemark) {
            statusColor = holder.itemView.getContext().getColor(R.color.expense_green);
        } else {
            statusColor = holder.itemView.getContext().getColor(R.color.income_red);
        }

        // 尝试获取资产名称
        String assetName = null;
        if (t.assetId != 0) {
            assetName = assetMap.get(t.assetId);
        }

        // 判断：只有当 assetId 不为0 且 确实找到了名称 时，才显示文字
        if (assetName != null) {
            // --- 显示资产名称 ---
            holder.viewIndicator.setVisibility(View.GONE);
            holder.tvAssetName.setVisibility(View.VISIBLE);
            holder.tvAssetName.setText(assetName);
            holder.tvAssetName.setTextColor(statusColor);
        } else {
            // --- 显示小色块 (兜底方案) ---
            // (包括: 无关联资产、资产被删除、或资产数据尚未加载完成)
            holder.tvAssetName.setVisibility(View.GONE);
            holder.viewIndicator.setVisibility(View.VISIBLE);
            holder.viewIndicator.setBackgroundColor(statusColor);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(t);
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvAmount, tvNote;
        View viewIndicator;
        TextView tvAssetName;

        ViewHolder(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tv_detail_date);
            tvAmount = v.findViewById(R.id.tv_detail_amount);
            tvNote = v.findViewById(R.id.tv_detail_note);
            viewIndicator = v.findViewById(R.id.view_remark_indicator);
            tvAssetName = v.findViewById(R.id.tv_asset_name);
        }
    }
}