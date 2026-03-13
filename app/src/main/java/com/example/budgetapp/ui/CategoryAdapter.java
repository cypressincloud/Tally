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

    private boolean isDetailed; // 【新增】保存是否开启了详细分类

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

        // 【新增】初始化时读取开关状态
        this.isDetailed = com.example.budgetapp.util.CategoryManager.isDetailedCategoryEnabled(context);

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

        // 【修改】根据是否开启详细分类，动态调整 UI 的长宽、圆角、字号和内边距
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);

        if (isDetailed) {
            // 胶囊样式：完美照抄 Material Chip 的自然包裹样式
            holder.tvIcon.setText(category != null ? category : "");

            // 宽、高都不写死，完全由内容撑开 (WRAP_CONTENT)
            ViewGroup.LayoutParams lp = holder.tvIcon.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.tvIcon.setLayoutParams(lp);

            // 照抄标准 Chip 的内边距：左右 12dp，上下 6dp
            int paddingH = (int) (12 * context.getResources().getDisplayMetrics().density);
            int paddingV = (int) (6 * context.getResources().getDisplayMetrics().density);
            holder.tvIcon.setPadding(paddingH, paddingV, paddingH, paddingV);

            // 照抄标准 Chip 的文字样式：14sp，且取消加粗 (NORMAL)
            holder.tvIcon.setTextSize(14);
            holder.tvIcon.setTypeface(null, android.graphics.Typeface.NORMAL);

            // 圆角给一个极大的值 (如 50dp)，Android 会自动根据实际高度把它画成完美的半圆端头
            background.setCornerRadius(50 * context.getResources().getDisplayMetrics().density);
        } else {
            // 默认单字符圆角矩形样式
            if (category != null && !category.isEmpty()) {
                holder.tvIcon.setText(category.substring(0, 1));
            } else {
                holder.tvIcon.setText("");
            }

            // 恢复固定的 50x50 dp 大小
            ViewGroup.LayoutParams lp = holder.tvIcon.getLayoutParams();
            lp.width = (int) (50 * context.getResources().getDisplayMetrics().density);
            lp.height = (int) (50 * context.getResources().getDisplayMetrics().density);
            holder.tvIcon.setLayoutParams(lp);

            // 清除 Padding，让文字完全居中
            holder.tvIcon.setPadding(0, 0, 0, 0);

            // 恢复较大的字号和加粗效果
            holder.tvIcon.setTextSize(18);
            holder.tvIcon.setTypeface(null, android.graphics.Typeface.BOLD);

            // 默认 16dp 圆角
            background.setCornerRadius(16 * context.getResources().getDisplayMetrics().density);
        }

        boolean isSelected = category.equals(selectedCategory);

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

        // 绑定长按事件
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