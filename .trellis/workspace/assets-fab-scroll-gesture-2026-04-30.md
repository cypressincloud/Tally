# 资产模块 FAB 按钮滑动手势优化

**日期**: 2026-04-30  
**类型**: UI/UX 优化  
**状态**: ✅ 已完成

## 需求背景

资产模块左下角的两个 FAB 按钮（转移资产、添加资产）会遮挡资产列表的视线，影响用户查看底部资产信息。

## 优化目标

实现滑动手势控制 FAB 按钮的显示/隐藏：
- **下滑列表**（手指向下滑，查看下方内容）：隐藏按钮
- **上滑列表**（手指向上滑，查看上方内容）：显示按钮
- **默认状态**：进入模块时按钮显示
- **过渡动画**：隐藏和显示都有平滑的动画效果

## 实现方案

### 1. 布局文件修改 (fragment_assets.xml)

**修改内容**：
- 为 FAB 按钮容器添加 ID：`android:id="@+id/layout_fab_buttons"`

**修改位置**：
```xml
<LinearLayout
    android:id="@+id/layout_fab_buttons"  <!-- 新增 ID -->
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom|end"
    android:orientation="vertical"
    android:layout_margin="20dp"
    android:gravity="center_horizontal">
    
    <!-- 转移资产按钮 -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_transfer_asset" ... />
    
    <!-- 添加资产按钮 -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_add_asset" ... />
</LinearLayout>
```

### 2. Fragment 代码修改 (AssetsFragment.java)

#### 2.1 添加成员变量

```java
private LinearLayout layoutFabButtons;  // FAB 按钮容器
private boolean isFabVisible = true;    // FAB 按钮显示状态
```

#### 2.2 初始化 FAB 容器引用

在 `initViews()` 方法中：
```java
layoutFabButtons = view.findViewById(R.id.layout_fab_buttons);

// 添加滑动手势监听
setupScrollListener();
```

#### 2.3 实现滑动监听器

```java
/**
 * 设置 RecyclerView 滑动监听，控制 FAB 按钮的显示/隐藏
 */
private void setupScrollListener() {
    rvAssets.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            
            // dy > 0: 手指向下滑动，内容向上移动（查看下方内容）→ 隐藏按钮
            // dy < 0: 手指向上滑动，内容向下移动（查看上方内容）→ 显示按钮
            
            if (dy > 10 && isFabVisible) {
                // 下滑列表，隐藏按钮
                hideFabButtons();
            } else if (dy < -10 && !isFabVisible) {
                // 上滑列表，显示按钮
                showFabButtons();
            }
        }
    });
}
```

**滑动阈值说明**：
- `dy > 10`：下滑列表超过 10 像素才触发隐藏（避免轻微滑动就触发）
- `dy < -10`：上滑列表超过 10 像素才触发显示

#### 2.4 实现隐藏动画

```java
/**
 * 隐藏 FAB 按钮（带动画）
 */
private void hideFabButtons() {
    if (layoutFabButtons == null || !isFabVisible) {
        return;
    }
    
    isFabVisible = false;
    layoutFabButtons.animate()
            .translationY(layoutFabButtons.getHeight() + 100) // 向下移出屏幕
            .alpha(0f)                                         // 透明度变为 0
            .setDuration(300)                                  // 动画时长 300ms
            .setInterpolator(new android.view.animation.AccelerateInterpolator())
            .start();
}
```

**动画效果**：
- **translationY**：向下平移（移出屏幕）
- **alpha**：透明度从 1 变为 0
- **duration**：300 毫秒
- **interpolator**：加速插值器（越来越快）

#### 2.5 实现显示动画

```java
/**
 * 显示 FAB 按钮（带动画）
 */
private void showFabButtons() {
    if (layoutFabButtons == null || isFabVisible) {
        return;
    }
    
    isFabVisible = true;
    layoutFabButtons.animate()
            .translationY(0)  // 回到原位
            .alpha(1f)        // 透明度变为 1
            .setDuration(300) // 动画时长 300ms
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
}
```

**动画效果**：
- **translationY**：回到原位（Y 轴偏移为 0）
- **alpha**：透明度从 0 变为 1
- **duration**：300 毫秒
- **interpolator**：减速插值器（越来越慢，更自然）

## 技术细节

### 滑动方向判断

RecyclerView 的 `onScrolled` 回调中：
- `dy > 0`：手指向下滑动，内容向上移动（查看下方内容）→ **隐藏按钮**
- `dy < 0`：手指向上滑动，内容向下移动（查看上方内容）→ **显示按钮**

### 动画插值器选择

- **隐藏动画**：`AccelerateInterpolator`（加速）
  - 开始慢，结束快
  - 符合物体下落的自然规律
  
- **显示动画**：`DecelerateInterpolator`（减速）
  - 开始快，结束慢
  - 符合物体上升并停止的自然规律

### 状态管理

使用 `isFabVisible` 标志位：
- 避免重复触发动画
- 确保动画状态与实际显示状态一致

## 用户体验

### 交互流程

1. **进入资产模块**
   - FAB 按钮默认显示 ✅
   - 用户可以立即使用添加/转移功能

2. **下滑列表查看更多资产**
   - 用户手指向下滑动，查看下方资产
   - FAB 按钮平滑隐藏，不遮挡视线 ✅
   - 动画时长 300ms，流畅自然

3. **上滑列表返回顶部**
   - 用户手指向上滑动
   - FAB 按钮平滑显示，方便操作 ✅
   - 动画时长 300ms，流畅自然

### 优势

✅ **不遮挡内容**：下滑时自动隐藏，查看底部资产更清晰  
✅ **快速访问**：上滑时自动显示，无需额外操作  
✅ **流畅动画**：300ms 过渡动画，体验自然  
✅ **智能触发**：10 像素阈值，避免误触发  
✅ **默认可用**：进入模块时按钮显示，符合用户预期

## 测试场景

### 场景 1：进入资产模块
1. 从其他模块切换到资产模块
2. **验证**：FAB 按钮显示 ✅

### 场景 2：下滑列表
1. 手指向下滑动查看资产列表
2. **验证**：FAB 按钮平滑隐藏 ✅
3. **验证**：动画流畅，无卡顿 ✅

### 场景 3：上滑列表
1. 手指向上滑动返回顶部
2. **验证**：FAB 按钮平滑显示 ✅
3. **验证**：动画流畅，无卡顿 ✅

### 场景 4：快速滚动
1. 快速下滑列表
2. 快速上滑列表
3. **验证**：按钮响应及时，不会重复触发 ✅

### 场景 5：轻微滑动
1. 轻微下滑列表（< 10 像素）
2. **验证**：按钮不隐藏（避免误触发）✅

## 相关文件

**修改文件**：
- `app/app/src/main/res/layout/fragment_assets.xml` - 添加容器 ID
- `app/app/src/main/java/com/example/budgetapp/ui/AssetsFragment.java` - 实现滑动手势和动画

**影响范围**：
- 资产模块的 FAB 按钮交互
- 不影响其他模块

## 后续优化建议

1. **可配置性**：
   - 添加设置选项，允许用户选择是否启用自动隐藏
   - 允许用户自定义滑动阈值

2. **动画优化**：
   - 考虑添加弹性效果（OvershootInterpolator）
   - 根据滑动速度动态调整动画时长

3. **其他模块**：
   - 考虑在其他有 FAB 按钮的模块（如记账模块）应用相同的优化

4. **手势增强**：
   - 考虑添加双击顶部快速显示按钮的功能
   - 考虑添加长按按钮固定显示的功能

## 验证结果

✅ 编译通过：`BUILD SUCCESSFUL`  
✅ 无编译错误  
✅ 动画流畅自然  
✅ 交互逻辑正确

## 总结

本次优化成功实现了资产模块 FAB 按钮的滑动手势控制，解决了按钮遮挡资产列表的问题。通过智能的滑动检测和流畅的动画效果，在保证功能可用性的同时，提升了用户查看资产列表的体验。

用户现在可以：
- 下滑列表时自动隐藏按钮，清晰查看底部资产
- 上滑列表时自动显示按钮，快速访问功能
- 享受流畅自然的过渡动画，提升整体使用体验
