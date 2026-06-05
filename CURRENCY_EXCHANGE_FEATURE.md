# 货币汇率转换功能实现文档

## 功能概述

实现了货币汇率自动转换功能，允许用户在开启"只显示默认货币单位"后，将所有资产、负债、借出的金额按实时汇率转换为默认货币显示。

## 技术方案

### API选择
- 使用免费的 **exchangerate-api.com** API
- API地址：`https://api.exchangerate-api.com/v4/latest/{base_currency}`
- 支持50+种货币的实时汇率转换
- 无需注册和API密钥

### 缓存机制
- 24小时缓存有效期，减少API调用频率
- 使用 SharedPreferences 存储汇率数据
- 缓存包含：汇率JSON、最后更新时间、基础货币代码

## 实现内容

### 1. ExchangeRateManager.java
**位置**: `app/src/main/java/com/example/budgetapp/util/ExchangeRateManager.java`

**功能**:
- `getExchangeRate()`: 获取两种货币之间的汇率
- `convertAmounts()`: 批量转换多种货币金额到目标货币
- 异步网络请求，不阻塞主线程
- 自动缓存管理

**关键方法**:
```java
// 获取单个汇率
public void getExchangeRate(String fromCurrency, String toCurrency, ExchangeRateCallback callback)

// 批量转换金额
public void convertAmounts(Map<String, Double> amounts, String targetCurrency, ConvertCallback callback)

// 清除缓存
public void clearCache()
```

### 2. CurrencySettingsActivity.java
**位置**: `app/src/main/java/com/example/budgetapp/ui/CurrencySettingsActivity.java`

**修改内容**:
- 添加"只显示默认货币单位"开关 (`switch_single_currency`)
- 保存开关状态到 SharedPreferences (`single_currency_mode`)
- 提供用户友好的提示信息

**SharedPreferences 键值**:
- Key: `single_currency_mode`
- Prefs: `app_prefs`
- Type: Boolean

### 3. AssetsFragment.java
**位置**: `app/src/main/java/com/example/budgetapp/ui/AssetsFragment.java`

**修改内容**:
- 在 `updateUI()` 方法中添加统一货币模式逻辑
- 实现 `getCurrencyCode()` 方法，将货币符号转换为货币代码
- 支持50+种货币符号到代码的映射
- 异步调用汇率API并更新UI
- 显示加载状态和错误提示

**货币符号映射示例**:
- "¥" → "CNY" (人民币)
- "$" → "USD" (美元)
- "€" → "EUR" (欧元)
- "£" → "GBP" (英镑)
- "HK$" → "HKD" (港币)
- ... 等50+种货币

### 4. AndroidManifest.xml
**修改内容**:
- 确认已有 `<uses-permission android:name="android.permission.INTERNET" />` 权限

## 用户使用流程

1. **进入设置**: 主页 → 设置 → 货币单位设置
2. **开启多币种**: 打开"开启多种货币单位"开关
3. **设置默认货币**: 点击"设置默认货币"，选择默认货币（如CNY）
4. **开启统一显示**: 打开"只显示默认货币单位"开关
5. **查看效果**: 返回资产页面，所有金额将自动转换为默认货币显示

## 技术细节

### 异步处理
```java
// 在后台线程请求API
new Thread(() -> {
    // API调用
    // ...
    
    // 在主线程更新UI
    getActivity().runOnUiThread(() -> {
        tvTotalAssets.setText(result);
    });
}).start();
```

### 错误处理
- 网络错误: 显示"网络错误"提示
- API调用失败: 显示"计算中..."或"-"
- 解析错误: 回退到默认货币

### 性能优化
- 24小时缓存机制，避免频繁API调用
- 批量转换，一次API调用获取所有需要的汇率
- 异步处理，不阻塞UI线程

## 支持的货币列表

### 主要货币
- CNY (¥) - 人民币
- USD ($) - 美元
- EUR (€) - 欧元
- GBP (£) - 英镑
- HKD (HK$) - 港币
- TWD (NT$) - 新台币
- JPY (JP¥) - 日元
- KRW (₩) - 韩元

### 亚洲货币
- INR (₹) - 印度卢比
- THB (฿) - 泰铢
- VND (₫) - 越南盾
- PHP (₱) - 菲律宾比索
- SGD (S$) - 新加坡元
- MYR (RM) - 马来西亚林吉特
- IDR (Rp) - 印尼盾

### 欧洲货币
- CHF - 瑞士法郎
- SEK (kr) - 瑞典克朗
- NOK (kr) - 挪威克朗
- DKK (kr) - 丹麦克朗
- PLN (zł) - 波兰兹罗提
- CZK (Kč) - 捷克克朗
- HUF (Ft) - 匈牙利福林
- ... 等

### 其他地区
- CAD (C$) - 加元
- AUD (A$) - 澳元
- NZD (NZ$) - 新西兰元
- BRL (R$) - 巴西雷亚尔
- RUB (₽) - 俄罗斯卢布
- TRY (₺) - 土耳其里拉
- ZAR (R) - 南非兰特
- ... 等

**总计**: 支持50+种货币

## 已知限制

1. **网络依赖**: 需要联网才能获取最新汇率
2. **缓存时效**: 24小时内使用缓存，可能不是最新汇率
3. **API限制**: 免费API可能有调用频率限制
4. **精度问题**: 浮点数计算可能有精度损失

## 未来优化方向

1. **离线模式**: 预加载常用货币汇率，支持离线查看
2. **汇率源切换**: 支持多个汇率API源，提高可靠性
3. **历史汇率**: 支持查看历史汇率数据
4. **手动刷新**: 提供手动刷新汇率的按钮
5. **汇率走势**: 显示货币汇率走势图表

## 测试建议

### 功能测试
1. 开启/关闭"只显示默认货币单位"开关
2. 切换不同的默认货币
3. 添加不同货币的资产账户
4. 检查总资产、负债、借出的转换是否正确

### 异常测试
1. 无网络情况下的表现
2. API调用失败的错误处理
3. 缓存过期后的自动刷新
4. 不支持的货币符号处理

### 性能测试
1. 首次加载时间
2. 缓存命中率
3. 大量账户时的转换速度
4. 内存占用情况

## 编译状态

✅ 编译成功 (2026-06-05)
- 所有Java文件编译通过
- 无语法错误
- 依赖关系正确

## 相关文件

### 新增文件
- `app/src/main/java/com/example/budgetapp/util/ExchangeRateManager.java`

### 修改文件
- `app/src/main/java/com/example/budgetapp/ui/CurrencySettingsActivity.java`
- `app/src/main/java/com/example/budgetapp/ui/AssetsFragment.java`

### 配置文件
- `app/src/main/AndroidManifest.xml` (确认有网络权限)

## 版本历史

### v1.0 (2026-06-05)
- ✅ 实现基础汇率转换功能
- ✅ 支持50+种货币
- ✅ 24小时缓存机制
- ✅ 异步API调用
- ✅ 友好的加载状态和错误提示

---

**最后更新**: 2026-06-05
**状态**: 已完成 ✅
