package io.github.gohoski.numai.mcp;

import cc.nnproject.json.JSONObject;

public class McpTool {
    private final String name;
    private final String mappedName;
    private final String description;
    private final JSONObject inputSchema;

    public McpTool(String name, String mappedName, String description,
            JSONObject inputSchema) {
        this.name = name == null ? "" : name;
        this.mappedName = mappedName == null ? "" : mappedName;
        this.description = description == null ? "" : description;
        this.inputSchema = inputSchema == null ? emptySchema() : inputSchema;
    }

    public String getName() { return name; }
    public String getMappedName() { return mappedName; }
    public String getDescription() { return description; }
    public JSONObject getInputSchema() { return inputSchema; }

    public JSONObject toOpenAiTool() {
        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        JSONObject function = new JSONObject();
        function.put("name", mappedName);
        String desc = description.length() == 0 ? "MCP tool " + name : description;
        function.put("description", "MCP " + name + ": " + desc);
        function.put("parameters", inputSchema);
        tool.put("function", function);
        return tool;
    }

    private static JSONObject emptySchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }
}
