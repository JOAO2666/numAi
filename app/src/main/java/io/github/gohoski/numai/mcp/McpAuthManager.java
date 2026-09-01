package io.github.gohoski.numai.mcp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.net.URI;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.api.ApiClient;
import io.github.gohoski.numai.api.ApiRequest;
import io.github.gohoski.numai.api.ApiResponse;
import io.github.gohoski.numai.util.Base64;

public class McpAuthManager {
    public static final String REDIRECT_URI = "io.github.gohoski.numai://oauth/mcp";
    private static final String SCOPE = "mcp:tools";
    private final Activity activity;
    private final McpConfigManager config;
    private final ApiClient api;

    public interface Callback {
        void onBrowserOpened();
        void onConnected();
        void onError(String message);
    }

    public McpAuthManager(Activity activity) {
        this.activity = activity;
        this.config = McpConfigManager.getInstance(activity);
        this.api = new ApiClient(activity);
    }

    public McpAuthManager(Context context) {
        this.activity = null;
        this.config = McpConfigManager.getInstance(context);
        this.api = new ApiClient(context);
    }

    public void startAuthorization(final String endpoint, final Callback callback) {
        if (activity == null) {
            if (callback != null) callback.onError("MCP authorization requires an Activity.");
            return;
        }
        config.setEndpoint(endpoint);
        config.clearServerCredential();
        new Thread(new Runnable() {
            public void run() {
                try {
                    discoverMetadata();
                    if (config.getClientId().length() == 0) registerClient();
                    final String verifier = randomToken(48);
                    final String state = randomToken(32);
                    final String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes("US-ASCII")));
                    config.savePending(state, verifier, config.getIssuer());
                    final String url = authorizationUrl(challenge, state);
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                                if (callback != null) callback.onBrowserOpened();
                            } catch (Exception e) {
                                if (callback != null) callback.onError("No browser is available for MCP authorization.");
                            }
                        }
                    });
                } catch (final Exception e) {
                    deliverError(callback, safeMessage(e));
                }
            }
        }).start();
    }

    public boolean isCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri data = intent.getData();
        return "io.github.gohoski.numai".equals(data.getScheme()) &&
                "oauth".equals(data.getHost()) && "/mcp".equals(data.getPath());
    }

    public void handleCallback(final Intent intent, final Callback callback) {
        if (activity == null) {
            if (callback != null) callback.onError("MCP authorization requires an Activity.");
            return;
        }
        if (!isCallback(intent)) return;
        final Uri data = intent.getData();
        new Thread(new Runnable() {
            public void run() {
                try {
                    String error = data.getQueryParameter("error");
                    if (error != null) throw new McpException("Authorization denied: " + error);
                    String state = data.getQueryParameter("state");
                    if (state == null || !state.equals(config.getPendingState())) {
                        throw new McpException("Invalid OAuth state returned by the MCP server.");
                    }
                    String issuer = data.getQueryParameter("iss");
                    if (issuer == null || !issuer.equals(config.getPendingIssuer())) {
                        throw new McpException("Unexpected OAuth issuer returned by the MCP server.");
                    }
                    String code = data.getQueryParameter("code");
                    if (code == null || code.length() == 0) {
                        throw new McpException("The MCP server did not return an authorization code.");
                    }
                    exchangeCode(code, config.getPendingVerifier());
                    config.clearPending();
                    config.setEnabled(true);
                    deliverConnected(callback);
                } catch (final Exception e) {
                    config.clearPending();
                    deliverError(callback, safeMessage(e));
                }
            }
        }).start();
    }

    public synchronized String getValidAccessToken(String endpoint) throws McpException {
        if (endpoint == null || !endpoint.equals(config.getEndpoint())) {
            throw new McpException("MCP endpoint changed; reconnect it in Settings.");
        }
        String access = config.getAccessToken();
        long expiresAt = config.getExpiresAt();
        if (access.length() > 0 && (expiresAt == 0L ||
                System.currentTimeMillis() + 60000L < expiresAt)) return access;
        return refreshAccessToken();
    }

    public synchronized String forceRefresh(String endpoint) throws McpException {
        if (endpoint == null || !endpoint.equals(config.getEndpoint())) {
            throw new McpException("MCP endpoint changed; reconnect it in Settings.");
        }
        return refreshAccessToken();
    }

    private void discoverMetadata() throws Exception {
        String endpoint = config.getEndpoint();
        requireHttps(endpoint, "MCP endpoint");
        URI uri = new URI(endpoint);
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        String path = uri.getPath();
        if (path == null || "/".equals(path)) path = "";
        String protectedUrl = origin + "/.well-known/oauth-protected-resource" + path;
        JSONObject protectedMetadata = getJson(protectedUrl);
        String resource = protectedMetadata.getNullableString("resource");
        if (resource == null || !trimTrailingSlash(endpoint).equals(
                trimTrailingSlash(resource))) {
            throw new McpException("MCP protected-resource metadata does not match the endpoint.");
        }
        JSONArray servers = protectedMetadata.getNullableArray("authorization_servers");
        if (servers == null || servers.size() == 0) {
            throw new McpException("MCP server did not publish an authorization server.");
        }
        String authorizationServer = servers.getString(0);
        requireHttps(authorizationServer, "MCP authorization server");
        JSONObject metadata = getJson(joinWellKnown(authorizationServer));
        String issuer = metadata.getNullableString("issuer");
        String authEndpoint = metadata.getNullableString("authorization_endpoint");
        String tokenEndpoint = metadata.getNullableString("token_endpoint");
        String registrationEndpoint = metadata.getNullableString("registration_endpoint");
        JSONArray methods = metadata.getNullableArray("code_challenge_methods_supported");
        boolean supportsS256 = false;
        if (methods != null) {
            for (int i = 0; i < methods.size(); i++) {
                if ("S256".equals(methods.getString(i, ""))) supportsS256 = true;
            }
        }
        if (issuer == null || authEndpoint == null || tokenEndpoint == null || !supportsS256) {
            throw new McpException("MCP OAuth metadata is incomplete or does not support PKCE S256.");
        }
        if (!trimTrailingSlash(authorizationServer).equals(trimTrailingSlash(issuer))) {
            throw new McpException("MCP OAuth issuer does not match its discovery address.");
        }
        requireHttps(issuer, "MCP OAuth issuer");
        requireHttps(authEndpoint, "MCP authorization endpoint");
        requireHttps(tokenEndpoint, "MCP token endpoint");
        if (registrationEndpoint == null || registrationEndpoint.length() == 0) {
            throw new McpException("MCP server does not support dynamic client registration.");
        }
        requireHttps(registrationEndpoint, "MCP registration endpoint");
        String previousIssuer = config.getIssuer();
        if (previousIssuer.length() > 0 && !previousIssuer.equals(issuer)) {
            config.clearAuthorization();
        }
        config.saveMetadata(issuer, authEndpoint, tokenEndpoint, registrationEndpoint);
    }

    private JSONObject getJson(String url) throws Exception {
        ApiResponse response = api.execute(new ApiRequest(url, "", "GET"));
        String body = api.readInputStreamToString(response.getBody());
        if (!response.isSuccessful()) throw new McpException(httpError(response, body));
        return JSON.getObject(body);
    }

    private void registerClient() throws Exception {
        JSONObject body = new JSONObject();
        JSONArray redirects = new JSONArray();
        redirects.add(REDIRECT_URI);
        body.put("redirect_uris", redirects);
        body.put("client_name", "numAi Android");
        body.put("client_uri", "https://github.com/JOAO2666/numAi");
        body.put("application_type", "native");
        body.put("token_endpoint_auth_method", "none");
        JSONArray grants = new JSONArray();
        grants.add("authorization_code");
        grants.add("refresh_token");
        body.put("grant_types", grants);
        JSONArray responses = new JSONArray();
        responses.add("code");
        body.put("response_types", responses);
        ApiRequest request = new ApiRequest(config.getRegistrationEndpoint(), "", "POST");
        request.setBody(body.toString());
        ApiResponse response = api.execute(request);
        String responseBody = api.readInputStreamToString(response.getBody());
        if (!response.isSuccessful()) throw new McpException(httpError(response, responseBody));
        String clientId = JSON.getObject(responseBody).getNullableString("client_id");
        if (clientId == null || clientId.length() == 0) {
            throw new McpException("MCP registration did not return a client ID.");
        }
        config.saveClientId(clientId);
    }

    private String authorizationUrl(String challenge, String state) throws Exception {
        StringBuilder url = new StringBuilder(config.getAuthorizationEndpoint());
        url.append(config.getAuthorizationEndpoint().indexOf('?') >= 0 ? '&' : '?');
        append(url, "response_type", "code");
        append(url, "client_id", config.getClientId());
        append(url, "redirect_uri", REDIRECT_URI);
        append(url, "code_challenge", challenge);
        append(url, "code_challenge_method", "S256");
        append(url, "scope", SCOPE);
        append(url, "resource", config.getEndpoint());
        append(url, "state", state);
        return url.toString();
    }

    private void exchangeCode(String code, String verifier) throws Exception {
        StringBuilder form = new StringBuilder();
        formField(form, "grant_type", "authorization_code");
        formField(form, "client_id", config.getClientId());
        formField(form, "code", code);
        formField(form, "redirect_uri", REDIRECT_URI);
        formField(form, "code_verifier", verifier);
        formField(form, "resource", config.getEndpoint());
        saveTokenResponse(postForm(config.getTokenEndpoint(), form.toString()));
    }

    private String refreshAccessToken() throws McpException {
        String refresh = config.getRefreshToken();
        if (refresh.length() == 0 || config.getTokenEndpoint().length() == 0) {
            config.clearTokens();
            throw new McpException("MCP authorization expired; reconnect it in Settings.");
        }
        try {
            StringBuilder form = new StringBuilder();
            formField(form, "grant_type", "refresh_token");
            formField(form, "client_id", config.getClientId());
            formField(form, "refresh_token", refresh);
            formField(form, "resource", config.getEndpoint());
            saveTokenResponse(postForm(config.getTokenEndpoint(), form.toString()));
            return config.getAccessToken();
        } catch (Exception e) {
            config.clearTokens();
            throw new McpException("MCP token refresh failed; reconnect it in Settings.");
        }
    }

    private JSONObject postForm(String url, String form) throws Exception {
        ApiRequest request = new ApiRequest(url, "", "POST");
        request.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        request.setBody(form);
        ApiResponse response = api.execute(request);
        String body = api.readInputStreamToString(response.getBody());
        if (!response.isSuccessful()) throw new McpException(httpError(response, body));
        return JSON.getObject(body);
    }

    private void saveTokenResponse(JSONObject tokens) throws McpException {
        String access = tokens.getNullableString("access_token");
        if (access == null || access.length() == 0) {
            throw new McpException("OAuth token response did not include an access token.");
        }
        String refresh = tokens.getNullableString("refresh_token");
        long expiresIn = tokens.getLong("expires_in", 3600L);
        long expiresAt = expiresIn <= 0 ? 0L : System.currentTimeMillis() + expiresIn * 1000L;
        config.saveTokens(access, refresh, expiresAt);
    }

    private static String joinWellKnown(String server) {
        try {
            URI uri = new URI(server);
            String path = uri.getPath();
            if (path == null || "/".equals(path)) path = "";
            return uri.getScheme() + "://" + uri.getAuthority() +
                    "/.well-known/oauth-authorization-server" + path;
        } catch (Exception ignored) {
            String value = server == null ? "" : server;
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            return value + "/.well-known/oauth-authorization-server";
        }
    }

    private static void requireHttps(String value, String label) throws Exception {
        URI uri = new URI(value == null ? "" : value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getAuthority() == null) {
            throw new McpException(label + " must use HTTPS.");
        }
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/") && result.length() > 8) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static void append(StringBuilder url, String key, String value) throws Exception {
        if (url.charAt(url.length() - 1) != '?' && url.charAt(url.length() - 1) != '&') url.append('&');
        url.append(encode(key)).append('=').append(encode(value));
    }

    private static void formField(StringBuilder form, String key, String value) throws Exception {
        if (form.length() > 0) form.append('&');
        form.append(encode(key)).append('=').append(encode(value));
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    static String randomToken(int bytes) {
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        return base64Url(data);
    }

    static String base64Url(byte[] data) {
        String value = Base64.encode(data).replace('+', '-').replace('/', '_');
        while (value.endsWith("=")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String httpError(ApiResponse response, String body) {
        String message = "HTTP " + response.getStatusCode();
        try {
            JSONObject json = JSON.getObject(body);
            String detail = json.getNullableString("message");
            if (detail == null) detail = json.getNullableString("error_description");
            if (detail != null && detail.length() > 0) message += ": " + detail;
        } catch (Exception ignored) {}
        return message;
    }

    private void deliverConnected(final Callback callback) {
        if (activity == null) {
            if (callback != null) callback.onConnected();
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() { if (callback != null) callback.onConnected(); }
        });
    }

    private void deliverError(final Callback callback, final String message) {
        if (activity == null) {
            if (callback != null) callback.onError(message);
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() { if (callback != null) callback.onError(message); }
        });
    }

    private static String safeMessage(Exception e) {
        String value = e == null ? null : e.getMessage();
        return value == null || value.length() == 0 ? "MCP authorization failed." : value;
    }
}
