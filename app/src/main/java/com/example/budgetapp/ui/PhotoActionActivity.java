package com.example.budgetapp.ui;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ResultReceiver;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.example.budgetapp.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PhotoActionActivity extends AppCompatActivity {

    public static final String EXTRA_RECEIVER = "result_receiver";
    public static final String KEY_RESULT_URI = "result_uri";
    
    // 【新增】动作类型控制
    public static final String EXTRA_ACTION_TYPE = "action_type";
    public static final int ACTION_SELECT = 0;  // 默认：显示选择弹窗
    public static final int ACTION_CAMERA = 1;  // 直接拍照
    public static final int ACTION_GALLERY = 2; // 直接选图
    public static final int ACTION_VIEW = 3;    // 查看大图
    public static final String EXTRA_IMAGE_URI = "image_uri"; // 查看大图时的URI

    private ResultReceiver resultReceiver;
    private Uri currentPhotoUri;

    private final ActivityResultLauncher<Uri> takePhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success) {
                    saveToBackupFolder(currentPhotoUri);
                } else {
                    finishWithResult(0, null); // Cancelled
                }
            }
    );

    private final ActivityResultLauncher<String> pickPhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    saveToBackupFolder(uri);
                } else {
                    finishWithResult(0, null);
                }
            }
    );
    
    // 【新增】用于查看大图的 Launcher，主要为了监听返回
    private final ActivityResultLauncher<Intent> viewPhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // 查看结束，直接返回
                finishWithResult(0, null);
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        resultReceiver = getIntent().getParcelableExtra(EXTRA_RECEIVER);
        int actionType = getIntent().getIntExtra(EXTRA_ACTION_TYPE, ACTION_SELECT);
        
        switch (actionType) {
            case ACTION_CAMERA:
                launchCamera();
                break;
            case ACTION_GALLERY:
                pickPhotoLauncher.launch("image/*");
                break;
            case ACTION_VIEW:
                String uriStr = getIntent().getStringExtra(EXTRA_IMAGE_URI);
                if (uriStr != null) {
                    launchViewer(Uri.parse(uriStr));
                } else {
                    finish();
                }
                break;
            case ACTION_SELECT:
            default:
                showOptionDialog();
                break;
        }
    }
    
    // ... showOptionDialog 代码保持不变 ...
    private void showOptionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_photo_action, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_action_camera).setOnClickListener(v -> {
            dialog.dismiss();
            launchCamera();
        });
        view.findViewById(R.id.btn_action_gallery).setOnClickListener(v -> {
            dialog.dismiss();
            pickPhotoLauncher.launch("image/*");
        });
        view.findViewById(R.id.btn_action_cancel).setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        dialog.setOnCancelListener(d -> finish());
        dialog.show();
    }

    private void launchCamera() {
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "创建临时文件失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (photoFile != null) {
            currentPhotoUri = FileProvider.getUriForFile(this,
                    "com.example.budgetapp.fileprovider",
                    photoFile);
            takePhotoLauncher.launch(currentPhotoUri);
        }
    }
    
    private void launchViewer(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            viewPhotoLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开图片", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void saveToBackupFolder(Uri sourceUri) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String treeUriStr = prefs.getString("photo_backup_uri", "");
        
        if (treeUriStr.isEmpty()) {
            Toast.makeText(this, "未设置备份路径", Toast.LENGTH_SHORT).show();
            finish(); // 未设置也算结束
            return;
        }

        try {
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, Uri.parse(treeUriStr));
            if (pickedDir == null || !pickedDir.canWrite()) {
                Toast.makeText(this, "无法写入备份文件夹", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "Tally_" + timeStamp + ".jpg";
            DocumentFile newFile = pickedDir.createFile("image/jpeg", fileName);

            if (newFile != null) {
                copyFile(sourceUri, newFile.getUri());
                // Success
                finishWithResult(1, newFile.getUri().toString());
                Toast.makeText(this, "照片已保存", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void finishWithResult(int code, String uriStr) {
        if (resultReceiver != null) {
            Bundle bundle = new Bundle();
            if (uriStr != null) {
                bundle.putString(KEY_RESULT_URI, uriStr);
            }
            resultReceiver.send(code, bundle);
        }
        finish();
    }

    private void copyFile(Uri src, Uri dest) throws IOException {
        ContentResolver resolver = getContentResolver();
        try (InputStream in = resolver.openInputStream(src);
             OutputStream out = resolver.openOutputStream(dest)) {
            if (in == null || out == null) return;
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}