# ChatGPT Web 1.0.2 RC3 Composer Input + Freshness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build RC3/vc103 with deterministic ChatGPT composer Enter semantics and lower-overhead, cache-friendly page freshness/recovery without regressing RC2 lifecycle, media, file, download, signing, or packaging behavior.

**Architecture:** The built-in ChatGPT WebExtension becomes the authoritative ComposerInputRouter for keydown/beforeinput and exposes a trusted bidirectional native port for a narrow performEditorAction fallback. Page health is simplified to a 10-second event-loop heartbeat with no whole-document MutationObserver, while the native PageHealthPolicy adds explicit stale/soft/hard grace windows and preserves Gecko's normal profile/cache semantics.

**Tech Stack:** Java 17, Android API 37 / minSdk 26 / targetSdk 28, GeckoView 153.0.20260810162159, WebExtension MV2 content script, Python + Java host tests, D8, Android build-tools 36.0.0, 7z DEFLATE multi-volume packaging.

**Spec:** `docs/2026-08-27-1.0.2-rc3/ChatGPT-Web-1.0.2-RC3-Input-Freshness-DESIGN-2026-08-27.md`

## Global Constraints

- package stays `com.liuchenlin.chatgptweb`; label stays `ChatGPT Web`.
- versionName stays `1.0.2`; versionCode becomes `103`.
- GeckoView stays exactly `153.0.20260810162159`.
- Java 17, minSdk 26, targetSdk 28, compileSdk 37.
- no Android WebView fallback, SpeechRecognizer legacy path, cache/cookie/storage purge, background refresh worker/service, SessionState restore, or Gecko upgrade.
- no synthetic `KEYCODE_ENTER`; no prompt/answer/cookie/token/localStorage transmission.
- normal resume never reloads; ordinary recovery never uses `LOAD_FLAGS_BYPASS_CACHE`.
- RC3 is not Formal without the target-device matrix.

---

### Task 1: Version and composer-routing contracts

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/assets/chatgpt_bridge/manifest.json`
- Create: `tests/test_103_input_router_contract.py`
- Modify: `tests/test_beta1_browserfit_contract.py`

**Interfaces:**
- Produces target identity vc103/versionName1.0.2 and extension version1.0.3.
- Defines static constraints for `ComposerInputRouter` behavior before production changes.

- [x] Write failing Python contracts asserting vc103, extension 1.0.3, no global MutationObserver, composer-scoped selectors, keydown/beforeinput/composition handling, no synthetic key token, and 10-second heartbeat.
- [x] Run the targeted tests and confirm failures are due to the missing RC3 behavior.
- [x] Change only versionCode/extension version needed for this task; keep tests for router RED until Task 2.
- [x] Re-run identity-specific assertions.

### Task 2: Web ComposerInputRouter

**Files:**
- Modify: `app/src/main/assets/chatgpt_bridge/content.js`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java`
- Modify: `tests/test_102_bridge_contract.py`
- Create/modify Java host bridge tests under `app/src/testHost/java/com/liuchenlin/chatgptweb/`.

**Interfaces:**
- Web -> native events: READY, HEARTBEAT, COMPOSER_STATE, INPUT_ROUTE.
- COMPOSER_STATE fields: `focused`, `dirty`, `composing`, `sendReady`, `mainComposer`.
- Native port command: `{type:"SUBMIT_COMPOSER"}`.

- [x] Add failing tests for exact main-composer scoping and metadata-only state.
- [x] Verify RED.
- [x] Implement `findMainComposer()`, same-form `findSendControl()`, visibility/enabled checks, plain Enter keydown submit, Shift+Enter bypass, beforeinput soft-keyboard submit, composition guards, repeat/modifier guards, and INPUT_ROUTE metadata.
- [x] Remove whole-document MutationObserver and DOCUMENT_ACTIVITY/QUIET traffic; implement 10-second visible heartbeat and visibility wake heartbeat.
- [x] Add `runtime.connectNative` port listener in content.js for `SUBMIT_COMPOSER`.
- [x] Extend native bridge event parsing with composing/sendReady/mainComposer and trusted port lifecycle validation.
- [x] Run targeted Python and Java host tests GREEN.

### Task 3: Native IME fallback without synthetic keys

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTGeckoView.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ImeActionPolicy.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java`
- Add Java host tests for IME routing.

**Interfaces:**
- `ImeActionPolicy.shouldRequestSubmit(int actionId, boolean mainComposerFocused, boolean composing, boolean sendReady): boolean`.
- `ChatGPTContentBridge.requestComposerSubmit(): boolean` posts `SUBMIT_COMPOSER` only through a validated current-session port.
- `ChatGPTGeckoView.setImeSubmitDelegate(...)` supplies current state and submit callback.

- [x] Write failing Java tests proving editor-action fallback is allowed only for validated, non-composing, send-ready main composer and never creates `KeyEvent`/`KEYCODE_ENTER`.
- [x] Verify RED.
- [x] Add an `InputConnectionWrapper` that overrides only `performEditorAction`; all text/composition methods remain inherited/delegated unchanged.
- [x] Return handled only when bridge accepted the semantic submit; otherwise delegate to Gecko.
- [x] Wire MainActivity's latest trusted composer state into ChatGPTGeckoView.
- [x] Run targeted Java + Python forbidden-symbol tests GREEN.

### Task 4: Cache-friendly freshness policy with grace windows

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/PageHealthPolicy.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java`
- Modify: `tests/test_102_health_contract.py`
- Modify/add `PageHealthPolicyTest.java` host tests.
- Create: `tests/test_103_cache_freshness_contract.py`

**Interfaces:**
- constants: `STALE_AFTER_MS=35000`, `SOFT_GRACE_MS=20000`, `HARD_GRACE_MS=30000`.
- recovery still exposes NONE/SOFT_RECOVERY/HARD_RECOVERY/SHOW_RECOVERY_UI.

- [x] Write failing policy tests for stale threshold, soft grace, hard grace, healthy-signal reset, loading/draft/composition/media/external/background guards.
- [x] Verify RED.
- [x] Implement timestamped staged policy.
- [x] Change MainActivity page-health poll cadence only if needed; do not add background work.
- [x] Keep `GeckoSessionController.softRecoverCurrentPage()` cache-respecting and hard recovery on shared runtime/profile; add explicit diagnostics proving normal/default cache semantics, without BYPASS_CACHE.
- [x] Add static tests asserting no cache clear, no bypass flag, no lifecycle reload, no MutationObserver, and no SessionState restore.
- [x] Run targeted policy + integration tests GREEN.

### Task 5: Extension installation/update and stale-session isolation

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java`
- Modify bridge Java host stubs/tests as required.

**Interfaces:**
- use `ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)` with manifest version1.0.3.
- current-session message and port validation remains mandatory.

- [x] Write failing tests that RC2's same-version extension is upgraded by version change but an already-current RC3 extension is not reinstalled every app start.
- [x] Verify RED against current `installBuiltIn` code.
- [x] Implement `ensureBuiltIn(location,id)` and retain attach-on-bind behavior.
- [x] Verify current-session/top-level/exact-origin validation for both one-off messages and ports; stale port must be disconnected/ignored.
- [x] Run targeted bridge tests GREEN.

### Task 6: Full source verification and actual GeckoView 153 ABI gate

**Files:**
- No production change unless a real ABI/test defect is found.

- [x] Run full Python suite.
- [x] Run full Java host suite.
- [x] Run Android API37 contract compile.
- [x] Run minSdk26 NewApi lint.
- [x] Scan source for forbidden WebView/SpeechRecognizer/synthetic-KeyEvent/cache-bypass/secret patterns.
- [x] Extract actual GeckoView 153 DEX from canonical payload and verify `ensureBuiltIn(String,String)`, `MessageDelegate.onConnect(Port)`, `Port.postMessage(JSONObject)`, `Port.sender`, and session-specific `setMessageDelegate` signatures.
- [x] If any ABI mismatch appears, add a failing contract and fix before packaging.

### Task 7: RC3 DEX/APK/signing/package audit

**Files:**
- Generate `classes3.dex` and signed RC3 APK outside source tree.

- [x] Compile app Java against API37 + Gecko ABI stubs with JDK17.
- [x] D8 with min-api26 and confirm only `com.liuchenlin.chatgptweb` app classes are program definitions.
- [x] Assemble vc103 manifest/classes3.dex/bridge assets onto verified baseline without changing Gecko/native/resource payload.
- [x] zipalign 16KiB.
- [x] Sign with the canonical current-release RSA4096 identity using private password-file inputs without exposing the secret.
- [x] Verify v2/v3 signature and exact certificate SHA256.
- [x] Audit package identity, component counts, 5 DEX, 39 `.so`, byte-identical resources/native/other DEX, and approved payload diff allowlist.

### Task 8: Clean source archive, comprehensive review, and true 7z delivery

**Files:**
- Generate `ChatGPT-Web-source-1.0.2-RC3-2026-08-27.tar.gz`.
- Generate Review/Gate/SHA docs.
- Generate `ChatGPT-Web-1.0.2-RC3-vc103.7z.001...`.

- [x] Clean caches/generated classes and create deterministic source tar.gz.
- [x] Extract that exact archive into a new directory and rerun the complete source gate.
- [x] Create a standard 7z archive whose sole content is the signed RC3 APK, then split into Library-safe ~30MiB `.7z.001...` volumes.
- [x] Verify full 7z extracts APK byte-identically; rejoined volumes reproduce the 7z byte-identically; rejoined 7z again extracts the APK byte-identically.
- [ ] Write comprehensive Release Review and Final Gate, explicitly separating source/package verification from unavailable target-device verification.
- [ ] Upload RC3 source/docs/7z volumes to Library and re-list/read them for persistence verification.
- [ ] Only after RC3 persistence is verified, delete superseded RC2 release/source/review/gate; preserve 1.0.1 canonical and useful early-phase evidence.


## Execution Result — 2026-08-27

All eight tasks above were executed in the isolated RC3 workspace using test-first changes and repeated full-gate verification. Production code is frozen after the post-implementation review. Final package status remains **RC3 / not Formal** because no online target ADB device was available for the required HarmonyOS/卓易通 runtime matrix.

Post-implementation review also caught and fixed defects not obvious from the initial design: stale-session attach could disconnect the current valid native port; generic textarea matching was too broad; COMPOSER_STATE was unnecessarily emitted per keystroke; native IME fallback was too permissive for UNSPECIFIED/GO; hard recovery could stall if the replacement Session never produced a bridge signal; Recents resume could zero a useful heartbeat; and Android API37 compile exposed a JSONObject checked-exception/statically-typed GeckoView mismatch. Each was covered by a regression contract before the final rebuild.
