package io.github.gohoski.numai.api;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.model.ModelInfo;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiServiceCompatibilityTest {
    @Test public void filtersNavyModelsByChatEndpoint() {
        JSONObject chat = JSON.getObject("{\"id\":\"gpt\",\"endpoint\":\"/v1/chat/completions\"}");
        JSONObject moderation = JSON.getObject("{\"id\":\"guard\",\"endpoint\":\"/v1/moderations\"}");
        assertTrue(ApiService.isChatCompletionModel(chat, "https://api.navy/v1"));
        assertFalse(ApiService.isChatCompletionModel(moderation, "https://api.navy/v1"));
    }

    @Test public void filtersDeepInfraModelsByTaskTag() {
        JSONObject chat = JSON.getObject("{\"id\":\"llm\",\"metadata\":{\"tags\":[\"chat\"]}}");
        JSONObject speech = JSON.getObject("{\"id\":\"whisper\",\"metadata\":{\"tags\":[\"stt\"]}}");
        String base = "https://api.deepinfra.com/v1/openai";
        assertTrue(ApiService.isChatCompletionModel(chat, base));
        assertFalse(ApiService.isChatCompletionModel(speech, base));
    }

    @Test public void filtersKnownNvidiaEmbeddingModels() {
        String base = "https://integrate.api.nvidia.com/v1";
        assertFalse(ApiService.isChatCompletionModel(
                JSON.getObject("{\"id\":\"nvidia/nv-embedqa-mistral-7b-v2\"}"), base));
        assertTrue(ApiService.isChatCompletionModel(
                JSON.getObject("{\"id\":\"meta/llama-3.3-70b-instruct\"}"), base));
    }

    @Test public void readsNestedProviderPricingAndFreeFlag() {
        ModelInfo paid = ApiService.toModelInfo(JSON.getObject(
                "{\"id\":\"chat\",\"metadata\":{\"pricing\":{\"input_tokens\":0.1,\"output_tokens\":0.2}}}"));
        ModelInfo free = ApiService.toModelInfo(JSON.getObject(
                "{\"id\":\"free-chat\",\"isFree\":true}"));
        assertFalse(paid.isFree());
        assertTrue(free.isFree());
    }

    @Test public void retriesOnlyExplicitToolCompatibilityErrors() {
        assertTrue(ApiService.shouldRetryWithoutTools(400,
                "This model does not support tool calling"));
        assertTrue(ApiService.shouldRetryWithoutTools(422,
                "Unknown function parameter"));
        assertFalse(ApiService.shouldRetryWithoutTools(401, "invalid api key"));
        assertFalse(ApiService.shouldRetryWithoutTools(404, "model not found"));
    }

    @Test public void retriesOnlyExplicitThinkingCompatibilityErrors() {
        assertTrue(ApiService.shouldRetryWithoutThinking(400,
                "reasoning_effort is not supported for this model"));
        assertTrue(ApiService.shouldRetryWithoutThinking(422,
                "unknown chat_template_kwargs field"));
        assertFalse(ApiService.shouldRetryWithoutThinking(401, "invalid api key"));
        assertFalse(ApiService.shouldRetryWithoutThinking(404, "model not found"));
    }
}
