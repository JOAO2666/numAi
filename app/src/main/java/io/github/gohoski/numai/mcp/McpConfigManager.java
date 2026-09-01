package io.github.gohoski.numai.mcp;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores MCP credentials and OAuth state separately from chat-provider credentials. */
public class McpConfigManager {
    public static final String DEFAULT_ENDPOINT = "";
    private static final String LEGACY_ORACLE_ENDPOINT =
            "https://129-148-23-167.nip.io/mcp";

    private static final String PREFS_NAME = "numAiMcp";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_AUTO_EXECUTE = "autoExecute";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_SERVER_CREDENTIAL = "serverCredential";
    private static final String KEY_ISSUER = "issuer";
    private static final String KEY_AUTH_ENDPOINT = "authorizationEndpoint";
    private static final String KEY_TOKEN_ENDPOINT = "tokenEndpoint";
    private static final String KEY_REGISTRATION_ENDPOINT = "registrationEndpoint";
    private static final String KEY_CLIENT_ID = "clientId";
    private static final String KEY_ACCESS_TOKEN = "accessToken";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";
    private static final String KEY_EXPIRES_AT = "expiresAt";
    private static final String KEY_PENDING_STATE = "pendingState";
    private static final String KEY_PENDING_VERIFIER = "pendingVerifier";
    private static final String KEY_PENDING_ISSUER = "pendingIssuer";
    private static final String KEY_WARNING_ACCEPTED = "warningAccepted";

    private static McpConfigManager instance;
    private final SharedPreferences preferences;

    private McpConfigManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        migrateLegacyEndpoint();
    }

    public static synchronized McpConfigManager getInstance(Context context) {
        if (instance == null) instance = new McpConfigManager(context);
        return instance;
    }

    private void migrateLegacyEndpoint() {
        if (preferences.contains(KEY_ENDPOINT)) return;
        boolean hasLegacyAuthorization = preferences.getString(KEY_ACCESS_TOKEN, "").length() > 0 ||
                preferences.getString(KEY_REFRESH_TOKEN, "").length() > 0 ||
                preferences.getString(KEY_CLIENT_ID, "").length() > 0;
        if (hasLegacyAuthorization) {
            preferences.edit().putString(KEY_ENDPOINT, LEGACY_ORACLE_ENDPOINT).commit();
        }
    }

    public synchronized McpSettings createSnapshot() {
        return new McpSettings(isEnabled(), isAutoExecute(), getEndpoint());
    }

    public synchronized boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public synchronized void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    public synchronized boolean isAutoExecute() {
        return preferences.getBoolean(KEY_AUTO_EXECUTE, false);
    }

    public synchronized void setAutoExecute(boolean autoExecute) {
        preferences.edit().putBoolean(KEY_AUTO_EXECUTE, autoExecute).commit();
    }

    public synchronized String getEndpoint() {
        return preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT);
    }

    public synchronized void setEndpoint(String endpoint) {
        String safe = endpoint == null ? "" : endpoint.trim();
        while (safe.endsWith("/") && safe.length() > 8) {
            safe = safe.substring(0, safe.length() - 1);
        }
        if (!safe.equals(getEndpoint())) {
            clearAuthorization();
            preferences.edit().putString(KEY_ENDPOINT, safe).commit();
        }
    }

    public synchronized boolean isConnected() {
        return getServerCredential().length() > 0 || getAccessToken().length() > 0 ||
                getRefreshToken().length() > 0;
    }

    public synchronized String getServerCredential() {
        return preferences.getString(KEY_SERVER_CREDENTIAL, "");
    }

    public synchronized void setServerCredential(String credential) {
        String value = safe(credential).trim();
        SharedPreferences.Editor editor = preferences.edit();
        if (value.length() == 0) editor.remove(KEY_SERVER_CREDENTIAL);
        else editor.putString(KEY_SERVER_CREDENTIAL, value);
        editor.commit();
    }

    public synchronized void clearServerCredential() {
        preferences.edit().remove(KEY_SERVER_CREDENTIAL).commit();
    }

    public synchronized void clearOAuthAuthorization() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_ISSUER);
        editor.remove(KEY_AUTH_ENDPOINT);
        editor.remove(KEY_TOKEN_ENDPOINT);
        editor.remove(KEY_REGISTRATION_ENDPOINT);
        editor.remove(KEY_CLIENT_ID);
        editor.remove(KEY_ACCESS_TOKEN);
        editor.remove(KEY_REFRESH_TOKEN);
        editor.remove(KEY_EXPIRES_AT);
        editor.remove(KEY_PENDING_STATE);
        editor.remove(KEY_PENDING_VERIFIER);
        editor.remove(KEY_PENDING_ISSUER);
        editor.commit();
    }

    public synchronized String getIssuer() { return preferences.getString(KEY_ISSUER, ""); }
    public synchronized String getAuthorizationEndpoint() { return preferences.getString(KEY_AUTH_ENDPOINT, ""); }
    public synchronized String getTokenEndpoint() { return preferences.getString(KEY_TOKEN_ENDPOINT, ""); }
    public synchronized String getRegistrationEndpoint() { return preferences.getString(KEY_REGISTRATION_ENDPOINT, ""); }
    public synchronized String getClientId() { return preferences.getString(KEY_CLIENT_ID, ""); }
    public synchronized String getAccessToken() { return preferences.getString(KEY_ACCESS_TOKEN, ""); }
    public synchronized String getRefreshToken() { return preferences.getString(KEY_REFRESH_TOKEN, ""); }
    public synchronized long getExpiresAt() { return preferences.getLong(KEY_EXPIRES_AT, 0L); }
    public synchronized String getPendingState() { return preferences.getString(KEY_PENDING_STATE, ""); }
    public synchronized String getPendingVerifier() { return preferences.getString(KEY_PENDING_VERIFIER, ""); }
    public synchronized String getPendingIssuer() { return preferences.getString(KEY_PENDING_ISSUER, ""); }

    public synchronized void saveMetadata(String issuer, String authorizationEndpoint,
            String tokenEndpoint, String registrationEndpoint) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_ISSUER, safe(issuer));
        editor.putString(KEY_AUTH_ENDPOINT, safe(authorizationEndpoint));
        editor.putString(KEY_TOKEN_ENDPOINT, safe(tokenEndpoint));
        editor.putString(KEY_REGISTRATION_ENDPOINT, safe(registrationEndpoint));
        editor.commit();
    }

    public synchronized void saveClientId(String clientId) {
        preferences.edit().putString(KEY_CLIENT_ID, safe(clientId)).commit();
    }

    public synchronized void savePending(String state, String verifier, String issuer) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PENDING_STATE, safe(state));
        editor.putString(KEY_PENDING_VERIFIER, safe(verifier));
        editor.putString(KEY_PENDING_ISSUER, safe(issuer));
        editor.commit();
    }

    public synchronized void clearPending() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_PENDING_STATE);
        editor.remove(KEY_PENDING_VERIFIER);
        editor.remove(KEY_PENDING_ISSUER);
        editor.commit();
    }

    public synchronized void saveTokens(String accessToken, String refreshToken,
            long expiresAt) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_ACCESS_TOKEN, safe(accessToken));
        if (refreshToken != null && refreshToken.length() > 0) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        }
        editor.putLong(KEY_EXPIRES_AT, expiresAt);
        editor.commit();
    }

    public synchronized void clearTokens() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_SERVER_CREDENTIAL);
        editor.remove(KEY_ACCESS_TOKEN);
        editor.remove(KEY_REFRESH_TOKEN);
        editor.remove(KEY_EXPIRES_AT);
        editor.putBoolean(KEY_ENABLED, false);
        editor.putBoolean(KEY_AUTO_EXECUTE, false);
        editor.commit();
    }

    public synchronized void clearAuthorization() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_ISSUER);
        editor.remove(KEY_AUTH_ENDPOINT);
        editor.remove(KEY_TOKEN_ENDPOINT);
        editor.remove(KEY_REGISTRATION_ENDPOINT);
        editor.remove(KEY_CLIENT_ID);
        editor.remove(KEY_SERVER_CREDENTIAL);
        editor.remove(KEY_ACCESS_TOKEN);
        editor.remove(KEY_REFRESH_TOKEN);
        editor.remove(KEY_EXPIRES_AT);
        editor.remove(KEY_PENDING_STATE);
        editor.remove(KEY_PENDING_VERIFIER);
        editor.remove(KEY_PENDING_ISSUER);
        editor.putBoolean(KEY_ENABLED, false);
        editor.putBoolean(KEY_AUTO_EXECUTE, false);
        editor.commit();
    }

    public synchronized boolean isWarningAccepted() {
        return preferences.getBoolean(KEY_WARNING_ACCEPTED, false);
    }

    public synchronized void setWarningAccepted(boolean accepted) {
        preferences.edit().putBoolean(KEY_WARNING_ACCEPTED, accepted).commit();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
