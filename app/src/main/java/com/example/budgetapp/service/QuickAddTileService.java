package com.example.budgetapp.service;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.ui.CategoryAdapter;
import com.example.budgetapp.ui.PhotoActionActivity;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickAddTileService extends TileService {

    private boolean isWindowShowing = false;
    private View windowRootView; // 【新增】持有悬浮窗根视图引用
    private List<AssetAccount> loadedAssets = new ArrayList<>();
    private String selectedSubCategory = null;

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }

        if (isLocked()) {
            unlockAndRun(this::checkPermissionAndShowWindow);
        } else {
            checkPermissionAndShowWindow();
        }
    }

    private void checkPermissionAndShowWindow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限才能使用快捷记账", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                );
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
            return;
        }
        showConfirmWindow();
    }

    private void showConfirmWindow() {
        if (isWindowShowing) return;
        selectedSubCategory = null;

        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();

            params.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            params.format = PixelFormat.TRANSLUCENT;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.5f;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            params.gravity = Gravity.CENTER;
            params.y = -350;

            ContextThemeWrapper themeContext = new ContextThemeWrapper(this, R.style.Theme_BudgetApp);
            LayoutInflater inflater = LayoutInflater.from(themeContext);
            View floatView = inflater.inflate(R.layout.window_confirm_transaction, null);

            this.windowRootView = floatView;
            android.widget.FrameLayout windowContentRoot = floatView.findViewById(R.id.window_root);

            View rootView = floatView.findViewById(R.id.window_root);
            if (rootView != null) {
                rootView.setOnClickListener(v -> closeWindow(windowManager, floatView));
            }
            View cardContent = floatView.findViewById(R.id.window_card_content);
            if (cardContent != null) {
                cardContent.setOnClickListener(v -> { /* 拦截 */ });
            }

            isWindowShowing = true;

            EditText etAmount = floatView.findViewById(R.id.et_window_amount);
            RadioGroup rgType = floatView.findViewById(R.id.rg_window_type);
            RecyclerView rvCategory = floatView.findViewById(R.id.rv_window_category);
            EditText etCategory = floatView.findViewById(R.id.et_window_category);
            EditText etNote = floatView.findViewById(R.id.et_window_note);
            EditText etRemark = floatView.findViewById(R.id.et_window_remark);
            Spinner spAsset = floatView.findViewById(R.id.sp_asset);
            Button btnCurrency = floatView.findViewById(R.id.btn_window_currency);

            Button btnSave = floatView.findViewById(R.id.btn_window_save);
            Button btnCancel = floatView.findViewById(R.id.btn_window_cancel);

            Button btnTakePhoto = floatView.findViewById(R.id.btn_window_take_photo);
            Button btnViewPhoto = floatView.findViewById(R.id.btn_window_view_photo);

            etAmount.setText("");
            etAmount.requestFocus();
            rgType.check(R.id.rb_window_expense);

            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);
            boolean isPhotoBackupEnabled = prefs.getBoolean("enable_photo_backup", false);
            final String[] currentPhotoPath = {null};

            if (isCurrencyEnabled) {
                btnCurrency.setVisibility(View.VISIBLE);
                String defaultSymbol = prefs.getString("default_currency_symbol", "¥");
                btnCurrency.setText(defaultSymbol);
                btnCurrency.setOnClickListener(v -> {
                    com.example.budgetapp.util.CurrencyUtils.showCurrencyDialog(themeContext, btnCurrency, true);
                });
            } else {
                btnCurrency.setVisibility(View.GONE);
            }

            if (isPhotoBackupEnabled) {
                btnTakePhoto.setVisibility(View.VISIBLE);
                btnTakePhoto.setOnClickListener(v -> {
                    showLocalPhotoDialog(themeContext, windowContentRoot, actionType -> {
                        hideWindowAndStartPhotoActivity(actionType, null, currentPhotoPath);
                    });
                });

                btnViewPhoto.setOnClickListener(v -> {
                    if (currentPhotoPath[0] != null) {
                        hideWindowAndStartPhotoActivity(PhotoActionActivity.ACTION_VIEW, currentPhotoPath[0], currentPhotoPath);
                    }
                });
            }

            List<String> expenseCategories = CategoryManager.getExpenseCategories(this);
            List<String> incomeCategories = CategoryManager.getIncomeCategories(this);

            rvCategory.setLayoutManager(new GridLayoutManager(themeContext, 5));
            final String[] selectedCategory = {expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0)};

            CategoryAdapter categoryAdapter = new CategoryAdapter(themeContext, expenseCategories, selectedCategory[0], cat -> {
                selectedCategory[0] = cat;
                selectedSubCategory = null;
                if ("自定义".equals(cat)) {
                    etCategory.setVisibility(View.VISIBLE);
                    etCategory.requestFocus();
                } else {
                    etCategory.setVisibility(View.GONE);
                }
            });

            categoryAdapter.setOnCategoryLongClickListener(cat -> {
                if (CategoryManager.isSubCategoryEnabled(this) && !"自定义".equals(cat)) {
                    if (!cat.equals(selectedCategory[0])) {
                        categoryAdapter.setSelectedCategory(cat);
                        selectedCategory[0] = cat;
                        selectedSubCategory = null;
                        etCategory.setVisibility(View.GONE);
                    }
                    showSubCategoryDialog(themeContext, cat, categoryAdapter);
                    return true;
                }
                return false;
            });

            rvCategory.setAdapter(categoryAdapter);

            rgType.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_window_income) {
                    categoryAdapter.updateData(incomeCategories);
                    String first = incomeCategories.isEmpty() ? "自定义" : incomeCategories.get(0);
                    categoryAdapter.setSelectedCategory(first);
                    selectedCategory[0] = first;
                    selectedSubCategory = null;
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                } else {
                    categoryAdapter.updateData(expenseCategories);
                    String first = expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0);
                    categoryAdapter.setSelectedCategory(first);
                    selectedCategory[0] = first;
                    selectedSubCategory = null;
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                }
            });

            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            etNote.setText(sdf.format(new Date()) + " shortcut");

            AssistantConfig config = new AssistantConfig(this);
            boolean isAssetEnabled = config.isAssetsEnabled();

            if (isAssetEnabled) {
                spAsset.setVisibility(View.VISIBLE);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dropdown);
                adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
                spAsset.setAdapter(adapter);

                AppDatabase.databaseWriteExecutor.execute(() -> {
                    // 【修改】同时加载资产(0)和负债(1)
                    List<AssetAccount> assets = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(0);
                    List<AssetAccount> liabilities = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(1);

                    loadedAssets.clear();
                    AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
                    noAsset.id = 0;
                    loadedAssets.add(noAsset);

                    if (assets != null) loadedAssets.addAll(assets);
                    if (liabilities != null) loadedAssets.addAll(liabilities);

                    List<String> names = new ArrayList<>();
                    for (AssetAccount a : loadedAssets) names.add(a.name);

                    int defaultAssetId = config.getDefaultAssetId();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        adapter.clear();
                        adapter.addAll(names);
                        adapter.notifyDataSetChanged();
                        if (defaultAssetId != -1) {
                            for (int i = 0; i < loadedAssets.size(); i++) {
                                if (loadedAssets.get(i).id == defaultAssetId) {
                                    spAsset.setSelection(i);
                                    break;
                                }
                            }
                        }
                    });
                });
            } else {
                spAsset.setVisibility(View.GONE);
            }

            btnSave.setOnClickListener(v -> {
                String amountStr = etAmount.getText().toString();
                if (amountStr.isEmpty()) {
                    Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    double finalAmount = Double.parseDouble(amountStr);
                    String finalNote = etNote.getText().toString();
                    String finalRemark = etRemark.getText().toString().trim();
                    int finalType = (rgType.getCheckedRadioButtonId() == R.id.rb_window_income) ? 1 : 0;

                    String finalCat = selectedCategory[0];
                    if ("自定义".equals(finalCat)) {
                        String customInput = etCategory.getText().toString().trim();
                        if (!customInput.isEmpty()) {
                            finalCat = customInput;
                        } else {
                            Toast.makeText(this, "请输入自定义分类", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    int assetId = 0;
                    if (isAssetEnabled) {
                        int selectedPos = spAsset.getSelectedItemPosition();
                        if (selectedPos >= 0 && selectedPos < loadedAssets.size()) {
                            assetId = loadedAssets.get(selectedPos).id;
                        }
                    }

                    String symbol = isCurrencyEnabled ? btnCurrency.getText().toString() : "¥";

                    saveToDatabase(finalAmount, finalType, finalCat, selectedSubCategory, finalNote, finalRemark, assetId, symbol, currentPhotoPath[0]);
                    closeWindow(windowManager, floatView);
                    Toast.makeText(this, "记账成功", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "金额格式错误", Toast.LENGTH_SHORT).show();
                }
            });

            btnCancel.setOnClickListener(v -> closeWindow(windowManager, floatView));
            windowManager.addView(floatView, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // --- 新增辅助方法 START ---

    interface PhotoActionResult {
        void onAction(int type);
    }

    private void showLocalPhotoDialog(Context context, android.widget.FrameLayout root, PhotoActionResult listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_photo_action, root, false);
        
        // 背景遮罩
        View mask = new View(context);
        mask.setBackgroundColor(Color.parseColor("#80000000"));
        mask.setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
        });
        
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(mask, params);

        android.view.ViewGroup.LayoutParams lp = dialogView.getLayoutParams();
        android.widget.FrameLayout.LayoutParams dialogParams = new android.widget.FrameLayout.LayoutParams(
                lp.width,
                lp.height);
        dialogParams.gravity = Gravity.CENTER;
        root.addView(dialogView, dialogParams);

        // 绑定事件
        dialogView.findViewById(R.id.btn_action_camera).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
            listener.onAction(PhotoActionActivity.ACTION_CAMERA);
        });

        dialogView.findViewById(R.id.btn_action_gallery).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
            listener.onAction(PhotoActionActivity.ACTION_GALLERY);
        });

        dialogView.findViewById(R.id.btn_action_cancel).setOnClickListener(v -> {
            root.removeView(mask);
            root.removeView(dialogView);
        });
    }

    private void hideWindowAndStartPhotoActivity(int actionType, String uri, String[] currentPhotoPathRef) {
        // 1. 隐藏悬浮窗
        if (windowRootView != null) windowRootView.setVisibility(View.GONE);

        Intent intent = new Intent(this, PhotoActionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PhotoActionActivity.EXTRA_ACTION_TYPE, actionType);
        if (uri != null) {
            intent.putExtra(PhotoActionActivity.EXTRA_IMAGE_URI, uri);
        }
        
        intent.putExtra(PhotoActionActivity.EXTRA_RECEIVER, new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                // 2. 恢复悬浮窗
                if (windowRootView != null) windowRootView.setVisibility(View.VISIBLE);
                
                if (resultCode == 1 && resultData != null) {
                    String resultUri = resultData.getString(PhotoActionActivity.KEY_RESULT_URI);
                    if (currentPhotoPathRef != null) {
                        currentPhotoPathRef[0] = resultUri;
                    }
                    if (resultUri != null && windowRootView != null) {
                        Button btnView = windowRootView.findViewById(R.id.btn_window_view_photo);
                        if (btnView != null) btnView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        startActivity(intent);
    }
    // --- 新增辅助方法 END ---

    private void showSubCategoryDialog(Context context, String parentCategory, CategoryAdapter adapter) {
        // ... (保持之前的二级分类 Dialog 逻辑不变) ...
        // ... 为节省篇幅，此处省略，逻辑与之前提交的完全一致，请保留原代码 ...
        // 提示：一定要记得在这里设置 dialog.getWindow().setType(...)
        
        // 1. 获取数据
        List<String> subCats = CategoryManager.getSubCategories(this, parentCategory);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View subCatView = LayoutInflater.from(context).inflate(R.layout.dialog_select_sub_category, null);
        builder.setView(subCatView);

        AlertDialog subCatDialog = builder.create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            subCatDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            subCatDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }

        subCatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView tvTitle = subCatView.findViewById(R.id.tv_title);
        tvTitle.setText(parentCategory + " - 选择细分");

        ChipGroup cgSubCategories = subCatView.findViewById(R.id.cg_sub_categories);
        TextView tvEmpty = subCatView.findViewById(R.id.tv_empty);
        View nsvContainer = subCatView.findViewById(R.id.nsv_container);
        Button btnCancel = subCatView.findViewById(R.id.btn_cancel);

        if (subCats.isEmpty()) {
            cgSubCategories.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            nsvContainer.setMinimumHeight(150);
        } else {
            cgSubCategories.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            int bgDefault = ContextCompat.getColor(context, R.color.cat_unselected_bg);
            int bgChecked = ContextCompat.getColor(context, R.color.app_yellow);
            int textDefault = ContextCompat.getColor(context, R.color.text_primary);
            int textChecked = ContextCompat.getColor(context, R.color.cat_selected_text);

            int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] { } };
            ColorStateList bgStateList = new ColorStateList(states, new int[] { bgChecked, bgDefault });
            ColorStateList textStateList = new ColorStateList(states, new int[] { textChecked, textDefault });

            for (String subCatName : subCats) {
                Chip chip = new Chip(context);
                chip.setText(subCatName);
                chip.setCheckable(true);
                chip.setClickable(true);
                chip.setChipBackgroundColor(bgStateList);
                chip.setTextColor(textStateList);
                chip.setChipStrokeWidth(0);
                chip.setCheckedIconVisible(false);

                if (subCatName.equals(selectedSubCategory)) {
                    chip.setChecked(true);
                }

                chip.setOnClickListener(v -> {
                    if (subCatName.equals(selectedSubCategory)) {
                        selectedSubCategory = null;
                        Toast.makeText(this, "已取消细分", Toast.LENGTH_SHORT).show();
                    } else {
                        selectedSubCategory = subCatName;
                        Toast.makeText(this, "已选择: " + subCatName, Toast.LENGTH_SHORT).show();
                    }
                    if (adapter != null) {
                        adapter.setSelectedCategory(parentCategory);
                    }
                    subCatDialog.dismiss();
                });
                cgSubCategories.addView(chip);
            }
        }
        btnCancel.setOnClickListener(v -> subCatDialog.dismiss());
        subCatDialog.show();
    }

    private void closeWindow(WindowManager wm, View view) {
        try {
            if (view != null && wm != null) wm.removeView(view);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isWindowShowing = false;
            windowRootView = null; // 清理引用
        }
    }

    private void saveToDatabase(double amount, int type, String category, String subCategory, String note, String remark, int assetId, String currencySymbol, String photoPath) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());

            Transaction t = new Transaction();
            t.date = System.currentTimeMillis();
            t.type = type;
            t.category = category;
            t.subCategory = subCategory;
            t.amount = amount;
            t.note = note;
            t.remark = remark;
            t.assetId = assetId;
            t.currencySymbol = currencySymbol;
            t.photoPath = photoPath;
            db.transactionDao().insert(t);

            if (assetId != 0) {
                AssetAccount asset = db.assetAccountDao().getAssetByIdSync(assetId);
                if (asset != null) {
                    // 【修改】区分资产和负债的计算逻辑
                    if (asset.type == 0) {
                        if (type == 1) asset.amount += amount;
                        else asset.amount -= amount;
                    } else if (asset.type == 1) {
                        if (type == 1) asset.amount -= amount; // 收入还款，负债减少
                        else asset.amount += amount; // 支出刷卡，负债增加
                    }
                    db.assetAccountDao().update(asset);
                }
            }
        });
    }
}