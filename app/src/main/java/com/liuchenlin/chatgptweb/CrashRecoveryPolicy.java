package com.liuchenlin.chatgptweb;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CrashRecoveryPolicy {
    public static final long WINDOW_MS = 60_000L;
    public static final int MAX_AUTOMATIC_RECOVERIES_IN_WINDOW = 2;

    public enum Decision {
        AUTO_RECOVER,
        SHOW_RECOVERY_UI
    }

    private final Deque<Long> failureTimes = new ArrayDeque<>();
    private boolean guarded;

    public Decision recordFailure(long now) {
        if (guarded) {
            return Decision.SHOW_RECOVERY_UI;
        }
        while (!failureTimes.isEmpty() && now - failureTimes.peekFirst() > WINDOW_MS) {
            failureTimes.removeFirst();
        }
        failureTimes.addLast(now);
        if (failureTimes.size() > MAX_AUTOMATIC_RECOVERIES_IN_WINDOW) {
            guarded = true;
            return Decision.SHOW_RECOVERY_UI;
        }
        return Decision.AUTO_RECOVER;
    }

    public void reset() {
        failureTimes.clear();
        guarded = false;
    }
}
