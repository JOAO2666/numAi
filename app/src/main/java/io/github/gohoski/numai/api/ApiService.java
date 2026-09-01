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
        chatCompletion(msg, thinking, snapshot, null, callback);
    }

    /** Adds already-normalized OpenAI-compatible tools for this request. */
    public void chatCompletion(final List<Message> msg, final boolean thinking,
            final ProviderSnapshot snapshot, final JSONArray externalTools,
            final ApiCallback<ApiResult> callback) {
        final ProviderSnapshot requestSnapshot = snapshot == null ?
                config.createSnapshot() : snapshot;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean[] hasImgHolder = new boolean[]{false};
                    JSONArray messages = buildMessages(msg, hasImgHolder, requestSnapshot);
                    boolean hasImg = hasImgHolder[0];

                    JSONObject body = new JSONObject();
                    final String model = thinking ? requestSnapshot.getThinkingModel() :
                            requestSnapshot.getChatModel();
                    body.put("model", model);
                    body.put("messages", messages);
                    body.put("stream", true);

                    if (!(hasImg && requestSnapshot.isDisableToolsWithImage())) {
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

                        if (externalTools != null) {
                            for (int i = 0; i < externalTools.size(); i++) {
                                JSONObject external = externalTools.getObject(i);
                                if (external != null) tools.add(external);
                            }
                        }

                        if (tools.size() > 0) body.put("tools", tools);
                    }

                    if (thinking) {
                        switch (requestSnapshot.getBaseUrl()) {
                            case "https://openrouter.ai/api/v1":
                                JSONObject reasoning = new JSONObject();
                                reasoning.put("enabled", true);
                                body.put("reasoning", reasoning);
                                break;
                            case "https://api.together.ai/v1":
                            case "https://api.together.xyz/v1":
                                JSONObject kw = new JSONObject();
                                kw.put("thinking", true);
                                body.put("chat_template_kwargs", kw);
                                break;
                            case "https://integrate.api.nvidia.com/v1":
                                JSONObject nvidiaKw = new JSONObject();
                                nvidiaKw.put("enable_thinking", true);
                                body.put("chat_template_kwargs", nvidiaKw);
                                break;
                            case "https://dashscope.aliyuncs.com/compatible-mode/v1":
                            case "https://dashscope-intl.aliyuncs.com/compatible-mode/v1":
                                body.put("enable_thinking", true); break;
                            case "https://api.groq.com/openai/v1":
                                String lowerModel = model == null ? "" : model.toLowerCase();
                                if (lowerModel.indexOf("gpt-oss") >= 0 ||
                                        lowerModel.indexOf("qwen3.8") >= 0) {
                                    body.put("reasoning_effort", "high");
                                } else if (lowerModel.indexOf("qwen3") >= 0) {
                                    body.put("reasoning_effort", "default");
                                }
                                break;
                            default:
                                // The selected reasoning model can reason natively. Avoid sending
                                // provider-specific fields that many OpenAI-compatible APIs reject.
                                break;
                        }
                    }
                    boolean sentTools = body.has("tools");
                    ApiResponse response = null;
                    String errorBody = null;
                    for (int attempt = 0; attempt < 3; attempt++) {
                        response = apiClient.execute(newChatRequest(requestSnapshot, body));
                        if (response.isSuccessful()) break;
                        errorBody = readResponseBody(response);
                        if (sentTools && shouldRetryWithoutTools(response.getStatusCode(), errorBody)) {
                            body.remove("tools");
                            sentTools = false;
                            continue;
                        }
                        if (hasThinkingOptions(body) && shouldRetryWithoutThinking(
                                response.getStatusCode(), errorBody)) {
                            removeThinkingOptions(body);
                            continue;
                        }
                        break;
                    }
                    if (response.isSuccessful()) {
                        deliverSuccess(callback, new ApiResult(model, response.getBody()));
                    } else {
                        deliverError(callback, new ApiError(ctx.getString(
                                hasImg ? R.string.fail_send_vision : R.string.fail_send,
                                response.getStatusCode() + " " +
                                        providerError(errorBody, requestSnapshot.getApiKey()))));
                    }
                } catch (ApiError e) {
                    deliverError(callback, e);
                } catch (Exception e) {
                    deliverError(callback, new ApiError(safeError(
                            e.getMessage(), requestSnapshot.getApiKey())));
                }
            }
        }).start();
    }

    private ApiRequest newChatRequest(ProviderSnapshot snapshot, JSONObject body) {
        ApiRequest request = new ApiRequest(snapshot.getBaseUrl(),
                "/chat/completions", "POST");
        request.setApiKey(snapshot.getApiKey());
        request.setReadTimeout(40000);
        request.setBody(body.toString());
        return request;
    }

    private String readResponseBody(ApiResponse response) {
        try {
            return apiClient.readInputStreamToString(response.getBody());
        } catch (Exception ignored) {
            return "";
        }
    }

    static boolean shouldRetryWithoutTools(int statusCode, String errorBody) {
        if (statusCode != 400 && statusCode != 404 && statusCode != 422) return false;
        String error = errorBody == null ? "" : errorBody.toLowerCase();
        boolean mentionsTools = error.contains("tool") || error.contains("function");
        boolean rejected = error.contains("support") || error.contains("invalid") ||
                error.contains("unknown") || error.contains("unrecognized") ||
                error.contains("not allowed") || error.contains("not available");
        return mentionsTools && rejected;
    }

    static boolean shouldRetryWithoutThinking(int statusCode, String errorBody) {
        if (statusCode != 400 && statusCode != 404 && statusCode != 422) return false;
        String error = errorBody == null ? "" : errorBody.toLowerCase();
        boolean mentionsThinking = error.contains("reasoning") || error.contains("thinking") ||
                error.contains("chat_template_kwargs") || error.contains("enable_thinking");
        boolean rejected = error.contains("support") || error.contains("invalid") ||
                error.contains("unknown") || error.contains("unrecognized") ||
                error.contains("not allowed") || error.contains("not available");
        return mentionsThinking && rejected;
    }

    private static boolean hasThinkingOptions(JSONObject body) {
        return body.has("reasoning") || body.has("reasoning_effort") ||
                body.has("chat_template_kwargs") || body.has("enable_thinking");
    }

    private static void removeThinkingOptions(JSONObject body) {
        body.remove("reasoning");
        body.remove("reasoning_effort");
        body.remove("chat_template_kwargs");
        body.remove("enable_thinking");
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
                    ApiResponse apiResponse = apiClient.execute(request);
                    String response = readResponseBody(apiResponse);
                    if (!apiResponse.isSuccessful()) {
                        deliverError(callback, new ApiError(apiResponse.getStatusCode() + " " +
                                providerError(response, requestSnapshot.getApiKey())));
                        return;
                    }
                    ArrayList<ModelInfo> models = new ArrayList<ModelInfo>();

                    JSONObject resp = JSON.getObject(response);
                    if (resp.has("error")) {
                        deliverError(callback, new ApiError(providerError(
                                response, requestSnapshot.getApiKey())));
                        return;
                    }
                    JSONArray json = resp.getNullableArray("data");
                    if (json == null) {
                        deliverError(callback, new ApiError(
                                "Provider returned an unsupported model catalog"));
                        return;
                    }
                    for (int i = 0; i < json.size(); i++) {
                        JSONObject model = json.getObject(i);
                        if (isChatCompletionModel(model, requestSnapshot.getBaseUrl())) {
                            ModelInfo info = toModelInfo(model);
                            if (info.getId().length() > 0) models.add(info);
                        }
                    }
                    deliverSuccess(callback, models);
                } catch (ApiError e) {
                    deliverError(callback, e);
                } catch (Exception e) {
                    deliverError(callback, new ApiError(safeError(
                            e.getMessage(), requestSnapshot.getApiKey())));
                }
            }
        }).start();
    }

    static boolean isChatCompletionModel(JSONObject model, String baseUrl) {
        if (model == null) return false;
        String endpoint = model.getNullableString("endpoint");
        if (endpoint != null && endpoint.length() > 0) {
            return endpoint.indexOf("chat/completions") >= 0;
        }
        JSONArray endpoints = model.getNullableArray("endpoints");
        if (endpoints != null) {
            for (int i = 0; i < endpoints.size(); i++) {
                String value = endpoints.getString(i, "");
                if (value.indexOf("chat/completions") >= 0) return true;
            }
            return false;
        }
        if ("https://api.deepinfra.com/v1/openai".equals(baseUrl)) {
            JSONObject metadata = model.getNullableObject("metadata");
            JSONArray tags = metadata == null ? null : metadata.getNullableArray("tags");
            if (tags == null) return false;
            for (int i = 0; i < tags.size(); i++) {
                if ("chat".equalsIgnoreCase(tags.getString(i, ""))) return true;
            }
            return false;
        }
        if ("https://integrate.api.nvidia.com/v1".equals(baseUrl)) {
            String id = model.getNullableString("id");
            String lower = id == null ? "" : id.toLowerCase();
            if (lower.indexOf("embed") >= 0 || lower.indexOf("rerank") >= 0) return false;
        }
        return true;
    }

    static ModelInfo toModelInfo(JSONObject model) {
        JSONObject pricing = model.getNullableObject("pricing");
        if (pricing == null) {
            JSONObject metadata = model.getNullableObject("metadata");
            pricing = metadata == null ? null : metadata.getNullableObject("pricing");
        }
        String prompt = price(pricing, "prompt");
        String completion = price(pricing, "completion");
        String input = firstPrice(pricing, "input", "input_tokens");
        String output = firstPrice(pricing, "output", "output_tokens");
        if (model.getBoolean("isFree", false)) {
            prompt = "0";
            completion = "0";
        }
        return new ModelInfo(model.getNullableString("id"),
                prompt, completion, input, output);
    }

    private static String firstPrice(JSONObject pricing, String first, String second) {
        String value = price(pricing, first);
        return value == null ? price(pricing, second) : value;
    }

    private static String price(JSONObject pricing, String name) {
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
        if (value == null || value.trim().length() == 0) return "Unknown provider error";
        String safe = value;
        if (apiKey != null && apiKey.length() > 0) {
            safe = safe.replace(apiKey, "[redacted]");
        }
        if (safe.length() > 800) safe = safe.substring(0, 800) + "…";
        return safe;
    }

    private String providerError(String response, String apiKey) {
        String message = response;
        try {
            JSONObject root = JSON.getObject(response);
            Object error = root.getNullable("error");
            if (error instanceof JSONObject) {
                JSONObject errorObject = (JSONObject) error;
                message = errorObject.getNullableString("message");
                if (message == null) message = errorObject.getNullableString("detail");
            } else if (error != null) {
                message = String.valueOf(error);
            } else {
                String rootMessage = root.getNullableString("message");
                if (rootMessage == null) rootMessage = root.getNullableString("detail");
                if (rootMessage != null) message = rootMessage;
            }
        } catch (Exception ignored) {}
        return safeError(message, apiKey);
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
