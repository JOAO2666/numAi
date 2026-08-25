package io.github.gohoski.numai.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.api.ApiManager;
import io.github.gohoski.numai.model.Config;
import io.github.gohoski.numai.model.ModelInfo;
import io.github.gohoski.numai.model.ProviderSettings;
import io.github.gohoski.numai.model.ProviderSnapshot;

/**
 * Persists provider-scoped state. The old flat keys are read once as a
 * migration source, but all subsequent provider values use providerId keys.
 */
public class ConfigManager {
    private static final String PREFS_NAME = "numAi";
    private static final String KEY_ACTIVE_PROVIDER_ID = "activeProviderId";
    private static final String KEY_BASE_URL = "baseUrl";
    private static final String KEY_API_KEY = "apiKey";
    private static final String KEY_CHAT_MODEL = "chatModel";
    private static final String KEY_THINKING_MODEL = "thinkingModel";
    private static final String KEY_SHRINK_THINK = "shrinkThink";
    private static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    private static final String KEY_UPDATE_DELAY = "updateDelay";
    private static final String KEY_WEB_SEARCH_ENABLED = "webSearchEnabled";
    private static final String KEY_WEB_FETCH_ENABLED = "webFetchEnabled";
    private static final String KEY_DISABLE_TOOLS_WITH_IMAGE = "disableToolsWithImage";
    private static final String KEY_SEARCH_ENGINE = "searchEngine";
    private static final String KEY_GEMINI_IMAGE_API_KEY = "geminiImageApiKey";
    private static final String KEY_GEMINI_IMAGE_MODEL = "geminiImageModel";

    private static ConfigManager instance;
    private final SharedPreferences preferences;
    private Config config;
    private ProviderSettings activeProvider;
    private String activeProviderId;

    private ConfigManager(Context appContext) {
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String legacyUrl = preferences.getString(KEY_BASE_URL, "https://api.voidai.app/v1");
        activeProviderId = preferences.getString(KEY_ACTIVE_PROVIDER_ID,
                ApiManager.getIdByUrl(legacyUrl));
        activeProvider = loadProvider(activeProviderId, legacyUrl);
        config = loadConfig(activeProvider);
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) instance = new ConfigManager(context.getApplicationContext());
        return instance;
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager not initialized; call getInstance(Context) first");
        }
        return instance;
    }

    private String providerKey(String providerId, String suffix) {
        return "provider." + providerId + "." + suffix;
    }

    private ProviderSettings loadProvider(String providerId, String legacyUrl) {
        String fallbackUrl = legacyUrl == null ? "" : legacyUrl;
        boolean migrateLegacy = !preferences.contains(KEY_ACTIVE_PROVIDER_ID) &&
                providerId != null && providerId.equals(ApiManager.getIdByUrl(legacyUrl));
        if (providerId != null && !"".equals(providerId)) {
            String known = ApiManager.getUrlByName(providerId);
            if (known.equals(providerId)) {
                for (String name : ApiManager.getAllApiNames()) {
                    if (providerId.equals(ApiManager.getIdByName(name))) {
                        known = ApiManager.getUrlByName(name);
                        break;
                    }
                }
            }
            if (!known.equals(providerId)) fallbackUrl = known;
        }
        ProviderSettings settings = new ProviderSettings(providerId, preferences.getString(
                providerKey(providerId, "baseUrl"), fallbackUrl));
        settings.setApiKey(preferences.getString(providerKey(providerId, "apiKey"),
                migrateLegacy ? preferences.getString(KEY_API_KEY, "") : ""));
        settings.setChatModel(preferences.getString(providerKey(providerId, "chatModel"),
                migrateLegacy ? preferences.getString(KEY_CHAT_MODEL, "") : ""));
        settings.setThinkingModel(preferences.getString(providerKey(providerId, "thinkingModel"),
                migrateLegacy ? preferences.getString(KEY_THINKING_MODEL, "") : ""));
        settings.setCacheTimestamp(preferences.getLong(providerKey(providerId, "cacheTimestamp"), 0L));
        settings.setCachedModels(loadModels(preferences.getString(
                providerKey(providerId, "cachedModels"), "")));
        return settings;
    }

    private Config loadConfig(ProviderSettings provider) {
        int storedUpdateDelay = preferences.getInt(KEY_UPDATE_DELAY, 250);
        if (storedUpdateDelay < 10 || storedUpdateDelay > 10000) storedUpdateDelay = 250;
        return new Config(provider.getBaseUrl(), provider.getApiKey(),
                provider.getChatModel(), provider.getThinkingModel(),
                preferences.getBoolean(KEY_SHRINK_THINK, false),
                preferences.getString(KEY_SYSTEM_PROMPT, ""),
                storedUpdateDelay,
                preferences.getBoolean(KEY_WEB_SEARCH_ENABLED, true),
                preferences.getString(KEY_SEARCH_ENGINE, "bing"),
                preferences.getBoolean(KEY_WEB_FETCH_ENABLED,
                        Integer.parseInt(android.os.Build.VERSION.SDK) >= 4),
                preferences.getBoolean(KEY_DISABLE_TOOLS_WITH_IMAGE, true));
    }

    private void saveProvider(ProviderSettings provider) {
        if (provider == null) return;
        SharedPreferences.Editor editor = preferences.edit();
        String id = provider.getProviderId();
        editor.putString(providerKey(id, "baseUrl"), provider.getBaseUrl());
        editor.putString(providerKey(id, "apiKey"), provider.getApiKey());
        editor.putString(providerKey(id, "chatModel"), provider.getChatModel());
        editor.putString(providerKey(id, "thinkingModel"), provider.getThinkingModel());
        editor.putLong(providerKey(id, "cacheTimestamp"), provider.getCacheTimestamp());
        editor.putString(providerKey(id, "cachedModels"), encodeModels(provider.getCachedModels()));
        editor.putString(KEY_ACTIVE_PROVIDER_ID, activeProviderId);
        editor.commit();
    }

    private void saveConfig() {
        activeProvider.setBaseUrl(config.getBaseUrl());
        activeProvider.setApiKey(config.getApiKey());
        activeProvider.setChatModel(config.getChatModel());
        activeProvider.setThinkingModel(config.getThinkingModel());
        saveProvider(activeProvider);

        // Keep legacy keys readable for older installed builds; no request reads them.
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_BASE_URL, config.getBaseUrl());
        editor.putString(KEY_API_KEY, config.getApiKey());
        editor.putString(KEY_CHAT_MODEL, config.getChatModel());
        editor.putString(KEY_THINKING_MODEL, config.getThinkingModel());
        editor.putBoolean(KEY_SHRINK_THINK, config.getShrinkThink());
        editor.putString(KEY_SYSTEM_PROMPT, config.getSystemPrompt());
        editor.putInt(KEY_UPDATE_DELAY, config.getUpdateDelay());
        editor.putBoolean(KEY_WEB_SEARCH_ENABLED, config.isWebSearchEnabled());
        editor.putBoolean(KEY_WEB_FETCH_ENABLED, config.isWebFetchEnabled());
        editor.putBoolean(KEY_DISABLE_TOOLS_WITH_IMAGE, config.isDisableToolsWithImage());
        editor.putString(KEY_SEARCH_ENGINE, config.getSearchEngine());
        editor.commit();
    }

    public synchronized Config getConfig() { return config; }
    public synchronized String getActiveProviderId() { return activeProviderId; }
    public synchronized ProviderSettings getActiveProviderSettings() { return activeProvider; }

    public synchronized ProviderSettings getProviderSettings(String providerId) {
        if (activeProviderId.equals(providerId)) return activeProvider;
        return loadProvider(providerId, ApiManager.getUrlByName(providerId));
    }

    public synchronized void selectProvider(String providerId, String baseUrl) {
        if (providerId == null) providerId = ApiManager.getIdByUrl(baseUrl);
        if (providerId.equals(activeProviderId)) {
            if (baseUrl != null && baseUrl.length() > 0) {
                activeProvider.setBaseUrl(baseUrl);
                config.setBaseUrl(baseUrl);
                saveConfig();
            }
            return;
        }
        saveConfig();
        activeProviderId = providerId;
        activeProvider = loadProvider(providerId, baseUrl);
        if (baseUrl != null && baseUrl.length() > 0) activeProvider.setBaseUrl(baseUrl);
        config = loadConfig(activeProvider);
        preferences.edit().putString(KEY_ACTIVE_PROVIDER_ID, activeProviderId).commit();
    }

    public synchronized void selectProviderByUrl(String baseUrl) {
        selectProvider(ApiManager.getIdByUrl(baseUrl), baseUrl);
    }

    public synchronized void setConfig(Config newConfig) {
        config = newConfig;
        saveConfig();
    }

    public synchronized void updateBaseUrl(String baseUrl) {
        selectProviderByUrl(baseUrl);
    }

    public synchronized void updateApiKey(String apiKey) {
        config.setApiKey(apiKey);
        saveConfig();
    }

    public synchronized void updateChatModel(String model) {
        config.setChatModel(model == null ? "" : model);
        saveConfig();
    }

    public synchronized void updateThinkingModel(String model) {
        config.setThinkingModel(model == null ? "" : model);
        saveConfig();
    }

    public synchronized void updateSystemPrompt(String systemPrompt) {
        config.setSystemPrompt(systemPrompt);
        saveConfig();
    }

    public synchronized boolean isConfigValid() { return config.isValid(); }

    public synchronized void replaceModelCache(String providerId, List<ModelInfo> models) {
        ProviderSettings provider = getProviderSettings(providerId);
        provider.setCachedModels(models);
        provider.setCacheTimestamp(System.currentTimeMillis());
        if (activeProviderId.equals(providerId)) activeProvider = provider;
        saveProvider(provider);
    }

    public synchronized void invalidateModelCache(String providerId) {
        ProviderSettings provider = getProviderSettings(providerId);
        provider.setCachedModels(new ArrayList<ModelInfo>());
        provider.setCacheTimestamp(0L);
        if (activeProviderId.equals(providerId)) activeProvider = provider;
        saveProvider(provider);
    }

    public synchronized ProviderSnapshot createSnapshot() {
        return new ProviderSnapshot(activeProviderId, config.getBaseUrl(), config.getApiKey(),
                config.getChatModel(), config.getThinkingModel(), config.getShrinkThink(),
                config.getSystemPrompt(), config.getUpdateDelay(),
                config.isWebSearchEnabled(), config.getSearchEngine(),
                config.isWebFetchEnabled(), config.isDisableToolsWithImage());
    }

    private String encodeModels(List<ModelInfo> models) {
        JSONArray array = new JSONArray();
        if (models != null) {
            for (int i = 0; i < models.size(); i++) {
                ModelInfo model = models.get(i);
                if (model == null) continue;
                JSONObject object = new JSONObject();
                object.put("id", model.getId());
                object.put("prompt", model.getPromptPrice());
                object.put("completion", model.getCompletionPrice());
                object.put("input", model.getInputPrice());
                object.put("output", model.getOutputPrice());
                array.add(object);
            }
        }
        return array.toString();
    }

    private List<ModelInfo> loadModels(String encoded) {
        ArrayList<ModelInfo> models = new ArrayList<ModelInfo>();
        if (encoded == null || encoded.trim().length() == 0) return models;
        try {
            JSONArray array = JSON.getArray(encoded);
            for (int i = 0; i < array.size(); i++) {
                JSONObject object = array.getObject(i);
                models.add(new ModelInfo(object.getNullableString("id"),
                        object.getNullableString("prompt"),
                        object.getNullableString("completion"),
                        object.getNullableString("input"),
                        object.getNullableString("output")));
            }
        } catch (Exception ignored) {}
        return models;
    }

    /** The Gemini image key is deliberately separate from the chat provider key. */
    public String getGeminiImageApiKey() {
        return preferences.getString(KEY_GEMINI_IMAGE_API_KEY, "");
    }

    public void updateGeminiImageApiKey(String apiKey) {
        preferences.edit().putString(KEY_GEMINI_IMAGE_API_KEY,
                apiKey == null ? "" : apiKey.trim()).commit();
    }

    public String getGeminiImageModel() {
        return preferences.getString(KEY_GEMINI_IMAGE_MODEL, "gemini-3.1-flash-image");
    }

    public void updateGeminiImageModel(String model) {
        String safeModel = model == null ? "" : model.trim();
        if (safeModel.length() == 0) safeModel = "gemini-3.1-flash-image";
        preferences.edit().putString(KEY_GEMINI_IMAGE_MODEL, safeModel).commit();
    }
}
