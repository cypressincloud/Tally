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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private static final String JSON_FILE_NAME = "backup_data.json";

    /**
     * 导出：将交易记录和资产列表打包成 Zip 文件并写入 Uri
     * 【修改】增加 assets 参数
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
        // 【修改】传入 assets
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
     * 导入：从 Uri 读取 Zip 文件并解析出备份数据对象
     * 【修改】返回类型改为 BackupData
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

                    // 【修改】返回整个数据对象，不再只返回 records
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