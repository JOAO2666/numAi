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
    private boolean webFetchEnabled;
    private boolean disableToolsWithImage;
    private String searchEngine;

    public Config() {}

    public Config(String baseUrl, String apiKey, String chatModel, String thinkingModel, boolean shrinkThink, String systemPrompt, int updateDelay) {
        this(baseUrl, apiKey, chatModel, thinkingModel, shrinkThink, systemPrompt, updateDelay, false, "bing", true, true);
    }

    public Config(String baseUrl, String apiKey, String chatModel, String thinkingModel, boolean shrinkThink, String systemPrompt, int updateDelay, boolean webSearchEnabled, String searchEngine, boolean webFetchEnabled, boolean disableToolsWithImage) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.chatModel = chatModel;
        this.thinkingModel = thinkingModel;
        this.shrinkThink = shrinkThink;
        this.systemPrompt = systemPrompt;
        this.updateDelay = updateDelay;
        this.webSearchEnabled = webSearchEnabled;
        this.searchEngine = searchEngine;
        this.webFetchEnabled = webFetchEnabled;
        this.disableToolsWithImage = disableToolsWithImage;
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey.trim(); }

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

    public boolean isWebFetchEnabled() { return webFetchEnabled; }
    public void setWebFetchEnabled(boolean webFetchEnabled) { this.webFetchEnabled = webFetchEnabled; }

    public boolean isDisableToolsWithImage() { return disableToolsWithImage; }
    public void setDisableToolsWithImage(boolean disableToolsWithImage) { this.disableToolsWithImage = disableToolsWithImage; }

    public String getSearchEngine() { return searchEngine; }
    public void setSearchEngine(String searchEngine) { this.searchEngine = searchEngine; }

    public boolean isValid() {
        return baseUrl != null && baseUrl.trim().length() != 0 &&
                apiKey != null && apiKey.trim().length() != 0 &&
                chatModel != null && chatModel.trim().length() != 0;
    }
}
