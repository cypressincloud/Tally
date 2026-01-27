package com.example.budgetapp;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.CategoryManager;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private static final String JSON_FILE_NAME = "backup_data.json";

    // ... (exportToZip 方法保持不变) ...
    public static void exportToZip(Context context, Uri uri, List<Transaction> transactions, List<AssetAccount> assets) throws Exception {
        if (transactions == null) transactions = new ArrayList<>();
        if (assets == null) assets = new ArrayList<>();
        Gson gson = new Gson();
        BackupData data = new BackupData(transactions, assets);
        // 如果需要，也可以在这里填充 category 数据到 BackupData，以便 zip 也能备份分类
        data.expenseCategories = CategoryManager.getExpenseCategories(context);
        data.incomeCategories = CategoryManager.getIncomeCategories(context);

        String jsonString = gson.toJson(data);
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            ZipEntry entry = new ZipEntry(JSON_FILE_NAME);
            zos.putNextEntry(entry);
            zos.write(jsonString.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    // ... (exportToExcel 方法保持不变) ...
    public static void exportToExcel(Context context, Uri uri, List<Transaction> transactions, List<AssetAccount> assets) throws Exception {
        if (transactions == null) transactions = new ArrayList<>();
        if (assets == null) assets = new ArrayList<>();

        Map<Integer, String> assetMap = new HashMap<>();
        for (AssetAccount asset : assets) {
            assetMap.put(asset.id, asset.name);
        }

        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append('\ufeff'); // BOM

        // 1. 交易记录
        csvBuilder.append("交易ID,时间,类型,分类,金额,资产账户,记录标识,备注\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);

        for (Transaction t : transactions) {
            csvBuilder.append(t.id).append(",");
            csvBuilder.append(sdf.format(new Date(t.date))).append(",");
            String typeStr = (t.type == 0) ? "支出" : (t.type == 1 ? "收入" : "其他");
            csvBuilder.append(typeStr).append(",");
            csvBuilder.append(escapeCsv(t.category)).append(",");
            csvBuilder.append(t.amount).append(",");
            String assetName = assetMap.get(t.assetId);
            if (assetName == null) assetName = "未知账户";
            csvBuilder.append(escapeCsv(assetName)).append(",");
            csvBuilder.append(escapeCsv(t.note)).append(",");
            csvBuilder.append(escapeCsv(t.remark)).append("\n");
        }

        csvBuilder.append("\n\n");

        // 2. 资产列表
        csvBuilder.append("=== 资产账户列表 ===\n");
        csvBuilder.append("ID,账户名称,余额,类型,币种\n");
        for (AssetAccount asset : assets) {
            csvBuilder.append(asset.id).append(",");
            csvBuilder.append(escapeCsv(asset.name)).append(",");
            csvBuilder.append(asset.amount).append(",");
            String assetTypeStr;
            switch (asset.type) {
                case 1: assetTypeStr = "负债"; break;
                case 2: assetTypeStr = "借出"; break;
                default: assetTypeStr = "资产"; break;
            }
            csvBuilder.append(assetTypeStr).append(",");
            String symbol = (asset.currencySymbol == null) ? "¥" : asset.currencySymbol;
            csvBuilder.append(escapeCsv(symbol)).append("\n");
        }

        csvBuilder.append("\n\n");

        // 3. 分类预设
        csvBuilder.append("=== 分类预设配置 ===\n");
        csvBuilder.append("分类类型,分类名称\n");
        List<String> expenseCategories = CategoryManager.getExpenseCategories(context);
        for (String category : expenseCategories) {
            csvBuilder.append("支出,").append(escapeCsv(category)).append("\n");
        }
        List<String> incomeCategories = CategoryManager.getIncomeCategories(context);
        for (String category : incomeCategories) {
            csvBuilder.append("收入,").append(escapeCsv(category)).append("\n");
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            outputStream.write(csvBuilder.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 【新增】从 CSV/Excel 文件导入数据
     * 解析逻辑：先读取所有行，分离出 Assets 区块构建映射，然后解析 Transactions 区块。
     */
    public static BackupData importFromExcel(Context context, Uri uri) throws Exception {
        List<String> lines = new ArrayList<>();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        if (lines.isEmpty()) {
            throw new Exception("文件内容为空");
        }

        // 移除 BOM (如果存在)
        if (lines.get(0).startsWith("\ufeff")) {
            lines.set(0, lines.get(0).substring(1));
        }

        // 准备数据容器
        List<Transaction> transactions = new ArrayList<>();
        List<AssetAccount> assets = new ArrayList<>();
        List<String> expenseCats = new ArrayList<>();
        List<String> incomeCats = new ArrayList<>();
        
        // 临时存储解析出的原始行，用于后续处理
        List<List<String>> transactionRows = new ArrayList<>();
        List<List<String>> assetRows = new ArrayList<>();
        List<List<String>> categoryRows = new ArrayList<>();

        // 简单的状态机，用于区分当前解析的区块
        // 0: 交易记录 (默认), 1: 资产列表, 2: 分类预设
        int currentSection = 0; 

        for (String line : lines) {
            String trimmed = line.trim();
            if (TextUtils.isEmpty(trimmed)) continue;

            // 检测区块头
            if (trimmed.contains("=== 资产账户列表 ===")) {
                currentSection = 1;
                continue;
            } else if (trimmed.contains("=== 分类预设配置 ===")) {
                currentSection = 2;
                continue;
            } else if (trimmed.startsWith("交易ID,时间") || trimmed.startsWith("ID,账户名称") || trimmed.startsWith("分类类型,分类名称")) {
                // 跳过表头
                continue;
            }

            List<String> tokens = parseCsvLine(line);
            if (tokens.isEmpty()) continue;

            if (currentSection == 0) {
                transactionRows.add(tokens);
            } else if (currentSection == 1) {
                assetRows.add(tokens);
            } else if (currentSection == 2) {
                categoryRows.add(tokens);
            }
        }

        // -------------------------
        // 第一步：解析资产 (为了建立 ID 映射)
        // -------------------------
        // Map<资产名称, 资产ID>
        Map<String, Integer> assetNameToIdMap = new HashMap<>();
        
        for (List<String> row : assetRows) {
            // 格式: ID,账户名称,余额,类型,币种
            if (row.size() < 2) continue;
            try {
                int id = Integer.parseInt(row.get(0));
                String name = row.get(1);
                double amount = parseDoubleSafe(row.size() > 2 ? row.get(2) : "0");
                String typeStr = row.size() > 3 ? row.get(3) : "资产";
                String symbol = row.size() > 4 ? row.get(4) : "¥";

                int type = 0;
                if ("负债".equals(typeStr)) type = 1;
                else if ("借出".equals(typeStr)) type = 2;

                AssetAccount asset = new AssetAccount(name, amount, type);
                asset.id = id; // 尝试保留原有ID
                asset.currencySymbol = symbol;
                assets.add(asset);

                assetNameToIdMap.put(name, id);
            } catch (Exception e) {
                Log.e("BackupManager", "解析资产行失败: " + row, e);
            }
        }

        // -------------------------
        // 第二步：解析交易记录
        // -------------------------
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
        
        for (List<String> row : transactionRows) {
            // 格式: 交易ID,时间,类型,分类,金额,资产账户,记录标识,备注
            if (row.size() < 6) continue; // 至少要有到资产账户列
            try {
                Transaction t = new Transaction();
                // ID: 忽略或保留? 建议保留，但在插入数据库时可能需要处理冲突。这里先解析出来。
                try { t.id = Integer.parseInt(row.get(0)); } catch (Exception e) { t.id = 0; }
                
                // 时间
                Date date = sdf.parse(row.get(1));
                t.date = (date != null) ? date.getTime() : System.currentTimeMillis();

                // 类型
                String typeStr = row.get(2);
                t.type = "收入".equals(typeStr) ? 1 : ("支出".equals(typeStr) ? 0 : 2); // 2=其他

                // 分类
                t.category = row.get(3);

                // 金额
                t.amount = parseDoubleSafe(row.get(4));

                // 资产账户 -> 转换为 ID
                String assetName = row.get(5);
                if (assetNameToIdMap.containsKey(assetName)) {
                    t.assetId = assetNameToIdMap.get(assetName);
                } else {
                    t.assetId = 0; // 未找到对应资产，归为0或未知
                }

                // 记录标识 (Note)
                t.note = (row.size() > 6) ? row.get(6) : "";

                // 备注 (Remark)
                t.remark = (row.size() > 7) ? row.get(7) : "";

                transactions.add(t);

            } catch (Exception e) {
                Log.e("BackupManager", "解析交易行失败: " + row, e);
            }
        }

        // -------------------------
        // 第三步：解析分类
        // -------------------------
        for (List<String> row : categoryRows) {
            // 格式: 分类类型,分类名称
            if (row.size() < 2) continue;
            String type = row.get(0);
            String name = row.get(1);
            
            if ("支出".equals(type)) {
                if (!expenseCats.contains(name)) expenseCats.add(name);
            } else if ("收入".equals(type)) {
                if (!incomeCats.contains(name)) incomeCats.add(name);
            }
        }

        // 构建返回对象
        BackupData data = new BackupData(transactions, assets);
        data.expenseCategories = expenseCats;
        data.incomeCategories = incomeCats;
        return data;
    }

    // CSV 行解析工具 (处理双引号和逗号)
    private static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                // 检查是否是转义的双引号 ("")
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++; // 跳过下一个引号
                } else {
                    inQuotes = !inQuotes; // 切换引用状态
                }
            } else if (c == ',' && !inQuotes) {
                // 遇到逗号且不在引号内 -> 分割字段
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString()); // 添加最后一个字段
        return tokens;
    }

    private static double parseDoubleSafe(String val) {
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ... (importFromZip 方法保持不变，如果需要可以添加分类解析逻辑) ...
    public static BackupData importFromZip(Context context, Uri uri) throws Exception {
         // ... 原有逻辑 ...
         // 提示：如果希望 importFromZip 也能恢复分类，需要确保 exportToZip 中保存了分类字段，
         // 并且这里 gson.fromJson 反序列化时会自动读取到 BackupData 的新字段。
         try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(JSON_FILE_NAME)) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    Gson gson = new Gson();
                    return gson.fromJson(sb.toString(), BackupData.class);
                }
            }
        }
        throw new Exception("无法识别的备份文件：未找到数据文件 " + JSON_FILE_NAME);
    }
    
    // ... escapeCsv 方法 ...
    private static String escapeCsv(String value) {
        if (value == null) return "";
        String result = value.replace("\"", "\"\""); 
        if (result.contains(",") || result.contains("\n") || result.contains("\"")) {
            return "\"" + result + "\"";
        }
        return result;
    }
}