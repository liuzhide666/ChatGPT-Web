# ChatGPT Web 1.1.0 Formal — Architecture Amendment

## Purpose

This amendment tightens the existing 1.1.0 RC1 architecture before Formal promotion. RC1 remains diagnostic evidence; Formal must not infer ChatGPT composer ownership from generic multiline-editor metadata and must not treat Web-only composition state as sufficient protection for Android IME control signals.

## 1. Strict composer ownership generation

Native input authority requires all of the following at the moment an input signal is routed:

- trusted top-level `https://chatgpt.com` page;
- a live Gecko `InputConnection` generation `G`;
- a WebExtension `SYNC_COMPOSER_STATE(G)` round trip completed on the current trusted content port;
- the returned snapshot confirms `mainComposer=true` and `focused=true`;
- `composerFocusGeneration == G`.

`EditorInfo` multiline flags remain diagnostic metadata only. They never authorize submit or line-break ownership.

On every new InputConnection, session replacement, page transition, composer blur, or mismatched generation, ownership is invalid until a new matching sync completes. Failure to prove ownership degrades to Gecko/DOM fallback rather than consuming an unrelated editor event.

## 2. Native composition lifecycle

`ChatGPTInputConnection` continues to delegate ordinary text editing unchanged, but observes control metadata for:

- `setComposingText` -> native composition active after successful delegation;
- non-empty `setComposingRegion` -> native composition active;
- zero-length `setComposingRegion` -> native composition finished;
- `finishComposingText` -> native composition finished;
- successful ordinary `commitText` -> native composition finished, consistent with Android's documented commit semantics.

No composing text, prompt text, selection content, clipboard content, cookie or token is recorded. Input routing blocks semantic submit/line-break whenever either native composition or Web composition is active.

## 3. Deterministic software-keyboard newline escape hatch

A lightweight native `换行` button is visible only while a generation-confirmed ChatGPT main composer is focused and composition is inactive. It emits the same semantic `LINE_BREAK_COMMAND` through the Native Router; it never synthesizes an Android Enter KeyEvent.

The button is session-adaptive: once a real native Shift+Enter path is observed and successfully applied end-to-end, the fallback button may hide for the remainder of that app session. This preserves a deterministic newline path for IMEs that never expose Shift while avoiding permanent UI duplication when native Shift is proven usable.

## 4. Network-aware destructive recovery

Network availability is a health signal, not a reason to clear cache. `BrowserHealthSupervisor` may still attempt non-destructive bridge rebind while offline, but `SOFT_RELOAD`, `HARD_REPLACE`, and automatic recovery escalation are blocked while the default network is known unavailable. A network transition resets the current health incident; when connectivity returns, the bridge is pinged/rebound before any destructive recovery is reconsidered.

Normal browsing and recovery continue to use Gecko default cache semantics. No Cookie/storage/cache purge and no bypass-cache flags are introduced.

## 5. Reproducible Formal source gate

The source tree must contain one canonical gate entry point that records exact commands for:

- Python contracts;
- Java 17 host tests against Android API 37;
- Node Site Adapter + content bridge simulation with both JS inputs explicitly supplied;
- Android/API compatibility checks used by the packaging pipeline.

A Formal claim is valid only from a clean extracted source archive rerunning this gate. Host/source gates do not substitute for the target-device matrix.

## 6. Promotion boundary

The project may produce a `1.1.0 Formal Candidate` APK and signed release artifacts after all reproducible source/package gates pass. The canonical baseline must not be switched from `1.0.3 Formal` to `1.1.0 Formal` until the target 卓易通 device matrix verifies at minimum physical Enter, physical Shift+Enter, Chinese/WeChat IME composition, software-keyboard Enter/newline fallback, long-conversation bridge-only failure, offline->online, background->foreground, attachment/media/external flows, and in-place upgrade.

## 7. Correlated semantic command ACK

Native semantic composer actions use `{type: COMMAND, commandId, command}`. `postMessage()` success proves only transport enqueue and is recorded as `REQUESTED`. The content script executes the Site Adapter operation and returns `{type: COMMAND_ACK, commandId, command, result}` on the trusted Port. Only a matching pending command may become `ACK_APPLIED`; rejection, timeout, disconnect and generation/page invalidation remain explicit trace states.

## 8. Gecko content-liveness diagnostics

`onPaintStatusReset` contributes fresh content-liveness evidence. `onSlowScript` records only a metadata signal (never the script filename/URL) and returns `SlowScriptResponse.CONTINUE`; it proves the content process is alive but may have a blocked JS event loop, and therefore never directly authorizes reload. `onCrash/onKill` remain dedicated failure paths. The release DEX pipeline must verify these method descriptors against the actual packaged GeckoView 153 DEX, not only Host stubs.
