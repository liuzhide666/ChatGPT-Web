# ChatGPT Web 1.1.0 RC2 / vc112 — Final Architecture & Execution Plan

> **Status:** accepted project handoff; design/implementation plan only. No source code or APK is changed by this document.
>
> **Target:** `versionName=1.1.0`, `versionCode=112`, internal stage `RC2`; GeckoView frozen at `153.0.20260810162159`; package/signing/profile/Cookie/cache/minSdk/targetSdk unchanged.
>
> **Canonical rule:** `1.0.3 Formal` remains the canonical baseline. `1.1.0 / vc111` is retained as failed target-device evidence and must not be promoted. RC2 may become Formal only by promoting the **same vc112 APK bytes** after the target-device gate; if source changes after a failed Gate, increment to vc113 and restart the candidate gate.

## 0. Executive conclusion

The next change should not be another Shift+Enter patch. The correct architecture is to treat every Enter-family signal as a single **InputIntentTransaction** whose ownership, composition guard, modifier classification, deduplication, semantic routing, command execution, ACK correlation and editor-effect verification are shared. `Enter` and `Shift+Enter` diverge only at the semantic action: `SUBMIT_COMPOSER` versus `INSERT_LINE_BREAK`.

The page-health path should also finish the transition already begun in vc111: remove the permanent visible-page heartbeat, use event-driven health signals plus one Native deadline, issue `PING` only when freshness becomes doubtful, then classify and escalate `PROBE -> REBIND -> SOFT_RELOAD -> HARD_REPLACE -> RECOVERY_UI`. Default Gecko cache semantics remain the invariant; no automatic cache/Cookie/storage purge is allowed.

Two corrections are mandatory beyond the supplied proposal:

1. **WebExtension internal version must advance.** vc111 bundles extension version `1.1.0`. GeckoView `ensureBuiltIn()` does not reinstall a built-in extension when the installed version is the same. RC2 changes `content.js`/`site_adapter.js`, therefore its bundled extension version must become **`1.1.1`** (app version remains `1.1.0`). Any later candidate that changes bridge assets increments the extension version again. A Native/Web protocol handshake must expose the running extension version so stale bridge code cannot fail silently.
2. **`onPaintStatusReset()` is not positive content liveness.** It means valid painted content is no longer being displayed. RC2 must set `paintValid=false` on this callback; only a subsequent `onFirstContentfulPaint()` sets `paintValid=true`. `onSlowScript()` is evidence that the content process is alive but JavaScript may be blocked; it is diagnostic/suspicion evidence, not a reload trigger by itself.

---

## 1. Frozen release identity and promotion rules

### 1.1 Candidate identity

- App package: `com.liuchenlin.chatgptweb`
- App label: `ChatGPT Web`
- App `versionName`: `1.1.0`
- App `versionCode`: `112`
- Built-in WebExtension version: **`1.1.1`**
- Native/Web input protocol version: **`2`**
- GeckoView: `153.0.20260810162159`
- minSdk / targetSdk / compileSdk: `26 / 28 / 37`
- Java: `17`
- canonical signer: unchanged RSA-4096 certificate
- profile/Cookie/cache/localStorage/service-worker state: preserved

### 1.2 vc111 status

Record vc111 as:

`1.1.0 / vc111 — FAILED_TARGET_DEVICE_GATE: Shift+Enter known defect`

Do not delete, overwrite or relabel its APK/source/review evidence. It is now a regression fixture and a negative target-device reference.

### 1.3 Same-bytes Formal promotion

If vc112 passes every source/package gate and the complete 卓易通 Gate F, Formal promotion must only:

- record device/OS/IME/build identifiers and APK SHA-256;
- copy/rename the same APK bytes into Formal release naming;
- create Formal 7z/SHA256SUMS around those bytes;
- update `CURRENT-PROJECT-INDEX.txt` to the new canonical release.

No recompile, no manifest edit, no versionCode change, no asset change after the passing device gate.

If any source change is required after target-device testing, vc112 is frozen as failed evidence and the next build is vc113.

---

## 2. Architecture decision: one input transaction, multiple ingress paths

### 2.1 New core types

Create three focused units:

1. `NativeKeyIngress` — receives real `KeyEvent` objects from Activity/View and converts them into transaction signals; owns DOWN/UP consumption.
2. `InputIntentTransaction` — immutable transaction identity plus lifecycle/state.
3. `InputEventDeduplicator` — guarantees that one physical/logical Enter gesture creates at most one semantic action across Activity, InputConnection, GeckoView and DOM fallback.

`ComposerInputRouter` remains a pure policy function; it must not own transport, deduplication or executor logic.

### 2.2 Ingress priority

Use the following priority as **authority order**, not as an assumption about physical provenance:

| Priority | Source | Role |
|---|---|---|
| P0 | `Activity.dispatchKeyEvent()` | earliest app-level `KeyEvent` ingress; preferred ownership point |
| P1 | `ChatGPTInputConnection.sendKeyEvent()` | IME/special-keyboard key-event ingress |
| P2 | `performEditorAction()` / exact standalone newline commit policy | software-IME semantic ingress |
| P3 | `ChatGPTGeckoView.dispatchKeyEvent()` | Native fallback when higher layer did not own the event |
| P4 | DOM `keydown` | compatibility fallback only |
| forbidden submit authority | DOM `beforeinput` | observation/suppression only; never decides submit |

Do **not** label every Activity event as “physical keyboard”. IMEs can synthesize key events and implementations may or may not mark them reliably. Diagnostics record the actual ingress and `KeyEvent` metadata; guarantee classification as physical/IME only when evidence supports it.

### 2.3 Shared routing conditions

All Enter-family transactions first evaluate the same ownership/safety predicate:

```text
trustedPage
&& currentComposerLease
&& composerFocusGeneration == inputGeneration
&& !nativeComposing
&& !webComposing
&& !repeat
&& !ctrl
&& !alt
&& !meta
```

Then only the semantic decision differs:

```text
ENTER_KEY && shift=false && sendReady=true -> SUBMIT_COMPOSER
ENTER_KEY && shift=true                  -> INSERT_LINE_BREAK
```

Shift+Enter does not require `sendReady`; generation/focus/composition/trust still apply.

`TEXT_COMMIT_LINE_BREAK` must remain a separate explicitly named software-IME policy signal. A bare `commitText("\n")` proves neither Shift nor physical Enter. For RC2 normal routing, preserve the existing product policy unless target-device evidence justifies changing it; never relabel it as Shift+Enter.

---

## 3. Transaction lifecycle and DOWN/UP ownership

### 3.1 Transaction state machine

Every owned Enter-family action uses one transaction:

```text
CREATED
 -> OWNERSHIP_CONFIRMED
 -> ROUTED
 -> COMMAND_REQUESTED
 -> EXECUTOR_ACCEPTED
 -> EFFECT_OBSERVED
 -> APPLIED
```

Terminal alternatives:

```text
DELEGATED
REJECTED
TIMEOUT
STALE
CANCELED
EXECUTOR_FAILED
EFFECT_NOT_OBSERVED
```

`APPLIED` is forbidden until an independent editor/page effect verifier has observed the expected effect.

### 3.2 Key identity and deduplication

For a real `KeyEvent`, use a stable gesture identity centered on:

```text
deviceId
scanCode
keyCode
downTime
```

Store `eventTime`, `action`, `repeatCount`, `metaState`, source and flags as trace metadata, but **do not require exact `eventTime` equality for dedup identity** because a copied/re-emitted event may not preserve it exactly.

Fallback identity when `deviceId/scanCode` are non-informative uses `keyCode + downTime + generation` plus source precedence. Any time window is only for bounded-registry garbage collection, never for deciding input semantics.

### 3.3 DOWN/UP invariant

If an Enter `ACTION_DOWN` transaction is owned at any Native ingress:

- all duplicate DOWN copies are consumed;
- repeat DOWNs with the same gesture identity are consumed without a new semantic action;
- the matching ACTION_UP is consumed;
- Gecko/DOM must not receive an orphaned UP from an owned DOWN.

Invariant for every gesture:

`semanticActionCount <= 1`

For a successfully routed Enter or Shift+Enter:

`semanticActionCount == 1`

---

## 4. Phase A — Target-device transport probe comes before executor replacement

### 4.1 Extend `InputTraceBuffer`

Keep capacity `64` and memory-only metadata. Expand event fields to:

```text
transactionId
timestampMs
source
phase/action (DOWN/UP/COMMIT/ACTION/ROUTER/COMMAND/ACK/EFFECT)
keyCode
deviceId
scanCode
downTime
eventTime
metaState
shiftPressed
repeatCount
nativeComposing
webComposing
inputGeneration
composerGeneration
leaseValid
semanticAction
commandId
result
extensionVersion
protocolVersion
```

Forbidden fields remain:

- prompt/candidate/committed text;
- selection text;
- clipboard;
- HTML/DOM textContent/value;
- Cookie/token/localStorage;
- request body/query payload.

Device/scan identifiers are diagnostic metadata only and must not be persisted across process restarts.

### 4.2 Source names

Use trace sources that match actual boundaries:

- `ACTIVITY_KEY`
- `INPUT_CONNECTION_KEY`
- `INPUT_CONNECTION_COMMIT`
- `IME_ACTION`
- `GECKOVIEW_KEY`
- `DOM_KEYDOWN`
- `ROUTER`
- `COMMAND_REQUEST`
- `COMMAND_ACK`
- `EDITOR_EFFECT`

### 4.3 User-visible diagnostics

Add a read-only `InputDiagnosticsDialog` that displays all recent 64 metadata rows and a compact health snapshot. It must be accessible without ADB while the composer is active, and also from Recovery UI.

Recommended minimal UI:

- normal `换行` button remains the fallback action;
- long-press `换行` opens Input Diagnostics;
- Recovery UI has a visible `输入诊断` action;
- no editable controls and no persistence.

### 4.4 Mandatory target classifications

A single Shift+Enter on 卓易通 must mechanically resolve to one of these classes:

| Trace | Conclusion | Next step |
|---|---|---|
| `ACTIVITY_KEY shift=1` then Router `LINE_BREAK` | modifier survives to Activity | inspect executor/effect |
| no Activity record; `INPUT_CONNECTION_KEY shift=1` | modifier survives only through IC path | keep IC ingress as owner |
| Enter arrives but every Native ingress shows `shift=0` | modifier already lost before semantic routing | stop Web modifier patching; platform boundary |
| no Enter KeyEvent; only `IME_ACTION`/`COMMIT` | IME exposes no Shift chord | native newline fallback is required |
| `INSERT_LINE_BREAK` executor accepted but no `EDITOR_EFFECT` | false-positive executor | repair/replace executor |

This table is a release decision table, not a debugging suggestion.

---

## 5. Phase B — ACK means observed effect, not “JavaScript returned true”

### 5.1 Current defect to eliminate

In vc111, `site_adapter.insertLineBreak()` returns a boolean and `content.js` immediately returns `APPLIED`; Native then records `ACK_APPLIED`. This proves only that the executor path reported success. It does not prove that ChatGPT/ProseMirror retained a line break.

RC2 therefore separates four levels:

```text
REQUESTED
EXECUTOR_ACCEPTED
EDITOR_EFFECT_OBSERVED
APPLIED
```

### 5.2 Independent effect observer

Add `observeEditorEffect(transactionId, expectedAction, baseline)` to `site_adapter.js` or a dedicated adapter-owned helper. The baseline is metadata only.

For `INSERT_LINE_BREAK`, observe during a short bounded effect window:

- trusted main editor is still the same logical composer;
- an `input` event attributable to the transaction occurs, where available;
- input type is compatible with line break (`insertLineBreak`/`insertParagraph`) where available;
- selection/caret metadata changes plausibly, where available;
- a composer-scoped DOM mutation or textarea value revision is observed;
- **no submit effect** is observed.

Do not log old/new text, value, textContent, HTML, selection content or absolute prompt length.

### 5.3 Do not use self-incremented revision as proof

The executor must not increment the same revision counter that the verifier uses as evidence. Maintain separate concepts:

- `commandRevision` — transaction/control-plane sequence;
- `editorObservedRevision` — advances only from observed editor events/mutations outside the executor return value.

For textarea `setRangeText()`, synchronous mutation may be an executor success signal, but final `APPLIED` should still wait at least through the next microtask/frame or observed input cycle so an immediate framework re-render/revert is not mislabeled success.

### 5.4 “Exactly one mutation” correction

Do **not** require exactly one raw `MutationRecord`; one logical ProseMirror edit can produce multiple DOM mutation records. The invariant is exactly one **logical editor-effect transaction**, with zero submit, not exactly one observer record.

Effect outcome:

```text
expected line-break effect observed && submitObserved=false -> APPLIED
executor accepted but no effect before deadline           -> EFFECT_NOT_OBSERVED
submit observed during line-break transaction             -> SAFETY_VIOLATION
```

A safety violation must keep the candidate non-Formal even if the text also changed.

---

## 6. Phase C — Two-level line-break executor, selected by evidence

### 6.1 Executor A: `SiteAdapterLineBreakExecutor`

Keep Site Adapter as vc112 default.

Requirements:

- only a confirmed main composer;
- accepts `transactionId`, not modifier semantics;
- never decides whether Shift was pressed;
- returns `EXECUTOR_ACCEPTED/FAILED`, never `APPLIED`;
- final success comes only from effect verification.

`content.js` must not gain another Shift+Enter timing branch.

### 6.2 Executor B: `GeckoInputConnectionLineBreakProbe`

Do not make raw `commitText("\n", 1)` the vc112 default. Android documents `commitText()` as editor text commitment that finishes composing text, so it is a legitimate text-edit path but it can perturb CJK composition and must be proven on the real Gecko/IME stack.

RC2 may contain a **one-shot diagnostic probe** that invokes the captured raw Gecko InputConnection directly, bypassing `ChatGPTInputConnection`, but it must not silently alter normal routing or persist a hidden user preference.

If target evidence shows:

```text
Shift survives Native routing
AND Site Adapter executor repeatedly has EFFECT_NOT_OBSERVED
```

then run the raw-IC probe matrix. If it passes, change normal executor policy in source and build vc113. Do not promote a vc112 whose normal path differed from the path actually tested.

### 6.3 Required raw-IC matrix before promotion to primary

- new conversation;
- ordinary history;
- very long history;
- English input;
- Chinese/WeChat IME candidate lifecycle;
- delete;
- caret movement;
- undo;
- paste multiline text;
- Shift+Enter ×2;
- Shift+Enter -> Enter;
- answer generating while editing.

Pass condition per line-break gesture: one logical line-break effect, zero submit, no composition corruption.

---

## 7. Native software-keyboard boundary

Software IMEs are not required to emit useful KeyEvents. RC2 must make this boundary explicit rather than synthesize missing modifiers.

### Hardware / Bluetooth / real KeyEvent path

When the platform supplies a Shift-bearing Enter KeyEvent and the current composer lease is valid:

- Enter = submit exactly once;
- Shift+Enter = line break exactly once.

### Software IME path

- `IME_ACTION_SEND/DONE`: may request submit only under the same lease/composition/sendReady guard.
- exact newline `commitText`: follows the explicit standalone-newline policy; it is not treated as evidence of Shift.
- if the IME exposes no Shift semantic signal, do not invent one.
- deterministic escape hatch remains native `换行`.

Native `换行` visibility:

```text
trustedPage
&& currentComposerLease
&& focused
&& foreground
&& !composing
&& !nativeShiftLineBreakVerifiedThisSession
```

Successful verification means `EDITOR_EFFECT_OBSERVED/APPLIED`, not merely command ACK.

---

## 8. Phase D — Web bridge responsibility contraction and protocol handshake

### 8.1 File boundary

```text
content.js
├─ BridgeTransport
├─ CommandDispatcher
├─ InputFallback
└─ PageLivenessAgent

site_adapter.js
├─ findMainEditor()
├─ findSendControl()
├─ snapshot()
├─ submit()
├─ insertLineBreak()
└─ observeEditorEffect()
```

`content.js` must contain no ChatGPT selector strings.

### 8.2 Extension version and protocol contract

RC2 `manifest.json`:

```text
version = 1.1.1
```

On `READY`, the Web bridge sends metadata:

```text
extensionVersion = browser.runtime.getManifest().version
protocolVersion = 2
```

Native expects exactly the RC2 protocol. If it sees an older extension/protocol after an in-place upgrade:

- do not grant semantic input authority;
- show `BRIDGE_VERSION_MISMATCH` in diagnostics;
- request/retry `ensureBuiltIn()`/bridge rebind through the existing install path;
- never silently continue with stale bridge semantics.

Add an upgrade test from vc111 without clearing app data that proves the running bridge reports `1.1.1 / protocol 2`.

---

## 9. Phase E/F — Event-driven browser health, no permanent heartbeat

### 9.1 Remove visible-page `setInterval(HEARTBEAT)`

Delete permanent `setInterval()` heartbeat/composer polling from `content.js`.

Keep event-driven Web signals:

- `READY`;
- `COMPOSER_STATE` on actual state changes;
- focus/input/composition;
- `visibilitychange`;
- `PONG` only in response to Native `PING`;
- command ACK/effect events.

One-shot reconnect timers after a real port disconnect are allowed; they are not heartbeat polling.

### 9.2 Health snapshot becomes multidimensional

#### Bridge

- `portConnected`
- `lastPongMs`
- `lastComposerStateMs`
- `lastAckMs`
- `bridgeVersionMatched`

#### Gecko/content

- `loading`
- `lastProgressMs`
- `lastSessionStateMs`
- `lastFcpMs`
- `paintValid`
- `lastSlowScriptMs`
- `crashed/killed` handled by dedicated path

#### User/system

- `lastInteractionMs`
- `foreground`
- `networkAvailable`
- `externalFlowActive`
- `recordingActive`
- `filePicker/uploadActive`

#### Composer safety

- `dirty`
- `composing`
- `sendReady`
- `focused`
- `trustedOrigin`
- generation-matched lease

### 9.3 Correct signal semantics

- `onProgressChange`: positive content activity while current session is loading/active.
- `onSessionStateChange`: positive session/content activity when current session is foreground; it can reflect navigation, scrolling or form-state changes.
- `onPaintStatusReset`: set `paintValid=false`; do not refresh “content alive” timestamp by itself.
- `onFirstContentfulPaint`: set `paintValid=true`, update FCP/content evidence.
- `onSlowScript`: mark `slowScriptRecent=true`; it proves content process callback viability but warns that JS may not answer PING promptly. Do not reload solely because of this callback.

### 9.4 One deadline, probe first

Only one Handler callback is scheduled:

`nextDeadline = earliestMeaningfulDeadline(snapshot)`

When bridge freshness reaches its deadline, do **not** immediately rebind/reload. Transition:

```text
HEALTHY -> PROBE_SENT
```

Send one `PING` and wait `PING_GRACE_MS = 5000`.

- PONG -> reset incident to HEALTHY.
- no PONG + fresh Gecko/session/user evidence -> `REBIND_BRIDGE`.
- no PONG + content also stale -> continue recovery classification.
- recent `onSlowScript` -> permit a longer non-destructive probe/rebind grace before any reload.

No periodic timer is needed while the page is demonstrably healthy.

---

## 10. Phase G — Recovery ladder

### Level 0 — Probe

Action: `PING`.

No page mutation.

### Level 1 — Bridge rebind

Condition:

```text
no PONG
&& (recent Gecko/session/user evidence || paintValid)
```

Action:

```text
rebind current-session port
PING
SYNC_COMPOSER_STATE(currentGeneration)
```

Never reload.

### Level 2 — Soft reload

Require all:

```text
bridge remains dead
&& content/page evidence stale
&& foreground
&& online
&& trusted ChatGPT origin
&& !loading
&& !dirty
&& !composing
&& !sendReady
&& !mediaActive
&& !externalFlowActive
&& !filePickerOrUploadActive
&& no recent user interaction
&& composer not actively focused
```

Then exactly once per incident:

`session.reload()`

Cache: DEFAULT.

### Level 3 — Hard Session replacement

Only after soft recovery grace and still:

```text
no FCP
no progress/session-state recovery
no bridge/PONG
```

Then:

- close old page context;
- create new `GeckoSession`;
- same `GeckoRuntime` and profile;
- load `lastTrustedChatGPTUri` with default load flags.

### Level 4 — Recovery UI

Stop automatic destructive actions. Show:

- `重新尝试`;
- `强制重新获取页面资源`;
- `输入诊断`;
- health-state summary.

`强制重新获取页面资源` is the only path allowed to invoke one-shot `LOAD_FLAGS_BYPASS_CACHE`; it must not clear Cookie/storage/profile.

---

## 11. Cache/lifecycle invariants

| Scenario | Policy |
|---|---|
| normal browsing | DEFAULT cache |
| Recents return | same live Session; no reload |
| automatic soft recovery | `session.reload()` DEFAULT |
| automatic hard replacement | shared profile + DEFAULT |
| Launcher explicit re-entry | fresh ChatGPT home + DEFAULT |
| user explicit force-refetch | one-shot BYPASS_CACHE only |
| Cookie | never automatically clear |
| localStorage/service worker/profile | never automatically clear |

No WorkManager/background refresh service, no time-elapsed reload, no cache purge as a health strategy.

---

## 12. File map for RC2

### Create

- `app/src/main/java/com/liuchenlin/chatgptweb/InputIntentTransaction.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/InputEventDeduplicator.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/NativeKeyIngress.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/InputDiagnosticsDialog.java`
- `tests/java/com/liuchenlin/chatgptweb/InputIntentTransactionTest.java`
- `tests/java/com/liuchenlin/chatgptweb/InputEventDeduplicatorTest.java`
- `tests/test_112_activity_key_ingress_contract.py`
- `tests/test_112_input_transaction_contract.py`
- `tests/test_112_effect_verification_contract.py`
- `tests/test_112_event_driven_health_contract.py`
- `tests/test_112_bridge_version_contract.py`

### Modify

- `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTGeckoView.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTInputConnection.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/ComposerInputRouter.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/InputTraceBuffer.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/InputRoutingState.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTContentBridge.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/BrowserHealthSupervisor.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java`
- `app/src/main/java/com/liuchenlin/chatgptweb/NetworkStateMonitor.java` only if additional transition metadata is needed
- `app/src/main/assets/chatgpt_bridge/content.js`
- `app/src/main/assets/chatgpt_bridge/site_adapter.js`
- `app/src/main/assets/chatgpt_bridge/manifest.json`
- `app/build.gradle`
- release/source gate scripts and version contracts

Do not add ChatGPT selector knowledge to Native Java or `content.js`.

---

## 13. RED -> GREEN implementation sequence

### Task 1 — Freeze vc111 failure and RC2 version/bridge identity

**RED contracts:**

- app version must be `1.1.0 / 112`;
- extension must be `1.1.1`;
- Native expects protocol `2`;
- source contract fails if changed bridge assets keep extension version `1.1.0`.

**GREEN:** update app/version assets and add `READY(extensionVersion, protocolVersion)` handshake.

**Gate:** simulated upgrade state must reject stale `1.1.0/protocol1` bridge and accept `1.1.1/protocol2`.

### Task 2 — Activity key ingress probe

**RED:** current `MainActivity.dispatchKeyEvent()` only marks user interaction and does not trace/route Enter.

**GREEN:** wire `Activity.dispatchKeyEvent()` through `NativeKeyIngress` before `super`, while retaining normal delegation for unowned events.

**Gate:** trace must show original `metaState`, shift flag, device/scan/downTime before the View path.

### Task 3 — Unified transaction/router

Test matrix:

```text
Enter             -> SUBMIT
Shift+Enter       -> INSERT_LINE_BREAK
Ctrl+Enter        -> NONE
Alt+Enter         -> NONE
Meta+Enter        -> NONE
repeat            -> NONE
native composing  -> NONE
web composing     -> NONE
stale generation  -> NONE
untrusted page    -> NONE
plain Enter !sendReady -> NONE
Shift+Enter !sendReady  -> INSERT_LINE_BREAK
```

Prove Enter and Shift+Enter call the same transaction creator/router and differ only at semantic action.

### Task 4 — Cross-layer dedup and DOWN/UP ownership

Prove:

- Activity-owned event suppresses GeckoView duplicate;
- InputConnection-owned event suppresses later View/DOM copy;
- repeated DOWN creates no second command;
- matching UP is consumed;
- stale transaction cannot consume a new generation's event;
- one gesture => one semantic action.

### Task 5 — Metadata trace + user-visible diagnostics

Prove capacity 64, bounded memory, no accepted free-form text fields, and full Phase-A boundary trace.

### Task 6 — Effect-verified Site Adapter

Node/JS tests must include the critical false-positive case:

```text
executor returns accepted/true
but no editor effect is observed
=> COMMAND_ACK result != APPLIED
```

Pass only when:

```text
expected editor effect observed
&& zero submit
=> APPLIED
```

Also test immediate framework re-render/revert and multiple raw MutationRecords coalescing into one logical effect.

### Task 7 — Event-driven health + on-demand PING

**RED:** current `content.js` contains permanent visible-page `setInterval` heartbeat.

**GREEN:** remove it, introduce `PROBE_SENT`, lastPong/port state and single deadline.

Tests prohibit:

- fixed 5s poll;
- permanent visible `setInterval(heartbeat)`;
- reload on idle healthy page before a failed PING;
- treating `onPaintStatusReset` as positive paint health.

### Task 8 — Recovery ladder and cache invariants

Prove:

- bridge stale + PONG => no rebind/reload;
- no PONG + fresh content => rebind only;
- offline => no destructive reload/replacement;
- dirty/composing/sendReady/focused/media/file/external flow => no destructive recovery;
- soft uses default reload;
- hard uses same runtime/profile/default cache;
- only explicit force-refetch uses BYPASS_CACHE.

### Task 9 — Optional raw InputConnection probe

Run only if target trace proves the Site Adapter executor is the remaining defect. This task does not change vc112 default behavior. If the probe is selected as the new primary executor, produce vc113 and restart all candidate gates.

### Task 10 — Full source/package/reproducibility gate

Run from a clean extracted source archive and require:

- Pure Router Gate;
- InputConnection Gate;
- Activity Key Ingress Gate;
- Dedup Gate;
- Site Adapter Gate;
- ACK + Effect Gate;
- Browser Health Gate;
- Network Gate;
- Python full regression;
- Java Host full regression;
- Node simulation;
- API37 compile;
- minSdk26 NewApi lint;
- real Gecko153 ABI;
- app-only DEX;
- `debuggable=false`;
- same canonical signer, v2/v3 signature;
- 16 KiB alignment;
- 39 `.so` audit;
- clean source archive;
- reproducible APK rebuild;
- 7z round-trip.

Expected payload differences relative to vc111 must be explicitly allowlisted. With programmatic diagnostics UI and no resource redesign, the expected changed payload should be limited to Android manifest/version, application DEX and the three bridge assets (`manifest.json`, `content.js`, `site_adapter.js`), unless the implementation proves an additional necessary file.

---

## 14. Target-device Gate F for vc112

Run on each of:

- new conversation;
- ordinary history;
- very long history.

### Input core

- `abc` + Enter -> exactly one submit;
- `abc` + Shift+Enter -> exactly one line break, zero submit;
- Shift+Enter ×2 -> two logical line breaks;
- Shift+Enter -> Enter -> line break then one submit;
- held Enter -> no repeated submit;
- diagnostics show one transaction and no duplicate semantic command.

### Chinese / WeChat IME

- candidate active + Enter -> candidate/IME only, no submit;
- composition finished + Enter -> normal submit when send-ready;
- delete/caret/candidate selection/paste/undo remain normal Gecko behavior;
- no duplicated action around composition transitions.

### Software IME

- SEND/DONE follows submit policy once;
- standalone newline behavior matches the declared policy;
- no missing Shift signal is fabricated;
- native `换行` remains deterministic when needed.

### Lifecycle / health

- idle visible page -> deadline PING -> PONG -> no reload;
- long-conversation scrolling/form activity -> session-state freshness, no reload;
- bridge fault + live page -> rebind only;
- offline -> no destructive recovery;
- online -> probe/rebind first;
- background -> Recents -> live Session;
- Launcher -> fresh home;
- paint reset -> invalid paint state until FCP;
- slow script signal alone -> no immediate reload.

### Other flows

- attachment/file picker/upload;
- recording/microphone;
- download/open;
- OAuth/external app return;
- answer generation;
- dirty draft;
- attachment-only send-ready state.

No recovery step may destroy an active flow.

---

## 15. Mechanical decision matrix after target testing

| Result | Required decision |
|---|---|
| Activity trace has `shift=1`, Router `LINE_BREAK`, no editor effect | fix executor/effect path; source change => vc113 |
| Activity trace has `shift=1`, Router `NONE` | fix ownership/router; source change => vc113 |
| Activity absent, IC has `shift=1` and works | IC becomes proven owner for that path; no speculative Activity workaround |
| Enter arrives Native-side but `shift=0` everywhere | platform/卓易通 lost modifier; stop Web Shift patches; retain native newline fallback |
| no KeyEvent/Shift, only IME action/commit | software IME chord is not guaranteeable; native newline fallback is the supported route |
| executor accepted + effect observed + zero submit across full matrix | line-break path eligible for release gate |
| all candidate gates including target matrix pass | promote exact vc112 APK bytes to Formal |

---

## 16. What I would explicitly not do

- Do not add another `content.js` Shift timing token as the primary fix.
- Do not use DOM `beforeinput` as submit authority.
- Do not infer composer ownership from generic multiline `EditorInfo`.
- Do not equate `postMessage()` success, `execCommand()` true, `setRangeText()` completion or `COMMAND_ACK` receipt with final applied effect.
- Do not treat raw MutationObserver record count as logical edit count.
- Do not synthesize Shift that an IME never supplied.
- Do not make raw `InputConnection.commitText("\n")` primary without target-device CJK/undo/caret proof.
- Do not keep the WebExtension version unchanged when bridge assets change.
- Do not treat `onPaintStatusReset()` as healthy paint evidence.
- Do not keep a permanent heartbeat merely to make health timestamps fresh.
- Do not clear cache/Cookie/storage automatically.
- Do not rebuild after a passing Formal target gate.

---

## 17. Final acceptance criteria

RC2 is technically acceptable only when all of the following are simultaneously true:

1. One Enter-family gesture maps to one `InputIntentTransaction` and at most one semantic action.
2. Activity/InputConnection/View/DOM duplicates are deduplicated; owned DOWN implies owned matching UP.
3. `ACK_APPLIED` means independent editor/page effect observed, not executor return value.
4. Shift+Enter success is proven by one line-break effect and zero submit.
5. Missing platform Shift remains an explicit unsupported chord, not a Web workaround target.
6. Running built-in extension is verifiably `1.1.1 / protocol 2` after in-place upgrade.
7. Health monitoring has no permanent visible-page heartbeat and probes with PING only at meaningful deadlines.
8. Paint validity uses `onPaintStatusReset=false` / subsequent FCP=true semantics.
9. Recovery escalates non-destructively first and respects draft/composition/focus/media/network/external-flow blockers.
10. Cache is DEFAULT for all normal/automatic paths; BYPASS_CACHE is user-explicit only.
11. Clean-source and package reproducibility gates pass.
12. The complete 卓易通 Gate F passes on the exact signed vc112 APK bytes.

Only then may `CURRENT-PROJECT-INDEX.txt` move from `1.0.3 Formal` to `1.1.0 Formal`.

## Technical basis checked for this plan

- Android `Activity.dispatchKeyEvent()` may intercept key events before Window dispatch.
- Android `InputConnection.commitText()` commits text and finishes/replaces composing text; raw key-event sending from IMEs is discouraged for normal text entry.
- GeckoView `WebExtensionController.ensureBuiltIn()` does not reinstall an already installed built-in extension with the same version.
- GeckoView `ProgressDelegate.onSessionStateChange()` can reflect navigation, scrolling and form-state changes.
- GeckoView `ContentDelegate.onPaintStatusReset()` indicates painted content is no longer valid/displayed; `onFirstContentfulPaint()` can be paired with it to determine valid rendered content.
- GeckoView `LOAD_FLAGS_NONE` is default loading; `LOAD_FLAGS_BYPASS_CACHE` explicitly bypasses cache; `reload()` and `reload(flags)` are separate APIs.
