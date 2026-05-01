# 需求文档 - 资产模块滚动隐藏按钮

## 简介

资产模块右下角的两个浮动按钮（添加资产按钮和转移资产按钮）在资产列表过多时会遮挡用户视线，影响列表内容的查看。本功能通过监听用户的滚动手势，在用户上滑浏览列表时自动隐藏按钮，下滑时重新显示按钮，从而提升用户体验。

## 术语表

- **Assets_Fragment**: 资产模块的主界面 Fragment，负责展示资产列表和管理浮动按钮
- **FAB_Container**: 包含两个浮动按钮的 LinearLayout 容器（fab_add_asset 和 fab_transfer_asset）
- **Assets_RecyclerView**: 显示资产列表的 RecyclerView 组件（rv_assets_list）
- **Scroll_Listener**: RecyclerView 的滚动监听器，用于检测滚动方向和状态
- **Animation_Handler**: 负责执行按钮显示/隐藏动画的处理器

## 需求

### 需求 1: 监听资产列表滚动事件

**用户故事:** 作为用户，我希望系统能够感知我的滚动操作，以便根据滚动方向自动调整按钮显示状态。

#### 验收标准

1. WHEN Assets_RecyclerView 发生滚动，THEN THE Scroll_Listener SHALL 捕获滚动事件
2. THE Scroll_Listener SHALL 计算滚动的垂直距离（dy）
3. THE Scroll_Listener SHALL 识别滚动方向（上滑或下滑）
4. WHEN 滚动距离的绝对值小于 5 像素，THEN THE Scroll_Listener SHALL 忽略该滚动事件（防止微小抖动触发动画）

### 需求 2: 上滑时隐藏浮动按钮

**用户故事:** 作为用户，当我向上滑动查看更多资产时，我希望浮动按钮自动隐藏，以便我能看清被遮挡的列表内容。

#### 验收标准

1. WHEN 用户向上滑动 Assets_RecyclerView（dy > 5），THEN THE Animation_Handler SHALL 隐藏 FAB_Container
2. THE Animation_Handler SHALL 使用平移动画（translationY）将按钮向下移出屏幕
3. THE Animation_Handler SHALL 设置动画持续时间为 200 毫秒
4. THE Animation_Handler SHALL 使用加速插值器（AccelerateInterpolator）使动画更自然
5. WHEN 按钮已处于隐藏状态，THEN THE Animation_Handler SHALL 不重复执行隐藏动画

### 需求 3: 下滑时显示浮动按钮

**用户故事:** 作为用户，当我向下滑动返回列表顶部或需要操作按钮时，我希望浮动按钮自动显示，以便我能快速访问添加和转移功能。

#### 验收标准

1. WHEN 用户向下滑动 Assets_RecyclerView（dy < -5），THEN THE Animation_Handler SHALL 显示 FAB_Container
2. THE Animation_Handler SHALL 使用平移动画（translationY）将按钮从屏幕外移回原位
3. THE Animation_Handler SHALL 设置动画持续时间为 200 毫秒
4. THE Animation_Handler SHALL 使用减速插值器（DecelerateInterpolator）使动画更自然
5. WHEN 按钮已处于显示状态，THEN THE Animation_Handler SHALL 不重复执行显示动画

### 需求 4: 保持按钮初始状态

**用户故事:** 作为用户，当我首次进入资产模块或切换资产类型时，我希望浮动按钮默认显示，以便我能立即看到可用的操作选项。

#### 验收标准

1. WHEN Assets_Fragment 初始化完成，THEN THE FAB_Container SHALL 处于完全可见状态
2. WHEN 用户切换资产类型（资产/负债/借出），THEN THE FAB_Container SHALL 保持当前的显示/隐藏状态
3. WHEN 用户从其他 Fragment 返回 Assets_Fragment，THEN THE FAB_Container SHALL 恢复为显示状态

### 需求 5: 处理边界情况

**用户故事:** 作为用户，我希望按钮的显示/隐藏行为在各种场景下都能正常工作，不会出现异常或卡顿。

#### 验收标准

1. WHEN Assets_RecyclerView 的内容不足以滚动（列表项少于屏幕高度），THEN THE FAB_Container SHALL 保持显示状态
2. WHEN 用户快速连续滚动，THEN THE Animation_Handler SHALL 取消未完成的动画并立即执行新动画
3. WHEN Assets_Fragment 被销毁，THEN THE Scroll_Listener SHALL 被正确移除以防止内存泄漏
4. WHEN 用户滚动到列表顶部或底部触发边界效果，THEN THE Scroll_Listener SHALL 正常处理滚动事件

### 需求 6: 保持无障碍功能兼容性

**用户故事:** 作为使用辅助功能的用户，我希望浮动按钮的隐藏/显示不会影响我通过 TalkBack 等工具访问按钮功能。

#### 验收标准

1. WHEN FAB_Container 被隐藏，THEN THE FAB_Container SHALL 保持可访问性属性不变（不修改 importantForAccessibility）
2. THE FAB_Container SHALL 保留原有的 contentDescription 属性
3. WHEN 使用 TalkBack 导航到按钮区域，THEN THE 用户 SHALL 能够通过触摸探索找到按钮（即使视觉上已隐藏）
