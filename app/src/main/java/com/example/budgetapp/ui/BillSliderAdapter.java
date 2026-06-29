package com.example.budgetapp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssetIconHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BillSliderAdapter extends RecyclerView.Adapter<BillSliderAdapter.BillViewHolder> {

    private List<Transaction> transactions = new ArrayList<>();
    private List<AssetAccount> assets = new ArrayList<>();
    private OnBillClickListener listener;
    private Context context;

    public interface OnBillClickListener {
        void onBillClick(Transaction transaction);
    }

    public BillSliderAdapter(OnBillClickListener listener) {
        this.listener = listener;
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setAssets(List<AssetAccount> assets) {
        this.assets = assets != null ? assets : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_bill_card, parent, false);
        return new BillViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BillViewHolder holder, int position) {
        Transaction transaction = transactions.get(position);

        // 分类名称
        holder.tvCategory.setText(transaction.category);

        // 二级分类
        String subCategory = transaction.subCategory;
        if (subCategory != null && !subCategory.isEmpty()) {
            holder.tvSubCategory.setVisibility(View.VISIBLE);
            holder.tvSubCategory.setText(subCategory);
        } else {
            holder.tvSubCategory.setVisibility(View.GONE);
        }

        // 金额处理
        double amount = transaction.amount;
        String amountText;
        int amountColor;
        if (transaction.type == 1) {
            // 收入
            amountText = String.format(Locale.getDefault(), "+%.2f", amount);
            amountColor = ContextCompat.getColor(context, R.color.income_red);
        } else if (transaction.type == 0) {
            // 支出
            amountText = String.format(Locale.getDefault(), "-%.2f", amount);
            amountColor = ContextCompat.getColor(context, R.color.expense_green);
        } else {
            // 其他类型
            amountText = String.format(Locale.getDefault(), "%.2f", amount);
            amountColor = ContextCompat.getColor(context, R.color.text_primary);
        }
        holder.tvAmount.setText(amountText);
        holder.tvAmount.setTextColor(amountColor);

        // 备注
        String note = transaction.note;
        if (note != null && !note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // 记录标识 (remark)
        String remark = transaction.remark;
        if (remark != null && !remark.isEmpty()) {
            holder.tvRemark.setVisibility(View.VISIBLE);
            holder.tvRemark.setText(remark);
            holder.viewRemarkIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.tvRemark.setVisibility(View.GONE);
            holder.viewRemarkIndicator.setVisibility(View.GONE);
        }

        // 资产信息
        if (transaction.assetId > 0) {
            AssetAccount asset = findAssetById(transaction.assetId);
            if (asset != null) {
                holder.llAssetInfo.setVisibility(View.VISIBLE);
                holder.tvAssetName.setText(asset.name);
                // 使用 AssetIconHelper 加载 SVG 图标
                AssetIconHelper.bindSvgIcon(holder.ivAssetIcon, asset.svgIcon);
            } else {
                holder.llAssetInfo.setVisibility(View.GONE);
            }
        } else {
            holder.llAssetInfo.setVisibility(View.GONE);
        }

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBillClick(transaction);
            }
        });

        // 透明度设置
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
        int surfaceColor = ContextCompat.getColor(context, R.color.white);
        holder.itemView.setBackgroundColor(isCustomBg ?
                androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 230) : surfaceColor);
    }

    private AssetAccount findAssetById(int assetId) {
        for (AssetAccount asset : assets) {
            if (asset.id == assetId) {
                return asset;
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class BillViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvSubCategory, tvAmount, tvNote, tvRemark;
        TextView tvAssetName;
        ImageView ivAssetIcon;
        LinearLayout llAssetInfo;
        View viewRemarkIndicator;

        BillViewHolder(View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_bill_category);
            tvSubCategory = itemView.findViewById(R.id.tv_bill_sub_category);
            tvAmount = itemView.findViewById(R.id.tv_bill_amount);
            tvNote = itemView.findViewById(R.id.tv_bill_note);
            tvRemark = itemView.findViewById(R.id.tv_bill_remark);
            tvAssetName = itemView.findViewById(R.id.tv_asset_name);
            ivAssetIcon = itemView.findViewById(R.id.iv_asset_icon);
            llAssetInfo = itemView.findViewById(R.id.ll_asset_info);
            viewRemarkIndicator = itemView.findViewById(R.id.view_remark_indicator);
        }
    }

    /**
     * 卡片切换动画效果
     */
    public static class BillCardTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.85f;
        private static final float MIN_ALPHA = 0.6f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1 || position > 1) {
                // 页面完全不可见
                page.setAlpha(0f);
            } else if (position <= 0) {
                // 当前页面
                page.setAlpha(1f);
                page.setScaleX(1f);
                page.setScaleY(1f);
            } else {
                // 相邻页面（左侧滑入或右侧滑出）
                float scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - position);
                float alphaFactor = MIN_ALPHA + (1 - MIN_ALPHA) * (1 - position);

                page.setAlpha(alphaFactor);
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            }
        }
    }
}