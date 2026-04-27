package com.example.budgetapp.ai;

import android.graphics.Bitmap;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenshotOcrHelper {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:[¥￥]\\s*)?(\\d{1,6}(?:\\.\\d{1,2})?)");
    private static final String[] TRADE_KEYWORDS = {
            "支付", "付款", "收款", "到账", "交易", "账单", "订单", "金额", "合计", "总计", "实付", "微信支付", "支付宝", "转账"
    };
    private static final String[] INCOME_KEYWORDS = {
            "收款", "到账", "入账", "收入", "退款成功"
    };

    public OcrExtractResult extract(Bitmap bitmap) throws Exception {
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        try {
            Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)));
            String rawText = text.getText() == null ? "" : text.getText().trim();
            String normalizedText = normalizeText(rawText);
            double amountCandidate = findAmountCandidate(normalizedText);
            boolean hasAmount = amountCandidate > 0d;
            boolean hasTradeKeyword = containsAny(normalizedText, TRADE_KEYWORDS);
            boolean hasIncomeKeyword = containsAny(normalizedText, INCOME_KEYWORDS);
            String confidenceHint = buildConfidenceHint(normalizedText, hasAmount, hasTradeKeyword);
            return new OcrExtractResult(
                    rawText,
                    normalizedText,
                    hasAmount,
                    confidenceHint,
                    amountCandidate,
                    hasTradeKeyword,
                    hasIncomeKeyword
            );
        } finally {
            recognizer.close();
        }
    }

    private String normalizeText(String rawText) {
        if (rawText == null) return "";
        return rawText.replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    private double findAmountCandidate(String text) {
        if (text == null || text.isEmpty()) return 0d;
        String[] lines = text.split("\\n");
        double bestAmount = 0d;
        int bestScore = Integer.MIN_VALUE;

        for (String line : lines) {
            Matcher matcher = AMOUNT_PATTERN.matcher(line);
            while (matcher.find()) {
                double amount = parseAmount(matcher.group(1));
                if (amount <= 0d) continue;
                int score = scoreLine(line, amount);
                if (score > bestScore || (score == bestScore && amount > bestAmount)) {
                    bestScore = score;
                    bestAmount = amount;
                }
            }
        }
        return bestAmount;
    }

    private int scoreLine(String line, double amount) {
        int score = (int) Math.round(amount);
        String normalized = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (normalized.contains("¥") || normalized.contains("￥")) score += 60;
        if (normalized.contains("支付") || normalized.contains("实付")) score += 120;
        if (normalized.contains("收款") || normalized.contains("到账")) score += 120;
        if (normalized.contains("金额") || normalized.contains("合计") || normalized.contains("总计")) score += 100;
        if (normalized.contains("优惠")) score -= 50;
        if (normalized.contains("手续费")) score -= 30;
        return score;
    }

    private double parseAmount(String rawAmount) {
        try {
            return Double.parseDouble(rawAmount);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private boolean containsAny(String text, String[] keywords) {
        if (text == null || text.isEmpty()) return false;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildConfidenceHint(String text, boolean hasAmount, boolean hasTradeKeyword) {
        if (text == null || text.trim().isEmpty()) {
            return "OCR 未提取到有效文字";
        }
        if (hasAmount && hasTradeKeyword) {
            return "OCR 已识别到金额和交易关键词";
        }
        if (hasAmount) {
            return "OCR 已识别到金额，但交易语义不够明确";
        }
        return "OCR 提取到了文字，但未找到可靠金额";
    }
}
