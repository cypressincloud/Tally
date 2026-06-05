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

    // 货币符号 → ISO 代码映射（与 CurrencySettingsActivity 的 currencyCodes / currencySymbols 保持一致）
    private static final java.util.Map<String, String> SYMBOL_TO_CODE = buildSymbolToCodeMap();

    private static java.util.Map<String, String> buildSymbolToCodeMap() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("¥", "CNY");
        map.put("$", "USD");
        map.put("€", "EUR");
        map.put("£", "GBP");
        map.put("HK$", "HKD");
        map.put("NT$", "TWD");
        map.put("JP¥", "JPY");
        map.put("₩", "KRW");
        map.put("C$", "CAD");
        map.put("A$", "AUD");
        map.put("S$", "SGD");
        map.put("NZ$", "NZD");
        map.put("₹", "INR");
        map.put("₽", "RUB");
        map.put("฿", "THB");
        map.put("₫", "VND");
        map.put("₱", "PHP");
        map.put("R$", "BRL");
        map.put("Rp", "IDR");
        map.put("RM", "MYR");
        map.put("CHF", "CHF");
        map.put("₺", "TRY");
        map.put("₪", "ILS");
        map.put("kr", "SEK");  // kr 在多国使用，这里用 SEK 作为兜底
        map.put("zł", "PLN");
        map.put("Kč", "CZK");
        map.put("Ft", "HUF");
        map.put("lei", "RON");
        map.put("лв", "BGN");
        map.put("RSD", "RSD");
        map.put("BYN", "BYN");
        map.put("₴", "UAH");
        map.put("L", "MDL");
        map.put("Lek", "ALL");
        map.put("KM", "BAM");
        map.put("den", "MKD");
        map.put("₾", "GEL");
        map.put("֏", "AMD");
        map.put("₼", "AZN");
        map.put("KD", "KWD");
        map.put("SR", "SAR");
        map.put("DH", "AED");
        map.put("R", "ZAR");
        map.put("₦", "NGN");
        map.put("E£", "EGP");
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * 将货币符号转换为 ISO 货币代码
     */
    public static String symbolToCode(String symbol) {
        if (symbol == null || symbol.isEmpty()) return "CNY";
        String code = SYMBOL_TO_CODE.get(symbol);
        return code != null ? code : "CNY";
    }


    public static final String[] CURRENCY_DISPLAY = {
            "¥ 人民币", "$ 美元", "€ 欧元", "£ 英镑", "HK$ 港币", "NT$ 新台币",
            "JP¥ 日元", "₩ 韩元", "C$ 加元", "A$ 澳元", "S$ 新加坡元", "NZ$ 新西兰元",
            "₹ 印度卢比", "₽ 俄卢布", "฿ 泰铢", "₫ 越南盾", "₱ 比索", "R$ 雷亚尔",
            "Rp 印尼盾", "RM 林吉特", "CHF 瑞郎", "₺ 土耳其里拉", "₪ 谢克尔",
            "SEK 瑞典克朗", "NOK 挪威克朗", "DKK 丹麦克朗", "PLN 波兰兹罗提", "CZK 捷克克朗",
            "HUF 匈牙利福林", "RON 罗马尼亚列伊", "BGN 保加利亚列夫", "RSD 塞尔维亚第纳尔", "ISK 冰岛克朗",
            "BYN 白俄罗斯卢布", "₴ 乌克兰格里夫纳", "MDL 摩尔多瓦列伊", "ALL 阿尔巴尼亚列克", "BAM 波黑马克",
            "MKD 马其顿第纳尔", "GEL 格鲁吉亚拉里", "AMD 亚美尼亚德拉姆", "AZN 阿塞拜疆马纳特",
            "KD 科威特第纳尔", "SR 沙特里亚尔", "DH 阿联酋迪拉姆", "R 兰特", "₦ 奈拉", "E£ 埃及镑"
    };

    public static final String[] CURRENCY_SYMBOLS = {
            "¥", "$", "€", "£", "HK$", "NT$",
            "JP¥", "₩", "C$", "A$", "S$", "NZ$",
            "₹", "₽", "฿", "₫", "₱", "R$",
            "Rp", "RM", "CHF", "₺", "₪",
            "kr", "kr", "kr", "zł", "Kč", "Ft", "lei", "лв", "RSD", "kr",
            "BYN", "₴", "L", "Lek", "KM", "den", "₾", "֏", "₼",
            "KD", "SR", "DH", "R", "₦", "E£"
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