package org.mozilla.geckoview;

import android.content.Context;
import android.view.View;

public class GeckoView extends View {
    private GeckoSession session;
    public GeckoView(Context context) { super(context); }
    public GeckoSession getSession() { return session; }
    public GeckoSession releaseSession() { GeckoSession old = session; session = null; return old; }
    public void setSession(GeckoSession session) { this.session = session; }
}
