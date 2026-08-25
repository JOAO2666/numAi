package io.github.gohoski.numai.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.R;
import io.github.gohoski.numai.data.ConfigManager;
import io.github.gohoski.numai.model.Message;
import io.github.gohoski.numai.model.ModelInfo;
import io.github.gohoski.numai.model.ProviderSnapshot;
import io.github.gohoski.numai.model.Role;
import io.github.gohoski.numai.util.Base64;

public class ApiService {
    private final ApiClient apiClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConfigManager config;
    private Context ctx;

    public ApiService(Context context) {
        this.apiClient = new ApiClient(context);
        this.config = ConfigManager.getInstance(context);
        ctx = context;
    }

    public void chatCompletion(final List<Message> msg, final boolean thinking, final ApiCallback<ApiResult> callback) {
        chatCompletion(msg, thinking, config.createSnapshot(), callback);
    }

    /**
     * Uses only the immutable snapshot captured when the message was sent.
     * Changing Settings while this request is streaming cannot redirect it.
     */
    public void chatCompletion(final List<Message> msg, final boolean thinking,
            final ProviderSnapshot snapshot, final ApiCallback<ApiResult> callback) {
        final ProviderSnapshot requestSnapshot = snapshot == null ?
                config.createSnapshot() : snapshot;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ApiRequest request = new ApiRequest(requestSnapshot.getBaseUrl(),
                            "/chat/completions", "POST");
                    request.setApiKey(requestSnapshot.getApiKey());
                    request.setReadTimeout(40000);
                    boolean[] hasImgHolder = new boolean[]{false};
                    JSONArray messages = buildMessages(msg, hasImgHolder, requestSnapshot);
                    boolean hasImg = hasImgHolder[0];

                    JSONObject body = new JSONObject();
                    final String model = thinking ? requestSnapshot.getThinkingModel() :
                            requestSnapshot.getChatModel();
                    body.put("model", model);
                    body.put("messages", messages);
                    body.put("stream", true);

                    if (!(hasImg && requestSnapshot.isDisableToolsWithImage()) &&
                            (requestSnapshot.isWebSearchEnabled() || requestSnapshot.isWebFetchEnabled())) {
                        JSONArray tools = new JSONArray();

                        if (requestSnapshot.isWebSearchEnabled()) {
                            JSONObject tool = new JSONObject();
                            tool.put("type", "function");

                            JSONObject function = new JSONObject();
                            function.put("name", "web_search");
                            function.put("description", "Mandatory tool to fetch real-time facts, tech tutorials, and software compatibility info. Must be executed prior to answering any factual or technical user query!");

                            JSONObject parameters = new JSONObject();
                            parameters.put("type", "object");

                            JSONObject properties = new JSONObject();
                            JSONObject queryProp = new JSONObject();
                            queryProp.put("type", "string");
                            queryProp.put("description", "Search query keywords");
                            properties.put("query", queryProp);

                            parameters.put("properties", properties);
                            JSONArray required = new JSONArray();
                            required.add("query");
                            parameters.put("required", required);

                            function.put("parameters", parameters);
                            tool.put("function", function);
                            tools.add(tool);
                        }

                        if (requestSnapshot.isWebFetchEnabled()) {
                            JSONObject fetchTool = new JSONObject();
                            fetchTool.put("type", "function");

                            JSONObject fetchFunction = new JSONObject();
                            fetchFunction.put("name", "web_fetch");
                            fetchFunction.put("description", "Fetches and extracts full text content from a target web page URL as clean Markdown to read articles, documentation, or news.");

                            JSONObject fetchParams = new JSONObject();
                            fetchParams.put("type", "object");

                            JSONObject fetchProps = new JSONObject();
                            JSONObject urlProp = new JSONObject();
                            urlProp.put("type", "string");
                            urlProp.put("description", "Target URL to fetch and read");
                            fetchProps.put("url", urlProp);

                            fetchParams.put("properties", fetchProps);
                            JSONArray fetchRequired = new JSONArray();
                            fetchRequired.add("url");
                            fetchParams.put("required", fetchRequired);

                            fetchFunction.put("parameters", fetchParams);
                            fetchTool.put("function", fetchFunction);
                            tools.add(fetchTool);
                        }

                        body.put("tools", tools);
                    }

                    if (thinking) {
                        switch (requestSnapshot.getBaseUrl()) {
                            case "https://openrouter.ai/api/v1":
                                JSONObject reasoning = new JSONObject();
                                reasoning.put("enabled", true);
                                body.put("reasoning", reasoning);
                                break;
                            case "https://api.together.xyz/v1":
                                JSONObject kw = new JSONObject();
                                kw.put("thinking", true);
                                body.put("chat_template_kwargs", kw);
                                break;
                            case "https://dashscope.aliyuncs.com/compatible-mode/v1":
                            case "https://dashscope-intl.aliyuncs.com/compatible-mode/v1":
                                body.put("enable_thinking", true); break;
                            default:
                                body.put("reasoning_effort", "high");
                        }
                    }
                    request.setBody(body.toString());

                    ApiResponse response = apiClient.execute(request);
                    if (response.isSuccessful()) {
                        deliverSuccess(callback, new ApiResult(model, response.getBody()));
                    } else {
                        String errorBody = "no body";
                        try {
                            errorBody = apiClient.readInputStreamToString(response.getBody());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        deliverError(callback, new ApiError(ctx.getString(
                                hasImg ? R.string.fail_send_vision : R.string.fail_send,
                                response.getStatusCode() + " " +
                                        safeError(errorBody, requestSnapshot.getApiKey()))));
                    }
                } catch (ApiError e) {
                    deliverError(callback, e);
                }
            }
        }).start();
    }

    private JSONArray buildMessages(List<Message> rawMessages, boolean[] hasImgHolder,
            ProviderSnapshot snapshot) {
        JSONArray messages = new JSONArray();
        String systemStr = snapshot.getSystemPrompt();
        String latexInstruction = ctx.getString(R.string.latex_system_instruction);
        if (systemStr == null || systemStr.trim().length() == 0) systemStr = latexInstruction;
        else systemStr = systemStr.trim() + "\n\n" + latexInstruction;
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", systemStr);
        messages.add(system);

        int size = rawMessages.size();
        for (int i = 0; i < size; i++) {
            Message message = rawMessages.get(i);

            if (message.getRoleEnum() == Role.TOOL) {
                // Tool messages are formatted and included alongside their parent Assistant message
                continue;
            }

            if (message.getRoleEnum() == Role.ASSISTANT && message.getToolCalls() != null && message.getToolCalls().size() > 0) {
                List<Message> followingToolMsgs = new ArrayList<Message>();
                for (int j = i + 1; j < size; j++) {
                    Message nextMsg = rawMessages.get(j);
                    if (nextMsg.getRoleEnum() == Role.TOOL) {
                        followingToolMsgs.add(nextMsg);
                    } else {
                        break;
                    }
                }

                JSONArray origToolCalls = message.getToolCalls();
                JSONArray validToolCalls = new JSONArray();
                List<Message> validToolMsgs = new ArrayList<Message>();

                for (int k = 0; k < origToolCalls.size(); k++) {
                    try {
                        JSONObject tc = origToolCalls.getObject(k);
                        if (tc != null && tc.has("id")) {
                            String callId = tc.getString("id");
                            Message matchingToolMsg = null;
                            for (int m = 0; m < followingToolMsgs.size(); m++) {
                                Message tm = followingToolMsgs.get(m);
                                if (callId.equals(tm.getToolCallId())) {
                                    matchingToolMsg = tm;
                                    break;
                                }
                            }
                            if (matchingToolMsg != null) {
                                validToolCalls.add(tc);
                                validToolMsgs.add(matchingToolMsg);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (validToolCalls.size() > 0) {
                    JSONObject assistantJson = new JSONObject();
                    assistantJson.put("role", "assistant");
                    assistantJson.put("tool_calls", validToolCalls);
                    if (message.getContent() != null && message.getContent().trim().length() > 0) {
                        assistantJson.put("content", message.getContent());
                    } else {
                        assistantJson.put("content", (String) null);
                    }
                    messages.add(assistantJson);

                    for (int m = 0; m < validToolMsgs.size(); m++) {
                        Message tm = validToolMsgs.get(m);
                        JSONObject toolJson = new JSONObject();
                        toolJson.put("role", "tool");
                        toolJson.put("tool_call_id", tm.getToolCallId());
                        toolJson.put("content", tm.getContent() != null ? tm.getContent() : "");
                        messages.add(toolJson);
                    }
                } else if (message.getContent() != null && message.getContent().trim().length() > 0) {
                    JSONObject assistantJson = new JSONObject();
                    assistantJson.put("role", "assistant");
                    assistantJson.put("content", message.getContent());
                    messages.add(assistantJson);
                }

            } else {
                JSONObject messageJson = new JSONObject();
                messageJson.put("role", message.getRole());
                List<String> inputImages = message.getInputImages();
                if (inputImages == null || inputImages.isEmpty()) {
                    messageJson.put("content", message.getContent());
                } else {
                    hasImgHolder[0] = true;
                    JSONArray content = new JSONArray();
                    JSONObject inputText = new JSONObject();
                    inputText.put("type", "text");
                    inputText.put("text", message.getContent() != null ? message.getContent() : "");
                    content.add(inputText);
                    for (int k = 0; k < inputImages.size(); k++) {
                        String image = inputImages.get(k);
                        JSONObject input = new JSONObject();
                        input.put("type", "image_url");
                        JSONObject imageUrl = new JSONObject();

                        String base64Url = getBase64FromFilename(ctx, image);
                        imageUrl.put("url", base64Url != null ? base64Url : image);

                        input.put("image_url", imageUrl);
                        content.add(input);
                    }
                    messageJson.put("content", content);
                }
                messages.add(messageJson);
            }
        }

        return messages;
    }

    public void getModels(final ApiCallback<ArrayList<String>> callback) {
        getModelInfos(new ApiCallback<ArrayList<ModelInfo>>() {
            @Override
            public void onSuccess(ArrayList<ModelInfo> infos) {
                ArrayList<String> ids = new ArrayList<String>();
                for (int i = 0; i < infos.size(); i++) ids.add(infos.get(i).getId());
                callback.onSuccess(ids);
            }

            @Override
            public void onError(ApiError error) {
                callback.onError(error);
            }
        });
    }

    public void getModelInfos(final ApiCallback<ArrayList<ModelInfo>> callback) {
        final ProviderSnapshot requestSnapshot = config.createSnapshot();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ApiRequest request = new ApiRequest(requestSnapshot.getBaseUrl(), "/models", "GET");
                    request.setApiKey(requestSnapshot.getApiKey());
                    String response = apiClient.executeAsString(request);
                    ArrayList<ModelInfo> models = new ArrayList<ModelInfo>();

                    JSONObject resp = JSON.getObject(response);
                    if (resp.has("error"))
                        deliverError(callback, new ApiError(safeError(
                                resp.getObject("error").getString("message"),
                                requestSnapshot.getApiKey())));
                    else {
                        JSONArray json = resp.getArray("data");
                        for (int i = 0; i < json.size(); i++) {
                            JSONObject model = json.getObject(i);
                            if (model.has("endpoints")) {
                                if (model.getArray("endpoints").has("/v1/chat/completions"))
                                    models.add(toModelInfo(model));
                            } else
                                models.add(toModelInfo(model));
                        }
                        deliverSuccess(callback, models);
                    }
                } catch (ApiError e) {
                    deliverError(callback, e);
                }
            }
        }).start();
    }

    private ModelInfo toModelInfo(JSONObject model) {
        JSONObject pricing = model.getNullableObject("pricing");
        return new ModelInfo(model.getNullableString("id"),
                price(pricing, "prompt"), price(pricing, "completion"),
                price(pricing, "input"), price(pricing, "output"));
    }

    private String price(JSONObject pricing, String name) {
        return pricing == null ? null : pricing.getNullableString(name);
    }

    private <T> void deliverSuccess(final ApiCallback<T> callback, final T result) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(result);
            }
        });
    }

    private <T> void deliverError(final ApiCallback<T> callback, final ApiError error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    private String safeError(String value, String apiKey) {
        if (value == null) return "Unknown provider error";
        String safe = value;
        if (apiKey != null && apiKey.length() > 0) {
            safe = safe.replace(apiKey, "[redacted]");
        }
        if (safe.length() > 800) safe = safe.substring(0, 800) + "…";
        return safe;
    }

    public static String getBase64FromFilename(Context context, String fileName) {
        if (fileName.startsWith("data:image")) return fileName;
        FileInputStream fis = null;
        try {
            fis = context.openFileInput(fileName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return "data:image/jpeg;base64," + Base64.encode(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException ignored) {}
            }
        }
    }
}
