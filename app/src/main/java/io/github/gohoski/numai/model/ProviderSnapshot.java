package io.github.gohoski.numai.model;

/** Immutable request configuration captured at send time. */
public class ProviderSnapshot {
    private final String providerId;
    private final String baseUrl;
    private final String apiKey;
    private final String chatModel;
    private final String thinkingModel;
    private final boolean shrinkThink;
    private final String systemPrompt;
    private final int updateDelay;
    private final boolean webSearchEnabled;
    private final String searchEngine;
    private final boolean webFetchEnabled;
    private final boolean disableToolsWithImage;

    public ProviderSnapshot(String providerId, String baseUrl, String apiKey,
            String chatModel, String thinkingModel, boolean shrinkThink,
            String systemPrompt, int updateDelay, boolean webSearchEnabled,
            String searchEngine, boolean webFetchEnabled,
            boolean disableToolsWithImage) {
        this.providerId = providerId == null ? "" : providerId;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.chatModel = chatModel == null ? "" : chatModel;
        this.thinkingModel = thinkingModel == null ? "" : thinkingModel;
        this.shrinkThink = shrinkThink;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.updateDelay = updateDelay;
        this.webSearchEnabled = webSearchEnabled;
        this.searchEngine = searchEngine == null ? "bing" : searchEngine;
        this.webFetchEnabled = webFetchEnabled;
        this.disableToolsWithImage = disableToolsWithImage;
    }

    public String getProviderId() { return providerId; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public String getChatModel() { return chatModel; }
    public String getThinkingModel() { return thinkingModel; }
    public boolean getShrinkThink() { return shrinkThink; }
    public String getSystemPrompt() { return systemPrompt; }
    public int getUpdateDelay() { return updateDelay; }
    public boolean isWebSearchEnabled() { return webSearchEnabled; }
    public String getSearchEngine() { return searchEngine; }
    public boolean isWebFetchEnabled() { return webFetchEnabled; }
    public boolean isDisableToolsWithImage() { return disableToolsWithImage; }
}
