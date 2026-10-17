package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebResponse;
import java.io.ByteArrayInputStream;

public final class GeckoSessionControllerTest {
    private static class Factory implements GeckoSessionController.SessionFactory {
        final List<GeckoSession> made = new ArrayList<>();
        @Override public GeckoSession create() { GeckoSession s = new GeckoSession(); made.add(s); return s; }
    }
    private static final class Binder implements GeckoSessionController.ViewBinder {
        final List<GeckoSession> bound = new ArrayList<>();
        @Override public void bind(GeckoSession session) { session.events.add("bind"); bound.add(session); }
    }
    private static final class RecoveryUi implements GeckoSessionController.RecoveryUi {
        int showCalls, hideCalls;
        @Override public void showRecovery() { showCalls++; }
        @Override public void hideRecovery() { hideCalls++; }
    }
    private static final class Clock implements GeckoSessionController.Clock {
        long now;
        @Override public long nowMs() { return now; }
    }
    private static final class Sink implements MediaDiagnostics.Sink {
        final List<String> lines = new ArrayList<>();
        @Override public void write(String tag, String message) { lines.add(tag + ":" + message); }
    }
    private static final class CloseAwareInputStream extends ByteArrayInputStream {
        boolean closed;
        CloseAwareInputStream() { super(new byte[]{1}); }
        @Override public void close() throws java.io.IOException { closed = true; super.close(); }
    }
    private static final class Downloads implements GeckoSessionController.DownloadHandler {
        int calls;
        WebResponse last;
        int abandonCalls;
        @Override public void handle(WebResponse response) { calls++; last = response; }
        @Override public void abandonPending() { abandonCalls++; }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static int indexOf(List<String> events, String value) {
        int i = events.indexOf(value);
        if (i < 0) throw new AssertionError("missing event " + value + " in " + events);
        return i;
    }

    private static GeckoSessionController controller(
            Factory factory, Binder binder, RecoveryUi recovery,
            Clock clock, ExternalFlowTracker flows, Sink sink) {
        return controller(factory, binder, recovery, clock, flows, sink, new Downloads(), () -> {});
    }

    private static GeckoSessionController controller(
            Factory factory, Binder binder, RecoveryUi recovery,
            Clock clock, ExternalFlowTracker flows, Sink sink, Runnable replacementCleanup) {
        return controller(factory, binder, recovery, clock, flows, sink, new Downloads(), replacementCleanup);
    }

    private static GeckoSessionController controller(
            Factory factory, Binder binder, RecoveryUi recovery,
            Clock clock, ExternalFlowTracker flows, Sink sink, Downloads downloads, Runnable replacementCleanup) {
        return new GeckoSessionController(
                new GeckoRuntime(), factory, binder,
                new GeckoSession.PermissionDelegate(){}, new GeckoSession.MediaDelegate(){},
                new GeckoSession.NavigationDelegate(){}, new GeckoSession.PromptDelegate(){}, downloads,
                new CrashRecoveryPolicy(), flows,
                new MediaDiagnostics(sink), recovery, replacementCleanup, clock);
    }

    public static void main(String[] args) {
        Factory factory = new Factory();
        Binder binder = new Binder();
        RecoveryUi recovery = new RecoveryUi();
        Clock clock = new Clock();
        ExternalFlowTracker flows = new ExternalFlowTracker();
        Sink sink = new Sink();
        GeckoSessionController controller = controller(factory, binder, recovery, clock, flows, sink);

        controller.startFreshHome();
        GeckoSession first = controller.currentSession();
        check(first != null && factory.made.size() == 1 && binder.bound.size() == 1, "one current session created and bound");
        for (String setter : new String[]{"setPermissionDelegate", "setMediaDelegate", "setNavigationDelegate",
                "setProgressDelegate", "setPromptDelegate", "setContentDelegate"}) {
            check(indexOf(first.events, setter) < indexOf(first.events, "open"), setter + " must precede open");
        }
        check(indexOf(first.events, "open") < indexOf(first.events, "bind"), "open precedes bind");
        check(first.loadCalls == 1 && "https://chatgpt.com/".equals(first.loadedUri),
                "cold startup always loads the ChatGPT home URL");
        check(first.restoreCalls == 0, "cold startup never restores the prior webpage SessionState");

        Downloads downloads = new Downloads();
        GeckoSessionController downloadController = controller(
                new Factory(), new Binder(), new RecoveryUi(), new Clock(),
                new ExternalFlowTracker(), new Sink(), downloads, downloads::abandonPending);
        downloadController.startFreshHome();
        GeckoSession downloadSession = downloadController.currentSession();
        ByteArrayInputStream currentBody = new ByteArrayInputStream(new byte[]{1});
        WebResponse currentResponse = new WebResponse("https://example.test/a", Map.of(), currentBody);
        downloadSession.contentDelegate.onExternalResponse(downloadSession, currentResponse);
        check(downloads.calls == 1 && downloads.last == currentResponse, "current external response forwarded exactly once");
        GeckoSession staleSession = new GeckoSession();
        CloseAwareInputStream staleBody = new CloseAwareInputStream();
        staleSession.contentDelegate = downloadSession.contentDelegate;
        staleSession.contentDelegate.onExternalResponse(staleSession,
                new WebResponse("https://example.test/stale", Map.of(), staleBody));
        check(downloads.calls == 1 && staleBody.closed, "stale external response body is closed, not forwarded");


        clock.now = 1_000L;
        controller.onStop();
        check(first.activeFalseCalls == 1 && first.flushCalls == 0,
                "background relies on Gecko setActive(false) state flush without duplicate explicit flush");
        controller.onStart();
        check(first.activeTrueCalls == 1 && first.reloadCalls == 0,
                "live session foreground resume never reloads");

        clock.now = 620_000L;
        controller.onStop();
        controller.onStart();
        check(first.reloadCalls == 0,
                "live session still resumes without reload even when no persisted state is currently available");

        flows.begin(ExternalFlowTracker.Flow.FILE_PICKER);
        int inactiveBeforePicker = first.activeFalseCalls;
        int flushBeforePicker = first.flushCalls;
        clock.now = 30_000L;
        controller.onStop();
        check(first.activeFalseCalls == inactiveBeforePicker && first.flushCalls == flushBeforePicker,
                "external picker stop keeps Gecko active and avoids unnecessary flush");

        flows.end(ExternalFlowTracker.Flow.FILE_PICKER);
        clock.now = 900_000L;
        controller.onStart();
        check(first.reloadCalls == 0, "external flow return never triggers a time-based reload");


        // Explicit Launcher re-entry replaces the live page with one clean ChatGPT home session.
        Factory restoreFactory = new Factory();
        Binder restoreBinder = new Binder();
        RecoveryUi restoreRecovery = new RecoveryUi();
        Clock restoreClock = new Clock();
        ExternalFlowTracker restoreFlows = new ExternalFlowTracker();
        Sink restoreSink = new Sink();
        final int[] cleanupCalls = {0};
        GeckoSessionController restoredController = controller(
                restoreFactory, restoreBinder, restoreRecovery, restoreClock, restoreFlows, restoreSink,
                () -> cleanupCalls[0]++);
        restoredController.startFreshHome();
        GeckoSession restored = restoredController.currentSession();
        restoredController.reopenFreshHome();
        GeckoSession reopened = restoredController.currentSession();
        check(reopened != restored && restoreFactory.made.size() == 2,
                "launcher re-entry creates exactly one replacement session");
        check(restored.closeCalls == 1, "launcher replacement closes the old live session exactly once");
        check(reopened.restoreCalls == 0 && reopened.loadCalls == 1
                        && GeckoSessionController.START_URI.equals(reopened.loadedUri),
                "launcher replacement loads a clean ChatGPT home page");
        check(cleanupCalls[0] == 1, "launcher replacement abandons pending external callbacks");
        GeckoSession restoredForCrash = reopened;

        // Crash/kill recovery uses a fresh session; third failure inside 60s stops the loop.
        restoreClock.now = 100_000L;
        restoredForCrash.contentDelegate.onCrash(restoredForCrash);
        GeckoSession afterCrash = restoredController.currentSession();
        check(afterCrash != restoredForCrash && restoreFactory.made.size() == 3, "first crash replaces dead session");
        check(restoredForCrash.closeCalls == 1, "replaced session is explicitly closed exactly once");
        check(afterCrash.restoreCalls == 0, "crash recovery must not restore the crashed page state");
        check(afterCrash.loadCalls == 1 && "https://chatgpt.com/".equals(afterCrash.loadedUri),
                "crash recovery loads a clean ChatGPT start URL");

        check(cleanupCalls[0] == 2, "content crash performs one additional cleanup before recovery");
        restoreClock.now = 120_000L;
        afterCrash.contentDelegate.onKill(afterCrash);
        GeckoSession afterKill = restoredController.currentSession();
        check(afterKill != afterCrash && restoreFactory.made.size() == 4, "second failure still auto-recovers");
        check(afterKill.restoreCalls == 0 && afterKill.loadCalls == 1,
                "second content failure also uses a clean start instead of crashed state");

        final int[] paintResets = {0};
        final int[] slowScripts = {0};
        restoredController.setHealthObserver(new GeckoSessionController.HealthObserver() {
            @Override public void onPaintStatusReset(long generation, long nowMs) { paintResets[0]++; }
            @Override public void onSlowScript(long generation, long nowMs) { slowScripts[0]++; }
        });
        afterKill.contentDelegate.onPaintStatusReset(afterKill);
        check(paintResets[0] == 1, "paint status reset is forwarded as content liveness evidence");
        org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.SlowScriptResponse> slowResult =
                afterKill.contentDelegate.onSlowScript(afterKill, "redacted.js");
        check(slowScripts[0] == 1, "slow script is forwarded as diagnostic liveness evidence");
        check(slowResult != null && slowResult.poll() == org.mozilla.geckoview.SlowScriptResponse.CONTINUE,
                "slow script observer continues content instead of forcing recovery");
        restoreClock.now = 150_000L;
        afterKill.contentDelegate.onCrash(afterKill);
        check(restoreFactory.made.size() == 4 && restoreRecovery.showCalls == 1,
                "third failure in 60 seconds shows recovery UI without restart loop");
        check(afterKill.closeCalls == 1,
                "third failed session is closed immediately before recovery UI is shown");
        check(restoredController.currentSession() == null,
                "recovery UI does not retain a crashed current session");
        afterKill.contentDelegate.onKill(afterKill);
        check(restoreFactory.made.size() == 4 && restoreRecovery.showCalls == 1,
                "callbacks from stale/dead session do not create extra sessions");

        restoredController.retryAfterRecoveryUi();
        check(restoreFactory.made.size() == 5 && restoreRecovery.hideCalls >= 1,
                "explicit recovery action resets guard and creates one fresh session");

        String logs = String.join("\n", restoreSink.lines);
        check(logs.contains("ChatGPTWeb/Recovery:CONTENT_PROCESS event=CRASH action=AUTO_RECOVER"),
                "crash recovery decision logged");
        check(logs.contains("action=SHOW_RECOVERY_UI"), "loop guard decision logged");

        // If Gecko emits a synchronous content crash from open(), recovery must win and
        // the stale first session must never be rebound after open() returns.
        Binder earlyBinder = new Binder();
        RecoveryUi earlyRecovery = new RecoveryUi();
        Clock earlyClock = new Clock();
        ExternalFlowTracker earlyFlows = new ExternalFlowTracker();
        Sink earlySink = new Sink();
        Factory earlyFactory = new Factory() {
            int count;
            @Override public GeckoSession create() {
                GeckoSession s = new GeckoSession();
                made.add(s);
                if (count++ == 0) {
                    s.onOpenHook = () -> s.contentDelegate.onCrash(s);
                }
                return s;
            }
        };
        GeckoSessionController earlyController = controller(
                earlyFactory, earlyBinder, earlyRecovery, earlyClock, earlyFlows, earlySink);
        earlyController.startFreshHome();
        check(earlyFactory.made.size() == 2, "synchronous open crash creates one replacement session");
        check(earlyController.currentSession() == earlyFactory.made.get(1),
                "replacement session remains current after stale open returns");
        check(earlyBinder.bound.size() == 1 && earlyBinder.bound.get(0) == earlyFactory.made.get(1),
                "stale session is never rebound after synchronous open crash");

        GeckoSession destroySession = earlyController.currentSession();
        earlyController.destroy();
        check(destroySession.closeCalls == 1, "activity destruction closes the live GeckoSession exactly once");
        check(earlyController.currentSession() == null, "destroy clears the current GeckoSession reference");

        // Explicit user force-refetch is the only cache-bypass path. It keeps a live session when possible.
        Factory forceFactory = new Factory();
        RecoveryUi forceUi = new RecoveryUi();
        GeckoSessionController forceController = controller(
                forceFactory, new Binder(), forceUi, new Clock(), new ExternalFlowTracker(), new Sink());
        forceController.startFreshHome();
        GeckoSession forceSession = forceController.currentSession();
        forceController.forceRefetchCurrentPage();
        check(forceController.currentSession() == forceSession,
                "user force-refetch keeps the current live GeckoSession");
        check(forceSession.reloadCalls == 1
                        && forceSession.reloadFlags == GeckoSession.LOAD_FLAGS_BYPASS_CACHE,
                "user force-refetch performs exactly one bypass-cache reload");

        // If no session exists, the same explicit action creates one trusted page with bypass flags.
        Factory coldForceFactory = new Factory();
        GeckoSessionController coldForceController = controller(
                coldForceFactory, new Binder(), new RecoveryUi(), new Clock(),
                new ExternalFlowTracker(), new Sink());
        coldForceController.forceRefetchCurrentPage();
        GeckoSession coldForced = coldForceController.currentSession();
        check(coldForced != null && coldForced.loadCalls == 1
                        && coldForced.loadedFlags == GeckoSession.LOAD_FLAGS_BYPASS_CACHE
                        && GeckoSessionController.START_URI.equals(coldForced.loadedUri),
                "user force-refetch can create a trusted session with one-shot bypass cache");

        System.out.println("GeckoSessionControllerTest OK");
    }
}
