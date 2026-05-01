# 资产模块 FAB 按钮手势控制功能撤回

**日期**: 2026-05-01  
**状态**: ✅ 已完成

## 背景

用户最初要求为资产模块的左下角两个 FAB 按钮（添加资产、转移资产）添加手势控制功能：
- 下滑手势隐藏按钮
- 上滑手势显示按钮
- 默认显示，带过渡动画

经过多次尝试实现（包括 RecyclerView 滚动监听和 GestureDetector 手势监听），用户反馈实际效果不符合预期，最终要求完全撤回此功能。

## 撤回内容

### 1. AssetsFragment.java 成员变量
**删除**:
```java
private LinearLayout layoutFabButtons;
private boolean isFabVisible = true;
```

### 2. AssetsFragment.java initViews() 方法
**删除**:
```java
layoutFabButtons = view.findViewById(R.id.layout_fab_buttons);
setupScrollListener();
setupTouchListener(view);
```

### 3. AssetsFragment.java 方法删除
完全删除以下方法：
- `setupTouchListener(View view)` - 触摸手势监听器（约80行代码）
- `setupScrollListener()` - RecyclerView 滚动监听器（约25行代码）
- `hideFabButtons()` - 隐藏按钮动画（约20行代码）
- `showFabButtons()` - 显示按钮动画（约20行代码）

### 4. 布局文件修改
**文件**: `app/src/main/res/layout/fragment_assets.xml`  
**文件**: `app/app/src/main/res/layout/fragment_assets.xml`

**删除**:
```xml
android:id="@+id/layout_fab_buttons"
```

从 FAB 按钮容器的 LinearLayout 中移除 ID 属性。

## 验收结果

✅ 编译成功：`BUILD SUCCESSFUL`  
✅ 无编译错误  
✅ 无警告（仅标准的过时 API 提示）  
✅ 代码已恢复到添加手势功能之前的状态

## 影响范围

- **修改文件**: 3个
  - `app/app/src/main/java/com/example/budgetapp/ui/AssetsFragment.java`
  - `app/src/main/res/layout/fragment_assets.xml`
  - `app/app/src/main/res/layout/fragment_assets.xml`
- **删除代码**: 约150行
- **功能影响**: FAB 按钮恢复为固定显示，不再响应滑动手势

## 经验总结

1. **手势冲突**: RecyclerView 自身的滚动事件与自定义手势监听存在冲突
2. **用户体验**: 手势控制需要精确的阈值调整，否则容易误触或不响应
3. **需求确认**: 对于 UI 交互类需求，应先做原型验证再实现

## 后续建议

如果未来需要类似功能，建议：
1. 使用 CoordinatorLayout + Behavior 实现更流畅的滚动联动
2. 参考 Material Design 的 FAB 滚动行为标准实现
3. 先做小范围测试，确认手势响应符合预期后再全面实现
