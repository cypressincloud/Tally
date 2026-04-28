# 设计文档 - AI分类关键字规则

## 概述

本功能为AI记账系统添加关键字规则管理能力，允许用户自定义关键字到分类的映射规则。当AI识别账单时，系统会根据账单描述中的关键字自动应用对应的分类规则，提升分类的准确性和一致性。

### 设计目标

- 提供直观的规则管理界面，支持规则的增删改查
- 实现高效的关键字匹配算法，支持不区分大小写的包含匹配
- 确保规则持久化存储，并集成WebDAV自动同步
- 在AI识别流程中无缝集成规则应用逻辑

### 设计原则

- **用户友好**: 提供清晰的UI和即时反馈
- **数据一致性**: 规则变更立即持久化并同步
- **性能优化**: 关键字匹配算法高效，不影响AI识别速度
- **可扩展性**: 架构设计支持未来功能扩展（如优先级、正则表达式等）


## 架构

### 系统架构图

```

                      UI Layer                                
─
  AiSettingActivity          AiCategoryRuleActivity          
  (入口卡片)                  (规则管理页面)                  
                                                            
                              
                                                             
─
│                   Business Logic Layer                       
─
           AiCategoryRuleManager                              
           (规则CRUD + 规则应用)                              
                                                             
                                     
                                                            
   CategoryManager      TransactionDraftMapper                
   (分类数据源)          (AI识别结果映射)                      
                                                              

                   Data Layer                                 
─
  SharedPreferences          BackupManager                    
  (规则持久化)                (WebDAV同步)                     
└─
```

### 组件职责

#### 1. AiSettingActivity
- 显示"AI分类关键字规则"入口卡片
- 处理卡片点击事件，启动AiCategoryRuleActivity

#### 2. AiCategoryRuleActivity
- 展示规则列表（RecyclerView）
- 处理添加、编辑、删除规则的用户交互
- 管理对话框显示和数据验证

#### 3. AiCategoryRuleManager
- 规则的增删改查操作
- 规则持久化（SharedPreferences + JSON）
- 规则应用逻辑（关键字匹配）
- 触发WebDAV自动同步

#### 4. CategoryRule (数据模型)
- 存储规则数据：关键字、分类、类型（收入/支出）

#### 5. TransactionDraftMapper
- 集成规则应用逻辑
- 在AI识别完成后应用关键字规则

### 数据流

#### 规则管理流程
```
用户操作  AiCategoryRuleActivity  AiCategoryRuleManager
                                            
                                             SharedPreferences (保存)
                                             BackupManager (同步)
```

#### 规则应用流程
```
AI识别  TransactionDraft  AiCategoryRuleManager.applyRules()
                                    
                                     匹配关键字
                                     更新分类  返回修改后的Draft
```


## 组件和接口

### 1. AiCategoryRuleActivity

**职责**: 规则管理页面，提供规则的增删改查UI

**关键方法**:
```java
public class AiCategoryRuleActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private RuleListAdapter adapter;
    private List<CategoryRule> ruleList;
    private AiCategoryRuleManager ruleManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState);
    
    // 加载规则列表
    private void loadRules();
    
    // 显示添加规则对话框
    private void showAddRuleDialog();
    
    // 显示编辑规则对话框
    private void showEditRuleDialog(CategoryRule rule, int position);
    
    // 显示删除确认对话框
    private void showDeleteConfirmDialog(CategoryRule rule, int position);
    
    // 刷新列表
    private void refreshList();
}
```

**UI组件**:
- RecyclerView: 展示规则列表
- FloatingActionButton/Button: 添加规则按钮
- TextView: 空状态提示

**布局文件**: `activity_ai_category_rule.xml`

### 2. RuleListAdapter

**职责**: RecyclerView适配器，绑定规则数据到列表项

**关键方法**:
```java
public class RuleListAdapter extends RecyclerView.Adapter<RuleListAdapter.ViewHolder> {
    private List<CategoryRule> rules;
    private OnItemClickListener listener;
    
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType);
    
    @Override
    public void onBindViewHolder(ViewHolder holder, int position);
    
    @Override
    public int getItemCount();
    
    // 点击监听接口
    public interface OnItemClickListener {
        void onEditClick(CategoryRule rule, int position);
        void onLongClick(CategoryRule rule, int position);
    }
}
```

**列表项布局**: `item_category_rule.xml`

### 3. AiCategoryRuleManager

**职责**: 规则管理核心类，处理规则的CRUD和应用逻辑

**关键方法**:
```java
public class AiCategoryRuleManager {
    private static final String PREF_NAME = "ai_category_rule_prefs";
    private static final String KEY_RULES = "rules";
    
    // 获取所有规则
    public static List<CategoryRule> getRules(Context context);
    
    // 添加规则
    public static void addRule(Context context, CategoryRule rule);
    
    // 更新规则
    public static void updateRule(Context context, int index, CategoryRule rule);
    
    // 删除规则
    public static void deleteRule(Context context, int index);
    
    // 保存规则列表
    private static void saveRules(Context context, List<CategoryRule> rules);
    
    // 应用规则到账单草稿
    public static void applyRules(Context context, TransactionDraft draft);
    
    // 关键字匹配（不区分大小写）
    private static boolean matchesKeyword(String description, String keyword);
}
```

**存储格式**:
```json
{
  "rules": [
    {
      "keyword": "咖啡",
      "category": "餐饮",
      "subCategory": "咖啡店",
      "type": 0
    },
    {
      "keyword": "工资",
      "category": "工资",
      "subCategory": "",
      "type": 1
    }
  ]
}
```

### 4. CategoryRule (数据模型)

**职责**: 规则数据模型

**字段定义**:
```java
public class CategoryRule {
    private String keyword;      // 关键字
    private String category;     // 主分类名称
    private String subCategory;  // 子分类名称（可选）
    private int type;            // 类型: 0=支出, 1=收入
    
    // 构造函数
    public CategoryRule(String keyword, String category, int type);
    public CategoryRule(String keyword, String category, String subCategory, int type);
    
    // Getter和Setter
    public String getKeyword();
    public void setKeyword(String keyword);
    public String getCategory();
    public void setCategory(String category);
    public String getSubCategory();
    public void setSubCategory(String subCategory);
    public int getType();
    public void setType(int type);
    
    // JSON序列化
    public JSONObject toJson() throws JSONException;
    public static CategoryRule fromJson(JSONObject json) throws JSONException;
}
```

### 5. 集成点

#### 5.1 AiSettingActivity集成

在`activity_ai_setting.xml`中添加入口卡片：
```xml
<androidx.cardview.widget.CardView
    android:id="@+id/card_ai_category_rules"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginTop="16dp"
    app:cardBackgroundColor="@color/white"
    app:cardCornerRadius="20dp"
    app:cardElevation="0dp">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="20dp"
        android:gravity="center_vertical">
        
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">
            
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="AI分类关键字规则"
                android:textColor="?android:attr/textColorPrimary"
                android:textSize="16sp"
                android:textStyle="bold" />
            
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="设置关键字自动分类规则"
                android:textColor="#888888"
                android:textSize="12sp" />
        </LinearLayout>
        
        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_arrow_right"
            android:tint="#888888" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
```

在`AiSettingActivity.java`中添加点击事件：
```java
findViewById(R.id.card_ai_category_rules).setOnClickListener(v -> {
    Intent intent = new Intent(this, AiCategoryRuleActivity.class);
    startActivity(intent);
});
```

#### 5.2 TransactionDraftMapper集成

在`TransactionDraftMapper.fromJson()`方法中，AI识别完成后应用规则：
```java
public static TransactionDraft fromJson(Context context, JSONObject json) throws Exception {
    // ... 现有的AI识别逻辑 ...
    
    TransactionDraft draft = new TransactionDraft();
    // ... 设置draft的各个字段 ...
    
    // 应用关键字规则
    AiCategoryRuleManager.applyRules(context, draft);
    
    return draft;
}
```


## UI优化设计

### 设计目标

当前实现使用基础的Spinner进行分类选择，需要优化为与项目设计语言一致的CategoryAdapter方式，支持两级分类选择和更好的用户体验。

### 优化内容

#### 1. 使用CategoryAdapter替代Spinner

**当前问题**:
- `dialog_add_rule.xml`使用Spinner进行分类选择
- `AiCategoryRuleActivity`使用`CategorySpinnerItem`类作为Spinner适配器
- UI风格与项目其他页面（如RecordFragment）不一致

**优化方案**:
- 使用`CategoryAdapter`（参考`RecordFragment.java`第1190-1600行）
- 支持详细分类模式（chip样式）和网格布局模式
- 使用FlexboxLayout或GridLayout展示分类
- 提供更直观的分类选择体验

#### 2. 支持两级分类选择

**当前问题**:
- `CategoryRule`模型只存储主分类（category字段）
- 无法支持子分类（subCategory）
- 与项目其他功能的分类体系不一致

**优化方案**:
- 在`CategoryRule`模型添加`subCategory`字段
- 支持长按主分类选择子分类（参考RecordFragment的实现）
- 使用`dialog_select_sub_category.xml`展示子分类选择对话框
- 子分类使用ChipGroup展示，支持单选

#### 3. 分类显示优化

**布局优化**:
- 使用RecyclerView + CategoryAdapter替代Spinner
- 根据`CategoryManager.isDetailedCategoryEnabled()`动态切换布局：
  - 详细模式：FlexboxLayout + chip样式（自适应宽度）
  - 网格模式：GridLayoutManager（5列固定宽度）

**交互优化**:
- 单击分类：选择主分类
- 长按分类：打开子分类选择对话框（如果启用了子分类功能）
- 子分类选择后自动关闭对话框并更新显示

### 参考实现

#### RecordFragment分类选择实现（第1190-1600行）

**关键代码片段**:
```java
// 1. 初始化CategoryAdapter
CategoryAdapter categoryAdapter = new CategoryAdapter(
    getContext(), 
    expenseCategories, 
    selectedCategory[0], 
    category -> {
        selectedCategory[0] = category;
        selectedSubCategory[0] = "";
        // 处理自定义分类
    }
);

// 2. 设置长按监听器
categoryAdapter.setOnCategoryLongClickListener(category -> {
    if (CategoryManager.isSubCategoryEnabled(getContext()) && !"自定义".equals(category)) {
        // 显示子分类选择对话框
        showSubCategoryDialog(category);
        return true;
    }
    return false;
});

// 3. 根据详细分类开关设置布局
boolean isDetailed = CategoryManager.isDetailedCategoryEnabled(getContext());
if (isDetailed) {
    FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(getContext());
    flexboxLayoutManager.setFlexWrap(FlexWrap.WRAP);
    flexboxLayoutManager.setFlexDirection(FlexDirection.ROW);
    rvCategory.setLayoutManager(flexboxLayoutManager);
} else {
    rvCategory.setLayoutManager(new GridLayoutManager(getContext(), 5));
}
```

#### CategoryAdapter特性

**支持的布局模式**:
- **详细模式**（chip样式）：
  - 宽度自适应内容（WRAP_CONTENT）
  - 圆角50dp（自动形成胶囊形状）
  - 内边距：左右12dp，上下6dp
  - 字体：14sp，正常粗细
  
- **网格模式**（单字符）：
  - 固定50x50dp大小
  - 圆角16dp
  - 显示分类首字符
  - 字体：18sp，加粗

**颜色状态**:
- 选中：`@color/app_yellow`背景，`@color/cat_selected_text`文字
- 未选中：`@color/cat_unselected_bg`背景，`@color/cat_unselected_text`文字

#### 子分类选择对话框（dialog_select_sub_category.xml）

**布局结构**:
```xml
<ConstraintLayout>
    <TextView id="tv_title" />  <!-- 标题："{主分类} - 选择细分" -->
    <NestedScrollView>
        <ChipGroup id="cg_sub_categories" />  <!-- 子分类列表 -->
    </NestedScrollView>
    <TextView id="tv_empty" />  <!-- 空状态提示 -->
    <MaterialButton id="btn_cancel" />  <!-- 取消按钮 -->
</ConstraintLayout>
```

**交互逻辑**:
- 点击Chip选择子分类，自动关闭对话框
- 再次点击已选中的Chip取消选择
- 取消按钮关闭对话框不做修改

## 数据模型

### CategoryRule

**用途**: 存储单条关键字规则

**字段**:
| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| keyword | String | 关键字 | 非空，不区分大小写匹配 |
| category | String | 主分类名称 | 非空，必须是有效的分类 |
| subCategory | String | 子分类名称 | 可选，支持二级分类 |
| type | int | 类型 | 0=支出, 1=收入 |

**JSON格式**:
```json
{
  "keyword": "咖啡",
  "category": "餐饮",
  "subCategory": "咖啡店",
  "type": 0
}
```

### 规则列表存储

**存储位置**: SharedPreferences (`ai_category_rule_prefs`)

**存储键**: `rules`

**存储格式**: JSON数组
```json
{
  "rules": [
    {"keyword": "咖啡", "category": "餐饮", "subCategory": "咖啡店", "type": 0},
    {"keyword": "工资", "category": "工资", "subCategory": "", "type": 1}
  ]
}
```


## 错误处理

### 输入验证

1. **空关键字检查**
   - 场景: 用户未输入关键字
   - 处理: 显示Toast提示"请输入关键字"，不执行保存

2. **未选择分类检查**
   - 场景: 用户未选择分类
   - 处理: 显示Toast提示"请选择分类"，不执行保存

3. **重复关键字检查**
   - 场景: 添加的关键字已存在
   - 处理: 显示Toast提示"该关键字已存在"，不执行保存

### 数据加载错误

1. **JSON解析失败**
   - 场景: SharedPreferences中的JSON格式错误
   - 处理: 返回空列表，记录日志

2. **分类不存在**
   - 场景: 规则中的分类在CategoryManager中不存在
   - 处理: 跳过该规则，继续处理其他规则

### 存储错误

1. **保存失败**
   - 场景: SharedPreferences写入失败
   - 处理: 显示Toast提示"保存失败，请重试"

2. **WebDAV同步失败**
   - 场景: 自动同步失败
   - 处理: 静默处理，不打扰用户（遵循项目规范）


## 测试策略

### 单元测试

**测试类**: `AiCategoryRuleManagerTest`

**测试用例**:
1. 测试规则的添加、更新、删除操作
2. 测试JSON序列化和反序列化
3. 测试关键字匹配逻辑（不区分大小写）
4. 测试空输入验证
5. 测试规则应用逻辑

**示例测试**:
```java
@Test
public void testAddRule() {
    CategoryRule rule = new CategoryRule("咖啡", "餐饮", 0);
    AiCategoryRuleManager.addRule(context, rule);
    List<CategoryRule> rules = AiCategoryRuleManager.getRules(context);
    assertEquals(1, rules.size());
    assertEquals("咖啡", rules.get(0).getKeyword());
}

@Test
public void testKeywordMatchingCaseInsensitive() {
    CategoryRule rule = new CategoryRule("咖啡", "餐饮", 0);
    AiCategoryRuleManager.addRule(context, rule);
    
    TransactionDraft draft = new TransactionDraft();
    draft.note = "购买星巴克咖啡";
    draft.category = "其他";
    
    AiCategoryRuleManager.applyRules(context, draft);
    assertEquals("餐饮", draft.category);
}
```

### 集成测试

**测试类**: `AiCategoryRuleIntegrationTest`

**测试用例**:
1. 测试规则持久化和加载
2. 测试与CategoryManager的集成
3. 测试与TransactionDraftMapper的集成
4. 测试WebDAV自动同步触发

### UI测试

**测试类**: `AiCategoryRuleActivityTest`

**测试用例**:
1. 测试规则列表显示
2. 测试添加规则对话框
3. 测试编辑规则对话框
4. 测试删除确认对话框
5. 测试空状态显示
6. 测试入口卡片点击跳转


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

本功能主要涉及UI交互和数据持久化，适合property-based testing的核心逻辑包括JSON序列化和关键字匹配。以下是可验证的正确性属性：

### Property 1: JSON序列化往返一致性

*For any* 有效的规则列表，将其序列化为JSON后再反序列化，应该得到与原始列表等价的规则列表。

**Validates: Requirements 7.2**

**测试策略**: 生成随机规则列表（包含不同的关键字、分类和类型），验证序列化后反序列化能恢复原始数据。

### Property 2: 关键字匹配大小写不敏感

*For any* 关键字和账单描述，如果描述包含关键字（忽略大小写），则匹配应该成功；如果描述不包含关键字，则匹配应该失败。

**Validates: Requirements 10.4**

**测试策略**: 生成随机关键字和描述，使用不同的大小写组合，验证匹配结果的一致性。

### Property 3: 规则应用优先级

*For any* 账单描述和规则列表，如果描述包含多个关键字，应用规则后的分类应该是第一个匹配规则的分类。

**Validates: Requirements 10.3**

**测试策略**: 生成包含多个关键字的账单描述和规则列表，验证应用规则后使用的是第一个匹配的规则。

### Property 4: 无匹配时保持原分类

*For any* 账单描述和规则列表，如果描述不包含任何关键字，应用规则后的分类应该保持不变。

**Validates: Requirements 10.5**

**测试策略**: 生成不包含任何规则关键字的账单描述，验证应用规则后分类保持原值。

### Property-Based Testing配置

- **测试库**: 使用JUnit + 自定义随机数据生成器
- **迭代次数**: 每个属性测试至少100次迭代
- **测试标签格式**: `// Feature: ai-category-keyword-rules, Property {number}: {property_text}`

### 测试覆盖策略

**Property-Based Tests** (验证通用属性):
- JSON序列化往返一致性
- 关键字匹配大小写不敏感性
- 规则应用优先级
- 无匹配时保持原分类

**Unit Tests** (验证具体场景):
- 空输入验证
- 重复关键字检查
- 分类不存在处理
- JSON解析错误处理

**Integration Tests** (验证集成点):
- 规则持久化和加载
- 与CategoryManager集成
- 与TransactionDraftMapper集成
- WebDAV自动同步触发

**UI Tests** (验证用户交互):
- 规则列表显示
- 对话框交互
- 空状态显示
- 页面跳转

