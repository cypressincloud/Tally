// 集成示例：在 DetailsFragment 中使用 AnimatedTabIndicator.setSelectedPosition()
// 
// 此文件展示如何在实际的 Fragment 中集成初始位置设置方法
// 这是任务 5.2 和 6.2 的参考实现

package com.example.budgetapp.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.budgetapp.R;

public class DetailsFragmentExample extends Fragment {
    
    private RadioGroup rgTimeMode;
    private AnimatedTabIndicator animatedIndicator;
    private SharedPreferences sharedPreferences;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                             @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_details, container, false);
        
        // 初始化 SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        
        // 获取 RadioGroup 和 AnimatedTabIndicator 引用
        rgTimeMode = view.findViewById(R.id.rg_time_mode);
        animatedIndicator = view.findViewById(R.id.animated_indicator);
        
        // 设置 RadioGroup 关联
        animatedIndicator.setRadioGroup(
            rgTimeMode,
            R.id.rb_year,   // 年选项 ID
            R.id.rb_month,  // 月选项 ID
            R.id.rb_week    // 周选项 ID
        );
        
        // 从 SharedPreferences 读取保存的时间维度偏好
        // 默认值为 1（月）
        int savedTimeMode = sharedPreferences.getInt("time_mode", 1);
        
        // 设置 RadioGroup 的选中状态
        int radioButtonId = getRadioButtonIdFromPosition(savedTimeMode);
        rgTimeMode.check(radioButtonId);
        
        // 【关键】设置初始位置，不执行动画（animated=false）
        // 这满足需求 3.1 和 3.4：初始显示时立即显示指示器，不执行动画
        animatedIndicator.setSelectedPosition(savedTimeMode, false);
        
        // 设置 RadioGroup 选中状态变化监听器
        rgTimeMode.setOnCheckedChangeListener((group, checkedId) -> {
            // 获取新选中的位置
            int newPosition = getPositionFromRadioButtonId(checkedId);
            
            // 保存到 SharedPreferences
            sharedPreferences.edit()
                .putInt("time_mode", newPosition)
                .apply();
            
            // 【关键】执行动画切换到新位置（animated=true）
            // 这满足需求 2.1：用户点击时平滑移动到新位置
            animatedIndicator.setSelectedPosition(newPosition, true);
            
            // 添加触觉反馈（需求 5.1）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                group.performHapticFeedback(
                    android.view.HapticFeedbackConstants.CLOCK_TICK
                );
            }
            
            // 刷新数据显示
            refreshData(newPosition);
        });
        
        return view;
    }
    
    /**
     * 根据位置获取 RadioButton ID
     * @param position 位置 (0=年, 1=月, 2=周)
     * @return RadioButton 的资源 ID
     */
    private int getRadioButtonIdFromPosition(int position) {
        switch (position) {
            case 0:
                return R.id.rb_year;
            case 1:
                return R.id.rb_month;
            case 2:
                return R.id.rb_week;
            default:
                return R.id.rb_month; // 默认返回月
        }
    }
    
    /**
     * 根据 RadioButton ID 获取位置
     * @param radioButtonId RadioButton 的资源 ID
     * @return 位置 (0=年, 1=月, 2=周)
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
     * 刷新数据显示
     * @param timeMode 时间维度 (0=年, 1=月, 2=周)
     */
    private void refreshData(int timeMode) {
        // 根据新的时间维度刷新数据
        // 这里是现有的数据刷新逻辑
        // processAndDisplayData();
    }
}

// ============================================================
// 布局文件示例：fragment_details.xml
// ============================================================
/*
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    
    <!-- 时间维度选择器容器 -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">
        
        <!-- 动画指示器（放在 RadioGroup 下方） -->
        <com.example.budgetapp.ui.AnimatedTabIndicator
            android:id="@+id/animated_indicator"
            android:layout_width="match_parent"
            android:layout_height="40dp" />
        
        <!-- RadioGroup（放在指示器上方） -->
        <RadioGroup
            android:id="@+id/rg_time_mode"
            android:layout_width="match_parent"
            android:layout_height="40dp"
            android:orientation="horizontal"
            android:gravity="center">
            
            <RadioButton
                android:id="@+id/rb_year"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text="年"
                android:gravity="center"
                android:button="@null"
                android:background="@android:color/transparent" />
            
            <RadioButton
                android:id="@+id/rb_month"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text="月"
                android:gravity="center"
                android:button="@null"
                android:background="@android:color/transparent" />
            
            <RadioButton
                android:id="@+id/rb_week"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text="周"
                android:gravity="center"
                android:button="@null"
                android:background="@android:color/transparent" />
        </RadioGroup>
    </FrameLayout>
    
    <!-- 其他内容 -->
    <!-- ... -->
    
</LinearLayout>
*/

// ============================================================
// 关键要点总结
// ============================================================
/*
1. 初始化顺序：
   - 先调用 setRadioGroup() 关联 RadioGroup
   - 再调用 setSelectedPosition(position, false) 设置初始位置
   
2. animated 参数使用：
   - 初始显示：animated=false（直接定位，无动画）
   - 用户切换：animated=true（平滑动画）
   
3. position 参数映射：
   - 0 = 年（rb_year）
   - 1 = 月（rb_month）
   - 2 = 周（rb_week）
   
4. 与现有逻辑集成：
   - 保持现有的 SharedPreferences 读写逻辑
   - 保持现有的数据刷新逻辑（processAndDisplayData）
   - 添加触觉反馈（HapticFeedback）
   
5. 布局层级：
   - AnimatedTabIndicator 在下层（z-index 低）
   - RadioGroup 在上层（z-index 高）
   - 使用 FrameLayout 叠加布局
*/
