package com.example.budgetapp.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.ai.AiModelProfile;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class AiModelManagerActivity extends AppCompatActivity {
    
    private LinearLayout layoutProfiles;
    private FloatingActionButton fabAdd;
    private String modelType;  // text, vision, audio
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        
        setContentView(R.layout.activity_ai_model_manager);
        
        // 获取模型类型
        modelType = getIntent().getStringExtra("model_type");
        if (modelType == null) modelType = "text";
        
        View rootView = findViewById(R.id.ai_model_manager_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        
        initViews();
        loadProfiles();
    }
    
    private void initViews() {
        TextView tvTitle = findViewById(R.id.tv_title);
        String title = "文本模型配置";
        if ("vision".equals(modelType)) {
            title = "视觉模型配置";
        } else if ("audio".equals(modelType)) {
            title = "音频模型配置";
        }
        tvTitle.setText(title);
        
        layoutProfiles = findViewById(R.id.layout_profiles);
        fabAdd = findViewById(R.id.fab_add);
        
        fabAdd.setOnClickListener(v -> showEditDialog(null));
    }
    
    private void loadProfiles() {
        layoutProfiles.removeAllViews();
        List<AiModelProfile> profiles = AiModelProfile.loadProfilesByType(this, modelType);
        
        if (profiles.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("暂无配置，点击右下角按钮添加");
            tvEmpty.setTextColor(0xFF888888);
            tvEmpty.setTextSize(14);
            tvEmpty.setPadding(0, 40, 0, 40);
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            layoutProfiles.addView(tvEmpty);
            return;
        }
        
        for (AiModelProfile profile : profiles) {
            View cardView = LayoutInflater.from(this).inflate(R.layout.item_ai_model_profile, layoutProfiles, false);
            bindProfileCard(cardView, profile);
            layoutProfiles.addView(cardView);
        }
    }
    
    private void bindProfileCard(View cardView, AiModelProfile profile) {
        TextView tvProfileName = cardView.findViewById(R.id.tv_profile_name);
        TextView tvModelName = cardView.findViewById(R.id.tv_model_name);
        TextView tvBaseUrl = cardView.findViewById(R.id.tv_base_url);
        TextView tvApiKey = cardView.findViewById(R.id.tv_api_key);
        ImageButton btnEdit = cardView.findViewById(R.id.btn_edit);
        ImageButton btnDelete = cardView.findViewById(R.id.btn_delete);
        
        tvProfileName.setText(profile.name);
        tvModelName.setText(profile.modelName);
        tvBaseUrl.setText(profile.baseUrl);
        
        // 隐藏API Key
        if (profile.apiKey != null && !profile.apiKey.isEmpty()) {
            tvApiKey.setText("sk-***" + profile.apiKey.substring(Math.max(0, profile.apiKey.length() - 4)));
        } else {
            tvApiKey.setText("未设置");
        }
        
        btnEdit.setOnClickListener(v -> showEditDialog(profile));
        btnDelete.setOnClickListener(v -> showDeleteDialog(profile));
    }
    
    private void showEditDialog(AiModelProfile profile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_ai_model_profile, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        EditText etProfileName = view.findViewById(R.id.et_profile_name);
        EditText etModelName = view.findViewById(R.id.et_model_name);
        EditText etBaseUrl = view.findViewById(R.id.et_base_url);
        EditText etApiKey = view.findViewById(R.id.et_api_key);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSave = view.findViewById(R.id.btn_save);
        
        // 如果是编辑，填充数据
        if (profile != null) {
            etProfileName.setText(profile.name);
            etModelName.setText(profile.modelName);
            etBaseUrl.setText(profile.baseUrl);
            etApiKey.setText(profile.apiKey);
        }
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSave.setOnClickListener(v -> {
            String name = etProfileName.getText().toString().trim();
            String modelName = etModelName.getText().toString().trim();
            String baseUrl = etBaseUrl.getText().toString().trim();
            String apiKey = etApiKey.getText().toString().trim();
            
            if (name.isEmpty()) {
                Toast.makeText(this, "请输入配置名称", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (modelName.isEmpty()) {
                Toast.makeText(this, "请输入模型名称", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (profile == null) {
                // 新增
                AiModelProfile newProfile = new AiModelProfile(name, modelName, baseUrl, apiKey, modelType);
                AiModelProfile.addProfile(this, newProfile);
                Toast.makeText(this, "添加成功", Toast.LENGTH_SHORT).show();
            } else {
                // 编辑
                profile.name = name;
                profile.modelName = modelName;
                profile.baseUrl = baseUrl;
                profile.apiKey = apiKey;
                AiModelProfile.updateProfile(this, profile);
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            }
            
            loadProfiles();
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void showDeleteDialog(AiModelProfile profile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        TextView tvMessage = view.findViewById(R.id.tv_dialog_message);
        
        tvTitle.setText("删除配置");
        tvMessage.setText("确定要删除配置 \"" + profile.name + "\" 吗？\n删除后将无法恢复。");
        
        view.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        
        view.findViewById(R.id.btn_dialog_confirm).setOnClickListener(v -> {
            AiModelProfile.deleteProfile(this, profile.id);
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            loadProfiles();
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadProfiles();
    }
}
