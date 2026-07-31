package io.github.gohoski.numai.model;

public class Config {
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String thinkingModel;
    private boolean shrinkThink;
    private String systemPrompt;
    private int updateDelay;
    private boolean webSearchEnabled;
    private String searchEngine;
    private int maxSearchResults;

    public Config() {}

    public Config(String baseUrl, String apiKey, String chatModel, String thinkingModel, boolean shrinkThink, String systemPrompt, int updateDelay) {
        this(baseUrl, apiKey, chatModel, thinkingModel, shrinkThink, systemPrompt, updateDelay, false, "bing", 5);
    }

    public Config(String baseUrl, String apiKey, String chatModel, String thinkingModel, boolean shrinkThink, String systemPrompt, int updateDelay, boolean webSearchEnabled, String searchEngine, int maxSearchResults) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.thinkingModel = thinkingModel;
        this.shrinkThink = shrinkThink;
        this.systemPrompt = systemPrompt;
        this.updateDelay = updateDelay;
        this.webSearchEnabled = webSearchEnabled;
        this.searchEngine = searchEngine;
        this.maxSearchResults = maxSearchResults;
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }

    public String getThinkingModel() { return thinkingModel; }
    public void setThinkingModel(String thinkingModel) { this.thinkingModel = thinkingModel; }

    public boolean getShrinkThink() { return shrinkThink; }
    public void setShrinkThink(boolean shrinkThink) { this.shrinkThink = shrinkThink; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public int getUpdateDelay() { return updateDelay; }
    public void setUpdateDelay(int updateDelay) { this.updateDelay = updateDelay; }

    public boolean isWebSearchEnabled() { return webSearchEnabled; }
    public void setWebSearchEnabled(boolean webSearchEnabled) { this.webSearchEnabled = webSearchEnabled; }

    public String getSearchEngine() { return searchEngine; }
    public void setSearchEngine(String searchEngine) { this.searchEngine = searchEngine; }

    public int getMaxSearchResults() { return maxSearchResults; }
    public void setMaxSearchResults(int maxSearchResults) { this.maxSearchResults = maxSearchResults; }

    public boolean isValid() {
        return baseUrl != null && baseUrl.trim().length() != 0 &&
                apiKey != null && apiKey.trim().length() != 0 &&
                chatModel != null && chatModel.trim().length() != 0;
    }
}