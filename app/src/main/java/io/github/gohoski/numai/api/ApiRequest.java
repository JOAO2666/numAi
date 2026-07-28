package io.github.gohoski.numai.api;

import java.util.HashMap;
import java.util.Map;

public class ApiRequest {
    private String endpoint;
    private String method;
    private Map<String, String> headers;
    private String body;

    public ApiRequest(String endpoint, String method) {
        this.endpoint = endpoint;
        this.method = method;
        this.headers = new HashMap<String, String>();
        this.body = "";
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getEndpoint() { return endpoint; }
    public String getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
}
