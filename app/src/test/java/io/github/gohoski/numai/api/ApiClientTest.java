package io.github.gohoski.numai.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApiClientTest {
    @Test public void joinsProviderPathWithoutDoubleSlash() {
        assertEquals("https://example.test/v1/models",
                ApiClient.joinUrl("https://example.test/v1/", "/models"));
    }

    @Test public void trimsProviderUrlAndPreservesEmptyEndpoint() {
        assertEquals("https://example.test/v1",
                ApiClient.joinUrl("  https://example.test/v1  ", ""));
    }
}
