// src/main/java/com/example/budgetapp/ui/CalendarAdapter.java
package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.RenewalItem;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

    private List<LocalDate> days = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private List<RenewalItem> renewalItems = new ArrayList<>(); // 支持多项续费项目
    private LocalDate selectedDate;
    private final OnDateClickListener listener;
    private int filterMode = 0;
    private YearMonth currentMonth;

    private boolean isBudgetEnabled = false;
    private float monthlyBudget = 0f;

    // 增加一个公开方法用于接收配置
    public void setBudgetConfig(boolean enabled, float budget) {
        this.isBudgetEnabled = enabled;
        this.monthlyBudget = budget;
        notifyDataSetChanged();
    }

    public interface OnDateClickListener {
        void onDateClick(LocalDate date);
    }

    public CalendarAdapter(OnDateClickListener listener) {
        this.listener = listener;
    }

    /**
     * 更新续费项目列表
     */
    public void setRenewalItems(List<RenewalItem> items) {
        this.renewalItems = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setFilterMode(int mode) {
        this.filterMode = mode;
        notifyDataSetChanged();
    }

    public void setCurrentMonth(YearMonth month) {
        this.currentMonth = month;
    }

    public void updateData(List<LocalDate> days, List<Transaction> transactions) {
        this.days = days;
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
        notifyDataSetChanged();
    }

    private int getThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return context.getColor(typedValue.resourceId);
            }
            return typedValue.data;
        }
        return Color.GRAY;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocalDate date = days.get(position);
        if (date == null) {
            holder.tvDay.setText("");
            holder.tvNet.setText("");
            holder.itemView.setBackgroundResource(0);
            holder.itemView.setSelected(false);
            return;
        }

        Context context = holder.itemView.getContext();
        holder.tvDay.setText(String.valueOf(date.getDayOfMonth()));

        // 基础颜色
        int colorPrimaryText = getThemeColor(context, android.R.attr.textColorPrimary);
        int colorSecondaryText = getThemeColor(context, android.R.attr.textColorSecondary);
        int themeColor = context.getColor(R.color.app_yellow);

        int incomeRed = context.getColor(R.color.income_red);
        int expenseGreen = context.getColor(R.color.expense_green);

        boolean isCurrentMonth = currentMonth != null &&
                date.getYear() == currentMonth.getYear() &&
                date.getMonth() == currentMonth.getMonth();

        // 1. 计算默认字体颜色
        int defaultDayColor;
        if (isCurrentMonth) {
            holder.tvDay.setAlpha(1.0f);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            defaultDayColor = isWeekend ? themeColor : colorPrimaryText;
        } else {
            holder.tvDay.setAlpha(0.3f);
            defaultDayColor = colorSecondaryText;
        }

        // 2. 统计金额及颜色处理
        double dailySum = 0;
        double dailyHours = 0; // 新增：统计每日工时
        double dailyExpenseForBudget = 0; // <--- 1. 新增这行，定义每日支出变量
        long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        for (Transaction t : transactions) {
            if (t.date >= start && t.date < end) {

                // <--- 2. 新增这块：只要是支出(type==0)，就累加到预算统计里 --->
                if (t.type == 0) {
                    dailyExpenseForBudget += t.amount;
                }

                switch (filterMode) {
                    case 0: // 结余
                        if (t.type == 1) {
                            if (!"加班".equals(t.category)) dailySum += t.amount;
                        } else {
                            dailySum -= t.amount;
                        }
                        break;
                    case 1: // 收入
                        if (t.type == 1 && !"加班".equals(t.category)) dailySum += t.amount;
                        break;
                    case 2: // 支出
                        if (t.type == 0) dailySum += t.amount;
                        break;
                    case 3: // 加班工资
                        if ("加班".equals(t.category)) dailySum += t.amount;
                        break;
                    case 4: // 加班工时
                        if ("加班".equals(t.category)) {
                            if (t.note != null) {
                                java.util.regex.Matcher m = java.util.regex.Pattern.compile("时长:\\s*([0-9.]+)\\s*小时").matcher(t.note);
                                if (m.find()) {
                                    try {
                                        dailyHours += Double.parseDouble(m.group(1));
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                            // 赋值 dailySum 让底部判断有数据
                            dailySum += t.amount;
                        }
                        break;
                }
            }
        }

        int defaultNetColor = 0;
        String netText = "";

        // 判断是否有收支数据 (增加对工时的判断)
        if (Math.abs(dailySum) > 0.001 || dailyHours > 0) {
            // === 有收支数据，按原逻辑显示金额 ===
            if (filterMode == 4) {
                netText = String.format("%.1fh", dailyHours); // 显示工时，例如 2.0h
                defaultNetColor = Color.parseColor("#2196F3"); // 给工时换个颜色 (蓝色) 用来区分
            } else {
                netText = String.format("%.2f", dailySum);
                if (filterMode == 2) {
                    defaultNetColor = expenseGreen;
                } else if (filterMode == 3) {
                    defaultNetColor = Color.parseColor("#FF9800"); // 加班工资使用橘色
                } else {
                    defaultNetColor = dailySum > 0 ? incomeRed : expenseGreen;
                }
            }
        } else {
            // === 无收支数据，显示农历或节假日 ===
            try {
                com.nlf.calendar.Solar solar = com.nlf.calendar.Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
                com.nlf.calendar.Lunar lunar = solar.getLunar();

                String festival = "";

                // 依次优先级：农历节日 -> 阳历节日 -> 节气
                if (lunar.getFestivals() != null && !lunar.getFestivals().isEmpty()) {
                    festival = lunar.getFestivals().get(0);
                } else if (solar.getFestivals() != null && !solar.getFestivals().isEmpty()) {
                    festival = solar.getFestivals().get(0);
                } else if (lunar.getJieQi() != null && !lunar.getJieQi().isEmpty()) {
                    festival = lunar.getJieQi();
                }

                // 确定显示的文字
                if (festival != null && !festival.isEmpty()) {
                    netText = festival;
                } else {
                    if (lunar.getDay() == 1) {
                        netText = lunar.getMonthInChinese() + "月";
                    } else {
                        netText = lunar.getDayInChinese();
                    }
                }

                // 【修改这里】：动态获取系统当前模式下的颜色 (日间#666666，夜间#6b6d6d)
                defaultNetColor = context.getColor(R.color.calendar_lunar_text);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 3. 样式应用逻辑核心
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);

        // --- 在判断背景色之前，先保存 View 原本的 Padding ---
        int padLeft = holder.itemView.getPaddingLeft();
        int padTop = holder.itemView.getPaddingTop();
        int padRight = holder.itemView.getPaddingRight();
        int padBottom = holder.itemView.getPaddingBottom();

        // --- 核心优化：检测多项自动续费日期 ---
        boolean isRenewalDay = false;
        for (RenewalItem item : renewalItems) {
            if ("Month".equals(item.period)) {
                if (date.getDayOfMonth() == item.day) {
                    isRenewalDay = true;
                    break;
                }
            } else if ("Year".equals(item.period)) {
                if (date.getMonthValue() == item.month && date.getDayOfMonth() == item.day) {
                    isRenewalDay = true;
                    break;
                }
            }
        }

        // --- 样式优先级：选中 > 今天 > 续费日期 > 普通 ---
        if (isSelected) {
            // [被选中状态]：显示黄色边框
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(themeColor);

            holder.tvDay.setTextColor(defaultDayColor);
            holder.tvDay.setAlpha(1.0f);
            holder.itemView.setSelected(true);

        } else if (isToday) {
            // [今天状态]：黄色实心背景 + 白色文字
            holder.itemView.setBackgroundResource(R.drawable.bg_calendar_today);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(themeColor);

            holder.tvDay.setTextColor(Color.WHITE);
            holder.tvDay.setAlpha(1.0f);
            holder.itemView.setSelected(false);

        } else if (isRenewalDay) {
            // [自动续费状态]：显示红色边框
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(incomeRed); // 设置为红色边框适配“自动续费”标识

            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);

        } else if (isBudgetEnabled && monthlyBudget > 0 && isCurrentMonth && !date.isAfter(LocalDate.now())) {

            // [新增：预算状态] 开启预算且是当月日期，并且“不是未来的日期”才显示预算背景
            int daysInMonth = date.lengthOfMonth();
            double dailyBudget = monthlyBudget / daysInMonth;
            if (dailyExpenseForBudget > dailyBudget) {
                holder.itemView.setBackgroundResource(R.drawable.bg_budget_exceed);
            } else {
                holder.itemView.setBackgroundResource(R.drawable.bg_budget_safe);
            }
            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);

        } else {
            // [普通状态]
            holder.itemView.setBackgroundResource(0);
            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);
        }

        // --- 新增：背景设置完毕后，强行恢复原本的 Padding ---
        holder.itemView.setPadding(padLeft, padTop, padRight, padBottom);
        // ----------------------------------------------------

        // 设置下方金额文字
        holder.tvNet.setText(netText);
        if (!netText.isEmpty()) {
            // 如果是今天且未被选中，金额显示白色以适配黄色背景
            if (isToday && !isSelected) {
                holder.tvNet.setTextColor(Color.WHITE);
            } else {
                holder.tvNet.setTextColor(defaultNetColor);
            }
            holder.tvNet.setAlpha(isCurrentMonth ? 1.0f : 0.3f);
        } else {
            holder.tvNet.setText("");
        }

        holder.itemView.setOnClickListener(v -> {
            // 修改为 KEYBOARD_TAP (模拟键盘敲击的清脆感)
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            listener.onDateClick(date);
        });
    }
    @Override
    public int getItemCount() {
        return days.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvNet;

        ViewHolder(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_day);
            tvNet = v.findViewById(R.id.tv_net_amount);
        }
    }
}