# 滑动速度阈值调整

## 调整日期
2026-04-29

## 调整原因
由于已经实现了跟手滑动功能，用户可以通过慢速滑动来切换页面。原有的快速滑动（fling）阈值设置过高，导致用户需要非常用力地快速滑动才能触发，体验不够友好。

## 调整内容

### 原有阈值
- **RecordFragment（月视图）**：
  - 滑动距离阈值：100px
  - 滑动速度阈值：100 px/s

- **YearCalendarActivity（年视图）**：
  - 滑动距离阈值：100px
  - 滑动速度阈值：100 px/s

- **StatsFragment（统计模块）**：
  - 滑动距离阈值：140px
  - 滑动速度阈值：200 px/s

- **DetailsFragment（明细模块）**：
  - 滑动距离阈值：100px
  - 滑动速度阈值：100 px/s

### 调整后的阈值
**所有模块统一调整为：**
- 滑动距离阈值：**50px**（降低 50%）
- 滑动速度阈值：**50 px/s**（降低 50-75%）

## 具体修改

### 1. RecordFragment.java
```java
private void initGestureDetector() {
    gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
        // 降低阈值，让快速滑动更容易触发
        private static final int SWIPE_THRESHOLD = 50;
        private static final int SWIPE_VELOCITY_THRESHOLD = 50;
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // ... fling 处理逻辑
        }
    });
}
```

### 2. YearCalendarActivity.java
```java
private void initGestureDetector() {
    gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // 降低阈值，让快速滑动更容易触发
            if (Math.abs(diffX) > Math.abs(diffY) &&
                    Math.abs(diffX) > 50 && Math.abs(velocityX) > 50) {
                // ... 切换年份
            }
        }
    });
}
```

### 3. StatsFragment.java
```java
private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
    // 降低阈值，让快速滑动更容易触发
    private static final int SWIPE_THRESHOLD = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;
    
    @Override
    public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, 
                          float velocityX, float velocityY) {
        // ... fling 处理逻辑
    }
}
```

### 4. DetailsFragment.java
```java
private void initGestureDetector() {
    gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
        // 降低阈值，让快速滑动更容易触发
        private static final int SWIPE_THRESHOLD = 50;
        private static final int SWIPE_VELOCITY_THRESHOLD = 50;
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // ... fling 处理逻辑
        }
    });
}
```

## 用户体验改进

### 调整前
- ❌ 需要非常用力地快速滑动才能触发 fling
- ❌ 统计模块的阈值特别高（140px 距离 + 200px/s 速度）
- ❌ 各模块阈值不统一，体验不一致

### 调整后
- ✅ 轻松的快速滑动即可触发 fling
- ✅ 所有模块阈值统一，体验一致
- ✅ 配合跟手滑动，提供两种切换方式：
  - **慢速滑动**：跟手移动，滑动超过 20% 屏幕宽度即可切换
  - **快速滑动**：fling 手势，轻轻一划即可切换

## 技术说明

### 为什么可以降低阈值？
1. **跟手滑动已实现**：用户主要通过跟手滑动来切换页面
2. **fling 作为辅助**：快速滑动（fling）作为快捷方式，应该更容易触发
3. **不会误触**：
   - 仍然需要横向滑动距离 > 50px
   - 仍然需要速度 > 50px/s
   - 横向滑动距离必须大于纵向滑动距离
   - 这些条件足以避免误触

### 阈值含义
- **SWIPE_THRESHOLD (50px)**：手指滑动的最小距离
  - 约等于 0.5cm（在常见手机屏幕上）
  - 足够避免误触，但不会太难触发

- **SWIPE_VELOCITY_THRESHOLD (50 px/s)**：手指滑动的最小速度
  - 非常低的速度要求
  - 几乎任何有意的快速滑动都能达到

## 编译结果
✅ 编译成功，无错误

```
BUILD SUCCESSFUL in 6s
38 actionable tasks: 9 executed, 29 up-to-date
```

## 测试建议
1. 测试慢速跟手滑动（已有功能）
2. 测试快速滑动（fling）是否更容易触发
3. 测试是否会误触（在垂直滚动时不应触发横向切换）
4. 对比调整前后的体验差异

## 总结
通过将滑动阈值降低 50-75%，并统一所有模块的阈值设置，用户现在可以更轻松地使用快速滑动（fling）手势来切换页面。配合已实现的跟手滑动功能，用户拥有了两种灵活的切换方式，大大提升了操作体验。
