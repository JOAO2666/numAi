package io.github.gohoski.numai.api;

public class ApiError extends Exception {
    private final String message;

    public ApiError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
