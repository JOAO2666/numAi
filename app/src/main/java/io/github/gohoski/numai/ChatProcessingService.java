package io.github.gohoski.numai;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.api.ApiCallback;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiResult;
import io.github.gohoski.numai.api.ApiService;
import io.github.gohoski.numai.data.ChatManager;
import io.github.gohoski.numai.model.Chat;
import io.github.gohoski.numai.model.Message;
import io.github.gohoski.numai.model.ProviderSnapshot;
import io.github.gohoski.numai.model.Role;
import io.github.gohoski.numai.mcp.McpCatalog;
import io.github.gohoski.numai.mcp.McpClient;
import io.github.gohoski.numai.mcp.McpConfigManager;
import io.github.gohoski.numai.mcp.McpSettings;
import io.github.gohoski.numai.mcp.McpTool;
import io.github.gohoski.numai.util.OpenAiDelta;
import io.github.gohoski.numai.search.SearchEngine;
import io.github.gohoski.numai.search.SearchManager;
import io.github.gohoski.numai.search.SearchResult;
import io.github.gohoski.numai.search.WebFetcher;
import io.github.gohoski.numai.util.GenerationRegistry;

/**
 * Owns network work independently from an Activity. It is deliberately small
 * and bounded because this app also supports very old Android devices.
 */
public class ChatProcessingService extends Service {
    private static final String ACTION_GENERATE =
            "io.github.gohoski.numai.action.GENERATE";
    private static final String EXTRA_CHAT_ID = "chat_id";
    private static final String EXTRA_MESSAGE_ID = "message_id";
    private static final String EXTRA_GENERATION_ID = "generation_id";
    private static final String EXTRA_THINKING = "thinking";
    private static final String EXTRA_PROVIDER_ID = "provider_id";
    private static final String EXTRA_BASE_URL = "base_url";
    private static final String EXTRA_API_KEY = "api_key";
    private static final String EXTRA_CHAT_MODEL = "chat_model";
    private static final String EXTRA_THINKING_MODEL = "thinking_model";
    private static final String EXTRA_SHRINK_THINK = "shrink_think";
    private static final String EXTRA_SYSTEM_PROMPT = "system_prompt";
    private static final String EXTRA_UPDATE_DELAY = "update_delay";
    private static final String EXTRA_WEB_SEARCH = "web_search";
    private static final String EXTRA_SEARCH_ENGINE = "search_engine";
    private static final String EXTRA_WEB_FETCH = "web_fetch";
    private static final String EXTRA_DISABLE_TOOLS_IMAGE = "disable_tools_image";
    private static final String EXTRA_MCP_ENABLED = "mcp_enabled";
    private static final String EXTRA_MCP_AUTO_EXECUTE = "mcp_auto_execute";
    private static final String EXTRA_MCP_ENDPOINT = "mcp_endpoint";
    private static final int NOTIFICATION_ID = 2666;
    private static final int MAX_TOOL_ROUNDS = 3;
    private static final long STREAM_PERSIST_INTERVAL_MS = 1000L;

    private static final GenerationRegistry REGISTRY = new GenerationRegistry();
    private ExecutorService executor;
    private ChatManager chatManager;

    public static Intent createIntent(Context context, Chat chat, Message userMessage,
            ProviderSnapshot snapshot, boolean thinking, String generationId) {
        Intent intent = new Intent(context, ChatProcessingService.class);
        intent.setAction(ACTION_GENERATE);
        intent.putExtra(EXTRA_CHAT_ID, chat.getId());
        intent.putExtra(EXTRA_MESSAGE_ID, userMessage.getMessageId());
        intent.putExtra(EXTRA_GENERATION_ID, generationId);
        intent.putExtra(EXTRA_THINKING, thinking);
        intent.putExtra(EXTRA_PROVIDER_ID, snapshot.getProviderId());
        intent.putExtra(EXTRA_BASE_URL, snapshot.getBaseUrl());
        intent.putExtra(EXTRA_API_KEY, snapshot.getApiKey());
        intent.putExtra(EXTRA_CHAT_MODEL, snapshot.getChatModel());
        intent.putExtra(EXTRA_THINKING_MODEL, snapshot.getThinkingModel());
        intent.putExtra(EXTRA_SHRINK_THINK, snapshot.getShrinkThink());
        intent.putExtra(EXTRA_SYSTEM_PROMPT, snapshot.getSystemPrompt());
        intent.putExtra(EXTRA_UPDATE_DELAY, snapshot.getUpdateDelay());
        intent.putExtra(EXTRA_WEB_SEARCH, snapshot.isWebSearchEnabled());
        intent.putExtra(EXTRA_SEARCH_ENGINE, snapshot.getSearchEngine());
        intent.putExtra(EXTRA_WEB_FETCH, snapshot.isWebFetchEnabled());
        intent.putExtra(EXTRA_DISABLE_TOOLS_IMAGE, snapshot.isDisableToolsWithImage());
        McpSettings mcp = McpConfigManager.getInstance(context).createSnapshot();
        intent.putExtra(EXTRA_MCP_ENABLED, mcp.isEnabled());
        intent.putExtra(EXTRA_MCP_AUTO_EXECUTE, mcp.isAutoExecute());
        intent.putExtra(EXTRA_MCP_ENDPOINT, mcp.getEndpoint());
        return intent;
    }

    public static boolean isGenerationActive(String chatId, String generationId) {
        return REGISTRY.isActive(chatId, generationId);
    }

    public static String getActiveGenerationId(String chatId) {
        return REGISTRY.getActiveGenerationId(chatId);
    }

    public static void cancelGeneration(Context context, String chatId,
            String generationId) {
        REGISTRY.cancel(chatId, generationId);
        Intent wake = new Intent(context, ChatProcessingService.class);
        wake.setAction("io.github.gohoski.numai.action.CANCEL");
        context.startService(wake);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newFixedThreadPool(2);
        chatManager = ChatManager.getInstance();
        if (chatManager.getSortedChats().isEmpty()) chatManager.loadChats(this);
        startForegroundCompat();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (intent != null && ACTION_GENERATE.equals(intent.getAction())) {
            final String chatId = intent.getStringExtra(EXTRA_CHAT_ID);
            final String messageId = intent.getStringExtra(EXTRA_MESSAGE_ID);
            final String generationId = intent.getStringExtra(EXTRA_GENERATION_ID);
            if (REGISTRY.getActiveGenerationId(chatId) != null) {
                return START_NOT_STICKY;
            }
            final GenerationRegistry.Generation generation =
                    REGISTRY.start(chatId, messageId, generationId);
            executor.submit(new Runnable() {
                public void run() { process(intent, generation); }
            });
        } else if (REGISTRY.activeCount() == 0) {
            stopForegroundCompat();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void process(final Intent intent,
            final GenerationRegistry.Generation generation) {
        Chat chat = chatManager.getChatById(generation.getChatId());
        if (chat == null || generation.isCancelled()) {
            REGISTRY.finish(generation.getChatId(), generation.getGenerationId());
            return;
        }
        chatManager.ensureMessagesLoaded(this, chat);
        GenerationContext generationContext = new GenerationContext(snapshotFrom(intent));
        boolean toolsDisabledForImage = generationContext.providerSnapshot
                .isDisableToolsWithImage() && hasInputImage(chat);
        if (intent.getBooleanExtra(EXTRA_MCP_ENABLED, false) &&
                intent.getBooleanExtra(EXTRA_MCP_AUTO_EXECUTE, false) &&
                !toolsDisabledForImage) {
            try {
                generationContext.mcpClient = new McpClient(this,
                        intent.getStringExtra(EXTRA_MCP_ENDPOINT));
                generationContext.mcpCatalog = generationContext.mcpClient.listTools();
            } catch (Exception error) {
                addPreparationError(chat, generation, error);
                finish(generation);
                return;
            }
        }

        requestCompletion(intent, generation, chat, generationContext,
                intent.getBooleanExtra(EXTRA_THINKING, false), 0);
    }

    private static boolean hasInputImage(Chat chat) {
        if (chat == null || chat.getMessages() == null) return false;
        List<Message> messages = chat.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            List<String> images = messages.get(i).getInputImages();
            if (images != null && !images.isEmpty()) return true;
        }
        return false;
    }

    private void requestCompletion(final Intent intent,
            final GenerationRegistry.Generation generation, final Chat chat,
            final GenerationContext generationContext, final boolean thinking,
            final int toolRound) {
        if (generation.isCancelled()) {
            finish(generation);
            return;
        }

        ArrayList<Message> requestMessages = new ArrayList<Message>(chat.getMessages());
        final Message assistant = new Message(Role.ASSISTANT, "",
                thinking ? intent.getStringExtra(EXTRA_THINKING_MODEL) :
                        intent.getStringExtra(EXTRA_CHAT_MODEL));
        assistant.setChatId(chat.getId());
        assistant.setGenerationId(generation.getGenerationId());
        chat.getMessages().add(assistant);
        chatManager.onMessageAdded(this, chat);

        final ProviderSnapshot snapshot = generationContext.providerSnapshot;
        ApiService api = new ApiService(this);
        api.chatCompletion(requestMessages,
                thinking, snapshot,
                generationContext.mcpCatalog == null ? null :
                        generationContext.mcpCatalog.toOpenAiTools(),
                new ApiCallback<ApiResult>() {
                    public void onSuccess(final ApiResult result) {
                        generation.setStream(result.getResult());
                        if (generation.isCancelled()) {
                            finish(generation);
                            return;
                        }
                        executor.submit(new Runnable() {
                            public void run() {
                                consume(intent, generation, chat, assistant,
                                        result.getResult(), thinking, generationContext,
                                        toolRound);
                            }
                        });
                    }

                    public void onError(ApiError error) {
                        if (!generation.isCancelled()) {
                            assistant.setContent(error.getMessage());
                            assistant.setAsError();
                            Chat chat = chatManager.getChatById(generation.getChatId());
                            if (chat != null) chatManager.onMessageAdded(
                                    ChatProcessingService.this, chat);
                        }
                        finish(generation);
                    }
                });
    }

    private void consume(Intent intent, GenerationRegistry.Generation generation,
            Chat chat, Message assistant, InputStream stream, boolean thinkingEnabled,
            GenerationContext generationContext, int toolRound) {
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        boolean hasThinking = false;
        List<StreamToolCall> streamToolCalls = new ArrayList<StreamToolCall>();
        BufferedReader reader = null;
        boolean handedOffToNextRequest = false;
        long lastPersistAt = 0L;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 8192);
            String line;
            while (!generation.isCancelled() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JSONObject root = JSON.getObject(data);
                    JSONArray choices = root.getNullableArray("choices");
                    if (choices == null || choices.size() == 0) continue;
                    JSONObject choice = choices.getObject(0);
                    JSONObject delta = choice.getNullableObject("delta");
                    if (delta == null) continue;
                    appendToolCalls(delta, streamToolCalls);
                    String reasoning = OpenAiDelta.text(delta, "reasoning");
                    if (reasoning == null) reasoning = OpenAiDelta.text(delta, "reasoning_content");
                    if (reasoning != null && reasoning.length() > 0) {
                        hasThinking = true;
                        thinking.append(reasoning);
                    }
                    String text = OpenAiDelta.text(delta, "content");
                    if (text != null) content.append(text);
                    String display = hasThinking ? "<think>" + thinking +
                            "</think>" + content : content.toString();
                    assistant.setContent(display);
                    long now = System.currentTimeMillis();
                    if (now - lastPersistAt >= STREAM_PERSIST_INTERVAL_MS) {
                        Chat currentChat = chatManager.getChatById(generation.getChatId());
                        if (currentChat != null) chatManager.onMessageAdded(this, currentChat);
                        lastPersistAt = now;
                    }
                } catch (Exception ignored) {}
            }
            if (!generation.isCancelled() && streamToolCalls.isEmpty() &&
                    content.length() == 0 && thinking.length() == 0) {
                assistant.setContent(getString(R.string.error));
                assistant.setAsError();
            }
            Chat currentChat = chatManager.getChatById(generation.getChatId());
            if (currentChat != null) chatManager.onMessageAdded(this, currentChat);
            if (!generation.isCancelled() && !streamToolCalls.isEmpty()) {
                handedOffToNextRequest = executeToolCalls(intent, generation, chat, assistant, streamToolCalls,
                        thinkingEnabled, generationContext, toolRound);
                return;
            }
        } catch (Exception error) {
            if (!generation.isCancelled()) {
                assistant.setContent(error.getMessage() == null ?
                        getString(R.string.error) : error.getMessage());
                assistant.setAsError();
                Chat currentChat = chatManager.getChatById(generation.getChatId());
                if (currentChat != null) chatManager.onMessageAdded(this, currentChat);
            }
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
            if (!handedOffToNextRequest) finish(generation);
        }
    }

    private void appendToolCalls(JSONObject delta, List<StreamToolCall> calls) {
        try {
            JSONArray array = delta.getNullableArray("tool_calls");
            if (array == null) return;
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getObject(i);
                if (item == null) continue;
                int index = item.getInt("index", 0);
                while (calls.size() <= index) calls.add(new StreamToolCall());
                StreamToolCall call = calls.get(index);
                String id = item.getNullableString("id");
                if (id != null) call.id = id;
                JSONObject function = item.getNullableObject("function");
                if (function != null) {
                    String name = function.getNullableString("name");
                    if (name != null) call.name = name;
                    String arguments = function.getNullableString("arguments");
                    if (arguments != null) call.arguments.append(arguments);
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean executeToolCalls(Intent intent,
            GenerationRegistry.Generation generation, Chat chat, Message assistant,
            List<StreamToolCall> calls, boolean thinking,
            GenerationContext generationContext, int toolRound) {
        ProviderSnapshot snapshot = generationContext.providerSnapshot;
        JSONArray toolCallsJson = new JSONArray();
        List<StreamToolCall> executable = new ArrayList<StreamToolCall>();
        for (int i = 0; i < calls.size(); i++) {
            StreamToolCall call = calls.get(i);
            if (call.id == null || call.id.length() == 0) call.id = "call_" + i;
            JSONObject callJson = new JSONObject();
            callJson.put("id", call.id);
            callJson.put("type", "function");
            JSONObject functionJson = new JSONObject();
            functionJson.put("name", call.name == null ? "" : call.name);
            functionJson.put("arguments", call.arguments.toString());
            callJson.put("function", functionJson);
            toolCallsJson.add(callJson);
            if (("web_search".equals(call.name) && snapshot.isWebSearchEnabled()) ||
                    ("web_fetch".equals(call.name) && snapshot.isWebFetchEnabled()) ||
                    (generationContext.mcpCatalog != null &&
                            generationContext.mcpCatalog.containsMappedName(call.name))) {
                executable.add(call);
            }
        }

        assistant.setToolCalls(toolCallsJson);
        assistant.setSearchResultCount(0);
        chatManager.onMessageAdded(this, chat);
        if (executable.isEmpty() || toolRound >= MAX_TOOL_ROUNDS) {
            if (assistant.getContent() == null || assistant.getContent().trim().length() == 0) {
                assistant.setContent(getString(R.string.error));
                assistant.setAsError();
                chatManager.onMessageAdded(this, chat);
            }
            finish(generation);
            return false;
        }

        SearchEngine searchEngine = SearchManager.getInstance().getEngine(this,
                snapshot.getSearchEngine());
        WebFetcher webFetcher = new WebFetcher();
        int totalResults = 0;
        for (int i = 0; i < executable.size() && !generation.isCancelled(); i++) {
            StreamToolCall call = executable.get(i);
            String resultText;
            if ("web_search".equals(call.name)) {
                resultText = executeSearch(searchEngine, call);
                totalResults += countSearchResults(resultText);
            } else if ("web_fetch".equals(call.name)) {
                resultText = executeFetch(webFetcher, call);
                if (resultText.length() > 0 && !resultText.startsWith("Error:")) totalResults++;
            } else {
                resultText = executeMcp(generationContext, call);
            }
            Message toolMessage = new Message(Role.TOOL, resultText);
            toolMessage.setToolCallId(call.id);
            toolMessage.setChatId(chat.getId());
            chat.getMessages().add(toolMessage);
        }
        assistant.setSearchResultCount(totalResults);
        chatManager.onMessageAdded(this, chat);
        if (generation.isCancelled()) {
            finish(generation);
            return false;
        }
        requestCompletion(intent, generation, chat, generationContext, thinking,
                toolRound + 1);
        return true;
    }

    private String executeMcp(GenerationContext generationContext, StreamToolCall call) {
        try {
            McpTool tool = generationContext.mcpCatalog.getByMappedName(call.name);
            JSONObject arguments = call.arguments.length() == 0 ? new JSONObject() :
                    JSON.getObject(call.arguments.toString());
            return generationContext.mcpClient.callTool(tool, arguments);
        } catch (Exception error) {
            return errorJson(error);
        }
    }

    private void addPreparationError(Chat chat,
            GenerationRegistry.Generation generation, Exception error) {
        String detail = error == null || error.getMessage() == null ?
                getString(R.string.mcp_unavailable) : error.getMessage();
        Message message = new Message(Role.ASSISTANT,
                getString(R.string.mcp_unavailable_detail, detail), getString(R.string.error));
        message.setAsError();
        message.setChatId(chat.getId());
        message.setGenerationId(generation.getGenerationId());
        chat.getMessages().add(message);
        chatManager.onMessageAdded(this, chat);
    }

    private String executeSearch(SearchEngine engine, StreamToolCall call) {
        try {
            JSONObject args = JSON.getObject(call.arguments.toString());
            String query = args.getString("query");
            List<SearchResult> results = engine.search(query);
            JSONObject response = new JSONObject();
            response.put("query", query);
            JSONArray items = new JSONArray();
            if (results == null || results.isEmpty()) {
                response.put("status", "no_results");
            } else {
                response.put("status", "success");
                for (int i = 0; i < results.size(); i++) {
                    SearchResult item = results.get(i);
                    JSONObject result = new JSONObject();
                    result.put("title", item.getTitle());
                    result.put("url", item.getUrl());
                    result.put("snippet", item.getSnippet());
                    items.add(result);
                }
            }
            response.put("results", items);
            return response.toString();
        } catch (Exception error) {
            return errorJson(error);
        }
    }

    private String executeFetch(WebFetcher fetcher, StreamToolCall call) {
        try {
            JSONObject args = JSON.getObject(call.arguments.toString());
            String url = args.getString("url");
            return "URL: " + url + "\n" + fetcher.fetch(url);
        } catch (Exception error) {
            return "Error: " + (error.getMessage() == null ? getString(R.string.error) :
                    error.getMessage());
        }
    }

    private int countSearchResults(String resultText) {
        try {
            JSONObject response = JSON.getObject(resultText);
            JSONArray results = response.getNullableArray("results");
            return results == null ? 0 : results.size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String errorJson(Exception error) {
        JSONObject response = new JSONObject();
        response.put("status", "error");
        response.put("message", error.getMessage() == null ? getString(R.string.error) :
                error.getMessage());
        return response.toString();
    }

    private static class StreamToolCall {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }

    private static class GenerationContext {
        final ProviderSnapshot providerSnapshot;
        McpClient mcpClient;
        McpCatalog mcpCatalog;

        GenerationContext(ProviderSnapshot providerSnapshot) {
            this.providerSnapshot = providerSnapshot;
        }
    }

    private void finish(GenerationRegistry.Generation generation) {
        REGISTRY.finish(generation.getChatId(), generation.getGenerationId());
        if (REGISTRY.activeCount() == 0) {
            stopForegroundCompat();
            stopSelf();
        }
    }

    private ProviderSnapshot snapshotFrom(Intent intent) {
        return new ProviderSnapshot(intent.getStringExtra(EXTRA_PROVIDER_ID),
                intent.getStringExtra(EXTRA_BASE_URL), intent.getStringExtra(EXTRA_API_KEY),
                intent.getStringExtra(EXTRA_CHAT_MODEL),
                intent.getStringExtra(EXTRA_THINKING_MODEL),
                intent.getBooleanExtra(EXTRA_SHRINK_THINK, false),
                intent.getStringExtra(EXTRA_SYSTEM_PROMPT),
                intent.getIntExtra(EXTRA_UPDATE_DELAY, 250),
                intent.getBooleanExtra(EXTRA_WEB_SEARCH, true),
                intent.getStringExtra(EXTRA_SEARCH_ENGINE),
                intent.getBooleanExtra(EXTRA_WEB_FETCH, true),
                intent.getBooleanExtra(EXTRA_DISABLE_TOOLS_IMAGE, true));
    }

    private void startForegroundCompat() {
        try {
            if (Integer.parseInt(android.os.Build.VERSION.SDK) >= 26) {
                Class<?> channelClass = Class.forName("android.app.NotificationChannel");
                Object channel = channelClass.getConstructor(String.class, CharSequence.class,
                        Integer.TYPE).newInstance("numai_generation",
                        "numAi processing", Integer.valueOf(2));
                NotificationManager manager = (NotificationManager)
                        getSystemService(Context.NOTIFICATION_SERVICE);
                manager.getClass().getMethod("createNotificationChannel", channelClass)
                        .invoke(manager, channel);
                Class<?> builderClass = Class.forName("android.app.Notification$Builder");
                Object builder = builderClass.getConstructor(Context.class).newInstance(this);
                builderClass.getMethod("setChannelId", String.class)
                        .invoke(builder, "numai_generation");
                startForegroundReflect(buildNotification(builder));
            } else {
                Class<?> builderClass = Class.forName("android.app.Notification$Builder");
                Object builder = builderClass.getConstructor(Context.class).newInstance(this);
                startForegroundReflect(buildNotification(builder));
            }
        } catch (Exception ignored) {
            try {
                Notification notification = new Notification();
                notification.icon = android.R.drawable.stat_sys_download;
                startForegroundReflect(notification);
            } catch (Exception ignoredAgain) {}
        }
    }

    private Notification buildNotification(Object builder) throws Exception {
        Class<?> builderClass = builder.getClass();
        builderClass.getMethod("setSmallIcon", Integer.TYPE)
                .invoke(builder, Integer.valueOf(android.R.drawable.stat_sys_download));
        builderClass.getMethod("setContentTitle", CharSequence.class)
                .invoke(builder, "numAi");
        builderClass.getMethod("setContentText", CharSequence.class)
                .invoke(builder, "Processing messages");
        builderClass.getMethod("setOngoing", Boolean.TYPE)
                .invoke(builder, Boolean.TRUE);
        return (Notification) builderClass.getMethod("getNotification").invoke(builder);
    }

    private void startForegroundReflect(Notification notification) throws Exception {
        getClass().getMethod("startForeground", Integer.TYPE, Notification.class)
                .invoke(this, Integer.valueOf(NOTIFICATION_ID), notification);
    }

    private void stopForegroundCompat() {
        try {
            getClass().getMethod("stopForeground", Boolean.TYPE)
                    .invoke(this, Boolean.TRUE);
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        REGISTRY.cancelAll();
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }
}
