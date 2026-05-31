package com.example.budgetapp.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI模型配置文件
 * 用于保存和管理多个AI模型配置
 */
public class AiModelProfile {
    private static final String PREFS_NAME = "ai_model_profiles";
    private static final String KEY_PROFILES = "profiles";
    
    public String id;  // 唯一标识
    public String name;  // 配置名称
    public String modelName;  // 模型名称
    public String baseUrl;  // Base URL
    public String apiKey;  // API Key
    public String type;  // 类型：text, vision, audio
    
    public AiModelProfile() {
        this.id = String.valueOf(System.currentTimeMillis());
    }
    
    public AiModelProfile(String name, String modelName, String baseUrl, String apiKey, String type) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.name = name;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.type = type;
    }
    
    // 转换为JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("modelName", modelName);
        json.put("baseUrl", baseUrl);
        json.put("apiKey", apiKey);
        json.put("type", type);
        return json;
    }
    
    // 从JSON创建
    public static AiModelProfile fromJson(JSONObject json) throws JSONException {
        AiModelProfile profile = new AiModelProfile();
        profile.id = json.getString("id");
        profile.name = json.getString("name");
        profile.modelName = json.getString("modelName");
        profile.baseUrl = json.getString("baseUrl");
        profile.apiKey = json.getString("apiKey");
        profile.type = json.getString("type");
        return profile;
    }
    
    // 保存所有配置
    public static void saveProfiles(Context context, List<AiModelProfile> profiles) {
        try {
            JSONArray array = new JSONArray();
            for (AiModelProfile profile : profiles) {
                array.put(profile.toJson());
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PROFILES, array.toString())
                    .apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    // 加载所有配置
    public static List<AiModelProfile> loadProfiles(Context context) {
        List<AiModelProfile> profiles = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_PROFILES, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                profiles.add(fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return profiles;
    }
    
    // 根据类型加载配置
    public static List<AiModelProfile> loadProfilesByType(Context context, String type) {
        List<AiModelProfile> allProfiles = loadProfiles(context);
        List<AiModelProfile> filtered = new ArrayList<>();
        for (AiModelProfile profile : allProfiles) {
            if (type.equals(profile.type)) {
                filtered.add(profile);
            }
        }
        return filtered;
    }
    
    // 添加配置
    public static void addProfile(Context context, AiModelProfile profile) {
        List<AiModelProfile> profiles = loadProfiles(context);
        profiles.add(profile);
        saveProfiles(context, profiles);
    }
    
    // 删除配置
    public static void deleteProfile(Context context, String id) {
        List<AiModelProfile> profiles = loadProfiles(context);
        profiles.removeIf(p -> p.id.equals(id));
        saveProfiles(context, profiles);
    }
    
    // 更新配置
    public static void updateProfile(Context context, AiModelProfile profile) {
        List<AiModelProfile> profiles = loadProfiles(context);
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profile.id)) {
                profiles.set(i, profile);
                break;
            }
        }
        saveProfiles(context, profiles);
    }
    
    // 根据ID查找配置
    public static AiModelProfile findById(Context context, String id) {
        List<AiModelProfile> profiles = loadProfiles(context);
        for (AiModelProfile profile : profiles) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }
}
