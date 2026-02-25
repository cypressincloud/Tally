package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager; // 引入 Grid 布局
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;

import java.util.ArrayList;
import java.util.List;

public class CurrencySettingsActivity extends AppCompatActivity {

    private SwitchCompat switchCurrency;
    private CardView cardDefaultCurrency;
    private TextView tvCurrentCurrency;
    private SharedPreferences prefs;

    // --- 数据同步：扩充了常用货币列表，确保和记账页面尽可能一致 ---
    private final String[] currencyCodes = {
            "CNY", "USD", "EUR", "GBP", "HKD", "TWD",
            "JPY", "KRW", "CAD", "AUD", "SGD", "NZD",
            "INR", "RUB", "THB", "VND", "PHP", "BRL",
            "IDR", "MYR", "CHF", "TRY", "ILS",
            // --- 欧洲国家补充 ---
            "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON", "BGN", "RSD", "ISK",
            "BYN", "UAH", "MDL", "ALL", "BAM", "MKD", "GEL", "AMD", "AZN",
            // --- 其他地区 ---
            "KWD", "SAR", "AED", "ZAR", "NGN", "EGP"
    };

    private final String[] currencySymbols = {
            "¥", "$", "€", "£", "HK$", "NT$",
            "JP¥", "₩", "C$", "A$", "S$", "NZ$",
            "₹", "₽", "฿", "₫", "₱", "R$",
            "Rp", "RM", "CHF", "₺", "₪",
            // --- 对应符号 ---
            "kr", "kr", "kr", "zł", "Kč", "Ft", "lei", "лв", "RSD", "kr",
            "BYN", "₴", "L", "Lek", "KM", "den", "₾", "֏", "₼",
            "KD", "SR", "DH", "R", "₦", "E£"
    };

    private final String[] currencyNames = {
            "人民币", "美元", "欧元", "英镑", "港币", "新台币",
            "日元", "韩元", "加元", "澳元", "新加坡元", "新西兰元",
            "印度卢比", "卢布", "泰铢", "越南盾", "比索", "雷亚尔",
            "印尼盾", "林吉特", "瑞郎", "土耳其里拉", "谢克尔",
            // --- 对应名称 ---
            "瑞典克朗", "挪威克朗", "丹麦克朗", "波兰兹罗提", "捷克克朗", "匈牙利福林", "罗马尼亚列伊", "保加利亚列夫", "塞尔维亚第纳尔", "冰岛克朗",
            "白俄罗斯卢布", "乌克兰格里夫纳", "摩尔多瓦列伊", "阿尔巴尼亚列克", "波黑马克", "马其顿第纳尔", "格鲁吉亚拉里", "亚美尼亚德拉姆", "阿塞拜疆马纳特",
            "科威特第纳尔", "沙特里亚尔", "阿联酋迪拉姆", "南非兰特", "奈拉", "埃及镑"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_currency_settings);

        View rootView = findViewById(R.id.currency_settings_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    originalPaddingLeft + insets.left,
                    originalPaddingTop + insets.top,
                    originalPaddingRight + insets.right,
                    originalPaddingBottom + insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        switchCurrency = findViewById(R.id.switch_currency);
        cardDefaultCurrency = findViewById(R.id.card_default_currency);
        tvCurrentCurrency = findViewById(R.id.tv_current_currency);
        View btnSetDefault = findViewById(R.id.btn_set_default_currency);

        boolean isEnabled = prefs.getBoolean("enable_currency", false);
        switchCurrency.setChecked(isEnabled);
        updateDefaultSettingsVisibility(isEnabled);

        switchCurrency.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("enable_currency", isChecked).apply();
            updateDefaultSettingsVisibility(isChecked);
        });

        updateCurrentCurrencyText();

        btnSetDefault.setOnClickListener(v -> showCurrencySelectDialog());
    }

    private void updateDefaultSettingsVisibility(boolean isEnabled) {
        if (isEnabled) {
            cardDefaultCurrency.setVisibility(View.VISIBLE);
            cardDefaultCurrency.setAlpha(1.0f);
        } else {
            cardDefaultCurrency.setVisibility(View.GONE);
        }
    }

    private void updateCurrentCurrencyText() {
        String code = prefs.getString("default_currency_code", "CNY");
        String symbol = prefs.getString("default_currency_symbol", "¥");
        tvCurrentCurrency.setText(String.format("%s (%s)", code, symbol));
    }

    private void showCurrencySelectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_currency_select, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView recyclerView = view.findViewById(R.id.rv_currency_list);

        if (recyclerView != null) {
            // --- 核心修改：使用 GridLayoutManager，一行4个 ---
            recyclerView.setLayoutManager(new GridLayoutManager(this, 4));

            List<CurrencyItem> items = new ArrayList<>();
            String currentCode = prefs.getString("default_currency_code", "CNY");

            for (int i = 0; i < currencyCodes.length; i++) {
                items.add(new CurrencyItem(currencyCodes[i], currencySymbols[i], currencyNames[i],
                        currencyCodes[i].equals(currentCode)));
            }

            SimpleCurrencyAdapter adapter = new SimpleCurrencyAdapter(items, item -> {
                prefs.edit()
                        .putString("default_currency_code", item.code)
                        .putString("default_currency_symbol", item.symbol)
                        .apply();

                updateCurrentCurrencyText();
                Toast.makeText(this, "默认货币已设置为: " + item.name, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            recyclerView.setAdapter(adapter);
        }

        dialog.show();

    }

    private static class CurrencyItem {
        String code, symbol, name;
        boolean isSelected;

        public CurrencyItem(String code, String symbol, String name, boolean isSelected) {
            this.code = code;
            this.symbol = symbol;
            this.name = name;
            this.isSelected = isSelected;
        }
    }

    private class SimpleCurrencyAdapter extends RecyclerView.Adapter<SimpleCurrencyAdapter.ViewHolder> {
        private final List<CurrencyItem> items;
        private final OnItemClickListener listener;

        public SimpleCurrencyAdapter(List<CurrencyItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 加载 item_currency.xml (现在它是网格卡片样式)
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_currency, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CurrencyItem item = items.get(position);
            Context context = holder.itemView.getContext();

            CardView cardRoot = (CardView) holder.itemView;
            TextView tvSymbol = holder.itemView.findViewById(R.id.tv_symbol);
            TextView tvName = holder.itemView.findViewById(R.id.tv_name);

            if (tvSymbol != null) tvSymbol.setText(item.symbol);
            if (tvName != null) tvName.setText(item.name); // 显示名称 (如 "人民币")

            // --- 选中状态样式：改变背景色和文字颜色 ---
            if (item.isSelected) {
                // 选中状态：淡蓝色背景，蓝色文字
                // 使用 app_yellow (实际是蓝色 #327ffc) 配合透明度，或者使用 currency_item_bg_selected
                cardRoot.setCardBackgroundColor(ContextCompat.getColor(context, R.color.currency_item_bg_selected));
                if (tvSymbol != null) tvSymbol.setTextColor(ContextCompat.getColor(context, R.color.currency_symbol_text_selected));
                if (tvName != null) tvName.setTextColor(ContextCompat.getColor(context, R.color.currency_symbol_text_selected));
            } else {
                // 未选中状态：浅灰背景，普通文字
                cardRoot.setCardBackgroundColor(ContextCompat.getColor(context, R.color.currency_item_bg_normal));
                if (tvSymbol != null) tvSymbol.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                if (tvName != null) tvName.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }

            holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    interface OnItemClickListener {
        void onItemClick(CurrencyItem item);
    }
}