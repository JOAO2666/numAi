package io.github.gohoski.numai;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.gohoski.numai.api.ApiManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ApiManagerTest {
    @Test public void newOpenAiCompatibleProvidersHaveExpectedBaseUrls() {
        assertEquals("https://129-148-23-167.nip.io/v1",
                ApiManager.getUrlByName("numAi Oracle"));
        assertEquals("https://integrate.api.nvidia.com/v1",
                ApiManager.getUrlByName("NVIDIA NIM"));
        assertEquals("https://api.tokenrouter.com/v1",
                ApiManager.getUrlByName("TokenRouter"));
        assertEquals("https://api.groq.com/openai/v1",
                ApiManager.getUrlByName("Groq"));
        assertEquals("https://api.together.ai/v1",
                ApiManager.getUrlByName("Together AI"));
        assertEquals("https://api.fireworks.ai/inference/v1",
                ApiManager.getUrlByName("Fireworks AI"));
        assertEquals("https://api.deepinfra.com/v1/openai",
                ApiManager.getUrlByName("DeepInfra"));
        assertEquals("https://router.huggingface.co/v1",
                ApiManager.getUrlByName("Hugging Face"));
    }

    @Test public void everyKnownProviderRoundTripsWithUniqueStableId() {
        List<String> names = ApiManager.getAllApiNames();
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String url = ApiManager.getUrlByName(name);
            assertTrue("Provider must use HTTPS: " + name, url.startsWith("https://"));
            assertEquals(name, ApiManager.getNameByUrl(url));
            assertTrue(ids.add(ApiManager.getIdByName(name)));
        }
    }

    @Test public void zaiProvidersUseEncryptedOfficialEndpoints() {
        assertEquals("https://api.z.ai/api/paas/v4", ApiManager.getUrlByName("Z.ai"));
        assertEquals("https://open.bigmodel.cn/api/paas/v4",
                ApiManager.getUrlByName("BigModel (Z.ai China)"));
        assertEquals("z_ai", ApiManager.getIdByUrl("http://api.z.ai/api/paas/v4"));
        assertEquals("bigmodel_zh",
                ApiManager.getIdByUrl("http://open.bigmodel.cn/api/paas/v4"));
    }
}
