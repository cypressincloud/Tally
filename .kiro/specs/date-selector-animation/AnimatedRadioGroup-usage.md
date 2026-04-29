# AnimatedRadioGroup 使用指南

## 概述

`AnimatedRadioGroup` 是一个自定义 ViewGroup 组件，封装了 `RadioGroup` 和 `AnimatedTabIndicator`，提供开箱即用的动画选择器功能。

## 核心特性

- ✅ 自动管理动画指示器位置更新
- ✅ 自动添加触觉反馈（CLOCK_TICK）
- ✅ 异常降级处理（降级为标准 RadioGroup）
- ✅ 简化集成，减少样板代码
- ✅ 满足需求 7.1, 7.2

## XML 布局使用

### 基本结构

```xml
<com.example.budgetapp.ui.AnimatedRadioGroup
    android:id="@+id/animated_radio_group"
    android:layout_width="match_parent"
    android:layout_height="40dp"
    android:padding="16dp">
    
    <!-- 动画指示器（必须放在第一层） -->
    <com.example.budgetapp.ui.AnimatedTabIndicator
        android:id="@+id/animated_indicator"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
    
    <!-- RadioGroup（必须放在第二层，覆盖在指示器上方） -->
    <RadioGroup
        android:id="@+id/rg_time_mode"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
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
</com.example.budgetapp.ui.AnimatedRadioGroup>
```

### 关键要点

1. **子 View 顺序**：AnimatedTabIndicator 必须在 RadioGroup 之前（z-index 层级）
2. **尺寸设置**：建议高度为 40dp，宽度为 match_parent
3. **RadioButton 配置**：
   - `android:button="@null"` - 隐藏默认的圆形按钮
   - `android:background="@android:color/transparent"` - 透明背景
   - `android:layout_weight="1"` - 均分宽度

## Java 代码使用

### 基本初始化

```java
public class DetailsFragment extends Fragment {
    
    private AnimatedRadioGroup animatedRadioGroup;
    private SharedPreferences sharedPreferences;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                             @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_details, container, false);
        
        // 1. 获取组件引用
        animatedRadioGroup = view.findViewById(R.id.animated_radio_group);
        
        // 2. 初始化 SharedPreferences
        sharedPreferences = requireContext()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        
        // 3. 读取保存的时间维度偏好（默认为 1=月）
        int savedTimeMode = sharedPreferences.getInt("time_mode", 1);
        
        // 4. 设置初始位置（不执行动画）
        animatedRadioGroup.setCheckedPosition(savedTimeMode, false);
        
        // 5. 设置选中状态变化监听器
        animatedRadioGroup.setOnCheckedChangeListener(
            new AnimatedRadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(AnimatedRadioGroup group, 
                                            int checkedId, 
                                            int position) {
                    // 保存到 SharedPreferences
                    sharedPreferences.edit()
                        .putInt("time_mode", position)
                        .apply();
                    
                    // 刷新数据显示
                    refreshData(position);
                }
            }
        );
        
        return view;
    }
    
    private void refreshData(int timeMode) {
        // 根据新的时间维度刷新数据
        // processAndDisplayData();
    }
}
```

### 高级用法

#### 获取当前选中状态

```java
// 获取当前选中的 RadioButton ID
int checkedId = animatedRadioGroup.getCheckedRadioButtonId();

// 获取当前选中的位置 (0=年, 1=月, 2=周)
int position = animatedRadioGroup.getCheckedPosition();
```

#### 程序化设置选中状态

```java
// 设置选中位置，执行动画
animatedRadioGroup.setCheckedPosition(1, true);  // 选中"月"，带动画

// 设置选中位置，不执行动画
animatedRadioGroup.setCheckedPosition(0, false); // 选中"年"，无动画
```

#### 访问内部组件

```java
// 获取内部的 RadioGroup（用于高级定制）
RadioGroup radioGroup = animatedRadioGroup.getRadioGroup();

// 获取内部的 AnimatedTabIndicator（用于高级定制）
AnimatedTabIndicator indicator = animatedRadioGroup.getAnimatedIndicator();
```

## 位置映射

| Position | 时间维度 | RadioButton ID |
|----------|---------|----------------|
| 0        | 年      | rb_year        |
| 1        | 月      | rb_month       |
| 2        | 周      | rb_week        |

## 自动功能

### 1. 动画管理

- **初始显示**：自动设置为无动画模式（满足需求 3.4）
- **用户切换**：自动执行 300ms 平滑动画（满足需求 2.1, 2.2）
- **连续点击**：自动响应最新目标位置（满足需求 2.5）

### 2. 触觉反馈

- 自动在用户点击时触发 `CLOCK_TICK` 触觉反馈（满足需求 5.1, 5.2）
- 自动检测设备支持情况（API 21+）

### 3. 异常处理

- 初始化失败时自动降级为标准 RadioGroup（满足需求 8.4）
- 动画异常时自动隐藏指示器，不影响用户使用
- 所有异常都会记录日志，便于调试

## 与旧版本对比

### 旧版本（手动集成）

```java
// 需要手动管理 3 个组件
RadioGroup rgTimeMode = view.findViewById(R.id.rg_time_mode);
AnimatedTabIndicator indicator = view.findViewById(R.id.animated_indicator);

// 需要手动关联
indicator.setRadioGroup(rgTimeMode, R.id.rb_year, R.id.rb_month, R.id.rb_week);

// 需要手动设置监听器
rgTimeMode.setOnCheckedChangeListener((group, checkedId) -> {
    int position = getPositionFromRadioButtonId(checkedId);
    indicator.setSelectedPosition(position, true);
    
    // 需要手动添加触觉反馈
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        group.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
    
    // 业务逻辑
    refreshData(position);
});

// 需要手动实现位置映射方法
private int getPositionFromRadioButtonId(int id) { ... }
```

### 新版本（AnimatedRadioGroup）

```java
// 只需要管理 1 个组件
AnimatedRadioGroup animatedRadioGroup = view.findViewById(R.id.animated_radio_group);

// 自动关联和初始化
animatedRadioGroup.setCheckedPosition(savedTimeMode, false);

// 简化的监听器（自动处理动画和触觉反馈）
animatedRadioGroup.setOnCheckedChangeListener((group, checkedId, position) -> {
    // 直接使用 position，无需手动映射
    refreshData(position);
});
```

**代码减少约 60%，更易维护！**

## 注意事项

1. **布局顺序**：AnimatedTabIndicator 必须在 RadioGroup 之前声明
2. **初始化时机**：在 `onCreateView()` 中初始化，确保 View 已填充
3. **动画参数**：初始设置使用 `animated=false`，用户切换自动使用 `animated=true`
4. **异常安全**：组件内部已处理所有异常，无需外部 try-catch

## 集成到现有项目

### DetailsFragment 集成

1. 修改 `fragment_details.xml`，将现有的 RadioGroup 替换为 AnimatedRadioGroup
2. 修改 `DetailsFragment.java`，使用新的 API
3. 删除手动的位置映射方法

### StatsFragment 集成

1. 修改 `fragment_stats.xml`，将现有的 RadioGroup 替换为 AnimatedRadioGroup
2. 修改 `StatsFragment.java`，使用新的 API
3. 删除手动的位置映射方法

## 测试建议

- ✅ 测试初始位置显示正确（无动画）
- ✅ 测试点击切换动画流畅（300ms）
- ✅ 测试快速连续点击响应正确
- ✅ 测试屏幕旋转后位置正确
- ✅ 测试触觉反馈正常工作
- ✅ 测试异常降级不影响使用

## 相关文件

- `AnimatedRadioGroup.java` - 本组件实现
- `AnimatedTabIndicator.java` - 动画指示器组件
- `requirements.md` - 功能需求文档
- `tasks.md` - 实现任务列表
