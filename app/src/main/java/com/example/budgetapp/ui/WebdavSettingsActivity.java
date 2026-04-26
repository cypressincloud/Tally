package com.example.budgetapp.ui;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.BackupManager;
import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class WebdavSettingsActivity extends AppCompatActivity {

    private TextInputEditText etUrl;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;

    private static final String PREF_NAME = "webdav_prefs";
    private static final String KEY_URL = "webdav_url";
    private static final String KEY_USERNAME = "webdav_username";
    private static final String KEY_PASSWORD = "webdav_password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 状态栏和导航栏完全透明，实现全面沉浸
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_webdav_settings);

        // 根据屏幕实际的系统栏宽高来动态赋予边距
        View rootView = findViewById(R.id.root_view);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        etUrl = findViewById(R.id.et_webdav_url);
        etUsername = findViewById(R.id.et_webdav_username);
        etPassword = findViewById(R.id.et_webdav_password);

        Button btnSave = findViewById(R.id.btn_save_webdav);
        Button btnUpload = findViewById(R.id.btn_upload_data);
        Button btnSync = findViewById(R.id.btn_sync_data);

        // 加载已保存的配置
        loadWebdavConfig();

        // 1. 保存配置
        btnSave.setOnClickListener(v -> saveWebdavConfig());

        // 2. 上传数据 (去掉了重复的 Button 定义)
        btnUpload.setOnClickListener(v -> {
            if (isConfigEmpty()) {
                Toast.makeText(this, "请先填写并保存 WebDAV 配置", Toast.LENGTH_SHORT).show();
                return;
            }

            // 锁定按钮防止重复点击
            btnUpload.setEnabled(false);
            btnUpload.setText("打包上传中...");

            String url = etUrl.getText().toString().trim();
            String user = etUsername.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();

            // 开启子线程进行数据库读取与网络请求
            new Thread(() -> {
                try {
                    com.example.budgetapp.database.AppDatabase db = com.example.budgetapp.database.AppDatabase.getDatabase(getApplicationContext());

                    // 获取需要备份的数据
                    List<com.example.budgetapp.database.Transaction> transactions = db.transactionDao().getAllTransactionsSync();
                    List<com.example.budgetapp.database.AssetAccount> assets = db.assetAccountDao().getAllAssetsSync();
                    // 【新增】获取存钱目标
                    List<com.example.budgetapp.database.Goal> goals = db.goalDao().getAllGoalsSync();

                    // 【修改】把 goals 传进去
                    com.example.budgetapp.BackupManager.uploadToWebDAV(this, url, user, pass, transactions, assets, goals);

                    // 成功后切回主线程更新 UI
                    runOnUiThread(() -> {
                        Toast.makeText(this, "✅ 备份数据已成功上传至 WebDAV！", Toast.LENGTH_LONG).show();
                        btnUpload.setEnabled(true);
                        btnUpload.setText("上传数据");
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    // 失败后切回主线程提示错误
                    runOnUiThread(() -> {
                        Toast.makeText(this, "❌ 上传失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        btnUpload.setEnabled(true);
                        btnUpload.setText("上传数据");
                    });
                }
            }).start();
        });

        // 3. 同步数据
        btnSync.setOnClickListener(v -> {
            if (isConfigEmpty()) {
                Toast.makeText(this, "请先填写并保存 WebDAV 配置", Toast.LENGTH_SHORT).show();
                return;
            }

            // 弹出二次确认框，防止用户误触导致本地数据被覆盖
            new android.app.AlertDialog.Builder(this)
                    .setTitle("同步数据确认")
                    .setMessage("从 WebDAV 同步数据将会覆盖本地现有的所有账单、资产和分类设置，是否继续？")
                    .setPositiveButton("确认同步", (dialog, which) -> {
                        btnSync.setEnabled(false);
                        btnSync.setText("下载同步中...");

                        String url = etUrl.getText().toString().trim();
                        String user = etUsername.getText().toString().trim();
                        String pass = etPassword.getText().toString().trim();

                        new Thread(() -> {
                            try {
                                // 1. 下载并解析 WebDAV 上的备份文件
                                com.example.budgetapp.BackupData data = com.example.budgetapp.BackupManager.downloadFromWebDAV(this, url, user, pass);

                                // 2. 执行数据库覆盖操作
                                com.example.budgetapp.database.AppDatabase db = com.example.budgetapp.database.AppDatabase.getDatabase(getApplicationContext());

                                db.runInTransaction(() -> {
                                    // 1. 清理旧数据
                                    db.transactionDao().deleteAll();
                                    db.goalDao().deleteAll(); // 清理旧目标

                                    // 清理旧资产
                                    java.util.List<com.example.budgetapp.database.AssetAccount> oldAssets = db.assetAccountDao().getAllAssetsSync();
                                    if (oldAssets != null) {
                                        for (com.example.budgetapp.database.AssetAccount oldAsset : oldAssets) {
                                            db.assetAccountDao().delete(oldAsset);
                                        }
                                    }

                                    // 2. 插入新数据
                                    if (data.records != null && !data.records.isEmpty()) {
                                        db.transactionDao().insertAll(data.records);
                                    }

                                    if (data.assets != null && !data.assets.isEmpty()) {
                                        for (com.example.budgetapp.database.AssetAccount asset : data.assets) {
                                            db.assetAccountDao().insert(asset);
                                        }
                                    }

                                    // 恢复新目标
                                    if (data.goals != null && !data.goals.isEmpty()) {
                                        db.goalDao().insertAll(data.goals);
                                    }
                                });

                                // 3. 成功后切回主线程更新 UI
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "✅ 数据同步成功！请重新启动应用使设置生效。", Toast.LENGTH_LONG).show();
                                    btnSync.setEnabled(true);
                                    btnSync.setText("同步数据");
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                // 失败后切回主线程提示错误
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "❌ 同步失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    btnSync.setEnabled(true);
                                    btnSync.setText("同步数据");
                                });
                            }
                        }).start();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private boolean isConfigEmpty() {
        String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        return url.isEmpty() || username.isEmpty() || password.isEmpty();
    }

    private void loadWebdavConfig() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        etUrl.setText(prefs.getString(KEY_URL, ""));
        etUsername.setText(prefs.getString(KEY_USERNAME, ""));
        etPassword.setText(prefs.getString(KEY_PASSWORD, ""));
    }

    private void saveWebdavConfig() {
        String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请填写完整配置信息", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_URL, url)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply();

        Toast.makeText(this, "WebDAV 配置已保存", Toast.LENGTH_SHORT).show();
        // 移除了 finish()，允许用户保存后停留在当前页面继续点击"上传"或"同步"
    }
}