# AI 记账配置优化 - 独立模型配置

**日期**: 2026-04-30  
**类型**: 功能优化  
**状态**: ✅ 已完成

## 需求背景

原有的 AI 记账配置中，文本、视觉、音频三个模型共用同一套 Base URL 和 API Key。这限制了用户的灵活性，无法为不同模型使用不同的 API 服务商。

## 优化目标

允许用户为"文本"、"视觉"、"音频"三个模型分别配置独立的 Base URL 和 API Key，未单独配置时自动使用默认配置（文本模型配置）。

## 实现方案

### 1. 数据模型优化 (AiConfig.java)

**新增字段**：
- 文本模型独立配置：`textBaseUrl`, `textApiKey`
- 视觉模型独立配置：`visionBaseUrl`, `visionApiKey`
- 音频模型独立配置：`audioBaseUrl`, `audioApiKey`

**向后兼容**：
- 保留原有的 `baseUrl` 和 `apiKey` 作为默认配置
- 旧版本数据可以无缝迁移

**配置优先级**：
```
文本模型：textBaseUrl/textApiKey → baseUrl/apiKey
视觉模型：visionBaseUrl/visionApiKey → textBaseUrl/textApiKey → baseUrl/apiKey
音频模型：audioBaseUrl/audioApiKey → textBaseUrl/textApiKey → baseUrl/apiKey
```

**新增方法**：
- `getEffectiveTextBaseUrl()` / `getEffectiveTextApiKey()`
- `getEffectiveVisionBaseUrl()` / `getEffectiveVisionApiKey()`
- `getEffectiveAudioBaseUrl()` / `getEffectiveAudioApiKey()`

### 2. UI 界面优化 (activity_ai_setting.xml)

**布局结构调整**：

1. **默认连接信息卡片**
   - Base URL（默认）
   - API Key（默认）
   - 说明：未单独配置时，所有模型将使用此连接信息

2. **文本模型配置卡片**
   - 模型名称（必填）
   - 独立接口地址（可选，留空则使用默认）
   - 独立 API Key（可选，留空则使用默认）
   - 状态显示

3. **视觉模型配置卡片**
   - 模型名称（可选）
   - 独立接口地址（可选，留空则使用文本模型配置）
   - 独立 API Key（可选，留空则使用文本模型配置）
   - 状态显示

4. **音频模型配置卡片**
   - 模型名称（可选）
   - 独立接口地址（可选，留空则使用文本模型配置）
   - 独立 API Key（可选，留空则使用文本模型配置）
   - 状态显示

### 3. Activity 逻辑优化 (AiSettingActivity.java)

**新增控件绑定**：
- 为每个模型添加独立的 Base URL 和 API Key 输入框
- 更新数据加载和保存逻辑

**配置验证优化**：
- 检查文本模型是否有可用的连接信息（独立配置或默认配置）
- 保存时保留未变更模型的测试结果

**测试连接优化**：
- 使用每个模型的有效配置进行测试
- 独立显示每个模型的测试结果

### 4. 客户端逻辑优化 (AiAccountingClient.java)

**方法重构**：

原有方法（使用共享配置）：
- `chat()` → 保留，内部调用新方法
- `probeVision()` → 保留，内部调用新方法
- `openConnection()` → 保留，内部调用新方法
- `resolveUrl()` → 保留，内部调用新方法

新增方法（支持独立配置）：
- `chatWithEndpoint(model, baseUrl, apiKey, systemPrompt, userMessage)`
- `probeVisionWithEndpoint(prompt, imageBytes, mimeType, baseUrl, apiKey)`
- `openConnectionWithEndpoint(url, apiKey, jsonContentType)`
- `resolveUrlWithBase(baseUrl, endpoint)`

**调用更新**：
- `parseText()` → 使用 `getEffectiveTextBaseUrl()` 和 `getEffectiveTextApiKey()`
- `parseVisionImage()` → 使用 `getEffectiveVisionBaseUrl()` 和 `getEffectiveVisionApiKey()`
- `transcribeAudio()` → 使用 `getEffectiveAudioBaseUrl()` 和 `getEffectiveAudioApiKey()`
- `testConfiguration()` → 为每个模型使用对应的有效配置

## 使用场景示例

### 场景 1：所有模型使用同一服务商
```
默认配置：
- Base URL: https://api.deepseek.com
- API Key: sk-xxx

文本模型：deepseek-chat（使用默认配置）
视觉模型：deepseek-vision（使用默认配置）
音频模型：（未配置）
```

### 场景 2：视觉模型使用不同服务商
```
默认配置：
- Base URL: https://api.deepseek.com
- API Key: sk-deepseek-xxx

文本模型：deepseek-chat（使用默认配置）

视觉模型：gpt-4o
- 独立 Base URL: https://api.openai.com
- 独立 API Key: sk-openai-xxx

音频模型：（未配置）
```

### 场景 3：三个模型使用不同服务商
```
默认配置：
- Base URL: https://api.deepseek.com
- API Key: sk-deepseek-xxx

文本模型：deepseek-chat
- 独立 Base URL: https://api.deepseek.com
- 独立 API Key: sk-deepseek-xxx

视觉模型：gpt-4o
- 独立 Base URL: https://api.openai.com
- 独立 API Key: sk-openai-xxx

音频模型：whisper-1
- 独立 Base URL: https://api.groq.com
- 独立 API Key: sk-groq-xxx
```

## 技术亮点

1. **向后兼容**：旧版本配置可以无缝迁移，不影响现有用户
2. **灵活配置**：支持从完全共享到完全独立的各种配置方式
3. **智能回退**：未配置时自动使用上级配置，减少重复输入
4. **清晰提示**：UI 上明确说明配置优先级和回退逻辑

## 测试验证

✅ 编译通过：`BUILD SUCCESSFUL`
- 无编译错误
- 无类型错误
- 方法签名正确

## 后续建议

1. **用户体验优化**：
   - 添加"复制默认配置"按钮，快速填充独立配置
   - 显示当前使用的有效配置（区分独立配置和默认配置）

2. **配置管理**：
   - 添加配置模板功能，保存常用服务商配置
   - 支持配置导入导出

3. **测试增强**：
   - 添加批量测试功能
   - 显示更详细的测试日志

## 相关文件

**修改文件**：
- `app/src/main/java/com/example/budgetapp/ai/AiConfig.java`
- `app/src/main/java/com/example/budgetapp/ai/AiAccountingClient.java`
- `app/src/main/java/com/example/budgetapp/ui/AiSettingActivity.java`
- `app/src/main/res/layout/activity_ai_setting.xml`

**影响范围**：
- AI 记账配置界面
- AI 模型调用逻辑
- 配置存储和读取

## 总结

本次优化成功实现了 AI 模型的独立配置功能，用户现在可以为不同的模型使用不同的 API 服务商，大大提升了配置的灵活性。同时保持了向后兼容性，不影响现有用户的使用体验。
