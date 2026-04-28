package com.example.budgetapp.util;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * AI分类关键字规则数据模型
 * 用于存储关键字到分类的映射规则
 */
public class CategoryRule {
    private String keyword;      // 关键字
    private String category;     // 主分类名称
    private String subCategory;  // 子分类名称（可选）
    private int type;            // 类型: 0=支出, 1=收入

    /**
     * 构造函数（不含子分类）
     * @param keyword 关键字
     * @param category 主分类名称
     * @param type 类型 (0=支出, 1=收入)
     */
    public CategoryRule(String keyword, String category, int type) {
        this.keyword = keyword;
        this.category = category;
        this.subCategory = "";
        this.type = type;
    }

    /**
     * 构造函数（含子分类）
     * @param keyword 关键字
     * @param category 主分类名称
     * @param subCategory 子分类名称
     * @param type 类型 (0=支出, 1=收入)
     */
    public CategoryRule(String keyword, String category, String subCategory, int type) {
        this.keyword = keyword;
        this.category = category;
        this.subCategory = subCategory != null ? subCategory : "";
        this.type = type;
    }

    // Getter方法
    public String getKeyword() {
        return keyword;
    }

    public String getCategory() {
        return category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public int getType() {
        return type;
    }

    // Setter方法
    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory != null ? subCategory : "";
    }

    public void setType(int type) {
        this.type = type;
    }

    /**
     * 将规则转换为JSON对象
     * @return JSON对象
     * @throws JSONException JSON序列化异常
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("keyword", keyword);
        json.put("category", category);
        json.put("subCategory", subCategory);
        json.put("type", type);
        return json;
    }

    /**
     * 从JSON对象创建规则
     * @param json JSON对象
     * @return CategoryRule实例
     * @throws JSONException JSON解析异常
     */
    public static CategoryRule fromJson(JSONObject json) throws JSONException {
        String keyword = json.getString("keyword");
        String category = json.getString("category");
        // 向后兼容：如果没有subCategory字段，默认为空字符串
        String subCategory = json.optString("subCategory", "");
        int type = json.getInt("type");
        return new CategoryRule(keyword, category, subCategory, type);
    }

    @Override
    public String toString() {
        return "CategoryRule{" +
                "keyword='" + keyword + '\'' +
                ", category='" + category + '\'' +
                ", subCategory='" + subCategory + '\'' +
                ", type=" + type +
                '}';
    }
}
