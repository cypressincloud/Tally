# 代码规范

## Java 编码标准

### 命名规范

- **类名**: 使用 PascalCase，如 `TransactionDao`, `FinanceViewModel`
- **方法名**: 使用 camelCase，如 `getAllTransactions()`, `updateBalance()`
- **变量名**: 使用 camelCase，如 `totalAmount`, `accountId`
- **常量**: 使用 UPPER_SNAKE_CASE，如 `MAX_RETRY_COUNT`, `DEFAULT_CURRENCY`

### 代码组织

- 每个类文件只包含一个公共类
- 类成员顺序：常量 → 静态变量 → 实例变量 → 构造函数 → 方法
- 方法长度不超过 50 行，复杂逻辑需拆分
- 使用有意义的变量名，避免单字母变量（循环除外）

### Android 特定规范

- Activity 生命周期方法按标准顺序排列
- Fragment 使用 newInstance() 工厂方法创建
- 避免在 Activity/Fragment 中直接操作数据库
- 使用 ViewModel 管理 UI 相关数据

## 线程规范

### 后台操作

- 所有数据库操作必须在后台线程执行
- 使用 `Executors` 或 `AsyncTask` 处理耗时操作
- 网络请求必须异步执行

### UI 更新

- UI 更新必须在主线程执行
- 使用 `runOnUiThread()` 或 `Handler` 切换到主线程
- LiveData 自动处理线程切换

## 异常处理

- 捕获具体异常类型，避免空 catch 块
- 数据库操作需处理 SQLiteException
- 文件操作需处理 IOException
- 记录异常日志便于调试

## 注释规范

- 公共 API 必须有 Javadoc 注释
- 复杂逻辑添加行内注释说明
- TODO 注释格式：`// TODO: 描述待办事项`
- 避免无意义的注释

## 资源管理

- 及时关闭 Cursor、InputStream 等资源
- 使用 try-with-resources 自动管理资源
- 避免内存泄漏，注意 Context 引用
