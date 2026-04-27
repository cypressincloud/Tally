package com.example.budgetapp.ai;

import com.example.budgetapp.database.Transaction;

public class TransactionDraft {
    public int type;
    public double amount;
    public String category;
    public String subCategory;
    public String note;
    public long date;
    public int assetId;
    public String currencySymbol;
    public boolean excludeFromBudget;

    public Transaction toTransaction() {
        Transaction transaction = new Transaction();
        transaction.date = date;
        transaction.type = type;
        transaction.amount = amount;
        transaction.category = category;
        transaction.subCategory = subCategory;
        transaction.note = note;
        transaction.assetId = assetId;
        transaction.currencySymbol = currencySymbol;
        transaction.excludeFromBudget = excludeFromBudget;
        transaction.remark = "";
        transaction.photoPath = "";
        transaction.targetObject = "";
        return transaction;
    }
}
