package com.example.budgetapp.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.util.AssistantConfig; 

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickAddTileService extends TileService {

    private boolean isWindowShowing = false;
    private List<AssetAccount> loadedAssets = new ArrayList<>();

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

        try {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            params.format = PixelFormat.TRANSLUCENT;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            
            // 动态计算宽度
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            
            if (screenWidth > screenHeight) {
                params.width = (int) (400 * metrics.density); 
            } else {
                params.width = (int) (screenWidth * 0.92);
            }

            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;

            LayoutInflater inflater = LayoutInflater.from(this);
            View floatView = inflater.inflate(R.layout.window_confirm_transaction, null);

            isWindowShowing = true;

            EditText etAmount = floatView.findViewById(R.id.et_window_amount);
            RadioGroup rgType = floatView.findViewById(R.id.rg_window_type);
            RadioGroup rgCategory = floatView.findViewById(R.id.rg_window_category);
            EditText etCategory = floatView.findViewById(R.id.et_window_category);
            EditText etNote = floatView.findViewById(R.id.et_window_note);
            EditText etRemark = floatView.findViewById(R.id.et_window_remark);
            Spinner spAsset = floatView.findViewById(R.id.sp_asset);

            Button btnSave = floatView.findViewById(R.id.btn_window_save);
            Button btnCancel = floatView.findViewById(R.id.btn_window_cancel);

            etAmount.setText("");
            etAmount.requestFocus();
            rgType.check(R.id.rb_window_expense);
            rgCategory.check(R.id.rb_cat_food);
            etCategory.setVisibility(View.GONE); // 初始隐藏

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

                    if (assets != null) {
                        loadedAssets.addAll(assets);
                    }

                    List<String> names = new ArrayList<>();
                    for (AssetAccount a : loadedAssets) {
                        names.add(a.name);
                    }

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

            rgType.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_window_income) {
                    rgCategory.setVisibility(View.GONE);
                    etCategory.setVisibility(View.GONE);
                } else {
                    rgCategory.setVisibility(View.VISIBLE);
                    if (rgCategory.getCheckedRadioButtonId() == R.id.rb_window_custom) {
                        etCategory.setVisibility(View.VISIBLE);
                    } else {
                        etCategory.setVisibility(View.GONE);
                    }
                }
            });

            rgCategory.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rb_window_custom) {
                    etCategory.setVisibility(View.VISIBLE);
                } else {
                    etCategory.setVisibility(View.GONE);
                }
            });

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

                    String finalCat = "其他";
                    if (finalType == 1) {
                        finalCat = "收入";
                    } else {
                        int checkedId = rgCategory.getCheckedRadioButtonId();
                        if (checkedId == R.id.rb_window_custom) {
                            String customInput = etCategory.getText().toString().trim();
                            if (!customInput.isEmpty()) {
                                finalCat = customInput;
                            } else {
                                Toast.makeText(this, "自定义分类*", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else if (checkedId == R.id.rb_cat_food) finalCat = "餐饮";
                        else if (checkedId == R.id.rb_cat_ent) finalCat = "娱乐";
                        else if (checkedId == R.id.rb_cat_shop) finalCat = "购物";
                    }

                    int assetId = 0;
                    if (isAssetEnabled) {
                        int selectedPos = spAsset.getSelectedItemPosition();
                        if (selectedPos >= 0 && selectedPos < loadedAssets.size()) {
                            assetId = loadedAssets.get(selectedPos).id;
                        }
                    }

                    saveToDatabase(finalAmount, finalType, finalCat, finalNote, finalRemark, assetId);
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

    private void closeWindow(WindowManager wm, View view) {
        try {
            if (view != null && wm != null) wm.removeView(view);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isWindowShowing = false;
        }
    }

    private void saveToDatabase(double amount, int type, String category, String note, String remark, int assetId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());

            Transaction t = new Transaction();
            t.date = System.currentTimeMillis();
            t.type = type;
            t.category = category;
            t.amount = amount;
            t.note = note;
            t.remark = remark;
            t.assetId = assetId; 
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