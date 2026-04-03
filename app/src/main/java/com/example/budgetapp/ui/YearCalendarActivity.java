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
import java.util.concurrent.ConcurrentHashMap;
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

    // 【新增 1】年份数据缓存，使用 ConcurrentHashMap 保证多线程安全
    private final Map<Integer, Map<Integer, Map<Integer, Double>>> yearStatsCache = new ConcurrentHashMap<>();

    // 【新增 2】单线程池，专门用于后台去数据库捞数据，防止频繁 new Thread 造成资源浪费
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

    // 【修改】核心逻辑：先读缓存，没缓存再查数据库，最后执行预加载
    private void loadData(int direction, int targetYear) {
        if (yearStatsCache.containsKey(targetYear)) {
            // 缓存命中：直接在主线程渲染，无需等待数据库！
            renderData(direction, targetYear, yearStatsCache.get(targetYear));
            prefetchAdjacentYears(targetYear);
        } else {
            // 缓存未命中：交由线程池去数据库查询
            dbExecutor.execute(() -> {
                Map<Integer, Map<Integer, Double>> stats = fetchYearStatsFromDb(targetYear);
                // 存入缓存
                yearStatsCache.put(targetYear, stats);
                runOnUiThread(() -> {
                    renderData(direction, targetYear, stats);
                    prefetchAdjacentYears(targetYear);
                });
            });
        }
    }

    // 【修改】使用轻量级对象查询，极大降低内存占用
    private Map<Integer, Map<Integer, Double>> fetchYearStatsFromDb(int year) {
        ZoneId zoneId = ZoneId.systemDefault();
        long start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        TransactionDao dao = AppDatabase.getDatabase(this).transactionDao();
        // 🌟 核心优化：只查 date, type, amount 三个字段，且已在 SQL 层排除了转账记录
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

    // 【新增】后台静默预加载相邻年份（上一年、下一年）
    private void prefetchAdjacentYears(int centerYear) {
        dbExecutor.execute(() -> {
            if (!yearStatsCache.containsKey(centerYear - 1)) {
                yearStatsCache.put(centerYear - 1, fetchYearStatsFromDb(centerYear - 1));
            }
            if (!yearStatsCache.containsKey(centerYear + 1)) {
                yearStatsCache.put(centerYear + 1, fetchYearStatsFromDb(centerYear + 1));
            }
        });
    }

    // 【抽离】将数据渲染并推入屏幕的 UI 动画逻辑单独拿出来
    private void renderData(int direction, int year, Map<Integer, Map<Integer, Double>> stats) {
        YearCalendarAdapter adapter = new YearCalendarAdapter(year, stats, viewPool);
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
            dbExecutor.shutdownNow();
        }
    }
}