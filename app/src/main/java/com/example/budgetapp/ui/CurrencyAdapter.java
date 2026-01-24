package com.example.budgetapp.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import java.util.List;

public class CurrencyAdapter extends RecyclerView.Adapter<CurrencyAdapter.ViewHolder> {
    // ... (保留构造函数和成员变量不变)
    private final List<String> displayNames;
    private final List<String> symbols;
    private final OnItemClickListener listener;
    private int selectedIndex = -1;

    public interface OnItemClickListener {
        void onItemClick(String symbol, int position);
    }

    public CurrencyAdapter(List<String> displayNames, List<String> symbols, String currentSymbol, OnItemClickListener listener) {
        this.displayNames = displayNames;
        this.symbols = symbols;
        this.listener = listener;
        if (currentSymbol != null) {
            for (int i = 0; i < symbols.size(); i++) {
                if (symbols.get(i).equals(currentSymbol)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_currency, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String fullStr = displayNames.get(position);
        String symbol = symbols.get(position);
        
        String name = "";
        String[] parts = fullStr.split(" ", 2);
        if (parts.length > 1) {
            name = parts[1];
        }

        holder.tvSymbol.setText(symbol);
        holder.tvName.setText(name);

        Context context = holder.itemView.getContext();

        // 【修改】使用 ContextCompat.getColor 获取动态颜色
        if (position == selectedIndex) {
            // 选中状态颜色
            ((CardView)holder.itemView).setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.currency_item_bg_selected));
            holder.tvSymbol.setTextColor(
                    ContextCompat.getColor(context, R.color.currency_symbol_text_selected));
        } else {
            // 未选中状态颜色
            ((CardView)holder.itemView).setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.currency_item_bg_normal));
            holder.tvSymbol.setTextColor(
                    ContextCompat.getColor(context, R.color.currency_symbol_text_normal));
        }

        holder.itemView.setOnClickListener(v -> {
            int prev = selectedIndex;
            selectedIndex = holder.getAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedIndex);
            listener.onItemClick(symbol, selectedIndex);
        });
    }

    // ... (保留 getItemCount 和 ViewHolder 不变)
    @Override
    public int getItemCount() {
        return displayNames.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSymbol, tvName;
        ViewHolder(View itemView) {
            super(itemView);
            tvSymbol = itemView.findViewById(R.id.tv_symbol);
            tvName = itemView.findViewById(R.id.tv_name);
        }
    }
}