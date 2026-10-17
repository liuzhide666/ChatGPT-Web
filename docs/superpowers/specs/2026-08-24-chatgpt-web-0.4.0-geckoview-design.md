# ChatGPT Web 0.4.0 Beta — GeckoView Engine Design Specification

Date: 2026-08-24
Status: Approved architecture; implementation authorized
Project root: `/工具/ChatGPT-Web/`
Source baseline: `/工具/ChatGPT-Web/source/ChatGPT-Web-source-current-0.3.1-dev.tar.gz`
Target environment: HarmonyOS / 卓易通 Android compatibility environment

## 1. Release definition

ChatGPT Web 0.4.0 Beta is a browser-engine migration, not a microphone hotfix for 0.3.1. It replaces the Android System WebView/Chromium embedding path with a single GeckoView engine and uses observable Gecko media events to determine whether ChatGPT can reach a real Android microphone path under 卓易通.

The release has four primary goals:

1. Remove System WebView from production source completely.
2. Route `navigator.mediaDevices.getUserMedia({audio:true})` through Gecko's native permission/media stack.
3. Produce an evidence chain from Android runtime permission, through Gecko `MediaSource`, to Gecko recording state.
4. Preserve normal ChatGPT browsing, authentication after first Gecko login, file upload, session persistence, background/foreground behavior, and content-process recovery.

A successful Android permission grant is not considered microphone success. A successful Gecko media permission grant is not considered recording success. `MICROPHONE + RECORDING` is the minimum proof of real Gecko recording; successful two-way ChatGPT Voice is the product-level success condition.

## 2. Frozen build and identity baseline

Formal application:

- App name: `ChatGPT Web`
- Application ID: `com.liuchenlin.chatgptweb`
- `versionCode = 40`
- `versionName = "0.4.0 Beta"`
- `minSdk = 26`
- `targetSdk = 28`
- `compileSdk = 37`
- Java source/target compatibility: 17
- Gradle: 9.3.1
- Android Gradle Plugin: 9.1.1
- SDK Build Tools: 36.0.0
- GeckoView: `org.mozilla.geckoview:geckoview:153.0.20260810162159`

The GeckoView version is immutable for the 0.4.0 development cycle. A move to GeckoView 154 or later is a later-version decision and must not occur as an unrecorded dependency drift.

The build is Universal/Fat for the first Beta. No `abiFilters` restriction is permitted in 0.4.0 unless a later approved design explicitly changes this rule.

## 3. Chosen architecture and rejected alternatives

Selected architecture: pure GeckoView single engine.

Rejected:

- GeckoView plus Mozilla Android Components: unnecessary browser-framework scope and dependency expansion for this diagnostic wrapper.
- WebView plus GeckoView dual engine: leaves two permission/session/upload implementations alive and contaminates root-cause analysis.

Production 0.4.0 contains exactly one browser engine, one process-level `GeckoRuntime`, and one current `GeckoSession`.

## 4. Mandatory source boundaries

The following are forbidden under `app/src/main`:

- `android.webkit.WebView`
- `android.webkit.WebChromeClient`
- `android.webkit.PermissionRequest`
- `SpeechRecognizer`
- `RecognizerIntent`
- `WebPermissionCompatScript`
- `NativeDictationScript`
- `navigator.permissions` shims
- `chatgptweb://native-dictation/*`

No production fallback may silently recreate WebView, Android speech-to-text injection, or a JavaScript permission facade.

## 5. Module decomposition

Production Java is split by responsibility:

- `MainActivity` — Android lifecycle/view ownership only; coordinates modules and external Activity results.
- `GeckoRuntimeHolder` — process-wide singleton `GeckoRuntime`; runtime settings/profile initialization only.
- `GeckoSessionController` — creates/replaces/binds the single current `GeckoSession`, registers delegates before opening, restores/loads, and performs crash recovery.
- `AndroidPermissionCoordinator` — one pending Android runtime permission transaction; strict permission whitelist.
- `GeckoMediaPermissionDelegate` — trusted-origin check plus selection of exact Gecko-provided media source objects.
- `TrustedOriginPolicy` — media-origin allowlist only.
- `NavigationPolicy` / `GeckoNavigationDelegate` — separates ordinary navigation from media trust.
- `MediaDiagnostics` — safe structured recording/permission/session diagnostics.
- `SessionStateStore` — serialized `GeckoSession.SessionState` and background timestamp persistence.
- `BackgroundReloadPolicy` — exact 600000 ms reload threshold and generation guard.
- `CrashRecoveryPolicy` — crash-window accounting and loop guard.
- `ExternalFlowTracker` — Android permission/file-picker lifecycle suppression.
- `FilePromptCoordinator` — Gecko file prompt to Android Storage Access Framework.

`MainActivity` must not become the implementation location for permission policy, media selection, navigation policy, crash policy, or persistence rules.

## 6. Gecko runtime/session rules

`GeckoRuntimeHolder` owns the only runtime instance for the process. A second runtime creation path is a contract violation.

Session construction order is:

1. Create `GeckoSession`.
2. Register permission, media, navigation, progress, prompt, and content delegates.
3. `open(runtime)`.
4. Bind to `GeckoView`.
5. Restore valid saved state; otherwise load `https://chatgpt.com/`.

Default Gecko user agent is retained. No Chrome UA spoofing is allowed in 0.4.0; production source must not set `userAgentOverride` or an equivalent Chrome-UA override. If ChatGPT Voice is absent under real Gecko UA, the result is classified as product/UA compatibility evidence rather than immediately hidden by spoofing.

## 7. Real microphone chain

Expected chain:

`ChatGPT getUserMedia(audio)` → Gecko web content → `PermissionDelegate.onAndroidPermissionsRequest()` → Android `RECORD_AUDIO` → `PermissionDelegate.onMediaPermissionRequest()` → offered Gecko `MediaSource[]` → select `TYPE_AUDIO + SOURCE_MICROPHONE` → `MediaCallback.grant(null, originalMicrophoneObject)` → Gecko MediaStream → Android audio input.

The media callback must receive an original `MediaSource` object supplied by Gecko. The app must never synthesize a `MediaSource` or grant by guessed device name.

Audio selection rule:

- Iterate Gecko's audio array in offered order.
- Select the first entry where `type == TYPE_AUDIO` and `source == SOURCE_MICROPHONE`.
- Never substitute `SOURCE_AUDIOCAPTURE` or `SOURCE_OTHER` for a physical microphone.
- If microphone audio was requested but no valid microphone source is offered, reject the media request.
- Log the offered sources and the chosen source using safe identifiers only.

Camera selection, when requested from a trusted media origin, follows the same original-object rule and may select the first `TYPE_VIDEO + SOURCE_CAMERA`. Screen capture is not implicitly granted.

## 8. Android runtime permission coordinator

Allowed dangerous-permission whitelist:

- `android.permission.RECORD_AUDIO`
- `android.permission.CAMERA`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_COARSE_LOCATION`

Unknown permissions are rejected. The coordinator must never grant every requested string by default.

State rules:

- If every requested whitelisted permission is already granted, invoke Gecko callback grant immediately.
- Otherwise create exactly one pending transaction, start the Android permission dialog, and resolve it from `onRequestPermissionsResult`.
- A superseding request must explicitly reject the prior pending callback; no callback may be leaked.
- Android permission success is logged as an Android-layer fact only and must not be promoted to `MediaStream` or recording success.

## 9. Trusted media-origin policy

Media permission is allowed only for HTTPS origins whose canonical host is exactly or is a true subdomain of:

- `chatgpt.com`
- `openai.com`

Allowed examples: `https://chatgpt.com`, `https://voice.chatgpt.com`, `https://openai.com`, `https://auth.openai.com`.

Rejected examples include HTTP, malformed URIs, user-info confusion, `evilchatgpt.com`, `chatgpt.com.evil.example`, `file://`, `content://`, `files.oaiusercontent.com`, and `cdn.oaistatic.com`.

Navigation trust and media trust are separate policies.

## 10. Navigation policy

Top-level navigation behavior:

- `https://...` → allowed in Gecko, including OAuth/verification hosts.
- `http://...` → denied inside Gecko and handed to an external Android browser intent when resolvable.
- `mailto:`, `tel:`, `intent:` and other supported external schemes → denied inside Gecko and handed to an external intent only when safely resolvable.
- `file://` → denied.
- `content://` → not permitted as arbitrary top-level web navigation. Content URIs are accepted only as user-selected SAF results passed to Gecko file prompts.
- malformed/unsupported schemes → denied without crashing.

No broad HTTPS domain allowlist is maintained for normal navigation.

## 11. Recording-state diagnostics

`MediaDelegate.onRecordingStatusChanged()` is the authoritative Gecko observation point. A microphone success event requires a `RecordingDevice` where `type == RecordingDevice.Type.MICROPHONE` and `status == RecordingDevice.Status.RECORDING`.

Log tags:

- `ChatGPTWeb/Permission`
- `ChatGPTWeb/Media`
- `ChatGPTWeb/Session`
- `ChatGPTWeb/Navigation`
- `ChatGPTWeb/Recovery`

Required semantic events include Android permission request/result, media request, offered source summary, selected source, and recording status transition.

Forbidden log content includes cookies, authorization headers, access/refresh/session tokens, ChatGPT conversation text, account identifiers, WebRTC SDP, request/response bodies, and full credential-bearing URLs. Diagnostics log origin scheme/host and safe media-source metadata only.

## 12. Session state and 10-minute background policy

On genuine app background:

- record `backgroundAt = now` only when no external system flow is active;
- `session.setActive(false)`;
- explicitly call `session.flushSessionState()` as a clear persistence request.

On foreground:

- `session.setActive(true)`;
- elapsed `< 600000 ms`: no `reload()`, no `loadUri()`, no `restoreState()`;
- elapsed `>= 600000 ms`: exactly one `reload()` for that foreground generation.

Boundary tests must include 599999 ms and 600000 ms.

`reloadGeneration` or an equivalent monotonic token prevents `onStart`, `onResume`, and focus callbacks from causing duplicate reloads.

`ProgressDelegate.onSessionStateChange()` persists the latest `SessionState.toString()` to `SharedPreferences`. Process recreation uses `SessionState.fromString()`; invalid values are ignored safely. A restored state must originate from Gecko session-state callbacks.

## 13. External flow isolation

`ExternalFlowTracker` initially tracks:

- `ANDROID_PERMISSION`
- `FILE_PICKER`

Activity stop/resume caused by one of these flows does not start the ten-minute background timer and does not trigger reload evaluation. Each external flow must be cleared on completion/cancel paths.

## 14. Content-process crash recovery

Both `ContentDelegate.onCrash()` and `ContentDelegate.onKill()` are handled.

Recovery sequence:

1. detach/release the closed session from `GeckoView`;
2. create a fresh `GeckoSession`;
3. register all delegates;
4. open on the existing process-level runtime;
5. bind to `GeckoView`;
6. restore the last valid saved session state, or load `https://chatgpt.com/` when unavailable.

Crash-loop guard: if three content-session crashes/kills occur inside a rolling 60-second window, automatic recovery stops and a simple recovery UI is displayed. User-triggered reload begins a fresh recovery attempt; the app must not enter an infinite content-process restart loop.

## 15. Login and browser storage migration

The Android package identity can remain upgrade-compatible while browser stores differ. WebView cookies are not migrated into GeckoView. 0.3.x authenticated state therefore may require one GeckoView login after upgrade.

The implementation must not read, export, log, transform, or inject old WebView cookies. After Gecko login, Gecko's own profile persists cookies, localStorage, IndexedDB, and related browser data.

## 16. File upload

Use `GeckoSession.PromptDelegate.onFilePrompt()` and Android `ACTION_OPEN_DOCUMENT` through the Storage Access Framework.

Requirements:

- single-file and multi-file selection;
- image, PDF, and ordinary document MIME filters from Gecko's prompt when meaningful;
- return only user-selected `content://` URIs through the prompt's `confirm(...)` API;
- chooser cancellation dismisses the Gecko prompt;
- replacing an outstanding chooser resolves/dismisses the older prompt first;
- no `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE`.

Folder upload and direct camera capture are outside the first 0.4.0 file-flow implementation unless separately approved.

## 17. Manifest baseline

Declared permissions:

- `INTERNET`
- `RECORD_AUDIO`
- `MODIFY_AUDIO_SETTINGS`
- `CAMERA`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

Hardware features are declared `required=false` for microphone, camera, and location-related hardware where applicable.

Application/activity constraints:

- `android:allowBackup="false"`
- `android:usesCleartextTraffic="false"`
- `android:hardwareAccelerated="true"`
- `android:resizeableActivity="true"`
- normal status/navigation bars; no forced edge-to-edge migration in 0.4.0
- `windowSoftInputMode="adjustResize"`

Forbidden additions include `FOREGROUND_SERVICE`, broad package-query privileges, overlay permission, and broad storage permissions unless a later approved requirement proves necessity.

## 18. Build and offline reproducibility

0.4.0 migrates from manual javac/D8/AAPT2 APK assembly to Gradle + Android Gradle Plugin because GeckoView has a Maven dependency graph and native assets.

The build environment is not considered prepared after one online build. Acceptance requires a populated dedicated Gradle dependency cache followed by a second clean offline build that succeeds with `--offline` and emits the agreed `GECKOVIEW_OFFLINE_OK` marker from the verifier.

The dependency payload records exact versions and hashes and excludes keystores, passwords, cookies, tokens, and account data.

## 19. Signing and upgrade gate

Expected release signer SHA-256:

`75a7d74cfaefc58a816c8a1b8febe9c13456661b6da3977ff3d2abfe072018f2`

The repository/library stores public signing metadata only. Formal release flow is build unsigned/release artifact → static verification → sign using externally protected keystore → `apksigner verify --verbose --print-certs` → compare signer SHA-256.

Only a final APK whose signer digest exactly matches the baseline can be labeled `IN_PLACE_UPGRADE_COMPATIBLE`. Otherwise release is blocked. A failed signature gate must not be bypassed by uninstalling the working app merely to force an install.

## 20. Gecko Probe A/B gate

Before same-package upgrade, build a separate diagnostic application:

- App name: `ChatGPT Web Gecko Probe`
- Application ID: `com.liuchenlin.chatgptweb.geckoprobe`
- same engine, media, navigation, lifecycle, upload, and diagnostics implementation as formal 0.4.0;
- coexist with 0.3.x;
- use non-release/debug signing;
- do not read or modify 0.3.x app data.

The implementation should use a build flavor or application-ID suffix so Probe and formal 0.4.0 share the same source modules instead of forking code.

Probe must be validated first on the same device. Only after Probe evidence is collected may the formal package be signed and tested as an in-place 0.3.x → 0.4.0 upgrade.

## 21. A/B interpretation matrix

- `SOURCE_MICROPHONE` selected + `MICROPHONE/RECORDING` + ChatGPT Voice works → System WebView/Chromium path becomes the leading prior root-cause candidate.
- `SOURCE_MICROPHONE` selected + `MICROPHONE/RECORDING` + Voice fails → investigate ChatGPT Voice/WebRTC/network/UA/signaling independently.
- microphone source offered but never reaches recording → investigate Gecko-to-Android audio bridge / 卓易通 layer.
- no `SOURCE_MICROPHONE` offered → lower-level audio-device exposure/compatibility problem.
- Android `RECORD_AUDIO` cannot be granted → Android/卓易通 permission layer.
- page exposes no Voice feature → Gecko/Firefox UA or product support path; do not spoof UA inside 0.4.0 to hide this result.

## 22. Test levels

### L1 — Static contracts

Validate package/version/SDK/Java/Gecko coordinate, Universal APK rule, manifest security constraints, real-UA rule, and absence of every retired WebView/dictation/shim identifier.

### L2 — Unit tests

Required policy suites:

- `TrustedOriginPolicy`
- media source selection
- `NavigationPolicy`
- `AndroidPermissionCoordinator`
- `BackgroundReloadPolicy`
- `SessionStateStore`
- `ExternalFlowTracker`
- `CrashRecoveryPolicy`
- `FilePromptCoordinator`

### L3 — Offline build

`gradle --offline clean assemble...` succeeds using only the prepared cache.

### L4 — APK static verification

Validate ZIP integrity, alignment, manifest, DEX/resources, native libraries, package/version/SDK, Universal ABI contents, forbidden identifiers, and signer metadata when signed.

### L5 — real-device Gecko Probe

Install and test launch, login, ordinary chat, file upload, media-source observations, recording state, Voice, background behavior, and process recovery.

### L6 — formal same-package upgrade

Only after Probe evidence, build/sign formal `com.liuchenlin.chatgptweb` and test 0.3.x → 0.4.0 without data-destructive uninstall.

## 23. Definition of Done

`ChatGPT Web 0.4.0 Beta — Ready for Device Validation` is allowed only when:

- exact GeckoView dependency cache is complete;
- clean `--offline` build passes;
- System WebView source count is zero;
- SpeechRecognizer/native dictation source count is zero;
- permission JavaScript shim source count is zero;
- all contract/unit tests pass;
- navigation/media-origin policies pass adversarial cases;
- file upload works in the implemented Gecko prompt path;
- session state persistence/restore is implemented and tested;
- 599999/600000 ms boundary passes;
- external flows do not contaminate background timing;
- content crash/kill recovery and 3-per-60-second guard exist;
- APK static verification passes;
- formal signer SHA-256 matches the fixed baseline;
- no secret/cookie/token/conversation logging exists.

`Gecko real microphone media path validated` is allowed only after device evidence contains an offered/selected physical microphone source and `RecordingDevice.Type.MICROPHONE + RecordingDevice.Status.RECORDING`.

`0.4.0 Voice target achieved` is allowed only after ChatGPT Voice establishes usable two-way audio on the target device.

## 24. Explicitly deferred

The following are not part of 0.4.0 unless a new approved change request is written:

- Chrome UA spoofing;
- Android Components migration;
- WebView fallback or dual-engine switch;
- ABI-size optimization such as arm64-only packaging;
- migration/injection of WebView cookies;
- broad storage access;
- native Android speech-recognition fallback;
- speculative device-name heuristics for microphone choice.

## 25. Verification references

Implementation must be checked against the pinned GeckoView public API and official Android build compatibility documentation before final compilation. In particular, verify the signatures of `PermissionDelegate`, `MediaCallback`, `MediaDelegate`, `ProgressDelegate`, `NavigationDelegate`, `PromptDelegate.FilePrompt`, `ContentDelegate`, `SessionState`, `setActive`, `flushSessionState`, and `restoreState` for the pinned GeckoView artifact or its matching source/Javadoc.
