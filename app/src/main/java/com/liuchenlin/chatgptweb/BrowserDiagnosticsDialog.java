package com.liuchenlin.chatgptweb;

/** Metadata-only browser diagnostics text model used by the Android recovery dialog. */
public final class BrowserDiagnosticsDialog {
    public static final class Snapshot {
        public final String appVersion;
        public final String extensionVersion;
        public final int protocolVersion;
        public final String trustedOriginClass;
        public final long navigationGeneration;
        public final boolean sessionPresent;
        public final boolean loading;
        public final boolean paintValid;
        public final long lastFcpMs;
        public final boolean bridgeConnected;
        public final long lastPongMs;
        public final boolean networkAvailable;
        public final String recoveryState;
        public final String lastRecoveryReason;
        public final String cacheMode;
        public final boolean composerFocused;
        public final boolean composerDirty;
        public final boolean composerComposing;
        public final boolean attachmentActive;
        public final boolean mediaActive;
        public final boolean externalFlowActive;

        public Snapshot(String appVersion, String extensionVersion, int protocolVersion,
                String trustedOriginClass, long navigationGeneration, boolean sessionPresent,
                boolean loading, boolean paintValid, long lastFcpMs, boolean bridgeConnected,
                long lastPongMs, boolean networkAvailable, String recoveryState,
                String lastRecoveryReason, String cacheMode, boolean composerFocused,
                boolean composerDirty, boolean composerComposing, boolean attachmentActive,
                boolean mediaActive, boolean externalFlowActive) {
            this.appVersion = safe(appVersion);
            this.extensionVersion = safe(extensionVersion);
            this.protocolVersion = Math.max(0, protocolVersion);
            this.trustedOriginClass = safe(trustedOriginClass);
            this.navigationGeneration = Math.max(0L, navigationGeneration);
            this.sessionPresent = sessionPresent;
            this.loading = loading;
            this.paintValid = paintValid;
            this.lastFcpMs = Math.max(0L, lastFcpMs);
            this.bridgeConnected = bridgeConnected;
            this.lastPongMs = Math.max(0L, lastPongMs);
            this.networkAvailable = networkAvailable;
            this.recoveryState = safe(recoveryState);
            this.lastRecoveryReason = safe(lastRecoveryReason);
            this.cacheMode = safe(cacheMode);
            this.composerFocused = composerFocused;
            this.composerDirty = composerDirty;
            this.composerComposing = composerComposing;
            this.attachmentActive = attachmentActive;
            this.mediaActive = mediaActive;
            this.externalFlowActive = externalFlowActive;
        }
    }

    public String render(Snapshot s) {
        if (s == null) return "Browser diagnostics unavailable";
        return "App: " + s.appVersion
                + "\nBridge: " + s.extensionVersion + " / protocol " + s.protocolVersion
                + "\nOrigin: " + s.trustedOriginClass
                + "\nNavigation generation: " + s.navigationGeneration
                + "\nSession: " + yesNo(s.sessionPresent) + ", loading=" + yesNo(s.loading)
                + "\nPaint valid: " + yesNo(s.paintValid) + ", last FCP(ms)=" + s.lastFcpMs
                + "\nBridge connected: " + yesNo(s.bridgeConnected) + ", last PONG(ms)=" + s.lastPongMs
                + "\nNetwork available: " + yesNo(s.networkAvailable)
                + "\nRecovery: " + s.recoveryState + " / " + s.lastRecoveryReason
                + "\nCache mode: " + s.cacheMode
                + "\nSafety: focused=" + yesNo(s.composerFocused)
                + ", dirty=" + yesNo(s.composerDirty)
                + ", composing=" + yesNo(s.composerComposing)
                + ", attachment=" + yesNo(s.attachmentActive)
                + ", media=" + yesNo(s.mediaActive)
                + ", external=" + yesNo(s.externalFlowActive);
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String yesNo(boolean value) { return value ? "yes" : "no"; }
}
