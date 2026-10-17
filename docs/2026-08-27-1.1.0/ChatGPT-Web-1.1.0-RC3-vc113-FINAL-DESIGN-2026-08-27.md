# ChatGPT Web 1.1.0 RC3 / vc113 — Final Design

## Keyboard Transparent + Persistent Browser Runtime

**Date:** 2026-08-27  
**Status:** APPROVED DESIGN — implementation divided into three conversation phases  
**App identity:** `com.liuchenlin.chatgptweb` / `ChatGPT Web`  
**versionName:** `1.1.0`  
**versionCode:** `113`  
**Built-in WebExtension version:** `1.1.2`  
**Native/Web bridge protocol:** `3`  
**GeckoView:** `153.0.20260810162159`  
**minSdk / targetSdk / compileSdk:** `26 / 28 / 37`  
**Java:** `17`

---

## 1. Decision

RC2/vc112 failed the actual target-device requirement: application-owned Enter/Shift+Enter routing did not reproduce the input behavior observed in the user's 卓易通 + GeckoView + ChatGPT environment. The project therefore stops trying to infer, synthesize, deduplicate, submit, or insert line breaks on behalf of Gecko/ChatGPT.

RC3/vc113 adopts **Approach A: Keyboard Transparent + passive browser-health bridge**.

The new architectural invariant is:

> **ZERO KEYBOARD SEMANTIC OWNERSHIP.**

The Android shell must not decide what Enter, Shift+Enter, IME SEND/DONE, committed newline text, DOM `keydown`, or DOM `beforeinput` means. The same device/input-method/browser combination must be allowed to reach GeckoView and ChatGPT's own Web application unchanged. If the original ChatGPT website currently treats Enter and Shift+Enter as line breaks in that environment, this app must naturally behave the same way. If the website later changes its own input policy, the shell should follow without another native input-policy release.

This is deliberately different from mapping both Enter and Shift+Enter to an app-defined `INSERT_LINE_BREAK`: that would still retain semantic ownership and the same class of cross-layer failure.

---

## 2. Versioning and promotion boundary

vc112 remains preserved as failed target-device evidence. It is not overwritten, renamed, or promoted.

Any source modification after the vc112 target-device failure requires:

- `versionCode=113`;
- WebExtension `1.1.2`;
- bridge protocol `3`;
- a complete clean-source and binary release gate;
- a new target-device A/B gate.

The canonical project pointer remains `1.0.3 Formal` until the exact vc113 APK bytes pass the new target-device gate. Formal promotion must reuse the exact tested APK bytes; rebuilding only to change wording is forbidden.

---

## 3. Responsibility split

### 3.1 Android shell owns

- GeckoRuntime / GeckoSession lifecycle;
- activity/window/system-bar behavior;
- downloads, attachments, file chooser and Android permissions;
- media / recording integration;
- network observation;
- trusted-origin policy;
- browser/page health observation;
- conservative browser recovery;
- recovery UI and browser diagnostics.

### 3.2 GeckoView + ChatGPT Web own

- every keyboard event;
- IME composition;
- text editing;
- Enter/Shift+Enter behavior;
- IME SEND/DONE behavior;
- send-button behavior;
- selection, cursor movement, deletion and undo/redo;
- editor DOM mutation.

### 3.3 WebExtension owns only passive observation

Protocol 3 may observe and report:

- bridge READY / PONG;
- top-level trusted state as already constrained by Native validation;
- visibility;
- composer safety snapshot needed only to prevent destructive recovery;
- optional navigation/page diagnostic state.

It must not execute editor or send actions.

---

## 4. Keyboard neutrality requirements

RC3 must remove all production paths that implement app-defined Enter semantics.

### 4.1 Remove native semantic input subsystem

Delete the production responsibilities implemented by:

- `NativeKeyIngress`;
- `InputIntentTransaction`;
- `InputEventDeduplicator`;
- `ComposerInputRouter`;
- `InputRoutingState` where it exists solely for semantic input ownership;
- `ChatGPTInputConnection` if no non-input feature requires it;
- semantic portions of `InputTraceBuffer` and `InputDiagnosticsDialog`.

The preferred `ChatGPTGeckoView.onCreateInputConnection()` behavior is to call and return `super.onCreateInputConnection(outAttrs)` without wrapping the returned connection and without altering EditorInfo for app-defined send/newline semantics.

`ChatGPTGeckoView.dispatchKeyEvent()` must not branch on `KEYCODE_ENTER`; ideally the override is removed entirely unless unrelated behavior requires it.

`MainActivity.dispatchKeyEvent()` must not consume, transform or route Enter-family events. If retained only for generic user-activity observation, it must perform generic activity bookkeeping and then call `super.dispatchKeyEvent(event)` for every key.

### 4.2 Remove IME semantic interception

Production code must not:

- consume `sendKeyEvent(KEYCODE_ENTER)`;
- special-case `commitText("\\n")`, `"\\r"` or `"\\r\\n"`;
- convert `performEditorAction(SEND/DONE)` into Native submit;
- fabricate a Shift modifier or create/change KeyEvents;
- modify `imeOptions` to force app-defined submit/newline behavior unless Gecko itself requires a generic compatibility setting and an independent non-semantic justification is documented.

### 4.3 Remove Web keyboard control

`content.js` must not register app-owned keyboard/beforeinput handlers for semantic routing. Remove:

- `keydown` Enter fallback;
- `beforeinput` Enter/line-break authority;
- `SUBMIT_COMPOSER` command execution;
- `INSERT_LINE_BREAK` command execution;
- command ACK/effect-verifier machinery tied to editor execution.

`site_adapter.js` becomes read-only. Remove:

- `submit()` executor;
- `insertLineBreak()` executor;
- `observeEditorEffect()`;
- send-control selectors if not needed by passive safety observation.

### 4.4 Remove native line-break UI

Delete the Native `↵ 换行` control and its adaptive visibility logic. It is no longer needed because the app must not maintain a second input system.

---

## 5. Bridge protocol 3

The built-in extension version must be `1.1.2`. READY must report extension version and `protocolVersion=3`.

Native must treat a stale extension/protocol as **bridge degraded**, not as authorization to run old semantic commands.

Protocol 3 has no semantic command vocabulary. In particular, Native and Web must not expose or accept:

- `SUBMIT_COMPOSER`;
- `INSERT_LINE_BREAK`;
- editor-effect events used to certify an input command;
- command IDs for keyboard/editor actions.

Allowed request/response operations are intentionally narrow:

- `PING` / `PONG`;
- `SYNC_COMPOSER_STATE` when needed to refresh passive recovery blockers;
- passive READY / COMPOSER_STATE / VISIBILITY-like telemetry.

The protocol may be reduced further during implementation if a message is proven unnecessary; it must not grow a keyboard executor again.

---

## 6. Passive composer safety state

Composer observation survives only for recovery safety. The browser supervisor may need to know whether a destructive page/session recovery risks losing user work.

Useful safety inputs:

- `composerFocused`;
- `composerDirty`;
- `composerComposing` (Web-observed only; no custom Native IME ownership is required);
- attachment/file flow state;
- recording/media state;
- external/OAuth flow state.

`sendReady` must not be treated as an independent reason to preserve a page. It does not reliably mean that unrecoverable user state exists.

Passive composer state must never be reused to interpret a keyboard event.

---

## 7. Persistent browser runtime

The main engineering focus after keyboard removal is keeping a healthy Gecko page alive and avoiding unnecessary refreshes.

### 7.1 Stable state has no permanent heartbeat

When the browser is stable, there is:

- no Web `setInterval` heartbeat;
- no Native fixed-period polling;
- no "N seconds of silence means failure" policy;
- no reload caused solely by user inactivity.

Timers exist only for a concrete operation already in progress, for example:

- a PING awaiting PONG;
- a rebind awaiting READY/PONG;
- a reload awaiting navigation/FCP completion.

Once that operation resolves or is abandoned, its deadline is cancelled.

### 7.2 Bridge health is not page health

Bridge failure alone must not reload a usable page. If the WebExtension is disconnected while Gecko content remains visibly/operationally usable, the state becomes `DEGRADED_STABLE` and Native may attempt bridge rebind without touching the page.

### 7.3 Real browser/page failure evidence

Destructive recovery requires either:

1. a strong Gecko/browser failure signal; or
2. a quorum of independent weak failure signals.

Weak signals that must not individually reload a page include:

- bridge timeout;
- paint reset;
- slow-script callback;
- long idle;
- absence of composer telemetry.

### 7.4 Paint semantics

- `onPaintStatusReset()` sets `paintValid=false`;
- `onFirstContentfulPaint()` sets `paintValid=true` for the current navigation generation;
- a reset followed quickly by FCP is normal and requires no recovery.

### 7.5 Slow-script semantics

A recent slow-script signal is evidence that content execution is still alive but delayed. It must lengthen non-destructive grace/diagnostic handling; it must never independently trigger reload.

---

## 8. Proposed health state machine

The target state model is:

`STABLE -> SUSPECT -> PROBING -> BRIDGE_RECOVERY -> {STABLE | DEGRADED_STABLE | SOFT_RECOVERY -> HARD_RECOVERY -> USER_RECOVERY}`

### STABLE

No permanent timer. Normal browsing continues.

### SUSPECT

Entered only because of a concrete abnormal signal, not elapsed idle time.

### PROBING

Send on-demand PING if the bridge can be meaningfully probed. PING is non-destructive.

### BRIDGE_RECOVERY

Attempt extension/session rebind. If the page remains usable but the bridge is still unavailable, settle in `DEGRADED_STABLE` rather than reloading.

### SOFT_RECOVERY

Allowed only after failure evidence and all recovery safety blockers permit it. Use `session.reload()` / default-cache semantics.

### HARD_RECOVERY

Create a replacement GeckoSession using the existing GeckoRuntime/profile and last trusted URI. Default cache only.

### USER_RECOVERY

Automatic recovery stops. UI exposes retry, explicit one-shot force-refetch, and browser diagnostics.

---

## 9. Cache invariants

Automatic paths always use Gecko's normal/default cache semantics.

Forbidden automatic operations:

- `LOAD_FLAGS_BYPASS_CACHE`;
- cache purge;
- Cookie purge;
- localStorage/profile purge;
- service-worker purge;
- login/session-state reset.

The only bypass-cache path is a clearly labelled **user-initiated one-shot force refetch** from Recovery UI. After that request, all later navigation/recovery returns to default cache behavior.

Network offline -> online is not an automatic page reload trigger.

---

## 10. Lifecycle invariants

### Recents return

Reuse the existing GeckoSession and preserve current conversation, scroll and JS state. No `loadUri()` and no reload merely because the Activity became visible again.

### Background -> foreground

Resume the live session. A probe may occur only when there is actual reason to suspect degradation; background duration alone does not authorize a reload.

### Explicit launcher re-entry

Keep the project's established fresh-home policy unless a later product decision changes it explicitly.

### Network restore

Update network state and clear/re-evaluate an existing incident. Do not reload a healthy ChatGPT page simply because connectivity returned.

---

## 11. Navigation generation

Input generation is removed with the semantic input layer. A separate **navigation generation** remains useful and must be scoped only to browser/page lifecycle.

Each top-level navigation/replacement increments a generation. Asynchronous FCP/session/page callbacks from an older generation must not mark the new page healthy.

---

## 12. Browser Diagnostics

`Input Diagnostics` is replaced by `Browser Diagnostics`.

It may show metadata such as:

- app version;
- extension version / protocol;
- current trusted URI classification;
- navigation generation;
- session state;
- loading state;
- paint-valid state / last FCP;
- bridge connection / last PONG;
- network state;
- current recovery state / last recovery reason;
- cache mode;
- passive safety blockers.

It must not contain prompt text, candidate text, selection text, clipboard, HTML, editor value/textContent, Cookies, auth tokens, storage values, or keyboard scan/device/transaction traces.

---

## 13. Test philosophy change

The critical new test is not "does Native route Shift+Enter correctly?". It is:

> **Can the build prove that Native/Web shell code has no semantic keyboard control path?**

### Keyboard Neutrality Gate

Production source must contain no app-owned semantic path for:

- `KEYCODE_ENTER` routing/consumption;
- Shift+Enter routing;
- semantic `sendKeyEvent` interception;
- newline `commitText` conversion;
- IME action submit interception;
- DOM keyboard/beforeinput routing;
- Native submit/line-break command;
- Web submit/line-break executor;
- editor-effect verifier for a Native input command;
- synthetic KeyEvent creation/change;
- Native line-break fallback UI.

Historical tests that assert those old behaviors must be retired or rewritten into negative neutrality contracts. They must not simply be deleted without replacement.

---

## 14. Target-device A/B gate

The target-device input criterion changes fundamentally.

Use the same 卓易通 device, Gecko/website account state and input method to compare:

1. original ChatGPT website behavior;
2. ChatGPT Web vc113 behavior.

Compare:

- Enter;
- Shift+Enter;
- Chinese candidate + Enter;
- backspace/delete;
- cursor movement/selection;
- multiline paste;
- undo/redo;
- software IME action.

The requirement is **behavioral parity with the original website on the same target environment**, not a hard-coded expectation that Enter must send.

Browser/lifecycle Gate separately validates long-history idle, scrolling, Recents resume, background/foreground, offline/online, attachment/upload, recording, OAuth/external flow, true page failure, soft/hard recovery and explicit force-refetch.

---

## 15. Three-conversation implementation partition

### Conversation Part 1 — Keyboard Transparency Foundation

Deliver a working vc113 source tree that:

- freezes vc112 target-device failure evidence;
- changes app/extension/protocol identity to `113 / 1.1.2 / 3`;
- adds RED Keyboard Neutrality contracts;
- removes Native Activity/View/InputConnection keyboard semantics;
- removes Native semantic command pipeline, input trace and line-break UI;
- removes Web keyboard/beforeinput semantic routing and editor executors;
- converts Site Adapter to read-only safety observation;
- keeps existing browser-health behavior functionally intact for now except changes strictly required by protocol/input removal;
- passes the Part-1 focused Python/Java/Node regression suite.

No deep browser-health algorithm redesign is allowed in Part 1.

### Conversation Part 2 — Persistent Browser / Cache / Recovery Refactor

Starting from the Part-1 tree:

- replace input diagnostics with Browser Diagnostics;
- remove remaining sendReady/input-generation coupling from health;
- introduce navigation generation;
- implement timerless STABLE and operation-scoped deadlines;
- decouple bridge failure from page failure;
- implement suspect/probe/rebind/degraded/soft/hard/user state machine;
- implement strong-failure / weak-signal quorum;
- lock cache/network/lifecycle invariants;
- migrate Java Host and Python health tests.

No APK promotion/build packaging occurs yet.

### Conversation Part 3 — Integration, Release and Target-Device Package

Starting from Part 2:

- full source regression and clean-source archive;
- API37 compile, NewApi lint, real Gecko153 ABI;
- release-only app DEX and binary payload allowlist;
- same signing identity, v2/v3, 16 KiB and native-lib audit;
- deterministic clean-source rebuild and byte-identical APK proof;
- true 7z multivolume delivery with `.001` entry point;
- RC3 release review, final pre-device gate and A/B target-device Gate document;
- Library upload as candidate only;
- keep canonical pointer at 1.0.3 until target-device pass.

---

## 16. Non-goals

RC3 will not:

- recreate desktop ChatGPT keyboard shortcuts;
- guarantee hardware Shift semantics for an IME/platform that does not expose them;
- execute prompt text from Native;
- add periodic heartbeat merely for diagnostic comfort;
- auto-clear browser data to repair transient failures;
- redesign unrelated permissions/download/media features.

---

## 17. Definition of success

RC3 succeeds only when all of the following are true:

1. static and behavioral gates prove keyboard semantic neutrality;
2. text editing is delegated to Gecko/ChatGPT without wrapper interference;
3. existing non-input app capabilities remain functional;
4. healthy pages are not refreshed because of idle time or bridge-only failure;
5. automatic recovery preserves profile/cache and is bounded by real failure evidence and safety blockers;
6. clean-source and binary gates are reproducible;
7. target-device A/B input behavior matches the original ChatGPT website on the same 卓易通 environment;
8. the exact target-tested APK bytes, and only those bytes, may later be promoted Formal.
