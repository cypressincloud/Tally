package com.example.budgetapp.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 自定义 RadioGroup 包装组件
 * 继承 FrameLayout，内部包含 RadioGroup 和 AnimatedTabIndicator
 * 自动管理动画指示器的位置更新和触觉反馈
 * 
 * 满足需求 7.1, 7.2：封装动画逻辑，简化集成
 */
public class AnimatedRadioGroup extends FrameLayout {
    
    private RadioGroup radioGroup;
    private AnimatedTabIndicator animatedIndicator;
    private OnCheckedChangeListener externalListener;
    private boolean isInitialized = false;
    private boolean isProgrammaticChange = false; // 标记是否为程序化改变（非用户点击）
    
    /**
     * 选中状态变化监听器接口
     */
    public interface OnCheckedChangeListener {
        /**
         * 当选中的 RadioButton 改变时调用
         * @param group AnimatedRadioGroup 实例
         * @param checkedId 新选中的 RadioButton ID
         * @param position 新选中的位置 (0=第一个, 1=第二个, 2=第三个)
         */
        void onCheckedChanged(AnimatedRadioGroup group, int checkedId, int position);
    }
    
    public AnimatedRadioGroup(@NonNull Context context) {
        super(context);
    }
    
    public AnimatedRadioGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    
    public AnimatedRadioGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        
        // 在布局填充完成后，查找子 View
        findChildViews();
        
        // 如果找到了必需的子 View，则初始化
        if (radioGroup != null && animatedIndicator != null) {
            initializeComponents();
        }
    }
    
    /**
     * 查找子 View（RadioGroup 和 AnimatedTabIndicator）
     */
    private void findChildViews() {
        int childCount = getChildCount();
        
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            
            if (child instanceof AnimatedTabIndicator) {
                animatedIndicator = (AnimatedTabIndicator) child;
            } else if (child instanceof RadioGroup) {
                radioGroup = (RadioGroup) child;
            }
        }
    }
    
    /**
     * 初始化组件，设置监听器和关联
     */
    private void initializeComponents() {
        if (isInitialized) {
            return;
        }
        
        try {
            // 获取 RadioGroup 中的 RadioButton ID
            int[] radioButtonIds = extractRadioButtonIds();
            
            if (radioButtonIds.length >= 3) {
                // 关联 RadioGroup 和 AnimatedTabIndicator
                animatedIndicator.setRadioGroup(
                    radioGroup,
                    radioButtonIds[0],  // 第一个选项（年）
                    radioButtonIds[1],  // 第二个选项（月）
                    radioButtonIds[2]   // 第三个选项（周）
                );
                
                // 设置 RadioGroup 选中状态变化监听器
                radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        handleCheckedChanged(group, checkedId);
                    }
                });
                
                isInitialized = true;
            }
        } catch (Exception e) {
            // 异常处理：降级为无动画的标准 RadioGroup 行为（需求 8.4）
            android.util.Log.e("AnimatedRadioGroup", "初始化失败，降级为标准 RadioGroup", e);
            
            // 隐藏动画指示器
            if (animatedIndicator != null) {
                animatedIndicator.setVisibility(GONE);
            }
        }
    }
    
    /**
     * 从 RadioGroup 中提取 RadioButton 的 ID
     * @return RadioButton ID 数组
     */
    private int[] extractRadioButtonIds() {
        int childCount = radioGroup.getChildCount();
        int[] ids = new int[childCount];
        int index = 0;
        
        for (int i = 0; i < childCount; i++) {
            View child = radioGroup.getChildAt(i);
            if (child instanceof RadioButton) {
                ids[index++] = child.getId();
            }
        }
        
        // 返回实际找到的 RadioButton ID 数组
        int[] result = new int[index];
        System.arraycopy(ids, 0, result, 0, index);
        return result;
    }
    
    /**
     * 处理 RadioGroup 选中状态变化
     * @param group RadioGroup 实例
     * @param checkedId 新选中的 RadioButton ID
     */
    private void handleCheckedChanged(RadioGroup group, int checkedId) {
        try {
            // 获取选中的位置
            int position = getPositionFromRadioButtonId(checkedId);
            
            if (position >= 0) {
                // 触发动画指示器位置更新（需求 7.2）
                // 强制始终显示动画（除非是通过 setCheckedPosition 明确指定 animated=false）
                boolean animated = isInitialized;
                
                // 如果是程序化改变，使用传入的 animated 参数
                if (isProgrammaticChange) {
                    // 在 setCheckedPosition 中已经调用了 animatedIndicator.setSelectedPosition
                    // 这里不需要再次调用
                } else {
                    // 用户点击，始终显示动画
                    animatedIndicator.setSelectedPosition(position, animated);
                }
                
                // 添加触觉反馈（需求 5.1, 5.2）
                // 只在用户点击时触发触觉反馈，程序化改变时不触发
                if (!isProgrammaticChange && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    group.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
            
            // 通知外部监听器
            if (externalListener != null) {
                externalListener.onCheckedChanged(this, checkedId, position);
            }
        } catch (Exception e) {
            // 异常处理：静默记录，不影响用户使用（需求 8.4）
            android.util.Log.e("AnimatedRadioGroup", "处理选中状态变化失败", e);
        }
    }
    
    /**
     * 根据 RadioButton ID 获取位置索引
     * @param radioButtonId RadioButton 的资源 ID
     * @return 位置索引 (0=第一个, 1=第二个, 2=第三个)，未找到返回 -1
     */
    private int getPositionFromRadioButtonId(int radioButtonId) {
        if (radioGroup == null) {
            return -1;
        }
        
        int childCount = radioGroup.getChildCount();
        int radioButtonIndex = 0;
        
        for (int i = 0; i < childCount; i++) {
            View child = radioGroup.getChildAt(i);
            if (child instanceof RadioButton) {
                if (child.getId() == radioButtonId) {
                    return radioButtonIndex;
                }
                radioButtonIndex++;
            }
        }
        
        return -1;
    }
    
    /**
     * 设置选中状态变化监听器
     * @param listener 监听器实例
     */
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.externalListener = listener;
    }
    
    /**
     * 设置选中的位置
     * @param position 位置索引 (0=第一个, 1=第二个, 2=第三个)
     * @param animated 是否执行动画
     */
    public void setCheckedPosition(int position, boolean animated) {
        if (radioGroup == null) {
            return;
        }
        
        try {
            // 获取对应位置的 RadioButton ID
            int radioButtonId = getRadioButtonIdFromPosition(position);
            
            if (radioButtonId != -1) {
                // 标记为程序化改变
                isProgrammaticChange = true;
                
                // 设置 RadioGroup 的选中状态
                radioGroup.check(radioButtonId);
                
                // 更新动画指示器位置
                if (animatedIndicator != null) {
                    animatedIndicator.setSelectedPosition(position, animated);
                }
                
                // 重置标记
                isProgrammaticChange = false;
            }
        } catch (Exception e) {
            // 异常处理：静默记录（需求 8.4）
            android.util.Log.e("AnimatedRadioGroup", "设置选中位置失败", e);
            // 确保重置标记
            isProgrammaticChange = false;
        }
    }
    
    /**
     * 根据位置索引获取 RadioButton ID
     * @param position 位置索引 (0=第一个, 1=第二个, 2=第三个)
     * @return RadioButton 的资源 ID，未找到返回 -1
     */
    private int getRadioButtonIdFromPosition(int position) {
        if (radioGroup == null) {
            return -1;
        }
        
        int childCount = radioGroup.getChildCount();
        int radioButtonIndex = 0;
        
        for (int i = 0; i < childCount; i++) {
            View child = radioGroup.getChildAt(i);
            if (child instanceof RadioButton) {
                if (radioButtonIndex == position) {
                    return child.getId();
                }
                radioButtonIndex++;
            }
        }
        
        return -1;
    }
    
    /**
     * 获取当前选中的 RadioButton ID
     * @return 选中的 RadioButton ID
     */
    public int getCheckedRadioButtonId() {
        if (radioGroup != null) {
            return radioGroup.getCheckedRadioButtonId();
        }
        return -1;
    }
    
    /**
     * 获取当前选中的位置
     * @return 位置索引 (0=第一个, 1=第二个, 2=第三个)，未选中返回 -1
     */
    public int getCheckedPosition() {
        if (radioGroup != null) {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            return getPositionFromRadioButtonId(checkedId);
        }
        return -1;
    }
    
    /**
     * 获取内部的 RadioGroup 实例
     * @return RadioGroup 实例
     */
    public RadioGroup getRadioGroup() {
        return radioGroup;
    }
    
    /**
     * 获取内部的 AnimatedTabIndicator 实例
     * @return AnimatedTabIndicator 实例
     */
    public AnimatedTabIndicator getAnimatedIndicator() {
        return animatedIndicator;
    }
}
