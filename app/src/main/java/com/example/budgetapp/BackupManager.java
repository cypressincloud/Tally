package com.example.budgetapp;

import android.content.Context;
import android.net.Uri;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
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

    /**
     * 导出：将交易记录和资产列表打包成 Zip 文件并写入 Uri
     */
    public static void exportToZip(Context context, Uri uri, List<Transaction> transactions, List<AssetAccount> assets) throws Exception {
        if (transactions == null) {
            transactions = new ArrayList<>();
        }
        if (assets == null) {
            assets = new ArrayList<>();
        }

        // 1. 将对象转为 JSON 字符串
        Gson gson = new Gson();
        BackupData data = new BackupData(transactions, assets);
        String jsonString = gson.toJson(data);

        // 2. 写入 Zip 文件
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(outputStream)) {

            // 在 Zip 中创建一个名为 backup_data.json 的条目
            ZipEntry entry = new ZipEntry(JSON_FILE_NAME);
            zos.putNextEntry(entry);
            zos.write(jsonString.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    /**
     * 导出：将交易记录导出为 Excel 可识别的 CSV 文件
     * 【修改】恢复“时间”列（格式：yyyy年MM月dd日），保留“记录标识”和“备注”
     */
    public static void exportToExcel(Context context, Uri uri, List<Transaction> transactions, List<AssetAccount> assets) throws Exception {
        if (transactions == null) transactions = new ArrayList<>();
        if (assets == null) assets = new ArrayList<>();

        // 1. 构建资产ID到名称的映射
        Map<Integer, String> assetMap = new HashMap<>();
        for (AssetAccount asset : assets) {
            assetMap.put(asset.id, asset.name);
        }

        StringBuilder csvBuilder = new StringBuilder();

        // 2. 添加 BOM (Byte Order Mark) 以支持 Excel 正确显示中文 UTF-8
        csvBuilder.append('\ufeff');

        // 3. 写入表头
        // 【修改】增加了 "时间" 列
        csvBuilder.append("交易ID,时间,类型,分类,金额,资产账户,记录标识,备注\n");

        // 【修改】格式化器：使用 RecordFragment 中相同的格式 "yyyy年MM月dd日"
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);

        // 4. 遍历数据
        for (Transaction t : transactions) {
            // ID
            csvBuilder.append(t.id).append(",");

            // 【新增】时间列：格式化为 yyyy年MM月dd日
            csvBuilder.append(sdf.format(new Date(t.date))).append(",");

            // 类型
            String typeStr = (t.type == 0) ? "支出" : (t.type == 1 ? "收入" : "其他");
            csvBuilder.append(typeStr).append(",");

            // 分类
            csvBuilder.append(escapeCsv(t.category)).append(",");

            // 金额
            csvBuilder.append(t.amount).append(",");

            // 资产账户
            String assetName = assetMap.get(t.assetId);
            if (assetName == null) assetName = "未知账户";
            csvBuilder.append(escapeCsv(assetName)).append(",");

            // 记录标识 (这里使用原来的 note 内容，或时间戳？)
            // 根据上下文，您之前要求“记录标识不是只显示时间戳就是原来的备注”，
            // 这里为了稳妥，且因为您要求“时间”列是日期，
            // 建议“记录标识”列存放 唯一的毫秒级时间戳 (以便技术识别或作为ID)，
            // 或者如果您想保留原来的 manual note，可以用 escapeCsv(t.note)。
            // 鉴于您之前的指令 "记录标识不是只显示时间戳就是原来的'备注'" 比较含糊，
            // 这里我将其设置为 **原来的备注内容 (t.note)**，因为这更有可读性。
            // 如果您确实需要毫秒级时间戳作为标识，请告知。
            csvBuilder.append(escapeCsv(t.note)).append(",");

            // 备注 (对应 remark 内容)
            csvBuilder.append(escapeCsv(t.remark)).append("\n");
        }

        // 5. 写入文件
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            outputStream.write(csvBuilder.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    // CSV 转义辅助方法
    private static String escapeCsv(String value) {
        if (value == null) return "";
        String result = value.replace("\"", "\"\""); // 双引号转义
        if (result.contains(",") || result.contains("\n") || result.contains("\"")) {
            return "\"" + result + "\""; // 包含特殊字符则包裹
        }
        return result;
    }

    /**
     * 导入：从 Uri 读取 Zip 文件并解析出备份数据对象
     */
    public static BackupData importFromZip(Context context, Uri uri) throws Exception {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            // 遍历 Zip 中的文件
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(JSON_FILE_NAME)) {
                    // 找到对应的 json 文件，读取内容
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }

                    // 解析 JSON
                    Gson gson = new Gson();
                    BackupData data = gson.fromJson(sb.toString(), BackupData.class);

                    if (data != null) {
                        return data;
                    } else {
                        throw new Exception("备份文件内容为空或格式不正确");
                    }
                }
            }
        }
        throw new Exception("无法识别的备份文件：未找到数据文件 " + JSON_FILE_NAME);
    }
}