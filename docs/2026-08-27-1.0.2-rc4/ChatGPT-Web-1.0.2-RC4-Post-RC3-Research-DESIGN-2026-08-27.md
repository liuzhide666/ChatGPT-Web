# ChatGPT Web 1.0.2 RC4 — Post-RC3 Research & Hardening Design

Date: 2026-08-27
Status: implemented candidate design; Formal promotion still requires target-device runtime matrix.
Baseline: 1.0.2 RC3 / vc103. RC4 uses versionCode 104 and keeps versionName 1.0.2.

## Goal

Harden the RC3 semantic composer router and page-health policy against edge cases found during a second post-build review, without widening DOM access, changing GeckoView, clearing user storage, or introducing periodic page reloads.

## Research conclusions

1. Recent ChatGPT Web DOM evidence continues to expose the main editor as `#prompt-textarea` (ProseMirror/contenteditable). The selector should remain narrow rather than falling back to arbitrary textarea/contenteditable elements.
2. Current/recent integrations also observe a send-control A/B variant `button[data-testid="fruitjuice-send-button"]`. It is safe to support only when the button is inside the same resolved composer/form as the trusted prompt editor.
3. GeckoView native messaging must treat all extension-originated messages as untrusted. RC4 validates nativeApp, current GeckoSession, top-level sender, exact HTTPS chatgpt.com origin, and `MessageSender.ENV_TYPE_CONTENT_SCRIPT`.
4. GeckoView navigation/location callbacks distinguish the currently loaded top-level page from the last trusted ChatGPT URL. Automatic health recovery must never act while the current top-level page is an external HTTPS login/OAuth/help page.
5. Normal Gecko `reload()` and reload-with-load-flags are separate APIs. RC4 keeps normal cache semantics and does not use bypass-cache flags or clear cookies/cache/storage.

## RC4 hardening changes

### 1. Consume successful semantic Enter

RC3 could click the real send control in a capture listener and only call `preventDefault()`. A page-level listener could still observe the same event. RC4 calls both `preventDefault()` and `stopImmediatePropagation()` only after a confirmed semantic submit succeeds. Shift+Enter, composing Enter, non-main editors, or disabled send controls remain untouched.

### 2. Main composer detection remains narrow

Custom routing is limited to:
- `#prompt-textarea`; or
- `textarea[name="prompt-textarea"]` as a narrow compatibility fallback.

Send controls are resolved only inside the same composer/form. RC4 supports `#composer-submit-button`, `[data-testid="send-button"]`, `[data-testid="fruitjuice-send-button"]`, and narrowly scoped Send-label fallbacks. It never performs a page-global generic button click.

### 3. Preserve unsent-state safety across focus changes

Focus is not equivalent to content safety. When the composer loses focus, RC4 immediately reports `focused=false` but preserves `dirty`, `sendReady`, and `mainComposer` from the mounted main composer. This prevents page-health recovery from treating a blurred draft or attachment-only prompt as disposable.

### 4. Post-React state synchronization

Capture-phase `input`/`compositionend` can occur before React updates send-control enabled state. RC4 sends an immediate de-duplicated composer snapshot, then queues one `setTimeout(..., 0)` refresh. This improves native IME fallback accuracy without observing or transmitting message text.

### 5. Recovery safety gates

PageHealthPolicy blocks automatic recovery while any of the following is true:
- current top-level page is not trusted chatgpt.com;
- page is loading;
- composer contains a draft;
- composition is active;
- composer send control is ready (including attachment-only content);
- media recording is active;
- an Android external flow is active;
- app is not foreground.

An empty focused composer no longer blocks recovery by itself.

### 6. Trusted-current-page state

GeckoSessionController maintains a separate `currentPageTrusted` boolean. Top-level page start updates it from the actual current URL. `lastTrustedUri` remains a recovery destination but is not evidence that the current page is trusted. External HTTPS navigation remains allowed and is simply excluded from automatic health recovery.

### 7. Bridge hardening

Native messages and Port connections are accepted only for the current GeckoSession, top-level exact chatgpt.com HTTPS sender, and content-script environment. Stale-session attach callbacks cannot disconnect the currently valid port.

## Loading, cache, and refresh policy

- Cold start / launcher re-entry: new Session to ChatGPT home as previously designed.
- Recents / ordinary foreground resume: keep live Session; no reload.
- Gecko activation: inactive when not visible, active when visible.
- Heartbeat: 10 seconds while visible.
- Foreground-resume grace: 12 seconds.
- Stale threshold: 35 seconds.
- Recovery: at most one normal-cache soft reload, then after at least 20 seconds one hard Session replacement to last trusted ChatGPT URI, then after at least 30 seconds recovery UI.
- No `LOAD_FLAGS_BYPASS_CACHE`, cache purge, cookie purge, local/session storage purge, WorkManager refresh, or background refresh Service.

## Privacy / data boundary

The bridge transmits only boolean/health metadata and monotonic event state. It does not transmit prompt text, answer text, clipboard contents, cookies, tokens, localStorage/sessionStorage contents, or DOM text.

## Verification requirements

RC4 is not eligible for Formal promotion until all source/package gates pass and a real target-device matrix verifies Enter/Shift+Enter, Chinese/WeChat IME composition, Launcher/Recents, microphone, upload/download, long-conversation behavior, bounded recovery, and same-certificate in-place upgrade.
