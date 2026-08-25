package io.github.gohoski.numai.api;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiRequest {
    private String endpoint;
    private String method;
    private Map<String, String> headers;
    private Map<String, String> params;
    private String body;
    private String baseUrl;
    private String apiKey;
    private int readTimeout = 15000;

    public ApiRequest(String endpoint, String method) {
        this.endpoint = endpoint;
        this.method = method;
        this.headers = new HashMap<String, String>();
        this.params = new LinkedHashMap<String, String>();
        this.body = "";
        this.baseUrl = null;
        this.apiKey = null;
    }

    public ApiRequest(String baseUrl, String endpoint, String method) {
        this.endpoint = endpoint;
        this.method = method;
        this.headers = new HashMap<String, String>();
        this.params = new LinkedHashMap<String, String>();
        this.body = "";
        this.baseUrl = baseUrl;
        this.apiKey = null;
    }

    public ApiRequest addHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public ApiRequest addParam(String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
        return this;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getReadTimeout() { return readTimeout; }

    public String getEndpoint() {
        if (params == null || params.isEmpty()) {
            return endpoint;
        }

        StringBuilder fullEndpoint = new StringBuilder(endpoint);
        boolean first = !endpoint.contains("?");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            fullEndpoint.append(first ? "?" : "&");
            try {
                fullEndpoint.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                fullEndpoint.append("=");
                fullEndpoint.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException ignored) {}
            first = false;
        }
        return fullEndpoint.toString();
    }

    public String getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getParams() { return params; }
    public String getBody() { return body; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
}
