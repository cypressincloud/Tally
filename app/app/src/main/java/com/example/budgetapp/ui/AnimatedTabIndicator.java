package com.example.budgetapp.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.budgetapp.R;

/**
 * 自定义动画指示器 View 组件
 * 用于显示 RadioGroup 选项的选中状态，支持平滑的位置切换动画
 */
public class AnimatedTabIndicator extends View {
    
    // 绘制相关
    private Paint paint;
    private RectF rectF;
    private float cornerRadius;
    
    // 动画相关
    private ObjectAnimator positionAnimator;
    private ObjectAnimator widthAnimator;
    private static final int ANIMATION_DURATION = 300; // 毫秒
    private static final int REDUCED_ANIMATION_DURATION = 100; // 减少动画模式下的时长
    
    // 位置和尺寸
    private float currentX = 0f;
    private float currentWidth = 0f;
    private float targetX = 0f;
    private float targetWidth = 0f;
    
    // RadioGroup 引用
    private android.widget.RadioGroup radioGroup;
    private int[] radioButtonIds = new int[3]; // 存储 3 个 RadioButton 的 ID
    
    // 配置
    private int indicatorColor;
    private float elevation;
    
    public AnimatedTabIndicator(@NonNull Context context) {
        this(context, null);
    }
    
    public AnimatedTabIndicator(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public AnimatedTabIndicator(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        // 初始化画笔
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        
        // 获取主题色
        indicatorColor = ContextCompat.getColor(context, R.color.app_yellow);
        paint.setColor(indicatorColor);
        
        // 初始化矩形
        rectF = new RectF();
        
        // 设置圆角半径 (8dp)
        float density = context.getResources().getDisplayMetrics().density;
        cornerRadius = 8 * density;
        
        // 设置阴影效果 (2dp elevation)
        elevation = 2 * density;
        setElevation(elevation);
        
        // 设置无障碍属性
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        
        // 启用硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }
    
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制圆角矩形
        rectF.set(currentX, 0, currentX + currentWidth, getHeight());
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 使用父 View 提供的尺寸
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        // 布局改变时（如屏幕旋转），重新计算位置
        if (changed && radioGroup != null) {
            // 获取当前选中的位置
            int selectedId = radioGroup.getCheckedRadioButtonId();
            int position = getPositionFromRadioButtonId(selectedId);
            
            if (position >= 0) {
                // 重新计算位置，不执行动画
                calculateTargetPosition(position);
                currentX = targetX;
                currentWidth = targetWidth;
                invalidate();
            }
        }
    }
    
    /**
     * 设置关联的 RadioGroup
     * @param radioGroup RadioGroup 实例
     * @param yearId 年选项的 ID
     * @param monthId 月选项的 ID
     * @param weekId 周选项的 ID
     */
    public void setRadioGroup(android.widget.RadioGroup radioGroup, int yearId, int monthId, int weekId) {
        this.radioGroup = radioGroup;
        this.radioButtonIds[0] = yearId;
        this.radioButtonIds[1] = monthId;
        this.radioButtonIds[2] = weekId;
    }
    
    /**
     * 根据 RadioButton ID 获取位置索引
     * @param radioButtonId RadioButton 的资源 ID
     * @return 位置索引 (0=年, 1=月, 2=周)，未找到返回 -1
     */
    private int getPositionFromRadioButtonId(int radioButtonId) {
        for (int i = 0; i < radioButtonIds.length; i++) {
            if (radioButtonIds[i] == radioButtonId) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 计算目标位置和宽度
     * @param position 目标位置 (0=年, 1=月, 2=周)
     */
    private void calculateTargetPosition(int position) {
        if (radioGroup == null || position < 0 || position >= radioButtonIds.length) {
            return;
        }
        
        // 获取对应的 RadioButton
        android.view.View radioButton = radioGroup.findViewById(radioButtonIds[position]);
        if (radioButton == null) {
            return;
        }
        
        // 计算 RadioButton 相对于 RadioGroup 的位置
        int[] radioGroupLocation = new int[2];
        int[] radioButtonLocation = new int[2];
        
        radioGroup.getLocationInWindow(radioGroupLocation);
        radioButton.getLocationInWindow(radioButtonLocation);
        
        // 计算相对位置（考虑 RadioGroup 的 padding）
        targetX = radioButtonLocation[0] - radioGroupLocation[0];
        targetWidth = radioButton.getWidth();
    }
    
    /**
     * 设置选中位置
     * @param position 选项位置 (0=年, 1=月, 2=周)
     * @param animated 是否执行动画
     */
    public void setSelectedPosition(int position, boolean animated) {
        // 计算目标位置和宽度
        calculateTargetPosition(position);
        
        if (!animated) {
            // 直接定位，不执行动画
            currentX = targetX;
            currentWidth = targetWidth;
            invalidate();
        } else {
            // 执行动画
            animateToPosition(position);
        }
    }
    
    /**
     * 执行动画到目标位置
     * @param position 目标位置
     */
    private void animateToPosition(int position) {
        // 取消正在执行的动画
        if (positionAnimator != null && positionAnimator.isRunning()) {
            positionAnimator.cancel();
        }
        if (widthAnimator != null && widthAnimator.isRunning()) {
            widthAnimator.cancel();
        }
        
        // 检测系统"减少动画"设置
        int duration = ANIMATION_DURATION;
        float animatorDurationScale = android.provider.Settings.Global.getFloat(
            getContext().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        );
        
        if (animatorDurationScale == 0f) {
            // 系统禁用了动画，直接定位
            currentX = targetX;
            currentWidth = targetWidth;
            invalidate();
            return;
        } else if (animatorDurationScale < 0.5f) {
            // 减少动画模式
            duration = REDUCED_ANIMATION_DURATION;
        }
        
        // 创建位置动画
        positionAnimator = ObjectAnimator.ofFloat(this, "animatedX", currentX, targetX);
        positionAnimator.setDuration(duration);
        positionAnimator.setInterpolator(new DecelerateInterpolator());
        
        // 创建宽度动画
        widthAnimator = ObjectAnimator.ofFloat(this, "animatedWidth", currentWidth, targetWidth);
        widthAnimator.setDuration(duration);
        widthAnimator.setInterpolator(new DecelerateInterpolator());
        
        // 启动动画
        positionAnimator.start();
        widthAnimator.start();
    }
    
    /**
     * 用于属性动画的 setter - X 位置
     */
    public void setAnimatedX(float x) {
        currentX = x;
        invalidate();
    }
    
    /**
     * 用于属性动画的 getter - X 位置
     */
    public float getAnimatedX() {
        return currentX;
    }
    
    /**
     * 用于属性动画的 setter - 宽度
     */
    public void setAnimatedWidth(float width) {
        currentWidth = width;
        invalidate();
    }
    
    /**
     * 用于属性动画的 getter - 宽度
     */
    public float getAnimatedWidth() {
        return currentWidth;
    }
    
    /**
     * 清理资源
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (positionAnimator != null) {
            positionAnimator.cancel();
        }
        if (widthAnimator != null) {
            widthAnimator.cancel();
        }
    }
}
