package com.example.budgetapp.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

import com.example.budgetapp.R;

public class PhotoBackupSettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private TextView tvPath;
    private SwitchCompat switchBackup;

    private final ActivityResultLauncher<Uri> openDocTreeLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    // 持久化权限
                    try {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    
                    prefs.edit().putString("photo_backup_uri", uri.toString()).apply();
                    updatePathDisplay(uri);
                    Toast.makeText(this, "路径已设置", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. 设置沉浸式绘制
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_photo_backup_settings);

        // 2. 处理 WindowInsets (状态栏和小白条)
        View rootView = findViewById(R.id.root_view);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();
        
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(originalPaddingLeft + insets.left, originalPaddingTop + insets.top, originalPaddingRight + insets.right, originalPaddingBottom + insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // 3. 【已移除】手动返回按钮逻辑

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        tvPath = findViewById(R.id.tv_backup_path);
        switchBackup = findViewById(R.id.switch_enable_backup);

        boolean isEnabled = prefs.getBoolean("enable_photo_backup", false);
        switchBackup.setChecked(isEnabled);
        String uriStr = prefs.getString("photo_backup_uri", "");
        if (!uriStr.isEmpty()) {
            try {
                updatePathDisplay(Uri.parse(uriStr));
            } catch (Exception e) {
                tvPath.setText("路径失效");
            }
        } else {
            tvPath.setText("未设置");
        }

        switchBackup.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean("enable_photo_backup", checked).apply();
            if (checked && prefs.getString("photo_backup_uri", "").isEmpty()) {
                Toast.makeText(this, "请先设置照片存储路径", Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.btn_select_path).setOnClickListener(v -> {
            openDocTreeLauncher.launch(null);
        });
    }

    private void updatePathDisplay(Uri uri) {
        DocumentFile file = DocumentFile.fromTreeUri(this, uri);
        if (file != null) {
            tvPath.setText(file.getName() != null ? file.getName() : uri.toString());
        } else {
            tvPath.setText(uri.toString());
        }
    }
}