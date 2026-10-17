package com.liuchenlin.chatgptweb;

import java.util.HashMap;
import java.util.Map;

public final class StartupStateStoreTest {
    private static final class MemoryBackend implements StartupStateStore.Backend {
        final Map<String, String> strings = new HashMap<>();
        final Map<String, Boolean> booleans = new HashMap<>();
        @Override public String getString(String key) { return strings.get(key); }
        @Override public void putString(String key, String value) { strings.put(key, value); }
        @Override public boolean getBoolean(String key, boolean fallback) {
            return booleans.getOrDefault(key, fallback);
        }
        @Override public void putBoolean(String key, boolean value) { booleans.put(key, value); }
        @Override public void remove(String key) { strings.remove(key); booleans.remove(key); }
    }

    public static void main(String[] args) {
        MemoryBackend backend = new MemoryBackend();
        StartupStateStore store = new StartupStateStore(backend);
        check(!store.hasInterruptedStartup(), "fresh launch must not be interrupted");

        store.begin(StartupStateStore.Stage.UI_READY);
        check(store.hasInterruptedStartup(), "begin must persist in-progress marker");
        check(store.lastStage() == StartupStateStore.Stage.UI_READY, "begin stage");

        store.advance(StartupStateStore.Stage.GECKO_RUNTIME);
        check(store.lastStage() == StartupStateStore.Stage.GECKO_RUNTIME, "advanced stage");

        StartupFailureInfo failure = new StartupFailureInfo(
                StartupStateStore.Stage.GECKO_RUNTIME,
                "RuntimeException", "failed", "IllegalStateException", "cause");
        store.recordFailure(failure);

        StartupStateStore secondProcess = new StartupStateStore(backend);
        check(secondProcess.hasInterruptedStartup(), "marker must survive process restart");
        check(secondProcess.lastStage() == StartupStateStore.Stage.GECKO_RUNTIME,
                "last stage must survive process restart");
        StartupFailureInfo restored = secondProcess.lastFailure();
        check(restored != null, "failure must survive process restart");
        check("RuntimeException".equals(restored.exceptionType), "restored type");
        check("cause".equals(restored.causeMessage), "restored cause message");

        secondProcess.complete();
        check(!secondProcess.hasInterruptedStartup(), "successful startup clears marker");
        check(secondProcess.lastStage() == StartupStateStore.Stage.NONE, "stage cleared");
        check(secondProcess.lastFailure() == null, "failure cleared");

        System.out.println("StartupStateStoreTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
