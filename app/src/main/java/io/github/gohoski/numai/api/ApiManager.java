package io.github.gohoski.numai.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiManager {
    private static final Map<String, String>
            NAME_TO_URL = new LinkedHashMap<String, String>(),
            URL_TO_NAME = new LinkedHashMap<String, String>(),
            NAME_TO_ID = new LinkedHashMap<String, String>(),
            URL_TO_ID = new LinkedHashMap<String, String>();

    static {
        addApi("VoidAI", "voidai", "https://api.voidai.app/v1");
        addApi("Ollama Cloud", "ollama_cloud", "https://ollama.com/v1");
        addApi("OpenCode Zen", "opencode_zen", "https://opencode.ai/zen/v1");
        addApi("NavyAI", "navyai", "https://api.navy/v1");
        addApi("OpenRouter", "openrouter", "https://openrouter.ai/api/v1");
        addApi("Google AI Studio", "google_ai_studio",
                "https://generativelanguage.googleapis.com/v1beta/openai");
        addApi("Z.ai", "z_ai", "http://api.z.ai/api/paas/v4");
        addApi("BigModel (Z.ai China)", "bigmodel_zh",
                "http://open.bigmodel.cn/api/paas/v4");
        addApi("Kilo Gateway", "kilo_gateway", "https://api.kilo.ai/api/gateway");
    }

    private static void addApi(String name, String id, String url) {
        NAME_TO_URL.put(name, url);
        URL_TO_NAME.put(url, name);
        NAME_TO_ID.put(name, id);
        URL_TO_ID.put(url, id);
    }

    public static String getUrlByName(String name) {
        String s = NAME_TO_URL.get(name);
        return s == null ? name : s;
    }

    public static String getNameByUrl(String url) {
        return URL_TO_NAME.get(url);
    }

    public static String getIdByName(String name) {
        String id = NAME_TO_ID.get(name);
        return id == null ? stableCustomId(getUrlByName(name)) : id;
    }

    public static String getIdByUrl(String url) {
        String id = URL_TO_ID.get(url);
        return id == null ? stableCustomId(url) : id;
    }

    public static String getProviderId(String nameOrUrl) {
        if (nameOrUrl == null) return "custom_empty";
        String id = NAME_TO_ID.get(nameOrUrl);
        return id == null ? getIdByUrl(nameOrUrl) : id;
    }

    private static String stableCustomId(String url) {
        if (url == null || url.length() == 0) return "custom_empty";
        return "custom_" + Integer.toHexString(url.trim().toLowerCase().hashCode());
    }

    public static List<String> getAllApiNames() {
        return new ArrayList<String>(NAME_TO_URL.keySet());
    }
}
