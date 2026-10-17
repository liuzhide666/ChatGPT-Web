package com.liuchenlin.chatgptweb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import org.mozilla.geckoview.WebResponse;

public final class LazyDownloadHandlerTest {
    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        boolean closed;
        CloseTrackingInputStream(byte[] data) { super(data); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
    private static final class Handler implements GeckoSessionController.DownloadHandler {
        int handles;
        int abandons;
        @Override public void handle(WebResponse response) { handles++; DownloadCoordinator.closeBody(response.body); }
        @Override public void abandonPending() { abandons++; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[] creates = {0};
        Handler delegate = new Handler();
        LazyDownloadHandler lazy = new LazyDownloadHandler(() -> {
            creates[0]++;
            return delegate;
        });

        lazy.abandonPending();
        check(creates[0] == 0, "cleanup before first download must not initialize subsystem");

        WebResponse response = new WebResponse("https://example.test/a", Map.of(), new ByteArrayInputStream(new byte[]{1}));
        lazy.handle(response);
        check(creates[0] == 1 && delegate.handles == 1, "first download initializes exactly once");

        lazy.handle(new WebResponse("https://example.test/b", Map.of(), new ByteArrayInputStream(new byte[]{2})));
        check(creates[0] == 1 && delegate.handles == 2, "subsequent downloads reuse delegate");

        lazy.abandonPending();
        check(delegate.abandons == 1, "cleanup delegates after initialization");

        CloseTrackingInputStream failedBody = new CloseTrackingInputStream(new byte[]{3});
        LazyDownloadHandler failing = new LazyDownloadHandler(() -> { throw new LinkageError("missing optional API"); });
        failing.handle(new WebResponse("https://example.test/c", Map.of(), failedBody));
        check(failedBody.closed, "optional download initialization failure closes response without crashing");
        System.out.println("LazyDownloadHandlerTest OK");
    }
}
