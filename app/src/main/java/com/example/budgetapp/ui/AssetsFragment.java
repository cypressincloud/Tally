package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CurrencyUtils;
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AssetsFragment extends Fragment {

    private FinanceViewModel viewModel;
    private AssistantConfig config;
    private TextView tvTotalAssets, tvTotalLiability, tvTotalLent, tvListTitle;
    private LinearLayout layoutAssets, layoutLiability, layoutLent;
    private RecyclerView rvAssets;
    private AssetAdapter adapter;
    private List<AssetAccount> allAccounts = new ArrayList<>();

    // 0: 资产, 1: 负债, 2: 借出
    private int currentType = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_assets, container, false);

        config = new AssistantConfig(requireContext());

        initViews(view);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);

        viewModel.getAllAssets().observe(getViewLifecycleOwner(), list -> {
            this.allAccounts = list;
            updateUI();
        });

        return view;
    }

    private void initViews(View view) {
        tvTotalAssets = view.findViewById(R.id.tv_total_assets);
        tvTotalLiability = view.findViewById(R.id.tv_total_liability);
        tvTotalLent = view.findViewById(R.id.tv_total_lent);
        tvListTitle = view.findViewById(R.id.tv_list_title);

        layoutAssets = view.findViewById(R.id.layout_assets);
        layoutLiability = view.findViewById(R.id.layout_liability);
        layoutLent = view.findViewById(R.id.layout_lent);

        rvAssets = view.findViewById(R.id.rv_assets_list);
        rvAssets.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new AssetAdapter(
                account -> showAddOrEditDialog(account, account.type),
                account -> {
                    // 【修改】允许资产(0)和负债(1)设为默认支付项
                    if (account.type == 0 || account.type == 1) {
                        int currentDefaultId = config.getDefaultAssetId();
                        if (currentDefaultId == account.id) {
                            config.setDefaultAssetId(-1);
                            adapter.setDefaultId(-1);
                            Toast.makeText(getContext(), "已取消默认支付项", Toast.LENGTH_SHORT).show();
                        } else {
                            config.setDefaultAssetId(account.id);
                            adapter.setDefaultId(account.id);
                            Toast.makeText(getContext(), "已设为默认支付项", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "仅支持将资产或负债设为默认支付项", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        rvAssets.setAdapter(adapter);

        view.findViewById(R.id.fab_add_asset).setOnClickListener(v -> {
            // 添加触摸振动反馈
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            showAddOrEditDialog(null, currentType);
        });

        layoutAssets.setOnClickListener(v -> switchType(0));
        layoutLiability.setOnClickListener(v -> switchType(1));
        layoutLent.setOnClickListener(v -> switchType(2));
    }
    private void switchType(int type) {
        if (currentType != type) {
            currentType = type;
            refreshList();
        }
    }

    private void updateUI() {
        if (allAccounts == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

        if (!isCurrencyEnabled) {
            // 单一币种逻辑
            double totalAsset = 0;
            double totalLiability = 0;
            double totalLent = 0;

            for (AssetAccount acc : allAccounts) {
                if (acc.type == 0) totalAsset += acc.amount;
                else if (acc.type == 1) totalLiability += acc.amount;
                else if (acc.type == 2) totalLent += acc.amount;
            }

            // 恢复默认大字体
            tvTotalAssets.setTextSize(32); 
            tvTotalAssets.setText(String.format("%.2f", totalAsset));
            tvTotalLiability.setText(String.format("%.2f", totalLiability));
            tvTotalLent.setText(String.format("%.2f", totalLent));
        } else {
            // 多币种逻辑
            Map<String, Double> assetMap = new TreeMap<>();
            Map<String, Double> liabilityMap = new TreeMap<>();
            Map<String, Double> lentMap = new TreeMap<>();

            for (AssetAccount acc : allAccounts) {
                String symbol = (acc.currencySymbol != null && !acc.currencySymbol.isEmpty()) ? acc.currencySymbol : "¥";
                if (acc.type == 0) {
                    assetMap.put(symbol, assetMap.getOrDefault(symbol, 0.0) + acc.amount);
                } else if (acc.type == 1) {
                    liabilityMap.put(symbol, liabilityMap.getOrDefault(symbol, 0.0) + acc.amount);
                } else if (acc.type == 2) {
                    lentMap.put(symbol, lentMap.getOrDefault(symbol, 0.0) + acc.amount);
                }
            }

            // 【关键修改】如果包含多个币种，缩小字体
            if (assetMap.size() > 1) {
                tvTotalAssets.setTextSize(20); // 缩小字体以容纳更多内容
            } else {
                tvTotalAssets.setTextSize(32); // 恢复默认
            }

            tvTotalAssets.setText(formatMultiCurrency(assetMap));
            tvTotalLiability.setText(formatMultiCurrency(liabilityMap));
            tvTotalLent.setText(formatMultiCurrency(lentMap));
        }

        refreshList();
    }

    private String formatMultiCurrency(Map<String, Double> map) {
        if (map.isEmpty()) return "0.00";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            if (sb.length() > 0) sb.append("  "); // 使用两个空格分隔，增加可读性
            sb.append(entry.getKey()).append(String.format("%.2f", entry.getValue()));
        }
        return sb.toString();
    }

    private void refreshList() {
        List<AssetAccount> filteredList = new ArrayList<>();
        for (AssetAccount acc : allAccounts) {
            if (acc.type == currentType) {
                filteredList.add(acc);
            }
        }

        adapter.setDefaultId(config.getDefaultAssetId());
        adapter.setData(filteredList);

        if (currentType == 0) {
            tvListTitle.setText("我的资产");
        } else if (currentType == 1) {
            tvListTitle.setText("我的负债");
        } else {
            tvListTitle.setText("我的借出");
        }
    }

    private void showAddOrEditDialog(AssetAccount existing, int initType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_asset, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        RadioGroup rgType = view.findViewById(R.id.rg_asset_type);
        EditText etName = view.findViewById(R.id.et_asset_name);
        EditText etAmount = view.findViewById(R.id.et_asset_amount);
        Button btnCurrency = view.findViewById(R.id.btn_currency);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSave = view.findViewById(R.id.btn_save);
        Button btnDelete = view.findViewById(R.id.btn_delete);

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

        if (isCurrencyEnabled) {
            btnCurrency.setVisibility(View.VISIBLE);
            if (existing != null && existing.currencySymbol != null && !existing.currencySymbol.isEmpty()) {
                btnCurrency.setText(existing.currencySymbol);
            } else {
                btnCurrency.setText("¥");
            }
            btnCurrency.setOnClickListener(v -> CurrencyUtils.showCurrencyDialog(getContext(), btnCurrency, false));
        } else {
            btnCurrency.setVisibility(View.GONE);
        }

        if (existing != null) {
            etName.setText(existing.name);
            etAmount.setText(String.valueOf(existing.amount));
            btnCancel.setVisibility(View.GONE);
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            btnCancel.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.GONE);
        }

        Runnable updateLabels = () -> {
            int selectedId = rgType.getCheckedRadioButtonId();
            String titleSuffix = "";
            String nameHint = "";

            if (selectedId == R.id.rb_asset) {
                titleSuffix = "资产";
                nameHint = "资产名称";
            } else if (selectedId == R.id.rb_liability) {
                titleSuffix = "负债";
                nameHint = "负债对象";
            } else if (selectedId == R.id.rb_lent) {
                titleSuffix = "借出";
                nameHint = "借款对象";
            }

            tvTitle.setText((existing == null ? "添加" : "修改") + titleSuffix);
            etName.setHint(nameHint);
            etAmount.setHint(titleSuffix + "金额");
        };

        rgType.setOnCheckedChangeListener((group, checkedId) -> updateLabels.run());

        int targetType = (existing != null) ? existing.type : initType;
        if (targetType == 1) rgType.check(R.id.rb_liability);
        else if (targetType == 2) rgType.check(R.id.rb_lent);
        else rgType.check(R.id.rb_asset);

        updateLabels.run();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDelete.setOnClickListener(v -> {
            // --- 修改开始: 使用自定义弹窗 ---
            AlertDialog.Builder delBuilder = new AlertDialog.Builder(getContext());
            View delView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_delete, null);
            delBuilder.setView(delView);
            AlertDialog delDialog = delBuilder.create();

            // 设置背景透明
            if (delDialog.getWindow() != null) {
                delDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            TextView tvMsg = delView.findViewById(R.id.tv_dialog_message);
            tvMsg.setText("确定要删除资产 “" + existing.name + "” 吗？\n相关记录可能无法正确显示。");

            delView.findViewById(R.id.btn_dialog_cancel).setOnClickListener(dv -> delDialog.dismiss());
            delView.findViewById(R.id.btn_dialog_confirm).setOnClickListener(dv -> {
                viewModel.deleteAsset(existing);
                delDialog.dismiss();
                dialog.dismiss(); // 关闭外层编辑弹窗
            });

            delDialog.show();
            // --- 修改结束 ---
        });

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim();
            if (name.isEmpty() || amountStr.isEmpty()) return;

            double amount = 0;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) { return; }

            int finalType = 0;
            int selectedId = rgType.getCheckedRadioButtonId();
            if (selectedId == R.id.rb_liability) finalType = 1;
            else if (selectedId == R.id.rb_lent) finalType = 2;

            String symbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

            if (existing == null) {
                AssetAccount newAcc = new AssetAccount(name, amount, finalType);
                newAcc.currencySymbol = symbol;
                viewModel.addAsset(newAcc);
            } else {
                existing.name = name;
                existing.amount = amount;
                existing.type = finalType;
                existing.currencySymbol = symbol;
                existing.updateTime = System.currentTimeMillis();
                viewModel.updateAsset(existing);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    // --- Adapter ---
    private static class AssetAdapter extends RecyclerView.Adapter<AssetAdapter.VH> {
        private List<AssetAccount> data = new ArrayList<>();
        private final OnItemClickListener listener;
        private final OnItemLongClickListener longListener;
        private int defaultAssetId = -1;

        interface OnItemClickListener {
            void onClick(AssetAccount account);
        }

        interface OnItemLongClickListener {
            void onLongClick(AssetAccount account);
        }

        AssetAdapter(OnItemClickListener listener, OnItemLongClickListener longListener) {
            this.listener = listener;
            this.longListener = longListener;
        }

        void setData(List<AssetAccount> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        void setDefaultId(int id) {
            this.defaultAssetId = id;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_asset_detail, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AssetAccount item = data.get(position);
            holder.tvName.setText(item.name);

            Context context = holder.itemView.getContext();
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

            String symbol = (item.currencySymbol != null && !item.currencySymbol.isEmpty()) ? item.currencySymbol : "¥";
            String amountStr = String.format("%.2f", item.amount);
            
            if (isCurrencyEnabled) {
                holder.tvAmount.setText(symbol + amountStr);
            } else {
                holder.tvAmount.setText(amountStr);
            }

            boolean isDefault = (item.id == defaultAssetId);

            holder.itemView.setSelected(isDefault);

            if (isDefault) {
                holder.tvName.setTextColor(Color.WHITE);
                holder.tvAmount.setTextColor(Color.WHITE);
            } else {
                try {
                    holder.tvName.setTextColor(context.getColor(R.color.text_primary));
                } catch (Exception e) {
                    holder.tvName.setTextColor(Color.BLACK);
                }

                if (item.type == 0) {
                    holder.tvAmount.setTextColor(context.getColor(R.color.app_yellow));
                } else if (item.type == 1) {
                    holder.tvAmount.setTextColor(context.getColor(R.color.expense_green));
                } else {
                    holder.tvAmount.setTextColor(context.getColor(R.color.income_red));
                }
            }

            holder.tvNote.setVisibility(View.GONE);

            holder.itemView.setOnClickListener(v -> listener.onClick(item));
            holder.itemView.setOnLongClickListener(v -> {
                longListener.onLongClick(item);
                return true;
            });
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvAmount, tvNote;
            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_detail_date);
                tvAmount = v.findViewById(R.id.tv_detail_amount);
                tvNote = v.findViewById(R.id.tv_detail_note);
            }
        }
    }
}