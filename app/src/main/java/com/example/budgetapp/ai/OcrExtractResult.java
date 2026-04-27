package com.example.budgetapp.ai;

import java.util.Locale;

public class OcrExtractResult {
    public final String rawText;
    public final String normalizedText;
    public final boolean hasAmount;
    public final String confidenceHint;
    public final double amountCandidate;
    public final boolean hasTradeKeyword;
    public final boolean hasIncomeKeyword;

    public OcrExtractResult(
            String rawText,
            String normalizedText,
            boolean hasAmount,
            String confidenceHint,
            double amountCandidate,
            boolean hasTradeKeyword,
            boolean hasIncomeKeyword
    ) {
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.hasAmount = hasAmount;
        this.confidenceHint = confidenceHint;
        this.amountCandidate = amountCandidate;
        this.hasTradeKeyword = hasTradeKeyword;
        this.hasIncomeKeyword = hasIncomeKeyword;
    }

    public boolean hasText() {
        return normalizedText != null && !normalizedText.trim().isEmpty();
    }

    public boolean isSufficientForAccounting() {
        return hasText() && hasAmount && hasTradeKeyword;
    }

    public String buildPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("以下是支付截图经过本地 OCR 提取的文字，请根据这些文字提取记账信息。");
        builder.append("如果是付款、消费、账单类截图，通常识别为支出；如果是收款、到账类截图，识别为收入。");
        if (hasAmount) {
            builder.append("\nOCR 推测金额：")
                    .append(String.format(Locale.US, "%.2f", amountCandidate));
        }
        builder.append("\nOCR 文字如下：\n");
        builder.append(normalizedText);
        return builder.toString();
    }
}
