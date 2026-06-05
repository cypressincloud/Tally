package com.example.budgetapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 汇率管理器
 * 使用免费的 exchangerate-api.com 获取实时汇率
 */
public class ExchangeRateManager {
    private static final String TAG = "ExchangeRateManager";
    private static final String PREFS_NAME = "exchange_rate_prefs";
    private static final String KEY_RATES = "rates_json";
    private static final String KEY_LAST_UPDATE = "last_update_time";
    private static final String KEY_BASE_CURRENCY = "base_currency";
    
    // 使用免费API: https://api.exchangerate-api.com/v4/latest/{base}
    private static final String API_URL_TEMPLATE = "https://api.exchangerate-api.com/v4/latest/%s";
    
    // 缓存有效期：24小时
    private static final long CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000;
    
    private final Context context;
    private final SharedPreferences prefs;
    
    public ExchangeRateManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取汇率（从缓存或API）
     * @param fromCurrency 源货币代码（如 "USD"）
     * @param toCurrency 目标货币代码（如 "CNY"）
     * @param callback 回调接口
     */
    public void getExchangeRate(String fromCurrency, String toCurrency, ExchangeRateCallback callback) {
        // 如果是相同货币，汇率为1
        if (fromCurrency.equals(toCurrency)) {
            callback.onSuccess(1.0);
            return;
        }
        
        // 检查缓存
        if (isCacheValid(fromCurrency)) {
            double rate = getRateFromCache(fromCurrency, toCurrency);
            if (rate > 0) {
                Log.d(TAG, "Using cached rate: " + fromCurrency + " -> " + toCurrency + " = " + rate);
                callback.onSuccess(rate);
                return;
            }
        }
        
        // 从API获取
        fetchRatesFromAPI(fromCurrency, callback, toCurrency);
    }
    
    /**
     * 批量转换金额（用于资产列表）
     * @param amounts Map<货币代码, 金额>
     * @param targetCurrency 目标货币
     * @param callback 回调
     */
    public void convertAmounts(Map<String, Double> amounts, String targetCurrency, ConvertCallback callback) {
        // 先检查缓存是否足够
        boolean needRefresh = false;
        for (String currency : amounts.keySet()) {
            if (!currency.equals(targetCurrency) && !isCacheValid(targetCurrency)) {
                needRefresh = true;
                break;
            }
        }
        
        if (!needRefresh) {
            // 使用缓存计算
            double total = 0;
            for (Map.Entry<String, Double> entry : amounts.entrySet()) {
                String currency = entry.getKey();
                double amount = entry.getValue();
                
                if (currency.equals(targetCurrency)) {
                    total += amount;
                } else {
                    double rate = getRateFromCache(targetCurrency, currency);
                    if (rate > 0) {
                        total += amount / rate; // 转换为目标货币
                    } else {
                        // 缓存不完整，需要刷新
                        needRefresh = true;
                        break;
                    }
                }
            }
            
            if (!needRefresh) {
                callback.onSuccess(total);
                return;
            }
        }
        
        // 需要从API刷新
        fetchRatesFromAPI(targetCurrency, new ExchangeRateCallback() {
            @Override
            public void onSuccess(double rate) {
                // 刷新成功，重新计算
                double total = 0;
                for (Map.Entry<String, Double> entry : amounts.entrySet()) {
                    String currency = entry.getKey();
                    double amount = entry.getValue();
                    
                    if (currency.equals(targetCurrency)) {
                        total += amount;
                    } else {
                        double r = getRateFromCache(targetCurrency, currency);
                        if (r > 0) {
                            total += amount / r;
                        }
                    }
                }
                callback.onSuccess(total);
            }
            
            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, null);
    }
    
    /**
     * 从缓存检查是否有效
     */
    private boolean isCacheValid(String baseCurrency) {
        long lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0);
        String cachedBase = prefs.getString(KEY_BASE_CURRENCY, "");
        
        return cachedBase.equals(baseCurrency) && 
               (System.currentTimeMillis() - lastUpdate) < CACHE_VALIDITY_MS;
    }
    
    /**
     * 从缓存获取汇率
     */
    private double getRateFromCache(String baseCurrency, String targetCurrency) {
        try {
            String ratesJson = prefs.getString(KEY_RATES, "{}");
            JSONObject rates = new JSONObject(ratesJson);
            
            if (rates.has(targetCurrency)) {
                return rates.getDouble(targetCurrency);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error reading cache", e);
        }
        return -1;
    }
    
    /**
     * 从API获取汇率
     */
    private void fetchRatesFromAPI(String baseCurrency, ExchangeRateCallback callback, String targetCurrency) {
        new Thread(() -> {
            try {
                String urlString = String.format(API_URL_TEMPLATE, baseCurrency);
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // 解析JSON
                    JSONObject json = new JSONObject(response.toString());
                    JSONObject rates = json.getJSONObject("rates");
                    
                    // 保存到缓存
                    prefs.edit()
                            .putString(KEY_RATES, rates.toString())
                            .putString(KEY_BASE_CURRENCY, baseCurrency)
                            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                            .apply();
                    
                    Log.d(TAG, "Fetched rates from API for base: " + baseCurrency);
                    
                    // 如果有指定目标货币，返回该汇率
                    if (targetCurrency != null && rates.has(targetCurrency)) {
                        double rate = rates.getDouble(targetCurrency);
                        callback.onSuccess(rate);
                    } else {
                        callback.onSuccess(1.0); // 默认返回1.0表示成功
                    }
                } else {
                    callback.onError("API返回错误: " + responseCode);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error fetching rates", e);
                callback.onError("网络错误: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        prefs.edit().clear().apply();
    }
    
    /**
     * 汇率回调接口
     */
    public interface ExchangeRateCallback {
        void onSuccess(double rate);
        void onError(String error);
    }
    
    /**
     * 转换回调接口
     */
    public interface ConvertCallback {
        void onSuccess(double convertedAmount);
        void onError(String error);
    }
}
