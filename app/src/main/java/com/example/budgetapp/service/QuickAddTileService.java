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
import android.os.Handler;
import android.os.Looper;
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
    private List<AssetAccount> loadedAssets = new ArrayList<>();
    // 【新增】用于记录选中的二级分类
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

        // 每次打开窗口重置二级分类
        selectedSubCategory = null;

        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();

            params.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            params.format = PixelFormat.TRANSLUCENT;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;

            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.5f;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            params.gravity = Gravity.CENTER;
            params.y = -350;

            ContextThemeWrapper themeContext = new ContextThemeWrapper(this, R.style.Theme_BudgetApp);
            LayoutInflater inflater = LayoutInflater.from(themeContext);
            View floatView = inflater.inflate(R.layout.window_confirm_transaction, null);

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

            etAmount.setText("");
            etAmount.requestFocus(); 
            rgType.check(R.id.rb_window_expense);

            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean isCurrencyEnabled = prefs.getBoolean("enable_currency", false);

            if (isCurrencyEnabled) {
                btnCurrency.setVisibility(View.VISIBLE);
                btnCurrency.setText("¥"); 
                btnCurrency.setOnClickListener(v -> {
                    com.example.budgetapp.util.CurrencyUtils.showCurrencyDialog(themeContext, btnCurrency, true);
                });
            } else {
                btnCurrency.setVisibility(View.GONE);
            }
            
            // 分类逻辑
            List<String> expenseCategories = CategoryManager.getExpenseCategories(this);
            List<String> incomeCategories = CategoryManager.getIncomeCategories(this);
            
            rvCategory.setLayoutManager(new GridLayoutManager(themeContext, 5));
            final String[] selectedCategory = {expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0)};

            CategoryAdapter categoryAdapter = new CategoryAdapter(themeContext, expenseCategories, selectedCategory[0], cat -> {
                selectedCategory[0] = cat;
                // 切换一级分类时重置二级分类
                selectedSubCategory = null; 
                if ("自定义".equals(cat)) {
                    etCategory.setVisibility(View.VISIBLE);
                    etCategory.requestFocus();
                } else {
                    etCategory.setVisibility(View.GONE);
                }
            });

            // 【新增】长按显示二级分类
            categoryAdapter.setOnCategoryLongClickListener(cat -> {
                if (CategoryManager.isSubCategoryEnabled(this) && !"自定义".equals(cat)) {
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
                    selectedSubCategory = null; // 切换收支类型重置
                    etCategory.setVisibility("自定义".equals(first) ? View.VISIBLE : View.GONE);
                } else {
                    categoryAdapter.updateData(expenseCategories);
                    String first = expenseCategories.isEmpty() ? "自定义" : expenseCategories.get(0);
                    categoryAdapter.setSelectedCategory(first);
                    selectedCategory[0] = first;
                    selectedSubCategory = null; // 切换收支类型重置
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
                    List<AssetAccount> assets = AppDatabase.getDatabase(this).assetAccountDao().getAssetsByTypeSync(0);
                    loadedAssets.clear();
                    AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
                    noAsset.id = 0;
                    loadedAssets.add(noAsset);
                    if (assets != null) loadedAssets.addAll(assets);
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
                    
                    // 【修改】传入 selectedSubCategory
                    saveToDatabase(finalAmount, finalType, finalCat, selectedSubCategory, finalNote, finalRemark, assetId, symbol);
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

    // 【修改】显示二级分类对话框 (逻辑与 RecordFragment 保持一致)
    private void showSubCategoryDialog(Context context, String parentCategory, CategoryAdapter adapter) {
        // 1. 获取数据
        List<String> subCats = CategoryManager.getSubCategories(this, parentCategory);

        // 2. 构建 Dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View subCatView = LayoutInflater.from(context).inflate(R.layout.dialog_select_sub_category, null);
        builder.setView(subCatView);

        AlertDialog subCatDialog = builder.create();

        // 【关键】Service 中弹窗需要设置 Window Type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            subCatDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            subCatDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }

        // 设置背景透明，适配圆角背景
        subCatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // 3. 绑定控件
        TextView tvTitle = subCatView.findViewById(R.id.tv_title);
        tvTitle.setText(parentCategory + " - 选择细分");

        ChipGroup cgSubCategories = subCatView.findViewById(R.id.cg_sub_categories);
        TextView tvEmpty = subCatView.findViewById(R.id.tv_empty);        // 空状态文本
        View nsvContainer = subCatView.findViewById(R.id.nsv_container); // 容器
        Button btnCancel = subCatView.findViewById(R.id.btn_cancel);       // 取消按钮

        // 4. 处理数据填充与空状态 (模仿 RecordFragment)
        if (subCats.isEmpty()) {
            cgSubCategories.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            // 稍微撑开高度，避免空状态下弹窗太扁
            nsvContainer.setMinimumHeight(150);
        } else {
            cgSubCategories.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            // 准备颜色资源 (保持一致的胶囊风格)
            int bgDefault = ContextCompat.getColor(context, R.color.cat_unselected_bg);
            int bgChecked = ContextCompat.getColor(context, R.color.app_yellow);
            int textDefault = ContextCompat.getColor(context, R.color.text_primary);
            int textChecked = ContextCompat.getColor(context, R.color.cat_selected_text);

            int[][] states = new int[][] {
                    new int[] { android.R.attr.state_checked },
                    new int[] { }
            };
            ColorStateList bgStateList = new ColorStateList(states, new int[] { bgChecked, bgDefault });
            ColorStateList textStateList = new ColorStateList(states, new int[] { textChecked, textDefault });

            // 动态添加胶囊
            for (String subCatName : subCats) {
                // ... (Chip 创建和样式设置保持不变)
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

                // 【修改】点击事件：支持取消选择
                chip.setOnClickListener(v -> {
                    if (subCatName.equals(selectedSubCategory)) {
                        // 取消选择
                        selectedSubCategory = null;
                        Toast.makeText(this, "已取消细分", Toast.LENGTH_SHORT).show();
                    } else {
                        // 选中
                        selectedSubCategory = subCatName;
                        Toast.makeText(this, "已选择: " + subCatName, Toast.LENGTH_SHORT).show();
                    }

                    // 刷新父分类选中状态并关闭
                    if (adapter != null) {
                        adapter.setSelectedCategory(parentCategory);
                    }
                    subCatDialog.dismiss();
                });

                cgSubCategories.addView(chip);
            }
        }

        // 5. 取消按钮逻辑 (模仿 RecordFragment)
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
        }
    }

    // 【修改】添加 subCategory 参数
    private void saveToDatabase(double amount, int type, String category, String subCategory, String note, String remark, int assetId, String currencySymbol) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());

            Transaction t = new Transaction();
            t.date = System.currentTimeMillis();
            t.type = type;
            t.category = category;
            t.subCategory = subCategory; // 保存二级分类
            t.amount = amount;
            t.note = note;
            t.remark = remark;
            t.assetId = assetId; 
            t.currencySymbol = currencySymbol; 
            db.transactionDao().insert(t);

            if (assetId != 0) {
                AssetAccount asset = db.assetAccountDao().getAssetByIdSync(assetId);
                if (asset != null) {
                    if (type == 1) {
                        asset.amount += amount;
                    } else {
                        asset.amount -= amount;
                    }
                    db.assetAccountDao().update(asset);
                }
            }
        });
    }
}