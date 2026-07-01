// src/main/java/com/example/budgetapp/ui/CalendarAdapter.java
package com.example.budgetapp.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
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
        // 🔧 修复 Bug：取消之前的动画，防止动画在复用的 ViewHolder 上继续执行
        Object animatorTag = holder.itemView.getTag(R.id.tv_day); // 使用一个 ID 作为 tag key
        if (animatorTag instanceof ValueAnimator) {
            ((ValueAnimator) animatorTag).cancel();
        }
        
        // 🔧 修复 Bug：强制重置 ViewHolder 的状态，防止复用时状态残留
        holder.itemView.setSelected(false);
        holder.itemView.setBackground(null); // 清除旧背景，防止 Drawable 复用
        
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
        int themeColor = context.getColor(R.color.app_blue);

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

                // 🌟 核心拦截：如果是资产互转，直接跳过，不参与日历下方任何数字的计算
                boolean isTransfer = (t.type == 2) || "资产互转".equals(t.category);
                if (isTransfer) {
                    continue;
                }

                // <--- 2. 新增这块：只要是支出(type==0)，就累加到预算统计里 --->
                if (t.type == 0) {
                    dailyExpenseForBudget += t.amount;
                }

                switch (filterMode) {
                    case 0: // 结余
                        if (t.type == 1) {
                            if (!"加班".equals(t.category)) dailySum += t.amount;
                        } else if (t.type == 0) { // 🌟 严格限制只有真正的支出才减去金额
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
                        if (t.type == 1 && "加班".equals(t.category)) dailySum += t.amount;
                        break;
                    case 4: // 加班工时
                        if (t.type == 1 && "加班".equals(t.category)) {
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

                // ==========================================
                // 新增逻辑：限制农历/节假日最多显示三个字，超出显示"..."
                // ==========================================
                if (netText.length() > 3) {
                    netText = netText.substring(0, 3) + "...";
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
        // --- 在判断背景色之前，先保存 View 原本的 Padding ---
        int padLeft = holder.itemView.getPaddingLeft();
        int padTop = holder.itemView.getPaddingTop();
        int padRight = holder.itemView.getPaddingRight();
        int padBottom = holder.itemView.getPaddingBottom();

        // --- 核心优化：检测多项自动续费日期 (支持自定义) ---
        boolean isRenewalDay = false;
        for (RenewalItem item : renewalItems) {
            // 【关键修改】：调用新增的统一判断方法
            if (isRenewalDate(item, date)) {
                isRenewalDay = true;
                break;
            }
        }

        // 检查是否是自定义背景模式
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;

        if (isSelected) {
            // [选中状态]：应用动画
            boolean wasToday = isToday;
            boolean wasBudget = isBudgetEnabled && monthlyBudget > 0 && isCurrentMonth && !date.isAfter(LocalDate.now());
            double dailyBudget = dailyBudget(date);
            applySelectedDateAnimation(holder, themeColor, defaultDayColor, wasToday, wasBudget, dailyExpenseForBudget, dailyBudget, isCurrentMonth);
            holder.tvNet.setTextColor(defaultNetColor);
        } else if (isToday) {
            // [今天状态]：蓝色圆角背景
            holder.itemView.setBackgroundResource(R.drawable.bg_calendar_today);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) {
                bg.setTint(themeColor);
                // 如果今天不属于当月（如在下个月查看时），背景透明度应与上月日期一致
                int bgAlpha = isCustomBg ? (isCurrentMonth ? 230 : 153) : 255;
                bg.setAlpha(bgAlpha);
            }

            holder.tvDay.setTextColor(Color.WHITE);
            holder.tvDay.setAlpha(isCurrentMonth ? 1.0f : 0.3f);
            holder.tvNet.setAlpha(isCurrentMonth ? 1.0f : 0.3f);
            holder.itemView.setSelected(false);

        } else if (isRenewalDay) {
            // [自动续费状态]：显示红色边框
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(incomeRed);

            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);

        } else if (isBudgetEnabled && monthlyBudget > 0 && isCurrentMonth && !date.isAfter(LocalDate.now())) {
            // [预算状态]
            int daysInMonth = date.lengthOfMonth();
            double dailyBudget = monthlyBudget / daysInMonth;
            if (dailyExpenseForBudget > dailyBudget) {
                holder.itemView.setBackgroundResource(R.drawable.bg_budget_exceed);
            } else {
                holder.itemView.setBackgroundResource(R.drawable.bg_budget_safe);
            }

            Drawable bg = holder.itemView.getBackground();
            if (bg != null) {
                // 如果是自定义背景，设置为 217 (85% 不透明度)，否则恢复 255 (不透明)
                bg.mutate().setAlpha(isCustomBg ? 230 : 255);
            }

            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);

        } else {
            // ====== 修改：[普通状态] ======
            if (isCustomBg) {
                // 1. 创建圆角矩形 Shape
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);

                // 【新增判断】：如果是当月日期，用 90% 不透明度 (230)；如果是非当月(上个月/下个月)，用 60% 不透明度 (153)
                int alpha = isCurrentMonth ? 230 : 153;

                // 设置颜色：淡灰底色注入动态透明度
                int surfaceColor = androidx.core.content.ContextCompat.getColor(context, R.color.white);
                int translucentGray = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, alpha);
                shape.setColor(translucentGray);

                // 设置圆角：12dp 转换为 px
                float radius = android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
                shape.setCornerRadius(radius);

                // 2. 创建 Inset 缩进
                // 设置间隙：4dp 转换为 px
                int inset = (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());

                // 将 Shape 放入 InsetDrawable 中，四周缩进 4dp
                android.graphics.drawable.InsetDrawable insetDrawable =
                        new android.graphics.drawable.InsetDrawable(shape, inset, inset, inset, inset);

                // 应用背景
                holder.itemView.setBackground(insetDrawable);
            } else {
                // 系统纯色背景：恢复完全透明，不遮挡系统的底色
                holder.itemView.setBackgroundResource(0);
            }

            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);
            // ===============================
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
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            listener.onDateClick(date);
        });
    }

    // 新增：用于判断目标日期是否为续费日（包含自定义周期的计算）
    private boolean isRenewalDate(RenewalItem item, LocalDate targetDate) {
        if ("Month".equals(item.period)) {
            return targetDate.getDayOfMonth() == item.day;
        } else if ("Year".equals(item.period)) {
            return targetDate.getMonthValue() == item.month && targetDate.getDayOfMonth() == item.day;
        } else if ("Custom".equals(item.period)) {
            // 安全检查，兼容旧数据
            int startYear = item.year > 2000 ? item.year : targetDate.getYear();
            LocalDate startDate;
            try {
                startDate = LocalDate.of(startYear, item.month, item.day);
            } catch (Exception e) {
                return false;
            }

            // 如果当前查看的日期在起算日期之前，则不触发
            if (targetDate.isBefore(startDate)) {
                return false;
            }

            int value = item.durationValue > 0 ? item.durationValue : 1;

            if ("Day".equals(item.durationUnit)) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, targetDate);
                return days % value == 0;
            } else if ("Week".equals(item.durationUnit)) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, targetDate);
                return days % (7L * value) == 0;
            } else if ("Month".equals(item.durationUnit)) {
                // 计算相差的自然月数
                int diffMonths = (targetDate.getYear() - startDate.getYear()) * 12 + (targetDate.getMonthValue() - startDate.getMonthValue());
                if (diffMonths >= 0 && diffMonths % value == 0) {
                    return startDate.plusMonths(diffMonths).equals(targetDate);
                }
                return false;
            } else if ("Year".equals(item.durationUnit)) {
                int diffYears = targetDate.getYear() - startDate.getYear();
                if (diffYears >= 0 && diffYears % value == 0) {
                    return startDate.plusYears(diffYears).equals(targetDate);
                }
                return false;
            }
        }
        return false;
    }

    /**
     * 计算每日预算
     */
    private double dailyBudget(LocalDate date) {
        if (!isBudgetEnabled || monthlyBudget <= 0) return 0;
        int daysInMonth = date.lengthOfMonth();
        return monthlyBudget / daysInMonth;
    }

    /**
     * 为选中的日期应用由浅入深的快速过渡动画（边框缩放+颜色渐变），并处理背景色块的淡出效果
     * @param holder ViewHolder
     * @param targetColor 目标颜色（主题色）
     * @param textColor 文字颜色
     * @param wasToday 是否是今天（需要淡出蓝色背景）
     * @param wasBudget 是否有预算色块（需要淡出预算背景）
     * @param dailyExpense 当日支出
     * @param dailyBudget 每日预算
     * @param isCurrentMonth 是否是当前月份
     */
    private void applySelectedDateAnimation(ViewHolder holder, int targetColor, int textColor, 
                                           boolean wasToday, boolean wasBudget, 
                                           double dailyExpense, double dailyBudget,
                                           boolean isCurrentMonth) {
        Context context = holder.itemView.getContext();
        
        // === 保存原始 Padding，防止动画过程中被改变 ===
        final int savedPadLeft = holder.itemView.getPaddingLeft();
        final int savedPadTop = holder.itemView.getPaddingTop();
        final int savedPadRight = holder.itemView.getPaddingRight();
        final int savedPadBottom = holder.itemView.getPaddingBottom();
        
        // 创建圆角矩形边框 Shape
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        
        // 设置圆角
        float radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
        shape.setCornerRadius(radius);
        
        // 设置边框宽度
        float strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1.5f, context.getResources().getDisplayMetrics());
        
        // 创建 Inset 缩进（初始值较大，用于缩放效果）
        int baseInset = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
        
        // === 核心优化：确定初始背景色（用于淡出动画）===
        final int startBgColor;
        final int startBgAlpha; // 记录原始透明度
        
        // 检查是否是自定义背景模式
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCustomBg = prefs.getInt("theme_mode", -1) == 3;
        
        if (wasToday) {
            // 今天的蓝色背景
            startBgColor = targetColor;
            // 自定义背景模式下透明度为 230，普通模式为 255
            startBgAlpha = isCustomBg ? 230 : 255;
        } else if (wasBudget) {
            // 预算色块背景（使用预算专用的浅色）
            int budgetSafeBg = context.getColor(R.color.budget_safe_bg);
            int budgetExceedBg = context.getColor(R.color.budget_exceed_bg);
            startBgColor = dailyExpense > dailyBudget ? budgetExceedBg : budgetSafeBg;
            // 预算色块的透明度：自定义背景 230，普通模式 255
            startBgAlpha = isCustomBg ? 230 : 255;
        } else {
            // 正常日期（非今天、非预算色块）
            // 在自定义背景模式下，正常日期也有半透明白色背景，需要淡出动画
            if (isCustomBg) {
                int surfaceColor = context.getColor(R.color.white);
                startBgColor = surfaceColor;
                // 根据是否是当月日期设置透明度
                startBgAlpha = isCurrentMonth ? 230 : 153;
            } else {
                startBgColor = Color.TRANSPARENT;
                startBgAlpha = 0;
            }
        }
        
        // 边框颜色动画：从浅色（15%透明度）到深色（100%不透明）
        final int startStrokeColor = androidx.core.graphics.ColorUtils.setAlphaComponent(targetColor, 38); // 15% 透明度
        final int endStrokeColor = targetColor; // 100% 不透明
        
        // 创建组合动画：背景淡出 + 边框颜色渐变 + 边框缩放
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(100); // 100ms 动画时长
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            
            // 1. 背景色淡出：保持原来的颜色，透明度从原始值逐渐降到 0（由深入浅）
            // 透明度从原始值（230 或 255）逐渐降到 0
            int currentAlpha = (int) (startBgAlpha * (1f - progress)); // progress: 0->1, alpha: startBgAlpha->0
            int animatedBgColor = androidx.core.graphics.ColorUtils.setAlphaComponent(startBgColor, currentAlpha);
            shape.setColor(animatedBgColor);
            
            // 2. 边框颜色插值：从浅色到深色
            int animatedStrokeColor = (int) new ArgbEvaluator().evaluate(progress, startStrokeColor, endStrokeColor);
            shape.setStroke((int) strokeWidth, animatedStrokeColor);
            
            // 3. 边框缩放效果：从 0.80 到 1.0（通过调整 inset 实现）
            float scale = 0.80f + (0.20f * progress);
            int extraInset = (int) (baseInset * (1f - scale) * 2.5f);
            int currentInset = baseInset + extraInset;
            
            InsetDrawable insetDrawable = new InsetDrawable(shape, currentInset, currentInset, currentInset, currentInset);
            holder.itemView.setBackground(insetDrawable);
            // === 每一帧都恢复 padding，防止背景改变时 padding 丢失 ===
            holder.itemView.setPadding(savedPadLeft, savedPadTop, savedPadRight, savedPadBottom);
            // 移除 invalidate()，setBackground 已经会触发重绘，避免和 RecyclerView 的布局动画冲突
        });
        
        // 文字颜色过渡动画（如果是今天，文字从白色过渡到默认颜色）
        if (wasToday) {
            ValueAnimator textAnimator = ValueAnimator.ofFloat(0f, 1f);
            textAnimator.setDuration(100); // 与边框动画同步
            textAnimator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                int animatedTextColor = (int) new ArgbEvaluator().evaluate(progress, Color.WHITE, textColor);
                holder.tvDay.setTextColor(animatedTextColor);
                
                // 同时处理下方文字（tvNet）的颜色过渡
                if (!holder.tvNet.getText().toString().isEmpty()) {
                    holder.tvNet.setTextColor(animatedTextColor);
                }
            });
            textAnimator.start();
        } else {
            holder.tvDay.setTextColor(textColor);
        }
        
        // 下方文字（tvNet）透明度过渡动画（如果是非当月日期被选中，透明度从 0.3 恢复到 1.0）
        if (!isCurrentMonth) {
            ValueAnimator alphaAnimator = ValueAnimator.ofFloat(0.3f, 1.0f);
            alphaAnimator.setDuration(100); // 与边框动画同步
            alphaAnimator.addUpdateListener(animation -> {
                float alpha = (float) animation.getAnimatedValue();
                holder.tvDay.setAlpha(alpha); // 同时设置日期数字的透明度
                holder.tvNet.setAlpha(alpha);
            });
            alphaAnimator.start();
        }
        
        // 启动动画并存储到 tag 中，以便在 ViewHolder 复用时取消
        holder.itemView.setTag(R.id.tv_day, animator);
        animator.start();
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