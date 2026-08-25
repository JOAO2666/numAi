package io.github.gohoski.numai;

import java.util.ArrayList;

import io.github.gohoski.numai.model.ModelInfo;
import io.github.gohoski.numai.util.GenerationRegistry;
import io.github.gohoski.numai.util.ModelLoadCoordinator;
import io.github.gohoski.numai.util.ProviderModelStore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ProviderAndGenerationTest {
    private ArrayList<ModelInfo> googleModels() {
        ArrayList<ModelInfo> result = new ArrayList<ModelInfo>();
        result.add(new ModelInfo("models/gemini-3.7-flash"));
        return result;
    }

    private ArrayList<ModelInfo> openRouterModels() {
        ArrayList<ModelInfo> result = new ArrayList<ModelInfo>();
        result.add(new ModelInfo("google/gemini-3.7-flash:free"));
        result.add(new ModelInfo("qwen/qwen-free:free"));
        return result;
    }

    @Test public void providerSwitchShowsOnlyCurrentModels() {
        ProviderModelStore store = new ProviderModelStore();
        store.switchProvider("google_ai_studio", "google");
        assertTrue(store.applyLoad(store.beginLoad(), "google_ai_studio", googleModels()));
        store.switchProvider("openrouter", "router");
        assertTrue(store.applyLoad(store.beginLoad(), "openrouter", openRouterModels()));
        assertEquals(2, store.get("openrouter").getCachedModels().size());
        assertEquals(1, store.get("google_ai_studio").getCachedModels().size());
        assertFalse(store.get("openrouter").getCachedModels().get(0).getId()
                .startsWith("models/"));
    }

    @Test public void switchingBackRestoresProviderSelection() {
        ProviderModelStore store = new ProviderModelStore();
        store.switchProvider("google_ai_studio", "google");
        store.applyLoad(store.beginLoad(), "google_ai_studio", googleModels());
        store.get("google_ai_studio").setChatModel("models/gemini-3.7-flash");
        store.switchProvider("openrouter", "router");
        store.applyLoad(store.beginLoad(), "openrouter", openRouterModels());
        store.get("openrouter").setChatModel("qwen/qwen-free:free");
        store.switchProvider("google_ai_studio", "google");
        assertEquals("models/gemini-3.7-flash",
                store.get("google_ai_studio").getChatModel());
        assertNotSame(store.get("google_ai_studio"), store.get("openrouter"));
    }

    @Test public void staleProviderResponseIsRejected() {
        ProviderModelStore store = new ProviderModelStore();
        store.switchProvider("google_ai_studio", "google");
        ModelLoadCoordinator.RequestToken google = store.beginLoad();
        store.switchProvider("openrouter", "router");
        ModelLoadCoordinator.RequestToken router = store.beginLoad();
        assertFalse(store.applyLoad(google, "google_ai_studio", googleModels()));
        assertTrue(store.applyLoad(router, "openrouter", openRouterModels()));
    }

    @Test public void cancelOneChatKeepsOtherChatActive() {
        GenerationRegistry registry = new GenerationRegistry();
        GenerationRegistry.Generation first = registry.start("chat-a", "message-a");
        GenerationRegistry.Generation second = registry.start("chat-b", "message-b");
        assertTrue(registry.cancel("chat-a", first.getGenerationId()));
        assertFalse(registry.isActive("chat-a", first.getGenerationId()));
        assertTrue(registry.isActive("chat-b", second.getGenerationId()));
    }
}
