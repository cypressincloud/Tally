package com.example.budgetapp.util;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;
import com.example.budgetapp.R;

public final class AssetIconHelper {
    public static final int ICON_SIZE_DP = 24;

    private AssetIconHelper() {}

    public interface OnSvgSavedListener {
        void onSaved(String svgCode);
    }

    public static String normalizeSvg(@Nullable String rawSvg) {
        return rawSvg == null ? "" : rawSvg.trim();
    }

    public static boolean hasSvgIcon(@Nullable String rawSvg) {
        return !TextUtils.isEmpty(normalizeSvg(rawSvg));
    }

    @Nullable
    public static Drawable createSvgDrawable(Context context, @Nullable String rawSvg) {
        String normalized = normalizeSvg(rawSvg);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            SVG svg = SVG.getFromString(normalized);
            PictureDrawable drawable = new PictureDrawable(svg.renderToPicture());
            int sizePx = dpToPx(context, ICON_SIZE_DP);
            drawable.setBounds(0, 0, sizePx, sizePx);
            return drawable;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean bindSvgIcon(ImageView imageView, @Nullable String rawSvg) {
        Drawable drawable = createSvgDrawable(imageView.getContext(), rawSvg);
        if (drawable == null) {
            imageView.setImageDrawable(null);
            imageView.setVisibility(View.GONE);
            return false;
        }
        imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageDrawable(drawable);
        imageView.setVisibility(View.VISIBLE);
        return true;
    }

    public static void showSvgEditorDialog(Context context,
                                           String assetName,
                                           @Nullable String currentSvg,
                                           OnSvgSavedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_asset_icon_editor, null);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tv_icon_dialog_title);
        EditText etSvg = view.findViewById(R.id.et_svg_code);
        ImageView ivPreview = view.findViewById(R.id.iv_svg_preview);
        TextView tvPreviewState = view.findViewById(R.id.tv_svg_preview_state);

        tvTitle.setText("设置图标" + (TextUtils.isEmpty(assetName) ? "" : " - " + assetName));
        etSvg.setText(normalizeSvg(currentSvg));
        updatePreview(ivPreview, tvPreviewState, etSvg.getText().toString());

        view.findViewById(R.id.btn_preview_svg).setOnClickListener(v ->
                updatePreview(ivPreview, tvPreviewState, etSvg.getText().toString()));

        view.findViewById(R.id.btn_get_icon).setOnClickListener(v -> {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse("https://www.iconfont.cn/?spm=a313x.user_detail.i3.d4d0a486a.25553a81auY3Jo"));
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_clear_svg).setOnClickListener(v -> {
            etSvg.setText("");
            updatePreview(ivPreview, tvPreviewState, "");
        });

        view.findViewById(R.id.btn_cancel_svg).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btn_save_svg).setOnClickListener(v -> {
            String normalized = normalizeSvg(etSvg.getText().toString());
            if (!normalized.isEmpty() && createSvgDrawable(context, normalized) == null) {
                Toast.makeText(context, "SVG 解析失败，请检查代码", Toast.LENGTH_SHORT).show();
                return;
            }
            listener.onSaved(normalized);
            dialog.dismiss();
        });

        dialog.show();
    }

    private static void updatePreview(ImageView imageView, TextView statusView, @Nullable String rawSvg) {
        if (bindSvgIcon(imageView, rawSvg)) {
            statusView.setText("预览成功，图标将按固定尺寸等比缩放显示");
        } else if (hasSvgIcon(rawSvg)) {
            statusView.setText("SVG 解析失败，请检查代码是否完整");
            imageView.setVisibility(View.INVISIBLE);
        } else {
            statusView.setText("未设置图标");
            imageView.setVisibility(View.INVISIBLE);
        }
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
