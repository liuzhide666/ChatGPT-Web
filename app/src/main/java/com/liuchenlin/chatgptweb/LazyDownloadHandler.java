package com.liuchenlin.chatgptweb;

import java.util.Objects;
import org.mozilla.geckoview.WebResponse;

/** Keeps the optional download subsystem completely out of the critical startup path. */
public final class LazyDownloadHandler implements GeckoSessionController.DownloadHandler {
    public interface Factory {
        GeckoSessionController.DownloadHandler create();
    }

    private final Factory factory;
    private GeckoSessionController.DownloadHandler delegate;

    public LazyDownloadHandler(Factory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public void handle(WebResponse response) {
        GeckoSessionController.DownloadHandler handler;
        try {
            handler = delegate();
        } catch (RuntimeException | LinkageError unavailable) {
            DownloadCoordinator.closeBody(response == null ? null : response.body);
            return;
        }
        handler.handle(response);
    }

    @Override
    public synchronized void abandonPending() {
        if (delegate != null) {
            delegate.abandonPending();
        }
    }

    private synchronized GeckoSessionController.DownloadHandler delegate() {
        if (delegate == null) {
            delegate = Objects.requireNonNull(factory.create(), "download handler");
        }
        return delegate;
    }
}
