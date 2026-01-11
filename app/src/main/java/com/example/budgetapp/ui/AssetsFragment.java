package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
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
    private AssistantConfig config; // 新增 Config
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

        config = new AssistantConfig(requireContext()); // 初始化 Config

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

        // 初始化 Adapter，添加点击和长按监听
        adapter = new AssetAdapter(
                account -> showAddOrEditDialog(account, account.type), // OnClick: 编辑
                account -> { // OnLongClick: 设为/取消默认
                    if (account.type == 0) { // 仅允许“资产”设为默认
                        // 获取当前已经存储的默认ID
                        int currentDefaultId = config.getDefaultAssetId();

                        if (currentDefaultId == account.id) {
                            // 1. 如果当前已经是默认 -> 取消默认 (设为 -1)
                            config.setDefaultAssetId(-1);
                            adapter.setDefaultId(-1);
                            Toast.makeText(getContext(), "已取消默认资产", Toast.LENGTH_SHORT).show();
                        } else {
                            // 2. 如果不是默认 -> 设为默认
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

        // 每次刷新列表时，同步最新的默认 ID
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
        // ... (保持原有的 Dialog 逻辑不变) ...
        // 为了简洁，此处省略未修改的 showAddOrEditDialog 代码，请直接使用原文件内容
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
                        // 如果删除了默认资产，可能需要重置默认值（此处暂不强制，下次选择会失效即可）
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
        private final OnItemLongClickListener longListener; // 新增长按监听
        private int defaultAssetId = -1; // 当前默认资产 ID

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

            // 1. 核心修改：设置 View 的选中状态
            // 这会自动触发 selector_asset_bg 切换背景 (高亮 vs 默认)
            holder.itemView.setSelected(isDefault);

            // 2. 处理文字颜色
            if (isDefault) {
                // 默认资产 (高亮状态)：背景是深色主题色，文字改为白色以保证可读性
                holder.tvName.setTextColor(Color.WHITE);
                holder.tvAmount.setTextColor(Color.WHITE);
            } else {
                // 普通资产：恢复原来的文字颜色逻辑

                // 注意：这里请确保你的 colors.xml 里有 text_primary，如果没有，可以用 Color.BLACK 或其他颜色
                // 这里的 catch 是为了防止资源找不到导致崩溃，如果确定有资源可去掉 try-catch
                try {
                    holder.tvName.setTextColor(context.getResources().getColor(R.color.text_primary, null));
                } catch (Exception e) {
                    holder.tvName.setTextColor(Color.BLACK); // Fallback
                }

                // 根据类型恢复原来的金额颜色
                if (item.type == 0) {
                    holder.tvAmount.setTextColor(Color.parseColor("#FFC107")); // 资产黄
                } else if (item.type == 1) {
                    holder.tvAmount.setTextColor(Color.parseColor("#4CAF50")); // 负债绿
                } else {
                    holder.tvAmount.setTextColor(Color.parseColor("#FF5252")); // 借出红
                }
            }

            holder.tvNote.setVisibility(View.GONE);

            holder.itemView.setOnClickListener(v -> listener.onClick(item));
            holder.itemView.setOnLongClickListener(v -> {
                longListener.onLongClick(item);
                return true; // 消费事件
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