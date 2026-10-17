package com.liuchenlin.chatgptweb;

import android.content.Context;
import org.mozilla.geckoview.GeckoRuntime;

/** Owns the single GeckoRuntime for the lifetime of the Android app process. */
public final class GeckoRuntimeHolder {
    public enum State {
        NOT_CREATED,
        CREATING,
        READY,
        FAILED
    }

    private static volatile GeckoRuntime runtime;
    private static volatile State state = State.NOT_CREATED;

    private GeckoRuntimeHolder() {}

    public static State state() {
        return state;
    }

    public static GeckoRuntime get(Context context) {
        GeckoRuntime current = runtime;
        if (current != null && state == State.READY) {
            return current;
        }
        if (state == State.FAILED) {
            throw new IllegalStateException("GeckoRuntime initialization already failed in this process");
        }
        synchronized (GeckoRuntimeHolder.class) {
            current = runtime;
            if (current != null && state == State.READY) {
                return current;
            }
            if (state == State.FAILED) {
                throw new IllegalStateException("GeckoRuntime initialization already failed in this process");
            }
            if (state == State.CREATING) {
                throw new IllegalStateException("GeckoRuntime initialization is already in progress");
            }
            state = State.CREATING;
            try {
                Context applicationContext = context.getApplicationContext();
                current = GeckoRuntime.create(applicationContext);
                runtime = current;
                state = State.READY;
                return current;
            } catch (RuntimeException | LinkageError error) {
                runtime = null;
                state = State.FAILED;
                throw error;
            }
        }
    }
}
