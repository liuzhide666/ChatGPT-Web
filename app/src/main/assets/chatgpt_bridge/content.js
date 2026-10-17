(() => {
  "use strict";

  const NATIVE_APP = "chatgptHealth";
  const EXTENSION_VERSION = "1.1.2";
  const PROTOCOL_VERSION = 3;
  const NATIVE_RECONNECT_MS = 1000;
  const adapter = globalThis.ChatGPTWebSiteAdapter;
  if (!adapter) return;

  let revision = 0;
  let composing = false;
  let nativePort = null;
  let nativeReconnectScheduled = false;
  let lastComposerStateKey = "";

  const send = (type, extra = {}) => {
    try {
      const pending = browser.runtime.sendNativeMessage(NATIVE_APP, {type, revision, ...extra});
      if (pending && typeof pending.catch === "function") pending.catch(() => {});
    } catch (_) {}
  };

  const announceReady = () => send("READY", {
    extensionVersion: EXTENSION_VERSION,
    protocolVersion: PROTOCOL_VERSION
  });

  const composerStateKey = (state) => [
    state.focused, state.dirty, state.composing, state.attachmentActive, state.mainComposer
  ].map((value) => value ? "1" : "0").join("");

  const sendComposerState = (force = false) => {
    const state = adapter.snapshot(composing);
    const key = composerStateKey(state);
    if (!force && key === lastComposerStateKey) return;
    lastComposerStateKey = key;
    send("COMPOSER_STATE", state);
  };

  const sendBlurredComposerState = () => {
    const state = adapter.snapshot(composing);
    state.focused = false;
    const key = composerStateKey(state);
    if (key !== lastComposerStateKey) {
      lastComposerStateKey = key;
      send("COMPOSER_STATE", state);
    }
  };

  document.addEventListener("focusin", () => sendComposerState(), true);
  document.addEventListener("focusout", () => {
    sendBlurredComposerState();
    setTimeout(() => sendComposerState(), 0);
  }, true);
  document.addEventListener("input", () => {
    revision += 1;
    sendComposerState();
    setTimeout(() => sendComposerState(), 0);
  }, true);
  document.addEventListener("compositionstart", () => {
    composing = true;
    sendComposerState();
  }, true);
  document.addEventListener("compositionend", () => {
    composing = false;
    revision += 1;
    sendComposerState();
    setTimeout(() => sendComposerState(), 0);
  }, true);

  const scheduleNativeReconnect = () => {
    if (nativePort || nativeReconnectScheduled || document.visibilityState !== "visible") return;
    nativeReconnectScheduled = true;
    setTimeout(() => {
      nativeReconnectScheduled = false;
      connectNativePort();
    }, NATIVE_RECONNECT_MS);
  };

  const connectNativePort = () => {
    if (nativePort) return;
    try {
      const port = browser.runtime.connectNative(NATIVE_APP);
      nativePort = port;
      announceReady();
      port.onMessage.addListener((message) => {
        if (!message || !message.type) return;
        if (message.type === "PING") {
          send("PONG");
        } else if (message.type === "SYNC_COMPOSER_STATE") {
          sendComposerState(true);
        }
      });
      port.onDisconnect.addListener(() => {
        if (nativePort === port) nativePort = null;
        scheduleNativeReconnect();
      });
    } catch (_) {
      nativePort = null;
      scheduleNativeReconnect();
    }
  };

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      connectNativePort();
      announceReady();
      sendComposerState(true);
    }
  }, true);

  connectNativePort();
  announceReady();
  sendComposerState(true);
})();
