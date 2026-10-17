package com.liuchenlin.chatgptweb;

import java.util.Objects;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.SlowScriptResponse;
import org.mozilla.geckoview.WebResponse;

/** Owns the single current GeckoSession and all state/lifecycle recovery transitions. */
public final class GeckoSessionController
        implements GeckoSession.ProgressDelegate, GeckoSession.ContentDelegate {
    public static final String START_URI = "https://chatgpt.com/";

    public interface SessionFactory {
        GeckoSession create();
    }

    public interface ViewBinder {
        /** Replaces any previously bound session with this current session. */
        void bind(GeckoSession session);
    }

    public interface DownloadHandler {
        void handle(WebResponse response);
        void abandonPending();
    }

    public interface RecoveryUi {
        void showRecovery();
        void hideRecovery();
    }

    public interface Clock {
        long nowMs();
    }

    public interface HealthObserver {
        default void onPageStart(String url, long generation, long nowMs) {}
        default void onProgress(int progress, long generation, long nowMs) {}
        default void onPageStop(boolean success, long generation, long nowMs) {}
        default void onFirstContentfulPaint(long generation, long nowMs) {}
        default void onPaintStatusReset(long generation, long nowMs) {}
        default void onSlowScript(long generation, long nowMs) {}
        default void onSessionStateChange(long generation, long nowMs) {}
    }

    private final GeckoRuntime runtime;
    private final SessionFactory sessionFactory;
    private final ViewBinder viewBinder;
    private final GeckoSession.PermissionDelegate permissionDelegate;
    private final GeckoSession.MediaDelegate mediaDelegate;
    private final GeckoSession.NavigationDelegate navigationDelegate;
    private final GeckoSession.PromptDelegate promptDelegate;
    private final DownloadHandler downloadHandler;
    private final CrashRecoveryPolicy crashPolicy;
    private final ExternalFlowTracker externalFlows;
    private final MediaDiagnostics diagnostics;
    private final RecoveryUi recoveryUi;
    private final Runnable replacementCleanup;
    private final Clock clock;
    private HealthObserver healthObserver = new HealthObserver() {};

    private GeckoSession currentSession;
    private boolean recoveryUiVisible;
    private boolean loading;
    private boolean currentPageTrusted;
    private String lastTrustedUri = START_URI;
    private long navigationGeneration;
    private boolean awaitingInitialPageStart;

    public GeckoSessionController(
            GeckoRuntime runtime,
            SessionFactory sessionFactory,
            ViewBinder viewBinder,
            GeckoSession.PermissionDelegate permissionDelegate,
            GeckoSession.MediaDelegate mediaDelegate,
            GeckoSession.NavigationDelegate navigationDelegate,
            GeckoSession.PromptDelegate promptDelegate,
            DownloadHandler downloadHandler,
            CrashRecoveryPolicy crashPolicy,
            ExternalFlowTracker externalFlows,
            MediaDiagnostics diagnostics,
            RecoveryUi recoveryUi,
            Runnable replacementCleanup,
            Clock clock) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.viewBinder = Objects.requireNonNull(viewBinder, "viewBinder");
        this.permissionDelegate = Objects.requireNonNull(permissionDelegate, "permissionDelegate");
        this.mediaDelegate = Objects.requireNonNull(mediaDelegate, "mediaDelegate");
        this.navigationDelegate = Objects.requireNonNull(navigationDelegate, "navigationDelegate");
        this.promptDelegate = Objects.requireNonNull(promptDelegate, "promptDelegate");
        this.downloadHandler = Objects.requireNonNull(downloadHandler, "downloadHandler");
        this.crashPolicy = Objects.requireNonNull(crashPolicy, "crashPolicy");
        this.externalFlows = Objects.requireNonNull(externalFlows, "externalFlows");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.recoveryUi = Objects.requireNonNull(recoveryUi, "recoveryUi");
        this.replacementCleanup = Objects.requireNonNull(replacementCleanup, "replacementCleanup");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void setHealthObserver(HealthObserver observer) {
        healthObserver = observer == null ? new HealthObserver() {} : observer;
    }

    public GeckoSession currentSession() {
        return currentSession;
    }

    /** Starts a new browser tab at ChatGPT home. Previous webpage SessionState is never restored. */
    public void startFreshHome() {
        recoveryUiVisible = false;
        recoveryUi.hideRecovery();
        lastTrustedUri = START_URI;
        createAndBindFreshSession();
    }

    /** Explicit Launcher re-entry: abandon stale external callbacks and open one fresh home session. */
    public void reopenFreshHome() {
        recoveryUiVisible = false;
        recoveryUi.hideRecovery();
        cleanupForReplacement();
        replaceWithFreshSession();
        diagnostics.sessionEvent("SESSION_REOPEN launcher=true home=true");
    }


    public boolean isLoading() {
        return loading;
    }

    public boolean isCurrentPageTrusted() {
        return currentPageTrusted;
    }

    public long navigationGeneration() {
        return navigationGeneration;
    }

    /** Records only top-level ChatGPT HTTPS locations as hard-recovery targets. */
    public void recordTrustedUri(String uri) {
        if (ChatGPTContentBridge.isTrustedUrl(uri)) {
            lastTrustedUri = uri;
        }
    }

    /** First page-health action: ordinary cache-respecting reload, exactly once per policy incident. */
    public void softRecoverCurrentPage() {
        GeckoSession session = currentSession;
        if (session == null || recoveryUiVisible) return;
        session.reload();
        diagnostics.sessionEvent("PAGE_HEALTH action=SOFT_RELOAD cache=DEFAULT");
    }

    /** Second page-health action: discard the bloated page context, keep the shared Gecko profile/cache. */
    public void hardRecoverTrustedPage() {
        if (currentSession == null || recoveryUiVisible) return;
        cleanupForReplacement();
        replaceWithUri(lastTrustedUri);
        diagnostics.sessionEvent("PAGE_HEALTH action=HARD_SESSION_REPLACE profile=SHARED cache=DEFAULT");
    }

    /** Explicit user action only: reload trusted content once while bypassing cached resources. */
    public void forceRefetchCurrentPage() {
        recoveryUiVisible = false;
        recoveryUi.hideRecovery();
        GeckoSession session = currentSession;
        if (session != null) {
            session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
            diagnostics.sessionEvent("PAGE_HEALTH action=USER_FORCE_REFETCH cache=BYPASS_ONCE");
            return;
        }
        createAndBindSession(lastTrustedUri, GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
        diagnostics.sessionEvent("PAGE_HEALTH action=USER_FORCE_REFETCH new_session=true cache=BYPASS_ONCE");
    }

    /** Final page-health action: stop automatic recovery and expose the existing manual recovery UI. */
    public void showPageHealthRecoveryUi() {
        if (recoveryUiVisible) return;
        recoveryUiVisible = true;
        recoveryUi.showRecovery();
        diagnostics.sessionEvent("PAGE_HEALTH action=SHOW_RECOVERY_UI");
    }

    @Override
    public void onPageStart(GeckoSession session, String url) {
        if (session != currentSession) return;
        if (awaitingInitialPageStart) {
            awaitingInitialPageStart = false;
        } else {
            navigationGeneration += 1L;
        }
        loading = true;
        currentPageTrusted = ChatGPTContentBridge.isTrustedUrl(url);
        if (currentPageTrusted) recordTrustedUri(url);
        healthObserver.onPageStart(url, navigationGeneration, clock.nowMs());
    }

    @Override
    public void onProgressChange(GeckoSession session, int progress) {
        if (session != currentSession) return;
        healthObserver.onProgress(progress, navigationGeneration, clock.nowMs());
    }

    @Override
    public void onPageStop(GeckoSession session, boolean success) {
        if (session != currentSession) return;
        loading = false;
        healthObserver.onPageStop(success, navigationGeneration, clock.nowMs());
    }

    @Override
    public void onSessionStateChange(GeckoSession session, GeckoSession.SessionState sessionState) {
        if (session != currentSession) return;
        healthObserver.onSessionStateChange(navigationGeneration, clock.nowMs());
    }

    @Override
    public void onFirstContentfulPaint(GeckoSession session) {
        if (session != currentSession) return;
        healthObserver.onFirstContentfulPaint(navigationGeneration, clock.nowMs());
    }

    @Override
    public void onPaintStatusReset(GeckoSession session) {
        if (session != currentSession) return;
        healthObserver.onPaintStatusReset(navigationGeneration, clock.nowMs());
        diagnostics.sessionEvent("CONTENT_PAINT_STATUS_RESET observed=true");
    }

    @Override
    public GeckoResult<SlowScriptResponse> onSlowScript(
            GeckoSession session, String scriptFileName) {
        if (session != currentSession) return null;
        healthObserver.onSlowScript(navigationGeneration, clock.nowMs());
        // Metadata-only diagnostic: never persist or log scriptFileName/URL contents.
        diagnostics.sessionEvent("CONTENT_SLOW_SCRIPT observed=true");
        return GeckoResult.fromValue(SlowScriptResponse.CONTINUE);
    }

    /** Called for a real Activity stop as well as temporary external-system flows. */
    public void onStop() {
        GeckoSession session = currentSession;
        if (session == null) {
            return;
        }
        if (externalFlows.isAnyActive()) {
            diagnostics.sessionEvent("BACKGROUND external_flow=true keep_active=true");
            return;
        }
        session.setActive(false);
        diagnostics.sessionEvent("BACKGROUND inactive=true gecko_flush=true");
    }

    /** Reactivates an already-live GeckoSession without time-based reloads or home resets. */
    public void onStart() {
        GeckoSession session = currentSession;
        if (session == null) {
            return;
        }
        session.setActive(true);
        diagnostics.sessionEvent("FOREGROUND active=true live_session_resume=true");
    }

    /** Final Activity teardown: release the live page context; this is not a background/resume transition. */
    public void destroy() {
        recoveryUiVisible = false;
        lastTrustedUri = START_URI;
        closeCurrentSession("SESSION_DESTROY current_closed=true");
    }

    @Override
    public void onExternalResponse(GeckoSession session, WebResponse response) {
        if (session != currentSession) {
            DownloadCoordinator.closeBody(response == null ? null : response.body);
            diagnostics.sessionEvent("DOWNLOAD stale_session=true body_closed=true");
            return;
        }
        downloadHandler.handle(response);
    }

    @Override
    public void onCrash(GeckoSession session) {
        handleContentFailure(session, "CRASH");
    }

    @Override
    public void onKill(GeckoSession session) {
        handleContentFailure(session, "KILL");
    }

    public void retryAfterRecoveryUi() {
        crashPolicy.reset();
        recoveryUiVisible = false;
        recoveryUi.hideRecovery();
        cleanupForReplacement();
        replaceWithFreshSession();
    }

    private void handleContentFailure(GeckoSession failedSession, String event) {
        if (failedSession != currentSession || recoveryUiVisible) {
            return;
        }
        cleanupForReplacement();
        CrashRecoveryPolicy.Decision decision = crashPolicy.recordFailure(clock.nowMs());
        diagnostics.recoveryEvent(event, decision.name());
        diagnostics.sessionEvent("CONTENT_FAILURE discarded_failed_session=true");
        if (decision == CrashRecoveryPolicy.Decision.SHOW_RECOVERY_UI) {
            closeCurrentSession("CONTENT_FAILURE failed_session_closed=true");
            recoveryUiVisible = true;
            recoveryUi.showRecovery();
            return;
        }
        replaceWithFreshSession();
    }

    private void cleanupForReplacement() {
        try {
            replacementCleanup.run();
        } catch (RuntimeException cleanupError) {
            diagnostics.sessionEvent("SESSION_REPLACEMENT cleanup_exception="
                    + cleanupError.getClass().getSimpleName());
        }
        externalFlows.clearAll();
    }

    private void replaceWithFreshSession() {
        replaceWithUri(START_URI);
    }

    private void replaceWithUri(String uri) {
        closeCurrentSession("SESSION_REPLACEMENT old_closed=true");
        createAndBindSession(uri);
    }

    private void closeCurrentSession(String diagnosticEvent) {
        GeckoSession old = currentSession;
        if (old == null) return;
        // Make any close-time callback stale before releasing the underlying Gecko object.
        currentSession = null;
        loading = false;
        currentPageTrusted = false;
        awaitingInitialPageStart = false;
        old.close();
        diagnostics.sessionEvent(diagnosticEvent);
    }

    private void createAndBindFreshSession() {
        createAndBindSession(START_URI);
    }

    private void createAndBindSession(String uri) {
        createAndBindSession(uri, GeckoSession.LOAD_FLAGS_NONE);
    }

    private void createAndBindSession(String uri, int loadFlags) {
        GeckoSession session = Objects.requireNonNull(sessionFactory.create(), "sessionFactory.create()");

        // Delegates must be present before open() so no early event can escape policy/diagnostics.
        session.setPermissionDelegate(permissionDelegate);
        session.setMediaDelegate(mediaDelegate);
        session.setNavigationDelegate(navigationDelegate);
        session.setProgressDelegate(this);
        session.setPromptDelegate(promptDelegate);
        session.setContentDelegate(this);

        currentSession = session;
        loading = true;
        currentPageTrusted = ChatGPTContentBridge.isTrustedUrl(uri);
        navigationGeneration += 1L;
        awaitingInitialPageStart = true;
        session.open(runtime);
        if (currentSession != session) {
            diagnostics.sessionEvent("SESSION_START stale_after_open=true");
            return;
        }
        viewBinder.bind(session);
        if (loadFlags == GeckoSession.LOAD_FLAGS_NONE) {
            session.loadUri(uri);
        } else {
            session.load(new GeckoSession.Loader().uri(uri).flags(loadFlags));
        }
        diagnostics.sessionEvent("SESSION_START uri=" + (START_URI.equals(uri) ? "home" : "trusted")
                + " cache=" + (loadFlags == GeckoSession.LOAD_FLAGS_NONE ? "DEFAULT" : "EXPLICIT_FLAGS"));
    }
}
