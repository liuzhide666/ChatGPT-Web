package com.liuchenlin.chatgptweb;

import java.util.Objects;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/** Applies the top-level navigation boundary without constraining normal HTTPS browsing. */
public final class GeckoNavigationDelegate implements GeckoSession.NavigationDelegate {
    public interface ExternalOpener {
        /** Attempts to open an external URI only when Android has a safe resolver. */
        boolean openIfResolvable(String uri);
    }

    private final ExternalOpener externalOpener;
    private final MediaDiagnostics diagnostics;

    public GeckoNavigationDelegate(ExternalOpener externalOpener, MediaDiagnostics diagnostics) {
        this.externalOpener = Objects.requireNonNull(externalOpener, "externalOpener");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    @Override
    public GeckoResult<AllowOrDeny> onLoadRequest(
            GeckoSession session,
            GeckoSession.NavigationDelegate.LoadRequest request) {
        String uri = request == null ? null : request.uri;
        NavigationPolicy.Action action = NavigationPolicy.classify(uri);
        if (action == NavigationPolicy.Action.ALLOW_IN_GECKO) {
            diagnostics.navigationDecision(uri, action, false);
            return null; // Gecko documents null as ALLOW / unhandled.
        }
        if (action == NavigationPolicy.Action.EXTERNAL_INTENT) {
            boolean opened = externalOpener.openIfResolvable(uri);
            diagnostics.navigationDecision(uri, action, opened);
            return GeckoResult.fromValue(AllowOrDeny.DENY);
        }
        diagnostics.navigationDecision(uri, action, false);
        return GeckoResult.fromValue(AllowOrDeny.DENY);
    }
}
