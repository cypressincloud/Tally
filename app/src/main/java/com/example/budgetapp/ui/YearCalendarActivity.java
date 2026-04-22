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
import com.example.budgetapp.database.TransactionDao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YearCalendarActivity extends AppCompatActivity {

    private int currentYear;
    private RecyclerView rvYearList;
    private TextView tvTitle;
    private GestureDetector gestureDetector;

    // 共享回收池
    private final RecyclerView.RecycledViewPool viewPool = new RecyclerView.RecycledViewPool();

    private float downX, downY;
    private boolean isMoved;
    private int touchSlop;
    private boolean isAnimating = false;

    // 单线程池，专门用于后台去数据库捞数据
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_year_calendar);

        View mainLayout = findViewById(R.id.main_layout);
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        currentYear = getIntent().getIntExtra("year", LocalDate.now().getYear());

        tvTitle = findViewById(R.id.tv_year_title);
        tvTitle.setText(String.valueOf(currentYear));

        rvYearList = findViewById(R.id.rv_year_list);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 3) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        };
        rvYearList.setLayoutManager(layoutManager);
        rvYearList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rvYearList.setHasFixedSize(true);
        rvYearList.setNestedScrollingEnabled(false);

        initGestureDetector();

        // 首次加载当前年
        loadData(0, currentYear);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isAnimating) return true;

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
                    if (dx > touchSlop || dy > touchSlop) {
                        isMoved = true;
                        MotionEvent cancel = MotionEvent.obtain(ev);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        return true;
                    }
                } else {
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isMoved) return true;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) &&
                        Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                    if (diffX > 0) {
                        currentYear--;
                        updateYearDisplay(-1);
                    } else {
                        currentYear++;
                        updateYearDisplay(1);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void updateYearDisplay(int direction) {
        tvTitle.setText(String.valueOf(currentYear));
        if (direction != 0 && rvYearList.getWidth() > 0) {
            isAnimating = true;
            rvYearList.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            int screenWidth = rvYearList.getWidth();

            // 旧数据滑出屏幕
            rvYearList.animate()
                    .translationX(direction == 1 ? -screenWidth : screenWidth)
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        // 动画结束后，去加载/读取新一年的数据
                        loadData(direction, currentYear);
                    })
                    .start();
        } else {
            loadData(0, currentYear);
        }
    }

    private void loadData(int direction, int targetYear) {
        dbExecutor.execute(() -> {
            // 获取时间范围
            ZoneId zoneId = ZoneId.systemDefault();
            long start = LocalDate.of(targetYear, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli();
            long end = LocalDate.of(targetYear, 12, 31).atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

            // 🌟 调用新的优化 SQL，只拿有数据的月份列表 (如 [1, 3, 10] 表示 1,3,10月有账单)
            List<Integer> monthsWithData = AppDatabase.getDatabase(this).transactionDao().getMonthsWithDataSync(start, end);

            if (!isFinishing() && !isDestroyed()) {
                runOnUiThread(() -> renderData(direction, targetYear, monthsWithData));
            }
        });
    }

    // 使用轻量级对象查询，极大降低内存占用
    private Map<Integer, Map<Integer, Double>> fetchYearStatsFromDb(int year) {
        ZoneId zoneId = ZoneId.systemDefault();
        long start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        TransactionDao dao = AppDatabase.getDatabase(this).transactionDao();
        List<com.example.budgetapp.database.TransactionMinimal> transactions = dao.getMinimalTransactionsSync(start, end);

        Map<Integer, Map<Integer, Double>> stats = new HashMap<>();
        for (com.example.budgetapp.database.TransactionMinimal t : transactions) {
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
        return stats;
    }

    // 将数据渲染并推入屏幕的 UI 动画逻辑
    private void renderData(int direction, int year, List<Integer> monthsWithData) {
        YearCalendarAdapter adapter = new YearCalendarAdapter(year, monthsWithData, viewPool);
        adapter.setOnMonthClickListener((y, m) -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("year", y);
            resultIntent.putExtra("month", m);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        rvYearList.setAdapter(adapter);

        if (direction != 0) {
            int screenWidth = rvYearList.getWidth();
            rvYearList.setTranslationX(direction == 1 ? screenWidth : -screenWidth);

            rvYearList.post(() -> {
                rvYearList.animate()
                        .translationX(0)
                        .alpha(1f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            rvYearList.setLayerType(View.LAYER_TYPE_NONE, null);
                            isAnimating = false;
                        })
                        .start();
            });
        } else {
            isAnimating = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Activity 销毁时释放线程池资源
        if (!dbExecutor.isShutdown()) {
            // 🌟 核心修复：绝对不能使用 shutdownNow() 强制中断！
            // 强制中断正在读取 Room 数据库的线程会直接锁死整个数据库连接，导致退回主页后死锁。
            // 改用 shutdown()，允许正在查询的任务正常跑完，但不再接受新任务。
            dbExecutor.shutdown();
        }
    }
}