package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.ai.AiAccountingClient;
import com.example.budgetapp.util.PromptManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * AI系统提示词编辑器
 */
public class AiPromptEditorActivity extends AppCompatActivity {
    
    private TextView tvStatusIndicator;
    private EditText etPromptContent;
    private TextView tvCharCount;
    private TextView btnViewRules;
    private TextView btnImport;
    private TextView btnExport;
    private Button btnRestoreDefault;
    private Button btnSave;
    
    private boolean isCustomPrompt = false;
    
    // 文件选择器
    private ActivityResultLauncher<Intent> importLauncher;
    private ActivityResultLauncher<Intent> exportLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化文件选择器
        initFileLaunchers();
        
        // 沉浸式状态栏设置
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        
        setContentView(R.layout.activity_ai_prompt_editor);

        // 适配内边距
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            final int originalPaddingTop = rootLayout.getPaddingTop();
            final int originalPaddingBottom = rootLayout.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                // 【核心修复】：同时获取系统栏 (systemBars) 和 软键盘 (ime) 的高度
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());

                v.setPadding(
                        v.getPaddingLeft(),
                        originalPaddingTop + insets.top,
                        v.getPaddingRight(),
                        originalPaddingBottom + insets.bottom // 键盘弹出时，bottom 会变成键盘的高度
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }
        
        initViews();
        loadPrompt();
        
        // 检查是否首次访问，如果是则强制显示规则说明
        checkAndShowFirstTimeRules();
    }
    
    private void initFileLaunchers() {
        // 导入文件选择器
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            importPromptFromFile(uri);
                        }
                    }
                }
        );
        
        // 导出文件选择器
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            exportPromptToFile(uri);
                        }
                    }
                }
        );
    }
    
    private void initViews() {
        tvStatusIndicator = findViewById(R.id.tv_status_indicator);
        etPromptContent = findViewById(R.id.et_prompt_content);
        tvCharCount = findViewById(R.id.tv_char_count);
        // 【修改点 2】: 这里现在绑定的是 XML 中的 TextView
        btnViewRules = findViewById(R.id.btn_view_rules);
        btnImport = findViewById(R.id.btn_import);
        btnExport = findViewById(R.id.btn_export);
        btnRestoreDefault = findViewById(R.id.btn_restore_default);
        btnSave = findViewById(R.id.btn_save);
        
        // 启用 EditText 的滚动功能
        etPromptContent.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());
        etPromptContent.setVerticalScrollBarEnabled(true);
        
        // 字符数统计
        etPromptContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                updateCharCount();
            }
        });
        
        // 按钮点击事件
        btnViewRules.setOnClickListener(v -> showRulesDialog());
        btnImport.setOnClickListener(v -> showImportDialog());
        btnExport.setOnClickListener(v -> showExportDialog());
        btnRestoreDefault.setOnClickListener(v -> showRestoreDefaultDialog());
        btnSave.setOnClickListener(v -> savePrompt());
    }
    
    private void loadPrompt() {
        // 在后台线程加载提示词
        new Thread(() -> {
            String prompt;
            boolean isCustom;
            
            if (PromptManager.hasCustomPrompt(this)) {
                prompt = PromptManager.getCustomPrompt(this);
                isCustom = true;
            } else {
                prompt = new AiAccountingClient().buildSystemPrompt(this);
                isCustom = false;
            }
            
            // 在主线程更新 UI
            final String finalPrompt = prompt;
            final boolean finalIsCustom = isCustom;
            runOnUiThread(() -> {
                isCustomPrompt = finalIsCustom;
                updateStatusIndicator(finalIsCustom);
                etPromptContent.setText(finalPrompt);
                updateCharCount();
            });
        }).start();
    }
    
    private void updateStatusIndicator(boolean isCustom) {
        if (isCustom) {
            tvStatusIndicator.setText("当前使用：自定义提示词");
            tvStatusIndicator.setTextColor(getResources().getColor(R.color.app_blue, null));
        } else {
            tvStatusIndicator.setText("当前使用：默认提示词");
            tvStatusIndicator.setTextColor(0xFF888888);
        }
    }
    
    private void updateCharCount() {
        int count = etPromptContent.getText().toString().length();
        tvCharCount.setText("字符数: " + count);
    }
    
    private void savePrompt() {
        String prompt = etPromptContent.getText().toString();
        
        // 验证提示词
        PromptManager.ValidationResult result = PromptManager.validatePrompt(prompt);
        
        if (!result.isValid) {
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示警告（如果有）
        if (result.warningMessage != null) {
            Toast.makeText(this, result.warningMessage, Toast.LENGTH_LONG).show();
        }
        
        // 保存提示词
        PromptManager.saveCustomPrompt(this, prompt);
        isCustomPrompt = true;
        updateStatusIndicator(true);
        
        Toast.makeText(this, "提示词已保存", Toast.LENGTH_SHORT).show();
    }
    
    private void showRestoreDefaultDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_restore_default, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnConfirm.setOnClickListener(v -> {
            // 清除自定义提示词
            PromptManager.clearCustomPrompt(this);
            
            // 在后台线程加载默认提示词
            new Thread(() -> {
                String defaultPrompt = new AiAccountingClient().buildSystemPrompt(this);
                
                // 在主线程更新 UI
                runOnUiThread(() -> {
                    etPromptContent.setText(defaultPrompt);
                    isCustomPrompt = false;
                    updateStatusIndicator(false);
                    updateCharCount();
                    Toast.makeText(this, "已恢复默认提示词", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }).start();
        });
        
        dialog.show();
    }
    
    private void showRulesDialog() {
        showRulesDialog(false);
    }
    
    private void showRulesDialog(boolean isFirstTime) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_prompt_rules, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        // 首次访问时不可取消
        if (isFirstTime) {
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
        }
        
        TextView tvRulesContent = view.findViewById(R.id.tv_rules_content);
        Button btnClose = view.findViewById(R.id.btn_close);
        
        // 设置规则说明内容
        String rulesContent = buildRulesContent();
        tvRulesContent.setText(rulesContent);
        
        btnClose.setOnClickListener(v -> {
            // 如果是首次访问，标记已显示
            if (isFirstTime) {
                PromptManager.markRulesShown(this);
            }
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    /**
     * 检查并显示首次访问的规则说明
     */
    private void checkAndShowFirstTimeRules() {
        if (PromptManager.isFirstTimeAccess(this)) {
            // 延迟显示，确保页面已完全加载
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                showRulesDialog(true);
            }, 300);
        }
    }
    
    private String buildRulesContent() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("默认提示词包含以下主要规则类别：\n\n");
        
        sb.append("【JSON 输出规则】\n\n");
        sb.append("定义AI返回的数据格式，确保返回标准的JSON数组结构。\n\n");
        
        sb.append("【多账单识别规则】\n\n");
        sb.append("处理截图中包含多条交易记录的情况，确保每条记录都被正确识别。\n\n");
        
        sb.append("【金额识别规则】\n\n");
        sb.append("识别实际支付金额，区分原价、优惠价、折扣等不同类型的金额。\n\n");
        
        sb.append("【收支类型规则】\n\n");
        sb.append("判断交易是支出还是收入，处理退款、转账等特殊情况。\n\n");
        
        sb.append("【时间识别规则】\n\n");
        sb.append("从截图或文本中提取交易时间，处理各种时间格式。\n\n");
        
        sb.append("【分类规则】\n\n");
        sb.append("根据交易内容自动分配合适的分类和子分类。\n\n");
        
        sb.append("【备注规则】\n\n");
        sb.append("提取商户名称、商品名称等关键信息作为备注。\n\n");
        
        sb.append("【资产账户规则】\n\n");
        sb.append("识别支付方式并匹配到对应的资产账户。\n\n");
        
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        
        sb.append("⚠️ 重要提示\n\n");
        
        sb.append("以下内容由代码动态生成，不建议在提示词中修改：\n\n");
        
        sb.append("• 当前系统时间\n");
        sb.append("  系统会自动插入当前时间，用于时间识别的参考。\n\n");
        
        sb.append("• 支出分类与二级分类\n");
        sb.append("  从应用的分类设置中自动读取，修改请前往分类管理。\n\n");
        
        sb.append("• 收入分类与二级分类\n");
        sb.append("  从应用的分类设置中自动读取，修改请前往分类管理。\n\n");
        
        sb.append("• 可用资产账户\n");
        sb.append("  从应用的资产账户列表中自动读取，修改请前往资产管理。\n\n");
        
        sb.append("• JSON 示例\n");
        sb.append("  系统自动生成的示例格式，用于演示正确的输出结构。\n\n");
        
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        
        sb.append("💡 编辑建议\n\n");
        
        sb.append("您可以根据个人需求修改以下内容：\n\n");
        
        sb.append("• 调整各类规则的优先级和判断逻辑\n");
        sb.append("• 添加特定商户或场景的识别规则\n");
        sb.append("• 修改金额、时间、分类的识别策略\n");
        sb.append("• 增加或删除某些规则条目\n\n");
        
        sb.append("但请注意保持 JSON 输出格式的完整性，否则可能导致识别失败。");
        
        return sb.toString();
    }
    
    /**
     * 显示导入对话框
     */
    private void showImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_import_prompt, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSelectFile = view.findViewById(R.id.btn_select_file);
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSelectFile.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            importLauncher.launch(intent);
        });
        
        dialog.show();
    }
    
    /**
     * 显示导出对话框
     */
    private void showExportDialog() {
        String prompt = etPromptContent.getText().toString();
        if (prompt.trim().isEmpty()) {
            Toast.makeText(this, "提示词内容为空，无法导出", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_export_prompt, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnExport = view.findViewById(R.id.btn_export);
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnExport.setOnClickListener(v -> {
            dialog.dismiss();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String fileName = "ai_prompt_" + timestamp + ".txt";
            
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            exportLauncher.launch(intent);
        });
        
        dialog.show();
    }
    
    /**
     * 从文件导入提示词
     */
    private void importPromptFromFile(Uri uri) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                );
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                inputStream.close();
                
                String importedPrompt = content.toString().trim();
                
                // 验证导入的提示词
                PromptManager.ValidationResult result = PromptManager.validatePrompt(importedPrompt);
                
                runOnUiThread(() -> {
                    if (!result.isValid) {
                        new AlertDialog.Builder(this)
                                .setTitle("导入失败")
                                .setMessage("导入的提示词不符合要求：\n\n" + result.errorMessage)
                                .setPositiveButton("确定", null)
                                .show();
                        return;
                    }
                    
                    // 显示警告（如果有）
                    if (result.warningMessage != null) {
                        Toast.makeText(this, result.warningMessage, Toast.LENGTH_LONG).show();
                    }
                    
                    // 更新编辑器内容
                    etPromptContent.setText(importedPrompt);
                    updateCharCount();
                    Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> 
                    Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
    
    /**
     * 导出提示词到文件
     */
    private void exportPromptToFile(Uri uri) {
        new Thread(() -> {
            try {
                String prompt = etPromptContent.getText().toString();
                
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream == null) {
                    runOnUiThread(() -> Toast.makeText(this, "无法写入文件", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                outputStream.write(prompt.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                outputStream.close();
                
                runOnUiThread(() -> 
                    Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show()
                );
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> 
                    Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}
