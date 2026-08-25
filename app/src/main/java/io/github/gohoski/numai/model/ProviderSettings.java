package io.github.gohoski.numai.model;

import java.util.ArrayList;
import java.util.List;

/** All mutable provider-specific settings live behind one stable provider id. */
public class ProviderSettings {
    private final String providerId;
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String thinkingModel;
    private List<ModelInfo> cachedModels;
    private long cacheTimestamp;

    public ProviderSettings(String providerId, String baseUrl) {
        this.providerId = providerId == null ? "" : providerId;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.apiKey = "";
        this.chatModel = "";
        this.thinkingModel = "";
        this.cachedModels = new ArrayList<ModelInfo>();
    }

    public String getProviderId() { return providerId; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value == null ? "" : value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value == null ? "" : value; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String value) { chatModel = value == null ? "" : value; }
    public String getThinkingModel() { return thinkingModel; }
    public void setThinkingModel(String value) { thinkingModel = value == null ? "" : value; }
    public List<ModelInfo> getCachedModels() { return cachedModels; }
    public void setCachedModels(List<ModelInfo> value) {
        cachedModels = value == null ? new ArrayList<ModelInfo>() :
                new ArrayList<ModelInfo>(value);
    }
    public long getCacheTimestamp() { return cacheTimestamp; }
    public void setCacheTimestamp(long value) { cacheTimestamp = value; }
}
