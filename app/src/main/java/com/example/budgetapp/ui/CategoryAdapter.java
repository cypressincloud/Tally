package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    private Context context;
    private List<String> categories;
    private String selectedCategory;
    private OnCategoryClickListener listener;
    // 【新增】长按监听器
    private OnCategoryLongClickListener longListener;

    private int selectedColor;
    private int unselectedColor;
    private int selectedTextColor;
    private int unselectedTextColor;

    public interface OnCategoryClickListener {
        void onCategoryClick(String category);
    }
    
    // 【新增】接口定义
    public interface OnCategoryLongClickListener {
        boolean onCategoryLongClick(String category);
    }

    public CategoryAdapter(Context context, List<String> categories, String currentCategory, OnCategoryClickListener listener) {
        this.context = context;
        this.categories = categories;
        this.selectedCategory = currentCategory;
        this.listener = listener;
        
        this.selectedColor = ContextCompat.getColor(context, R.color.app_yellow);
        this.selectedTextColor = ContextCompat.getColor(context, R.color.cat_selected_text);
        this.unselectedColor = ContextCompat.getColor(context, R.color.cat_unselected_bg);
        this.unselectedTextColor = ContextCompat.getColor(context, R.color.cat_unselected_text);
    }

    // 【新增】设置长按监听器
    public void setOnCategoryLongClickListener(OnCategoryLongClickListener longListener) {
        this.longListener = longListener;
    }

    public void updateData(List<String> newCategories) {
        this.categories = newCategories;
        if (!categories.contains(selectedCategory) && !categories.isEmpty()) {
            selectedCategory = categories.get(0);
            if (listener != null) listener.onCategoryClick(selectedCategory);
        }
        notifyDataSetChanged();
    }
    
    public void setSelectedCategory(String category) {
        this.selectedCategory = category;
        notifyDataSetChanged();
    }

    public String getSelectedCategory() {
        return selectedCategory;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_button, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String category = categories.get(position);

        if (category != null && !category.isEmpty()) {
            holder.tvIcon.setText(category.substring(0, 1));
        } else {
            holder.tvIcon.setText("");
        }

        boolean isSelected = category.equals(selectedCategory);
        
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        float radius = 16 * context.getResources().getDisplayMetrics().density;
        background.setCornerRadius(radius);

        if (isSelected) {
            background.setColor(selectedColor);
            holder.tvIcon.setTextColor(selectedTextColor);
        } else {
            background.setColor(unselectedColor);
            holder.tvIcon.setTextColor(unselectedTextColor);
        }
        
        holder.tvIcon.setBackground(background);

        holder.itemView.setOnClickListener(v -> {
            selectedCategory = category;
            notifyDataSetChanged();
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });

        // 【新增】绑定长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (longListener != null) {
                return longListener.onCategoryLongClick(category);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tv_category_icon);
        }
    }
}