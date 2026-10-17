package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.List;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

public final class GeckoNavigationDelegateTest {
    private static final class Opener implements GeckoNavigationDelegate.ExternalOpener {
        final List<String> attempted = new ArrayList<>();
        boolean resolvable = true;
        @Override public boolean openIfResolvable(String uri) {
            attempted.add(uri);
            return resolvable;
        }
    }
    private static final class Sink implements MediaDiagnostics.Sink {
        final List<String> lines = new ArrayList<>();
        @Override public void write(String tag, String message) { lines.add(tag + ":" + message); }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static AllowOrDeny value(GeckoResult<AllowOrDeny> result) {
        return result == null ? AllowOrDeny.ALLOW : result.poll();
    }

    public static void main(String[] args) {
        Opener opener = new Opener();
        Sink sink = new Sink();
        GeckoNavigationDelegate delegate = new GeckoNavigationDelegate(opener, new MediaDiagnostics(sink));
        GeckoSession session = new GeckoSession();

        check(value(delegate.onLoadRequest(session, new GeckoSession.NavigationDelegate.LoadRequest(
                "https://accounts.google.com/o/oauth2/auth?secret=never-log"))) == AllowOrDeny.ALLOW,
                "ordinary HTTPS remains in Gecko");
        check(opener.attempted.isEmpty(), "HTTPS must not launch external intent");

        check(value(delegate.onLoadRequest(session, new GeckoSession.NavigationDelegate.LoadRequest(
                "http://example.com/a?token=secret"))) == AllowOrDeny.DENY,
                "HTTP denied inside Gecko");
        check(opener.attempted.size() == 1, "HTTP launches external browser when resolvable");

        check(value(delegate.onLoadRequest(session, new GeckoSession.NavigationDelegate.LoadRequest(
                "mailto:user@example.com?body=private"))) == AllowOrDeny.DENY,
                "mailto denied inside Gecko");
        check(opener.attempted.size() == 2, "mailto handed to external resolver");

        opener.resolvable = false;
        check(value(delegate.onLoadRequest(session, new GeckoSession.NavigationDelegate.LoadRequest(
                "tel:+123456789"))) == AllowOrDeny.DENY,
                "unresolvable external scheme still denied inside Gecko");
        check(opener.attempted.size() == 3, "external resolver is consulted exactly once");

        check(value(delegate.onLoadRequest(session, new GeckoSession.NavigationDelegate.LoadRequest(
                "file:///sdcard/private.txt"))) == AllowOrDeny.DENY,
                "file navigation denied");
        check(value(delegate.onLoadRequest(session, new GeckoSession.NavigationDelegate.LoadRequest(
                "content://provider/secret"))) == AllowOrDeny.DENY,
                "content top-level navigation denied");
        check(opener.attempted.size() == 3, "file/content are not launched externally");

        String logs = String.join("\n", sink.lines);
        check(logs.contains("ChatGPTWeb/Navigation:NAVIGATION action=ALLOW_IN_GECKO target=https://accounts.google.com"),
                "HTTPS decision logged by origin only");
        check(logs.contains("action=EXTERNAL_INTENT target=http://example.com externalOpened=true"),
                "HTTP external result logged");
        check(logs.contains("action=DENY target=file:"), "file denial logged without path");
        check(!logs.contains("never-log") && !logs.contains("secret") && !logs.contains("user@example.com")
                        && !logs.contains("+123456789") && !logs.contains("private.txt"),
                "navigation diagnostics redact paths, query data, addresses and phone numbers");

        System.out.println("GeckoNavigationDelegateTest OK");
    }
}
