package com.example.budgetapp.ui;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.budgetapp.R;

import java.io.OutputStream;

public class DonateActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_donate);

        View rootView = findViewById(R.id.donate_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
             androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
             v.setPadding(originalPaddingLeft + insets.left, originalPaddingTop + insets.top, originalPaddingRight + insets.right, originalPaddingBottom + insets.bottom);
             return WindowInsetsCompat.CONSUMED;
        });

        // 二维码图片
        ImageView ivWechat = findViewById(R.id.iv_wechat_qr);
        ImageView ivAlipay = findViewById(R.id.iv_alipay_qr);

        ivWechat.setOnClickListener(v -> {
            Bitmap bitmap = getBitmapFromDrawable(ivWechat.getDrawable());
            if (bitmap != null) {
                showSaveQrConfirmDialog(bitmap, "wechat_pay_qr");
            }
        });

        ivAlipay.setOnClickListener(v -> {
            Bitmap bitmap = getBitmapFromDrawable(ivAlipay.getDrawable());
            if (bitmap != null) {
                showSaveQrConfirmDialog(bitmap, "alipay_pay_qr");
            }
        });
    }

    private void showSaveQrConfirmDialog(Bitmap bitmap, String fileNamePrefix) {
        new AlertDialog.Builder(this)
                .setTitle("保存收款码")
                .setMessage("将收款码保存到系统相册？")
                .setPositiveButton("保存", (dialog, which) -> {
                    saveBitmapToGallery(bitmap, fileNamePrefix);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private Bitmap getBitmapFromDrawable(Drawable drawable) {
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveBitmapToGallery(Bitmap bitmap, String fileNamePrefix) {
        String fileName = fileNamePrefix + "_" + System.currentTimeMillis() + ".png";
        
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Tally");
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "保存失败: 无法创建文件", Toast.LENGTH_SHORT).show();
        }
    }
}