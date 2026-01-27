package com.example.budgetapp;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.AutoAssetManager;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private static final String JSON_FILE_NAME = "backup_data.json";

    // ============================================================================================
    // ZIP 导出/导入 (JSON格式)
    // ============================================================================================
    // ... (exportToZip 和 importFromZip 逻辑不需要修改，因为字段顺序由 Class 定义决定) ...
    public static void exportToZip(Context context, Uri uri, List<Transaction> transactions, List<AssetAccount> assets) throws Exception {
        if (transactions == null) transactions = new ArrayList<>();
        if (assets == null) assets = new ArrayList<>();
        Gson gson = new Gson();
        
        BackupData data = new BackupData(transactions, assets);
        
        List<String> expenseCats = CategoryManager.getExpenseCategories(context);
        List<String> incomeCats = CategoryManager.getIncomeCategories(context);
        data.expenseCategories = expenseCats;
        data.incomeCategories = incomeCats;

        Map<String, List<String>> subMap = new HashMap<>();
        List<String> allCats = new ArrayList<>(expenseCats);
        allCats.addAll(incomeCats);
        for (String parent : allCats) {
            List<String> subs = CategoryManager.getSubCategories(context, parent);
            if (subs != null && !subs.isEmpty()) {
                subMap.put(parent, subs);
            }
        }
        data.subCategoryMap = subMap;

        List<AutoAssetManager.AssetRule> rules = AutoAssetManager.getRules(context);
        List<String> ruleStrings = new ArrayList<>();
        for (AutoAssetManager.AssetRule rule : rules) {
            ruleStrings.add(rule.toString());
        }
        data.autoAssetRules = ruleStrings;

        AssistantConfig config = new AssistantConfig(context);
        BackupData.AssistantConfigData configData = new BackupData.AssistantConfigData();
        configData.enableAutoTrack = config.isEnabled();
        configData.enableRefund = config.isRefundEnabled();
        configData.enableAssets = config.isAssetsEnabled();
        configData.defaultAssetId = config.getDefaultAssetId();
        configData.expenseKeywords = config.getExpenseKeywords();
        configData.incomeKeywords = config.getIncomeKeywords();
        configData.weekdayRate = config.getWeekdayOvertimeRate();
        configData.holidayRate = config.getHolidayOvertimeRate();
        configData.monthlyBaseSalary = config.getMonthlyBaseSalary();
        
        data.assistantConfig = configData;

        String jsonString = gson.toJson(data);
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            ZipEntry entry = new ZipEntry(JSON_FILE_NAME);
            zos.putNextEntry(entry);
            zos.write(jsonString.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    public static BackupData importFromZip(Context context, Uri uri) throws Exception {
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
                    BackupData data = gson.fromJson(sb.toString(), BackupData.class);

                    if (data.autoAssetRules != null && !data.autoAssetRules.isEmpty()) {
                         for (String ruleStr : data.autoAssetRules) {
                            AutoAssetManager.AssetRule rule = AutoAssetManager.AssetRule.fromString(ruleStr);
                            if (rule != null) {
                                AutoAssetManager.addRule(context, rule);
                            }
                        }
                    }

                    if (data.assistantConfig != null) {
                        restoreAssistantConfig(context, data.assistantConfig);
                    }

                    if (data.subCategoryMap != null) {
                        for (Map.Entry<String, List<String>> entryMap : data.subCategoryMap.entrySet()) {
                            CategoryManager.saveSubCategories(context, entryMap.getKey(), entryMap.getValue());
                        }
                    }

                    return data;
                }
            }
        }
        throw new Exception("无法识别的备份文件：未找到数据文件 " + JSON_FILE_NAME);
    }

    // ============================================================================================
    // Excel (CSV) 导出/导入
    // ============================================================================================

    public static void exportToExcel(Context context, Uri uri, List<Transaction> transactions, List<AssetAccount> assets) throws Exception {
        if (transactions == null) transactions = new ArrayList<>();
        if (assets == null) assets = new ArrayList<>();

        Map<Integer, String> assetMap = new HashMap<>();
        for (AssetAccount asset : assets) {
            assetMap.put(asset.id, asset.name);
        }

        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append('\ufeff'); // BOM

        // 【修改】写入顺序：先写配置数据，最后写交易记录

        // -------------------------
        // 1. 资产列表 (原第2部分，现移至第1)
        // -------------------------
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

        // -------------------------
        // 2. 分类预设
        // -------------------------
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
        csvBuilder.append("\n\n");

        // -------------------------
        // 3. 二级分类配置
        // -------------------------
        csvBuilder.append("=== 二级分类配置 ===\n");
        csvBuilder.append("一级分类,二级分类列表\n");
        List<String> allCats = new ArrayList<>(expenseCategories);
        allCats.addAll(incomeCategories);
        for (String parent : allCats) {
            List<String> subs = CategoryManager.getSubCategories(context, parent);
            if (subs != null && !subs.isEmpty()) {
                csvBuilder.append(escapeCsv(parent)).append(",")
                          .append(escapeCsv(TextUtils.join("|", subs))).append("\n");
            }
        }
        csvBuilder.append("\n\n");

        // -------------------------
        // 4. 记账助手配置
        // -------------------------
        csvBuilder.append("=== 记账助手配置 ===\n");
        csvBuilder.append("配置项,值\n");
        AssistantConfig config = new AssistantConfig(context);
        csvBuilder.append("自动记账开关,").append(config.isEnabled()).append("\n");
        csvBuilder.append("退款监听,").append(config.isRefundEnabled()).append("\n");
        csvBuilder.append("资产模块,").append(config.isAssetsEnabled()).append("\n");
        csvBuilder.append("默认资产ID,").append(config.getDefaultAssetId()).append("\n");
        csvBuilder.append("工作日加班倍率,").append(config.getWeekdayOvertimeRate()).append("\n");
        csvBuilder.append("节假日加班倍率,").append(config.getHolidayOvertimeRate()).append("\n");
        csvBuilder.append("月薪底薪,").append(config.getMonthlyBaseSalary()).append("\n");
        csvBuilder.append("支出关键字,").append(escapeCsv(joinSet(config.getExpenseKeywords()))).append("\n");
        csvBuilder.append("收入关键字,").append(escapeCsv(joinSet(config.getIncomeKeywords()))).append("\n");
        csvBuilder.append("\n\n");

        // -------------------------
        // 5. 自动资产规则
        // -------------------------
        csvBuilder.append("=== 自动资产规则 ===\n");
        csvBuilder.append("应用包名,关键字,绑定资产ID\n");
        List<AutoAssetManager.AssetRule> rules = AutoAssetManager.getRules(context);
        for (AutoAssetManager.AssetRule rule : rules) {
            csvBuilder.append(escapeCsv(rule.packageName)).append(",");
            csvBuilder.append(escapeCsv(rule.keyword)).append(",");
            csvBuilder.append(rule.assetId).append("\n");
        }
        csvBuilder.append("\n\n");

        // -------------------------
        // 6. 交易记录 (移至最后)
        // -------------------------
        // 【新增】显式标题，以便导入时定位
        csvBuilder.append("=== 交易记录 ===\n");
        csvBuilder.append("交易ID,时间,类型,分类,金额,资产账户,记录标识,备注,二级分类\n");
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
            csvBuilder.append(escapeCsv(t.remark)).append(",");
            csvBuilder.append(escapeCsv(t.subCategory)).append("\n");
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            outputStream.write(csvBuilder.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

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

        if (lines.get(0).startsWith("\ufeff")) {
            lines.set(0, lines.get(0).substring(1));
        }

        List<Transaction> transactions = new ArrayList<>();
        List<AssetAccount> assets = new ArrayList<>();
        List<String> expenseCats = new ArrayList<>();
        List<String> incomeCats = new ArrayList<>();
        
        List<List<String>> transactionRows = new ArrayList<>();
        List<List<String>> assetRows = new ArrayList<>();
        List<List<String>> categoryRows = new ArrayList<>();
        List<List<String>> assistantRows = new ArrayList<>(); 
        List<List<String>> ruleRows = new ArrayList<>();
        List<List<String>> subCategoryRows = new ArrayList<>();

        // 默认状态：0=交易, 1=资产, 2=分类, 3=助手, 4=规则, 5=二级分类
        // 如果没有明确Header（旧版备份），默认认为是交易记录
        int currentSection = 0; 

        for (String line : lines) {
            String trimmed = line.trim();
            if (TextUtils.isEmpty(trimmed)) continue;

            // 检测区块头
            if (trimmed.contains("=== 交易记录 ===")) { // 新增检测
                currentSection = 0; continue;
            } else if (trimmed.contains("=== 资产账户列表 ===")) {
                currentSection = 1; continue;
            } else if (trimmed.contains("=== 分类预设配置 ===")) {
                currentSection = 2; continue;
            } else if (trimmed.contains("=== 二级分类配置 ===")) {
                currentSection = 5; continue;
            } else if (trimmed.contains("=== 记账助手配置 ===")) {
                currentSection = 3; continue;
            } else if (trimmed.contains("=== 自动资产规则 ===")) {
                currentSection = 4; continue;
            } else if (trimmed.startsWith("交易ID,") || trimmed.startsWith("ID,") 
                    || trimmed.startsWith("分类类型,") || trimmed.startsWith("配置项,")
                    || trimmed.startsWith("应用包名,") || trimmed.startsWith("一级分类,")) {
                // 跳过表头
                continue;
            }

            List<String> tokens = parseCsvLine(line);
            if (tokens.isEmpty()) continue;

            if (currentSection == 0) transactionRows.add(tokens);
            else if (currentSection == 1) assetRows.add(tokens);
            else if (currentSection == 2) categoryRows.add(tokens);
            else if (currentSection == 3) assistantRows.add(tokens);
            else if (currentSection == 4) ruleRows.add(tokens);
            else if (currentSection == 5) subCategoryRows.add(tokens);
        }

        // 解析资产 (ID映射)
        Map<String, Integer> assetNameToIdMap = new HashMap<>();
        for (List<String> row : assetRows) {
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
                asset.id = id;
                asset.currencySymbol = symbol;
                assets.add(asset);
                assetNameToIdMap.put(name, id);
            } catch (Exception e) {
                Log.e("BackupManager", "解析资产行失败", e);
            }
        }

        // 解析交易
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
        for (List<String> row : transactionRows) {
            // 至少要有到金额的列
            if (row.size() < 5) continue;
            try {
                Transaction t = new Transaction();
                try { t.id = Integer.parseInt(row.get(0)); } catch (Exception e) { t.id = 0; }
                Date date = sdf.parse(row.get(1));
                t.date = (date != null) ? date.getTime() : System.currentTimeMillis();
                String typeStr = row.get(2);
                t.type = "收入".equals(typeStr) ? 1 : ("支出".equals(typeStr) ? 0 : 2);
                t.category = row.get(3);
                t.amount = parseDoubleSafe(row.get(4));
                String assetName = (row.size() > 5) ? row.get(5) : "";
                t.assetId = assetNameToIdMap.containsKey(assetName) ? assetNameToIdMap.get(assetName) : 0;
                t.note = (row.size() > 6) ? row.get(6) : "";
                t.remark = (row.size() > 7) ? row.get(7) : "";
                t.subCategory = (row.size() > 8) ? row.get(8) : "";
                transactions.add(t);
            } catch (Exception e) {
                Log.e("BackupManager", "解析交易行失败", e);
            }
        }

        // 解析分类
        for (List<String> row : categoryRows) {
            if (row.size() < 2) continue;
            String type = row.get(0);
            String name = row.get(1);
            if ("支出".equals(type)) {
                if (!expenseCats.contains(name)) expenseCats.add(name);
            } else if ("收入".equals(type)) {
                if (!incomeCats.contains(name)) incomeCats.add(name);
            }
        }

        // 解析二级分类
        Map<String, List<String>> restoredSubMap = new HashMap<>();
        for (List<String> row : subCategoryRows) {
            if (row.size() < 2) continue;
            String parent = row.get(0);
            String subsStr = row.get(1);
            if (!TextUtils.isEmpty(subsStr)) {
                String[] subsArr = subsStr.split("\\|");
                List<String> subList = new ArrayList<>();
                for (String s : subsArr) {
                    if (!TextUtils.isEmpty(s)) subList.add(s);
                }
                CategoryManager.saveSubCategories(context, parent, subList);
                restoredSubMap.put(parent, subList);
            }
        }

        // 解析助手配置
        BackupData.AssistantConfigData restoredConfig = new BackupData.AssistantConfigData();
        AssistantConfig currentConfig = new AssistantConfig(context);
        // 初始化为当前值，防止文件缺少配置
        restoredConfig.enableAutoTrack = currentConfig.isEnabled();
        restoredConfig.enableRefund = currentConfig.isRefundEnabled();
        restoredConfig.enableAssets = currentConfig.isAssetsEnabled();
        restoredConfig.defaultAssetId = currentConfig.getDefaultAssetId();
        
        for (List<String> row : assistantRows) {
            if (row.size() < 2) continue;
            String key = row.get(0);
            String val = row.get(1);
            try {
                switch (key) {
                    case "自动记账开关": restoredConfig.enableAutoTrack = Boolean.parseBoolean(val); break;
                    case "退款监听": restoredConfig.enableRefund = Boolean.parseBoolean(val); break;
                    case "资产模块": restoredConfig.enableAssets = Boolean.parseBoolean(val); break;
                    case "默认资产ID": restoredConfig.defaultAssetId = Integer.parseInt(val); break;
                    case "工作日加班倍率": restoredConfig.weekdayRate = Float.parseFloat(val); break;
                    case "节假日加班倍率": restoredConfig.holidayRate = Float.parseFloat(val); break;
                    case "月薪底薪": restoredConfig.monthlyBaseSalary = Float.parseFloat(val); break;
                    case "支出关键字": restoredConfig.expenseKeywords = splitSet(val); break;
                    case "收入关键字": restoredConfig.incomeKeywords = splitSet(val); break;
                }
            } catch (Exception e) {
                Log.e("BackupManager", "解析配置行失败: " + key, e);
            }
        }
        restoreAssistantConfig(context, restoredConfig);

        // 解析自动资产规则
        List<String> restoredRules = new ArrayList<>();
        for (List<String> row : ruleRows) {
            if (row.size() < 3) continue;
            String pkg = row.get(0);
            String kw = row.get(1);
            try {
                int id = Integer.parseInt(row.get(2));
                AutoAssetManager.AssetRule rule = new AutoAssetManager.AssetRule(pkg, kw, id);
                AutoAssetManager.addRule(context, rule);
                restoredRules.add(rule.toString());
            } catch (Exception e) {
                Log.e("BackupManager", "解析规则行失败", e);
            }
        }

        BackupData data = new BackupData(transactions, assets);
        data.expenseCategories = expenseCats;
        data.incomeCategories = incomeCats;
        data.assistantConfig = restoredConfig;
        data.autoAssetRules = restoredRules;
        data.subCategoryMap = restoredSubMap;
        return data;
    }

    // ... (rest of the helper methods: restoreAssistantConfig, joinSet, splitSet, parseCsvLine, parseDoubleSafe, escapeCsv remain unchanged) ...
    private static void restoreAssistantConfig(Context context, BackupData.AssistantConfigData cd) {
        if (cd == null) return;
        AssistantConfig config = new AssistantConfig(context);
        config.setEnabled(cd.enableAutoTrack);
        config.setRefundEnabled(cd.enableRefund);
        config.setAssetsEnabled(cd.enableAssets);
        config.setDefaultAssetId(cd.defaultAssetId);
        config.setWeekdayOvertimeRate(cd.weekdayRate);
        config.setHolidayOvertimeRate(cd.holidayRate);
        config.setMonthlyBaseSalary(cd.monthlyBaseSalary);

        if (cd.expenseKeywords != null) {
            for (String k : cd.expenseKeywords) config.addExpenseKeyword(k);
        }
        if (cd.incomeKeywords != null) {
            for (String k : cd.incomeKeywords) config.addIncomeKeyword(k);
        }
    }

    private static String joinSet(Set<String> set) {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append("|");
            sb.append(s);
        }
        return sb.toString();
    }

    private static Set<String> splitSet(String val) {
        Set<String> set = new HashSet<>();
        if (TextUtils.isEmpty(val)) return set;
        String[] parts = val.split("\\|");
        for (String p : parts) {
            if (!TextUtils.isEmpty(p)) set.add(p);
        }
        return set;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"'); i++; 
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString()); sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private static double parseDoubleSafe(String val) {
        try { return Double.parseDouble(val); } catch (Exception e) { return 0.0; }
    }
    
    private static String escapeCsv(String value) {
        if (value == null) return "";
        String result = value.replace("\"", "\"\""); 
        if (result.contains(",") || result.contains("\n") || result.contains("\"")) {
            return "\"" + result + "\"";
        }
        return result;
    }
}