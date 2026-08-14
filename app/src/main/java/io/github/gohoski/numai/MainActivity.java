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
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.api.ApiCallback;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiResult;
import io.github.gohoski.numai.api.ApiService;
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
import io.github.gohoski.numai.util.SSLDisabler;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_PICK_IMAGE = 1;

    // Static process-wide generation state across activity lifecycles
    private static volatile MainActivity currentActivityInstance = null;
    private static volatile int globalGenerationId = 0;
    private static volatile boolean globalCancelled = false;
    private static volatile InputStream globalCurrentStream = null;
    private static volatile boolean isGenerating = false;
    private static volatile boolean isThinkingState = false;
    private static volatile boolean isThinkingEnabled = false;
    private static volatile Message currentAssistantMsg = null;

    private static final Object bufferLock = new Object();
    private static final StringBuilder thinkBuffer = new StringBuilder();
    private static final StringBuilder contentBuffer = new StringBuilder();

    private ApiService apiService;
    private ConfigManager config;

    private ListView msgList;
    private View helloLayout;
    private EditText input;
    private MessageAdapter adapter;
    private ImageButton sendBtn;
    private ToggleButton thinkingToggle;
    private ProgressBar progressBar;
    private TextView imgCount;
    private boolean autoScroll = true;
    int UPDATE_DELAY_MS = 250;

    private ImageButton attachBtn;
    private final List<String> inputImages = new ArrayList<String>();

    private static class StreamToolCall {
        String id = "";
        String name = "";
        StringBuilder arguments = new StringBuilder();
    }

    private static void runOnCurrentActivity(Runnable r) {
        MainActivity act = currentActivityInstance;
        if (act != null) {
            act.runOnUiThread(r);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentActivityInstance = this;
        setContentView(R.layout.activity_main);
        SSLDisabler.disableSSLCertificateChecking();
        System.setProperty("http.keepAlive", "false");

        config = ConfigManager.getInstance(this);
        if (config.getConfig().getApiKey().length() == 0) {
            startActivity(new Intent(this, FirstTimeActivity.class));
            finish();
            return;
        }
        UPDATE_DELAY_MS = config.getConfig().getUpdateDelay();

        if (savedInstanceState == null) {
            ChatManager.getInstance().loadChats(this);
            ChatManager.getInstance().startNewChat();
        }

        apiService = new ApiService(this);
        msgList = (ListView) findViewById(R.id.messages_list);
        helloLayout = findViewById(R.id.hello_layout);
        input = (EditText) findViewById(R.id.message_input);
        sendBtn = (ImageButton) findViewById(R.id.send_button);
        attachBtn = (ImageButton) findViewById(R.id.attach_button);
        thinkingToggle = (ToggleButton) findViewById(R.id.thinking);
        progressBar = (ProgressBar) findViewById(R.id.waiting);
        imgCount = (TextView) findViewById(R.id.img_count);

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
                startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)), REQUEST_CODE_PICK_IMAGE);
            }
        });

        adapter = new MessageAdapter(this, MessageManager.getInstance().getMessages());
        msgList.setAdapter(adapter);
        scrollToBottom();
        updateEmptyState();

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

        // Restore generating state across rotation
        if (isGenerating) {
            sendBtn.setImageResource(R.drawable.ic_action_stop);
            input.setEnabled(false);
            attachBtn.setEnabled(false);
            thinkingToggle.setChecked(isThinkingEnabled);
            if (currentAssistantMsg == null) {
                progressBar.setVisibility(View.VISIBLE);
            } else {
                progressBar.setVisibility(View.GONE);
                updateStreamUI(currentAssistantMsg, isThinkingEnabled, false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentActivityInstance = this;
        if (isGenerating) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            if (currentAssistantMsg != null) {
                updateStreamUI(currentAssistantMsg, isThinkingEnabled, false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentActivityInstance == this) {
            currentActivityInstance = null;
        }
        // Only terminate generation if the user is truly leaving the activity (e.g. Back pressed),
        // not when rotating (configuration change where isFinishing() is false)
        if (isFinishing()) {
            stopGeneration();
        }
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

        // Clean up any trailing orphaned tool messages
        while (!msgs.isEmpty() && msgs.get(msgs.size() - 1).getRoleEnum() == Role.TOOL) {
            msgs.remove(msgs.size() - 1);
        }
        if (!msgs.isEmpty() && msgs.get(msgs.size() - 1).getRoleEnum() == Role.ASSISTANT) {
            Message last = msgs.get(msgs.size() - 1);
            last.setToolCalls(null);
        }

        if (msgs.isEmpty()) return;

        autoScroll = true;
        sendBtn.setImageResource(R.drawable.ic_action_stop);
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        inputImages.clear();
        imgCount.setVisibility(View.GONE);

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
                stopGeneration();
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
                        stopGeneration();
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
                        stopGeneration();
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
                        stopGeneration();
                        ChatManager.getInstance().deleteChat(MainActivity.this, chat);
                        refreshMessageAdapter();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshMessageAdapter() {
        autoScroll = true;
        adapter = new MessageAdapter(this, MessageManager.getInstance().getMessages());
        msgList.setAdapter(adapter);
        scrollToBottom();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (adapter == null || helloLayout == null || msgList == null) return;
        boolean hasMessages = adapter.getCount() > 0;
        helloLayout.setVisibility(hasMessages ? View.GONE : View.VISIBLE);
        msgList.setVisibility(hasMessages ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            processSelectedImage(data.getData());
        }
    }

    private void processSelectedImage(Uri uri) {
        String fileName = "img_" + System.currentTimeMillis() + ".jpg";
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
        if (!autoScroll || msgList == null || adapter == null) return;
        msgList.post(new Runnable() {
            public void run() {
                if (msgList == null || adapter == null) return;
                msgList.post(new Runnable() {
                    public void run() {
                        if (msgList == null || adapter == null) return;
                        adapter.notifyDataSetChanged();
                        int count = adapter.getCount();
                        if (count > 0) {
                            msgList.setSelection(count - 1);
                        }
                    }
                });
            }
        });
    }

    private void stopGeneration() {
        globalGenerationId++;
        globalCancelled = true;
        isGenerating = false;
        currentAssistantMsg = null;
        synchronized (bufferLock) {
            thinkBuffer.setLength(0);
            contentBuffer.setLength(0);
        }
        if (globalCurrentStream != null) {
            try {
                globalCurrentStream.close();
            } catch (IOException ignored) {}
            globalCurrentStream = null;
        }

        // Clean up any incomplete or dangling tool calls / empty assistant turns from the chat
        Chat currentChat = ChatManager.getInstance().getCurrentChat();
        if (currentChat != null) {
            List<Message> msgs = currentChat.getMessages();
            while (!msgs.isEmpty()) {
                Message lastMsg = msgs.get(msgs.size() - 1);
                if (lastMsg.getRoleEnum() == Role.TOOL) {
                    msgs.remove(msgs.size() - 1);
                } else if (lastMsg.getRoleEnum() == Role.ASSISTANT) {
                    boolean hasDisplay = lastMsg.getDisplayRaw() != null && lastMsg.getDisplayRaw().trim().length() > 0;
                    boolean hasThink = lastMsg.getThinkingRaw() != null && lastMsg.getThinkingRaw().trim().length() > 0;
                    boolean hasToolCalls = lastMsg.getToolCalls() != null && lastMsg.getToolCalls().size() > 0;
                    if (!hasDisplay && !hasThink) {
                        msgs.remove(msgs.size() - 1);
                    } else if (hasToolCalls) {
                        lastMsg.setToolCalls(null);
                        if (!hasDisplay && !hasThink) {
                            msgs.remove(msgs.size() - 1);
                        }
                    }
                    break;
                } else {
                    break;
                }
            }
            ChatManager.getInstance().saveChat(this, currentChat);
            ChatManager.getInstance().saveChats(this);
        }

        MainActivity act = currentActivityInstance;
        if (act != null) {
            act.resetUIState();
        } else {
            resetUIState();
        }
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.length() == 0 && inputImages.isEmpty()) return;
        if (isGenerating) {
            stopGeneration();
        }
        hideKeyboard();
        autoScroll = true;
        MessageManager.getInstance().addMessage(new Message(Role.USER, text, new ArrayList<String>(inputImages), null));
        ChatManager.getInstance().onMessageAdded(this);
        input.setText("");
        sendBtn.setImageResource(R.drawable.ic_action_stop);
        input.setEnabled(false);
        attachBtn.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        inputImages.clear();
        imgCount.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
        updateEmptyState();
        scrollToBottom();
        requestAICompletion();
    }

    private void requestAICompletion() {
        synchronized (bufferLock) {
            thinkBuffer.setLength(0);
            contentBuffer.setLength(0);
        }
        currentAssistantMsg = null;
        isThinkingState = false;
        isGenerating = true;
        globalCancelled = false;
        final int genId = ++globalGenerationId;
        final boolean thinkingEnabled = thinkingToggle.isChecked();
        isThinkingEnabled = thinkingEnabled;

        apiService.chatCompletion(MessageManager.getInstance().getMessages(), thinkingEnabled, new ApiCallback<ApiResult>() {
            @Override
            public void onSuccess(final ApiResult apiResult) {
                if (genId != globalGenerationId || globalCancelled) {
                    if (apiResult != null && apiResult.getResult() != null) {
                        try { apiResult.getResult().close(); } catch (IOException ignored) {}
                    }
                    return;
                }
                startResponseStream(genId, apiResult.getResult(), apiResult.getModel(), thinkingEnabled);
            }

            @Override
            public void onError(final ApiError error) {
                if (genId != globalGenerationId || globalCancelled) return;
                runOnCurrentActivity(new Runnable() {
                    public void run() {
                        if (genId != globalGenerationId || globalCancelled) return;
                        handleStreamError(error.getMessage());
                    }
                });
            }
        });
    }

    private void startResponseStream(final int genId, final InputStream stream, String model, final boolean thinkingEnabled) {
        if (genId != globalGenerationId || globalCancelled) {
            if (stream != null) {
                try { stream.close(); } catch (IOException ignored) {}
            }
            return;
        }
        globalCurrentStream = stream;
        final Message msg = new Message(Role.ASSISTANT, "", model);
        currentAssistantMsg = msg;
        MessageManager.getInstance().addMessage(msg);

        MainActivity act = currentActivityInstance;
        ChatManager.getInstance().onMessageAdded(act != null ? act : this);

        runOnCurrentActivity(new Runnable() {
            public void run() {
                MainActivity a = currentActivityInstance;
                if (a != null) {
                    if (a.progressBar != null) a.progressBar.setVisibility(View.GONE);
                    if (a.adapter != null) a.adapter.notifyDataSetChanged();
                    a.updateEmptyState();
                }
            }
        });

        new Thread(new Runnable() {
            public void run() {
                try {
                    readStream(genId, stream, msg, thinkingEnabled);
                } catch (Exception e) {
                    if (genId == globalGenerationId && isGenerating && !globalCancelled) {
                        final String err = e.getMessage();
                        runOnCurrentActivity(new Runnable() {
                            public void run() {
                                handleStreamError(err);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void handleStreamError(String errorMsg) {
        isGenerating = false;
        currentAssistantMsg = null;
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        Chat currentChat = ChatManager.getInstance().getCurrentChat();
        if (currentChat != null) {
            List<Message> msgs = currentChat.getMessages();
            if (!msgs.isEmpty()) {
                Message last = msgs.get(msgs.size() - 1);
                if (last.getRoleEnum() == Role.ASSISTANT &&
                        (last.getContent() == null || last.getContent().trim().length() == 0) &&
                        (last.getToolCalls() == null || last.getToolCalls().size() == 0)) {
                    msgs.remove(msgs.size() - 1);
                } else if (last.getRoleEnum() == Role.ASSISTANT && last.getToolCalls() != null) {
                    last.setToolCalls(null);
                }
            }
        }

        Message error = new Message(Role.ASSISTANT, errorMsg, getString(R.string.error));
        error.setAsError();
        MessageManager.getInstance().addMessage(error);
        ChatManager.getInstance().onMessageAdded(this);
        resetUIState();
    }

    private void resetUIState() {
        isGenerating = false;
        currentAssistantMsg = null;
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
        autoScroll = true;
        scrollToBottom();
        if (sendBtn != null) sendBtn.setImageResource(android.R.drawable.ic_menu_send);
        if (input != null) input.setEnabled(true);
        if (attachBtn != null) attachBtn.setEnabled(true);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
    }

    private void readStream(final int genId, InputStream inputStream, final Message msg, final boolean thinkingEnabled) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8192);
        String line;
        long lastUpdateTime = 0;
        List<StreamToolCall> streamToolCalls = new ArrayList<StreamToolCall>();

        while (genId == globalGenerationId && !globalCancelled && (line = reader.readLine()) != null) {
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

                String contentStr = null;
                if (delta.has("content") && !delta.isNull("content")) {
                    try {
                        contentStr = delta.getString("content");
                    } catch (JSONException ignored) {}
                }

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
                    runOnCurrentActivity(new Runnable() {
                        public void run() {
                            if (genId != globalGenerationId || globalCancelled) return;
                            MainActivity act = currentActivityInstance;
                            if (act != null) {
                                act.updateStreamUI(msg, thinkingEnabled, false);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("readStream", "error parsing chunk " + jsonData, e);
            }
        }
        if (genId != globalGenerationId || globalCancelled) return;

        final List<StreamToolCall> finalToolCalls = streamToolCalls;
        runOnCurrentActivity(new Runnable() {
            public void run() {
                if (genId != globalGenerationId || globalCancelled) return;
                MainActivity act = currentActivityInstance;
                if (act != null) {
                    act.updateStreamUI(msg, thinkingEnabled, true);
                }
                if (!finalToolCalls.isEmpty()) {
                    executeToolCalls(genId, msg, finalToolCalls);
                } else {
                    isGenerating = false;
                    currentAssistantMsg = null;
                    ChatManager.getInstance().onMessageAdded(act != null ? act : MainActivity.this);
                    if (act != null) {
                        act.resetUIState();
                    }
                }
            }
        });
    }

    private void executeToolCalls(final int genId, final Message assistantMsg, final List<StreamToolCall> toolCalls) {
        if (genId != globalGenerationId || globalCancelled) return;

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

        // Finalize the assistant message turn and detach from active buffer before tool execution
        currentAssistantMsg = null;
        synchronized (bufferLock) {
            thinkBuffer.setLength(0);
            contentBuffer.setLength(0);
        }

        MainActivity act = currentActivityInstance;
        ChatManager.getInstance().onMessageAdded(act != null ? act : this);

        if (executableCalls.isEmpty()) {
            isGenerating = false;
            currentAssistantMsg = null;
            if (act != null) act.resetUIState();
            return;
        }

        final Chat targetChat = ChatManager.getInstance().getCurrentChat();

        runOnCurrentActivity(new Runnable() {
            @Override
            public void run() {
                if (genId != globalGenerationId || globalCancelled) return;
                MainActivity a = currentActivityInstance;
                if (a != null) {
                    if (a.progressBar != null) a.progressBar.setVisibility(View.VISIBLE);
                    if (a.adapter != null) a.adapter.notifyDataSetChanged();
                    a.scrollToBottom();
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                int totalResults = 0;
                WebFetcher webFetcher = new WebFetcher();

                for (int i = 0; i < executableCalls.size(); i++) {
                    if (genId != globalGenerationId || globalCancelled || ChatManager.getInstance().getCurrentChat() != targetChat) {
                        return;
                    }
                    StreamToolCall stc = executableCalls.get(i);
                    String callId = stc.id;
                    String resultText;

                    if ("web_search".equals(stc.name)) {
                        MainActivity curAct = currentActivityInstance;
                        Context searchCtx = curAct != null ? curAct : MainActivity.this;
                        SearchEngine engine = SearchManager.getInstance().getEngine(searchCtx);
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
                    if (genId != globalGenerationId || globalCancelled || ChatManager.getInstance().getCurrentChat() != targetChat) {
                        return;
                    }
                    Message toolMessage = new Message(Role.TOOL, resultText);
                    toolMessage.setToolCallId(callId);
                    targetChat.getMessages().add(toolMessage);
                }

                if (genId != globalGenerationId || globalCancelled || ChatManager.getInstance().getCurrentChat() != targetChat) {
                    return;
                }

                assistantMsg.setSearchResultCount(totalResults);
                MainActivity curAct = currentActivityInstance;
                ChatManager.getInstance().onMessageAdded(curAct != null ? curAct : MainActivity.this);

                runOnCurrentActivity(new Runnable() {
                    @Override
                    public void run() {
                        if (genId != globalGenerationId || globalCancelled || ChatManager.getInstance().getCurrentChat() != targetChat) {
                            return;
                        }
                        MainActivity a = currentActivityInstance;
                        if (a != null) {
                            if (a.adapter != null) a.adapter.notifyDataSetChanged();
                            a.requestAICompletion();
                        }
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
        if (msg == null) return;
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

        if (displayThink.length() > 0 || displayContent.length() > 0) {
            if (thinkingEnabled && displayThink.length() > 0) {
                msg.setContent("<think>" + displayThink + "</think>" + displayContent);
            } else {
                msg.setContent(displayContent);
            }
        }

        int firstVis = msgList.getFirstVisiblePosition();
        int lastVis = msgList.getLastVisiblePosition();
        int count = adapter != null ? adapter.getCount() : 0;
        int targetIndex = count - 1;
        boolean viewUpdated = false;

        if (targetIndex >= firstVis && targetIndex <= lastVis && targetIndex >= 0) {
            if (adapter != null && targetIndex < adapter.getCount() && adapter.getItem(targetIndex) == msg) {
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
                            if (thinkLayout != null) thinkLayout.setVisibility(View.VISIBLE);
                            View noThink = view.findViewById(R.id.noThinking);
                            if (noThink != null) noThink.setVisibility(View.GONE);
                            if (tvThink != null) {
                                tvThink.setMovementMethod(LinkMovementMethod.getInstance());
                                tvThink.setText(msg.getParsedThinkContent(MainActivity.this, !isFinal));
                            }
                        } else {
                            if (thinkLayout != null) thinkLayout.setVisibility(View.VISIBLE);
                            View noThink = view.findViewById(R.id.noThinking);
                            if (noThink != null) noThink.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (thinkLayout != null) thinkLayout.setVisibility(View.GONE);
                    }

                    if (vResponse != null) {
                        vResponse.setVisibility((displayContent.length() > 0) ? View.VISIBLE : View.GONE);
                    }
                    viewUpdated = true;
                }
            }
        }

        if (!viewUpdated && adapter != null) {
            adapter.notifyDataSetChanged();
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
        if (delta == null) return null;
        try {
            return delta.getString("reasoning");
        } catch (Exception ignored) {}

        try {
            return delta.getString("reasoning_content");
        } catch (Exception ignored) {}

        try {
            JSONArray arr = delta.getArray("reasoning_content");
            if (arr != null && arr.size() > 0) {
                JSONObject obj = arr.getObject(0);
                if (obj != null && !obj.isNull("thinking")) {
                    return obj.getString("thinking");
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}