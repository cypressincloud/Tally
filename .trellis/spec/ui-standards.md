# UI 规范

## 架构模式

### MVVM 架构

- **View**: Activity/Fragment，负责显示 UI
- **ViewModel**: 管理 UI 相关数据和业务逻辑
- **Model**: 数据层，包括 Repository 和数据源

### 职责划分

- Activity/Fragment 只负责 UI 展示和用户交互
- ViewModel 处理业务逻辑和数据转换
- 数据库操作通过 Repository 封装

## Fragment 规范

### 创建方式

```java
public class RecordFragment extends Fragment {
    public static RecordFragment newInstance() {
        return new RecordFragment();
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_record, container, false);
    }
}
```

### 生命周期

- 在 `onCreateView()` 中初始化视图
- 在 `onViewCreated()` 中设置监听器
- 在 `onDestroyView()` 中清理资源

## Activity 规范

### 使用场景

- 独立功能页面（设置、详情等）
- 需要独立任务栈的页面
- 主界面使用 Fragment 切换

### 生命周期

- 在 `onCreate()` 中初始化
- 避免在 `onResume()` 中执行耗时操作
- 在 `onDestroy()` 中清理资源

## ViewModel 规范

### 数据管理

```java
public class FinanceViewModel extends AndroidViewModel {
    private final MutableLiveData<List<Transaction>> transactions;
    
    public LiveData<List<Transaction>> getTransactions() {
        return transactions;
    }
    
    public void loadTransactions() {
        // 后台加载数据
    }
}
```

### LiveData 使用

- 使用 LiveData 暴露数据给 View
- 避免在 ViewModel 中持有 View 引用
- 使用 MediatorLiveData 组合多个数据源

## Material Design

### 组件使用

- 使用 MaterialButton 替代 Button
- 使用 TextInputLayout 包装 EditText
- 使用 RecyclerView 显示列表
- 使用 FloatingActionButton 作为主操作按钮

### 主题和样式

- 统一使用 Material Design 主题
- 定义全局颜色和尺寸资源
- 支持深色模式

### 动画和过渡

- 使用 Material Motion 过渡动画
- 列表项使用 ripple 效果
- 页面切换使用淡入淡出

## 布局规范

### 布局选择

- 简单布局使用 LinearLayout
- 复杂布局使用 ConstraintLayout
- 列表使用 RecyclerView

### 性能优化

- 减少布局层级，避免过度嵌套
- 使用 ViewStub 延迟加载
- 使用 merge 标签减少层级

### 适配规范

- 使用 dp 作为尺寸单位
- 使用 sp 作为字体单位
- 提供不同屏幕密度的资源

## 用户体验

### 加载状态

- 显示加载进度条
- 提供加载失败提示
- 支持下拉刷新

### 错误处理

- 友好的错误提示
- 提供重试机制
- 记录错误日志

### 交互反馈

- 按钮点击有视觉反馈
- 操作成功显示 Toast 或 Snackbar
- 重要操作需要确认对话框
