package org.mozilla.geckoview;

import org.json.JSONObject;

public class WebExtension {
    public interface MessageDelegate {
        default GeckoResult<Object> onMessage(String nativeApp, Object message, MessageSender sender) { return null; }
        default void onConnect(Port port) {}
    }

    public interface PortDelegate {
        default void onPortMessage(Object message, Port port) {}
        default void onDisconnect(Port port) {}
    }

    public static class MessageSender {
        public static final int ENV_TYPE_EXTENSION = 1;
        public static final int ENV_TYPE_CONTENT_SCRIPT = 2;
        public final GeckoSession session;
        public final String url;
        public final int environmentType;
        private final boolean topLevel;
        public MessageSender(GeckoSession session, String url, boolean topLevel) {
            this(session, url, topLevel, ENV_TYPE_CONTENT_SCRIPT);
        }
        public MessageSender(GeckoSession session, String url, boolean topLevel, int environmentType) {
            this.session = session;
            this.url = url;
            this.topLevel = topLevel;
            this.environmentType = environmentType;
        }
        public boolean isTopLevel() { return topLevel; }
    }

    public static class Port {
        public final MessageSender sender;
        public final String name;
        public JSONObject lastPostedMessage;
        public boolean disconnected;
        public PortDelegate delegate;

        public Port(MessageSender sender, String name) {
            this.sender = sender;
            this.name = name;
        }

        public void postMessage(JSONObject message) { lastPostedMessage = message; }
        public void disconnect() {
            disconnected = true;
            if (delegate != null) delegate.onDisconnect(this);
        }
        public void setDelegate(PortDelegate delegate) { this.delegate = delegate; }
    }

    public static class SessionController {
        public WebExtension extension;
        public MessageDelegate delegate;
        public String nativeApp;
        public void setMessageDelegate(WebExtension extension, MessageDelegate delegate, String nativeApp) {
            this.extension = extension;
            this.delegate = delegate;
            this.nativeApp = nativeApp;
        }
    }
}
