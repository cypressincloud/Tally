package com.example.budgetapp.util;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.ui.CurrencyAdapter;

import java.util.Arrays;
import java.util.List;

public class CurrencyUtils {

    public static final String[] CURRENCY_DISPLAY = {
            "¥ 人民币", "$ 美元", "€ 欧元", "£ 英镑", "HK$ 港币", "NT$ 新台币",
            "JP¥ 日元", "₩ 韩元", "C$ 加元", "A$ 澳元", "S$ 新加坡元",
            "₹ 印度卢比", "₽ 俄卢布", "฿ 泰铢", "₫ 越南盾", "₱ 比索",
            "R$ 雷亚尔", "Fr 法郎", "Rp 印尼盾", "RM 林吉特"
    };

    public static final String[] CURRENCY_SYMBOLS = {
            "¥", "$", "€", "£", "HK$", "NT$",
            "JP¥", "₩", "C$", "A$", "S$",
            "₹", "₽", "฿", "₫", "₱",
            "R$", "Fr", "Rp", "RM"
    };

    /**
     * 显示自定义货币选择弹窗
     * @param context 上下文
     * @param targetBtn 需要更新文字的目标按钮
     * @param isOverlay 是否是在悬浮窗/服务中调用 (需要设置 Window Type)
     */
    public static void showCurrencyDialog(Context context, Button targetBtn, boolean isOverlay) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_currency_select, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // 如果是 Service/悬浮窗环境，需要设置 Window Type
        if (isOverlay) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
            }
        }
        
        // 设置背景透明，以便显示圆角
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView rv = dialogView.findViewById(R.id.rv_currency_list);
        rv.setLayoutManager(new GridLayoutManager(context, 4));

        List<String> displayList = Arrays.asList(CURRENCY_DISPLAY);
        List<String> symbolList = Arrays.asList(CURRENCY_SYMBOLS);
        String current = targetBtn.getText().toString();

        CurrencyAdapter adapter = new CurrencyAdapter(displayList, symbolList, current, (symbol, pos) -> {
            targetBtn.setText(symbol);
            dialog.dismiss();
        });
        rv.setAdapter(adapter);

        dialog.show();
    }
}