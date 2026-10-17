# ChatGPT Web 1.1.0 RC2 / vc112 — Release Review

**Date:** 2026-08-27  
**Status:** **RC2 — package-audited target-device candidate, not Formal**  
**Canonical baseline remains:** `1.0.3 Formal`  
**Superseded test candidate:** `1.1.0 / vc111` remains preserved as evidence of a known target-device Shift+Enter defect.  
**GeckoView:** `153.0.20260810162159` frozen.

## 1. Release identity

- Package: `com.liuchenlin.chatgptweb`
- Application label: `ChatGPT Web`
- `versionName=1.1.0`
- `versionCode=112`
- Built-in WebExtension version: `1.1.1`
- Native/Web bridge protocol: `2`
- minSdk / targetSdk / compileSdk: `26 / 28 / 37`
- Java: `17`
- Canonical signer certificate SHA-256: `23130c6a6b7b0969d4c76ac9e7278f9b4c26c77a897d0c4a0de514a9c3a4e70c`

RC2 keeps package, signer, profile, Gecko runtime/profile/cache semantics and user-facing version name unchanged. If any source change follows target-device testing, `versionCode` must advance to 113; vc112 may be promoted only without rebuilding its APK bytes.

## 2. Input architecture

RC2 no longer treats Shift+Enter as an isolated Web patch. `Enter` and `Shift+Enter` are two semantic outcomes of one `InputIntentTransaction`. The common path is:

`real input signal -> generation ownership -> native/web composition guards -> modifier classification -> cross-layer deduplication -> semantic action -> correlated command -> executor -> effect verification -> ACK`.

### 2.1 Native ingress and deduplication

- `Activity.dispatchKeyEvent()` is the earliest Enter-family KeyEvent ingress.
- `InputConnection.sendKeyEvent()` and `ChatGPTGeckoView.dispatchKeyEvent()` remain lower-priority transport paths.
- DOM `keydown` remains compatibility fallback only; DOM `beforeinput` has no submit authority.
- `InputEventDeduplicator` correlates Activity/InputConnection/View copies by physical-key gesture identity and generation. A Native-owned DOWN owns its matching UP, and repeat events cannot create extra semantic commands.
- No production source constructs a synthetic Android `KeyEvent`; real events are observed/consumed only.

### 2.2 Semantic split

After all shared guards:

- plain Enter -> `SUBMIT`, requiring `sendReady=true`;
- Shift+Enter -> `INSERT_LINE_BREAK`, not requiring send-button availability;
- Ctrl/Alt/Meta Enter, repeat, stale generation, untrusted page or active composition -> no semantic command.

Native and Web composition states are separate safety inputs. Generic multiline `EditorInfo` is diagnostic only and never grants ChatGPT composer authority.

## 3. Phase-A target-device observability

`InputTraceBuffer` is a 64-entry metadata-only ring. Its schema contains only primitives and enums; Host tests reject free-form `String`, `CharSequence` or `Object` payload fields/constructor parameters. It records boundaries including:

`ACTIVITY_KEY`, `GECKOVIEW_KEY`, `IC_SEND_KEY`, `IC_COMMIT`, `IME_ACTION`, `DOM_KEYDOWN`, `ROUTER`, `COMMAND_REQUEST`, `COMMAND_ACK`, `EDITOR_EFFECT`.

The trace may contain transaction id, key/action metadata, modifiers, native/Web composition flags, input/composer generation, lease validity, semantic action, command id, effect booleans and allowlisted result enums. It never stores prompt text, candidate text, committed text, clipboard contents, HTML, Cookie, tokens, localStorage or URL query/body.

A read-only in-app Input Diagnostics surface is available without ADB; long-pressing the native newline control also exposes it while the control is present.

## 4. Line-break effect verification

vc111 could still equate adapter/executor success with applied editor state. RC2 separates:

`REQUESTED -> EXECUTOR_ACCEPTED -> EDITOR_EFFECT_OBSERVED -> APPLIED`.

For `INSERT_LINE_BREAK`, a matching `COMMAND_ACK(result=APPLIED)` is emitted only after an independent editor-effect observer sees one persistent logical editor mutation and zero submit side effect. Node regression explicitly covers:

- executor returns success but editor state does not change -> not APPLIED;
- transient change is rolled back before observation -> not APPLIED;
- persistent single line break with zero submit -> APPLIED.

`SiteAdapterLineBreakExecutor` remains the default. Raw Gecko `InputConnection.commitText("\n",1)` is not the default executor and is not eligible for Formal use without separate target-device composition/undo/cursor/multiline validation.

## 5. Built-in extension upgrade correctness

The WebExtension version advances from `1.1.0` to `1.1.1`, and READY carries `protocolVersion=2`. This is required because GeckoView `ensureBuiltIn()` does not need to reinstall an already-current built-in extension. Native semantic commands are disabled until an exact compatible READY handshake is observed on the current trusted session/port; every newly bound port requires a fresh READY.

Legacy `HEARTBEAT` is no longer a valid Native bridge event. Page health is not authorized by a permanent Web timer.

## 6. Browser health and recovery

RC2 uses event-driven state plus one meaningful deadline:

`events -> deadline -> PING -> PONG or rebind -> only then destructive recovery if both bridge and content evidence remain stale`.

Recovery stages are:

1. `PROBE` — PING only;
2. `REBIND_BRIDGE` — non-destructive port rebind + state sync;
3. `SOFT_RELOAD` — ordinary cache-respecting reload;
4. `HARD_REPLACE` — new GeckoSession, same runtime/profile, trusted URI, default cache;
5. `RECOVERY_UI` — automation stops.

`onPaintStatusReset()` sets `paintValid=false`; it is not positive content-freshness evidence. A later `onFirstContentfulPaint()` restores valid-paint evidence. `onSlowScript()` is metadata-only diagnostic evidence that the content process is responsive enough to report a slow script; it cannot alone trigger reload.

Focused/dirty/composing/send-ready composer state, media, external flows, background, loading, untrusted top-level page and known offline state block destructive recovery as applicable. Non-destructive PING/rebind may still be used while a draft is protected or while offline.

## 7. Cache and lifecycle policy

- normal browsing: default Gecko cache;
- Recents return: same live GeckoSession, no time-based reload;
- explicit Launcher re-entry: fresh ChatGPT home session, same runtime/profile/cache;
- automatic soft reload: default cache;
- automatic hard replacement: shared profile + default cache;
- Cookie/localStorage/service worker/profile: never automatically purged;
- `LOAD_FLAGS_BYPASS_CACHE`: available only from the explicit user action **强制重新获取页面资源**.

Java Host tests prove both one-shot bypass behavior on a live session and the no-session fallback. Python contracts prove automatic recovery contains no bypass flag.

## 8. Source gate

Frozen RC2 source gate passes:

- Python: **183 passed**;
- Java 17 Host: **36 passed**;
- Node integrated input simulation: **PASS**;
- Node editor-effect verification simulation: **PASS**;
- Android API37 compile: **PASS**;
- minSdk26 NewApi lint: **PASS**;
- real packaged GeckoView 153 ABI: **PASS**.

The Host compiler is explicitly UTF-8 and no longer depends on machine locale. Host Gecko/JSON stubs were extended only after the exact Gecko153 APK ABI was verified (`reload(int)`, load flags, `Loader.uri/flags`) and Android `JSONObject.optInt` usage was confirmed.

## 9. Binary candidate audit

**APK:** `ChatGPT-Web-1.1.0-RC2-vc112.apk`  
**APK SHA-256:** `469343cd23129073c2f2735d6c218fb417a0c7c3da758fdb3e168aa04c7324ad`  
**APK size:** `527721224` bytes  
**Independent application DEX SHA-256:** `6f324503067bb07dd231c9ea4f084cb86ae2983b0a69ab8b34f34b36775c7138`  
**Application DEX size:** `148048` bytes

Verified:

- APK Signature Scheme v2: PASS;
- APK Signature Scheme v3: PASS;
- v1/JAR signature disabled;
- signer count: 1;
- signer: same canonical RSA-4096 release identity;
- 16 KiB alignment: PASS;
- `debuggable=false`, `allowBackup=false`, `usesCleartextTraffic=false`;
- activities/services/providers/receivers: `2 / 89 / 1 / 1`;
- permissions: 10, text-identical to vc111;
- native `.so`: 39, all ZIP STORED and byte-identical to vc111;
- DEX: 5;
- application DEX: 163 class definitions, all `com.liuchenlin.chatgptweb`, foreign=0;
- `resources.arsc`, `classes.dex`, `classes2.dex`, `classes4.dex`, `classes5.dex`: byte-identical to vc111;
- final APK `classes3.dex`: byte-identical to independently generated D8 output;
- bridge manifest/site adapter/content script: byte-identical to frozen RC2 source.

Relative to vc111, the complete non-signature payload difference allowlist is exactly:

1. `AndroidManifest.xml` — versionCode 112;
2. `classes3.dex` — RC2 application code;
3. `assets/chatgpt_bridge/manifest.json` — extension 1.1.1;
4. `assets/chatgpt_bridge/site_adapter.js` — effect observer;
5. `assets/chatgpt_bridge/content.js` — protocol 2, PING/event flow and correlated effect ACK.

There are no added or missing non-signature ZIP entries.

## 10. Runtime evidence boundary

Fresh ADB preflight in the build environment reports **0 online target devices**. Therefore the package is **not Formal**. Host/source/package evidence cannot prove how the actual 卓易通 + keyboard/IME + GeckoView + ChatGPT/ProseMirror chain transports Shift/modifiers and composition.

The exact remaining authority is the RC2 target-device Gate F. The same APK bytes with SHA-256 `469343cd...7324ad` must pass. A source change after any failure requires vc113; the failing vc112 APK remains preserved as evidence.
