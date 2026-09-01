package io.github.gohoski.numai.mcp;

import android.content.Context;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.numai.BuildConfig;
import io.github.gohoski.numai.api.ApiClient;
import io.github.gohoski.numai.api.ApiRequest;
import io.github.gohoski.numai.api.ApiResponse;
import io.github.gohoski.numai.util.Base64;

/** Minimal dependency-free MCP Streamable HTTP client. */
public class McpClient {
    private static final String MODERN_VERSION = "2026-07-28";
    private static final String LEGACY_VERSION = "2025-11-25";
    private static final int MAX_RESULT_LENGTH = 15000;

    private final String endpoint;
    private final ApiClient api;
    private final McpAuthManager auth;
    private final McpConfigManager config;
    private boolean modern;
    private boolean initialized;
    private String protocolVersion = LEGACY_VERSION;
    private String sessionId;
    private long nextRequestId = 1L;
    private boolean recoveringSession;

    public McpClient(Context context, String endpoint) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.api = new ApiClient(context);
        this.auth = new McpAuthManager(context);
        this.config = McpConfigManager.getInstance(context);
    }

    public synchronized McpCatalog listTools() throws McpException {
        ensureConnected();
        List<JSONObject> definitions = new ArrayList<JSONObject>();
        String cursor = null;
        do {
            JSONObject params = new JSONObject();
            if (cursor != null && cursor.length() > 0) params.put("cursor", cursor);
            JSONObject response = sendRequest("tools/list", null, params, null, true);
            JSONObject result = resultOf(response);
            JSONArray tools = result.getNullableArray("tools");
            if (tools != null) {
                for (int i = 0; i < tools.size(); i++) {
                    JSONObject definition = tools.getObject(i);
                    if (definition != null) definitions.add(definition);
                }
            }
            cursor = result.getNullableString("nextCursor");
        } while (cursor != null && cursor.length() > 0);

        Collections.sort(definitions, new Comparator<JSONObject>() {
            public int compare(JSONObject first, JSONObject second) {
                String a = first == null ? "" : first.getString("name", "");
                String b = second == null ? "" : second.getString("name", "");
                return a.compareTo(b);
            }
        });

        Map<String, Boolean> used = new HashMap<String, Boolean>();
        List<McpTool> tools = new ArrayList<McpTool>();
        for (int i = 0; i < definitions.size(); i++) {
            JSONObject definition = definitions.get(i);
            String name = definition.getNullableString("name");
            if (!isValidToolName(name)) continue;
            JSONObject schema = definition.getNullableObject("inputSchema");
            tools.add(new McpTool(name, McpCatalog.mappedName(name, used),
                    definition.getNullableString("description"), schema));
        }
        return new McpCatalog(tools);
    }

    public synchronized String callTool(McpTool tool, JSONObject arguments)
            throws McpException {
        if (tool == null) throw new McpException("Unknown MCP tool.");
        ensureConnected();
        JSONObject params = new JSONObject();
        params.put("name", tool.getName());
        params.put("arguments", arguments == null ? new JSONObject() : arguments);
        Map<String, String> headers = modern ? mirroredHeaders(tool, arguments) : null;
        JSONObject response = sendRequest("tools/call", tool.getName(), params,
                headers, true);
        return normalizeResult(resultOf(response));
    }

    private void ensureConnected() throws McpException {
        if (initialized) return;
        getAuthorizationToken();
        try {
            modern = true;
            protocolVersion = MODERN_VERSION;
            JSONObject discover = resultOf(sendRequest("server/discover", null,
                    new JSONObject(), null, true));
            if (!supportsModernVersion(discover)) {
                throw new McpException("MCP server does not advertise protocol " +
                        MODERN_VERSION + ".", 0, 0, true);
            }
            initialized = true;
        } catch (McpException modernError) {
            if (!modernError.isLegacyFallbackAllowed()) throw modernError;
            modern = false;
            protocolVersion = LEGACY_VERSION;
            initializeLegacy();
        }
    }

    private void initializeLegacy() throws McpException {
        JSONObject params = new JSONObject();
        params.put("protocolVersion", LEGACY_VERSION);
        params.put("capabilities", new JSONObject());
        JSONObject clientInfo = new JSONObject();
        clientInfo.put("name", "numAi");
        clientInfo.put("version", BuildConfig.VERSION_NAME);
        params.put("clientInfo", clientInfo);
        WireResponse wire = sendWire("initialize", null, params, null, true);
        JSONObject response = parseResponse(wire.body);
        JSONObject result = resultOf(response);
        String selected = result.getNullableString("protocolVersion");
        if (selected != null && selected.length() > 0) protocolVersion = selected;
        sessionId = wire.sessionId;
        sendNotification("notifications/initialized", new JSONObject());
        initialized = true;
    }

    private JSONObject sendRequest(String method, String name, JSONObject params,
            Map<String, String> extraHeaders, boolean allowRefresh) throws McpException {
        WireResponse wire = sendWire(method, name, params, extraHeaders,
                allowRefresh);
        return parseResponse(wire.body);
    }

    private void sendNotification(String method, JSONObject params) throws McpException {
        JSONObject body = requestBody(null, method, params);
        ApiRequest request = baseRequest(method, null, null);
        request.setBody(body.toString());
        try {
            ApiResponse response = api.execute(request);
            String responseBody = api.readInputStreamToString(response.getBody());
            if (!response.isSuccessful()) {
                throw responseError(response.getStatusCode(), responseBody);
            }
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException(safeMessage(e));
        }
    }

    private WireResponse sendWire(String method, String name, JSONObject params,
            Map<String, String> extraHeaders, boolean allowRefresh) throws McpException {
        Long requestId = Long.valueOf(nextRequestId++);
        JSONObject body = requestBody(requestId, method, params);
        ApiRequest request = baseRequest(method, name, extraHeaders);
        request.setBody(body.toString());
        try {
            ApiResponse response = api.execute(request);
            String responseBody = api.readInputStreamToString(response.getBody());
            if (response.getStatusCode() == 401 && allowRefresh) {
                if (config.getServerCredential().length() == 0) {
                    auth.forceRefresh(endpoint);
                    return sendWire(method, name, params, extraHeaders, false);
                }
            }
            if (response.getStatusCode() == 404 && !modern && initialized &&
                    !recoveringSession) {
                recoveringSession = true;
                try {
                    initialized = false;
                    sessionId = null;
                    protocolVersion = LEGACY_VERSION;
                    initializeLegacy();
                    return sendWire(method, name, params, extraHeaders,
                            allowRefresh);
                } finally {
                    recoveringSession = false;
                }
            }
            if (!response.isSuccessful()) {
                throw responseError(response.getStatusCode(), responseBody);
            }
            return new WireResponse(responseBody, response.getHeader("Mcp-Session-Id"));
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException(safeMessage(e));
        }
    }

    private ApiRequest baseRequest(String method, String name,
            Map<String, String> extraHeaders) throws McpException {
        ApiRequest request = new ApiRequest(endpoint, "", "POST");
        request.setApiKey(getAuthorizationToken());
        request.setReadTimeout(40000);
        request.addHeader("Accept", "application/json, text/event-stream");
        request.addHeader("Content-Type", "application/json; charset=UTF-8");
        request.addHeader("Mcp-Method", method);
        request.addHeader("MCP-Protocol-Version", protocolVersion);
        if (name != null && name.length() > 0) request.addHeader("Mcp-Name", name);
        if (!modern && sessionId != null && sessionId.length() > 0) {
            request.addHeader("Mcp-Session-Id", sessionId);
        }
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return request;
    }

    private String getAuthorizationToken() throws McpException {
        String credential = config.getServerCredential();
        if (credential.length() > 0) return credential;
        return auth.getValidAccessToken(endpoint);
    }

    private JSONObject requestBody(Long id, String method, JSONObject params) {
        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        if (id != null) body.put("id", id.longValue());
        body.put("method", method);
        JSONObject safeParams = params == null ? new JSONObject() : params;
        if (modern) {
            JSONObject meta = safeParams.getNullableObject("_meta");
            if (meta == null) meta = new JSONObject();
            meta.put("io.modelcontextprotocol/protocolVersion", MODERN_VERSION);
            JSONObject clientInfo = new JSONObject();
            clientInfo.put("name", "numAi");
            clientInfo.put("version", BuildConfig.VERSION_NAME);
            meta.put("io.modelcontextprotocol/clientInfo", clientInfo);
            meta.put("io.modelcontextprotocol/clientCapabilities", new JSONObject());
            safeParams.put("_meta", meta);
        }
        body.put("params", safeParams);
        return body;
    }

    private JSONObject parseResponse(String body) throws McpException {
        if (body == null || body.trim().length() == 0) {
            throw new McpException("MCP server returned an empty response.");
        }
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("{")) return JSON.getObject(trimmed);
            BufferedReader reader = new BufferedReader(new StringReader(body));
            String line;
            JSONObject last = null;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.startsWith("{")) last = JSON.getObject(data);
            }
            if (last != null) return last;
        } catch (Exception e) {
            throw new McpException("Invalid MCP response.");
        }
        throw new McpException("Unsupported MCP response format.");
    }

    private JSONObject resultOf(JSONObject response) throws McpException {
        JSONObject error = response == null ? null : response.getNullableObject("error");
        if (error != null) {
            String message = error.getNullableString("message");
            int code = error.getInt("code", 0);
            boolean fallback = code == -32600 || code == -32601 || code == -32602 ||
                    code == -32022;
            throw new McpException(message == null ? "MCP JSON-RPC error." : message,
                    0, code, fallback);
        }
        JSONObject result = response == null ? null : response.getNullableObject("result");
        if (result == null) throw new McpException("MCP response did not contain a result.");
        return result;
    }

    private static boolean supportsModernVersion(JSONObject discover) {
        JSONArray versions = discover == null ? null :
                discover.getNullableArray("supportedVersions");
        if (versions == null) return false;
        for (int i = 0; i < versions.size(); i++) {
            if (MODERN_VERSION.equals(versions.getString(i, ""))) return true;
        }
        return false;
    }

    private Map<String, String> mirroredHeaders(McpTool tool, JSONObject arguments) {
        Map<String, String> result = new HashMap<String, String>();
        if (arguments == null) return result;
        JSONObject properties = tool.getInputSchema().getNullableObject("properties");
        if (properties == null) return result;
        Enumeration keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = String.valueOf(keys.nextElement());
            JSONObject property = properties.getNullableObject(key);
            if (property == null || !property.has("x-mcp-header") || !arguments.has(key)) continue;
            Object annotation = property.getNullable("x-mcp-header");
            String headerName = annotation instanceof String ? (String) annotation : key;
            if (!isValidHeaderSuffix(headerName)) continue;
            Object value = arguments.getNullable(key);
            if (value == null || value instanceof JSONObject || value instanceof JSONArray) continue;
            result.put("Mcp-Param-" + headerName, headerValue(String.valueOf(value)));
        }
        return result;
    }

    static boolean isValidToolName(String name) {
        if (name == null || name.length() == 0 || name.length() > 128) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidHeaderSuffix(String name) {
        if (name == null || name.length() == 0 || name.length() > 64) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '_' || c == '-')) return false;
        }
        return true;
    }

    static String headerValue(String value) {
        if (value == null) return "";
        boolean safe = value.length() == value.trim().length();
        for (int i = 0; i < value.length() && safe; i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e) safe = false;
        }
        if (safe) return value;
        try {
            return "=?base64?" + Base64.encode(value.getBytes("UTF-8")) + "?=";
        } catch (Exception e) {
            return value;
        }
    }

    static String normalizeResult(JSONObject result) {
        StringBuilder output = new StringBuilder();
        if ("input_required".equals(result.getNullableString("resultType"))) {
            return "MCP tool requires interactive input, which this numAi version does not support.";
        }
        if (result.getNullableObject("task") != null) {
            return "MCP tool returned an asynchronous task, which this numAi version does not support.";
        }
        if (result.getBoolean("isError", false)) output.append("MCP tool error:\n");
        JSONArray content = result.getNullableArray("content");
        if (content != null) {
            for (int i = 0; i < content.size(); i++) {
                JSONObject block = content.getObject(i);
                if (block == null) continue;
                String type = block.getNullableString("type");
                if ("text".equals(type)) {
                    appendBlock(output, block.getNullableString("text"));
                } else if ("resource".equals(type)) {
                    JSONObject resource = block.getNullableObject("resource");
                    if (resource != null) {
                        appendBlock(output, resource.getNullableString("text"));
                        appendBlock(output, resource.getNullableString("uri"));
                    }
                } else if ("resource_link".equals(type)) {
                    appendBlock(output, block.getNullableString("name"));
                    appendBlock(output, block.getNullableString("uri"));
                } else if ("image".equals(type) || "audio".equals(type)) {
                    appendBlock(output, "[MCP " + type + " content omitted; mimeType=" +
                            block.getString("mimeType", "unknown") + "]");
                } else {
                    appendBlock(output, "[Unsupported MCP content block: " + type + "]");
                }
                if (output.length() >= MAX_RESULT_LENGTH) break;
            }
        }
        Object structured = result.getNullable("structuredContent");
        if (structured != null) appendBlock(output, String.valueOf(structured));
        if (output.length() == 0) output.append("MCP tool completed without textual output.");
        if (output.length() > MAX_RESULT_LENGTH) {
            output.setLength(MAX_RESULT_LENGTH);
            output.append("\n\n...[MCP result truncated]");
        }
        return output.toString();
    }

    private static void appendBlock(StringBuilder output, String value) {
        if (value == null || value.length() == 0 || output.length() >= MAX_RESULT_LENGTH) return;
        if (output.length() > 0) output.append('\n');
        int remaining = MAX_RESULT_LENGTH - output.length();
        output.append(value.length() > remaining ? value.substring(0, remaining) : value);
    }

    private static String httpError(int status, String body) {
        String message = "MCP HTTP " + status;
        try {
            JSONObject json = JSON.getObject(body);
            String detail = json.getNullableString("message");
            if (detail == null) {
                JSONObject error = json.getNullableObject("error");
                if (error != null) detail = error.getNullableString("message");
            }
            if (detail != null && detail.length() > 0) message += ": " + detail;
        } catch (Exception ignored) {}
        return message;
    }

    private static McpException responseError(int status, String body) {
        try {
            JSONObject response = JSON.getObject(body);
            JSONObject error = response.getNullableObject("error");
            if (error != null) {
                int code = error.getInt("code", 0);
                String message = error.getNullableString("message");
                boolean fallback = status == 400 || status == 404 || status == 405 ||
                        code == -32600 || code == -32601 || code == -32602 ||
                        code == -32022;
                return new McpException(message == null ? httpError(status, body) : message,
                        status, code, fallback);
            }
        } catch (Exception ignored) {}
        boolean fallback = status == 400 || status == 404 || status == 405;
        return new McpException(httpError(status, body), status, 0, fallback);
    }

    private static String safeMessage(Exception e) {
        String message = e == null ? null : e.getMessage();
        return message == null || message.length() == 0 ? "MCP network request failed." : message;
    }

    private static class WireResponse {
        final String body;
        final String sessionId;
        WireResponse(String body, String sessionId) {
            this.body = body == null ? "" : body;
            this.sessionId = sessionId;
        }
    }
}
