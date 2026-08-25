package io.github.gohoski.numai.util;

/** Rejects stale model-list responses after a provider switch or refresh. */
public class ModelLoadCoordinator {
    private String activeProviderId = "";
    private long generation;

    public synchronized RequestToken begin(String providerId) {
        activeProviderId = providerId == null ? "" : providerId;
        generation++;
        return new RequestToken(activeProviderId, generation);
    }

    public synchronized void switchProvider(String providerId) {
        activeProviderId = providerId == null ? "" : providerId;
        generation++;
    }

    public synchronized boolean accepts(RequestToken token) {
        return token != null && token.generation == generation &&
                activeProviderId.equals(token.providerId);
    }

    public synchronized String getActiveProviderId() { return activeProviderId; }

    public static final class RequestToken {
        private final String providerId;
        private final long generation;

        private RequestToken(String providerId, long generation) {
            this.providerId = providerId;
            this.generation = generation;
        }

        public String getProviderId() { return providerId; }
        public long getGeneration() { return generation; }
    }
}
