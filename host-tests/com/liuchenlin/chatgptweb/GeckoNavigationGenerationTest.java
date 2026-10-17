package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.List;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebResponse;

public final class GeckoNavigationGenerationTest {
    private static void check(boolean v,String m){ if(!v) throw new AssertionError(m); }
    private static final class Factory implements GeckoSessionController.SessionFactory {
        final List<GeckoSession> sessions = new ArrayList<>();
        @Override public GeckoSession create(){ GeckoSession s=new GeckoSession(); sessions.add(s); return s; }
    }
    public static void main(String[] args) {
        Factory factory=new Factory();
        ExternalFlowTracker flows=new ExternalFlowTracker();
        GeckoSessionController controller=new GeckoSessionController(
                new GeckoRuntime(), factory, session -> {},
                new GeckoSession.PermissionDelegate(){}, new GeckoSession.MediaDelegate(){},
                new GeckoSession.NavigationDelegate(){}, new GeckoSession.PromptDelegate(){},
                new GeckoSessionController.DownloadHandler(){
                    @Override public void handle(WebResponse r) {}
                    @Override public void abandonPending() {}
                }, new CrashRecoveryPolicy(), flows,
                new MediaDiagnostics((tag,message)->{}),
                new GeckoSessionController.RecoveryUi(){
                    @Override public void showRecovery() {}
                    @Override public void hideRecovery() {}
                }, ()->{}, ()->123L);
        List<Long> starts=new ArrayList<>();
        List<Long> fcps=new ArrayList<>();
        controller.setHealthObserver(new GeckoSessionController.HealthObserver(){
            @Override public void onPageStart(String url,long generation,long nowMs){ starts.add(generation); }
            @Override public void onFirstContentfulPaint(long generation,long nowMs){ fcps.add(generation); }
        });

        controller.startFreshHome();
        GeckoSession first=controller.currentSession();
        check(controller.navigationGeneration()==1L,"new session allocates generation 1");
        first.progressDelegate.onPageStart(first,"https://chatgpt.com/");
        first.contentDelegate.onFirstContentfulPaint(first);
        check(starts.equals(List.of(1L)) && fcps.equals(List.of(1L)),
                "initial page callbacks share session generation");

        first.progressDelegate.onPageStart(first,"https://chatgpt.com/c/next");
        first.contentDelegate.onFirstContentfulPaint(first);
        check(controller.navigationGeneration()==2L,"same-session top-level navigation increments generation");
        check(starts.equals(List.of(1L,2L)) && fcps.equals(List.of(1L,2L)),
                "later navigation callbacks carry new generation");

        controller.reopenFreshHome();
        GeckoSession second=controller.currentSession();
        check(second!=first && controller.navigationGeneration()==3L,
                "replacement session receives a new generation");
        int before=starts.size();
        first.progressDelegate.onPageStart(first,"https://chatgpt.com/c/stale");
        check(starts.size()==before && controller.navigationGeneration()==3L,
                "stale old-session callbacks cannot advance current generation");
        second.progressDelegate.onPageStart(second,"https://chatgpt.com/");
        check(starts.get(starts.size()-1)==3L,
                "replacement initial page start does not double-increment generation");

        System.out.println("GeckoNavigationGenerationTest OK");
    }
}
