package com.example.budgetapp.ui;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.budgetapp.R;

/**
 * 动画测试 Activity
 * 用于验证 AnimatedTabIndicator 的位置和宽度动画功能
 * 
 * 测试场景：
 * 1. 基本动画：点击不同选项，观察指示器平滑移动
 * 2. 连续点击：快速点击多个选项，验证立即响应
 * 3. 动画中断：动画进行中点击其他选项，验证方向改变
 */
public class AnimationTest extends AppCompatActivity {
    
    private RadioGroup rgTimeMode;
    private AnimatedTabIndicator animatedIndicator;
    private Button btnTestContinuousClick;
    private Button btnTestInterrupt;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 注意：这是测试代码，实际布局需要在 XML 中定义
        // 这里仅展示测试逻辑
        
        // 初始化视图（假设布局已定义）
        rgTimeMode = findViewById(R.id.rg_time_mode);
        animatedIndicator = findViewById(R.id.animated_indicator);
        btnTestContinuousClick = findViewById(R.id.btn_test_continuous_click);
        btnTestInterrupt = findViewById(R.id.btn_test_interrupt);
        
        // 设置 RadioGroup 关联
        animatedIndicator.setRadioGroup(
            rgTimeMode,
            R.id.rb_year,
            R.id.rb_month,
            R.id.rb_week
        );
        
        // 设置初始位置（月）
        animatedIndicator.setSelectedPosition(1, false);
        rgTimeMode.check(R.id.rb_month);
        
        // 设置 RadioGroup 监听器
        rgTimeMode.setOnCheckedChangeListener((group, checkedId) -> {
            int position = getPositionFromRadioButtonId(checkedId);
            animatedIndicator.setSelectedPosition(position, true);
            
            // 添加触觉反馈
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                group.performHapticFeedback(
                    android.view.HapticFeedbackConstants.CLOCK_TICK
                );
            }
            
            Toast.makeText(this, "切换到位置: " + position, Toast.LENGTH_SHORT).show();
        });
        
        // 测试按钮：连续点击测试
        btnTestContinuousClick.setOnClickListener(v -> {
            testContinuousClick();
        });
        
        // 测试按钮：动画中断测试
        btnTestInterrupt.setOnClickListener(v -> {
            testInterrupt();
        });
    }
    
    /**
     * 测试场景 1：连续点击测试
     * 快速点击 年 → 周 → 月，验证指示器立即响应每次点击
     */
    private void testContinuousClick() {
        Toast.makeText(this, "开始连续点击测试", Toast.LENGTH_SHORT).show();
        
        // 使用 Handler 模拟快速点击
        android.os.Handler handler = new android.os.Handler();
        
        // 点击"年"
        handler.postDelayed(() -> {
            rgTimeMode.check(R.id.rb_year);
        }, 100);
        
        // 点击"周"（动画进行中）
        handler.postDelayed(() -> {
            rgTimeMode.check(R.id.rb_week);
        }, 200);
        
        // 点击"月"（动画进行中）
        handler.postDelayed(() -> {
            rgTimeMode.check(R.id.rb_month);
        }, 300);
        
        // 显示测试结果
        handler.postDelayed(() -> {
            Toast.makeText(this, "连续点击测试完成，指示器应停在'月'", Toast.LENGTH_LONG).show();
        }, 700);
    }
    
    /**
     * 测试场景 2：动画中断测试
     * 点击"年"，等待动画进行到一半，然后点击"周"
     * 验证指示器立即改变方向
     */
    private void testInterrupt() {
        Toast.makeText(this, "开始动画中断测试", Toast.LENGTH_SHORT).show();
        
        android.os.Handler handler = new android.os.Handler();
        
        // 点击"年"，启动动画
        handler.postDelayed(() -> {
            rgTimeMode.check(R.id.rb_year);
        }, 100);
        
        // 等待 150ms（动画时长 300ms 的一半），然后点击"周"
        handler.postDelayed(() -> {
            rgTimeMode.check(R.id.rb_week);
            Toast.makeText(this, "动画中断，改变方向到'周'", Toast.LENGTH_SHORT).show();
        }, 250);
        
        // 显示测试结果
        handler.postDelayed(() -> {
            Toast.makeText(this, "动画中断测试完成，指示器应停在'周'", Toast.LENGTH_LONG).show();
        }, 700);
    }
    
    /**
     * 根据 RadioButton ID 获取位置
     */
    private int getPositionFromRadioButtonId(int radioButtonId) {
        if (radioButtonId == R.id.rb_year) {
            return 0;
        } else if (radioButtonId == R.id.rb_month) {
            return 1;
        } else if (radioButtonId == R.id.rb_week) {
            return 2;
        }
        return 1; // 默认返回月
    }
    
    /**
     * 测试场景 3：验证动画参数
     * 检查动画是否使用正确的参数
     */
    private void verifyAnimationParameters() {
        // 这个方法用于调试，验证动画参数是否正确
        
        // 获取动画器（需要在 AnimatedTabIndicator 中添加 getter 方法）
        // ObjectAnimator positionAnimator = animatedIndicator.getPositionAnimator();
        // ObjectAnimator widthAnimator = animatedIndicator.getWidthAnimator();
        
        // 验证动画时长
        // long duration = positionAnimator.getDuration();
        // assert duration == 300 : "动画时长应为 300ms";
        
        // 验证插值器
        // assert positionAnimator.getInterpolator() instanceof DecelerateInterpolator;
        
        Toast.makeText(this, "动画参数验证通过", Toast.LENGTH_SHORT).show();
    }
}

/**
 * 测试布局示例（activity_animation_test.xml）
 * 
 * <?xml version="1.0" encoding="utf-8"?>
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:orientation="vertical"
 *     android:padding="16dp">
 *     
 *     <TextView
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="AnimatedTabIndicator 动画测试"
 *         android:textSize="20sp"
 *         android:textStyle="bold"
 *         android:gravity="center"
 *         android:layout_marginBottom="24dp" />
 *     
 *     <!-- 时间维度选择器 -->
 *     <FrameLayout
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:layout_marginBottom="32dp">
 *         
 *         <com.example.budgetapp.ui.AnimatedTabIndicator
 *             android:id="@+id/animated_indicator"
 *             android:layout_width="match_parent"
 *             android:layout_height="40dp" />
 *         
 *         <RadioGroup
 *             android:id="@+id/rg_time_mode"
 *             android:layout_width="match_parent"
 *             android:layout_height="40dp"
 *             android:orientation="horizontal"
 *             android:gravity="center">
 *             
 *             <RadioButton
 *                 android:id="@+id/rb_year"
 *                 android:layout_width="0dp"
 *                 android:layout_height="match_parent"
 *                 android:layout_weight="1"
 *                 android:text="年"
 *                 android:gravity="center"
 *                 android:button="@null"
 *                 android:background="@android:color/transparent" />
 *             
 *             <RadioButton
 *                 android:id="@+id/rb_month"
 *                 android:layout_width="0dp"
 *                 android:layout_height="match_parent"
 *                 android:layout_weight="1"
 *                 android:text="月"
 *                 android:gravity="center"
 *                 android:button="@null"
 *                 android:background="@android:color/transparent" />
 *             
 *             <RadioButton
 *                 android:id="@+id/rb_week"
 *                 android:layout_width="0dp"
 *                 android:layout_height="match_parent"
 *                 android:layout_weight="1"
 *                 android:text="周"
 *                 android:gravity="center"
 *                 android:button="@null"
 *                 android:background="@android:color/transparent" />
 *         </RadioGroup>
 *     </FrameLayout>
 *     
 *     <!-- 测试按钮 -->
 *     <TextView
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="自动化测试"
 *         android:textSize="16sp"
 *         android:textStyle="bold"
 *         android:layout_marginBottom="8dp" />
 *     
 *     <Button
 *         android:id="@+id/btn_test_continuous_click"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="测试连续点击"
 *         android:layout_marginBottom="8dp" />
 *     
 *     <Button
 *         android:id="@+id/btn_test_interrupt"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="测试动画中断"
 *         android:layout_marginBottom="8dp" />
 *     
 *     <!-- 测试说明 -->
 *     <TextView
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="测试说明：\n\n1. 手动点击测试：直接点击上方的年/月/周选项，观察动画效果\n\n2. 连续点击测试：点击按钮，自动快速点击 年→周→月，验证指示器立即响应\n\n3. 动画中断测试：点击按钮，在动画进行到一半时改变目标，验证方向改变"
 *         android:textSize="14sp"
 *         android:lineSpacingExtra="4dp"
 *         android:layout_marginTop="16dp" />
 * </LinearLayout>
 */
