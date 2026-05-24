package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;

import java.text.DecimalFormat;

/**
 * 通用资产 Spinner 适配器，支持显示 SVG 图标、自定义颜色和余额。
 * 用于手动记账、自动记账、磁贴记账、AI记账等所有资产选择场景。
 */
public class AssetSpinnerAdapter extends ArrayAdapter<AssetAccount> {

    private static final int MAX_DROPDOWN_HEIGHT_DP = 260;

    private final LayoutInflater inflater;
    private final DecimalFormat decimalFormat;
    private final boolean isCurrencyEnabled;

    public AssetSpinnerAdapter(@NonNull Context context) {
        super(context, R.layout.item_spinner_asset);
        this.inflater = LayoutInflater.from(context);
        this.decimalFormat = new DecimalFormat("#,##0.00");
        
        // 读取多币种设置
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        this.isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
    }

    /**
     * 限制 Spinner 下拉列表的最大高度。
     * 通过拦截 Spinner 点击事件，使用自定义 ListPopupWindow 替代默认弹窗。
     * 在 setAdapter 之后调用此方法。
     */
    public static void limitDropDownHeight(Spinner spinner) {
        int maxHeightPx = (int) (MAX_DROPDOWN_HEIGHT_DP * spinner.getContext().getResources().getDisplayMetrics().density);

        spinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                showCustomDropdown(spinner, maxHeightPx);
                return true;
            }
            return true;
        });
    }

    private static void showCustomDropdown(Spinner spinner, int maxHeightPx) {
        android.widget.ListPopupWindow popupWindow = new android.widget.ListPopupWindow(spinner.getContext());
        popupWindow.setAnchorView(spinner);
        popupWindow.setAdapter((android.widget.ListAdapter) spinner.getAdapter());
        popupWindow.setWidth(spinner.getWidth());

        // 项目少于等于6个时自适应高度，超过6个时限制最大高度
        int itemCount = spinner.getAdapter().getCount();
        if (itemCount <= 6) {
            popupWindow.setHeight(android.widget.ListPopupWindow.WRAP_CONTENT);
        } else {
            popupWindow.setHeight(maxHeightPx);
        }

        popupWindow.setModal(true);
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(spinner.getContext(), R.drawable.bg_input_field));
        popupWindow.setOnItemClickListener((parent, view, position, id) -> {
            spinner.setSelection(position);
            popupWindow.dismiss();
        });
        popupWindow.show();

        // 隐藏滚动条
        if (popupWindow.getListView() != null) {
            popupWindow.getListView().setVerticalScrollBarEnabled(false);
        }
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    private View createView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        ViewHolder holder;

        if (view == null) {
            view = inflater.inflate(R.layout.item_spinner_asset, parent, false);
            holder = new ViewHolder();
            holder.ivIcon = view.findViewById(R.id.iv_asset_icon);
            holder.tvName = view.findViewById(R.id.tv_asset_name);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        AssetAccount asset = getItem(position);
        if (asset != null) {
            // 构建显示文本：资产名称 + 余额
            String displayText = buildDisplayText(asset);
            holder.tvName.setText(displayText);

            // 绑定 SVG 图标
            if (holder.ivIcon != null) {
                AssetIconHelper.bindSvgIcon(holder.ivIcon, asset.svgIcon);
            }

            // 应用颜色
            applyTextColor(holder.tvName, asset);
        }

        return view;
    }

    /**
     * 构建显示文本：资产名称 (余额)
     * 格式：中国银行 (5043.54) 或 中国银行 ($5043.54)
     */
    private String buildDisplayText(AssetAccount asset) {
        StringBuilder sb = new StringBuilder();
        sb.append(asset.name);
        sb.append(" (");
        
        // 如果开启了多币种，显示货币符号
        if (isCurrencyEnabled && asset.currencySymbol != null && !asset.currencySymbol.isEmpty()) {
            sb.append(asset.currencySymbol);
        }
        
        // 显示余额
        sb.append(decimalFormat.format(asset.amount));
        sb.append(")");
        
        return sb.toString();
    }

    private void applyTextColor(TextView tv, AssetAccount asset) {
        Context context = tv.getContext();
        if (asset.colorType == 1) {
            tv.setTextColor(ContextCompat.getColor(context, R.color.income_red));
        } else if (asset.colorType == 2) {
            tv.setTextColor(ContextCompat.getColor(context, R.color.expense_green));
        } else if (asset.colorType == 3 && asset.customColorHex != null && !asset.customColorHex.isEmpty()) {
            try {
                tv.setTextColor(Color.parseColor(asset.customColorHex));
            } catch (Exception e) {
                tv.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            }
        } else {
            try {
                tv.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            } catch (Exception e) {
                tv.setTextColor(Color.BLACK);
            }
        }
    }

    private static class ViewHolder {
        ImageView ivIcon;
        TextView tvName;
    }
}
