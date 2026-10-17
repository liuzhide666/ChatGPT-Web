package org.mozilla.geckoview;

import android.content.Context;

public class GeckoRuntime {
    private final WebExtensionController webExtensionController = new WebExtensionController();
    public static int createCount;
    public static Context lastContext;
    public static RuntimeException failure;

    public static GeckoRuntime create(Context context) {
        createCount++;
        lastContext = context;
        if (failure != null) {
            throw failure;
        }
        return new GeckoRuntime();
    }
    public WebExtensionController getWebExtensionController() { return webExtensionController; }
}
