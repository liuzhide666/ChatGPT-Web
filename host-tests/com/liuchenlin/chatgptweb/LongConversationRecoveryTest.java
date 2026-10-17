package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.List;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebResponse;

public final class LongConversationRecoveryTest {
    private static final class Factory implements GeckoSessionController.SessionFactory {
        final List<GeckoSession> made = new ArrayList<>();
        public GeckoSession create() { GeckoSession s = new GeckoSession(); made.add(s); return s; }
    }
    private static final class Ui implements GeckoSessionController.RecoveryUi {
        int show, hide; public void showRecovery(){show++;} public void hideRecovery(){hide++;}
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        Factory factory = new Factory();
        Ui ui = new Ui();
        GeckoSessionController controller = new GeckoSessionController(
                new GeckoRuntime(), factory, s -> {},
                new GeckoSession.PermissionDelegate(){}, new GeckoSession.MediaDelegate(){},
                new GeckoSession.NavigationDelegate(){}, new GeckoSession.PromptDelegate(){},
                new GeckoSessionController.DownloadHandler(){ public void handle(WebResponse r){} public void abandonPending(){} },
                new CrashRecoveryPolicy(), new ExternalFlowTracker(), new MediaDiagnostics((t,m)->{}), ui, ()->{}, ()->0L);
        controller.startFreshHome();
        GeckoSession first = controller.currentSession();
        check(controller.isCurrentPageTrusted(), "fresh ChatGPT session starts trusted");
        controller.onPageStart(first, "https://auth.openai.com/authorize");
        check(!controller.isCurrentPageTrusted(), "external HTTPS top-level page disarms page-health");
        controller.onPageStart(first, "https://chatgpt.com/c/long-thread");
        check(controller.isCurrentPageTrusted(), "return to ChatGPT re-arms trusted-page eligibility");
        controller.recordTrustedUri("https://chatgpt.com/c/long-thread");
        controller.softRecoverCurrentPage();
        check(first.reloadCalls == 1 && factory.made.size() == 1, "soft recovery is exactly one ordinary reload");
        controller.hardRecoverTrustedPage();
        GeckoSession second = controller.currentSession();
        check(first.closeCalls == 1 && second != first && factory.made.size() == 2,
                "hard recovery closes old and creates exactly one replacement");
        check("https://chatgpt.com/c/long-thread".equals(second.loadedUri),
                "hard recovery returns to last trusted ChatGPT URI");
        controller.recordTrustedUri("https://evil.example/steal");
        controller.hardRecoverTrustedPage();
        GeckoSession third = controller.currentSession();
        check("https://chatgpt.com/c/long-thread".equals(third.loadedUri), "untrusted URI never replaces trusted recovery target");
        controller.showPageHealthRecoveryUi();
        controller.showPageHealthRecoveryUi();
        check(ui.show == 1, "page-health recovery UI is shown once, without loop");
        System.out.println("LongConversationRecoveryTest OK");
    }
}
