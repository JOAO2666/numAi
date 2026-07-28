package io.github.gohoski.numai.api;

import java.io.InputStream;

public class ApiResponse {
    private int statusCode;
    private InputStream body;
    private boolean successful;

    public ApiResponse(int statusCode, InputStream body) {
        this.statusCode = statusCode;
        this.body = body;
        this.successful = (statusCode >= 200 && statusCode < 300);
    }

    public int getStatusCode() { return statusCode; }
    public InputStream getBody() { return body; }
    public boolean isSuccessful() { return successful; }
}
