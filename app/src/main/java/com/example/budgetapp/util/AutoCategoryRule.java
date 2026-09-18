package com.example.budgetapp.util;

import org.json.JSONException;
import org.json.JSONObject;

/** A user rule that maps an automatic record identifier to a category. */
public class AutoCategoryRule {
    public static final int MATCH_CONTAINS = 0;
    public static final int MATCH_EQUALS = 1;
    public static final int MATCH_REGEX = 2;

    private String packageName;
    private String expression;
    private int matchType;
    private int sourceType;
    private int targetType;
    private String category;
    private String subCategory;

    public AutoCategoryRule() {
        this("", "", MATCH_CONTAINS, 2, 0, "", "");
    }

    public AutoCategoryRule(String packageName, String expression, int matchType,
                            int transactionType, String category, String subCategory) {
        this(packageName, expression, matchType, transactionType, transactionType, category, subCategory);
    }

    public AutoCategoryRule(String packageName, String expression, int matchType,
                            int sourceType, int targetType, String category, String subCategory) {
        this.packageName = packageName == null ? "" : packageName;
        this.expression = expression == null ? "" : expression;
        this.matchType = matchType;
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.category = category == null ? "" : category;
        this.subCategory = subCategory == null ? "" : subCategory;
    }

    public String getPackageName() { return packageName; }
    public String getExpression() { return expression; }
    public int getMatchType() { return matchType; }
    public int getSourceType() { return sourceType; }
    public int getTargetType() { return targetType; }
    /** Backward-compatible alias for callers that only need the rule target. */
    public int getTransactionType() { return targetType; }
    public String getCategory() { return category; }
    public String getSubCategory() { return subCategory; }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("packageName", packageName);
        json.put("expression", expression);
        json.put("matchType", matchType);
        json.put("sourceType", sourceType);
        json.put("targetType", targetType);
        json.put("category", category);
        json.put("subCategory", subCategory);
        return json;
    }

    public static AutoCategoryRule fromJson(JSONObject json) throws JSONException {
        int legacyType = json.optInt("transactionType", 0);
        int sourceType = json.has("sourceType") ? json.optInt("sourceType", 2) : legacyType;
        int targetType = json.has("targetType") ? json.optInt("targetType", legacyType) : legacyType;
        return new AutoCategoryRule(
                json.optString("packageName", ""),
                json.optString("expression", ""),
                json.optInt("matchType", MATCH_CONTAINS),
                sourceType, targetType,
                json.optString("category", ""),
                json.optString("subCategory", ""));
    }
}
