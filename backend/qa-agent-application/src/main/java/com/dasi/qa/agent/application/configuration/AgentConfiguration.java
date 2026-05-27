package com.dasi.qa.agent.application.configuration;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class AgentConfiguration {

    public static final int MAX_MESSAGE = 20;
    public static final int DASI_TEMP_CHAT_MAX_MESSAGE = 12;
    private static final int DASI_TEMP_CHAT_MAX_SESSION = 1000;
    private static final Duration DASI_TEMP_CHAT_TTL = Duration.ofMinutes(30);
    private static final Duration DASI_TEMP_CHAT_CLEAN_INTERVAL = Duration.ofMinutes(1);

    @Bean
    public ChatMemoryProvider qaGenerationChatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGE);
    }

    @Bean
    @Qualifier("dasiTempChatMemoryProvider")
    public ChatMemoryProvider dasiTempChatMemoryProvider() {
        return new ExpiringMessageWindowChatMemoryProvider(
                DASI_TEMP_CHAT_MAX_MESSAGE,
                DASI_TEMP_CHAT_TTL,
                DASI_TEMP_CHAT_MAX_SESSION
        );
    }

    private static class ExpiringMessageWindowChatMemoryProvider implements ChatMemoryProvider {

        private final int maxMessages;
        private final long ttlMillis;
        private final int maxSessions;
        private final ConcurrentHashMap<Object, MemoryEntry> memories = new ConcurrentHashMap<>();
        private final AtomicLong lastCleanupAt = new AtomicLong(0);

        private ExpiringMessageWindowChatMemoryProvider(int maxMessages, Duration ttl, int maxSessions) {
            this.maxMessages = maxMessages;
            this.ttlMillis = ttl.toMillis();
            this.maxSessions = maxSessions;
        }

        @Override
        public ChatMemory get(Object memoryId) {
            long now = System.currentTimeMillis();
            cleanupIfNeeded(now);
            MemoryEntry entry = memories.compute(memoryId, (id, existing) -> {
                if (existing == null || isExpired(existing, now)) {
                    return new MemoryEntry(MessageWindowChatMemory.builder()
                            .id(id)
                            .maxMessages(maxMessages)
                            .build(), now);
                }
                existing.touch(now);
                return existing;
            });
            trimIfNeeded();
            return entry.memory();
        }

        private void cleanupIfNeeded(long now) {
            long previous = lastCleanupAt.get();
            if (now - previous < DASI_TEMP_CHAT_CLEAN_INTERVAL.toMillis()
                    || !lastCleanupAt.compareAndSet(previous, now)) {
                return;
            }
            memories.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        }

        private void trimIfNeeded() {
            if (memories.size() <= maxSessions) {
                return;
            }
            memories.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().lastAccessAt()))
                    .map(Map.Entry::getKey)
                    .ifPresent(memories::remove);
        }

        private boolean isExpired(MemoryEntry entry, long now) {
            return now - entry.lastAccessAt() > ttlMillis;
        }

        private record MemoryEntry(ChatMemory memory, AtomicLong lastAccess) {

            private MemoryEntry(ChatMemory memory, long lastAccessAt) {
                this(memory, new AtomicLong(lastAccessAt));
            }

            private void touch(long now) {
                lastAccess.set(now);
            }

            private long lastAccessAt() {
                return lastAccess.get();
            }
        }
    }

}
