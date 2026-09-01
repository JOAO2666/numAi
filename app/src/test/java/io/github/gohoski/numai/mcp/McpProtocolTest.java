package io.github.gohoski.numai.mcp;

import java.util.HashMap;
import java.util.Map;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class McpProtocolTest {
    @Test public void keepsToolEnableAndAutomaticExecutionIndependent() {
        assertEquals("", McpConfigManager.DEFAULT_ENDPOINT);
        McpSettings manual = new McpSettings(true, false, "https://example.com/mcp");
        McpSettings automatic = new McpSettings(true, true, "https://example.com/mcp");
        assertTrue(manual.isEnabled());
        assertFalse(manual.isAutoExecute());
        assertTrue(automatic.isAutoExecute());
        assertEquals("https://example.com/mcp", automatic.getEndpoint());
    }

    @Test public void mapsToolNamesToOpenAiSafeStableNames() {
        Map<String, Boolean> used = new HashMap<String, Boolean>();
        String first = McpCatalog.mappedName("oracle.sql.query", used);
        String collision = McpCatalog.mappedName("oracle/sql/query", used);
        String repeated = McpCatalog.mappedName("oracle/sql/query", used);
        assertEquals("mcp_oracle_sql_query", first);
        assertTrue(collision.startsWith("mcp_oracle_sql_query_"));
        assertFalse(collision.equals(repeated));
        assertTrue(first.length() <= 64);
        assertTrue(collision.length() <= 64);
        assertTrue(repeated.length() <= 64);
    }

    @Test public void pkceEncodingIsUrlSafeAndUnpadded() throws Exception {
        String encoded = McpAuthManager.base64Url("test value".getBytes("UTF-8"));
        assertFalse(encoded.contains("+"));
        assertFalse(encoded.contains("/"));
        assertFalse(encoded.contains("="));
    }

    @Test public void normalizesTextAndStructuredToolResults() {
        JSONObject result = JSON.getObject("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]," +
                "\"structuredContent\":{\"count\":2}}");
        String normalized = McpClient.normalizeResult(result);
        assertTrue(normalized.contains("ok"));
        assertTrue(normalized.contains("count"));
    }

    @Test public void marksBinaryBlocksWithoutForwardingPayload() {
        JSONObject result = JSON.getObject("{\"content\":[{\"type\":\"image\"," +
                "\"mimeType\":\"image/png\",\"data\":\"secret-base64\"}]}");
        String normalized = McpClient.normalizeResult(result);
        assertTrue(normalized.contains("image content omitted"));
        assertFalse(normalized.contains("secret-base64"));
    }

    @Test public void unsafeHeaderValuesUseBase64Sentinel() {
        assertEquals("plain", McpClient.headerValue("plain"));
        assertTrue(McpClient.headerValue(" Olá ").startsWith("=?base64?"));
    }

    @Test public void rejectsUnsafeToolNamesBeforeUsingThemAsHeaders() {
        assertTrue(McpClient.isValidToolName("oracle.sql-query_2"));
        assertFalse(McpClient.isValidToolName("bad tool"));
        assertFalse(McpClient.isValidToolName("bad\r\nHeader"));
    }
}
