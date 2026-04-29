# 任务 2.2 验证文档：实现初始位置设置方法

## 任务概述
实现 `AnimatedTabIndicator` 的初始位置设置方法，支持根据 position 参数设置指示器位置，并可控制是否执行动画。

## 实现详情

### 方法签名
```java
public void setSelectedPosition(int position, boolean animated)
```

### 参数说明
- `position` (int): 选项位置
  - 0 = 年
  - 1 = 月
  - 2 = 周
- `animated` (boolean): 是否执行动画
  - `true` = 执行平滑动画切换
  - `false` = 直接定位，不执行动画（用于初始显示）

### 实现位置
文件：`app/app/src/main/java/com/example/budgetapp/ui/AnimatedTabIndicator.java`

行号：约 170-183

### 实现代码
```java
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
```

### 相关方法

#### calculateTargetPosition(int position)
计算目标位置和宽度，根据 RadioButton 的实际测量尺寸动态计算。

```java
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
```

#### animateToPosition(int position)
执行平滑动画到目标位置，使用 ObjectAnimator 和 DecelerateInterpolator。

## 需求验证

### ✅ 需求 3.1: 初始显示时立即显示指示器
- 当 `animated=false` 时，直接设置 `currentX` 和 `currentWidth`
- 调用 `invalidate()` 立即重绘，无延迟

### ✅ 需求 3.4: 初始显示时不执行动画
- `animated` 参数控制动画行为
- `animated=false` 分支直接定位，跳过 `animateToPosition()` 调用

### ✅ 需求 7.3: 提供公开方法用于设置当前选中项
- 方法声明为 `public`
- 可从外部（Fragment）调用
- 接口清晰，参数语义明确

## 使用示例

### 初始显示（无动画）
```java
// 在 Fragment 的 onCreateView 或 onViewCreated 中
AnimatedTabIndicator indicator = findViewById(R.id.animated_indicator);
indicator.setRadioGroup(radioGroup, R.id.rb_year, R.id.rb_month, R.id.rb_week);

// 根据 SharedPreferences 设置初始位置
int savedPosition = sharedPreferences.getInt("time_mode", 1); // 默认月=1
indicator.setSelectedPosition(savedPosition, false); // 不执行动画
```

### 用户切换（有动画）
```java
radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
    int position = getPositionFromId(checkedId);
    indicator.setSelectedPosition(position, true); // 执行动画
});
```

## 测试验证

### 单元测试
创建了 `AnimatedTabIndicatorTest.java`，验证：
- ✅ 方法签名正确（public, 返回 void）
- ✅ 参数类型正确（int position, boolean animated）
- ✅ 方法可访问性（public 方法）

测试文件位置：`app/app/src/test/java/com/example/budgetapp/ui/AnimatedTabIndicatorTest.java`

**注意**：由于 `AnimatedTabIndicator` 继承自 Android View 类，完整的功能测试需要在 Android 环境中运行（Instrumented Test 或 Robolectric）。当前的单元测试通过反射验证方法签名和可见性。

### 手动验证步骤
1. 编译项目：`.\gradlew.bat :app:assembleDebug`
2. 在 DetailsFragment 或 StatsFragment 中集成该方法
3. 运行应用，观察初始显示：
   - 指示器应立即显示在正确位置
   - 无动画效果
4. 点击切换选项，观察动画效果：
   - 指示器应平滑移动到新位置
   - 动画时长约 300ms

## 代码质量

### ✅ 代码规范
- 遵循 Java 命名约定
- 添加完整的 JavaDoc 注释
- 参数验证（通过 `calculateTargetPosition` 中的边界检查）

### ✅ 错误处理
- 无效 position 值（< 0 或 >= 3）静默处理，不抛出异常
- RadioGroup 未设置时安全返回
- RadioButton 未找到时安全返回

### ✅ 性能考虑
- 直接定位模式（animated=false）无性能开销
- 动画模式使用硬件加速（在 init() 中设置）
- 使用 ObjectAnimator 而非帧动画

## 后续集成

该方法已准备好在以下任务中使用：
- 任务 5.2: 集成到 DetailsFragment
- 任务 6.2: 集成到 StatsFragment

集成时需要：
1. 在布局中添加 AnimatedTabIndicator
2. 在 Fragment 中获取 indicator 引用
3. 调用 `setRadioGroup()` 关联 RadioGroup
4. 调用 `setSelectedPosition(position, false)` 设置初始位置
5. 在 RadioGroup 监听器中调用 `setSelectedPosition(position, true)` 处理切换

## 结论

✅ **任务 2.2 已完成**

`setSelectedPosition` 方法已成功实现，满足所有需求：
- 支持根据 position 参数计算目标位置（0=年, 1=月, 2=周）
- 支持通过 animated 参数控制是否执行动画
- 初始显示时可使用 animated=false 直接定位
- 代码质量良好，错误处理完善
- 已通过编译验证

方法已准备好供后续任务使用。
