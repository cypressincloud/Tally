# Implementation Plan: 日期选择器动画功能

## Overview

为 DetailsFragment 和 StatsFragment 的"年月日"选择器（RadioGroup）添加平滑的滑块动画效果。通过创建自定义 View 组件实现动画指示器，提升用户在切换时间维度时的视觉体验和交互流畅度。

## Tasks

- [x] 1. 创建自定义动画指示器 View 组件
  - 创建 `AnimatedTabIndicator.java` 自定义 View 类
  - 实现圆角矩形背景绘制（8dp 圆角，app_yellow 主题色）
  - 添加 2dp 阴影效果（elevation）
  - 设置 `importantForAccessibility` 为 "no" 以支持无障碍功能
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 9.3, 10.1_

- [x] 2. 实现动画指示器的位置计算逻辑
  - [x] 2.1 实现测量和布局逻辑
    - 重写 `onMeasure()` 和 `onLayout()` 方法
    - 动态计算每个选项的实际宽度和位置
    - 支持响应式布局适配（4.5-7 英寸屏幕）
    - _Requirements: 4.1, 4.4_
  
  - [x] 2.2 实现初始位置设置方法
    - 创建 `setSelectedPosition(int position, boolean animated)` 公开方法
    - 根据 position 参数计算目标位置（0=年, 1=月, 2=周）
    - 初始显示时不执行动画（animated=false）
    - _Requirements: 3.1, 3.4, 7.3_

- [x] 3. 实现平滑切换动画
  - [x] 3.1 配置动画参数
    - 使用 ObjectAnimator 实现属性动画
    - 设置动画时长为 300 毫秒
    - 使用 DecelerateInterpolator 实现减速缓动效果
    - 启用硬件加速渲染
    - _Requirements: 2.2, 2.3, 6.2, 6.3, 9.2_
  
  - [x] 3.2 实现位置和宽度动画
    - 同时动画化 translationX（水平位置）和 scaleX（宽度）属性
    - 处理动画执行期间的连续点击（立即响应最新目标位置）
    - _Requirements: 2.1, 2.4, 2.5_
  
  - [x] 3.3 添加系统动画设置支持
    - 检测系统"减少动画"设置
    - 当启用"减少动画"时，缩短动画时长至 100 毫秒或禁用
    - _Requirements: 10.3_

- [x] 4. 创建自定义 RadioGroup 包装组件
  - 创建 `AnimatedRadioGroup.java` 自定义 ViewGroup 类
  - 继承 FrameLayout，内部包含 RadioGroup 和 AnimatedTabIndicator
  - 实现 RadioGroup 选中状态监听
  - 自动触发动画指示器位置更新
  - _Requirements: 7.1, 7.2_

- [x] 5. 集成到 DetailsFragment
  - [x] 5.1 更新布局文件
    - 修改 `fragment_details.xml`
    - 将现有 RadioGroup (rg_time_mode) 替换为 AnimatedRadioGroup
    - 保持现有的 ID 和样式配置
    - _Requirements: 7.2_
  
  - [x] 5.2 更新 Fragment 代码
    - 修改 `DetailsFragment.java` 中的 RadioGroup 引用
    - 在 `onCreateView()` 中初始化动画指示器
    - 根据 SharedPreferences 中的 `time_mode` 设置初始位置
    - 在 RadioGroup 选中状态变化时添加触觉反馈（CLOCK_TICK）
    - _Requirements: 3.2, 3.3, 5.1, 5.2, 8.1, 8.2_

- [x] 6. 集成到 StatsFragment
  - [x] 6.1 更新布局文件
    - 修改 `fragment_stats.xml`
    - 将现有 RadioGroup (rg_time_scope) 替换为 AnimatedRadioGroup
    - 保持现有的 ID 和样式配置
    - _Requirements: 7.2, 7.4_
  
  - [x] 6.2 更新 Fragment 代码
    - 修改 `StatsFragment.java` 中的 RadioGroup 引用
    - 在 `onCreateView()` 中初始化动画指示器
    - 根据当前 `currentMode` 设置初始位置
    - 在 RadioGroup 选中状态变化时添加触觉反馈（CLOCK_TICK）
    - _Requirements: 3.2, 3.3, 5.1, 5.2, 8.1, 8.2, 7.4_

- [x] 7. 处理屏幕方向变化
  - 在 AnimatedTabIndicator 中重写 `onConfigurationChanged()`
  - 屏幕方向改变时重新计算并立即定位到正确位置（无动画）
  - 确保在不同屏幕密度下保持一致的视觉比例
  - _Requirements: 4.2, 4.3_

- [x] 8. 添加异常处理和降级逻辑
  - 在 AnimatedRadioGroup 中添加 try-catch 块
  - 当动画功能出现异常时，降级为无动画的标准 RadioGroup 行为
  - 记录异常日志但不影响用户使用
  - _Requirements: 8.4_

- [x] 9. Checkpoint - 功能验证
  - 在 DetailsFragment 和 StatsFragment 中测试动画效果
  - 验证初始状态显示正确
  - 验证切换动画流畅（60 FPS）
  - 验证触觉反馈正常工作
  - 验证屏幕旋转后动画指示器位置正确
  - 验证在低端设备（API 21+）上运行流畅
  - 确认所有测试通过，如有问题请向用户反馈

- [ ]* 10. 编写单元测试
  - [ ]* 10.1 测试 AnimatedTabIndicator 位置计算
    - 测试不同屏幕宽度下的位置计算准确性
    - 测试边界条件（position = 0, 1, 2）
    - _Requirements: 4.1_
  
  - [ ]* 10.2 测试动画参数配置
    - 验证动画时长为 300ms
    - 验证使用 DecelerateInterpolator
    - 验证硬件加速已启用
    - _Requirements: 2.2, 2.3, 6.2_
  
  - [ ]* 10.3 测试系统动画设置响应
    - 模拟"减少动画"系统设置
    - 验证动画时长缩短或禁用
    - _Requirements: 10.3_

- [ ]* 11. 编写 UI 自动化测试
  - [ ]* 11.1 测试 DetailsFragment 动画
    - 测试点击"年"、"月"、"周"选项时动画正常执行
    - 测试快速连续点击时动画响应正确
    - _Requirements: 2.1, 2.5_
  
  - [ ]* 11.2 测试 StatsFragment 动画
    - 测试点击"年"、"月"、"周"选项时动画正常执行
    - 测试屏幕旋转后动画指示器位置正确
    - _Requirements: 2.1, 4.2_
  
  - [ ]* 11.3 测试无障碍功能
    - 启用 TalkBack 测试焦点导航顺序
    - 验证动画指示器不干扰屏幕阅读器
    - 验证 RadioButton 选项保持完整的可访问性标签
    - _Requirements: 10.1, 10.2, 10.4_

- [x] 12. Final Checkpoint - 完整验收
  - 在真实设备上测试所有功能
  - 验证 Material Design 合规性
  - 验证向后兼容性（不影响现有功能）
  - 验证性能要求（60 FPS，低端设备流畅）
  - 确认所有需求已满足，如有问题请向用户反馈

## Notes

- 任务标记 `*` 为可选任务，可跳过以加快 MVP 交付
- 每个任务都引用了具体的需求编号以确保可追溯性
- Checkpoint 任务用于增量验证，确保功能正确性
- 自定义 View 组件设计为可复用，两个模块共享相同实现
- 动画实现使用 ObjectAnimator 而非帧动画，确保性能
- 异常处理确保功能降级不影响用户体验
