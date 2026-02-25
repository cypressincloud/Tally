package com.example.budgetapp.ui;

import android.content.Context;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YearPagerAdapter extends RecyclerView.Adapter<YearPagerAdapter.YearViewHolder> {

    private final int startYear;
    private final Context context;
    private YearCalendarAdapter.OnMonthClickListener monthClickListener;
    private final RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();

    // 线程池管理
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    // 1. 扩大缓存容量，确保可以覆盖 getItemCount() 定义的大部分年份
    private final LruCache<Integer, Map<Integer, Map<Integer, Double>>> dataCache = new LruCache<>(100);

    public YearPagerAdapter(Context context, int startYear) {
        this.context = context.getApplicationContext();
        this.startYear = startYear;
    }

    /**
     * 主动预热缓存：在后台线程中预加载所有年份的数据
     */
    public void preloadAllYears() {
        executor.execute(() -> {
            try {
                int totalCount = getItemCount();
                for (int i = 0; i < totalCount; i++) {
                    int year = startYear + (i - 500);
                    // 如果缓存中还没有，则进行加载
                    if (dataCache.get(year) == null) {
                        loadDataToCacheOnly(year);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 仅执行数据查询并放入缓存，不涉及任何 UI 操作
     */
    private void loadDataToCacheOnly(int year) {
        ZoneId zoneId = ZoneId.systemDefault();
        long start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        var dao = AppDatabase.getDatabase(context).transactionDao();
        var transactions = dao.getTransactionsByRange(start, end);

        Map<Integer, Map<Integer, Double>> stats = new HashMap<>();
        if (transactions != null) {
            for (var t : transactions) {
                LocalDate date = Instant.ofEpochMilli(t.date).atZone(zoneId).toLocalDate();
                int m = date.getMonthValue();
                int d = date.getDayOfMonth();
                stats.computeIfAbsent(m, k -> new HashMap<>())
                        .merge(d, t.type == 1 ? t.amount : -t.amount, Double::sum);
            }
        }
        dataCache.put(year, stats);
    }

    public void setOnMonthClickListener(YearCalendarAdapter.OnMonthClickListener listener) {
        this.monthClickListener = listener;
    }

    public void shutdownExecutor() {
        if (!executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    @NonNull
    @Override
    public YearViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_year_page, parent, false);
        return new YearViewHolder(view, sharedPool);
    }

    @Override
    public void onBindViewHolder(@NonNull YearViewHolder holder, int position) {
        int year = startYear + (position - 500);
        holder.itemView.setTag(year);

        Map<Integer, Map<Integer, Double>> cachedData = dataCache.get(year);
        if (cachedData != null) {
            updateUI(holder, year, cachedData);
        } else {
            holder.rvYearList.setAdapter(null);
            loadYearDataAsync(holder, year);
        }
    }

    private void loadYearDataAsync(YearViewHolder holder, int year) {
        executor.execute(() -> {
            loadDataToCacheOnly(year);
            Map<Integer, Map<Integer, Double>> stats = dataCache.get(year);

            holder.itemView.post(() -> {
                Object tag = holder.itemView.getTag();
                if (tag != null && (int) tag == year) {
                    updateUI(holder, year, stats);
                }
            });
        });
    }

    private void updateUI(YearViewHolder holder, int year, Map<Integer, Map<Integer, Double>> stats) {
        if (holder.rvYearList != null) {
            YearCalendarAdapter adapter = new YearCalendarAdapter(year, stats, sharedPool);
            adapter.setOnMonthClickListener(monthClickListener);
            holder.rvYearList.setAdapter(adapter);
        }
    }

    @Override
    public int getItemCount() {
        return 1000;
    }

    static class YearViewHolder extends RecyclerView.ViewHolder {
        RecyclerView rvYearList;
        public YearViewHolder(@NonNull View itemView, RecyclerView.RecycledViewPool pool) {
            super(itemView);
            rvYearList = itemView.findViewById(R.id.rv_inner_year_list);
            if (rvYearList != null) {
                rvYearList.setRecycledViewPool(pool);
                rvYearList.setLayoutManager(new GridLayoutManager(itemView.getContext(), 3));
                rvYearList.setHasFixedSize(true);
                rvYearList.setNestedScrollingEnabled(false);
            }
        }
    }
}