# ChatGPT Web 1.1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 `ChatGPT Web 1.1.0 / vc110`，以 Native-first 输入语义和多信号 BrowserHealthSupervisor 根本替换脆弱的 Shift+Enter DOM 依赖与固定 page-health 轮询。

**Architecture:** Android View/InputConnection 先归一化输入意图，经可信 WebExtension Port 调用版本化 ChatGPT Site Adapter；DOM 键盘事件只保留 fallback。健康恢复以 Gecko progress/FCP、bridge liveness、用户交互与生命周期信号驱动单一 deadline，并按 rebind→soft reload→hard Session→UI 有界升级。

**Tech Stack:** Java 17, Android API37/minSdk26/targetSdk28, GeckoView 153.0.20260810162159, MV2 built-in WebExtension, Python/Java host tests, Node event simulation, D8, apksigner, 7-Zip.

**Spec:** `2026-08-27-1.1.0/ChatGPT-Web-1.1.0-Native-Input-Browser-Health-DESIGN-2026-08-27.md`

## Global Constraints

- package `com.liuchenlin.chatgptweb`, label `ChatGPT Web`。
- `versionName=1.1.0`, `versionCode=110`, extension `1.1.0`。
- GeckoView 固定 `153.0.20260810162159`；不新增 runtime dependency。
- 不读取/记录 prompt 正文、clipboard、cookie、token、localStorage 内容。
- 不生成 synthetic Android Enter KeyEvent；只消费真实 View/InputConnection KeyEvent 或发送语义 command。
- 不清 cache/Cookie/storage，不使用 bypass-cache，不添加生命周期 reload/后台刷新 worker。
- 同 canonical RSA-4096 release signer。

---

### Task 1: Input Transport Probe + pure routing policy

**Files:**
- Create: `src/main/java/com/liuchenlin/chatgptweb/InputTraceBuffer.java`
- Create: `src/main/java/com/liuchenlin/chatgptweb/ComposerInputRouter.java`
- Test: `com/liuchenlin/chatgptweb/InputTraceBufferTest.java`
- Test: `com/liuchenlin/chatgptweb/ComposerInputRouterTest.java`

**Interfaces:** `ComposerInputRouter.route(InputSignal): Action`; `InputTraceBuffer.add(InputTraceEvent)` / `snapshot()`。

- [x] RED: Shift+Enter => LINE_BREAK, Enter+sendReady => SUBMIT, composition/untrusted/not-main => NONE。
- [x] RED: trace buffer is bounded and rejects/sanitizes free-form text payloads.
- [x] GREEN: implement immutable metadata signal/event types and pure router.
- [x] Run Java host targeted tests.

### Task 2: Native-first GeckoView/InputConnection routing

**Files:**
- Modify: `src/main/java/com/liuchenlin/chatgptweb/ChatGPTGeckoView.java`
- Modify: `src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify: `src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java`
- Test: routing/static + Java host contracts.

**Interfaces:** `InputIntentDelegate.onInputIntent(Source, shift, repeat, actionId, generation)`; bridge `requestComposerSubmit()`, `requestComposerLineBreak()`, `requestPing()`。

- [x] RED: View ACTION_DOWN Shift+Enter consumed before `super`; matching ACTION_UP also consumed.
- [x] RED: InputConnection `sendKeyEvent()` uses same router; exact standalone newline `commitText()` is additionally classified as Enter while all other text/composition methods remain delegated.
- [x] RED: IME SEND/DONE maps only to submit; stale generation/unfocused/composing rejected.
- [x] GREEN: implement Native-first event consumption, handler-aware raw InputConnection line-break commit, standalone-newline commit routing, and metadata trace.
- [x] Run targeted Java/Python tests.

### Task 3: Versioned ChatGPT Site Adapter

**Files:**
- Create: `src/main/assets/chatgpt_bridge/site_adapter.js`
- Modify: `src/main/assets/chatgpt_bridge/content.js`
- Modify: `src/main/assets/chatgpt_bridge/manifest.json`
- Test: `verify_input_router_node.js`, Python contracts.

**Interfaces:** global adapter `snapshot()`, `submit()`, `insertLineBreak()`; port commands `SUBMIT_COMPOSER`, `INSERT_LINE_BREAK`, `PING`。

- [x] RED: native LINE_BREAK works without any keydown/shiftKey DOM event.
- [x] RED: site adapter refuses arbitrary textarea/contenteditable and never searches send buttons outside the composer.
- [x] RED: PING produces metadata PONG.
- [x] GREEN: extract DOM code into Site Adapter; keep DOM keydown as lowest-level fallback and remove DOM beforeinput submit authority.
- [x] Node event simulation GREEN.

### Task 4: BrowserHealthSupervisor + deadline scheduler

**Files:**
- Create: `src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java`
- Modify: `GeckoSessionController.java`, `MainActivity.java`, `ChatGPTContentBridge.java`
- Test: `BrowserHealthSupervisorTest.java` + Python contracts.

**Interfaces:** health signals `onBridge/onPort/onPageStart/onProgress/onPageStop/onFcp/onUserInteraction/onForeground/onBackground`; `Decision`=`NONE/REBIND_BRIDGE/SOFT_RELOAD/HARD_REPLACE/SHOW_UI`; `nextDelayMs(now)`。

- [x] RED: bridge-only stale + recent Gecko/user activity => REBIND only.
- [x] RED: bridge+content stale => bounded soft/hard/UI ladder.
- [x] RED: background/untrusted/loading/draft/composition/sendReady/media/external block destructive recovery.
- [x] RED: no fixed 5-second polling token remains.
- [x] GREEN: wire ProgressDelegate/FCP/session-state/user-interaction signals, reconnectable native port, and single Handler deadline.
- [x] Run targeted + full source tests.

### Task 5: Version 1.1.0 and full release gate

**Files:** build.gradle, extension manifest, final Design/Plan/Review/Gate, clean source archive, release volumes.

- [x] RED/GREEN version contracts for `110 / 1.1.0 / 1.1.0`.
- [x] Full historical and clean-archive gates: 127 Python + 28 Java host + API37 compile + Node event simulation + minSdk26 NewApi lint.
- [x] Verify actual GeckoView 153 ABI and build app-only DEX with zero foreign class definitions.
- [x] Build/zipalign/sign hardened APK with same canonical signer; enforce `debuggable=false`; byte-audit permissions, 39 `.so`, five DEX, resources and payload allowlist.
- [x] Persist `tools/patch_release_manifest.py` so future binary-manifest packaging cannot regress to `debuggable=true`.
- [x] Generate true 7z 30MiB volumes and verify full/rejoin/extract hashes.
- [ ] Persist Design/Plan/source/Review/Gate/RC1 release to Library and re-read. Keep 1.0.3 canonical until target-device 1.1.0 Shift+Enter matrix passes.
