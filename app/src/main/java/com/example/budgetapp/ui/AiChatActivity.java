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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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
import com.example.budgetapp.util.ScreenshotAutoSaveManager;
import com.example.budgetapp.viewmodel.FinanceViewModel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
    
    // 语音录制遮罩层
    private FrameLayout voiceRecordingOverlay;
    private TextView tvRecordingHint;
    private TextView tvCancelHint;

    // --- 内部录音专用的变量 ---
    private android.media.MediaRecorder mediaRecorder;
    private java.io.File currentAudioFile;
    private long recordStartTime;

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private ChatAdapter chatAdapter;
    private FinanceViewModel financeViewModel;
    private AiAccountingClient aiClient;
    private AiConfig aiConfig;
    private ScreenshotOcrHelper ocrHelper;
    private ScreenshotAutoSaveManager screenshotAutoSaveManager;
    private String currentScreenshotPath;

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
        screenshotAutoSaveManager = new ScreenshotAutoSaveManager(this);

        layoutTopNav = findViewById(R.id.layout_top_nav);
        layoutBottomInput = findViewById(R.id.layout_bottom_input);
        rvChat = findViewById(R.id.rv_chat);
        etInput = findViewById(R.id.et_chat_input);
        btnVoice = findViewById(R.id.btn_voice_input);
        btnImage = findViewById(R.id.btn_image_input);
        btnSend = findViewById(R.id.btn_send);

        findViewById(R.id.btn_ai_settings).setOnClickListener(v ->
                startActivity(new Intent(this, AiSettingActivity.class)));
        
        voiceRecordingOverlay = findViewById(R.id.voice_recording_overlay);
        tvRecordingHint = findViewById(R.id.tv_recording_hint);
        tvCancelHint = findViewById(R.id.tv_cancel_hint);

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

        btnVoice.setOnTouchListener(new View.OnTouchListener() {
            private float startY;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                        startY = event.getY();
                        // 显示遮罩层
                        voiceRecordingOverlay.setVisibility(View.VISIBLE);
                        tvRecordingHint.setVisibility(View.VISIBLE);
                        tvCancelHint.setVisibility(View.GONE);
                        startInternalRecording(); // 开始应用内录音
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        // 如果手指向上滑动超过 200 像素，显示取消提示
                        if (startY - event.getY() > 200) {
                            tvRecordingHint.setVisibility(View.GONE);
                            tvCancelHint.setVisibility(View.VISIBLE);
                        } else {
                            tvRecordingHint.setVisibility(View.VISIBLE);
                            tvCancelHint.setVisibility(View.GONE);
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        // 隐藏遮罩层
                        voiceRecordingOverlay.setVisibility(View.GONE);
                        // 判断是否是上滑取消，或者异常中断
                        boolean isCancel = event.getAction() == android.view.MotionEvent.ACTION_CANCEL || (startY - event.getY() > 200);
                        if (isCancel && event.getAction() == android.view.MotionEvent.ACTION_UP) {
                            Toast.makeText(AiChatActivity.this, "已取消发送", Toast.LENGTH_SHORT).show();
                        }
                        stopInternalRecording(isCancel); // 停止录音
                        return true;
                }
                return false;
            }
        });
        handleIncomingIntent(getIntent());
    }

    private void startInternalRecording() {
        if (!ensureTextReady()) return;
        
        // 【修改】：如果没有配置音频大模型，直接使用系统语音识别
        if (!aiConfig.isAudioReady()) {
            voiceRecordingOverlay.setVisibility(View.GONE);
            startSystemSpeechRecognition();
            return;
        }
        
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
            return;
        }

        try {
            // 在应用的缓存目录创建一个临时音频文件 (无需存储权限)
            // 使用 M4A 格式 (AAC 编码)，API 支持且质量好
            currentAudioFile = new java.io.File(getCacheDir(), "voice_record.m4a");
            mediaRecorder = new android.media.MediaRecorder();
            mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000); // 128 kbps
            mediaRecorder.setAudioSamplingRate(44100); // 44.1 kHz
            mediaRecorder.setOutputFile(currentAudioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            recordStartTime = System.currentTimeMillis();
            android.util.Log.d("AiChatActivity", "Recording started, file: " + currentAudioFile.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("AiChatActivity", "Failed to start recording", e);
            Toast.makeText(this, "麦克风被占用或无法录音", Toast.LENGTH_SHORT).show();
            stopInternalRecording(true);
        }
    }

    private void stopInternalRecording(boolean isCancel) {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException e) {
                // 如果用户长按时间极短（如零点几秒），stop() 会抛出异常，这里捕获并当做取消处理
                isCancel = true;
            }
            mediaRecorder.release();
            mediaRecorder = null;

            if (!isCancel && currentAudioFile != null && currentAudioFile.exists()) {
                long duration = System.currentTimeMillis() - recordStartTime;
                if (duration < 1000) {
                    Toast.makeText(this, "说话时间太短", Toast.LENGTH_SHORT).show();
                    currentAudioFile.delete();
                } else {
                    // 开始调用 AI 大模型进行转写
                    transcribeInternalAudio(currentAudioFile);
                }
            } else if (currentAudioFile != null && currentAudioFile.exists()) {
                currentAudioFile.delete(); // 用户取消或太短，直接删除临时文件
            }
        }
    }

    private void startSystemSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出记账内容");
        speechRecognizerLauncher.launch(intent);
    }

    private void transcribeInternalAudio(java.io.File file) {
        int statusIndex = addMessage(ChatMessage.aiText("正在转写语音..."));

        new Thread(() -> {
            try {
                // 读取录好的音频文件转化为字节
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[1024];
                int len;
                while ((len = fis.read(b)) != -1) {
                    bos.write(b, 0, len);
                }
                byte[] audioBytes = bos.toByteArray();
                fis.close();
                
                // 添加日志：打印音频文件大小
                android.util.Log.d("AiChatActivity", "Audio file size: " + audioBytes.length + " bytes");
                
                file.delete(); // 内存读取完毕，清理磁盘临时文件

                // 提交给 AI 接口，使用 audio/m4a (M4A 格式，AAC 编码)
                String transcript = aiClient.transcribeAudio(audioBytes, "voice-input.m4a", "audio/m4a");
                runOnUiThread(() -> {
                    updateMessage(statusIndex, "正在为您生成账单...");
                    addMessage(ChatMessage.mine(transcript, null));
                    processTextAccounting(transcript, statusIndex);
                });
            } catch (Exception e) {
                runOnUiThread(() -> updateMessage(statusIndex, "语音转写失败：" + e.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 【删除旧的 mySpeechRecognizer 代码，换成释放 mediaRecorder】
        if (mediaRecorder != null) {
            try {
                // 如果页面意外销毁时还在录音，先停止它
                mediaRecorder.stop();
            } catch (RuntimeException e) {
                // 忽略未处于录制状态的报错
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }
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
        
        // 处理语音触发
        if (intent.getBooleanExtra("trigger_voice", false)) {
            // 延迟触发语音输入，等待界面完全加载
            btnVoice.postDelayed(() -> {
                if (!isFinishing()) {
                    btnVoice.performClick();
                }
            }, 300);
            return;
        }
        
        // 处理图片分享
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                handleImageUri(uri);
            }
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
            
            // 保存截图到照片备注目录（如果功能已启用）
            currentScreenshotPath = screenshotAutoSaveManager.saveScreenshot(scaledBitmap);
            
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
        int statusIndex = addMessage(ChatMessage.aiText("正在识别截图..."));
        new Thread(() -> {
            // Step 1: 优先使用 OCR + 文本模型
            if (aiConfig.isTextReady()) {
                runOnUiThread(() -> updateMessage(statusIndex, "正在用 OCR 识别截图..."));
                try {
                    OcrExtractResult ocrResult = ocrHelper.extract(bitmap);
                    if (ocrResult.hasText()) {
                        runOnUiThread(() -> updateMessage(statusIndex, "OCR 已提取文字，正在用文本模型生成账单..."));
                        try {
                            List<TransactionDraft> drafts = aiClient.parseText(this, ocrResult.buildPrompt());
                            List<AssetAccount> assets = loadAccountingAssets();
                            runOnUiThread(() -> {
                                removeMessage(statusIndex);
                                addDraftCardsReply(drafts, assets, "截图我已经整理好了。下面这些卡片可以直接保存，也可以继续修改。");
                            });
                            return;
                        } catch (Exception e) {
                            // OCR 提取到文字但文本模型解析失败，尝试视觉模型兜底
                            if (aiConfig.isVisionReady()) {
                                runOnUiThread(() -> updateMessage(statusIndex, "文本模型解析失败，尝试用视觉模型识别..."));
                                try {
                                    List<TransactionDraft> drafts = aiClient.parseVisionImage(this, "请提取截图里的记账信息。", imageBytes, mimeType);
                                    List<AssetAccount> assets = loadAccountingAssets();
                                    runOnUiThread(() -> {
                                        removeMessage(statusIndex);
                                        addDraftCardsReply(drafts, assets, "截图我已经整理好了。下面这些卡片可以直接保存，也可以继续修改。");
                                    });
                                    return;
                                } catch (Exception visionError) {
                                    runOnUiThread(() -> updateMessage(statusIndex, "OCR 提取到文字，但文本模型和视觉模型都解析失败：" + e.getMessage()));
                                    return;
                                }
                            } else {
                                runOnUiThread(() -> updateMessage(statusIndex, "OCR 文字提取到了，但文本模型解析失败：" + e.getMessage()));
                                return;
                            }
                        }
                    } else {
                        // OCR 没提取到文字，尝试视觉模型兜底
                        if (aiConfig.isVisionReady()) {
                            runOnUiThread(() -> updateMessage(statusIndex, "OCR 未提取到有效文字，尝试用视觉模型识别..."));
                            try {
                                List<TransactionDraft> drafts = aiClient.parseVisionImage(this, "请提取截图里的记账信息。", imageBytes, mimeType);
                                List<AssetAccount> assets = loadAccountingAssets();
                                runOnUiThread(() -> {
                                    removeMessage(statusIndex);
                                    addDraftCardsReply(drafts, assets, "截图我已经整理好了。下面这些卡片可以直接保存，也可以继续修改。");
                                });
                                return;
                            } catch (Exception visionError) {
                                runOnUiThread(() -> updateMessage(statusIndex, "OCR 和视觉模型都无法识别这张截图。"));
                                return;
                            }
                        } else {
                            runOnUiThread(() -> updateMessage(statusIndex, "没有配置视觉模型，OCR 也没提取到有效文字，无法识别这张截图。"));
                        }
                    }
                } catch (Exception e) {
                    // OCR 识别失败，尝试视觉模型兜底
                    if (aiConfig.isVisionReady()) {
                        runOnUiThread(() -> updateMessage(statusIndex, "OCR 识别失败，尝试用视觉模型识别..."));
                        try {
                            List<TransactionDraft> drafts = aiClient.parseVisionImage(this, "请提取截图里的记账信息。", imageBytes, mimeType);
                            List<AssetAccount> assets = loadAccountingAssets();
                            runOnUiThread(() -> {
                                removeMessage(statusIndex);
                                addDraftCardsReply(drafts, assets, "截图我已经整理好了。下面这些卡片可以直接保存，也可以继续修改。");
                            });
                            return;
                        } catch (Exception visionError) {
                            runOnUiThread(() -> updateMessage(statusIndex, "OCR 和视觉模型都识别失败：" + e.getMessage()));
                            return;
                        }
                    } else {
                        runOnUiThread(() -> updateMessage(statusIndex, "OCR 识别失败：" + e.getMessage()));
                    }
                }
            } else {
                // 没有配置文本模型，尝试视觉模型兜底
                if (aiConfig.isVisionReady()) {
                    runOnUiThread(() -> updateMessage(statusIndex, "未配置文本模型，尝试用视觉模型识别..."));
                    try {
                        List<TransactionDraft> drafts = aiClient.parseVisionImage(this, "请提取截图里的记账信息。", imageBytes, mimeType);
                        List<AssetAccount> assets = loadAccountingAssets();
                        runOnUiThread(() -> {
                            removeMessage(statusIndex);
                            addDraftCardsReply(drafts, assets, "截图我已经整理好了。下面这些卡片可以直接保存，也可以继续修改。");
                        });
                        return;
                    } catch (Exception visionError) {
                        runOnUiThread(() -> updateMessage(statusIndex, "视觉模型识别失败：" + visionError.getMessage()));
                        return;
                    }
                } else {
                    runOnUiThread(() -> updateMessage(statusIndex, "没有配置视觉模型和文本模型，无法识别截图。请先在设置中配置 AI 模型。"));
                }
            }
        }).start();
    }

    private void fallbackToVisionOrFail(int statusIndex, byte[] imageBytes, String mimeType, String fallbackReason) {
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
        // 【新增】：检查是否开启了AI静默记账
        boolean isSilentAiRecordingEnabled = AiConfig.isSilentAiRecordingEnabled(this);
        
        if (isSilentAiRecordingEnabled) {
            // 静默记账模式：直接保存所有账单，然后显示已保存的卡片
            List<DraftCardModel> savedModels = new ArrayList<>();
            int savedCount = 0;
            
            for (TransactionDraft draft : drafts) {
                // 为所有 draft 设置相同的 photoPath（如果有截图）
                if (currentScreenshotPath != null && !currentScreenshotPath.isEmpty()) {
                    draft.photoPath = currentScreenshotPath;
                }
                
                // 直接保存
                boolean saved = false;
                if (draft.isTransfer) {
                    // 转账逻辑
                    if (draft.fromAssetId > 0 && draft.toAssetId > 0) {
                        AssetAccount fromAccount = null;
                        AssetAccount toAccount = null;
                        for (AssetAccount asset : assets) {
                            if (asset.id == draft.fromAssetId) fromAccount = asset;
                            if (asset.id == draft.toAssetId) toAccount = asset;
                        }
                        if (fromAccount != null && toAccount != null) {
                            financeViewModel.transferAsset(fromAccount, toAccount, draft.amount, draft.discount, draft.note);
                            saved = true;
                            savedCount++;
                        }
                    }
                } else {
                    // 普通记账
                    financeViewModel.addTransactionWithAssetSync(draft.toTransaction());
                    saved = true;
                    savedCount++;
                }
                
                // 创建已保存状态的卡片模型
                if (saved) {
                    DraftCardModel model = new DraftCardModel(draft);
                    model.saved = true;  // 标记为已保存
                    model.editing = false;  // 不处于编辑状态
                    savedModels.add(model);
                }
            }
            
            // 清空缓存的截图路径
            currentScreenshotPath = null;
            
            // 显示保存结果和已保存的卡片
            String message = savedCount > 0 ? 
                String.format("已自动保存 %d 笔账单", savedCount) : 
                "没有可保存的账单";
            addMessage(ChatMessage.aiDrafts(message, savedModels, new ArrayList<>(assets)));
            return;
        }
        
        // 原有逻辑：显示卡片让用户确认
        List<DraftCardModel> models = new ArrayList<>();
        for (TransactionDraft draft : drafts) {
            // 为所有 draft 设置相同的 photoPath（如果有截图）
            if (currentScreenshotPath != null && !currentScreenshotPath.isEmpty()) {
                draft.photoPath = currentScreenshotPath;
            }
            models.add(new DraftCardModel(draft));
        }
        // 清空缓存的截图路径
        currentScreenshotPath = null;
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
        return addMessage(message, true);
    }

    private int addMessage(ChatMessage message, boolean autoScroll) {
        chatMessages.add(message);
        int index = chatMessages.size() - 1;
        chatAdapter.notifyItemInserted(index);
        if (autoScroll) {
            rvChat.smoothScrollToPosition(index);
        }
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
                ivImage.setOnClickListener(v -> {
                    ImageViewerActivity.setPendingBitmap(message.image);
                    v.getContext().startActivity(
                            new android.content.Intent(v.getContext(), ImageViewerActivity.class));
                });
            } else {
                ivImage.setVisibility(View.GONE);
                ivImage.setOnClickListener(null);
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
        private final TextView tvStatus;
        private final TextView tvTransactionTime;
        private final ImageButton btnRemove;

        private final LinearLayout layoutSummary;
        private final TextView tvTitle;
        private final TextView tvDetail;

        private final LinearLayout layoutEditor;
        private final RadioGroup rgType;
        private final RecyclerView rvCategory;
        private final EditText etAmount;
        private final Spinner spAsset;
        private final EditText etNote;
        private final CheckBox cbExcludeBudget;

        private final TextView btnEdit;
        private final TextView btnSave;

        private final List<AssetAccount> selectableAssets = new ArrayList<>();
        private CategoryAdapter categoryAdapter;

        private String currentSelectedCategory = "其他";
        private String currentSelectedSubCategory = "";

        DraftCardController(View root, DraftCardModel model, List<AssetAccount> assets, int index) {
            this.root = root;
            this.model = model;
            this.assets = assets == null ? new ArrayList<>() : assets;
            this.index = index;

            tvIndex = root.findViewById(R.id.tv_draft_index);
            tvStatus = root.findViewById(R.id.tv_draft_status);
            tvTransactionTime = root.findViewById(R.id.tv_transaction_time);
            btnRemove = root.findViewById(R.id.btn_remove);

            layoutSummary = root.findViewById(R.id.layout_summary);
            tvTitle = root.findViewById(R.id.tv_draft_title);
            tvDetail = root.findViewById(R.id.tv_draft_detail);

            layoutEditor = root.findViewById(R.id.layout_editor);
            rgType = root.findViewById(R.id.rg_type);
            rvCategory = root.findViewById(R.id.rv_category);
            etAmount = root.findViewById(R.id.et_amount);
            spAsset = root.findViewById(R.id.sp_asset);
            etNote = root.findViewById(R.id.et_note);
            cbExcludeBudget = root.findViewById(R.id.cb_exclude_budget);

            btnEdit = root.findViewById(R.id.btn_edit);
            btnSave = root.findViewById(R.id.btn_save);
        }

        void bind() {
            tvIndex.setText("账单 " + index);
            setupForm();
            fillForm(model.draft);
            bindSummary();
            updateEditorState();
            updateTransactionTime(model.draft.date);

            btnEdit.setOnClickListener(v -> {
                if (model.saved) return;
                model.editing = !model.editing;
                updateEditorState();
            });

            btnSave.setOnClickListener(v -> saveDraft());

            btnRemove.setOnClickListener(v -> {
                root.setVisibility(View.GONE);
            });
        }

        private void setupForm() {
            // 1. 设置类型单选栏
            if (rgType != null) {
                rgType.setOnCheckedChangeListener((group, checkedId) -> {
                    int type = 0; // 默认支出
                    if (checkedId == R.id.rb_income) type = 1;
                    else if (checkedId == R.id.rb_liability) type = 2;
                    else if (checkedId == R.id.rb_lend) type = 3;
                    updateCategoryAdapter(type, currentSelectedCategory, "");
                });
            }

            // 2. 直接复用你原版“记一笔”的 CategoryAdapter
            if (rvCategory != null) {
                // 如果开启了“详细分类”，建议使用 StaggeredGridLayoutManager 或 FlexboxLayoutManager
                // 根据是否开启详细分类，动态选择布局管理器
                boolean isDetailed = com.example.budgetapp.util.CategoryManager.isDetailedCategoryEnabled(AiChatActivity.this);
                if (isDetailed) {
                    // 详细分类：使用 FlexboxLayoutManager 实现横向自适应换行
                    com.google.android.flexbox.FlexboxLayoutManager flexboxLayoutManager = new com.google.android.flexbox.FlexboxLayoutManager(AiChatActivity.this);
                    flexboxLayoutManager.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
                    flexboxLayoutManager.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
                    flexboxLayoutManager.setJustifyContent(com.google.android.flexbox.JustifyContent.FLEX_START);
                    rvCategory.setLayoutManager(flexboxLayoutManager);
                } else {
                    // 默认：5列网格布局
                    rvCategory.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(AiChatActivity.this, 5));
                }

                categoryAdapter = new CategoryAdapter(AiChatActivity.this, new ArrayList<>(), currentSelectedCategory, category -> {
                    // 点击一级分类时触发
                    currentSelectedCategory = category;
                    currentSelectedSubCategory = ""; // 清空二级分类
                });

                // 绑定长按事件
                categoryAdapter.setOnCategoryLongClickListener(category -> {
                    currentSelectedCategory = category;
                    categoryAdapter.setSelectedCategory(category);
                    showSubCategoryDialog(category);
                    return true;
                });

                rvCategory.setAdapter(categoryAdapter);
            }

            selectableAssets.clear();
            AssetAccount noAsset = new AssetAccount("不关联资产", 0, 0);
            noAsset.id = 0;
            selectableAssets.add(noAsset);
            selectableAssets.addAll(assets);
            com.example.budgetapp.util.AssetSpinnerAdapter assetAdapter = new com.example.budgetapp.util.AssetSpinnerAdapter(AiChatActivity.this);
            assetAdapter.addAll(selectableAssets);
            spAsset.setAdapter(assetAdapter);
            com.example.budgetapp.util.AssetSpinnerAdapter.limitDropDownHeight(spAsset);
            etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }

        private void fillForm(TransactionDraft draft) {
            // 转账类型特殊处理
            if (draft.isTransfer) {
                // 隐藏普通记账的UI元素
                rgType.setVisibility(View.GONE);
                rvCategory.setVisibility(View.GONE);
                spAsset.setVisibility(View.GONE);
                cbExcludeBudget.setVisibility(View.GONE);
                
                // 显示金额和备注
                etAmount.setText(String.format(Locale.getDefault(), "%.2f", draft.amount));
                etNote.setText(draft.note == null ? "" : draft.note);
                etNote.setHint("转账备注（可选）");
                
                // TODO: 添加转出/转入资产选择器
                // 暂时在备注中显示提示
                if (draft.note == null || draft.note.isEmpty()) {
                    etNote.setText(draft.fromAsset + " -> " + draft.toAsset);
                }
                
                return;
            }
            
            // 普通记账逻辑
            rgType.setVisibility(View.VISIBLE);
            rvCategory.setVisibility(View.VISIBLE);
            spAsset.setVisibility(View.VISIBLE);
            cbExcludeBudget.setVisibility(View.VISIBLE);
            etNote.setHint("添加备注");
            
            if (draft.type == 1) rgType.check(R.id.rb_income);
            else rgType.check(R.id.rb_expense);

            etAmount.setText(String.format(Locale.getDefault(), "%.2f", draft.amount));
            etNote.setText(draft.note == null ? "" : draft.note);
            cbExcludeBudget.setChecked(draft.excludeFromBudget);

            currentSelectedCategory = draft.category;
            currentSelectedSubCategory = draft.subCategory;
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
            if (selectedCategory == null || !categories.contains(selectedCategory)) {
                currentSelectedCategory = "其他";
            } else {
                currentSelectedCategory = selectedCategory;
            }
            currentSelectedSubCategory = selectedSubCategory == null ? "" : selectedSubCategory;

            if (categoryAdapter != null) {
                categoryAdapter.updateData(categories);
                categoryAdapter.setSelectedCategory(currentSelectedCategory);
            }
        }

        private void showSubCategoryDialog(String primaryCategory) {
            List<String> savedSubCategories = CategoryManager.getSubCategories(AiChatActivity.this, primaryCategory);
            if (savedSubCategories == null || savedSubCategories.isEmpty()) {
                Toast.makeText(AiChatActivity.this, "【" + primaryCategory + "】无二级分类", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. 居中悬浮窗
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(AiChatActivity.this);
            View view = LayoutInflater.from(AiChatActivity.this).inflate(R.layout.dialog_select_sub_category, null);
            builder.setView(view);
            android.app.AlertDialog dialog = builder.create();

            // 2. 悬浮窗透明背景（让 XML 里的圆角完美显示）
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            TextView tvTitle = view.findViewById(R.id.tv_title);
            tvTitle.setText("选择细分");

            com.google.android.material.chip.ChipGroup chipGroup = view.findViewById(R.id.cg_sub_categories);
            chipGroup.removeAllViews();

            // 3. 【核心照搬】：提取 CategoryAdapter.java 里的配色，生成状态颜色列表
            int[][] states = new int[][] {
                    new int[] { android.R.attr.state_checked }, // 选中状态
                    new int[] { -android.R.attr.state_checked } // 未选中状态
            };
            int[] bgColors = new int[] {
                    getColor(R.color.app_blue),           // 选中时的黄色背景
                    getColor(R.color.cat_unselected_bg)     // 未选中时的灰色背景
            };
            int[] textColors = new int[] {
                    getColor(R.color.cat_selected_text),    // 选中时的文字颜色
                    getColor(R.color.cat_unselected_text)   // 未选中时的文字颜色
            };
            android.content.res.ColorStateList bgColorStateList = new android.content.res.ColorStateList(states, bgColors);
            android.content.res.ColorStateList textColorStateList = new android.content.res.ColorStateList(states, textColors);

            // 4. 动态添加并极简美化 Chip
            for (String sub : savedSubCategories) {
                com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(AiChatActivity.this);
                chip.setText(sub);
                chip.setCheckable(true);
                chip.setClickable(true);

                // === 完美照搬 UI 样式 ===
                chip.setChipBackgroundColor(bgColorStateList); // 应用背景色状态
                chip.setTextColor(textColorStateList);         // 应用文字色状态
                chip.setCheckedIconVisible(false);             // 隐藏默认的打勾图标，保持文字居中
                chip.setChipStrokeWidth(0f);                   // 去除自带的边框
                chip.setTextSize(14f);                         // 统一字号

                // 回显之前选中的二级分类
                if (sub.equals(currentSelectedSubCategory)) {
                    chip.setChecked(true);
                }

                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        currentSelectedSubCategory = sub;
                        // 延迟 150 毫秒关闭，让用户能看清丝滑的颜色切换动画
                        buttonView.postDelayed(dialog::dismiss, 150);
                    }
                });
                chipGroup.addView(chip);
            }

            // 绑定取消按钮
            view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

            dialog.show();
        }

        private void bindSummary() {
            TransactionDraft draft = model.draft;
            
            // 转账类型特殊显示
            if (draft.isTransfer) {
                tvTitle.setText("转账 " + draft.currencySymbol + String.format(Locale.getDefault(), "%.2f", draft.amount));
                
                StringBuilder detailBuilder = new StringBuilder();
                detailBuilder.append("转出: ").append(getAssetName(assets, draft.fromAssetId));
                detailBuilder.append("\n转入: ").append(getAssetName(assets, draft.toAssetId));
                if (draft.discount > 0) {
                    detailBuilder.append("\n优惠: ").append(draft.currencySymbol).append(String.format(Locale.getDefault(), "%.2f", draft.discount));
                }
                if (draft.note != null && !draft.note.trim().isEmpty()) {
                    detailBuilder.append("\n备注: ").append(draft.note.trim());
                }
                tvDetail.setText(detailBuilder.toString());
            } else {
                // 普通记账显示
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
            }

            tvStatus.setText(model.saved ? "已入账" : "待确认");
            tvStatus.setTextColor(getColor(model.saved ? R.color.expense_green : R.color.app_blue));
        }

        private void updateTransactionTime(long timestamp) {
            if (timestamp <= 0) {
                timestamp = System.currentTimeMillis();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            String formattedTime = sdf.format(new Date(timestamp));
            tvTransactionTime.setText(formattedTime);
        }

        private void updateEditorState() {
            // 根据状态控制显示：如果是编辑中且没保存，就展示你的漂亮布局
            boolean showEditor = model.editing && !model.saved;
            layoutEditor.setVisibility(showEditor ? View.VISIBLE : View.GONE);
            layoutSummary.setVisibility(showEditor ? View.GONE : View.VISIBLE);

            btnEdit.setText(model.saved ? "已保存" : (model.editing ? "收起" : "修改"));
            btnEdit.setEnabled(!model.saved);
            btnEdit.setAlpha(model.saved ? 0.5f : 1f);

            btnSave.setVisibility(model.saved ? View.GONE : View.VISIBLE);
            btnRemove.setVisibility(model.saved ? View.GONE : View.VISIBLE);
        }

        private void saveDraft() {
            TransactionDraft updatedDraft = collectDraft();
            if (updatedDraft == null) return;

            model.draft = updatedDraft;
            model.saved = true;
            model.editing = false;

            // 判断是否为转账
            if (updatedDraft.isTransfer) {
                // 转账逻辑：调用 transferAsset
                if (updatedDraft.fromAssetId <= 0 || updatedDraft.toAssetId <= 0) {
                    Toast.makeText(AiChatActivity.this, "转账需要指定转出和转入资产。", Toast.LENGTH_SHORT).show();
                    model.saved = false;
                    return;
                }
                
                // 查找转出和转入资产对象
                AssetAccount fromAccount = null;
                AssetAccount toAccount = null;
                for (AssetAccount asset : assets) {
                    if (asset.id == updatedDraft.fromAssetId) {
                        fromAccount = asset;
                    }
                    if (asset.id == updatedDraft.toAssetId) {
                        toAccount = asset;
                    }
                }
                
                if (fromAccount == null || toAccount == null) {
                    Toast.makeText(AiChatActivity.this, "找不到对应的资产账户。", Toast.LENGTH_SHORT).show();
                    model.saved = false;
                    return;
                }
                
                // 执行转账
                financeViewModel.transferAsset(fromAccount, toAccount, updatedDraft.amount, updatedDraft.discount, updatedDraft.note);
                Toast.makeText(AiChatActivity.this, "转账已完成。", Toast.LENGTH_SHORT).show();
            } else {
                // 普通记账逻辑：调用带有资产同步逻辑的方法
                financeViewModel.addTransactionWithAssetSync(updatedDraft.toTransaction());
                Toast.makeText(AiChatActivity.this, "已保存这笔账单。", Toast.LENGTH_SHORT).show();
            }

            bindSummary();
            updateEditorState();
            // 【优化】：保存后不自动滚动到AI回复，让用户停留在当前卡片位置
            addMessage(ChatMessage.aiText("这笔我已经帮你入账了。"), false);
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
            
            // 转账类型：保持原有的转账信息
            if (draft.isTransfer) {
                draft.amount = amount;
                draft.note = etNote.getText().toString().trim();
                // 保持 fromAssetId, toAssetId, discount 等字段不变
                return draft;
            }

            // 普通记账逻辑
            if (rgType != null) {
                int checkedId = rgType.getCheckedRadioButtonId();
                if (checkedId == R.id.rb_income) draft.type = 1;
                    // 【修复】：原本写的是 2 和 3，实际上 2 是资产互转，负债和借出分别是 3 和 4
                else if (checkedId == R.id.rb_liability) draft.type = 3;
                else if (checkedId == R.id.rb_lend) draft.type = 4;
                else draft.type = 0; // 默认支出
            }

            draft.amount = amount;
            draft.category = currentSelectedCategory;
            draft.subCategory = currentSelectedSubCategory;
            draft.note = etNote.getText().toString().trim();
            draft.assetId = selectableAssets.get(spAsset.getSelectedItemPosition()).id;
            draft.excludeFromBudget = cbExcludeBudget.isChecked();
            draft.currencySymbol = getCurrencySymbol();

            if (draft.date <= 0L) {
                draft.date = System.currentTimeMillis();
            }
            
            // 保留 photoPath（如果之前已设置）
            // draft.photoPath 已经在 addDraftCardsReply() 中设置，这里不需要额外处理
            
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
