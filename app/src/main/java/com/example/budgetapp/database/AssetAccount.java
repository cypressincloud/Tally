package com.example.budgetapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "asset_accounts")
public class AssetAccount {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public double amount;

    // 0: 资产, 1: 负债, 2: 借出, 3: 理财, 4: 分期
    public int type;

    public long updateTime;
    public String currencySymbol;

    // 【新增】是否计入总资产 (默认 true，兼容老数据)
    public boolean isIncludedInTotal = true;

    // 【新增】资产背景颜色 (0: 默认, 1: 红色, 2: 绿色)
    public int colorType = 0;

    // ========== 新增：自定义颜色 HEX 值 ==========
    public String customColorHex = "";
    // ==========================================

    // === 新增理财专属字段 ===
    public boolean isFixedTerm; // true 定期, false 活期
    public int durationMonths;  // 存储时间(个月)
    public double interestRate; // 年利率(%)
    public double expectedReturn; // 预计结算最终资产

    public long depositDate; // <--- 【新增】存入时间(时间戳)

    // === 新增：计息方式 ===
    public boolean isCompoundInterest; // true 复利(利滚利), false 单利

    // === 新增：分期专属字段 (type == 4 时使用) ===
    public int totalInstallments = 0;        // 总期数
    public double installmentAmount = 0.0;   // 每期金额
    public String paidInstallments = "[]";   // 已还期数 JSON 数组，如 "[1,3,5]"

    public AssetAccount(String name, double amount, int type) {
        this.name = name;
        this.amount = amount;
        this.type = type;
        this.updateTime = System.currentTimeMillis();
        this.currencySymbol = "¥";
    }

    // ========== 分期相关辅助方法 ==========
    
    /**
     * 获取已还期数列表
     */
    public List<Integer> getPaidInstallmentsList() {
        try {
            JSONArray array = new JSONArray(paidInstallments);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getInt(i));
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 设置已还期数列表
     */
    public void setPaidInstallmentsList(List<Integer> list) {
        try {
            JSONArray array = new JSONArray(list);
            paidInstallments = array.toString();
        } catch (Exception e) {
            paidInstallments = "[]";
        }
    }

    /**
     * 获取剩余期数
     */
    public int getRemainingInstallments() {
        if (type != 4) return 0;
        return totalInstallments - getPaidInstallmentsList().size();
    }

    /**
     * 获取剩余应还金额
     */
    public double getRemainingAmount() {
        if (type != 4) return amount;
        return getRemainingInstallments() * installmentAmount;
    }

    /**
     * 获取总金额
     */
    public double getTotalAmount() {
        if (type != 4) return amount;
        return totalInstallments * installmentAmount;
    }

    /**
     * 获取已还金额
     */
    public double getPaidAmount() {
        if (type != 4) return 0;
        return getPaidInstallmentsList().size() * installmentAmount;
    }
}
