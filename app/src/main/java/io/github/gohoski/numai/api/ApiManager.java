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
        addApi("NVIDIA NIM", "nvidia_nim",
                "https://integrate.api.nvidia.com/v1");
        addApi("TokenRouter", "tokenrouter", "https://api.tokenrouter.com/v1");
        addApi("Groq", "groq", "https://api.groq.com/openai/v1");
        addApi("Together AI", "together_ai", "https://api.together.ai/v1");
        addApi("Fireworks AI", "fireworks_ai",
                "https://api.fireworks.ai/inference/v1");
        addApi("DeepInfra", "deepinfra", "https://api.deepinfra.com/v1/openai");
        addApi("Hugging Face", "hugging_face", "https://router.huggingface.co/v1");
        addApi("Google AI Studio", "google_ai_studio",
                "https://generativelanguage.googleapis.com/v1beta/openai");
        addApi("Z.ai", "z_ai", "https://api.z.ai/api/paas/v4");
        addApi("BigModel (Z.ai China)", "bigmodel_zh",
                "https://open.bigmodel.cn/api/paas/v4");
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
        return URL_TO_NAME.get(normalizeUrl(url));
    }

    public static String getIdByName(String name) {
        String id = NAME_TO_ID.get(name);
        return id == null ? stableCustomId(getUrlByName(name)) : id;
    }

    public static String getIdByUrl(String url) {
        String normalized = normalizeUrl(url);
        String id = URL_TO_ID.get(normalized);
        return id == null ? stableCustomId(normalized) : id;
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

    public static String normalizeUrl(String url) {
        if ("http://api.z.ai/api/paas/v4".equals(url)) {
            return "https://api.z.ai/api/paas/v4";
        }
        if ("http://open.bigmodel.cn/api/paas/v4".equals(url)) {
            return "https://open.bigmodel.cn/api/paas/v4";
        }
        return url;
    }

    public static List<String> getAllApiNames() {
        return new ArrayList<String>(NAME_TO_URL.keySet());
    }
}
