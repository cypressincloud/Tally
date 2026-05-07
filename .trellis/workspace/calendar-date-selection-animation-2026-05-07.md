# 日历日期选中动画优化

**日期**: 2026-05-07  
**类型**: UI 优化  
**状态**: ✅ 已完成

## 需求描述

为记账模块的日历日期点击效果添加过渡动画。原有效果是点击后直接显示主题色外框，缺少过渡效果。现在需要添加干脆快速的由浅入深的过渡动画。

## 实现方案

### 1. 技术方案
- 使用 `ValueAnimator` 和 `ArgbEvaluator` 实现颜色过渡动画
- 动画时长：200ms（快速干脆）
- 颜色过渡：从 30% 透明度到 100% 不透明度
- 保持原有的圆角矩形边框样式

### 2. 代码修改

**文件**: `app/src/main/java/com/example/budgetapp/ui/CalendarAdapter.java`

#### 新增导入
```java
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
```

#### 新增方法
```java
/**
 * 为选中的日期应用由浅入深的快速过渡动画
 * @param holder ViewHolder
 * @param targetColor 目标颜色（主题色）
 * @param textColor 文字颜色
 */
private void applySelectedDateAnimation(ViewHolder holder, int targetColor, int textColor)
```

#### 修改选中状态逻辑
将原有的静态背景设置：
```java
holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
Drawable bg = holder.itemView.getBackground();
if (bg != null) bg.setTint(themeColor);
```

替换为动画方法调用：
```java
applySelectedDateAnimation(holder, themeColor, defaultDayColor);
```

### 3. 动画特性
- **起始颜色**: 主题色 + 15% 透明度（38/255）
- **结束颜色**: 主题色 + 100% 不透明度
- **动画时长**: 100ms（极速干脆）
- **动画类型**: 
  - 颜色渐变（ARGB）
  - 边框缩放（通过 inset 调整实现）
- **缩放范围**: 80% → 100%
- **边框宽度**: 1.5dp
- **圆角半径**: 12dp
- **基础内边距**: 4dp

### 4. 动画效果
组合动画效果：
1. **颜色动画**：边框从极浅色（15%透明度）到深色（100%不透明）
2. **边框缩放**：边框从 80% 缩放到 100%，通过动态调整 inset 实现
3. **日期内容**：保持静止不动，只有边框参与动画

## 测试验证

### 编译测试
```bash
gradlew.bat :app:assembleDebug
```
✅ 编译成功，无错误

### 预期效果
1. 点击日历日期时，边框颜色从极浅色快速过渡到深色
2. 边框同时从 80% 缩放到 100%，产生更明显的"弹出"效果
3. 日期数字和金额文字保持静止，不参与动画
4. 动画时长 100ms，极速干脆的反馈
5. 保持原有的圆角矩形边框样式
6. 不影响其他状态（今天、续费日期、预算状态等）

## 技术细节

### 动画实现原理
1. 创建 `GradientDrawable` 作为背景形状
2. 使用单个 `ValueAnimator` 同时控制颜色和缩放
3. **颜色动画**：使用 `ArgbEvaluator` 实现颜色插值（15% → 100% 透明度）
4. **边框缩放**：通过动态调整 `InsetDrawable` 的 inset 值实现（80% → 100%）
   - 缩放越小，inset 越大（边框离边缘越远）
   - 缩放越大，inset 越小（边框离边缘越近）
5. 在动画更新回调中同时更新颜色和 inset
6. 调用 `invalidate()` 触发重绘
7. 日期内容不参与动画，保持稳定

### 性能考虑
- 动画时长短（100ms），不会造成性能问题
- 仅在选中状态触发，不影响其他日期的渲染
- 使用硬件加速的 `ValueAnimator`，性能优秀
- 通过 inset 调整实现边框缩放，避免整个 View 的缩放变换
- 日期内容保持静止，减少重绘范围

## 相关文件
- `app/src/main/java/com/example/budgetapp/ui/CalendarAdapter.java` ✅ 已修改
- `app/src/main/res/drawable/bg_selected_date.xml`（保留原有资源，未修改）

## 后续优化建议
1. 可以考虑添加触摸反馈动画（涟漪效果）
2. 可以考虑添加取消选中时的淡出动画
3. 可以将动画时长、透明度、缩放比例参数提取为常量，便于调整
4. 可以考虑使用 AnimatorSet 来更精细地控制动画时序
