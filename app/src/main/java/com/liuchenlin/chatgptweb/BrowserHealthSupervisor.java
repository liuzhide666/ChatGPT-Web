package com.liuchenlin.chatgptweb;

/**
 * Event-driven browser recovery policy.
 *
 * STABLE and DEGRADED_STABLE intentionally have no time-based watchdog. Timers exist only while
 * a concrete probe/rebind/recovery operation is already in progress.
 */
public final class BrowserHealthSupervisor {
    public static final long PROBE_GRACE_MS = 5_000L;
    public static final long REBIND_GRACE_MS = 8_000L;
    public static final long SOFT_GRACE_MS = 30_000L;
    public static final long HARD_GRACE_MS = 45_000L;

    public enum State {
        STABLE,
        SUSPECT,
        PROBING,
        BRIDGE_RECOVERY,
        DEGRADED_STABLE,
        SOFT_RECOVERY,
        HARD_RECOVERY,
        USER_RECOVERY
    }

    public enum Decision { NONE, PROBE, REBIND_BRIDGE, SOFT_RELOAD, HARD_REPLACE, SHOW_UI }

    public enum Reason {
        NONE,
        BRIDGE_DISCONNECTED,
        BRIDGE_VERSION_MISMATCH,
        PAINT_INVALID,
        TOP_LEVEL_LOAD_FAILED,
        SLOW_SCRIPT,
        PROBE_TIMEOUT,
        REBIND_TIMEOUT,
        SOFT_RECOVERY_TIMEOUT,
        HARD_RECOVERY_TIMEOUT
    }

    public static final class Snapshot {
        public final long nowMs;
        public final boolean foreground;
        public final boolean loading;
        public final boolean currentPageTrusted;
        public final boolean composerFocused;
        public final boolean composerDirty;
        public final boolean composerComposing;
        public final boolean attachmentActive;
        public final boolean mediaActive;
        public final boolean externalFlowActive;
        public final boolean networkAvailable;
        public final boolean bridgeConnected;
        public final boolean protocolCompatible;
        public final boolean paintValid;

        public Snapshot(long nowMs, boolean foreground, boolean loading, boolean currentPageTrusted,
                boolean composerFocused, boolean composerDirty, boolean composerComposing,
                boolean attachmentActive, boolean mediaActive, boolean externalFlowActive, boolean networkAvailable,
                boolean bridgeConnected, boolean protocolCompatible, boolean paintValid) {
            this.nowMs = nowMs;
            this.foreground = foreground;
            this.loading = loading;
            this.currentPageTrusted = currentPageTrusted;
            this.composerFocused = composerFocused;
            this.composerDirty = composerDirty;
            this.composerComposing = composerComposing;
            this.attachmentActive = attachmentActive;
            this.mediaActive = mediaActive;
            this.externalFlowActive = externalFlowActive;
            this.networkAvailable = networkAvailable;
            this.bridgeConnected = bridgeConnected;
            this.protocolCompatible = protocolCompatible;
            this.paintValid = paintValid;
        }
    }

    private State state = State.STABLE;
    private Reason lastReason = Reason.NONE;
    private long operationDeadlineMs = Long.MAX_VALUE;
    private boolean bridgeDegraded;
    private boolean paintInvalid;
    private boolean strongFailure;

    public State state() { return state; }
    public Reason lastReason() { return lastReason; }

    public void resetIncident() {
        state = State.STABLE;
        lastReason = Reason.NONE;
        operationDeadlineMs = Long.MAX_VALUE;
        bridgeDegraded = false;
        paintInvalid = false;
        strongFailure = false;
    }

    public void onBridgeDisconnected(long nowMs) {
        bridgeDegraded = true;
        lastReason = Reason.BRIDGE_DISCONNECTED;
        enterSuspectIfPassive();
    }

    public void onBridgeVersionMismatch(long nowMs) {
        bridgeDegraded = true;
        lastReason = Reason.BRIDGE_VERSION_MISMATCH;
        enterSuspectIfPassive();
    }

    /** READY, PONG, or another compatible trusted bridge event proves bridge liveness. */
    public void onBridgeHealthy(long nowMs) {
        bridgeDegraded = false;
        if (!paintInvalid && !strongFailure) settlePassiveHealthy();
    }

    public void onNavigationStarted(long nowMs) {
        paintInvalid = true;
        lastReason = Reason.PAINT_INVALID;
        // Loading itself is not a failure. Do not arm a timer merely because a navigation began.
    }

    public void onPaintInvalid(long nowMs) {
        paintInvalid = true;
        lastReason = Reason.PAINT_INVALID;
        enterSuspectIfPassive();
    }

    /** Current-generation progress/session-state proves content execution but not valid paint. */
    public void onContentProgress(long nowMs) {
        strongFailure = false;
        if (!paintInvalid && !bridgeDegraded) settlePassiveHealthy();
    }

    /** Current-generation FCP proves usable content and ends page-health recovery. */
    public void onContentHealthy(long nowMs) {
        paintInvalid = false;
        strongFailure = false;
        settlePassiveHealthy();
    }

    public void onTopLevelLoadFailed(long nowMs) {
        strongFailure = true;
        lastReason = Reason.TOP_LEVEL_LOAD_FAILED;
        enterSuspectIfPassive();
    }

    /** Slow script is evidence of delayed-but-alive content and never starts recovery alone. */
    public void onSlowScript(long nowMs) {
        lastReason = Reason.SLOW_SCRIPT;
    }

    public Decision evaluate(Snapshot s) {
        if (s == null) return Decision.NONE;
        switch (state) {
            case STABLE:
            case DEGRADED_STABLE:
            case USER_RECOVERY:
                return Decision.NONE;
            case SUSPECT:
                if (baseBlocked(s)) return Decision.NONE;
                if (strongFailure) {
                    if (destructiveBlocked(s)) return Decision.NONE;
                    return beginSoftRecovery(s.nowMs);
                }
                if (bridgeDegraded) {
                    if (s.bridgeConnected && s.protocolCompatible) {
                        state = State.PROBING;
                        operationDeadlineMs = safeAdd(s.nowMs, PROBE_GRACE_MS);
                        return Decision.PROBE;
                    }
                    state = State.BRIDGE_RECOVERY;
                    operationDeadlineMs = safeAdd(s.nowMs, REBIND_GRACE_MS);
                    return Decision.REBIND_BRIDGE;
                }
                // A single weak page signal such as paint reset does not authorize recovery.
                return Decision.NONE;
            case PROBING:
                if (baseBlocked(s) || s.nowMs < operationDeadlineMs) return Decision.NONE;
                lastReason = Reason.PROBE_TIMEOUT;
                state = State.BRIDGE_RECOVERY;
                operationDeadlineMs = safeAdd(s.nowMs, REBIND_GRACE_MS);
                return Decision.REBIND_BRIDGE;
            case BRIDGE_RECOVERY:
                if (!bridgeDegraded) {
                    settlePassiveHealthy();
                    return Decision.NONE;
                }
                if (baseBlocked(s) || s.nowMs < operationDeadlineMs) return Decision.NONE;
                lastReason = Reason.REBIND_TIMEOUT;
                // Bridge-only failure settles degraded and leaves the page untouched.
                if (!strongFailure && !paintInvalid && s.paintValid) {
                    state = State.DEGRADED_STABLE;
                    operationDeadlineMs = Long.MAX_VALUE;
                    return Decision.NONE;
                }
                // Independent bridge + page evidence is a weak-signal quorum.
                if (!strongFailure && !paintInvalid) {
                    state = State.DEGRADED_STABLE;
                    operationDeadlineMs = Long.MAX_VALUE;
                    return Decision.NONE;
                }
                if (destructiveBlocked(s)) return Decision.NONE;
                return beginSoftRecovery(s.nowMs);
            case SOFT_RECOVERY:
                if (baseBlocked(s) || s.nowMs < operationDeadlineMs) return Decision.NONE;
                if (destructiveBlocked(s)) return Decision.NONE;
                lastReason = Reason.SOFT_RECOVERY_TIMEOUT;
                state = State.HARD_RECOVERY;
                operationDeadlineMs = safeAdd(s.nowMs, HARD_GRACE_MS);
                return Decision.HARD_REPLACE;
            case HARD_RECOVERY:
                if (baseBlocked(s) || s.nowMs < operationDeadlineMs) return Decision.NONE;
                if (destructiveBlocked(s)) return Decision.NONE;
                lastReason = Reason.HARD_RECOVERY_TIMEOUT;
                state = State.USER_RECOVERY;
                operationDeadlineMs = Long.MAX_VALUE;
                return Decision.SHOW_UI;
            default:
                return Decision.NONE;
        }
    }

    /** Only an active operation has a deadline. Passive states return Long.MAX_VALUE. */
    public long nextDelayMs(Snapshot s) {
        if (s == null) return Long.MAX_VALUE;
        switch (state) {
            case PROBING:
            case BRIDGE_RECOVERY:
            case SOFT_RECOVERY:
            case HARD_RECOVERY:
                if (state != State.PROBING && state != State.BRIDGE_RECOVERY && destructiveBlocked(s)) {
                    return Long.MAX_VALUE;
                }
                if (operationDeadlineMs == Long.MAX_VALUE) return Long.MAX_VALUE;
                return Math.max(1L, operationDeadlineMs - s.nowMs);
            case STABLE:
            case SUSPECT:
            case DEGRADED_STABLE:
            case USER_RECOVERY:
            default:
                return Long.MAX_VALUE;
        }
    }

    private Decision beginSoftRecovery(long nowMs) {
        state = State.SOFT_RECOVERY;
        operationDeadlineMs = safeAdd(nowMs, SOFT_GRACE_MS);
        return Decision.SOFT_RELOAD;
    }

    private void enterSuspectIfPassive() {
        if (state == State.STABLE || state == State.DEGRADED_STABLE) {
            state = State.SUSPECT;
            operationDeadlineMs = Long.MAX_VALUE;
        }
    }

    private void settlePassiveHealthy() {
        operationDeadlineMs = Long.MAX_VALUE;
        state = bridgeDegraded ? State.DEGRADED_STABLE : State.STABLE;
        if (state == State.STABLE) lastReason = Reason.NONE;
    }

    private static boolean baseBlocked(Snapshot s) {
        return s.nowMs < 0L || !s.foreground || !s.currentPageTrusted || s.loading;
    }

    private static boolean destructiveBlocked(Snapshot s) {
        return baseBlocked(s) || !s.networkAvailable || s.composerFocused || s.composerDirty
                || s.composerComposing || s.attachmentActive || s.mediaActive || s.externalFlowActive;
    }

    private static long safeAdd(long value, long delta) {
        if (value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        return value + delta;
    }
}
