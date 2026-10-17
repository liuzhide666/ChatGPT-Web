# ChatGPT Web RC3 vc113 — Three-Part Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace application-owned Enter/Shift+Enter routing with complete keyboard transparency, then refactor browser health/cache/recovery around a persistent Gecko runtime, and finally produce a reproducible target-device candidate.

**Architecture:** Part 1 removes semantic keyboard ownership while preserving the existing browser shell. Part 2 independently refactors passive bridge/browser-health and cache/recovery. Part 3 performs clean-source integration, deterministic APK packaging and target-device delivery. This partition keeps regressions attributable to one subsystem at a time.

**Tech Stack:** Android Java 17, GeckoView `153.0.20260810162159`, WebExtension Manifest v2, Java Host tests, pytest source contracts, Node bridge simulations, Android API37 compile/NewApi lint, D8/apksigner/zipalign/7z release tooling.

**Spec:** `/任务项目/01-软件开发/ChatGPT-Web/docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-RC3-vc113-FINAL-DESIGN-2026-08-27.md`

## Global Constraints

- Package stays `com.liuchenlin.chatgptweb`; label stays `ChatGPT Web`.
- App identity is `versionName=1.1.0`, `versionCode=113`.
- Built-in extension is `1.1.2`; bridge protocol is `3`.
- GeckoView stays `153.0.20260810162159`; min/target/compile remain `26/28/37`; Java remains 17.
- vc112 failure evidence must remain immutable.
- Canonical pointer remains 1.0.3 Formal until exact vc113 target-tested APK passes.
- No production code may interpret or execute Enter/Shift+Enter/IME action semantics.
- No automatic cache/Cookie/storage/profile purge.
- TDD: each behavior change starts with a failing contract/behavior test and is verified green before refactor.

---

# Conversation Part 1 — Keyboard Transparency Foundation

## Task 1. Freeze vc112 failure and establish vc113 identity

**Files:**
- Create: `docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-RC2-vc112-TARGET-FAILURE-2026-08-27.md`
- Modify: `app/build.gradle`
- Modify: `app/src/main/assets/chatgpt_bridge/manifest.json`
- Modify: protocol constants/READY validation in `ChatGPTContentBridge.java` and `content.js`
- Test: `tests/test_113_version_bridge_contract.py`

**Produces:** `113 / 1.1.2 / protocol 3` identity and preserved vc112 failure record.

- [ ] Write a failing test requiring versionCode 113, extension 1.1.2, protocol 3, and the vc112 failure document.
- [ ] Run the focused test and confirm it fails on vc112.
- [ ] Update identity fields and READY/protocol validation only; do not remove keyboard code yet.
- [ ] Re-run focused tests and bridge Node smoke test.

## Task 2. Add Keyboard Neutrality RED gate

**Files:**
- Create: `tests/test_113_keyboard_neutrality_contract.py`

**Produces:** a negative source contract that fails whenever app-owned keyboard semantics exist.

The test must inspect production source and initially fail because vc112 contains the old architecture. Assertions include:

- no `KEYCODE_ENTER` semantic branch in MainActivity/ChatGPTGeckoView/custom InputConnection;
- no `NativeKeyIngress`, `ComposerInputRouter`, `InputIntentTransaction`, `InputEventDeduplicator` production classes;
- no semantic `sendKeyEvent`, newline `commitText`, `performEditorAction` interception;
- no `SUBMIT_COMPOSER` / `INSERT_LINE_BREAK` in Native or Web bridge;
- no semantic `keydown` or `beforeinput` listeners;
- no adapter `submit`, `insertLineBreak`, `observeEditorEffect` executor;
- no synthetic KeyEvent constructors/change methods;
- no Native line-break fallback control.

- [ ] Add the contract.
- [ ] Run only this file and verify expected RED failures identify the old paths rather than syntax/test errors.

## Task 3. Remove Activity/View/InputConnection keyboard ownership

**Files:**
- Modify: `MainActivity.java`
- Modify: `ChatGPTGeckoView.java`
- Delete: `ChatGPTInputConnection.java` if no independent feature needs it
- Delete: `NativeKeyIngress.java`
- Delete: `InputEventDeduplicator.java`
- Delete: `InputIntentTransaction.java`
- Delete: `ComposerInputRouter.java`
- Delete/reshape: `InputRoutingState.java`
- Retire/migrate: Java Host tests for these components

**Produces:** Gecko receives raw key/IME editing path with no semantic wrapper.

- [ ] Add/modify Java Host tests requiring `ChatGPTGeckoView.onCreateInputConnection()` to return the raw `super` connection and requiring `dispatchKeyEvent` not to special-case Enter.
- [ ] Verify RED.
- [ ] Remove Activity `NativeKeyIngress` setup and all Enter routing methods/state.
- [ ] Make GeckoView stop wrapping the InputConnection and stop setting input-intent delegates/observers.
- [ ] Remove custom semantic InputConnection and router/dedup/transaction classes.
- [ ] Preserve unrelated generic user-interaction bookkeeping only if it is key-agnostic and delegates to `super`.
- [ ] Run Java Host focused tests + neutrality contract.

## Task 4. Remove Native semantic command pipeline and input UI/trace

**Files:**
- Modify: `MainActivity.java`
- Modify: `ChatGPTContentBridge.java`
- Delete/replace later: `InputDiagnosticsDialog.java`
- Delete/replace later: `InputTraceBuffer.java`
- Remove Native line-break button construction/visibility logic
- Retire/migrate input-command/effect Java/Python tests

**Produces:** Native has no submit/line-break command IDs, ACK/effect pipeline, or keyboard diagnostics.

- [ ] Add RED assertions that `requestComposerSubmit`, `requestComposerLineBreak`, pending input command maps/timeouts, and Native line-break UI are absent.
- [ ] Remove those APIs and their callers.
- [ ] Remove COMMAND_ACK/editor-effect handling that exists only for semantic input execution.
- [ ] Remove line-break fallback UI and input-specific diagnostics entry point.
- [ ] Do not yet build Browser Diagnostics; that is Part 2.
- [ ] Run Java Host and Python focused tests.

## Task 5. Make WebExtension passive-only

**Files:**
- Modify: `app/src/main/assets/chatgpt_bridge/content.js`
- Modify: `app/src/main/assets/chatgpt_bridge/site_adapter.js`
- Modify: `app/src/main/assets/chatgpt_bridge/manifest.json`
- Test: create `tests/run_keyboard_neutral_node.js`
- Migrate existing Node simulations away from semantic command expectations

**Produces:** protocol-3 passive bridge with no editor mutation/submit capability.

- [ ] Add Node RED tests that dispatch Enter/Shift+Enter/beforeinput and assert the extension neither prevents default nor calls submit/line-break executors.
- [ ] Remove DOM keyboard/beforeinput semantic listeners.
- [ ] Remove COMMAND handling for `SUBMIT_COMPOSER` and `INSERT_LINE_BREAK`.
- [ ] Remove adapter `submit`, `insertLineBreak`, `observeEditorEffect` and send-button executor selectors.
- [ ] Preserve only passive composer safety snapshot and PING/PONG/READY/state synchronization.
- [ ] Run neutrality Node test plus surviving bridge Node tests.

## Task 6. Part-1 regression/migration gate

**Files:**
- Modify old input tests that assert vc110-vc112 routing behavior, converting them into historical or negative-neutrality contracts where appropriate.
- Do not delete unrelated browser/media/download/lifecycle tests.

**Produces:** a coherent vc113 source tree with keyboard neutrality but the pre-Part-2 browser-health algorithm otherwise preserved.

- [ ] Run all pytest tests and classify every failure as expected obsolete-input contract vs real regression.
- [ ] Migrate each obsolete-input test to a vc113 negative contract or explicit historical assertion; never blanket-skip the suite.
- [ ] Run all Java Host tests.
- [ ] Run all Node tests.
- [ ] Run API37 compile + minSdk26 NewApi lint + real Gecko153 ABI compile.
- [ ] Record Part-1 completion report. Do not package a candidate APK yet.

**Part-1 stop condition:** source and compile gates are green and the neutrality gate proves no semantic keyboard path remains. Browser-health redesign has not yet begun.

---

# Conversation Part 2 — Persistent Browser / Cache / Recovery Refactor

## Task 7. Introduce Browser Diagnostics and remove input-centric state

**Files:**
- Create: `BrowserDiagnosticsDialog.java`
- Modify: `MainActivity.java`
- Remove remaining input-trace classes if not already deleted
- Tests: `test_113_browser_diagnostics_contract.py`, Java Host diagnostics test

**Produces:** metadata-only browser diagnostics with no keyboard trace fields.

## Task 8. Reduce Site Adapter to recovery safety snapshot

**Files:** `site_adapter.js`, `content.js`, bridge tests.

**Produces:** focused/dirty/composing/attachment-like passive safety state only; `sendReady` is not a destructive-recovery blocker.

## Task 9. Add navigation generation and correct Gecko health semantics

**Files:** `GeckoSessionController.java`, `MainActivity.java`, health observer/tests.

**Produces:** current-generation load/FCP/paint state; stale callbacks ignored.

## Task 10. Refactor BrowserHealthSupervisor to timerless STABLE

**Files:** `BrowserHealthSupervisor.java`, Java Host tests, Python source contract.

**Produces:** no permanent heartbeat/polling; deadlines exist only for active probe/rebind/recovery operations.

## Task 11. Decouple bridge failure from page failure

**Produces:** bridge-only failure -> PING/rebind/`DEGRADED_STABLE`, never automatic page reload while content remains usable.

## Task 12. Implement failure quorum and recovery ladder

**Produces:** strong failure or independent weak-signal quorum before soft recovery; soft/default-cache -> hard same runtime/profile/default-cache -> user recovery.

## Task 13. Lock cache/network/lifecycle invariants

**Produces:** offline/online no healthy-page reload; Recents/foreground reuse live session; one-shot user bypass cache only; no automatic storage clearing.

## Task 14. Part-2 complete regression gate

Run pytest, Java Host, Node, API37, lint and real Gecko153 ABI. Record a Part-2 report. Do not create final APK yet.

---

# Conversation Part 3 — Integration, Reproducible Release, Target-Device Candidate

## Task 15. Clean-source freeze

Generate source archive excluding generated caches/build output and rerun all source/compile gates from a clean extract.

## Task 16. vc113 release DEX and binary allowlist

Build app-only DEX, prove foreign class count zero, verify Gecko153 ABI, compare baseline payload and allow only intended manifest/app-Dex/bridge changes.

## Task 17. APK signing/security/package audit

Use canonical release identity; require v2/v3, one expected RSA-4096 signer, 16 KiB alignment, debuggable=false, allowBackup=false, cleartext=false, expected permissions/components, 39 native libraries preserved.

## Task 18. Deterministic rebuild

Rebuild DEX/APK from clean source and require byte-identical result to the initial vc113 release build.

## Task 19. 7z delivery

Create true 7z archive containing only the APK, split into 30 MiB volumes, require `.001` entry point, rejoin/extract round-trip and byte-identical APK.

## Task 20. Release evidence and Library upload

Create RC3 Candidate Index, Release Review, Final Pre-Device Gate, Target-Device A/B Gate, checksums and source/release artifacts. Upload to project Library. Do not modify canonical pointer.

## Task 21. Target-device A/B acceptance

On the actual 卓易通 environment compare original ChatGPT website vs vc113 input behavior and separately run lifecycle/cache/recovery matrix. PASS permits exact-byte Formal promotion; any source fix requires vc114.
