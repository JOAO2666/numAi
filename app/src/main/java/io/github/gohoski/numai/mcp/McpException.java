package io.github.gohoski.numai.mcp;

public class McpException extends Exception {
    private final int httpStatus;
    private final int protocolCode;
    private final boolean legacyFallbackAllowed;

    public McpException(String message) {
        this(message, null, 0, 0, false);
    }

    public McpException(String message, Throwable cause) {
        this(message, cause, 0, 0, false);
    }

    McpException(String message, int httpStatus, int protocolCode,
            boolean legacyFallbackAllowed) {
        this(message, null, httpStatus, protocolCode, legacyFallbackAllowed);
    }

    private McpException(String message, Throwable cause, int httpStatus,
            int protocolCode, boolean legacyFallbackAllowed) {
        super(message == null ? "MCP error" : message, cause);
        this.httpStatus = httpStatus;
        this.protocolCode = protocolCode;
        this.legacyFallbackAllowed = legacyFallbackAllowed;
    }

    public int getHttpStatus() { return httpStatus; }
    public int getProtocolCode() { return protocolCode; }
    boolean isLegacyFallbackAllowed() { return legacyFallbackAllowed; }
}
