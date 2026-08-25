package io.github.gohoski.numai.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Process-local registry used to cancel one chat without touching others. */
public class GenerationRegistry {
    private final Map<String, Generation> generations = new HashMap<String, Generation>();

    public synchronized Generation start(String chatId, String messageId) {
        return start(chatId, messageId, UUID.randomUUID().toString());
    }

    public synchronized Generation start(String chatId, String messageId,
            String generationId) {
        Generation generation = new Generation(chatId, messageId,
                generationId == null ? UUID.randomUUID().toString() : generationId);
        generations.put(key(chatId, generation.getGenerationId()), generation);
        return generation;
    }

    public synchronized boolean isActive(String chatId, String generationId) {
        Generation generation = generations.get(key(chatId, generationId));
        return generation != null && !generation.isCancelled();
    }

    public synchronized boolean cancel(String chatId, String generationId) {
        Generation generation = generations.get(key(chatId, generationId));
        if (generation == null) return false;
        generation.cancel();
        return true;
    }

    public synchronized void finish(String chatId, String generationId) {
        generations.remove(key(chatId, generationId));
    }

    public synchronized int activeCount() {
        int count = 0;
        for (Generation generation : generations.values()) {
            if (!generation.isCancelled()) count++;
        }
        return count;
    }

    private String key(String chatId, String generationId) {
        return String.valueOf(chatId) + ":" + String.valueOf(generationId);
    }

    public static class Generation {
        private final String chatId;
        private final String messageId;
        private final String generationId;
        private volatile boolean cancelled;
        private volatile InputStream stream;

        private Generation(String chatId, String messageId, String generationId) {
            this.chatId = chatId;
            this.messageId = messageId;
            this.generationId = generationId;
        }

        public String getChatId() { return chatId; }
        public String getMessageId() { return messageId; }
        public String getGenerationId() { return generationId; }
        public boolean isCancelled() { return cancelled; }

        public void setStream(InputStream value) {
            stream = value;
            if (cancelled) closeStream();
        }

        public void cancel() {
            cancelled = true;
            closeStream();
        }

        private void closeStream() {
            InputStream value = stream;
            if (value != null) {
                try { value.close(); } catch (IOException ignored) {}
                stream = null;
            }
        }
    }
}
