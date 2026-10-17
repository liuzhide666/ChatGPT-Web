# ChatGPT Web 1.1.0 Formal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden RC1 into a reproducible ChatGPT Web 1.1.0 Formal Candidate whose native input authority is generation-bound, composition-safe, network-aware, and equipped with a deterministic software-keyboard newline fallback.

**Architecture:** Native input remains authoritative only after a versioned WebExtension handshake proves that the current Gecko InputConnection generation belongs to the focused ChatGPT main composer. InputConnection observes composition metadata without reading text; BrowserHealthSupervisor blocks destructive recovery while connectivity is known absent; a small native newline control uses the same semantic router. Site Adapter and default-cache recovery boundaries remain unchanged.

**Tech Stack:** Java 17, Android API 37 / minSdk 26 / targetSdk 28, GeckoView 153.0.20260810162159, MV2 built-in WebExtension, Python contracts, Java host tests, Node VM event simulation, Android build-tools 36.0.0, D8, apksigner, zipalign, 7-Zip.

**Spec:** `docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-FORMAL-DESIGN-AMENDMENT-2026-08-27.md`

## Global Constraints

- Keep `com.liuchenlin.chatgptweb`, app label `ChatGPT Web`, `versionName=1.1.0`, `versionCode=111`, extension `1.1.0`.
- Keep GeckoView exactly `153.0.20260810162159`.
- Never record prompt/composition text, clipboard, cookies, tokens or Web storage values.
- Never synthesize Android Enter KeyEvents.
- Multiline EditorInfo is metadata only and never proves composer ownership.
- No cache/Cookie/storage purge, bypass-cache reload, lifecycle reload loop or background refresh worker.
- Preserve the canonical RSA-4096 signing identity; do not expose private signing material.
- Formal promotion remains blocked without the target-device matrix.

---

### Task 1: Generation-bound composer ownership

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ComposerInputRouter.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/InputRoutingState.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTGeckoView.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java`
- Modify: `app/src/main/assets/chatgpt_bridge/content.js`
- Modify: `host-tests/com/liuchenlin/chatgptweb/ComposerInputRouterTest.java`
- Modify: `host-tests/com/liuchenlin/chatgptweb/ComposerInputRouterMultilineTest.java`
- Modify/Create: `tests/test_110_composer_ownership_generation_contract.py`
- Modify: `tools/verify_input_router_node.js`

**Interfaces:**
- `ComposerInputRouter.Signal` carries `generation` and `composerFocusGeneration`; route authority requires equality.
- `ChatGPTGeckoView.InputConnectionObserver.onInputConnectionCreated(long generation, boolean editorMultiline)` invalidates ownership and triggers a sync.
- `ChatGPTContentBridge.requestComposerState(long generation)` sends `SYNC_COMPOSER_STATE`.
- `COMPOSER_STATE` may echo `inputGeneration`; only a matching focused main-composer response establishes ownership.

- [ ] Write host/static/Node tests proving generic multiline Shift+Enter returns `NONE`, mismatched generations return `NONE`, and matching ownership permits line break/submit.
- [ ] Run targeted tests and observe RED on RC1.
- [ ] Implement the minimal generation handshake and strict router predicate.
- [ ] Run targeted tests and Node simulation GREEN.

### Task 2: InputConnection composition observation

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTGeckoView.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify/Create: `tests/test_110_native_composition_contract.py`

**Interfaces:**
- `InputConnectionObserver.onCompositionChanged(long generation, boolean composing)` publishes metadata only.
- Native route receives `nativeComposing`; effective composing state is native OR Web.

- [ ] Write static tests requiring delegated `setComposingText`, `setComposingRegion`, `finishComposingText`, and `commitText` lifecycle observation while forbidding any text logging/storage.
- [ ] Run tests and observe RED.
- [ ] Implement delegation-first composition metadata updates; preserve ordinary Gecko text behavior.
- [ ] Run Python + Java/API compile checks GREEN.

### Task 3: Native software-keyboard newline fallback

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/ComposerInputRouter.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify/Create: `tests/test_110_native_linebreak_ui_contract.py`

**Interfaces:**
- Add `Kind.LINE_BREAK_COMMAND`; source `NATIVE_UI` routes only with matching composer ownership and no composition.
- Native fallback button calls the same line-break execution path and hides after a successful real native Shift+Enter is verified in-session.

- [ ] Write tests requiring generation-confirmed visibility and semantic command routing with no synthetic key event.
- [ ] Run RED.
- [ ] Implement the minimal overlay button and adaptive visibility.
- [ ] Run targeted/full tests GREEN.

### Task 4: Network-aware BrowserHealthSupervisor

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/NetworkStateMonitor.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify: `host-tests/com/liuchenlin/chatgptweb/BrowserHealthSupervisorTest.java`
- Modify/Create: `tests/test_110_network_recovery_contract.py`

**Interfaces:**
- `BrowserHealthSupervisor.Snapshot.networkAvailable` means the default network is not known unavailable.
- `NetworkStateMonitor.mayHaveNetwork()` returns true on unknown/available, false only on known offline.
- Offline state blocks `SOFT_RELOAD/HARD_REPLACE/SHOW_UI` escalation but not `REBIND_BRIDGE`.

- [ ] Write host tests for offline rebind-only behavior and resumption after connectivity returns.
- [ ] Run RED.
- [ ] Implement network monitor and supervisor gating; network transitions reset the incident and request bridge ping.
- [ ] Run host/Python tests GREEN.

### Task 5: Reproducible Formal source/package gate

**Files:**
- Create: `tools/run_110_formal_source_gate.sh`
- Create/Modify: `tests/test_110_reproducible_gate_contract.py`
- Update: `docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-FORMAL-Release-Review-2026-08-27.md`
- Update: `docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-FORMAL-CANDIDATE-GATE-2026-08-27.txt`

**Interfaces:**
- Gate requires `TOOLKIT_ROOT` and invokes Node as `node tools/verify_input_router_node.js app/src/main/assets/chatgpt_bridge/site_adapter.js app/src/main/assets/chatgpt_bridge/content.js`.

- [ ] Write static contract for exact gate inputs and RED it before the script exists.
- [ ] Implement gate script and rerun from the working source tree.
- [ ] Create a clean source archive, extract to a fresh directory, and rerun the same gate.
- [ ] If package tooling and canonical signing material are available, build/audit/sign a Formal Candidate and verify v2/v3 signature, 16KiB alignment, DEX ownership, payload allowlist and 7z round-trip.
- [ ] Persist new Design/Plan/source/review/gate/release artifacts to Library and re-read them. Do not change the canonical pointer until target-device validation passes.

### Task 6: Correlated command ACK evidence

**Files:** `ChatGPTContentBridge.java`, `MainActivity.java`, `InputTraceBuffer.java`, `content.js`, Host/Node/Python contracts.

- [x] RED: direct command types and `postMessage == APPLIED` fail new ACK contracts.
- [x] GREEN: `{COMMAND, commandId, command}` -> Site Adapter -> `{COMMAND_ACK,...}`; trace records REQUESTED/ACK_APPLIED/ACK_REJECTED/TIMEOUT/CANCELED/STALE.
- [x] Verify Host + Node + full source gate.

### Task 7: Gecko paint-reset / slow-script evidence and actual ABI gate

**Files:** `GeckoSessionController.java`, `MainActivity.java`, Gecko Host stubs, `verify_geckoview153_abi.sh`, release DEX builder, tests.

- [x] RED: missing callbacks and missing real-ABI verification.
- [x] GREEN: paint reset and slow script feed health evidence only; no direct reload; script filename is never logged.
- [x] Verify actual packaged GeckoView 153 descriptors before release DEX compilation.
