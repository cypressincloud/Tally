package com.example.budgetapp.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;
import com.example.budgetapp.ai.AiAccountingClient;
import com.example.budgetapp.ai.AiConfig;
import com.example.budgetapp.ai.OcrExtractResult;
import com.example.budgetapp.ai.ScreenshotOcrHelper;
import com.example.budgetapp.ai.TransactionDraft;
import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.database.AssetAccount;
import com.example.budgetapp.util.AssistantConfig;
import com.example.budgetapp.util.CategoryManager;
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AiChatActivity extends AppCompatActivity {
    private static final int TYPE_AI_TEXT = 0;
    private static final int TYPE_MINE = 1;
    private static final int TYPE_AI_DRAFTS = 2;

    private RecyclerView rvChat;
    private EditText etInput;
    private ImageButton btnVoice;
    private ImageButton btnImage;
    private ImageButton btnSend;
    private LinearLayout layoutTopNav;
    private LinearLayout layoutBottomInput;

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private ChatAdapter chatAdapter;
    private FinanceViewModel financeViewModel;
    private AiAccountingClient aiClient;
    private AiConfig aiConfig;
    private ScreenshotOcrHelper ocrHelper;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> speechRecognizerLauncher;
    private ActivityResultLauncher<Intent> audioRecorderLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupStatusBarImmersion();
        setContentView(R.layout.activity_ai_chat);

        aiClient = new AiAccountingClient();
        aiConfig = AiConfig.load(this);
        aiClient.setConfig(aiConfig);
        ocrHelper = new ScreenshotOcrHelper();

        layoutTopNav = findViewById(R.id.layout_top_nav);
        layoutBottomInput = findViewById(R.id.layout_bottom_input);
        rvChat = findViewById(R.id.rv_chat);
        etInput = findViewById(R.id.et_chat_input);
        btnVoice = findViewById(R.id.btn_voice_input);
        btnImage = findViewById(R.id.btn_image_input);
        btnSend = findViewById(R.id.btn_send);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        setupWindowInsets();
        setupInputElevationEffect();
        setupLaunchers();

        financeViewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(chatAdapter);

        addMessage(ChatMessage.aiText("可以直接输入一句账单、说一句话，或者发一张支付截图给我。我会先整理成卡片，你可以直接保存，也可以先改。"));

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                return;
            }
            addMessage(ChatMessage.mine(text, null));
            etInput.setText("");
            processTextAccounting(text);
        });

        btnImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        btnVoice.setOnClickListener(v -> startSpeechRecognition());
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void setupLaunchers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleImageUri(uri);
                        }
                    }
                }
        );

        speechRecognizerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (results != null && !results.isEmpty()) {
                            String text = results.get(0).trim();
                            if (!text.isEmpty()) {
                                addMessage(ChatMessage.mine(text, null));
                                processTextAccounting(text);
                                return;
                            }
                        }
                    }
                    startAudioFallback("系统语音识别失败。");
                }
        );

        audioRecorderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            transcribeAudioUri(uri);
                        } else {
                            Toast.makeText(this, "没有拿到录音文件。", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                handleImageUri(uri);
            }
        }
    }

    private void startSpeechRecognition() {
        if (!ensureTextReady()) {
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "说一句账单内容");
        try {
            speechRecognizerLauncher.launch(intent);
        } catch (Exception e) {
            startAudioFallback("当前设备语音识别不可用。");
        }
    }

    private void startAudioFallback(String message) {
        if (!ensureTextReady()) {
            return;
        }
        if (!aiConfig.isAudioReady()) {
            Toast.makeText(this, message + " 未配置音频模型，请改用文本输入。", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, message + " 将改为录音转写。", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        try {
            audioRecorderLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "当前设备无法录音，请改用文本输入。", Toast.LENGTH_LONG).show();
        }
    }

    private void transcribeAudioUri(Uri uri) {
        int statusIndex = addMessage(ChatMessage.aiText("正在转写语音..."));
        new Thread(() -> {
            try {
                byte[] audioBytes = readBytes(uri);
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null || mimeType.isEmpty()) {
                    mimeType = "audio/*";
                }
                String transcript = aiClient.transcribeAudio(audioBytes, "voice-input", mimeType);
                runOnUiThread(() -> {
                    updateMessage(statusIndex, "音频模型已转成文字，正在解析账单...");
                    addMessage(ChatMessage.mine(transcript, null));
                    processTextAccounting(transcript, statusIndex);
                });
            } catch (Exception e) {
                runOnUiThread(() -> updateMessage(statusIndex, "语音转写失败：" + e.getMessage()));
            }
        }).start();
    }

    private void handleImageUri(Uri uri) {
        if (!ensureScreenshotReady()) {
            return;
        }
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                Toast.makeText(this, "无法读取图片。", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap scaledBitmap = scaleBitmap(bitmap, 1200);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);

            addMessage(ChatMessage.mine("请识别这张截图里的账单。", scaledBitmap));
            processImageAccounting(scaledBitmap, outputStream.toByteArray(), "image/jpeg");
        } catch (Exception e) {
            Toast.makeText(this, "图片处理失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processTextAccounting(String text) {
        int statusIndex = addMessage(ChatMessage.aiText("正在解析账单..."));
        processTextAccounting(text, statusIndex);
    }

    private void processTextAccounting(String text, int statusIndex) {
        if (!ensureTextReady()) {
            updateMessage(statusIndex, "请先在设置里启用 AI，并至少配置 Base URL、API Key、文本模型。");
            return;
        }
        new Thread(() -> {
            try {
                List<TransactionDraft> drafts = aiClient.parseText(this, text);
                List<AssetAccount> assets = loadAccountingAssets();
                runOnUiThread(() -> {
                    removeMessage(statusIndex);
                    addDraftCardsReply(drafts, assets, "我先帮你整理成卡片了。确认没问题就直接保存，要改也可以在卡片里改。");
                });
            } catch (Exception e) {
                runOnUiThread(() -> updateMessage(statusIndex, "文本记账失败：" + e.getMessage()));
            }
        }).start();
    }

    private void processImageAccounting(Bitmap bitmap, byte[] imageBytes, String mimeType) {
        int statusIndex = addMessage(ChatMessage.aiText("正在做 OCR 识别..."));
        new Thread(() -> {
            String fallbackReason = "";
            try {
                OcrExtractResult ocrResult = ocrHelper.extract(bitmap);
                if (!ocrResult.hasText()) {
                    fallbackReason = "OCR 没提取到有效文字";
                } else {
                    runOnUiThread(() -> updateMessage(statusIndex, "OCR 已提取文字，正在用文本模型生成账单..."));
                    try {
                        List<TransactionDraft> drafts = aiClient.parseText(this, ocrResult.buildPrompt());
                        List<AssetAccount> assets = loadAccountingAssets();
                        if (ocrResult.isSufficientForAccounting() || !aiConfig.isVisionReady()) {
                            runOnUiThread(() -> {
                                removeMessage(statusIndex);
                                if (!ocrResult.isSufficientForAccounting() && !aiConfig.isVisionReady()) {
                                    addMessage(ChatMessage.aiText("这张图 OCR 信息不算完整，但你没配视觉模型，所以我先按 OCR 结果给你出卡片，建议重点看看金额和资产。"));
                                }
                                addDraftCardsReply(drafts, assets, "截图我已经整理好了。下面这些卡片可以直接保存，也可以继续修改。");
                            });
                            return;
                        }
                        fallbackReason = ocrResult.confidenceHint + "，正在改用视觉模型补识别";
                    } catch (Exception e) {
                        fallbackReason = "OCR 文字提取到了，但文本模型解析失败：" + e.getMessage();
                    }
                }
            } catch (Exception e) {
                fallbackReason = "OCR 识别失败：" + e.getMessage();
            }

            fallbackToVisionOrFail(statusIndex, imageBytes, mimeType, fallbackReason);
        }).start();
    }

    private void fallbackToVisionOrFail(int statusIndex, byte[] imageBytes, String mimeType, String fallbackReason) {
        if (!aiConfig.isVisionReady()) {
            runOnUiThread(() -> updateMessage(statusIndex, fallbackReason + "\n未配置视觉模型，无法继续补识别。"));
            return;
        }

        runOnUiThread(() -> updateMessage(statusIndex, fallbackReason + "\n正在调用视觉模型继续识别..."));
        try {
            List<TransactionDraft> drafts = aiClient.parseVisionImage(this, "请提取截图里的记账信息。", imageBytes, mimeType);
            List<AssetAccount> assets = loadAccountingAssets();
            runOnUiThread(() -> {
                removeMessage(statusIndex);
                addMessage(ChatMessage.aiText("这张图我已经切到视觉模型补识别了。"));
                addDraftCardsReply(drafts, assets, "下面是我整理出的账单卡片。");
            });
        } catch (Exception e) {
            runOnUiThread(() -> updateMessage(statusIndex, fallbackReason + "\n视觉模型兜底失败：" + e.getMessage()));
        }
    }

    private boolean ensureTextReady() {
        aiConfig = AiConfig.load(this);
        aiClient.setConfig(aiConfig);
        if (aiConfig.isEnabledAndReady()) {
            return true;
        }
        Toast.makeText(this, "请先在设置里启用 AI，并至少配置 Base URL、API Key、文本模型。", Toast.LENGTH_LONG).show();
        return false;
    }

    private boolean ensureScreenshotReady() {
        aiConfig = AiConfig.load(this);
        aiClient.setConfig(aiConfig);
        if (aiConfig.enabled && aiConfig.hasAnyScreenshotSupport()) {
            return true;
        }
        Toast.makeText(this, "截图记账至少需要启用 AI，并配置 Base URL、API Key、文本模型。", Toast.LENGTH_LONG).show();
        return false;
    }

    private List<AssetAccount> loadAccountingAssets() {
        List<AssetAccount> rawAssets = AppDatabase.getDatabase(this).assetAccountDao().getAllAssetsSync();
        List<AssetAccount> assets = new ArrayList<>();
        if (rawAssets != null) {
            for (AssetAccount asset : rawAssets) {
                if (asset != null && (asset.type == 0 || asset.type == 1 || asset.type == 2)) {
                    assets.add(asset);
                }
            }
        }
        return assets;
    }

    private void addDraftCardsReply(List<TransactionDraft> drafts, List<AssetAccount> assets, String intro) {
        List<DraftCardModel> models = new ArrayList<>();
        for (TransactionDraft draft : drafts) {
            models.add(new DraftCardModel(draft));
        }
        addMessage(ChatMessage.aiDrafts(intro, models, new ArrayList<>(assets)));
    }

    private void setupStatusBarImmersion() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(true);
        }
    }

    private void setupWindowInsets() {
        View rootView = findViewById(R.id.ai_chat_root);
        final int padding16px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        final int padding12px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            if (layoutTopNav != null) {
                layoutTopNav.setPadding(padding16px, systemBars.top + padding16px, padding16px, padding16px);
            }
            if (layoutBottomInput != null) {
                int bottomInset = Math.max(systemBars.bottom, ime.bottom);
                layoutBottomInput.setPadding(padding12px, padding12px, padding12px, bottomInset + padding12px);
            }
            if (ime.bottom > 0 && !chatMessages.isEmpty()) {
                rvChat.post(() -> rvChat.smoothScrollToPosition(chatMessages.size() - 1));
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupInputElevationEffect() {
        etInput.setOnFocusChangeListener((v, hasFocus) -> {
            float dp = hasFocus ? 8 : 2;
            layoutBottomInput.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
        });
    }

    private int addMessage(ChatMessage message) {
        chatMessages.add(message);
        int index = chatMessages.size() - 1;
        chatAdapter.notifyItemInserted(index);
        rvChat.smoothScrollToPosition(index);
        return index;
    }

    private void updateMessage(int index, String newText) {
        if (index >= 0 && index < chatMessages.size()) {
            chatMessages.get(index).content = newText;
            chatAdapter.notifyItemChanged(index);
            rvChat.smoothScrollToPosition(index);
        }
    }

    private void removeMessage(int index) {
        if (index >= 0 && index < chatMessages.size()) {
            chatMessages.remove(index);
            chatAdapter.notifyItemRemoved(index);
        }
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IllegalArgumentException("无法读取文件。");
            }
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toByteArray();
        }
    }

    private Bitmap scaleBitmap(Bitmap bitmap, int maxSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
        if (scale >= 1f) {
            return bitmap;
        }
        return Bitmap.createScaledBitmap(bitmap, Math.round(width * scale), Math.round(height * scale), true);
    }

    private String getTypeLabel(int type) {
        return type == 1 ? "收入" : "支出";
    }

    private String getAssetName(List<AssetAccount> assets, int assetId) {
        if (assetId == 0) {
            return "不关联资产";
        }
        if (assets != null) {
            for (AssetAccount asset : assets) {
                if (asset.id == assetId) {
                    return asset.name;
                }
            }
        }
        return "不关联资产";
    }

    private List<String> getPrimaryCategories(int type) {
        List<String> source = type == 1
                ? CategoryManager.getIncomeCategories(this)
                : CategoryManager.getExpenseCategories(this);
        List<String> categories = new ArrayList<>();
        if (source != null) {
            for (String category : source) {
                if (category != null && !category.trim().isEmpty()) {
                    categories.add(category.trim());
                }
            }
        }
        if (!categories.contains("其他")) {
            categories.add("其他");
        }
        return categories;
    }

    private int resolvePreferredAssetId(List<AssetAccount> assets, int currentAssetId) {
        if (currentAssetId > 0) {
            for (AssetAccount asset : assets) {
                if (asset.id == currentAssetId) {
                    return currentAssetId;
                }
            }
        }
        int defaultAssetId = new AssistantConfig(this).getDefaultAssetId();
        if (defaultAssetId > 0) {
            for (AssetAccount asset : assets) {
                if (asset.id == defaultAssetId) {
                    return defaultAssetId;
                }
            }
        }
        return 0;
    }

    private String getCurrencySymbol() {
        boolean currencyEnabled = getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("enable_currency", false);
        return currencyEnabled
                ? getSharedPreferences("app_prefs", MODE_PRIVATE).getString("default_currency_symbol", "¥")
                : "¥";
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            ChatMessage message = chatMessages.get(position);
            if (message.kind == MessageKind.DRAFTS) {
                return TYPE_AI_DRAFTS;
            }
            return message.isMine ? TYPE_MINE : TYPE_AI_TEXT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_MINE) {
                return new TextMessageViewHolder(inflater.inflate(R.layout.item_chat_message_mine, parent, false));
            }
            if (viewType == TYPE_AI_DRAFTS) {
                return new DraftMessageViewHolder(inflater.inflate(R.layout.item_chat_message_ai_drafts, parent, false));
            }
            return new TextMessageViewHolder(inflater.inflate(R.layout.item_chat_message_ai, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage message = chatMessages.get(position);
            if (holder instanceof DraftMessageViewHolder) {
                holder.setIsRecyclable(false);
                ((DraftMessageViewHolder) holder).bind(message);
            } else if (holder instanceof TextMessageViewHolder) {
                ((TextMessageViewHolder) holder).bind(message);
            }
        }

        @Override
        public int getItemCount() {
            return chatMessages.size();
        }
    }

    private static class TextMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvContent;
        private final ImageView ivImage;

        TextMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_chat_text);
            ivImage = itemView.findViewById(R.id.iv_chat_image);
        }

        void bind(ChatMessage message) {
            tvContent.setText(message.content);
            if (message.image != null) {
                ivImage.setVisibility(View.VISIBLE);
                ivImage.setImageBitmap(message.image);
            } else {
                ivImage.setVisibility(View.GONE);
            }
        }
    }

    private class DraftMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvContent;
        private final LinearLayout layoutDraftCards;

        DraftMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_chat_text);
            layoutDraftCards = itemView.findViewById(R.id.layout_draft_cards);
        }

        void bind(ChatMessage message) {
            tvContent.setText(message.content);
            layoutDraftCards.removeAllViews();
            for (int i = 0; i < message.draftCards.size(); i++) {
                View cardView = LayoutInflater.from(AiChatActivity.this)
                        .inflate(R.layout.item_ai_draft_card, layoutDraftCards, false);
                new DraftCardController(cardView, message.draftCards.get(i), message.assets, i + 1).bind();
                layoutDraftCards.addView(cardView);
            }
        }
    }

    private class DraftCardController {
        private final View root;
        private final DraftCardModel model;
        private final List<AssetAccount> assets;
        private final int index;

        private final TextView tvIndex;
        private final TextView tvTitle;
        private final TextView tvStatus;
        private final TextView tvDetail;
        private final LinearLayout layoutEditor;
        private final Spinner spType;
        private final EditText etAmount;
        private final Spinner spCategory;
        private final Spinner spSubCategory;
        private final Spinner spAsset;
        private final EditText etNote;
        private final CheckBox cbExcludeBudget;
        private final TextView btnEdit;
        private final TextView btnSave;
        private final List<AssetAccount> selectableAssets = new ArrayList<>();

        DraftCardController(View root, DraftCardModel model, List<AssetAccount> assets, int index) {
            this.root = root;
            this.model = model;
            this.assets = assets == null ? new ArrayList<>() : assets;
            this.index = index;

            tvIndex = root.findViewById(R.id.tv_draft_index);
            tvTitle = root.findViewById(R.id.tv_draft_title);
            tvStatus = root.findViewById(R.id.tv_draft_status);
            tvDetail = root.findViewById(R.id.tv_draft_detail);
            layoutEditor = root.findViewById(R.id.layout_editor);
            spType = root.findViewById(R.id.sp_type);
            etAmount = root.findViewById(R.id.et_amount);
            spCategory = root.findViewById(R.id.sp_category);
            spSubCategory = root.findViewById(R.id.sp_sub_category);
            spAsset = root.findViewById(R.id.sp_asset);
            etNote = root.findViewById(R.id.et_note);
            cbExcludeBudget = root.findViewById(R.id.cb_exclude_budget);
            btnEdit = root.findViewById(R.id.btn_edit);
            btnSave = root.findViewById(R.id.btn_save);
        }

        void bind() {
            tvIndex.setText("账单建议 " + index);
            setupForm();
            fillForm(model.draft);
            bindSummary();
            updateEditorState();

            btnEdit.setOnClickListener(v -> {
                if (model.saved) {
                    return;
                }
                model.editing = !model.editing;
                updateEditorState();
            });

            btnSave.setOnClickListener(v -> saveDraft());
        }

        private void setupForm() {
            ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                    AiChatActivity.this,
                    android.R.layout.simple_spinner_dropdown_item,
                    Arrays.asList("支出", "收入")
            );
            spType.setAdapter(typeAdapter);
            spType.setOnItemSelectedListener(new SimpleItemSelectedListener() {
                @Override
                public void onItemSelected(int position) {
                    updateCategoryAdapter(position == 1 ? 1 : 0, null, null);
                }
            });
            spCategory.setOnItemSelectedListener(new SimpleItemSelectedListener() {
                @Override
                public void onItemSelected(int position) {
                    updateSubCategoryAdapter(null);
                }
            });

            selectableAssets.clear();
            AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
            noAsset.id = 0;
            selectableAssets.add(noAsset);
            selectableAssets.addAll(assets);
            List<String> assetNames = new ArrayList<>();
            for (AssetAccount asset : selectableAssets) {
                assetNames.add(asset.name);
            }
            spAsset.setAdapter(new ArrayAdapter<>(AiChatActivity.this, android.R.layout.simple_spinner_dropdown_item, assetNames));
            etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }

        private void fillForm(TransactionDraft draft) {
            spType.setSelection(draft.type == 1 ? 1 : 0, false);
            etAmount.setText(String.format(Locale.getDefault(), "%.2f", draft.amount));
            etNote.setText(draft.note == null ? "" : draft.note);
            cbExcludeBudget.setChecked(draft.excludeFromBudget);
            updateCategoryAdapter(draft.type, draft.category, draft.subCategory);

            int preferredAssetId = resolvePreferredAssetId(assets, draft.assetId);
            int selectedAssetIndex = 0;
            for (int i = 0; i < selectableAssets.size(); i++) {
                if (selectableAssets.get(i).id == preferredAssetId) {
                    selectedAssetIndex = i;
                    break;
                }
            }
            spAsset.setSelection(selectedAssetIndex, false);
        }

        private void updateCategoryAdapter(int type, String selectedCategory, String selectedSubCategory) {
            List<String> categories = getPrimaryCategories(type);
            spCategory.setAdapter(new ArrayAdapter<>(AiChatActivity.this, android.R.layout.simple_spinner_dropdown_item, categories));
            int categoryIndex = Math.max(0, categories.indexOf(selectedCategory));
            spCategory.setSelection(categoryIndex, false);
            updateSubCategoryAdapter(selectedSubCategory);
        }

        private void updateSubCategoryAdapter(String selectedSubCategory) {
            String category = spCategory.getSelectedItem() == null ? "其他" : spCategory.getSelectedItem().toString();
            List<String> subCategories = new ArrayList<>();
            subCategories.add("无二级分类");
            List<String> savedSubCategories = CategoryManager.getSubCategories(AiChatActivity.this, category);
            if (savedSubCategories != null) {
                for (String subCategory : savedSubCategories) {
                    if (subCategory != null && !subCategory.trim().isEmpty()) {
                        subCategories.add(subCategory.trim());
                    }
                }
            }
            spSubCategory.setAdapter(new ArrayAdapter<>(AiChatActivity.this, android.R.layout.simple_spinner_dropdown_item, subCategories));
            int index = Math.max(0, subCategories.indexOf(selectedSubCategory));
            spSubCategory.setSelection(index, false);
        }

        private void bindSummary() {
            TransactionDraft draft = model.draft;
            tvTitle.setText(getTypeLabel(draft.type) + " " + draft.currencySymbol + String.format(Locale.getDefault(), "%.2f", draft.amount));

            String categoryLine = draft.category;
            if (draft.subCategory != null && !draft.subCategory.trim().isEmpty()) {
                categoryLine += " / " + draft.subCategory.trim();
            }

            StringBuilder detailBuilder = new StringBuilder();
            detailBuilder.append("分类: ").append(categoryLine);
            detailBuilder.append("\n资产: ").append(getAssetName(assets, draft.assetId));
            if (draft.note != null && !draft.note.trim().isEmpty()) {
                detailBuilder.append("\n备注: ").append(draft.note.trim());
            }
            detailBuilder.append("\n预算: ").append(draft.excludeFromBudget ? "不计入" : "计入");
            tvDetail.setText(detailBuilder.toString());

            tvStatus.setText(model.saved ? "已保存" : "待确认");
            tvStatus.setTextColor(getColor(model.saved ? R.color.expense_green : R.color.app_yellow));
            btnSave.setEnabled(!model.saved);
            btnSave.setAlpha(model.saved ? 0.5f : 1f);
            btnEdit.setEnabled(!model.saved);
            btnEdit.setAlpha(model.saved ? 0.5f : 1f);
        }

        private void updateEditorState() {
            layoutEditor.setVisibility(model.editing && !model.saved ? View.VISIBLE : View.GONE);
            btnEdit.setText(model.saved ? "已保存" : (model.editing ? "收起" : "编辑"));
        }

        private void saveDraft() {
            TransactionDraft updatedDraft = collectDraft();
            if (updatedDraft == null) {
                return;
            }
            model.draft = updatedDraft;
            model.saved = true;
            model.editing = false;
            financeViewModel.addTransaction(updatedDraft.toTransaction());
            bindSummary();
            updateEditorState();
            Toast.makeText(AiChatActivity.this, "已保存这笔账单。", Toast.LENGTH_SHORT).show();
            addMessage(ChatMessage.aiText("这笔我已经帮你入账了。"));
        }

        private TransactionDraft collectDraft() {
            double amount;
            try {
                amount = Double.parseDouble(etAmount.getText().toString().trim());
            } catch (Exception e) {
                Toast.makeText(AiChatActivity.this, "请输入有效金额。", Toast.LENGTH_SHORT).show();
                return null;
            }
            if (amount <= 0d) {
                Toast.makeText(AiChatActivity.this, "金额需要大于 0。", Toast.LENGTH_SHORT).show();
                return null;
            }

            TransactionDraft draft = model.draft;
            draft.type = spType.getSelectedItemPosition() == 1 ? 1 : 0;
            draft.amount = amount;
            draft.category = spCategory.getSelectedItem() == null ? "其他" : spCategory.getSelectedItem().toString();
            String selectedSub = spSubCategory.getSelectedItem() == null ? "" : spSubCategory.getSelectedItem().toString();
            draft.subCategory = "无二级分类".equals(selectedSub) ? "" : selectedSub;
            draft.note = etNote.getText().toString().trim();
            draft.assetId = selectableAssets.get(spAsset.getSelectedItemPosition()).id;
            draft.excludeFromBudget = cbExcludeBudget.isChecked();
            draft.currencySymbol = getCurrencySymbol();
            if (draft.date <= 0L) {
                draft.date = System.currentTimeMillis();
            }
            return draft;
        }
    }

    private static class ChatMessage {
        final boolean isMine;
        final MessageKind kind;
        String content;
        final Bitmap image;
        final List<DraftCardModel> draftCards;
        final List<AssetAccount> assets;

        private ChatMessage(boolean isMine, MessageKind kind, String content, Bitmap image, List<DraftCardModel> draftCards, List<AssetAccount> assets) {
            this.isMine = isMine;
            this.kind = kind;
            this.content = content;
            this.image = image;
            this.draftCards = draftCards == null ? new ArrayList<>() : draftCards;
            this.assets = assets == null ? new ArrayList<>() : assets;
        }

        static ChatMessage mine(String content, Bitmap image) {
            return new ChatMessage(true, MessageKind.TEXT, content, image, null, null);
        }

        static ChatMessage aiText(String content) {
            return new ChatMessage(false, MessageKind.TEXT, content, null, null, null);
        }

        static ChatMessage aiDrafts(String content, List<DraftCardModel> draftCards, List<AssetAccount> assets) {
            return new ChatMessage(false, MessageKind.DRAFTS, content, null, draftCards, assets);
        }
    }

    private static class DraftCardModel {
        TransactionDraft draft;
        boolean saved;
        boolean editing;

        DraftCardModel(TransactionDraft draft) {
            this.draft = draft;
        }
    }

    private enum MessageKind {
        TEXT,
        DRAFTS
    }

    private abstract static class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            onItemSelected(position);
        }

        public abstract void onItemSelected(int position);
    }
}
