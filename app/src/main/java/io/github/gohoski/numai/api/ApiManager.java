package io.github.gohoski.numai.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiManager {
    private static final Map<String, String>
            NAME_TO_URL = new LinkedHashMap<String, String>(),
            URL_TO_NAME = new LinkedHashMap<String, String>();

    static {
        addApi("VoidAI", "https://api.voidai.app/v1");
        addApi("Ollama Cloud", "https://ollama.com/v1");
        addApi("OpenCode Zen", "https://opencode.ai/zen/v1");
        addApi("NavyAI", "https://api.navy/v1");
        addApi("OpenRouter","https://openrouter.ai/api/v1");
        addApi("Google AI Studio","https://generativelanguage.googleapis.com/v1beta/openai");
        addApi("Z.ai","http://api.z.ai/api/paas/v4");
        addApi("BigModel (Z.ai China)","http://open.bigmodel.cn/api/paas/v4");
        addApi("Kilo Gateway", "https://api.kilo.ai/api/gateway");
    }

    private static void addApi(String name, String url) {
        NAME_TO_URL.put(name, url);
        URL_TO_NAME.put(url, name);
    }

    public static String getUrlByName(String name) {
        String s = NAME_TO_URL.get(name);
        return s == null ? name : s;
    }

    public static String getNameByUrl(String url) {
        return URL_TO_NAME.get(url);
    }

    public static List<String> getAllApiNames() {
        return new ArrayList<String>(NAME_TO_URL.keySet());
    }
}
