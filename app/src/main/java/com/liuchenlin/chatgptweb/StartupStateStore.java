package com.liuchenlin.chatgptweb;

import java.util.Objects;

/** Persists startup progress so a process-level Gecko crash is visible on the next launch. */
public final class StartupStateStore {
    static final String KEY_IN_PROGRESS = "startup_in_progress";
    static final String KEY_STAGE = "startup_stage";
    static final String KEY_FAILURE_TYPE = "startup_failure_type";
    static final String KEY_FAILURE_MESSAGE = "startup_failure_message";
    static final String KEY_CAUSE_TYPE = "startup_cause_type";
    static final String KEY_CAUSE_MESSAGE = "startup_cause_message";

    public enum Stage {
        NONE,
        UI_READY,
        GECKO_VIEW,
        GECKO_RUNTIME,
        GECKO_SESSION
    }

    public interface Backend {
        String getString(String key);
        void putString(String key, String value);
        boolean getBoolean(String key, boolean fallback);
        void putBoolean(String key, boolean value);
        void remove(String key);
    }

    private final Backend backend;

    public StartupStateStore(Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public void begin(Stage stage) {
        clearFailure();
        backend.putBoolean(KEY_IN_PROGRESS, true);
        advance(stage);
    }

    public void advance(Stage stage) {
        Stage safe = stage == null ? Stage.NONE : stage;
        backend.putString(KEY_STAGE, safe.name());
    }

    public boolean hasInterruptedStartup() {
        return backend.getBoolean(KEY_IN_PROGRESS, false);
    }

    public Stage lastStage() {
        String raw = backend.getString(KEY_STAGE);
        if (raw == null || raw.isEmpty()) {
            return Stage.NONE;
        }
        try {
            return Stage.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return Stage.NONE;
        }
    }

    public void recordFailure(StartupFailureInfo info) {
        if (info == null) {
            clearFailure();
            return;
        }
        backend.putString(KEY_FAILURE_TYPE, info.exceptionType);
        backend.putString(KEY_FAILURE_MESSAGE, info.exceptionMessage);
        backend.putString(KEY_CAUSE_TYPE, info.causeType);
        backend.putString(KEY_CAUSE_MESSAGE, info.causeMessage);
    }

    public StartupFailureInfo lastFailure() {
        String type = backend.getString(KEY_FAILURE_TYPE);
        if (type == null || type.isEmpty()) {
            return null;
        }
        return new StartupFailureInfo(
                lastStage(),
                type,
                backend.getString(KEY_FAILURE_MESSAGE),
                backend.getString(KEY_CAUSE_TYPE),
                backend.getString(KEY_CAUSE_MESSAGE));
    }

    public void complete() {
        backend.remove(KEY_IN_PROGRESS);
        backend.remove(KEY_STAGE);
        clearFailure();
    }

    private void clearFailure() {
        backend.remove(KEY_FAILURE_TYPE);
        backend.remove(KEY_FAILURE_MESSAGE);
        backend.remove(KEY_CAUSE_TYPE);
        backend.remove(KEY_CAUSE_MESSAGE);
    }
}
