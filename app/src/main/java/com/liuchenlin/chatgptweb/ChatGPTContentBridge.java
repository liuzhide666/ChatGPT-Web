package com.liuchenlin.chatgptweb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.function.Supplier;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

/** Passive metadata-only bridge. It never executes keyboard, editor or submit semantics. */
public final class ChatGPTContentBridge implements WebExtension.MessageDelegate, WebExtension.PortDelegate {
    public static final String NATIVE_APP = "chatgptHealth";
    static final String EXTENSION_LOCATION = "resource://android/assets/chatgpt_bridge/";
    static final String EXTENSION_ID = "chatgpt-health@liuchenlin.local";
    static final String EXPECTED_EXTENSION_VERSION = "1.1.2";
    static final int EXPECTED_PROTOCOL_VERSION = 3;

    public enum EventType { READY, PONG, COMPOSER_STATE }

    public static final class Event {
        public final EventType type;
        public final long revision;
        public final boolean composerFocused;
        public final boolean composerDirty;
        public final boolean composing;
        public final boolean attachmentActive;
        public final boolean mainComposer;
        public final String pageUrl;
        public final String extensionVersion;
        public final int protocolVersion;
        public final boolean protocolCompatible;

        Event(EventType type, long revision, boolean composerFocused, boolean composerDirty,
                boolean composing, boolean attachmentActive, boolean mainComposer, String pageUrl,
                String extensionVersion, int protocolVersion, boolean protocolCompatible) {
            this.type = type;
            this.revision = revision;
            this.composerFocused = composerFocused;
            this.composerDirty = composerDirty;
            this.composing = composing;
            this.attachmentActive = attachmentActive;
            this.mainComposer = mainComposer;
            this.pageUrl = pageUrl;
            this.extensionVersion = extensionVersion == null ? "" : extensionVersion;
            this.protocolVersion = Math.max(0, protocolVersion);
            this.protocolCompatible = protocolCompatible;
        }
    }

    public interface Listener { void onEvent(Event event); }
    public interface Logger { void log(String line); }
    public interface ConnectionListener {
        void onPortConnected();
        void onPortDisconnected();
    }

    private final Supplier<GeckoSession> currentSession;
    private final Listener listener;
    private final Logger logger;
    private WebExtension extension;
    private WebExtension.Port currentPort;
    private ConnectionListener connectionListener;
    private volatile boolean bridgeProtocolCompatible;

    public ChatGPTContentBridge(Supplier<GeckoSession> currentSession, Listener listener, Logger logger) {
        this.currentSession = Objects.requireNonNull(currentSession, "currentSession");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void setConnectionListener(ConnectionListener listener) { connectionListener = listener; }

    public void install(GeckoRuntime runtime) {
        runtime.getWebExtensionController().ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).accept(
                installed -> {
                    extension = installed;
                    attachSession(currentSession.get());
                    logger.log("CONTENT_BRIDGE installed=true");
                },
                error -> logger.log("CONTENT_BRIDGE installed=false error="
                        + (error == null ? "unknown" : error.getClass().getSimpleName())));
    }

    public void attachSession(GeckoSession session) {
        WebExtension installed = extension;
        if (installed == null || session == null || session != currentSession.get()) return;
        clearPort();
        session.getWebExtensionController().setMessageDelegate(installed, this, NATIVE_APP);
    }

    @Override
    public GeckoResult<Object> onMessage(
            String nativeApp, Object message, WebExtension.MessageSender sender) {
        if (!isTrustedSender(nativeApp, sender) || !(message instanceof JSONObject)) {
            logger.log("CONTENT_BRIDGE ignored_untrusted=true");
            return null;
        }
        JSONObject json = (JSONObject) message;
        EventType type;
        try {
            type = EventType.valueOf(json.optString("type", ""));
        } catch (IllegalArgumentException unknownType) {
            logger.log("CONTENT_BRIDGE ignored_type=true");
            return null;
        }
        long revision = Math.max(0L, json.optLong("revision", 0L));
        if (type == EventType.READY) {
            bridgeProtocolCompatible = isCompatibleReady(json);
            logger.log("CONTENT_BRIDGE ready compatible=" + bridgeProtocolCompatible
                    + " extension=" + json.optString("extensionVersion", "")
                    + " protocol=" + json.optInt("protocolVersion", 0));
        } else if (!bridgeProtocolCompatible) {
            logger.log("CONTENT_BRIDGE ignored_incompatible_protocol=true type=" + type);
            return null;
        }
        listener.onEvent(eventFromJson(type, revision, json, sender.url));
        return null;
    }

    private static boolean isCompatibleReady(JSONObject json) {
        return json != null
                && EXPECTED_EXTENSION_VERSION.equals(json.optString("extensionVersion", ""))
                && json.optInt("protocolVersion", 0) == EXPECTED_PROTOCOL_VERSION;
    }

    private static Event eventFromJson(EventType type, long revision, JSONObject json, String pageUrl) {
        return new Event(
                type,
                revision,
                json.optBoolean("focused", false),
                json.optBoolean("dirty", false),
                json.optBoolean("composing", false),
                json.optBoolean("attachmentActive", false),
                json.optBoolean("mainComposer", false),
                pageUrl,
                json.optString("extensionVersion", ""),
                json.optInt("protocolVersion", 0),
                type != EventType.READY || isCompatibleReady(json));
    }

    @Override
    public void onConnect(WebExtension.Port port) {
        if (port == null || !NATIVE_APP.equals(port.name)
                || !isTrustedSender(NATIVE_APP, port.sender)) {
            if (port != null) port.disconnect();
            logger.log("CONTENT_BRIDGE port_ignored=true");
            return;
        }
        clearPort();
        currentPort = port;
        port.setDelegate(this);
        logger.log("CONTENT_BRIDGE port_connected=true");
        if (connectionListener != null) connectionListener.onPortConnected();
    }

    @Override
    public void onPortMessage(Object message, WebExtension.Port port) {
        if (port == null || port != currentPort || !isTrustedSender(NATIVE_APP, port.sender)) {
            logger.log("CONTENT_BRIDGE port_message_ignored=true");
        }
    }

    @Override
    public void onDisconnect(WebExtension.Port port) {
        if (port == currentPort) {
            currentPort = null;
            if (connectionListener != null) connectionListener.onPortDisconnected();
        }
    }

    public boolean requestPing() { return requestCommand("PING"); }

    public boolean requestComposerState() { return requestCommand("SYNC_COMPOSER_STATE"); }

    public boolean rebindCurrentSession() {
        GeckoSession session = currentSession.get();
        if (extension == null || session == null) return false;
        attachSession(session);
        return true;
    }

    public boolean isProtocolCompatible() { return bridgeProtocolCompatible; }

    public boolean hasConnectedPort() {
        WebExtension.Port port = currentPort;
        return port != null && isTrustedSender(NATIVE_APP, port.sender);
    }

    private boolean requestCommand(String type) {
        if (!bridgeProtocolCompatible) return false;
        WebExtension.Port port = currentPort;
        if (port == null || !isTrustedSender(NATIVE_APP, port.sender)) {
            clearPort();
            return false;
        }
        try {
            JSONObject command = new JSONObject();
            command.put("type", type);
            port.postMessage(command);
            return true;
        } catch (Exception disconnected) {
            clearPort();
            return false;
        }
    }

    private void clearPort() {
        bridgeProtocolCompatible = false;
        WebExtension.Port old = currentPort;
        currentPort = null;
        if (old != null) {
            old.setDelegate(null);
            try { old.disconnect(); } catch (RuntimeException ignored) { }
        }
    }

    private boolean isTrustedSender(String nativeApp, WebExtension.MessageSender sender) {
        return NATIVE_APP.equals(nativeApp)
                && sender != null
                && sender.session != null
                && sender.session == currentSession.get()
                && sender.environmentType == WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT
                && sender.isTopLevel()
                && isTrustedUrl(sender.url);
    }

    static boolean isTrustedUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            int port = uri.getPort();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "chatgpt.com".equalsIgnoreCase(uri.getHost())
                    && (port == -1 || port == 443);
        } catch (URISyntaxException invalid) {
            return false;
        }
    }
}
