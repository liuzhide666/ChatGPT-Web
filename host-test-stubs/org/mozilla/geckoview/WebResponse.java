package org.mozilla.geckoview;

import java.io.InputStream;
import java.util.Map;

public class WebResponse {
    public final String uri;
    public final Map<String, String> headers;
    public final InputStream body;

    public WebResponse(String uri, Map<String, String> headers, InputStream body) {
        this.uri = uri;
        this.headers = headers;
        this.body = body;
    }
}
