package io.github.gohoski.numai.data;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.gohoski.numai.model.Config;

public class ConfigManager {
    private static final String PREFS_NAME = "numAi",
            KEY_BASE_URL = "baseUrl",
            KEY_API_KEY = "apiKey",
            KEY_CHAT_MODEL = "chatModel",
            KEY_THINKING_MODEL = "thinkingModel",
            KEY_SHRINK_THINK = "shrinkThink",
            KEY_SYSTEM_PROMPT = "systemPrompt",
            KEY_UPDATE_DELAY = "updateDelay",
            KEY_WEB_SEARCH_ENABLED = "webSearchEnabled",
            KEY_WEB_FETCH_ENABLED = "webFetchEnabled",
            KEY_SEARCH_ENGINE = "searchEngine";

    private static ConfigManager instance;
    private final SharedPreferences preferences;
    private Config config;

    private ConfigManager(Context appContext) {
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        config = loadConfig();
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null)
            instance = new ConfigManager(context.getApplicationContext());
        return instance;
    }

    public static ConfigManager getInstance() {
        if (instance == null)
            throw new IllegalStateException("ConfigManager not initialized; call getInstance(Context) first");
        return instance;
    }

    private Config loadConfig() {
        return new Config(preferences.getString(KEY_BASE_URL, "https://api.voidai.app/v1"),
                preferences.getString(KEY_API_KEY, ""),
                preferences.getString(KEY_CHAT_MODEL, ""),
                preferences.getString(KEY_THINKING_MODEL, ""),
                preferences.getBoolean(KEY_SHRINK_THINK, false),
                preferences.getString(KEY_SYSTEM_PROMPT, ""),
                preferences.getInt(KEY_UPDATE_DELAY, 250),
                preferences.getBoolean(KEY_WEB_SEARCH_ENABLED, true),
                preferences.getString(KEY_SEARCH_ENGINE, "bing"),
                preferences.getBoolean(KEY_WEB_FETCH_ENABLED, Integer.parseInt(android.os.Build.VERSION.SDK) >= 4));
    }

    private void saveConfig() {
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
        editor.putString(KEY_SEARCH_ENGINE, config.getSearchEngine());
        editor.commit();
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
        saveConfig();
    }

    public void updateBaseUrl(String baseUrl) {
        config.setBaseUrl(baseUrl);
        saveConfig();
    }

    public void updateApiKey(String apiKey) {
        config.setApiKey(apiKey);
        saveConfig();
    }

    public void updateChatModel(String model) {
        config.setChatModel(model);
        saveConfig();
    }
    public void updateThinkingModel(String model) {
        config.setThinkingModel(model);
        saveConfig();
    }

    public void updateSystemPrompt(String systemPrompt) {
        config.setSystemPrompt(systemPrompt);
        saveConfig();
    }

    public boolean isConfigValid() {
        return config.isValid();
    }
}