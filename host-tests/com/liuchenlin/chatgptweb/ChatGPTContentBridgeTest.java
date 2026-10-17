package com.liuchenlin.chatgptweb;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

public final class ChatGPTContentBridgeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static JSONObject ready() {
        return new JSONObject().put("type", "READY")
                .put("extensionVersion", ChatGPTContentBridge.EXPECTED_EXTENSION_VERSION)
                .put("protocolVersion", ChatGPTContentBridge.EXPECTED_PROTOCOL_VERSION);
    }

    public static void main(String[] args) throws Exception {
        GeckoSession current = new GeckoSession();
        GeckoSession stale = new GeckoSession();
        List<ChatGPTContentBridge.Event> events = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        ChatGPTContentBridge bridge = new ChatGPTContentBridge(() -> current, events::add, logs::add);
        WebExtension.MessageSender trusted = new WebExtension.MessageSender(
                current, "https://chatgpt.com/c/abc", true);

        bridge.onMessage(ChatGPTContentBridge.NATIVE_APP, ready(), trusted);
        check(events.size() == 1 && events.get(0).type == ChatGPTContentBridge.EventType.READY,
                "compatible READY establishes protocol3 liveness");
        check(events.get(0).protocolCompatible
                        && "1.1.2".equals(events.get(0).extensionVersion)
                        && events.get(0).protocolVersion == 3,
                "READY carries exact passive bridge identity");

        JSONObject pong = new JSONObject().put("type", "PONG").put("revision", 7);
        bridge.onMessage(ChatGPTContentBridge.NATIVE_APP, pong, trusted);
        check(events.size() == 2 && events.get(1).type == ChatGPTContentBridge.EventType.PONG,
                "trusted PONG accepted after compatible READY");
        check(events.get(1).revision == 7, "revision forwarded as metadata");

        bridge.onMessage(ChatGPTContentBridge.NATIVE_APP, pong,
                new WebExtension.MessageSender(stale, "https://chatgpt.com/c/abc", true));
        bridge.onMessage(ChatGPTContentBridge.NATIVE_APP, pong,
                new WebExtension.MessageSender(current, "https://evil.example/", true));
        bridge.onMessage(ChatGPTContentBridge.NATIVE_APP, pong,
                new WebExtension.MessageSender(current, "https://chatgpt.com/c/abc", false));
        bridge.onMessage("wrong-app", pong, trusted);
        bridge.onMessage(ChatGPTContentBridge.NATIVE_APP,
                new JSONObject().put("type", "COMMAND_ACK"), trusted);
        check(events.size() == 2, "stale/untrusted/removed semantic event types are ignored");

        JSONObject composer = new JSONObject().put("type", "COMPOSER_STATE")
                .put("focused", true).put("dirty", true).put("composing", false).put("attachmentActive", true)
                .put("mainComposer", true).put("revision", 9);
        bridge.onMessage(ChatGPTContentBridge.NATIVE_APP, composer, trusted);
        check(events.size() == 3 && events.get(2).composerFocused && events.get(2).composerDirty,
                "passive composer safety metadata accepted");
        check(events.get(2).mainComposer && !events.get(2).composing && events.get(2).attachmentActive,
                "passive composer state is metadata only");
        check(!composer.has("text") && !composer.has("content"), "payload carries no prompt content");

        WebExtension.Port stalePort = new WebExtension.Port(
                new WebExtension.MessageSender(stale, "https://chatgpt.com/c/abc", true),
                ChatGPTContentBridge.NATIVE_APP);
        bridge.onConnect(stalePort);
        check(stalePort.disconnected, "stale-session port disconnected");

        WebExtension.Port trustedPort = new WebExtension.Port(trusted, ChatGPTContentBridge.NATIVE_APP);
        bridge.onConnect(trustedPort);
        check(!bridge.requestPing(), "new port requires a fresh compatible READY");
        bridge.onMessage(ChatGPTContentBridge.NATIVE_APP, ready(), trusted);
        check(bridge.requestPing(), "trusted current-session port accepts health PING");
        check(trustedPort.lastPostedMessage != null
                        && "PING".equals(trustedPort.lastPostedMessage.optString("type", "")),
                "PING has no editor payload");
        check(bridge.requestComposerState(), "trusted current-session port accepts passive state sync");
        check(trustedPort.lastPostedMessage != null
                        && "SYNC_COMPOSER_STATE".equals(trustedPort.lastPostedMessage.optString("type", ""))
                        && !trustedPort.lastPostedMessage.has("generation")
                        && !trustedPort.lastPostedMessage.has("command"),
                "state sync has no keyboard/editor command metadata");

        bridge.attachSession(stale);
        check(!trustedPort.disconnected, "stale attach callback must not disconnect current valid port");
        trustedPort.disconnect();
        check(!bridge.requestPing(), "disconnected port is cleared");

        System.out.println("ChatGPTContentBridgeTest OK");
    }
}
