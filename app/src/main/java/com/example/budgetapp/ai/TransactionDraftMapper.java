package com.example.budgetapp.ai;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionDraftMapper {

    public static TransactionDraft fromJson(Context context, JSONObject object) {
        TransactionDraft draft = new TransactionDraft();
        draft.type = normalizeType(object.opt("type"));
        draft.amount = Math.max(0d, object.optDouble("amount", 0d));
        draft.note = firstNonEmpty(
                object.optString("note", ""),
                object.optString("remark", ""),
                object.optString("description", "")
        );
        draft.date = System.currentTimeMillis();
        draft.excludeFromBudget = false;

        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean currencyEnabled = prefs.getBoolean("enable_currency", false);
        draft.currencySymbol = currencyEnabled
                ? prefs.getString("default_currency_symbol", "¥")
                : "¥";

        String rawCategory = firstNonEmpty(
                object.optString("category", ""),
                object.optString("mainCategory", ""),
                object.optString("primaryCategory", "")
        );
        String rawSubCategory = firstNonEmpty(
                object.optString("subCategory", ""),
                object.optString("subcategory", ""),
                object.optString("secondaryCategory", "")
        );
        String rawAsset = firstNonEmpty(
                object.optString("asset", ""),
                object.optString("assetName", ""),
                object.optString("account", ""),
                object.optString("paymentAccount", ""),
                object.optString("paymentMethod", ""),
                object.optString("wallet", ""),
                object.optString("sourceAsset", ""),
                object.optString("destinationAsset", "")
        );

        normalizeCategories(context, draft, rawCategory, rawSubCategory);
        draft.assetId = resolveAssetId(context, rawAsset, draft.note);

        if (draft.note.isEmpty()) {
            draft.note = firstNonEmpty(rawSubCategory, rawCategory, rawAsset);
        }
        return draft;
    }

    private static void normalizeCategories(Context context, TransactionDraft draft, String rawCategory, String rawSubCategory) {
        List<String> primaryCategories = getAiPrimaryCategories(context, draft.type);
        String fallback = findFallbackCategory(primaryCategories);

        CategoryMatch preferredPrimary = matchPrimary(primaryCategories, rawCategory);
        CategoryMatch matchedSubCategory = matchSubCategoryAcrossAll(context, primaryCategories, rawSubCategory, preferredPrimary == null ? null : preferredPrimary.value);
        if (matchedSubCategory == null) {
            matchedSubCategory = matchSubCategoryAcrossAll(context, primaryCategories, rawCategory, preferredPrimary == null ? null : preferredPrimary.value);
        }

        if (matchedSubCategory != null) {
            draft.category = matchedSubCategory.parent;
            draft.subCategory = matchedSubCategory.value;
            return;
        }

        if (preferredPrimary != null) {
            draft.category = preferredPrimary.value;
            draft.subCategory = "";
            return;
        }

        draft.category = fallback;
        draft.subCategory = "";
    }

    private static int resolveAssetId(Context context, String rawAsset, String note) {
        List<AssetAccount> assets = AppDatabase.getDatabase(context).assetAccountDao().getAllAssetsSync();
        List<AssetAccount> selectableAssets = new ArrayList<>();
        if (assets != null) {
            for (AssetAccount asset : assets) {
                if (asset != null && (asset.type == 0 || asset.type == 1 || asset.type == 2)) {
                    selectableAssets.add(asset);
                }
            }
        }

        String combinedHint = firstNonEmpty(rawAsset, note);
        AssetAccount matchedAsset = matchAsset(selectableAssets, combinedHint);
        if (matchedAsset != null) {
            return matchedAsset.id;
        }

        int defaultAssetId = new AssistantConfig(context).getDefaultAssetId();
        if (defaultAssetId > 0) {
            for (AssetAccount asset : selectableAssets) {
                if (asset.id == defaultAssetId) {
                    return defaultAssetId;
                }
            }
        }
        return 0;
    }

    private static AssetAccount matchAsset(List<AssetAccount> assets, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalizedRaw = normalize(raw);
        for (AssetAccount asset : assets) {
            if (asset.name != null && normalize(asset.name).equals(normalizedRaw)) {
                return asset;
            }
        }
        for (AssetAccount asset : assets) {
            if (asset.name == null) {
                continue;
            }
            String normalizedName = normalize(asset.name);
            if (normalizedRaw.contains(normalizedName) || normalizedName.contains(normalizedRaw)) {
                return asset;
            }
        }
        return null;
    }

    private static List<String> getAiPrimaryCategories(Context context, int type) {
        List<String> source = type == 1
                ? CategoryManager.getIncomeCategories(context)
                : CategoryManager.getExpenseCategories(context);
        List<String> categories = new ArrayList<>();
        if (source != null) {
            for (String category : source) {
                if (category != null && !category.trim().isEmpty()) {
                    categories.add(category.trim());
                }
            }
        }
        if (!categories.contains("其他")) {
            categories.add("其他");
        }
        return categories;
    }

    private static String findFallbackCategory(List<String> categories) {
        if (categories.contains("其他")) {
            return "其他";
        }
        if (categories.contains("自定义")) {
            return "自定义";
        }
        return categories.isEmpty() ? "其他" : categories.get(categories.size() - 1);
    }

    private static CategoryMatch matchPrimary(List<String> categories, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalizedRaw = normalize(raw);
        for (String category : categories) {
            if (normalize(category).equals(normalizedRaw)) {
                return new CategoryMatch(category, category);
            }
        }
        for (String category : categories) {
            String normalizedCategory = normalize(category);
            if (normalizedCategory.contains(normalizedRaw) || normalizedRaw.contains(normalizedCategory)) {
                return new CategoryMatch(category, category);
            }
        }
        return null;
    }

    private static CategoryMatch matchSubCategoryAcrossAll(Context context, List<String> primaryCategories, String raw, String preferredPrimary) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        if (preferredPrimary != null) {
            String matchedSub = matchSubCategory(context, preferredPrimary, raw);
            if (!matchedSub.isEmpty()) {
                return new CategoryMatch(preferredPrimary, matchedSub);
            }
        }
        for (String category : primaryCategories) {
            if ("其他".equals(category)) {
                continue;
            }
            String matchedSub = matchSubCategory(context, category, raw);
            if (!matchedSub.isEmpty()) {
                return new CategoryMatch(category, matchedSub);
            }
        }
        return null;
    }

    private static String matchSubCategory(Context context, String primaryCategory, String raw) {
        List<String> subCategories = CategoryManager.getSubCategories(context, primaryCategory);
        if (subCategories == null || raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String normalizedRaw = normalize(raw);
        for (String subCategory : subCategories) {
            if (subCategory != null && normalize(subCategory).equals(normalizedRaw)) {
                return subCategory;
            }
        }
        for (String subCategory : subCategories) {
            if (subCategory == null) {
                continue;
            }
            String normalized = normalize(subCategory);
            if (normalized.contains(normalizedRaw) || normalizedRaw.contains(normalized)) {
                return subCategory;
            }
        }
        return "";
    }

    private static int normalizeType(Object typeValue) {
        if (typeValue instanceof Number) {
            return ((Number) typeValue).intValue() == 1 ? 1 : 0;
        }
        String text = normalize(String.valueOf(typeValue));
        return (text.contains("收入") || text.contains("income") || text.contains("进账")) ? 1 : 0;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace("：", "")
                .replace(":", "")
                .replace(",", "")
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static class CategoryMatch {
        final String parent;
        final String value;

        CategoryMatch(String parent, String value) {
            this.parent = parent;
            this.value = value;
        }
    }
}
