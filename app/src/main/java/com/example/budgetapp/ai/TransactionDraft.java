package com.example.budgetapp.ai;

import com.example.budgetapp.database.Transaction;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionDraft {
    public int type;
    public double amount;
    public String category;
    public String subCategory;
    public String note; // AI 解析出的备注文本
    public long date;
    public int assetId;
    public String currencySymbol;
    public boolean excludeFromBudget;
    public String photoPath; // 截图保存路径
    
    // 转账相关字段
    public boolean isTransfer; // 是否为转账
    public String fromAsset; // 转出资产名称（AI识别的原始文本）
    public String toAsset; // 转入资产名称（AI识别的原始文本）
    public int fromAssetId; // 转出资产ID
    public int toAssetId; // 转入资产ID
    public double discount; // 优惠金额（如还款优惠）

    public Transaction toTransaction() {
        Transaction transaction = new Transaction();
        transaction.date = date;
        transaction.type = type;
        transaction.amount = amount;
        transaction.category = category;
        transaction.subCategory = subCategory;

        // 1. 格式化当前时间为 "MM-dd HH:mm " (注意末尾有一个空格)
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm ", Locale.getDefault());
        String timePrefix = sdf.format(new Date(date));

        // 2. 将“时间前缀”与“AI提取的备注”拼接，存入“记录标识”(note 字段)
        String parsedNote = note == null ? "" : note.trim();
        transaction.note = timePrefix + parsedNote;

        // 3. remark 字段留空
        transaction.remark = "";

        transaction.assetId = assetId;
        transaction.currencySymbol = currencySymbol;
        transaction.excludeFromBudget = excludeFromBudget;
        transaction.photoPath = photoPath == null ? "" : photoPath;
        transaction.targetObject = "";

        return transaction;
    }
}