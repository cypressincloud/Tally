package com.example.budgetapp.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import java.util.Calendar;
import java.util.List;

public class AboutActivity extends AppCompatActivity {

    private FinanceViewModel financeViewModel;
    private TextView tvStatsInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_about);

        tvStatsInfo = findViewById(R.id.tv_stats_info);

        // 处理沉浸式内边距
        View rootView = findViewById(R.id.about_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // 初始化 ViewModel 并观察数据
        financeViewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        financeViewModel.getAllTransactions().observe(this, this::updateStatistics);

        // 按钮跳转逻辑
        findViewById(R.id.btn_user_notice).setOnClickListener(v -> 
            startActivity(new Intent(this, UserNoticeActivity.class)));
        findViewById(R.id.btn_donate).setOnClickListener(v -> 
            startActivity(new Intent(this, DonateActivity.class)));
    }

    private void updateStatistics(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            tvStatsInfo.setText("开始记下你的第一笔账单吧");
            return;
        }

        int count = transactions.size();
        long earliestDate = Long.MAX_VALUE;

        // 寻找最早的一笔账单时间
        for (Transaction t : transactions) {
            if (t.date < earliestDate) {
                earliestDate = t.date;
            }
        }

        // 计算天数
        long days = calculateDays(earliestDate);
        
        String info = String.format("已坚持记账 %d 天，共记账 %d 条", days, count);
        tvStatsInfo.setText(info);
    }

    private long calculateDays(long firstTimestamp) {
        // 获取今天凌晨 0 点的时间戳
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        // 获取开始记账那天凌晨 0 点的时间戳
        Calendar startDate = Calendar.getInstance();
        startDate.setTimeInMillis(firstTimestamp);
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);

        // 计算差值并转为天数（+1 表示包括今天）
        long diff = today.getTimeInMillis() - startDate.getTimeInMillis();
        return (diff / (1000 * 60 * 60 * 24)) + 1;
    }
}