package com.example.budgetapp.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YearPagerAdapter extends RecyclerView.Adapter<YearPagerAdapter.YearViewHolder> {

    private final int startYear;
    private final Context context;
    private YearCalendarAdapter.OnMonthClickListener monthClickListener;
    private final RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();

    // 线程池管理
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public YearPagerAdapter(Context context, int startYear) {
        this.context = context.getApplicationContext();
        this.startYear = startYear;
    }

    /**
     * 🌟 优化：直接查询该年份哪些月份有数据，返回 1-12 的月份列表
     */
    private List<Integer> fetchMonthsWithData(int year) {
        ZoneId zoneId = ZoneId.systemDefault();
        long start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();

        // 调用优化后的 DAO 方法，只返回存在记录的月份
        return AppDatabase.getDatabase(context).transactionDao().getMonthsWithDataSync(start, end);
    }

    public void setOnMonthClickListener(YearCalendarAdapter.OnMonthClickListener listener) {
        this.monthClickListener = listener;
    }

    /**
     * 🌟 优化：改为平滑关闭，防止强制中断导致数据库死锁
     */
    public void shutdownExecutor() {
        if (!executor.isShutdown()) {
            executor.shutdown();
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

        // 每次绑定时先清空，防止旧视图闪现
        holder.rvYearList.setAdapter(null);
        loadYearDataAsync(holder, year);
    }

    private void loadYearDataAsync(YearViewHolder holder, int year) {
        executor.execute(() -> {
            // 🌟 获取有数据的月份列表
            List<Integer> monthsWithData = fetchMonthsWithData(year);

            holder.itemView.post(() -> {
                Object tag = holder.itemView.getTag();
                // 校验 Tag 确保异步回调时 ViewHolder 没被复用
                if (tag != null && (int) tag == year) {
                    updateUI(holder, year, monthsWithData);
                }
            });
        });
    }

    private void updateUI(YearViewHolder holder, int year, List<Integer> monthsWithData) {
        if (holder.rvYearList != null) {
            // 🌟 按照新的构造函数传入 List<Integer>
            YearCalendarAdapter adapter = new YearCalendarAdapter(year, monthsWithData, sharedPool);
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