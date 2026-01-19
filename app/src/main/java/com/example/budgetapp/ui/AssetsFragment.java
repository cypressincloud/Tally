package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
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
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.util.ArrayList;
import java.util.List;

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
                    if (account.type == 0) {
                        int currentDefaultId = config.getDefaultAssetId();
                        if (currentDefaultId == account.id) {
                            config.setDefaultAssetId(-1);
                            adapter.setDefaultId(-1);
                            Toast.makeText(getContext(), "已取消默认资产", Toast.LENGTH_SHORT).show();
                        } else {
                            config.setDefaultAssetId(account.id);
                            adapter.setDefaultId(account.id);
                            Toast.makeText(getContext(), "已设为默认资产", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "仅支持将资产设为默认支付项", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        rvAssets.setAdapter(adapter);

        view.findViewById(R.id.fab_add_asset).setOnClickListener(v -> showAddOrEditDialog(null, currentType));

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

        double totalAsset = 0;
        double totalLiability = 0;
        double totalLent = 0;

        for (AssetAccount acc : allAccounts) {
            if (acc.type == 0) totalAsset += acc.amount;
            else if (acc.type == 1) totalLiability += acc.amount;
            else if (acc.type == 2) totalLent += acc.amount;
        }

        tvTotalAssets.setText(String.format("%.2f", totalAsset));
        tvTotalLiability.setText(String.format("%.2f", totalLiability));
        tvTotalLent.setText(String.format("%.2f", totalLent));

        refreshList();
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
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnSave = view.findViewById(R.id.btn_save);
        Button btnDelete = view.findViewById(R.id.btn_delete);

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
            new AlertDialog.Builder(getContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除这项记录吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        viewModel.deleteAsset(existing);
                        dialog.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
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

            if (existing == null) {
                AssetAccount newAcc = new AssetAccount(name, amount, finalType);
                viewModel.addAsset(newAcc);
            } else {
                existing.name = name;
                existing.amount = amount;
                existing.type = finalType;
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
            holder.tvAmount.setText(String.format("%.2f", item.amount));

            boolean isDefault = (item.id == defaultAssetId);
            Context context = holder.itemView.getContext();

            holder.itemView.setSelected(isDefault);

            if (isDefault) {
                // 默认状态：白色文字
                holder.tvName.setTextColor(Color.WHITE);
                holder.tvAmount.setTextColor(Color.WHITE);
            } else {
                // 普通状态：使用资源颜色以适配夜间模式
                try {
                    // 资产名称颜色
                    holder.tvName.setTextColor(context.getColor(R.color.text_primary));
                } catch (Exception e) {
                    holder.tvName.setTextColor(Color.BLACK);
                }

                // *** 核心修改：使用 context.getColor 获取主题适配的颜色 ***
                if (item.type == 0) {
                    // 资产 -> app_yellow
                    holder.tvAmount.setTextColor(context.getColor(R.color.app_yellow));
                } else if (item.type == 1) {
                    // 负债 -> expense_green
                    holder.tvAmount.setTextColor(context.getColor(R.color.expense_green));
                } else {
                    // 借出 -> income_red
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