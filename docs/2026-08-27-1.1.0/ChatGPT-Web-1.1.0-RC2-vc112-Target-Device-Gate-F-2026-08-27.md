# ChatGPT Web 1.1.0 RC2 / vc112 — Target-Device Gate F

**Candidate APK:** `ChatGPT-Web-1.1.0-RC2-vc112.apk`  
**Required SHA-256:** `469343cd23129073c2f2735d6c218fb417a0c7c3da758fdb3e168aa04c7324ad`  
**Required target:** the actual 卓易通 Android environment, physical/attached keyboard if used, and the real Chinese/微信 input-method stack.  
**Promotion rule:** the exact APK bytes above must pass every critical row. A source modification invalidates vc112 promotion and requires vc113.

## A. Installation and profile continuity

1. Upgrade from installed 1.0.3 without clearing data: sign-in, Cookie/profile and normal ChatGPT state remain usable.
2. If vc111 is installed, upgrade in place to vc112 succeeds with the same signer.
3. App identity remains `ChatGPT Web`, `1.1.0`, and no debug marker is exposed.
4. Input Diagnostics shows bridge extension `1.1.1 / protocol 2` after the new page/extension is active. A stale 1.1.0 bridge must not receive Native semantic authority.

## B. Phase-A Shift transport probe

Before judging the line-break executor, press one real Shift+Enter and inspect the in-app trace. Record which first applicable result occurs:

| Trace evidence | Classification | Required next decision |
|---|---|---|
| `ACTIVITY_KEY`, Shift=1 | Android Activity received modifier intact | Continue through Router/Effect checks |
| no Activity row, `IC_SEND_KEY`, Shift=1 | modifier reached InputConnection | Continue through Router/Effect checks |
| Enter reaches Native but Shift=0 at earliest observed boundary | modifier lost before/at application ingress | stop Web timing/selector fixes; document platform/input-stack boundary |
| no Enter KeyEvent; only IME action/commit path | software IME did not expose Shift chord | hardware guarantee does not extend to this IME; use native newline path |
| Router=`INSERT_LINE_BREAK`, effect ends `NO_EDITOR_EFFECT` | intent correct, executor failed | executor investigation only |
| effect=`EDITOR_EFFECT_OBSERVED`, one mutation, zero submit, ACK=`APPLIED` | end-to-end success | continue full matrix |

The trace must contain no candidate/prompt/clipboard/HTML/Cookie/token/storage content.

## C. Physical / attached-keyboard input

Run the complete set in **new conversation**, **normal history** and **very-long history**:

1. `abc` + Enter -> exactly one submit, no inserted line break.
2. `abc` + Shift+Enter -> exactly one line break, zero submit.
3. Shift+Enter twice -> exactly two line breaks, zero submit.
4. Shift+Enter then Enter -> first line break, then exactly one submit.
5. Hold/repeat Enter -> no repeated multi-submit.
6. Ctrl+Enter / Alt+Enter / Meta+Enter -> no Native submit/line-break semantic action.
7. Every physical Enter gesture produces at most one semantic transaction even if Activity/InputConnection/View all observe copies.
8. If Native owns ACTION_DOWN, matching ACTION_UP is also consumed by the same transaction.

For successful Shift+Enter, the trace must show correlated stages ending in `EDITOR_EFFECT_OBSERVED` and `ACK_APPLIED`, with one logical mutation and `submitObserved=false`.

## D. Chinese / 微信 IME and editing invariants

1. Candidate composing + Enter -> confirms/operates the IME only; does not submit ChatGPT.
2. After composition has actually finished, ordinary Enter -> normal submit when send-ready.
3. Deletion/backspace behaves as Gecko/IME normally does.
4. Cursor movement and selection remain correct.
5. Multi-line paste remains correct and never becomes a submit command merely because it contains `\n`.
6. Undo/redo behavior is not corrupted by Shift+Enter.
7. Consecutive line breaks remain editable.
8. Changing between English and Chinese input does not leave a stale composing block or stale Native ownership generation.

## E. Software keyboard boundary

1. Software IME SEND/DONE -> submit only when the generation-matched main composer is focused, not composing and send-ready.
2. Standalone newline/commit behavior follows the gated standalone-newline policy; no fabricated Shift modifier is permitted.
3. If the IME exposes no Shift semantics, the native `↵ 换行` control remains the deterministic newline path.
4. Native newline control is visible only for a trusted, foreground, generation-confirmed focused main composer and is hidden/blocked during composition or stale ownership.
5. Using the native newline control does not corrupt candidate state or dismiss the keyboard beyond normal editor behavior.
6. Once a real hardware Shift+Enter is proven end-to-end for the session, adaptive hiding of the fallback control must not remove the ability to reopen Input Diagnostics.

## F. Editor-effect correctness

For a successful line break:

- `INSERT_LINE_BREAK` requested exactly once;
- executor accepted exactly once;
- editor-effect observer sees a persistent change;
- logical mutation count is exactly 1;
- no send/submit side effect occurs;
- final correlated ACK is `APPLIED`.

Any `executorAccepted=true` with no persistent editor effect must not produce `APPLIED`.

## G. Lifecycle and navigation

1. Long-history scrolling with a live page must not trigger time-based refresh.
2. Recents -> return preserves the live GeckoSession/conversation/scroll state.
3. Explicit Launcher re-entry follows the project's fresh-home policy.
4. Background -> foreground does not create a polling/reload loop.
5. Login/OAuth and non-ChatGPT top-level pages do not receive ChatGPT composer semantic authority.
6. A page transition/new InputConnection invalidates old composer ownership until a matching new generation sync completes.

## H. Event-driven browser health

Fault-inject or otherwise reproduce each class where practical:

1. Bridge stale, page/user content still active -> first action PING only.
2. No PONG after probe grace -> bridge rebind; still no reload.
3. PONG arrives -> incident resets, no rebind/reload.
4. Bridge and content both stale -> soft reload only after full rebind grace and safety checks.
5. Content becomes fresh after soft recovery -> no hard replacement.
6. True continued page failure -> bounded hard GeckoSession replacement, same runtime/profile/default cache.
7. Continued failure -> recovery UI; automatic operations stop.
8. `onPaintStatusReset` alone does not count as valid fresh paint; a later FCP restores paint validity.
9. slow-script signal alone never forces reload.

## I. Safety blockers

While each condition is active, destructive recovery must not occur:

- focused composer;
- dirty draft;
- active native or Web composition;
- send-ready composer safety state where configured;
- active recording/media;
- file picker/upload/external flow;
- background;
- active page load;
- untrusted top-level origin;
- known offline network.

Non-destructive PING/rebind may still occur where defined.

## J. Network and cache

1. Offline -> no automatic soft reload or hard replacement.
2. Online transition -> incident baseline resets; PING/rebind precedes destructive recovery.
3. Automatic soft reload uses default cache.
4. Automatic hard replacement reuses the same profile and default cache.
5. Cookie/localStorage/service-worker/profile state is never automatically purged.
6. Only explicitly tapping **强制重新获取页面资源** may perform one bypass-cache request.
7. After that explicit request, normal navigation/recovery returns to default cache semantics.

## K. Attachments, recording, files and external flows

Verify no regressions in:

- attachment UI and attachment-only state;
- file picker and upload;
- microphone permission and recording;
- download/open flow;
- OAuth/external HTTPS/app round trip;
- answer generation while entering a new draft;
- dirty draft during recovery incidents.

None may be spuriously refreshed by the browser-health supervisor.

## L. Promotion decision

**PASS:** all critical rows pass on APK SHA-256 `469343cd...7324ad`. Preserve device/OS/input-method identifiers and the Input Diagnostics evidence. Promote these exact APK bytes to `ChatGPT Web 1.1.0 Formal`; do not rebuild solely to change release wording or versionCode. Then update the canonical project pointer.

**FAIL:** do not change the canonical pointer. Classify the failure mechanically from the trace:

- Shift intact + Router wrong -> ownership/router fix;
- Shift intact + Router line-break + no effect -> executor/effect fix;
- Shift already absent at earliest Native boundary -> platform/input-stack boundary; stop Web selector/timing patches;
- no Shift/KeyEvent from software IME -> retain native newline control; do not fabricate modifier semantics.

Any code/source change after a failure produces `versionCode=113` and restarts the complete package + target-device gate.
