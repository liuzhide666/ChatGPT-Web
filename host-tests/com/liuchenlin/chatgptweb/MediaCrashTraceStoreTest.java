package com.liuchenlin.chatgptweb;

import java.util.HashMap;
import java.util.Map;

public final class MediaCrashTraceStoreTest {
    private static final class Backend implements MediaCrashTraceStore.Backend {
        final Map<String,String> values = new HashMap<>();
        @Override public String getString(String key) { return values.get(key); }
        @Override public void putString(String key, String value) { values.put(key, value); }
        @Override public void remove(String key) { values.remove(key); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Backend backend = new Backend();
        MediaCrashTraceStore store = new MediaCrashTraceStore(backend, 3);
        store.append("ANDROID_REQUEST permission=RECORD_AUDIO");
        store.append("ANDROID_RESULT permission=RECORD_AUDIO granted=true");
        store.append("MEDIA_REQUEST origin=https://chatgpt.com");
        store.append("AUDIO_SELECTED source=MICROPHONE idHash=abc123");

        String summary = store.summary();
        check(!summary.contains("ANDROID_REQUEST"), "ring buffer retains only newest entries");
        check(summary.contains("ANDROID_RESULT permission=RECORD_AUDIO granted=true"), "permission result retained");
        check(summary.contains("MEDIA_REQUEST origin=https://chatgpt.com"), "media request retained");
        check(summary.contains("AUDIO_SELECTED source=MICROPHONE idHash=abc123"), "selection retained");

        store.beginAttempt();
        check(store.hasInterruptedAttempt(), "active media attempt is marked durably");
        MediaCrashTraceStore afterRestart = new MediaCrashTraceStore(backend, 3);
        check(afterRestart.summary().equals(summary), "trace persists across process recreation");
        check(afterRestart.hasInterruptedAttempt(), "interrupted media attempt survives process recreation");
        afterRestart.acknowledgeInterruptedAttempt();
        check(!afterRestart.hasInterruptedAttempt(), "acknowledgement clears only the active-attempt sentinel");
        check(afterRestart.summary().equals(summary), "acknowledgement keeps trace for diagnosis");
        afterRestart.beginAttempt();
        afterRestart.completeAttempt();
        check(!afterRestart.hasInterruptedAttempt(), "normal completion clears active-attempt sentinel");
        afterRestart.clear();
        check(afterRestart.summary().isEmpty(), "clear removes persisted trace");
        System.out.println("MediaCrashTraceStoreTest OK");
    }
}
