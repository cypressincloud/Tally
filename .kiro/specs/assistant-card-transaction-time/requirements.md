# 需求文档 - AI记账助手卡片显示账单时间

## 简介

本功能为AI记账助手的草稿卡片添加账单时间显示功能。当AI助手解析用户输入并生成账单草稿卡片时,用户能够直观地看到系统识别到的账单时间,以便确认时间是否正确。

## 术语表

- **AI_Chat_Activity**: AI聊天界面(AiChatActivity),用户通过文字、语音或图片与AI助手交互的界面
- **Draft_Card**: 草稿卡片,AI助手解析用户输入后生成的账单草稿界面(item_ai_draft_card.xml)
- **Transaction_Draft**: 账单草稿数据模型(TransactionDraft),包含type、amount、category、note、date等字段
- **Transaction_Time**: 账单时间,记录交易发生的时间戳,存储在TransactionDraft的date字段中(long类型)
- **Time_Display_Field**: 时间显示字段,卡片上用于显示账单时间的UI组件(需要新增)
- **User**: 应用的最终用户,需要查看和确认AI识别的账单信息

## 需求

### 需求 1: 在草稿卡片上显示账单时间

**用户故事:** 作为用户,我希望在AI记账助手生成的草稿卡片上看到账单的记录时间,以便我能够确认这笔交易的时间是否正确。

#### 验收标准

1. WHEN AI_Chat_Activity生成Draft_Card, THE Time_Display_Field SHALL显示Transaction_Draft.date的格式化文本
2. THE Time_Display_Field SHALL使用"MM-dd HH:mm"格式显示Transaction_Time (例如: "04-28 14:30")
3. THE Time_Display_Field SHALL显示在卡片顶部标题行,位于"账单"文字和状态文字之间
4. THE Time_Display_Field SHALL使用灰色文字颜色(?android:attr/textColorSecondary)以区别于主要信息
5. THE Time_Display_Field SHALL使用14sp字体大小,与状态文字保持一致

### 需求 2: 时间显示的视觉设计

**用户故事:** 作为用户,我希望时间显示清晰易读且不影响卡片的整体布局,以便快速获取时间信息。

#### 验收标准

1. THE Time_Display_Field SHALL位于卡片顶部的LinearLayout中
2. THE Time_Display_Field SHALL在"账单"标题(tv_draft_index)之后显示
3. THE Time_Display_Field SHALL使用layout_marginStart="8dp"与标题保持适当间距
4. THE Time_Display_Field SHALL使用wrap_content宽度,不占用过多空间
5. THE Time_Display_Field SHALL不影响删除按钮(btn_remove)的位置和功能

### 需求 3: 兼容现有的数据流

**用户故事:** 作为开发者,我希望新功能能够兼容现有的数据流和UI逻辑,以便不破坏现有的功能。

#### 验收标准

1. WHEN DraftCardController.bind()方法执行, THE Time_Display_Field SHALL从model.draft.date读取时间戳
2. WHEN model.draft.date为0或无效值, THE Time_Display_Field SHALL显示当前时间
3. THE Time_Display_Field SHALL为只读显示,不提供编辑功能
4. WHEN User保存草稿, THE Transaction_Draft.date字段 SHALL保持原始值不变
5. THE Time_Display_Field SHALL不影响现有的表单编辑和保存逻辑

### 需求 4: 保持UI一致性

**用户故事:** 作为用户,我希望时间显示的样式与卡片上其他信息保持一致,以便获得统一的视觉体验。

#### 验收标准

1. THE Time_Display_Field SHALL使用TextView组件
2. THE Time_Display_Field SHALL使用与tv_draft_status相同的文本大小(14sp)
3. THE Time_Display_Field SHALL使用?android:attr/textColorSecondary颜色
4. THE Time_Display_Field SHALL在卡片收起(summary模式)和展开(editor模式)时都保持可见
5. THE Time_Display_Field SHALL与卡片的整体Material Design风格保持一致

## 技术约束

1. 时间格式化必须使用SimpleDateFormat类,格式为"MM-dd HH:mm"
2. 时间显示字段必须使用TextView组件(只读显示)
3. 必须在主线程中更新UI组件
4. 不得修改TransactionDraft的date字段类型(保持long类型)
5. 必须保持与现有DraftCardController.bind()方法的兼容性
6. 布局修改必须在item_ai_draft_card.xml中完成
7. 数据绑定逻辑必须在AiChatActivity的DraftCardController类中实现

## 非功能性需求

1. 时间格式化操作必须在主线程中完成,避免UI卡顿
2. 时间显示必须使用设备的默认Locale设置
3. 代码修改必须遵循项目现有的Java代码规范
4. 必须保持向后兼容,不影响现有的AI记账功能
5. UI修改必须遵循Material Design设计规范
6. 时间显示不应占用过多屏幕空间,保持卡片的紧凑性
