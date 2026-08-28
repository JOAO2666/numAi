package io.github.gohoski.numai.util;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OpenAiDeltaTest {
    @Test public void readsOrdinaryStringContent() {
        JSONObject delta = JSON.getObject("{\"content\":\"Olá\"}");
        assertEquals("Olá", OpenAiDelta.text(delta, "content"));
    }

    @Test public void joinsArrayContentParts() {
        JSONObject delta = JSON.getObject(
                "{\"content\":[{\"type\":\"text\",\"text\":\"Bom \"},{\"text\":\"dia\"}]}");
        assertEquals("Bom dia", OpenAiDelta.text(delta, "content"));
    }

    @Test public void readsProviderReasoningArray() {
        JSONObject delta = JSON.getObject(
                "{\"reasoning_content\":[{\"thinking\":\"passo 1\"},{\"text\":\"; passo 2\"}]}");
        assertEquals("passo 1; passo 2", OpenAiDelta.text(delta, "reasoning_content"));
    }
}
