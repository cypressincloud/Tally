package com.example.budgetapp.ui;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences; // 新增导入
import android.os.Bundle;
import android.view.View;
import android.widget.TextView; // 新增导入
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.budgetapp.R;

public class UserNoticeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 开启沉浸式：状态栏和导航栏（小白条）内容下沉
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_user_notice);

        // 处理系统栏遮挡，增加内边距
        View rootView = findViewById(R.id.notice_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Github 链接复制逻辑
        findViewById(R.id.btn_copy_github).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setText("https://github.com/cypressincloud/Tally");
            Toast.makeText(this, "链接已复制到剪切板", Toast.LENGTH_SHORT).show();
        });

        // 新增：123盘链接复制逻辑
        findViewById(R.id.btn_copy_123pan).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setText("https://www.123pan.com/s/Ih5uVv-nYapd.html");
            Toast.makeText(this, "链接已复制到剪切板", Toast.LENGTH_SHORT).show();
        });

        // 隐藏彩蛋：长按标题取消激活高级设置
        TextView tvTitle = findViewById(R.id.tv_notice_title);
        tvTitle.setOnLongClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("is_premium_activated", false).apply();
            Toast.makeText(this, "高级设置已取消激活", Toast.LENGTH_SHORT).show();
            return true; // 返回 true 表示已消费此长按事件
        });
    }
}