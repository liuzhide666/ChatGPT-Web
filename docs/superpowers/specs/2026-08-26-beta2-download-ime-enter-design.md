# ChatGPT Web 0.4.0 Beta 2 — Download / IME Anchor / Enter-to-Send Design

Date: 2026-08-26

## Goal

Ship a Probe-first 0.4.0 Beta 2 (`versionCode=41`) that preserves the verified Gecko/MediaGuard/BrowserFit baseline while adding an explicit download path, improving IME anchor correctness without controlling third-party IME UI, and making the keyboard action send messages without breaking active CJK composition. Formal release is allowed only if the Beta 1 Release signing key can be proven available.

## Release baseline

- Product name: `ChatGPT Web`.
- Formal package: `com.liuchenlin.chatgptweb`; Probe package: `com.liuchenlin.chatgptweb.geckoprobe`.
- Beta 2: `versionCode=41`, `versionName=0.4.0 Beta 2`.
- `minSdk=26`, `targetSdk=28`, `compileSdk=37`, Java 17.
- GeckoView remains `153.0.20260810162159`.
- Preserve R4 MediaGuard, physical `SOURCE_MICROPHONE`, upload SAF flow, session/crash recovery, 10-minute background policy, 39 Gecko/native `.so` payload and third-party DEX payload.
- No WebView fallback, UA spoof, SpeechRecognizer, JS permission bridge, overlay, accessibility service, broad storage permission, foreground service, or new network stack.

## 1. Source convergence

The source tree becomes the authoritative source for package/version/brand. `formal` and `probe` launcher labels, `app_name`, and `probe_display_name` are all `ChatGPT Web`; the manifest continues to use the flavor placeholder. No post-build resource string patch is required for branding. Contract tests prevent legacy Chinese Beta labels from reappearing.

## 2. Downloads

`GeckoSession.ContentDelegate.onExternalResponse(session, WebResponse)` is the single download entry point. The app consumes the existing `WebResponse.body` exactly once and closes it in all terminal paths. It does not re-fetch the URL.

A small `DownloadCoordinator` owns download metadata parsing and Android storage writes. On API 29+, it inserts into `MediaStore.Downloads` with `RELATIVE_PATH=Download/ChatGPT Web`, `IS_PENDING=1`, streams the body to the returned URI, then commits with `IS_PENDING=0`. On failure it deletes the pending item. On API 26-28 it uses `ACTION_CREATE_DOCUMENT` so the user selects the destination; the response body is held only while that external flow is active, and cancellation closes it.

Filename precedence: RFC 5987 `filename*` from `Content-Disposition`, then quoted/plain `filename`, then final URI path segment, then `download`. Sanitize `/`, `\\`, NUL/control characters and `.`/`..`; cap filename length conservatively. MIME comes from `Content-Type` without parameters, otherwise `application/octet-stream`.

The UI gives a concise Toast for start/success/failure. It does not auto-open or auto-install downloads.

## 3. IME anchor

Keep `android:windowSoftInputMode="adjustResize"` and Gecko's native text-input stack. Keep `IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN`. Remove redundant runtime `setSoftInputMode(...)` mutation from `applySystemBars()` so system-bar repainting does not repeatedly alter IME policy.

Do not draw a custom candidate window. Candidate positioning remains the IME's responsibility. The app's responsibility is to preserve the native Gecko input connection and cursor-anchor updates. Probe diagnostics may record only editor/geometry state, never text contents.

## 4. Enter-to-send

`ChatGPTGeckoView.onCreateInputConnection(EditorInfo)` wraps Gecko's native connection only when non-null. It requests `IME_ACTION_SEND` for text editors and clears `IME_FLAG_NO_ENTER_ACTION`, while retaining no-extract/no-fullscreen flags. The wrapper handles `performEditorAction(IME_ACTION_SEND)` by sending a standard Enter key down/up pair into Gecko, leaving ChatGPT's web composer to decide whether that Enter sends.

CJK safety: the wrapper must not synthesize send while a composing region is active. Composition state is tracked from `setComposingText`, `setComposingRegion`, `finishComposingText`, and `commitText`. While composing, SEND finalizes the current composing text and consumes that action without emitting Enter; only a later SEND action may synthesize Enter. This prevents a composition-confirmation press from sending the whole message.

Hardware key semantics are not globally intercepted: the web page continues to own physical Enter / Shift+Enter behavior.

## 5. Lifecycle and failure boundaries

Downloads do not alter SessionState or Gecko navigation. API 26-28 save-as uses `ExternalFlowTracker.Flow.DOWNLOAD_SAVE` so temporary Activity stop does not deactivate/flush the current GeckoSession. Crash cleanup closes any pending download body and abandons the save-as callback, matching the existing file-picker and Android-permission stale-callback policy.

## 6. Verification gates

1. TDD for every new policy/helper and controller integration.
2. Existing Python and Java host suites remain green.
3. Android API 37 contract compile passes with the verified toolchain.
4. Forbidden WebView/dictation/secret scans pass.
5. Probe APK identity: `com.liuchenlin.chatgptweb.geckoprobe`, vc41, `0.4.0 Beta 2`, label `ChatGPT Web`.
6. Preserve permission set; do not add storage permission.
7. APK Signature v2/v3 and 16 KiB alignment pass.
8. Base Beta1 Formal -> Beta2 Probe payload changes must be explainable: application DEX plus manifest/resources only where version/package/brand or new Android APIs require them; Gecko/native and third-party payload remain byte-identical.
9. Device claims require an online ADB device. Without one, mark download destination behavior, WeChat IME candidate placement and Enter-to-send as pending real-device validation.
10. Formal APK is blocked unless its signer equals the Beta1 Formal signer SHA-256 `0cdf68fa7eec8b16bdb2b62248a276e09ea18db6dad52a8a1b4d4407b814dd70`.
