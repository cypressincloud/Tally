package com.example.budgetapp.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.database.TransactionDao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YearCalendarActivity extends AppCompatActivity {

    private int currentYear;
    private RecyclerView rvYearList;
    private TextView tvTitle;
    private GestureDetector gestureDetector;
    
    // 共享回收池
    private final RecyclerView.RecycledViewPool viewPool = new RecyclerView.RecycledViewPool();

    // 手势拦截相关变量
    private float downX, downY;
    private boolean isMoved; // 是否判定为滑动
    private int touchSlop;   // 系统最小滑动距离阈值

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 沉浸式状态栏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_year_calendar);

        View mainLayout = findViewById(R.id.main_layout);
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // 获取系统认为的“滑动”最小距离
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        currentYear = getIntent().getIntExtra("year", LocalDate.now().getYear());

        tvTitle = findViewById(R.id.tv_year_title);
        tvTitle.setText(String.valueOf(currentYear));

        rvYearList = findViewById(R.id.rv_year_list);
        
        // 禁止垂直滑动
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        };
        rvYearList.setLayoutManager(layoutManager);
        
        // 禁用边缘回弹效果 & 优化性能
        rvYearList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rvYearList.setHasFixedSize(true);
        rvYearList.setNestedScrollingEnabled(false);

        // 初始化手势识别
        initGestureDetector();
        
        // 注意：这里移除了 rvYearList.setOnTouchListener，改由 dispatchTouchEvent 全局接管

        loadData();
    }

    /**
     * 重写事件分发，实现 滑动优先级 > 点击
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 1. 优先让 GestureDetector 处理（检测 Fling 切换年份）
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                isMoved = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!isMoved) {
                    float dx = Math.abs(ev.getX() - downX);
                    float dy = Math.abs(ev.getY() - downY);
                    // 如果移动距离超过系统阈值，判定为滑动意图
                    if (dx > touchSlop || dy > touchSlop) {
                        isMoved = true;
                        // 发送 CANCEL 事件给子 View（如月份卡片），让它们取消按压状态
                        MotionEvent cancel = MotionEvent.obtain(ev);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        return true; // 拦截后续 MOVE 事件，不再传递给子 View
                    }
                } else {
                    return true; // 已经判定为滑动，拦截所有 MOVE
                }
                break;

            case MotionEvent.ACTION_UP:
                if (isMoved) {
                    // 如果是滑动操作，直接拦截 UP 事件
                    // 这样子 View 就永远收不到 UP，也就不会触发 onClick
                    return true; 
                }
                break;
        }

        // 如果不是滑动，正常分发事件（触发点击）
        return super.dispatchTouchEvent(ev);
    }

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true; // 必须返回 true 才能接收后续事件
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // 水平滑动判断
                if (Math.abs(diffX) > Math.abs(diffY) &&
                        Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    
                    if (diffX > 0) {
                        // 向右滑 -> 上一年
                        currentYear--;
                    } else {
                        // 向左滑 -> 下一年
                        currentYear++;
                    }
                    updateYearDisplay();
                    return true;
                }
                return false;
            }
        });
    }

    private void updateYearDisplay() {
        tvTitle.setText(String.valueOf(currentYear));
        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            ZoneId zoneId = ZoneId.systemDefault();
            long start = LocalDate.of(currentYear, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli();
            long end = LocalDate.of(currentYear, 12, 31).atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

            TransactionDao dao = AppDatabase.getDatabase(this).transactionDao();
            List<Transaction> transactions = dao.getTransactionsByRange(start, end);

            Map<Integer, Map<Integer, Double>> stats = new HashMap<>();

            for (Transaction t : transactions) {
                LocalDate date = Instant.ofEpochMilli(t.date).atZone(zoneId).toLocalDate();
                int month = date.getMonthValue();
                int day = date.getDayOfMonth();

                if (!stats.containsKey(month)) {
                    stats.put(month, new HashMap<>());
                }
                Map<Integer, Double> monthMap = stats.get(month);

                double amount = (t.type == 1) ? t.amount : -t.amount;
                monthMap.put(day, monthMap.getOrDefault(day, 0.0) + amount);
            }

            runOnUiThread(() -> {
                // 传入 viewPool
                YearCalendarAdapter adapter = new YearCalendarAdapter(currentYear, stats, viewPool);
                adapter.setOnMonthClickListener((year, month) -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("year", year);
                    resultIntent.putExtra("month", month);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });
                rvYearList.setAdapter(adapter);
            });
        }).start();
    }
}