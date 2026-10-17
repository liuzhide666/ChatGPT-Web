package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Persists a small, sanitized ring of media/recovery stages for post-crash diagnosis. */
public final class MediaCrashTraceStore {
    private static final String KEY_TRACE = "media_crash_trace_v1";
    private static final String KEY_ACTIVE_ATTEMPT = "media_attempt_active_v1";
    private static final int DEFAULT_CAPACITY = 12;
    private static final int MAX_EVENT_LENGTH = 240;

    public interface Backend {
        String getString(String key);
        void putString(String key, String value);
        void remove(String key);
    }

    private final Backend backend;
    private final int capacity;

    public MediaCrashTraceStore(Backend backend) {
        this(backend, DEFAULT_CAPACITY);
    }

    MediaCrashTraceStore(Backend backend, int capacity) {
        this.backend = Objects.requireNonNull(backend, "backend");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized void append(String event) {
        String safe = sanitize(event);
        if (safe.isEmpty()) {
            return;
        }
        List<String> events = readEvents();
        events.add(safe);
        while (events.size() > capacity) {
            events.remove(0);
        }
        backend.putString(KEY_TRACE, String.join("\n", events));
    }

    /** Marks that a media request/recording is in flight; deliberately durable across process death. */
    public synchronized void beginAttempt() {
        backend.putString(KEY_ACTIVE_ATTEMPT, "1");
    }

    /** Marks a media request/recording as cleanly finished while retaining the trace for diagnosis. */
    public synchronized void completeAttempt() {
        backend.remove(KEY_ACTIVE_ATTEMPT);
    }

    public synchronized boolean hasInterruptedAttempt() {
        return "1".equals(backend.getString(KEY_ACTIVE_ATTEMPT));
    }

    /** User/app acknowledged a prior process interruption; trace remains available until explicitly cleared. */
    public synchronized void acknowledgeInterruptedAttempt() {
        backend.remove(KEY_ACTIVE_ATTEMPT);
    }

    public synchronized String summary() {
        return String.join("\n", readEvents());
    }

    public synchronized void clear() {
        backend.remove(KEY_TRACE);
        backend.remove(KEY_ACTIVE_ATTEMPT);
    }

    private List<String> readEvents() {
        String raw = backend.getString(KEY_TRACE);
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split("\\n")) {
            String safe = sanitize(line);
            if (!safe.isEmpty()) {
                out.add(safe);
            }
        }
        while (out.size() > capacity) {
            out.remove(0);
        }
        return out;
    }

    private static String sanitize(String event) {
        if (event == null) {
            return "";
        }
        String safe = event.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        if (safe.length() > MAX_EVENT_LENGTH) {
            safe = safe.substring(0, MAX_EVENT_LENGTH);
        }
        return safe;
    }
}
