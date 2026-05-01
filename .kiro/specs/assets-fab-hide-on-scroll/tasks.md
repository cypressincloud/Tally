# 实现计划：资产模块滚动隐藏按钮

## 概述

本功能为 AssetsFragment 添加滚动监听机制，根据用户的滚动方向自动显示或隐藏浮动按钮容器。实现方式采用 RecyclerView.OnScrollListener 监听滚动事件，使用 ViewPropertyAnimator 执行平移动画，确保流畅的用户体验。

## 任务列表

- [x] 1. 修改布局文件，为 FAB 容器添加 ID
  - 打开 `app/src/main/res/layout/fragment_assets.xml`
  - 找到包含 `fab_add_asset` 和 `fab_transfer_asset` 的 LinearLayout 容器
  - 为该 LinearLayout 添加 `android:id="@+id/fab_container"` 属性
  - 确保容器的 `android:layout_gravity` 为 `bottom|end`
  - _需求: 1.1, 2.1, 3.1_

- [x] 2. 在 AssetsFragment 中添加滚动监听器和动画处理逻辑
  - [x] 2.1 添加成员变量和常量定义
    - 在 AssetsFragment 类中添加成员变量：`private LinearLayout fabContainer;`
    - 添加成员变量：`private boolean isFabVisible = true;`
    - 添加成员变量：`private FabScrollListener fabScrollListener;`
    - 添加常量：`private static final int SCROLL_THRESHOLD = 5;`
    - 添加常量：`private static final int ANIMATION_DURATION = 200;`
    - _需求: 1.2, 4.1_

  - [x] 2.2 实现 FabScrollListener 内部类
    - 在 AssetsFragment 中创建内部类 `FabScrollListener extends RecyclerView.OnScrollListener`
    - 重写 `onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)` 方法
    - 实现滚动阈值过滤逻辑：`if (Math.abs(dy) < SCROLL_THRESHOLD) return;`
    - 实现向上滚动隐藏逻辑：`if (dy > 0 && isFabVisible) hideFab();`
    - 实现向下滚动显示逻辑：`else if (dy < 0 && !isFabVisible) showFab();`
    - _需求: 1.1, 1.2, 1.3, 1.4_

  - [x] 2.3 实现 hideFab() 方法
    - 创建 `private void hideFab()` 方法
    - 添加空指针保护：`if (fabContainer == null || !isFabVisible) return;`
    - 更新状态：`isFabVisible = false;`
    - 计算平移距离：`int translationY = fabContainer.getHeight() + getResources().getDimensionPixelSize(R.dimen.fab_margin);`
    - 执行动画：使用 `fabContainer.animate().translationY(translationY).setInterpolator(new AccelerateInterpolator()).setDuration(ANIMATION_DURATION).start();`
    - _需求: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 2.4 实现 showFab() 方法
    - 创建 `private void showFab()` 方法
    - 添加空指针保护：`if (fabContainer == null || isFabVisible) return;`
    - 更新状态：`isFabVisible = true;`
    - 执行动画：使用 `fabContainer.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).setDuration(ANIMATION_DURATION).start();`
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. 集成滚动监听器到 Fragment 生命周期
  - [x] 3.1 在 onCreateView 中初始化组件
    - 在 `initViews(View view)` 方法中添加：`fabContainer = view.findViewById(R.id.fab_container);`
    - 在 `initViews(View view)` 方法末尾创建监听器：`fabScrollListener = new FabScrollListener();`
    - 在 `initViews(View view)` 方法末尾添加监听器：`rvAssets.addOnScrollListener(fabScrollListener);`
    - _需求: 4.1_

  - [x] 3.2 在 onResume 中重置按钮状态
    - 在现有的 `onResume()` 方法中添加代码
    - 重置可见状态：`isFabVisible = true;`
    - 重置位置：`if (fabContainer != null) { fabContainer.setTranslationY(0); }`
    - _需求: 4.3_

  - [x] 3.3 在 onDestroyView 中清理资源
    - 创建 `onDestroyView()` 方法（如果不存在）
    - 移除监听器：`if (rvAssets != null && fabScrollListener != null) { rvAssets.removeOnScrollListener(fabScrollListener); }`
    - 清空引用：`fabScrollListener = null;`
    - 清空引用：`fabContainer = null;`
    - 调用父类方法：`super.onDestroyView();`
    - _需求: 5.3_

- [x] 4. 检查点 - 确保所有测试通过
  - 确保所有测试通过，询问用户是否有问题

- [ ] 5. 手动测试和验证
  - [~] 5.1 基本功能测试
    - 启动应用，进入资产模块
    - 向上滚动列表，验证按钮平滑隐藏
    - 向下滚动列表，验证按钮平滑显示
    - 验证动画流畅，无卡顿
    - _需求: 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4_

  - [~] 5.2 边界情况测试
    - 测试列表项少于屏幕高度时，按钮保持显示
    - 测试快速连续滚动，验证动画正常切换
    - 测试滚动到列表顶部和底部，验证行为正常
    - _需求: 5.1, 5.2, 5.4_

  - [~] 5.3 生命周期测试
    - 切换到其他 Fragment 再返回，验证按钮恢复显示状态
    - 切换资产类型（资产/负债/借出），验证按钮状态保持
    - 旋转屏幕，验证功能正常
    - _需求: 4.2, 4.3_

  - [~] 5.4 无障碍功能测试
    - 启用 TalkBack
    - 验证按钮在隐藏状态下仍可通过触摸探索访问
    - 验证按钮的 contentDescription 正常工作
    - _需求: 6.1, 6.2, 6.3_

- [ ] 6. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，询问用户是否有问题

## 注意事项

- 本功能不涉及数据库操作，无需触发 WebDAV 自动同步
- 使用 `ViewPropertyAnimator` 而非 `ObjectAnimator` 以获得更好的性能
- 动画使用硬件加速，确保流畅体验
- 按钮使用 `translationY` 而非 `visibility` 属性，保持无障碍兼容性
- 所有动画操作在主线程执行，无需后台线程处理

## 技术细节

### 动画参数
- **隐藏动画**: 
  - 平移距离 = 容器高度 + 底部边距
  - 插值器: AccelerateInterpolator（加速）
  - 持续时间: 200ms

- **显示动画**:
  - 平移距离 = 0（回到原位）
  - 插值器: DecelerateInterpolator（减速）
  - 持续时间: 200ms

### 滚动阈值
- 阈值: 5 像素
- 目的: 过滤微小滚动，防止抖动触发动画

### 状态管理
- `isFabVisible`: 跟踪按钮当前显示状态
- 防止重复动画执行
- 在 `onResume` 中重置为显示状态
