package com.example.budgetapp.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.ai.AiAccountingClient;
import com.example.budgetapp.ai.AiConfig;

public class AiSettingActivity extends AppCompatActivity {

    private EditText etBaseUrl;
    private EditText etApiKey;
    private EditText etTextModel;
    private EditText etVisionModel;
    private EditText etAudioModel;
    private SwitchCompat switchAi;
    private Button btnTest;
    private TextView tvTextStatus;
    private TextView tvVisionStatus;
    private TextView tvAudioStatus;
    private AiAccountingClient aiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 【新增】：状态栏与导航栏背景透明化，实现真正的沉浸式
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_ai_setting);
        aiClient = new AiAccountingClient();

        View rootView = findViewById(R.id.ai_setting_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initView();
    }

    private void initView() {

        switchAi = findViewById(R.id.switch_ai_enable);
        etBaseUrl = findViewById(R.id.et_base_url);
        etApiKey = findViewById(R.id.et_api_key);
        etTextModel = findViewById(R.id.et_text_model);
        etVisionModel = findViewById(R.id.et_vision_model);
        etAudioModel = findViewById(R.id.et_audio_model);
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
        etVisionModel.setText(config.visionModel);
        etAudioModel.setText(config.audioModel);
        showCachedCapabilities(config);

        // 添加AI分类关键字规则入口点击事件
        findViewById(R.id.card_ai_category_rules).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, AiCategoryRuleActivity.class);
            startActivity(intent);
        });

        btnTest.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> {
            AiConfig newConfig = buildConfigFromInput();
            if (newConfig.enabled && !newConfig.isTextReady()) {
                Toast.makeText(this, "启用 AI 记账前，请至少填写 Base URL、API Key 和文本模型。", Toast.LENGTH_LONG).show();
                return;
            }

            AiConfig cachedConfig = AiConfig.load(this);
            if (cachedConfig.matchesSharedEndpoint(newConfig) && safeEquals(cachedConfig.textModel, newConfig.textModel)) {
                newConfig.textTestOk = cachedConfig.textTestOk;
            }
            if (cachedConfig.matchesSharedEndpoint(newConfig) && safeEquals(cachedConfig.visionModel, newConfig.visionModel)) {
                newConfig.visionTestOk = cachedConfig.visionTestOk;
            }
            if (cachedConfig.matchesSharedEndpoint(newConfig) && safeEquals(cachedConfig.audioModel, newConfig.audioModel)) {
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
        config.visionModel = etVisionModel.getText().toString().trim();
        config.audioModel = etAudioModel.getText().toString().trim();
        return config;
    }

    private void testConnection() {
        AiConfig config = buildConfigFromInput();
        if (!config.hasSharedConnection()) {
            Toast.makeText(this, "请先填写 Base URL 和 API Key。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (config.textModel.isEmpty()) {
            Toast.makeText(this, "请先填写文本模型。", Toast.LENGTH_SHORT).show();
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

    private boolean safeEquals(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }
}
