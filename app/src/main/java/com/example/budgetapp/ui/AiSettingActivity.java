package com.example.budgetapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.ai.AiAccountingClient;
import com.example.budgetapp.ai.AiConfig;
import com.example.budgetapp.util.ScreenshotAutoSaveManager;

import java.util.ArrayList;
import java.util.List;

public class AiSettingActivity extends AppCompatActivity {

    private EditText etBaseUrl;
    private EditText etApiKey;
    
    private EditText etTextModel;
    private EditText etTextBaseUrl;
    private EditText etTextApiKey;
    
    private EditText etVisionModel;
    private EditText etVisionBaseUrl;
    private EditText etVisionApiKey;
    
    private EditText etAudioModel;
    private EditText etAudioBaseUrl;
    private EditText etAudioApiKey;
    
    private SwitchCompat switchAi;
    private Button btnTest;
    private TextView tvTextStatus;
    private TextView tvVisionStatus;
    private TextView tvAudioStatus;
    private AiAccountingClient aiClient;
    
    // 截图自动保存相关
    private SwitchCompat switchScreenshotAutoSave;
    private TextView tvScreenshotAutoSaveHint;
    private ScreenshotAutoSaveManager screenshotAutoSaveManager;
    
    // 静默记账开关
    private SwitchCompat switchAiSilentRecording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 【新增】：状态栏与导航栏背景透明化，实现真正的沉浸式
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_ai_setting);
        aiClient = new AiAccountingClient();
        screenshotAutoSaveManager = new ScreenshotAutoSaveManager(this);

        View rootView = findViewById(R.id.ai_setting_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initView();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateScreenshotAutoSaveHint(); // 动态更新状态提示
    }

    private void initView() {

        switchAi = findViewById(R.id.switch_ai_enable);
        etBaseUrl = findViewById(R.id.et_base_url);
        etApiKey = findViewById(R.id.et_api_key);
        
        etTextModel = findViewById(R.id.et_text_model);
        etTextBaseUrl = findViewById(R.id.et_text_base_url);
        etTextApiKey = findViewById(R.id.et_text_api_key);
        
        etVisionModel = findViewById(R.id.et_vision_model);
        etVisionBaseUrl = findViewById(R.id.et_vision_base_url);
        etVisionApiKey = findViewById(R.id.et_vision_api_key);
        
        etAudioModel = findViewById(R.id.et_audio_model);
        etAudioBaseUrl = findViewById(R.id.et_audio_base_url);
        etAudioApiKey = findViewById(R.id.et_audio_api_key);
        
        btnTest = findViewById(R.id.btn_test_connection);
        Button btnSave = findViewById(R.id.btn_save_ai_config);
        tvTextStatus = findViewById(R.id.tv_text_status);
        tvVisionStatus = findViewById(R.id.tv_vision_status);
        tvAudioStatus = findViewById(R.id.tv_audio_status);

        AiConfig config = AiConfig.load(this);
        switchAi.setChecked(config.enabled);
        etBaseUrl.setText(config.baseUrl);
        etApiKey.setText(config.apiKey);
        
        etTextModel.setText(config.textModel);
        etTextBaseUrl.setText(config.textBaseUrl);
        etTextApiKey.setText(config.textApiKey);
        
        etVisionModel.setText(config.visionModel);
        etVisionBaseUrl.setText(config.visionBaseUrl);
        etVisionApiKey.setText(config.visionApiKey);
        
        etAudioModel.setText(config.audioModel);
        etAudioBaseUrl.setText(config.audioBaseUrl);
        etAudioApiKey.setText(config.audioApiKey);
        
        showCachedCapabilities(config);
        
        // 初始化截图自动保存开关
        initScreenshotAutoSaveSwitch();
        
        // 初始化静默记账开关
        initAiSilentRecordingSwitch();

        // 添加模型切换按钮点击事件
        findViewById(R.id.btn_switch_text_model).setOnClickListener(v -> showModelSwitchDialog("text"));
        findViewById(R.id.btn_switch_vision_model).setOnClickListener(v -> showModelSwitchDialog("vision"));
        findViewById(R.id.btn_switch_audio_model).setOnClickListener(v -> showModelSwitchDialog("audio"));

        // 添加AI分类关键字规则入口点击事件
        findViewById(R.id.card_ai_category_rules).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, AiCategoryRuleActivity.class);
            startActivity(intent);
        });

        // 添加AI识别提示词入口点击事件
        findViewById(R.id.card_ai_prompt).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, AiPromptEditorActivity.class);
            startActivity(intent);
        });

        // 添加AI记账配置指南入口点击事件（在默认连接信息标题右边）
        findViewById(R.id.tv_ai_guide).setOnClickListener(v -> {
            Intent intent = new Intent(this, AiGuideActivity.class);
            startActivity(intent);
        });

        btnTest.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> {
            AiConfig newConfig = buildConfigFromInput();
            if (newConfig.enabled && !newConfig.isTextReady()) {
                Toast.makeText(this, "启用 AI 记账前，请至少配置文本模型（填写模型名称，并确保有可用的连接信息）。", Toast.LENGTH_LONG).show();
                return;
            }

            AiConfig cachedConfig = AiConfig.load(this);
            
            // 保留文本模型测试结果（如果配置未变）
            if (matchesEndpoint(cachedConfig.getEffectiveTextBaseUrl(), cachedConfig.getEffectiveTextApiKey(),
                    newConfig.getEffectiveTextBaseUrl(), newConfig.getEffectiveTextApiKey())
                    && safeEquals(cachedConfig.textModel, newConfig.textModel)) {
                newConfig.textTestOk = cachedConfig.textTestOk;
            }
            
            // 保留视觉模型测试结果（如果配置未变）
            if (matchesEndpoint(cachedConfig.getEffectiveVisionBaseUrl(), cachedConfig.getEffectiveVisionApiKey(),
                    newConfig.getEffectiveVisionBaseUrl(), newConfig.getEffectiveVisionApiKey())
                    && safeEquals(cachedConfig.visionModel, newConfig.visionModel)) {
                newConfig.visionTestOk = cachedConfig.visionTestOk;
            }
            
            // 保留音频模型测试结果（如果配置未变）
            if (matchesEndpoint(cachedConfig.getEffectiveAudioBaseUrl(), cachedConfig.getEffectiveAudioApiKey(),
                    newConfig.getEffectiveAudioBaseUrl(), newConfig.getEffectiveAudioApiKey())
                    && safeEquals(cachedConfig.audioModel, newConfig.audioModel)) {
                newConfig.audioTestOk = cachedConfig.audioTestOk;
            }

            newConfig.save(this);
            Toast.makeText(this, "AI 配置已保存。", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void showCachedCapabilities(AiConfig config) {
        updateStatusView(tvTextStatus, "文本模型", config.textModel, config.textTestOk, "未检测");
        updateStatusView(tvVisionStatus, "视觉模型", config.visionModel, config.visionTestOk, "未检测/未配置");
        updateStatusView(tvAudioStatus, "音频模型", config.audioModel, config.audioTestOk, "未检测/未配置");
    }

    private void updateStatusView(TextView textView, String label, String model, boolean ok, String fallback) {
        if (model == null || model.trim().isEmpty()) {
            textView.setText(label + "：未配置");
        } else if (ok) {
            textView.setText(label + "：可用");
        } else {
            textView.setText(label + "：" + fallback);
        }
    }

    private AiConfig buildConfigFromInput() {
        AiConfig config = new AiConfig();
        config.enabled = switchAi.isChecked();
        
        config.baseUrl = etBaseUrl.getText().toString().trim();
        config.apiKey = etApiKey.getText().toString().trim();
        
        config.textModel = etTextModel.getText().toString().trim();
        config.textBaseUrl = etTextBaseUrl.getText().toString().trim();
        config.textApiKey = etTextApiKey.getText().toString().trim();
        
        config.visionModel = etVisionModel.getText().toString().trim();
        config.visionBaseUrl = etVisionBaseUrl.getText().toString().trim();
        config.visionApiKey = etVisionApiKey.getText().toString().trim();
        
        config.audioModel = etAudioModel.getText().toString().trim();
        config.audioBaseUrl = etAudioBaseUrl.getText().toString().trim();
        config.audioApiKey = etAudioApiKey.getText().toString().trim();
        
        return config;
    }

    private void testConnection() {
        AiConfig config = buildConfigFromInput();
        if (!config.isTextReady()) {
            Toast.makeText(this, "请先配置文本模型（填写模型名称，并确保有可用的连接信息）。", Toast.LENGTH_SHORT).show();
            return;
        }

        btnTest.setEnabled(false);
        btnTest.setText("检测中...");
        tvTextStatus.setText("文本模型：检测中...");
        tvVisionStatus.setText(config.visionModel.isEmpty() ? "视觉模型：未配置" : "视觉模型：检测中...");
        tvAudioStatus.setText(config.audioModel.isEmpty() ? "音频模型：未配置" : "音频模型：检测中...");

        new Thread(() -> {
            aiClient.setConfig(config);
            AiAccountingClient.TestResult result = aiClient.testConfiguration(this);
            new Handler(Looper.getMainLooper()).post(() -> {
                btnTest.setEnabled(true);
                btnTest.setText("测试连接");
                tvTextStatus.setText("文本模型：" + result.textMessage);
                tvVisionStatus.setText("视觉模型：" + result.visionMessage);
                tvAudioStatus.setText("音频模型：" + result.audioMessage);
                if (result.textOk) {
                    Toast.makeText(AiSettingActivity.this, "AI 模型检测完成。", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AiSettingActivity.this, result.textMessage, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private boolean matchesEndpoint(String url1, String key1, String url2, String key2) {
        String u1 = url1 == null ? "" : url1.trim();
        String k1 = key1 == null ? "" : key1.trim();
        String u2 = url2 == null ? "" : url2.trim();
        String k2 = key2 == null ? "" : key2.trim();
        return u1.equals(u2) && k1.equals(k2);
    }

    private boolean safeEquals(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }
    
    private void initScreenshotAutoSaveSwitch() {
        switchScreenshotAutoSave = findViewById(R.id.switchScreenshotAutoSave);
        tvScreenshotAutoSaveHint = findViewById(R.id.tvScreenshotAutoSaveHint);
        
        switchScreenshotAutoSave.setChecked(screenshotAutoSaveManager.isEnabled());
        updateScreenshotAutoSaveHint();
        
        switchScreenshotAutoSave.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 验证前置条件
                ScreenshotAutoSaveManager.ValidationResult result = 
                    screenshotAutoSaveManager.validatePrerequisites();
                
                if (!result.isValid) {
                    // 显示错误对话框
                    showPrerequisiteDialog(result.errorMessage);
                    switchScreenshotAutoSave.setChecked(false);
                    return;
                }
            }
            
            screenshotAutoSaveManager.setEnabled(isChecked);
            updateScreenshotAutoSaveHint();
            Toast.makeText(this, 
                isChecked ? "已开启截图自动保存" : "已关闭截图自动保存", 
                Toast.LENGTH_SHORT).show();
        });
    }
    
    private void showPrerequisiteDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_prerequisite_check, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        TextView tvMessage = view.findViewById(R.id.tv_message);
        tvMessage.setText(message);
        
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_go_settings).setOnClickListener(v -> {
            Intent intent = new Intent(this, PhotoBackupSettingsActivity.class);
            startActivity(intent);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void updateScreenshotAutoSaveHint() {
        ScreenshotAutoSaveManager.ValidationResult result = 
            screenshotAutoSaveManager.validatePrerequisites();
        
        if (result.isValid) {
            tvScreenshotAutoSaveHint.setText("功能已就绪");
            tvScreenshotAutoSaveHint.setTextColor(0xFF4CAF50); // Green
        } else {
            tvScreenshotAutoSaveHint.setText(result.errorMessage);
            tvScreenshotAutoSaveHint.setTextColor(0xFFFF9800); // Orange
        }
    }
    
    private void initAiSilentRecordingSwitch() {
        switchAiSilentRecording = findViewById(R.id.switchAiSilentRecording);
        switchAiSilentRecording.setChecked(AiConfig.isSilentAiRecordingEnabled(this));
        
        switchAiSilentRecording.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AiConfig.setSilentAiRecordingEnabled(this, isChecked);
            Toast.makeText(this, 
                isChecked ? "已开启AI静默记账" : "已关闭AI静默记账", 
                Toast.LENGTH_SHORT).show();
        });
    }
    
    private void showModelSwitchDialog(String modelType) {
        List<com.example.budgetapp.ai.AiModelProfile> profiles = 
            com.example.budgetapp.ai.AiModelProfile.loadProfilesByType(this, modelType);
        
        // 创建自定义对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_model_switch, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        
        // 设置标题
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_dialog_subtitle);
        String title = "文本模型配置";
        if ("vision".equals(modelType)) {
            title = "视觉模型配置";
        } else if ("audio".equals(modelType)) {
            title = "音频模型配置";
        }
        tvTitle.setText(title);
        
        // 设置RecyclerView
        androidx.recyclerview.widget.RecyclerView rvProfiles = dialogView.findViewById(R.id.rv_profiles);
        rvProfiles.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        
        // 创建适配器
        ModelSwitchAdapter adapter = new ModelSwitchAdapter(profiles, profile -> {
            applyModelProfile(profile, modelType);
            dialog.dismiss();
        });
        rvProfiles.setAdapter(adapter);
        
        // 如果没有配置，隐藏RecyclerView和分隔线
        if (profiles.isEmpty()) {
            rvProfiles.setVisibility(View.GONE);
            dialogView.findViewById(R.id.rv_profiles).setVisibility(View.GONE);
            tvSubtitle.setText("暂无已保存的配置");
        }
        
        // 新增配置按钮
        dialogView.findViewById(R.id.btn_add_new).setOnClickListener(v -> {
            Intent intent = new Intent(this, AiModelManagerActivity.class);
            intent.putExtra("model_type", modelType);
            startActivity(intent);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    // 内部适配器类
    private static class ModelSwitchAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ModelSwitchAdapter.ViewHolder> {
        private final List<com.example.budgetapp.ai.AiModelProfile> profiles;
        private final OnProfileClickListener listener;
        
        interface OnProfileClickListener {
            void onProfileClick(com.example.budgetapp.ai.AiModelProfile profile);
        }
        
        ModelSwitchAdapter(List<com.example.budgetapp.ai.AiModelProfile> profiles, OnProfileClickListener listener) {
            this.profiles = profiles;
            this.listener = listener;
        }
        
        @androidx.annotation.NonNull
        @Override
        public ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_model_switch_profile, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ViewHolder holder, int position) {
            com.example.budgetapp.ai.AiModelProfile profile = profiles.get(position);
            holder.tvProfileName.setText(profile.name);
            holder.tvModelInfo.setText(profile.modelName);
            holder.itemView.setOnClickListener(v -> listener.onProfileClick(profile));
        }
        
        @Override
        public int getItemCount() {
            return profiles.size();
        }
        
        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            TextView tvProfileName;
            TextView tvModelInfo;
            
            ViewHolder(View itemView) {
                super(itemView);
                tvProfileName = itemView.findViewById(R.id.tv_profile_name);
                tvModelInfo = itemView.findViewById(R.id.tv_model_info);
            }
        }
    }
    
    private void applyModelProfile(com.example.budgetapp.ai.AiModelProfile profile, String modelType) {
        if ("text".equals(modelType)) {
            etTextModel.setText(profile.modelName);
            etTextBaseUrl.setText(profile.baseUrl);
            etTextApiKey.setText(profile.apiKey);
            Toast.makeText(this, "已应用配置：" + profile.name, Toast.LENGTH_SHORT).show();
        } else if ("vision".equals(modelType)) {
            etVisionModel.setText(profile.modelName);
            etVisionBaseUrl.setText(profile.baseUrl);
            etVisionApiKey.setText(profile.apiKey);
            Toast.makeText(this, "已应用配置：" + profile.name, Toast.LENGTH_SHORT).show();
        } else if ("audio".equals(modelType)) {
            etAudioModel.setText(profile.modelName);
            etAudioBaseUrl.setText(profile.baseUrl);
            etAudioApiKey.setText(profile.apiKey);
            Toast.makeText(this, "已应用配置：" + profile.name, Toast.LENGTH_SHORT).show();
        }
    }
}
