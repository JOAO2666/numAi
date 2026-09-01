package io.github.gohoski.numai.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cc.nnproject.json.JSONArray;

public class McpCatalog {
    private final List<McpTool> tools;
    private final Map<String, McpTool> byMappedName;

    public McpCatalog(List<McpTool> tools) {
        this.tools = tools == null ? new ArrayList<McpTool>() : tools;
        byMappedName = new HashMap<String, McpTool>();
        for (int i = 0; i < this.tools.size(); i++) {
            McpTool tool = this.tools.get(i);
            byMappedName.put(tool.getMappedName(), tool);
        }
    }

    public int size() { return tools.size(); }
    public McpTool getByMappedName(String name) { return byMappedName.get(name); }
    public boolean containsMappedName(String name) { return byMappedName.containsKey(name); }
    public List<McpTool> getTools() { return new ArrayList<McpTool>(tools); }

    public JSONArray toOpenAiTools() {
        JSONArray result = new JSONArray();
        for (int i = 0; i < tools.size(); i++) result.add(tools.get(i).toOpenAiTool());
        return result;
    }

    public static String mappedName(String original, Map<String, Boolean> used) {
        String source = original == null ? "tool" : original;
        StringBuilder safe = new StringBuilder("mcp_");
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '_' || c == '-') {
                safe.append(c);
            } else {
                safe.append('_');
            }
        }
        String hash = Integer.toHexString(source.hashCode());
        String candidate = safe.toString();
        if (candidate.length() > 64) candidate = candidate.substring(0, 55) + "_" + hash;
        if (used != null && used.containsKey(candidate)) {
            String base = candidate.substring(0, Math.min(candidate.length(), 55));
            candidate = base + "_" + hash;
            int suffix = 2;
            while (used.containsKey(candidate)) {
                String number = "_" + suffix;
                int max = Math.min(base.length(), 64 - hash.length() - number.length() - 1);
                candidate = base.substring(0, max) + "_" + hash + number;
                suffix++;
            }
        }
        if (used != null) used.put(candidate, Boolean.TRUE);
        return candidate;
    }
}
