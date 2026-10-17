package org.mozilla.geckoview;

public class WebExtensionController {
    public String lastUri;
    public String lastId;
    public WebExtension extension = new WebExtension();
    public GeckoResult<WebExtension> installBuiltIn(String uri) {
        return GeckoResult.fromValue(new WebExtension());
    }

    public GeckoResult<WebExtension> ensureBuiltIn(String uri, String id) {
        lastUri = uri;
        lastId = id;
        return GeckoResult.fromValue(extension);
    }
}
