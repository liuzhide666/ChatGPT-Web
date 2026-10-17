# ChatGPT Web 1.1.0 — Formal Target-Device Gate F

**Candidate:** 1.1.0 / vc111  
**APK SHA-256:** `4532ee94a5e469972daaa58701e1ce7181d7b5a2c5e7eb3167c20fa8b9d10dd8`  
**Required device:** target 卓易通 Android environment with the actual keyboard/Chinese IME stack used in production.  
**Rule:** every critical row must pass on these exact APK bytes before canonical promotion.

## A. Upgrade and profile continuity

1. Upgrade from 1.0.3 without clearing app data: login/profile/Cookie state remains usable.
2. Upgrade from RC1/vc110 without clearing app data: install succeeds and profile remains usable.
3. App label remains `ChatGPT Web`; no debug build marker is exposed.

## B. Physical / hardware input — run in new, normal-history and very-long conversations

1. `abc` + Enter -> exactly one submit, no inserted newline.
2. `abc` + Shift+Enter -> exactly one line break, zero submit.
3. two Shift+Enter presses -> exactly two line breaks, zero submit.
4. Shift+Enter immediately followed by Enter -> first line break, second submit.
5. held/repeated Enter does not cause repeated multi-submit.
6. trace identifies the signal as `VIEW_KEY` or `INPUT_CONNECTION_KEY` with generation-matched ownership; the semantic command shows `REQUESTED -> ACK_APPLIED` for a successful action, and no prompt text appears in trace.

## C. Chinese / WeChat IME composition

1. While candidate text is composing, Enter confirms/operates the IME and does not prematurely submit ChatGPT.
2. composition finish -> ordinary Enter submits only when composer is send-ready.
3. cursor movement, deletion, candidate selection and multi-line paste remain Gecko/IME-owned.
4. no duplicated newline or duplicated submit after composition transitions.
5. trace contains only metadata (source/action/generation/composing/result), never candidate or prompt content.

## D. Software-keyboard deterministic newline

1. ordinary IME Enter/editor action follows the intended submit behavior without duplicate action.
2. if the IME provides no Shift modifier, the native `换行` control is visible only for a generation-confirmed focused main composer and inserts exactly one line break.
3. the control is hidden while composition is active or ownership is stale/non-main.
4. using the native control does not dismiss/corrupt IME state beyond normal editor behavior.

## E. Composer-state boundaries

Run Enter/line-break checks while attachment UI, dirty draft, send-disabled and answer-generation states are present. Native semantic routing must neither escape to a non-main editor nor submit when composition/send-ready constraints prohibit it.

## F. Lifecycle, cache and navigation

1. Recents -> return preserves the live conversation/session/scroll state without automatic reload.
2. explicit Launcher re-entry follows the project's fresh-home semantics.
3. login/OAuth and non-chatgpt.com top-level navigation do not receive ChatGPT composer semantic input authority.
4. background -> foreground does not create a 5-second refresh loop or clear login state.

## G. Network and recovery

1. offline -> keep page/session; no automatic soft reload or hard replacement while network is known unavailable.
2. online transition -> incident baseline resets; bridge is pinged/rebound before destructive recovery is reconsidered.
3. artificially disconnect the bridge while page/user activity continues -> rebind/PING only, no page reload.
4. true content/page hang -> bounded escalation `REBIND_BRIDGE -> SOFT_RELOAD -> HARD_REPLACE -> RECOVERY_UI`, each destructive step at most once per incident.
5. content crash/kill follows the crash-specific recovery path without cache/Cookie/storage purge.

## H. Media / file / external-flow regressions

Verify microphone/recording, file picker/upload, download/open notification path, OAuth/external HTTPS/app round trips. Active flows block destructive recovery where specified.

## Promotion rule

Formal promotion is allowed only if all critical rows above pass with no unexplained trace anomaly. On PASS, record device/OS/input-method build identifiers and the exact APK SHA-256 in the release evidence, rename the same bytes from Formal Candidate to Formal, generate final Formal 7z/SHA256SUMS, and then update `CURRENT-PROJECT-INDEX.txt` to 1.1.0. On any failure, do not change the canonical pointer.
