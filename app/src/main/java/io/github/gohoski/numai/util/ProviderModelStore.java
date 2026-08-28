package io.github.gohoski.numai.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gohoski.numai.model.ModelInfo;
import io.github.gohoski.numai.model.ProviderSettings;

/** In-memory core of the provider cache rules; persistence is supplied by ConfigManager. */
public class ProviderModelStore {
    private final Map<String, ProviderSettings> providers =
            new HashMap<String, ProviderSettings>();
    private final ModelLoadCoordinator coordinator = new ModelLoadCoordinator();
    private String activeProviderId = "";

    public synchronized ProviderSettings switchProvider(String providerId, String baseUrl) {
        activeProviderId = providerId == null ? "" : providerId;
        ProviderSettings provider = providers.get(activeProviderId);
        if (provider == null) {
            provider = new ProviderSettings(activeProviderId, baseUrl);
            providers.put(activeProviderId, provider);
        } else if (baseUrl != null && baseUrl.length() > 0) {
            provider.setBaseUrl(baseUrl);
        }
        coordinator.switchProvider(activeProviderId);
        return provider;
    }

    public synchronized String getActiveProviderId() { return activeProviderId; }

    public synchronized ProviderSettings get(String providerId) {
        return providers.get(providerId);
    }

    public synchronized ModelLoadCoordinator.RequestToken beginLoad() {
        return coordinator.begin(activeProviderId);
    }

    public synchronized boolean applyLoad(ModelLoadCoordinator.RequestToken token,
            String providerId, List<ModelInfo> models) {
        if (!activeProviderId.equals(providerId) || !coordinator.accepts(token)) return false;
        ProviderSettings provider = providers.get(providerId);
        if (provider == null) return false;
        provider.setCachedModels(models);
        provider.setCacheTimestamp(System.currentTimeMillis());
        provider.setChatModel(ModelCatalog.validSelection(provider.getChatModel(), models, false));
        provider.setThinkingModel(ModelCatalog.validSelection(
                provider.getThinkingModel(), models, true));
        return true;
    }

    public synchronized void refreshCurrent() {
        ProviderSettings provider = providers.get(activeProviderId);
        if (provider != null) {
            provider.setCachedModels(null);
            provider.setCacheTimestamp(0L);
        }
        coordinator.begin(activeProviderId);
    }
}
