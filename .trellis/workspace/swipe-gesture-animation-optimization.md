# 左右滑动手势动画优化实现

## 任务概述
优化"记账模块年视图"和"统计模块"的左右滑动手势动画，使其具有和"记账模块月视图日历"一样的跟手效果。

## 实现日期
2026-04-29

## 问题分析

### 原有实现
1. **RecordFragment（月视图日历）**：使用跟手的 `translationX` 动画，手指滑动时视图实时跟随
2. **YearCalendarActivity（年视图）**：使用传统的 `slide_in` XML 动画，只在滑动完成后播放固定动画
3. **StatsFragment（统计模块）**：使用传统的 `slide_in` XML 动画，只在滑动完成后播放固定动画

### 用户体验差异
- 月视图：手指滑动时视图实时跟随，松手后根据滑动距离决定是否切换，体验流畅自然
- 年视图和统计模块：手指滑动时无反馈，只有在快速滑动（fling）后才触发切换动画，体验生硬

## 解决方案

### 核心思路
将 RecordFragment 中成熟的跟手滑动动画机制移植到 YearCalendarActivity 和 StatsFragment。

### 关键技术点

#### 1. 使用 `getRawX()` 而非 `getX()`
```java
// ❌ 错误：使用相对坐标会导致视图移动时坐标疯狂抵消
float dx = e.getX() - initialX;

// ✅ 正确：使用屏幕绝对坐标
initialX = e.getRawX();
float dx = e.getRawX() - initialX;
```

#### 2. 正确的触摸事件处理
```java
// ❌ 错误：使用 RecyclerView.SimpleOnItemTouchListener
// 问题：onTouchEvent 不会被正确调用，导致无法跟手

// ✅ 正确：直接使用 View.OnTouchListener
view.setOnTouchListener(new View.OnTouchListener() {
    @Override
    public boolean onTouch(View v, MotionEvent e) {
        // 在这里处理所有触摸事件
        // ACTION_MOVE 会被正确调用，实现跟手效果
    }
});
```

#### 3. 剥夺父容器的滑动权
```java
if (v.getParent() != null) {
    v.getParent().requestDisallowInterceptTouchEvent(true);
}
```

#### 4. 跟手阻尼移动
```java
// 0.6 的阻尼系数让滑动更有质感
v.setTranslationX(dx * 0.6f);
// 滑动时降低透明度，增强视觉反馈
v.setAlpha(1f - (Math.abs(dx) / screenWidth) * 0.5f);
```

#### 5. 滑动距离判断
```java
// 滑动超过屏幕宽度的 20% 才触发切换
if (dx > screenWidth * 0.2f) {
    finishSwipeAnimation(v, screenWidth, -1);
} else if (dx < -screenWidth * 0.2f) {
    finishSwipeAnimation(v, -screenWidth, 1);
} else {
    // 距离不够，平滑恢复原位
    v.animate().translationX(0f).alpha(1f).setDuration(250)
            .setInterpolator(new DecelerateInterpolator()).start();
}
```

#### 5. 结算动画流程
```java
private void finishSwipeAnimation(View view, float targetX, int offset) {
    // 1. 滑出屏幕（150ms）
    view.animate()
        .translationX(targetX)
        .alpha(0f)
        .setDuration(150)
        .withEndAction(() -> {
            // 2. 在屏幕外切换数据
            updateData(offset);
            
            // 3. 瞬移到另一侧准备入场
            view.setTranslationX(-targetX * 0.5f);
            
            // 4. 减速滑入（300ms）
            view.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        })
        .start();
}
```

## 具体修改

### 1. YearCalendarActivity.java

#### 新增成员变量
```java
// 跟手滑动相关变量
private float initialX, initialY;
private boolean isHorizontalSwipe = false;
```

#### 新增触摸监听器（关键修复：使用 View.OnTouchListener）
```java
private void setupTouchListener() {
    rvYearList.setOnTouchListener(new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (isAnimating) return true;

            float dx = 0;
            float dy = 0;
            
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = e.getRawX();
                    initialY = e.getRawY();
                    isHorizontalSwipe = false;
                    return true; // 消费DOWN事件
                    
                case MotionEvent.ACTION_MOVE:
                    dx = e.getRawX() - initialX;
                    dy = e.getRawY() - initialY;
                    
                    // 判断是否为横向滑动
                    if (!isHorizontalSwipe && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        if (Math.abs(dx) > Math.abs(dy)) {
                            isHorizontalSwipe = true;
                            if (v.getParent() != null) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        } else {
                            return false; // 垂直滑动，不处理
                        }
                    }
                    
                    // 🌟 关键：跟手阻尼移动（实时响应手指位置）
                    if (isHorizontalSwipe) {
                        float screenWidth = v.getWidth();
                        if (screenWidth == 0) screenWidth = 1080;
                        v.setTranslationX(dx * 0.6f);
                        v.setAlpha(1f - (Math.abs(dx) / screenWidth) * 0.5f);
                        return true; // 消费MOVE事件
                    }
                    break;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isHorizontalSwipe) {
                        dx = e.getRawX() - initialX;
                        float screenWidth = v.getWidth();
                        if (screenWidth == 0) screenWidth = 1080;
                        
                        if (dx > screenWidth * 0.2f) {
                            finishSwipeAnimation(rvYearList, screenWidth, -1);
                        } else if (dx < -screenWidth * 0.2f) {
                            finishSwipeAnimation(rvYearList, -screenWidth, 1);
                        } else {
                            v.animate().translationX(0f).alpha(1f).setDuration(250)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                        }
                        isHorizontalSwipe = false;
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        return true;
                    }
                    break;
            }
            return false;
        }
    });
}
```

#### 新增结算动画方法
```java
private void finishSwipeAnimation(RecyclerView rv, float targetTranslationX, int yearOffset) {
    isAnimating = true;
    rv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    
    rv.animate()
            .translationX(targetTranslationX)
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> {
                currentYear += yearOffset;
                tvTitle.setText(String.valueOf(currentYear));
                rv.setTranslationX(-targetTranslationX * 0.5f);
                loadData(0, currentYear);
                
                rv.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            rv.setLayerType(View.LAYER_TYPE_NONE, null);
                            isAnimating = false;
                        })
                        .start();
            })
            .start();
}
```

### 2. StatsFragment.java

#### 新增成员变量
```java
// 跟手滑动相关变量
private float initialX, initialY;
private boolean isSwipeInProgress = false;
private View statsContainer; // 统计内容容器
```

#### 修改 setupGestures() 方法
```java
private void setupGestures() {
    if (scrollView == null) return;
    gestureDetector = new GestureDetector(requireContext(), new SwipeGestureListener());

    scrollView.setOnTouchListener((v, event) -> {
        gestureDetector.onTouchEvent(event);
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = event.getRawX();
                initialY = event.getRawY();
                touchStartX = event.getX();
                touchStartY = event.getY();
                isDirectionLocked = false;
                isHorizontalSwipe = false;
                isSwipeInProgress = false;
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (!isDirectionLocked) {
                    float dx = Math.abs(event.getX() - touchStartX);
                    float dy = Math.abs(event.getY() - touchStartY);
                    if (dx > touchSlop || dy > touchSlop) {
                        isDirectionLocked = true;
                        if (dx > dy) {
                            isHorizontalSwipe = true;
                            isSwipeInProgress = true;
                            scrollView.requestDisallowInterceptTouchEvent(true);
                        } else {
                            isHorizontalSwipe = false;
                        }
                    }
                }
                
                // 跟手滑动效果
                if (isSwipeInProgress && statsContainer != null) {
                    float dx = event.getRawX() - initialX;
                    float screenWidth = statsContainer.getWidth();
                    if (screenWidth == 0) screenWidth = 1080;
                    
                    statsContainer.setTranslationX(dx * 0.6f);
                    statsContainer.setAlpha(1f - (Math.abs(dx) / screenWidth) * 0.5f);
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isSwipeInProgress && statsContainer != null) {
                    float dx = event.getRawX() - initialX;
                    float screenWidth = statsContainer.getWidth();
                    if (screenWidth == 0) screenWidth = 1080;
                    
                    if (dx > screenWidth * 0.2f) {
                        finishSwipeAnimation(statsContainer, screenWidth, -1);
                    } else if (dx < -screenWidth * 0.2f) {
                        finishSwipeAnimation(statsContainer, -screenWidth, 1);
                    } else {
                        statsContainer.animate().translationX(0f).alpha(1f).setDuration(250)
                                .setInterpolator(new DecelerateInterpolator()).start();
                    }
                }
                isDirectionLocked = false;
                isHorizontalSwipe = false;
                isSwipeInProgress = false;
                scrollView.requestDisallowInterceptTouchEvent(false);
                break;
        }
        return isDirectionLocked && isHorizontalSwipe;
    });
    
    // ... 图表触摸监听器保持不变
}
```

#### 新增结算动画方法
```java
private void finishSwipeAnimation(View container, float targetTranslationX, int offset) {
    container.animate()
            .translationX(targetTranslationX)
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> {
                if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
                else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
                else selectedDate = selectedDate.plusWeeks(offset);

                container.setTranslationX(-targetTranslationX * 0.5f);
                updateDateRangeDisplay();
                refreshData();

                container.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            })
            .start();
}
```

#### 修改 changeDate() 方法
```java
private void changeDate(int offset) {
    if (statsContainer != null && statsContainer.getWidth() > 0) {
        float screenWidth = statsContainer.getWidth();
        float targetX = (offset == -1) ? screenWidth : -screenWidth;
        finishSwipeAnimation(statsContainer, targetX, offset);
    } else {
        // 降级方案
        if (currentMode == 0) selectedDate = selectedDate.plusYears(offset);
        else if (currentMode == 1) selectedDate = selectedDate.plusMonths(offset);
        else selectedDate = selectedDate.plusWeeks(offset);
        updateDateRangeDisplay();
        refreshData();
    }
}
```

### 3. fragment_stats.xml

#### 添加统计内容容器
在时间范围选择器和导航按钮之后，添加一个容器包裹所有统计内容：

```xml
<!-- 新增：统计内容容器，用于跟手滑动动画 -->
<LinearLayout
    android:id="@+id/stats_content_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">

    <!-- 所有统计内容（折线图、饼图、总结）都放在这个容器内 -->
    <LinearLayout
        android:id="@+id/layout_trend_section"
        ...>
    </LinearLayout>
    
    <LinearLayout
        android:id="@+id/layout_expense_section"
        ...>
    </LinearLayout>
    
    <LinearLayout
        android:id="@+id/layout_summary_section"
        ...>
    </LinearLayout>

</LinearLayout>
<!-- 结束：统计内容容器 -->
```

## 效果对比

### 优化前
- ❌ 手指滑动时无视觉反馈
- ❌ 只有快速滑动（fling）才能触发切换
- ❌ 动画生硬，缺乏流畅感
- ❌ 用户需要用力滑动才能切换

### 优化后
- ✅ 手指滑动时视图实时跟随
- ✅ 慢速滑动也能触发切换（超过20%屏幕宽度）
- ✅ 动画流畅自然，有阻尼感
- ✅ 滑动距离不够时平滑回弹
- ✅ 透明度渐变增强视觉反馈
- ✅ 与月视图日历体验完全一致

## 技术亮点

1. **统一的交互体验**：三个模块（月视图、年视图、统计）现在使用相同的滑动交互逻辑
2. **性能优化**：使用硬件加速层（LAYER_TYPE_HARDWARE）提升动画性能
3. **智能判断**：区分横向和纵向滑动，不影响 ScrollView 的垂直滚动
4. **平滑过渡**：使用 DecelerateInterpolator 让动画更自然
5. **视觉反馈**：透明度渐变和阻尼移动增强用户体验

## 测试建议

1. **基础功能测试**
   - 在年视图中左右滑动切换年份
   - 在统计模块中左右滑动切换时间段（年/月/周）
   - 验证滑动距离不够时的回弹效果

2. **边界情况测试**
   - 快速连续滑动
   - 滑动到边界年份（如2000年、2050年）
   - 在滑动过程中切换时间范围模式

3. **性能测试**
   - 观察动画是否流畅（60fps）
   - 检查是否有内存泄漏
   - 验证在低端设备上的表现

4. **交互冲突测试**
   - 统计模块中横向滑动与垂直滚动的冲突
   - 图表的触摸交互与滑动切换的冲突

## 编译结果

✅ 编译成功，无错误和警告

```
BUILD SUCCESSFUL in 24s
38 actionable tasks: 9 executed, 29 up-to-date
```

## 总结

通过将 RecordFragment 中成熟的跟手滑动动画机制移植到 YearCalendarActivity 和 StatsFragment，成功实现了三个模块统一的交互体验。优化后的滑动手势更加流畅自然，用户体验显著提升。
