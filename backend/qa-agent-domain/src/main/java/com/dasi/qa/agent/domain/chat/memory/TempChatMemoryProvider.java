package com.dasi.qa.agent.domain.chat.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TempChatMemoryProvider implements ChatMemoryProvider {

    private static final int MAX_MESSAGE = 12;
    private static final int MAX_SESSION = 1000;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration CLEAN_INTERVAL = Duration.ofMinutes(1);

    private final ConcurrentHashMap<Object, MemoryEntry> memories = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupAt = new AtomicLong(0);

    @Override
    public ChatMemory get(Object memoryId) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        MemoryEntry entry = memories.compute(memoryId, (id, existing) -> {
            if (existing == null || isExpired(existing, now)) {
                return new MemoryEntry(MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(MAX_MESSAGE)
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
        if (now - previous < CLEAN_INTERVAL.toMillis()
                || !lastCleanupAt.compareAndSet(previous, now)) {
            return;
        }
        memories.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private void trimIfNeeded() {
        if (memories.size() <= MAX_SESSION) {
            return;
        }
        memories.entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().lastAccessAt()))
                .map(Map.Entry::getKey)
                .ifPresent(memories::remove);
    }

    private boolean isExpired(MemoryEntry entry, long now) {
        return now - entry.lastAccessAt() > TTL.toMillis();
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