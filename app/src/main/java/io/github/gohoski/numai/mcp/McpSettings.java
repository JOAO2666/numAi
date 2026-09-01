package io.github.gohoski.numai.mcp;

/** Immutable MCP configuration captured when a generation starts. */
public class McpSettings {
    private final boolean enabled;
    private final boolean autoExecute;
    private final String endpoint;

    public McpSettings(boolean enabled, boolean autoExecute, String endpoint) {
        this.enabled = enabled;
        this.autoExecute = autoExecute;
        this.endpoint = endpoint == null ? "" : endpoint.trim();
    }

    public boolean isEnabled() { return enabled; }
    public boolean isAutoExecute() { return autoExecute; }
    public String getEndpoint() { return endpoint; }
}
