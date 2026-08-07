package io.github.gohoski.numai.api;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class ApiResponse {
    private int statusCode;
    private InputStream body;
    private boolean successful;
    private Map<String, List<String>> headers;

    public ApiResponse(int statusCode, InputStream body) {
        this(statusCode, body, null);
    }

    public ApiResponse(int statusCode, InputStream body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.successful = (statusCode >= 200 && statusCode < 300);
        this.headers = headers;
    }

    public int getStatusCode() { return statusCode; }
    public InputStream getBody() { return body; }
    public boolean isSuccessful() { return successful; }
    public Map<String, List<String>> getHeaders() { return headers; }

    public String getHeader(String name) {
        if (headers == null || name == null) return null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }
        return null;
    }
}