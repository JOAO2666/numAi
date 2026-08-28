package io.github.gohoski.numai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.ClipboardManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.api.ApiCallback;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiResult;
import io.github.gohoski.numai.api.ApiService;
import io.github.gohoski.numai.api.GeminiImageResult;
import io.github.gohoski.numai.api.GeminiImageService;
import io.github.gohoski.numai.model.ProviderSnapshot;
import io.github.gohoski.numai.data.ChatManager;
import io.github.gohoski.numai.data.ConfigManager;
import io.github.gohoski.numai.data.MessageManager;
import io.github.gohoski.numai.model.Chat;
import io.github.gohoski.numai.model.Message;
import io.github.gohoski.numai.model.Role;
import io.github.gohoski.numai.search.SearchEngine;
import io.github.gohoski.numai.search.SearchManager;
import io.github.gohoski.numai.search.SearchResult;
import io.github.gohoski.numai.search.WebFetcher;
import io.github.gohoski.numai.ui.MessageAdapter;
import io.github.gohoski.numai.util.Base64;
import io.github.gohoski.numai.util.OpenAiDelta;
import io.github.gohoski.numai.util.SSLDisabler;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_PICK_IMAGE = 1;

    private ApiService apiService;
    private GeminiImageService geminiImageService;
    private ConfigManager config;

    private ListView msgList;
    private View helloLayout;
    private EditText input;
    private MessageAdapter adapter;
    private ImageButton sendBtn;
    private ToggleButton thinkingToggle;
    private ToggleButton imageToggle;
    private ProgressBar progressBar;
    private TextView imgCount;
    private boolean autoScroll = true;
    private boolean isGenerating = false;
    private boolean isThinkingState = false;
    private InputStream currentStream;
    private volatile boolean isCancelled = false;
    private volatile int currentGenerationId = 0;
    private String activeChatId;
    private String activeChatGenerationId;
    private final Handler generationHandler = new Handler();
    private final Map<String, Integer> imageGenerationIds = new HashMap<String, Integer>();
    int UPDATE_DELAY_MS = 250;

    private ImageButton attachBtn;
    private final Object bufferLock = new Object();
    private final StringBuilder thinkBuffer = new StringBuilder();
    private final StringBuilder contentBuffer = new StringBuilder();
    private final List<String> inputImages = new ArrayList<String>();
    private long nextImageId = System.currentTimeMillis();

    private static class StreamToolCall {
        String id = "";
        String name = "";
        StringBuilder arguments = new StringBuilder();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SSLDisabler.disableSSLCertificateChecking();
        // Android versions before Froyo had unreliable HttpURLConnection
        // pooling. Modern devices benefit from connection reuse.
        if (Integer.parseInt(Build.VERSION.SDK) < 8) {
            System.setProperty("http.keepAlive", "false");
        }

        config = ConfigManager.getInstance(this);
        if (config.getConfig().getApiKey().length() == 0) {
            startActivity(new Intent(this, FirstTimeActivity.class));
            finish();
            return;
        }
        UPDATE_DELAY_MS = config.getConfig().getUpdateDelay();

        if (savedInstanceState == null) {
            ChatManager.getInstance().loadChats(this);
            // Keep the process-local current chat when returning from Settings
            // or another Activity; the Chats menu is the explicit new-chat action.
        }

        apiService = new ApiService(this);
        geminiImageService = new GeminiImageService(this);
        msgList = (ListView) findViewById(R.id.messages_list);
        helloLayout = findViewById(R.id.hello_layout);
        input = (EditText) findViewById(R.id.message_input);
        sendBtn = (ImageButton) findViewById(R.id.send_button);
        attachBtn = (ImageButton) findViewById(R.id.attach_button);
        thinkingToggle = (ToggleButton) findViewById(R.id.thinking);
        imageToggle = (ToggleButton) findViewById(R.id.image_generation);
        progressBar = (ProgressBar) findViewById(R.id.waiting);
        imgCount = (TextView) findViewById(R.id.img_count);
        imgCount.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (!inputImages.isEmpty()) {
                    discardPendingImages();
                    Toast.makeText(MainActivity.this, R.string.attachments_cleared,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        sendBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (isGenerating) {
                    stopGeneration();
                } else {
                    sendMessage();
                }
            }
        });

        attachBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                // Use the raw extra name so the app can keep its API 1 compatibility.
                // Android 4.3+ file pickers return multiple selections through ClipData.
                intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)), REQUEST_CODE_PICK_IMAGE);
            }
        });
        attachBtn.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View view) {
                if (inputImages.isEmpty()) return false;
                discardPendingImages();
                Toast.makeText(MainActivity.this, R.string.attachments_cleared,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        imageToggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                updateImageModeUi();
            }
        });
        updateImageModeUi();

        adapter = new MessageAdapter(this, MessageManager.getInstance().getMessages());
        msgList.setAdapter(adapter);
        scrollToBottom();
        updateEmptyState();
        resumeGenerationForCurrentChat();

        if (config.getConfig().getShrinkThink()) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) thinkingToggle.getLayoutParams();
            params.bottomMargin = (int) (3 * getResources().getDisplayMetrics().density + 0.5f);
            thinkingToggle.setLayoutParams(params);
        }

        msgList.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL || scrollState == SCROLL_STATE_FLING) {
                    autoScroll = false;
                }
            }
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
        });

        msgList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                if (position < 0 || position >= adapter.getCount()) return true;
                final Message selectedMsg = adapter.getItem(position);
                if (selectedMsg == null) return true;

                String[] options = new String[]{getString(android.R.string.copy), getString(R.string.regenerate)};
                new AlertDialog.Builder(MainActivity.this)
                        .setItems(options, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    clipboard.setText(selectedMsg.getContent());
                                    Toast.makeText(MainActivity.this, R.string.text_copied, Toast.LENGTH_SHORT).show();
                                } else if (which == 1) {
                                    regenerateFromMessage(selectedMsg);
                                }
                            }
                        })
                        .show();
                return true;
            }
        });
    }

    private void regenerateFromMessage(final Message selectedMsg) {
        stopGeneration();

        List<Message> msgs = MessageManager.getInstance().getMessages();
        int index = msgs.indexOf(selectedMsg);
        if (index == -1) return;

        if (selectedMsg.isSent()) {
            while (msgs.size() > index + 1) {
                msgs.remove(msgs.size() - 1);
            }
        } else {
            while (msgs.size() > index) {
                msgs.remove(msgs.size() - 1);
            }
        }

        if (msgs.isEmpty()) return;

        autoScroll = true;
        currentStream = null;

        sendBtn.setImageResource(R.drawable.ic_action_stop);
        sendBtn.setContentDescription(getString(R.string.stop_generation));
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        discardPendingImages();

        ChatManager.getInstance().onMessageAdded(this);
        refreshMessageAdapter();

        requestAICompletion();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.chats:
                showChatsDialog();
                return true;
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
                return true;
            case R.id.exit:
                currentGenerationId++;
                isCancelled = true;
                if (currentStream != null) {
                    try {
                        currentStream.close();
                    } catch (IOException ignored) {}
                }
                android.os.Process.killProcess(android.os.Process.myPid());
                return true;
            case R.id.about:
                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(15, 15, 15, 15);

                final TextView app = new TextView(this);
                app.setText(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
                app.setTypeface(null, Typeface.BOLD);
                app.setTextSize(20);
                app.setGravity(Gravity.CENTER_HORIZONTAL);
                layout.addView(app);

                final ImageView imageView = new ImageView(this);
                imageView.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                imageView.setImageResource(R.drawable.main_icon);
                layout.addView(imageView);

                final TextView text = new TextView(this);
                String ccLicense = getString(R.string.about_cc);
                SpannableStringBuilder sb = new SpannableStringBuilder(
                        getString(R.string.about_, ccLicense) + "\n\nMIT License\n" +
                                "\n" +
                                "Copyright (c) 2021-2026 Arman Jussupgaliyev\n" +
                                "\n" +
                                "Permission is hereby granted, free of charge, to any person obtaining a copy\n" +
                                "of this software and associated documentation files (the \"Software\"), to deal\n" +
                                "in the Software without restriction, including without limitation the rights\n" +
                                "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n" +
                                "copies of the Software, and to permit persons to whom the Software is\n" +
                                "furnished to do so, subject to the following conditions:\n" +
                                "\n" +
                                "The above copyright notice and this permission notice shall be included in all\n" +
                                "copies or substantial portions of the Software.\n" +
                                "\n" +
                                "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
                                "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
                                "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
                                "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
                                "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
                                "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n" +
                                "SOFTWARE.");
                int linkStart = sb.toString().indexOf(ccLicense);
                if (linkStart >= 0)
                    sb.setSpan(new URLSpan("https://creativecommons.org/licenses/by/3.0/"),
                            linkStart, linkStart + ccLicense.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setText(sb);
                text.setMovementMethod(LinkMovementMethod.getInstance());
                layout.addView(text);

                ScrollView scrollView = new ScrollView(this);
                scrollView.addView(layout);

                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.about)
                        .setView(scrollView)
                        .setNeutralButton(android.R.string.ok, null).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void hideKeyboard() {
        if (Integer.parseInt(Build.VERSION.SDK) >= 3 && input != null) {
            try {
                Class<?> immClass = Class.forName("android.view.inputmethod.InputMethodManager");
                Object imm = getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    Method hideMethod = immClass.getMethod(
                            "hideSoftInputFromWindow",
                            android.os.IBinder.class,
                            Integer.TYPE
                    );
                    hideMethod.invoke(imm, input.getWindowToken(), 0);
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to hide soft keyboard", e);
            }
        }
    }

    private void showChatsDialog() {
        final List<Chat> sortedChats = ChatManager.getInstance().getSortedChats();
        List<String> optionsList = new ArrayList<String>();
        optionsList.add(getString(R.string.new_chat));
        for (int i = 0; i < sortedChats.size(); i++) {
            optionsList.add(sortedChats.get(i).getTitle());
        }
        final String[] options = optionsList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(R.string.chats)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            ChatManager.getInstance().startNewChat();
                            refreshMessageAdapter();
                        } else {
                            final Chat selectedChat = sortedChats.get(which - 1);
                            showChatOptionsDialog(selectedChat);
                        }
                    }
                })
                .show();
    }

    private void showChatOptionsDialog(final Chat chat) {
        String[] actions = new String[]{getString(R.string.open), getString(R.string.delete)};
        new AlertDialog.Builder(this)
                .setTitle(chat.getTitle())
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            ChatManager.getInstance().setCurrentChat(MainActivity.this, chat);
                            refreshMessageAdapter();
                        } else if (which == 1) {
                            confirmDeleteChat(chat);
                        }
                    }
                })
                .show();
    }

    private void confirmDeleteChat(final Chat chat) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_chat_title)
                .setMessage(getString(R.string.delete_chat_message, chat.getTitle()))
                    .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String generationId = ChatProcessingService.getActiveGenerationId(chat.getId());
                        if (generationId != null) {
                            ChatProcessingService.cancelGeneration(MainActivity.this,
                                    chat.getId(), generationId);
                            if (chat.getId().equals(activeChatId)) {
                                activeChatId = null;
                                activeChatGenerationId = null;
                            }
                        }
                        ChatManager.getInstance().deleteChat(MainActivity.this, chat);
                        refreshMessageAdapter();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshMessageAdapter() {
        // Switching chats changes only the displayed adapter; other chat
        // generations remain owned by ChatProcessingService.
        isGenerating = false;
        isCancelled = false;
        activeChatId = null;
        activeChatGenerationId = null;
        autoScroll = true;
        discardPendingImages();
        adapter = new MessageAdapter(this, MessageManager.getInstance().getMessages());
        msgList.setAdapter(adapter);
        scrollToBottom();
        updateEmptyState();
        resumeGenerationForCurrentChat();
    }

    private void resumeGenerationForCurrentChat() {
        if (sendBtn == null || input == null || attachBtn == null || progressBar == null) return;
        Chat current = ChatManager.getInstance().getCurrentChat();
        if (current == null || current.getId() == null) return;
        if (activeChatGenerationId != null && current.getId().equals(activeChatId) &&
                ChatProcessingService.isGenerationActive(current.getId(), activeChatGenerationId)) {
            return;
        }

        String generationId = ChatProcessingService.getActiveGenerationId(current.getId());
        if (generationId == null) {
            if (current.getId().equals(activeChatId)) {
                activeChatId = null;
                activeChatGenerationId = null;
            }
            return;
        }

        activeChatId = current.getId();
        activeChatGenerationId = generationId;
        isGenerating = true;
        isCancelled = false;
        sendBtn.setImageResource(R.drawable.ic_action_stop);
        sendBtn.setContentDescription(getString(R.string.stop_generation));
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        observeGeneration(current, generationId);
    }

    private void updateEmptyState() {
        boolean hasMessages = adapter.getCount() > 0;
        helloLayout.setVisibility(hasMessages ? View.GONE : View.VISIBLE);
        msgList.setVisibility(hasMessages ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            boolean processedMultiple = processSelectedImages(data);
            if (!processedMultiple && data.getData() != null) {
                processSelectedImage(data.getData());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeGenerationForCurrentChat();
    }

    @Override
    protected void onDestroy() {
        generationHandler.removeCallbacksAndMessages(null);
        discardPendingImages();
        super.onDestroy();
    }

    private void discardPendingImages() {
        for (int i = 0; i < inputImages.size(); i++) {
            String fileName = inputImages.get(i);
            if (fileName != null && !fileName.startsWith("data:image")) {
                deleteFile(fileName);
            }
        }
        inputImages.clear();
        if (imgCount != null) {
            imgCount.setText("0");
            imgCount.setVisibility(View.GONE);
        }
    }

    private boolean processSelectedImages(Intent data) {
        try {
            Method getClipData = data.getClass().getMethod("getClipData", new Class[0]);
            Object clipData = getClipData.invoke(data, new Object[0]);
            if (clipData == null) return false;

            Method getItemCount = clipData.getClass().getMethod("getItemCount", new Class[0]);
            Method getItemAt = clipData.getClass().getMethod("getItemAt", new Class[]{Integer.TYPE});
            int itemCount = ((Integer) getItemCount.invoke(clipData, new Object[0])).intValue();

            for (int i = 0; i < itemCount; i++) {
                Object item = getItemAt.invoke(clipData, new Object[]{Integer.valueOf(i)});
                Method getUri = item.getClass().getMethod("getUri", new Class[0]);
                Uri uri = (Uri) getUri.invoke(item, new Object[0]);
                if (uri != null) processSelectedImage(uri);
            }
            return itemCount > 0;
        } catch (Exception ignored) {
            // ClipData does not exist on old Android versions. The single-image
            // data URI fallback in onActivityResult keeps the old behavior working.
            return false;
        }
    }

    private void processSelectedImage(Uri uri) {
        String fileName = "img_" + nextImageId++ + ".jpg";
        FileOutputStream fos = null;
        boolean success = false;
        try {
            Bitmap bitmap = decodeSampledBitmap(this, uri, 1080, 1080);
            if (bitmap != null) {
                fos = openFileOutput(fileName, Context.MODE_PRIVATE);
                success = bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
                bitmap.recycle();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }

        if (success) {
            inputImages.add(fileName);
            imgCount.setVisibility(View.VISIBLE);
            imgCount.setText(String.valueOf(inputImages.size()));
        } else {
            deleteFile(fileName);
            Toast.makeText(this, R.string.space_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollToBottom() {
        if (!autoScroll) return;
        msgList.post(new Runnable() {
            public void run() {
                int count = adapter.getCount();
                if (count > 0) msgList.setSelection(count - 1);
            }
        });
    }

    private void stopGeneration() {
        final Chat current = ChatManager.getInstance().getCurrentChat();
        if (activeChatId != null && activeChatGenerationId != null &&
                activeChatId.equals(current.getId())) {
            ChatProcessingService.cancelGeneration(this, activeChatId,
                    activeChatGenerationId);
            List<Message> currentMessages = current.getMessages();
            for (int i = currentMessages.size() - 1; i >= 0; i--) {
                if (activeChatGenerationId.equals(
                        currentMessages.get(i).getGenerationId())) {
                    currentMessages.remove(i);
                }
            }
            ChatManager.getInstance().onMessageAdded(this, current);
            activeChatId = null;
            activeChatGenerationId = null;
            resetUIState();
            adapter.notifyDataSetChanged();
            updateEmptyState();
            return;
        }

        cancelImageGeneration(current.getId());
        currentGenerationId++;
        isCancelled = true;
        if (currentStream != null) {
            try {
                currentStream.close();
            } catch (IOException ignored) {}
            currentStream = null;
        }

        List<Message> msgs = MessageManager.getInstance().getMessages();
        int size = msgs.size();
        if (size > 0 && msgs.get(size - 1).getRole().equals(Role.ASSISTANT.toString())) {
            Message lastMsg = msgs.get(size - 1);
            if ((lastMsg.getContent() == null || lastMsg.getContent().length() == 0) &&
                    (lastMsg.getToolCalls() == null || lastMsg.getToolCalls().size() == 0)) {
                msgs.remove(size - 1);
            }
        }
        resetUIState();
        ChatManager.getInstance().onMessageAdded(this, current);

        runOnUiThread(new Runnable() {
            public void run() {
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }
        });
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        final boolean generateImage = imageToggle != null && imageToggle.isChecked();
        if (generateImage && text.length() == 0) {
            Toast.makeText(this, R.string.gemini_image_prompt_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (text.length() == 0 && inputImages.isEmpty()) return;
        if (isGenerating) {
            stopGeneration();
        }
        hideKeyboard();
        autoScroll = true;
        currentStream = null;
        final List<String> selectedImages = new ArrayList<String>(inputImages);
        MessageManager.getInstance().addMessage(new Message(Role.USER, text, selectedImages, null));
        ChatManager.getInstance().onMessageAdded(this);
        input.setText("");
        sendBtn.setImageResource(R.drawable.ic_action_stop);
        sendBtn.setContentDescription(getString(R.string.stop_generation));
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        inputImages.clear();
        imgCount.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
        updateEmptyState();
        scrollToBottom();
        if (generateImage) {
            requestGeminiImage(text, selectedImages);
        } else {
            requestAICompletion();
        }
    }

    private void requestAICompletion() {
        isGenerating = true;
        isCancelled = false;
        final boolean thinkingEnabled = thinkingToggle.isChecked();
        final Chat targetChat = ChatManager.getInstance().getCurrentChat();
        final List<Message> messages = targetChat.getMessages();
        if (messages.isEmpty()) {
            resetUIState();
            return;
        }
        final Message userMessage = messages.get(messages.size() - 1);
        activeChatId = targetChat.getId();
        activeChatGenerationId = UUID.randomUUID().toString();
        final ProviderSnapshot snapshot = config.createSnapshot();
        startChatProcessingService(ChatProcessingService.createIntent(this, targetChat,
                userMessage, snapshot, thinkingEnabled, activeChatGenerationId));
        observeGeneration(targetChat, activeChatGenerationId);
    }

    private void startChatProcessingService(Intent intent) {
        try {
            if (Integer.parseInt(Build.VERSION.SDK) >= 26) {
                Method method = Context.class.getMethod("startForegroundService", Intent.class);
                method.invoke(this, intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            startService(intent);
        }
    }

    private void observeGeneration(final Chat targetChat, final String generationId) {
        generationHandler.postDelayed(new Runnable() {
            public void run() {
                boolean active = ChatProcessingService.isGenerationActive(
                        targetChat.getId(), generationId);
                if (ChatManager.getInstance().getCurrentChat() == targetChat) {
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    if (autoScroll) scrollToBottom();
                }
                if (active) {
                    generationHandler.postDelayed(this, UPDATE_DELAY_MS);
                } else if (ChatManager.getInstance().getCurrentChat() == targetChat) {
                    if (generationId.equals(activeChatGenerationId)) {
                        activeChatGenerationId = null;
                        activeChatId = null;
                        resetUIState();
                    }
                }
            }
        }, UPDATE_DELAY_MS);
    }

    private void requestGeminiImage(final String prompt, final List<String> selectedImages) {
        isThinkingState = false;
        isGenerating = true;
        isCancelled = false;
        final Chat targetChat = ChatManager.getInstance().getCurrentChat();
        final String targetChatId = targetChat.getId();
        final int genId = nextImageGenerationId(targetChatId);

        geminiImageService.generate(prompt, selectedImages, new ApiCallback<GeminiImageResult>() {
            @Override
            public void onSuccess(final GeminiImageResult result) {
                if (!isImageGenerationCurrent(targetChatId, genId)) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isImageGenerationCurrent(targetChatId, genId)) return;
                        saveGeminiImageResult(result, targetChat);
                    }
                });
            }

            @Override
            public void onError(final ApiError error) {
                if (!isImageGenerationCurrent(targetChatId, genId)) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isImageGenerationCurrent(targetChatId, genId)) return;
                        handleGenerationError(error.getMessage(), targetChat);
                    }
                });
            }
        });
    }

    private synchronized int nextImageGenerationId(String chatId) {
        Integer current = imageGenerationIds.get(chatId);
        int next = current == null ? 1 : current.intValue() + 1;
        imageGenerationIds.put(chatId, Integer.valueOf(next));
        return next;
    }

    private synchronized boolean isImageGenerationCurrent(String chatId, int generationId) {
        Integer current = imageGenerationIds.get(chatId);
        return current != null && current.intValue() == generationId;
    }

    private synchronized void cancelImageGeneration(String chatId) {
        if (chatId != null) nextImageGenerationId(chatId);
    }

    private void saveGeminiImageResult(GeminiImageResult result, Chat targetChat) {
        String mimeType = result.getMimeType();
        boolean isPng = "image/png".equalsIgnoreCase(mimeType);
        String fileName = "gemini_img_" + nextImageId++ + (isPng ? ".png" : ".jpg");
        FileOutputStream fos = null;
        boolean saved = false;
        try {
            byte[] bytes = Base64.decode(result.getImageData());
            fos = openFileOutput(fileName, Context.MODE_PRIVATE);
            fos.write(bytes);
            fos.flush();
            saved = bytes.length > 0;
        } catch (Exception e) {
            Log.e("GeminiImage", "Could not save generated image", e);
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }

        if (!saved) {
            deleteFile(fileName);
            handleGenerationError(getString(R.string.gemini_image_save_failed), targetChat);
            return;
        }

        String description = result.getText();
        if (description == null || description.trim().length() == 0) {
            description = getString(R.string.gemini_image_done);
        }
        Message generated = new Message(Role.ASSISTANT, description, getString(R.string.gemini_image_model_label));
        generated.setOutputImage(fileName);
        generated.setChatId(targetChat.getId());
        targetChat.getMessages().add(generated);
        ChatManager.getInstance().onMessageAdded(this, targetChat);
        if (ChatManager.getInstance().getCurrentChat() == targetChat) resetUIState();
    }

    private void handleGenerationError(String message, Chat targetChat) {
        Message error = new Message(Role.ASSISTANT, message, getString(R.string.error));
        error.setAsError();
        error.setChatId(targetChat.getId());
        targetChat.getMessages().add(error);
        ChatManager.getInstance().onMessageAdded(this, targetChat);
        if (ChatManager.getInstance().getCurrentChat() == targetChat) {
            resetUIState();
            adapter.notifyDataSetChanged();
            updateEmptyState();
        }
    }

    private void updateImageModeUi() {
        if (imageToggle == null || thinkingToggle == null || input == null) return;
        boolean generatingImage = imageToggle.isChecked();
        if (generatingImage) {
            thinkingToggle.setChecked(false);
        }
        thinkingToggle.setEnabled(!generatingImage);
        input.setHint(generatingImage ? R.string.gemini_image_prompt_hint : R.string.ask_anything);
    }

    private void startResponseStream(final int genId, final InputStream stream, String model, final boolean thinkingEnabled) {
        if (genId != currentGenerationId || isCancelled) {
            if (stream != null) {
                try { stream.close(); } catch (IOException ignored) {}
            }
            return;
        }
        this.currentStream = stream;
        progressBar.setVisibility(View.GONE);
        final Message msg = new Message(Role.ASSISTANT, "", model);
        MessageManager.getInstance().addMessage(msg);
        ChatManager.getInstance().onMessageAdded(this);
        adapter.notifyDataSetChanged();
        updateEmptyState();
        new Thread(new Runnable() {
            public void run() {
                try {
                    readStream(genId, stream, msg, thinkingEnabled);
                } catch (Exception e) {
                    if (genId == currentGenerationId && isGenerating && !isCancelled) {
                        final String err = e.getMessage();
                        runOnUiThread(new Runnable() { public void run() { handleStreamError(err); } });
                    }
                }
            }
        }).start();
    }

    private void handleStreamError(String errorMsg) {
        progressBar.setVisibility(View.GONE);
        Message error = new Message(Role.ASSISTANT, errorMsg, getString(R.string.error));
        error.setAsError();
        MessageManager.getInstance().addMessage(error);
        ChatManager.getInstance().onMessageAdded(this);
        resetUIState();
    }

    private void resetUIState() {
        isGenerating = false;
        adapter.notifyDataSetChanged();
        updateEmptyState();
        autoScroll = true;
        scrollToBottom();
        sendBtn.setImageResource(android.R.drawable.ic_menu_send);
        sendBtn.setContentDescription(getString(R.string.send_message));
        input.setEnabled(true);
        attachBtn.setEnabled(true);
        progressBar.setVisibility(View.GONE);
    }

    private void readStream(final int genId, InputStream inputStream, final Message msg, final boolean thinkingEnabled) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8192);
        String line;
        long lastUpdateTime = 0;
        List<StreamToolCall> streamToolCalls = new ArrayList<StreamToolCall>();

        while (genId == currentGenerationId && !isCancelled && (line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String jsonData = line.substring(5).trim();
            if ("[DONE]".equals(jsonData)) break;
            try {
                JSONObject jsonObj = JSON.getObject(jsonData);
                if (!jsonObj.has("choices")) continue;

                JSONArray choices = jsonObj.getArray("choices");
                if (choices == null || choices.size() == 0) continue;

                JSONObject choiceObj = choices.getObject(0);
                if (choiceObj == null || !choiceObj.has("delta")) continue;

                JSONObject delta = choiceObj.getObject("delta");
                if (delta == null) continue;

                if (delta.has("tool_calls") && !delta.isNull("tool_calls")) {
                    JSONArray tcArr = delta.getArray("tool_calls");
                    if (tcArr != null) {
                        for (int i = 0; i < tcArr.size(); i++) {
                            JSONObject tcObj = tcArr.getObject(i);
                            if (tcObj == null) continue;

                            int index = tcObj.getInt("index", 0);
                            while (streamToolCalls.size() <= index) {
                                streamToolCalls.add(new StreamToolCall());
                            }
                            StreamToolCall stc = streamToolCalls.get(index);
                            if (tcObj.has("id") && !tcObj.isNull("id")) {
                                stc.id = tcObj.getString("id");
                            }
                            if (tcObj.has("function") && !tcObj.isNull("function")) {
                                JSONObject fnObj = tcObj.getObject("function");
                                if (fnObj != null) {
                                    if (fnObj.has("name") && !fnObj.isNull("name")) {
                                        stc.name = fnObj.getString("name");
                                    }
                                    if (fnObj.has("arguments") && !fnObj.isNull("arguments")) {
                                        stc.arguments.append(fnObj.getString("arguments"));
                                    }
                                }
                            }
                        }
                    }
                }

                String contentStr = OpenAiDelta.text(delta, "content");

                String reasoningStr = extractJSONReasoning(delta);
                boolean hasUpdates = false;

                synchronized (bufferLock) {
                    if (thinkingEnabled) {
                        if (reasoningStr != null && reasoningStr.length() > 0) {
                            thinkBuffer.append(reasoningStr);
                            hasUpdates = true;
                        }
                        if (contentStr != null && contentStr.length() > 0) {
                            processContentWithTags(contentStr);
                            hasUpdates = true;
                        }
                    } else {
                        if (contentStr != null && contentStr.length() > 0) {
                            processContentWithTags(contentStr);
                            hasUpdates = true;
                        }
                    }
                }

                long currentTime = System.currentTimeMillis();
                long targetDelay = UPDATE_DELAY_MS;
                int contentLength;
                synchronized (bufferLock) {
                    contentLength = contentBuffer.length() + thinkBuffer.length();
                }
                if (contentLength > 3500) {
                    targetDelay = Math.max(UPDATE_DELAY_MS, 600);
                } else if (contentLength > 1500) {
                    targetDelay = Math.max(UPDATE_DELAY_MS, 400);
                }

                if (hasUpdates && (currentTime - lastUpdateTime >= targetDelay)) {
                    lastUpdateTime = currentTime;
                    updateStreamUI(msg, thinkingEnabled, false);
                }
            } catch (Exception e) {
                Log.e("readStream", "error parsing chunk " + jsonData, e);
            }
        }
        if (genId != currentGenerationId || isCancelled) return;

        final List<StreamToolCall> finalToolCalls = streamToolCalls;
        runOnUiThread(new Runnable() {
            public void run() {
                if (genId != currentGenerationId || isCancelled) return;
                updateStreamUI(msg, thinkingEnabled, true);
                if (!finalToolCalls.isEmpty()) {
                    executeToolCalls(genId, msg, finalToolCalls);
                } else {
                    ChatManager.getInstance().onMessageAdded(MainActivity.this);
                    resetUIState();
                }
            }
        });
    }

    private void executeToolCalls(final int genId, final Message assistantMsg, final List<StreamToolCall> toolCalls) {
        if (genId != currentGenerationId || isCancelled) return;

        JSONArray toolCallsArray = new JSONArray();
        final List<StreamToolCall> executableCalls = new ArrayList<StreamToolCall>();

        for (int i = 0; i < toolCalls.size(); i++) {
            StreamToolCall stc = toolCalls.get(i);
            JSONObject tcJson = new JSONObject();
            tcJson.put("id", stc.id);
            tcJson.put("type", "function");

            JSONObject fnJson = new JSONObject();
            fnJson.put("name", stc.name);
            fnJson.put("arguments", stc.arguments.toString());
            tcJson.put("function", fnJson);
            toolCallsArray.add(tcJson);

            if ("web_search".equals(stc.name) || "web_fetch".equals(stc.name)) {
                executableCalls.add(stc);
            }
        }

        assistantMsg.setToolCalls(toolCallsArray);
        assistantMsg.setSearchResultCount(0);

        if (executableCalls.isEmpty()) {
            resetUIState();
            return;
        }

        final Chat targetChat = ChatManager.getInstance().getCurrentChat();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (genId != currentGenerationId || isCancelled) return;
                adapter.notifyDataSetChanged();
                scrollToBottom();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                int totalResults = 0;
                WebFetcher webFetcher = new WebFetcher();

                for (int i = 0; i < executableCalls.size(); i++) {
                    if (genId != currentGenerationId || isCancelled || ChatManager.getInstance().getCurrentChat() != targetChat) {
                        return;
                    }
                    StreamToolCall stc = executableCalls.get(i);
                    String callId = stc.id;
                    String resultText;

                    if ("web_search".equals(stc.name)) {
                        SearchEngine engine = SearchManager.getInstance().getEngine(MainActivity.this);
                        try {
                            JSONObject args = JSON.getObject(stc.arguments.toString());
                            String q = args.getString("query");
                            List<SearchResult> results = engine.search(q);
                            JSONObject responseJson = new JSONObject();
                            responseJson.put("query", q);
                            if (results == null || results.isEmpty()) {
                                Log.d("SearchEngine", "No results");
                                responseJson.put("status", "no_results");
                                responseJson.put("results", new JSONArray());
                            } else {
                                totalResults += results.size();
                                responseJson.put("status", "success");
                                JSONArray resultsArray = new JSONArray();
                                for (int k = 0; k < results.size(); k++) {
                                    SearchResult res = results.get(k);
                                    JSONObject item = new JSONObject();
                                    item.put("title", res.getTitle());
                                    item.put("url", res.getUrl());
                                    item.put("snippet", res.getSnippet());
                                    resultsArray.add(item);
                                }
                                responseJson.put("results", resultsArray);
                            }
                            resultText = responseJson.toString();
                        } catch (Exception e) {
                            e.printStackTrace();
                            resultText = "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
                        }
                    } else if ("web_fetch".equals(stc.name)) {
                        try {
                            JSONObject args = JSON.getObject(stc.arguments.toString());
                            String targetUrl = args.getString("url");
                            resultText = "URL: " + targetUrl + "\n" + webFetcher.fetch(targetUrl);
                            totalResults++;
                        } catch (Exception e) {
                            e.printStackTrace();
                            resultText = e.getMessage();
                        }
                    } else {
                        resultText = "{\"status\": \"error\", \"message\": \"Unsupported tool call\"}";
                    }
                    Log.i("Tool", resultText);
                    if (genId != currentGenerationId || isCancelled || ChatManager.getInstance().getCurrentChat() != targetChat) {
                        return;
                    }
                    Message toolMessage = new Message(Role.TOOL, resultText);
                    toolMessage.setToolCallId(callId);
                    targetChat.getMessages().add(toolMessage);
                }

                assistantMsg.setSearchResultCount(totalResults);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (genId != currentGenerationId || isCancelled || ChatManager.getInstance().getCurrentChat() != targetChat) {
                            return;
                        }
                        adapter.notifyDataSetChanged();
                        requestAICompletion();
                    }
                });
            }
        }).start();
    }

    private void processContentWithTags(String token) {
        int cursor = 0;
        while (cursor < token.length()) {
            if (isThinkingState) {
                int endTag = token.indexOf("</think>", cursor);
                if (endTag != -1) {
                    thinkBuffer.append(token.substring(cursor, endTag));
                    isThinkingState = false;
                    cursor = endTag + 8;
                } else {
                    thinkBuffer.append(token.substring(cursor));
                    break;
                }
            } else {
                int startTag = token.indexOf("<think>", cursor);
                if (startTag != -1) {
                    contentBuffer.append(token.substring(cursor, startTag));
                    isThinkingState = true;
                    cursor = startTag + 7;
                } else {
                    contentBuffer.append(token.substring(cursor));
                    break;
                }
            }
        }
    }

    private void updateStreamUI(final Message msg, final boolean thinkingEnabled, final boolean isFinal) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateStreamUI(msg, thinkingEnabled, isFinal);
                }
            });
            return;
        }

        String displayContent;
        String displayThink;
        synchronized (bufferLock) {
            displayContent = cleanChannelTokens(contentBuffer.toString());
            displayThink = thinkBuffer.toString();
        }

        if (thinkingEnabled && displayThink.length() > 0) {
            msg.setContent("<think>" + displayThink + "</think>" + displayContent);
        } else {
            msg.setContent(displayContent);
        }

        int firstVis = msgList.getFirstVisiblePosition();
        int lastVis = msgList.getLastVisiblePosition();
        int count = adapter.getCount();
        int targetIndex = count - 1;
        if (targetIndex >= firstVis && targetIndex <= lastVis) {
            View view = msgList.getChildAt(targetIndex - firstVis);
            if (view != null) {
                TextView tvText = (TextView) view.findViewById(R.id.message_text);
                LinearLayout thinkLayout = (LinearLayout) view.findViewById(R.id.thinkingLayout);
                TextView tvThink = (TextView) view.findViewById(R.id.thinkingProcess);
                View vResponse = view.findViewById(R.id.response);

                if (tvText != null) {
                    tvText.setMovementMethod(LinkMovementMethod.getInstance());
                    tvText.setText(msg.getParsedDisplayContent(MainActivity.this, !isFinal));
                }

                if (thinkingEnabled) {
                    boolean hasThinkContent = displayThink.length() > 0;
                    if (hasThinkContent) {
                        thinkLayout.setVisibility(View.VISIBLE);
                        View noThink = view.findViewById(R.id.noThinking);
                        if (noThink != null) noThink.setVisibility(View.GONE);
                        if (tvThink != null) {
                            tvThink.setMovementMethod(LinkMovementMethod.getInstance());
                            tvThink.setText(msg.getParsedThinkContent(MainActivity.this, !isFinal));
                        }
                    } else {
                        thinkLayout.setVisibility(View.VISIBLE);
                        View noThink = view.findViewById(R.id.noThinking);
                        if (noThink != null) noThink.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (thinkLayout != null) thinkLayout.setVisibility(View.GONE);
                }

                if (vResponse != null) {
                    vResponse.setVisibility((displayContent.length() > 0) ? View.VISIBLE : View.GONE);
                }
            }
        }

        if (autoScroll) scrollToBottom();
    }

    private static String cleanChannelTokens(String text) {
        if (text == null || text.length() == 0) return "";
        if (text.indexOf('<') == -1 && text.indexOf('t') == -1 && text.indexOf('T') == -1) {
            return text;
        }
        String cleaned = text.trim();
        if (cleaned.regionMatches(true, 0, "thought", 0, 7)) {
            cleaned = cleaned.substring(7).trim();
        }
        if (cleaned.startsWith("<|channel|>")) {
            cleaned = cleaned.substring(11).trim();
        } else if (cleaned.startsWith("<channel>")) {
            cleaned = cleaned.substring(9).trim();
        }
        return cleaned;
    }

    public static Bitmap decodeSampledBitmap(Context ctx, Uri uri, int reqW, int reqH) throws IOException {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);
        is.close();

        options.inSampleSize = calculateInSampleSize(options, reqW, reqH);
        options.inJustDecodeBounds = false;

        is = ctx.getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(is, null, options);
        is.close();
        return bmp;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqH || width > reqW) {
            if (width > height) {
                while ((width / inSampleSize) > reqW) {
                    inSampleSize *= 2;
                }
            } else {
                while ((height / inSampleSize) > reqH) {
                    inSampleSize *= 2;
                }
            }
        }
        return inSampleSize;
    }

    private String extractJSONReasoning(JSONObject delta) {
        String reasoning = OpenAiDelta.text(delta, "reasoning");
        return reasoning == null ? OpenAiDelta.text(delta, "reasoning_content") : reasoning;
    }
}
