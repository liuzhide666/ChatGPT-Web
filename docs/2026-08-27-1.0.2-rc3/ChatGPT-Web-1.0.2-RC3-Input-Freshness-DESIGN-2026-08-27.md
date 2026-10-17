# ChatGPT Web 1.0.2 RC3 — Composer Input + Freshness/Cache Design

Date: 2026-08-27
Status: Approved for implementation
Baseline: 1.0.2 RC2
Target identity: versionName 1.0.2, versionCode 103, GeckoView 153.0.20260810162159

## Goal

Make Enter semantics deterministic across ChatGPT conversations and keyboard types while preserving Chinese IME composition, Shift+Enter newline behavior, current login/cache/profile data, Fresh Home semantics, bounded long-conversation recovery, upload/download/microphone behavior, and the 1.0.1 Gecko/native payload.

## Research constraints

1. GeckoView SessionTextInput handles both Android InputConnection and key events; therefore one Android-only path cannot reliably normalize all keyboards.
2. GeckoView WebExtensions are the preferred mechanism for interacting with web content and support bidirectional native messaging via runtime.connectNative / WebExtension.Port.
3. Gecko reload has an explicit BYPASS_CACHE flag; RC3 must not use it. Normal reload/load must preserve Gecko's shared profile and HTTP cache.
4. Current ChatGPT DOM evidence consistently identifies the visible composer as `#prompt-textarea` (ProseMirror/contenteditable) and send as `button[data-testid="send-button"]` / `#composer-submit-button` inside the same form. These selectors are treated as narrow operational evidence, not a broad page contract.
5. `beforeinput` is the web-standard interception point for editable DOM mutations and exposes inputType such as insertParagraph/insertLineBreak.

## Architecture

### A. ComposerInputRouter — web-semantic authority

The built-in extension owns send-vs-newline semantics only when a validated main composer is focused.

Validated composer requirements:
- current focused element is `#prompt-textarea`, or a very narrow textarea fallback named `prompt-textarea`;
- element is visible and editable;
- nearest enclosing form contains an enabled, visible ChatGPT send control identified by `data-testid=send-button`, `#composer-submit-button`, or `aria-label="Send prompt"`;
- no global button fallback is allowed.

Event routing:
- hardware `keydown` plain Enter, not repeat, no Shift/Ctrl/Alt/Meta, not composing: click the same-form enabled send control and prevent default only after the send target is validated;
- Shift+Enter: allow newline and arm a short `allowLineBreakUntil` guard so the following beforeinput is not reinterpreted as soft-keyboard Enter;
- `beforeinput` insertParagraph/insertLineBreak with no preceding Shift guard and not composing: submit when the same-form send control is enabled; otherwise allow the page's native behavior;
- compositionstart..compositionend always blocks custom submit;
- non-main editable areas are never intercepted.

No prompt/answer text is sent to Android. Diagnostics contain only route/source/result booleans/enums.

### B. Native IME semantic fallback

ChatGPTGeckoView may wrap Gecko's InputConnection only to observe `performEditorAction`. It must never synthesize KEYCODE_ENTER and must never intercept commitText, setComposingText, deleteSurroundingText, or other text operations.

A native editor action may request `SUBMIT_COMPOSER` only when the bridge's latest trusted state says:
- main composer focused;
- not composing;
- send control ready.

The request travels through the session-specific WebExtension.Port established by `runtime.connectNative`. The port is accepted only for the current GeckoSession, top-level trusted `https://chatgpt.com` sender, and expected native app name. On disconnect/session replacement, the native port is discarded.

If no valid port/composer state exists, the InputConnection delegates unchanged to Gecko.

### C. Lightweight bridge / performance

Remove the whole-document MutationObserver. It is unnecessary for page liveness and can amplify work in very long conversations.

Bridge signals:
- READY once per document;
- HEARTBEAT every 10 seconds while document is visible;
- immediate HEARTBEAT on visibilitychange -> visible;
- COMPOSER_STATE on focusin/focusout/input/compositionstart/compositionend;
- INPUT_ROUTE only for metadata diagnostics.

Use `ensureBuiltIn(location, extensionId)` with extension version `1.0.3`: GeckoView avoids reinstalling an already-current built-in extension but updates when the bundled version differs. Page loading must not wait on extension installation.

### D. Cache/load/refresh policy

Normal browsing:
- cold process start -> a fresh GeckoSession at `https://chatgpt.com/` using normal cache semantics;
- launcher re-entry -> replace session with fresh home using shared Gecko profile/cache;
- Recents / foreground resume -> reactivate same live session, no loadUri/reload;
- onStop -> setActive(false) unless an external Android flow is active;
- no SessionState restore of the previous conversation page;
- no cache/cookie/localStorage purge;
- no BYPASS_CACHE loads;
- no WorkManager/background refresh service;
- no timer-based reload solely because time passed.

Long-conversation health:
- health is armed only by a trusted bridge signal from the current session;
- STALE_AFTER_MS = 35,000;
- first stale incident -> one ordinary cache-respecting soft reload;
- wait at least SOFT_GRACE_MS = 20,000 after soft recovery before considering hard recovery, and never escalate while loading;
- if still stale -> one hard GeckoSession replacement to last trusted ChatGPT URI, reusing the same GeckoRuntime/profile/cache;
- wait at least HARD_GRACE_MS = 30,000 before showing manual recovery UI;
- any real trusted bridge signal resets the incident;
- composer focus/draft/composition, recording, external flow, background, and active page load block automatic recovery.

### E. Versioning and release

- versionName remains 1.0.2;
- versionCode becomes 103 to make RC2 -> RC3 an unambiguous upgrade;
- extension version becomes 1.0.3;
- release candidate name: ChatGPT-Web-1.0.2-RC3-vc103;
- same package `com.liuchenlin.chatgptweb` and same RSA-4096 release certificate;
- final Library delivery is true 7-Zip multi-volume `.7z.001...`; extracting only `.001` with all volumes present must directly produce the APK.

## Acceptance matrix

Input:
- new-chat home, ordinary historical conversation, long historical conversation;
- plain Enter sends;
- Shift+Enter inserts newline;
- Chinese/WeChat IME candidate confirmation never sends prematurely;
- physical keyboard plain Enter/Shift+Enter;
- search/rename/dialog/Writing Block/other contenteditable Enter remains untouched;
- blank/disabled send state does not trigger custom submission;
- attachment-only enabled send remains submit-capable;
- repeated keydown cannot double-send.

Lifecycle/performance:
- cold start fresh home;
- launcher re-entry fresh home;
- Recents preserves current live session;
- no normal lifecycle reload;
- no global MutationObserver;
- no cache bypass/clear;
- one soft + one hard maximum per incident with grace periods;
- stale old session/port cannot affect current session;
- extension update from RC2 cannot leave old content.js active.

Release:
- Python/Java host tests green;
- API37 compile and minSdk26 NewApi lint green;
- actual GeckoView 153 ABI verified for all newly used APIs;
- D8 app DEX contains no host stubs;
- v2/v3 signature, same cert, RSA4096, 16KiB alignment;
- resources.arsc, 39 native .so, classes.dex/classes2/classes4/classes5 byte-identical to 1.0.1 canonical;
- approved payload diff only manifest/classes3.dex/bridge assets;
- true 7z multi-volume round-trip byte-identical;
- no Formal promotion without target-device matrix.
