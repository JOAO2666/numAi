package io.github.gohoski.numai.api;

public class ApiError extends Exception {
    private final String message;
    private final boolean timeout;

    public ApiError(String message) {
        this(message, false);
    }

    public ApiError(String message, boolean timeout) {
        this.message = message;
        this.timeout = timeout;
    }

    public String getMessage() {
        return message;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
