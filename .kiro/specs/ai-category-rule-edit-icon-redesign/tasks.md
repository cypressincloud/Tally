# Implementation Plan: AI分类关键字规则编辑图标重绘

## Overview

本实现计划将替换AI分类关键字规则界面中的编辑图标，从系统默认图标（`@android:drawable/ic_menu_edit`）升级为符合Material Design规范的自定义矢量图标。实现包括创建矢量图标资源、更新布局文件、添加字符串和颜色资源，以及优化可点击区域和交互反馈。

## Tasks

- [x] 1. 创建矢量图标资源文件
  - 在 `app/src/main/res/drawable/` 目录创建 `ic_edit_rule.xml` 文件
  - 使用 Material Design 的编辑图标路径数据（铅笔形状）
  - 设置视口尺寸为 24dp x 24dp
  - 使用 `#888888` 作为默认填充颜色
  - 确保支持 `android:tint` 属性进行颜色覆盖
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 2. 添加颜色资源定义
  - [x] 2.1 在 `app/src/main/res/values/colors.xml` 中添加 `icon_secondary` 颜色定义
    - 定义浅色模式下的颜色值为 `#888888`
    - _Requirements: 7.3, 7.4_
  
  - [x] 2.2 在 `app/src/main/res/values-night/colors.xml` 中添加深色模式颜色定义
    - 如果 `values-night` 目录不存在，先创建该目录
    - 定义深色模式下的颜色值为 `#AAAAAA`
    - _Requirements: 7.1, 7.2, 7.5_

- [x] 3. 添加字符串资源
  - 在 `app/src/main/res/values/strings.xml` 中添加 `edit_rule` 字符串资源
  - 设置值为 "编辑规则"
  - 用于无障碍支持的 contentDescription
  - _Requirements: 9.2, 9.3_

- [x] 4. 更新规则卡片布局文件
  - 修改 `app/src/main/res/layout/item_category_rule.xml` 中的 ImageView 配置
  - 将 `android:src` 从 `@android:drawable/ic_menu_edit` 改为 `@drawable/ic_edit_rule`
  - 将 `android:tint` 从硬编码 `#888888` 改为 `@color/icon_secondary`
  - 将 `android:contentDescription` 从硬编码文本改为 `@string/edit_rule`
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 7.3, 9.2_

- [x] 5. Checkpoint - 验证图标显示
  - 在 Android Studio 中预览布局文件，确认图标正确显示
  - 在模拟器或真机上运行应用，测试图标显示效果
  - 测试深色模式下的图标颜色是否正确
  - 确认无障碍功能（TalkBack）能正确朗读"编辑规则"
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- 本任务不包含自动化测试，因为这是纯 UI 资源更新
- 图标设计遵循 Material Design 规范，确保与应用整体风格一致
- 深色模式适配通过 `values-night` 资源目录实现
- 所有硬编码文本和颜色值已替换为资源引用，提升可维护性
- 根据用户要求，不添加点击涟漪效果和可点击区域优化
