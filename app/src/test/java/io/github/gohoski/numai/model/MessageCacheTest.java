package io.github.gohoski.numai.model;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageCacheTest {
    @Test public void cacheIsReusedForSameStreamingState() {
        assertFalse(Message.needsParsedCacheRefresh("cached", true, true));
        assertFalse(Message.needsParsedCacheRefresh("cached", false, false));
    }

    @Test public void cacheRefreshesWhenStreamingStateChanges() {
        assertTrue(Message.needsParsedCacheRefresh("cached", true, false));
        assertTrue(Message.needsParsedCacheRefresh("cached", false, true));
        assertTrue(Message.needsParsedCacheRefresh(null, false, false));
    }
}
