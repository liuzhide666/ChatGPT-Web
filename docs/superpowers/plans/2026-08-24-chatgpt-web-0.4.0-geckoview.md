# ChatGPT Web 0.4.0 GeckoView Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a single-engine GeckoView ChatGPT wrapper, validate the engine through a side-by-side Gecko Probe first, then produce a signer-gated same-package 0.4.0 Beta for real-device validation.

**Architecture:** A fresh Java 17 Android application shares one implementation between Probe and formal release variants. One process-wide `GeckoRuntime` feeds one current `GeckoSession`; focused policy/coordinator classes own media permissions, Android permissions, navigation, lifecycle/session persistence, external flows, crash recovery, uploads, and safe diagnostics. The build is Gradle/AGP-based and is not accepted until the exact dependency graph builds cleanly offline.

**Tech Stack:** Java 17, Android minSdk 26 / targetSdk 28 / compileSdk 37, Gradle 9.3.1, Android Gradle Plugin 9.1.1, SDK Build Tools 36.0.0, GeckoView `153.0.20260810162159`, JUnit 4, Python contract tests, ADB/logcat, apksigner.

**Spec:** `docs/superpowers/specs/2026-08-24-chatgpt-web-0.4.0-geckoview-design.md`

## Global Constraints

- Formal package: `com.liuchenlin.chatgptweb`; Probe package: `com.liuchenlin.chatgptweb.geckoprobe`.
- Formal version: `versionCode 40`, `versionName "0.4.0 Beta"`.
- `minSdk 26`, `targetSdk 28`, `compileSdk 37`; Java 17.
- Gradle 9.3.1 + AGP 9.1.1 + Build Tools 36.0.0.
- GeckoView is pinned to `153.0.20260810162159`; no dynamic version and no in-cycle Gecko upgrade.
- Universal/Fat APK for 0.4.0; no `abiFilters` narrowing.
- No System WebView, WebChromeClient, WebView PermissionRequest, SpeechRecognizer/RecognizerIntent, native dictation bridge, or JavaScript permission shim in `app/src/main`.
- Media grants use original Gecko-provided `MediaSource` objects; physical microphone means `TYPE_AUDIO + SOURCE_MICROPHONE` only.
- Default Gecko UA only; no Chrome UA spoofing.
- No credential/token/cookie/conversation/body/SDP logging.
- Formal release signer SHA-256 must equal `75a7d74cfaefc58a816c8a1b8febe9c13456661b6da3977ff3d2abfe072018f2`.
- Probe validation precedes formal same-package signing/install validation.

---

### Task 1: Prepare and prove the exact offline Gradle/GeckoView dependency payload

**Files:**
- Create: `tools/prepare_geckoview_offline.sh`
- Create: `geckoview-offline/bootstrap-project/settings.gradle`
- Create: `geckoview-offline/bootstrap-project/build.gradle`
- Create: `geckoview-offline/bootstrap-project/app/build.gradle`
- Create: `geckoview-offline/bootstrap-project/app/src/main/AndroidManifest.xml`
- Create: `geckoview-offline/bootstrap-project/app/src/main/java/com/liuchenlin/geckoviewbootstrap/MainActivity.java`
- Create: `geckoview-offline/gradle/verification-metadata.xml`
- Create: `geckoview-offline/dependency-report.txt`
- Create: `geckoview-offline/versions.txt`
- Create: `geckoview-offline/SHA256SUMS.txt`
- Create: `geckoview-offline/verify_geckoview_offline.sh`
- Create: `geckoview-offline/offline-verify.log`

**Interfaces:**
- Consumes: restored JDK 17, Gradle 9.3.1, Android SDK platform 37 and Build Tools 36.0.0.
- Produces: isolated `GRADLE_USER_HOME` capable of resolving AGP 9.1.1 and GeckoView 153 with all transitives using `--offline`.

- [ ] **Step 1: Create a minimal bootstrap project** whose only Android dependency is `org.mozilla.geckoview:geckoview:153.0.20260810162159`, with repositories restricted to Google, Maven Central, and Mozilla Maven.
- [ ] **Step 2: Warm the dedicated cache in an internet-capable Linux environment** using exactly JDK 17 and Gradle 9.3.1; preserve the full dependency graph rather than copying only the Gecko AAR.
- [ ] **Step 3: Run the clean offline verifier** with network disabled/`--offline`; it must execute `clean assembleDebug` and print `GECKOVIEW_OFFLINE_OK` only after `BUILD SUCCESSFUL`.
- [ ] **Step 4: Record exact versions, dependency graph and immutable hashes**; run `sha256sum -c geckoview-offline/SHA256SUMS.txt` and save output.
- [ ] **Step 5: Run a secret scan** over the returned payload and reject any keystore, password, token, cookie, account data, or unrelated user file.
- [ ] **Step 6: Preserve `offline-verify.log`, `du -sh` result, Java/Gradle versions and hash verification result** as the acceptance evidence for this task.

### Task 2: Establish the fresh 0.4.0 project and static architecture contracts

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `app/build.gradle`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/styles.xml`
- Test: `tests/test_040_contract.py`

**Interfaces:**
- Consumes: Global Constraints.
- Produces: one source tree with `probe` and `formal` variants sharing production code.

- [ ] **Step 1: Write `tests/test_040_contract.py` first** to assert the formal/probe IDs, formal version, SDK values, Java 17, exact Gecko coordinate, no ABI narrowing, manifest security values, allowed permission set, absence of broad storage/foreground/overlay/package-query permissions, and absence of all retired WebView/dictation/shim identifiers.
- [ ] **Step 2: Run the contract suite and verify RED** because the 0.4.0 Gradle/manifest files do not yet exist.
- [ ] **Step 3: Add the minimal Gradle/manifest/resources required by the contracts**, using a flavor/applicationId-suffix design so Probe and formal builds share code.
- [ ] **Step 4: Re-run the contract suite and verify GREEN**.
- [ ] **Step 5: Once Task 1 is available, run `gradle --offline :app:assembleProbeDebug :app:assembleFormalDebug`** and preserve the first clean successful build log.

### Task 3: Implement pure policy classes with host-side TDD

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/TrustedOriginPolicy.java`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/NavigationPolicy.java`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/BackgroundReloadPolicy.java`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/CrashRecoveryPolicy.java`
- Test: `host-tests/com/liuchenlin/chatgptweb/TrustedOriginPolicyTest.java`
- Test: `host-tests/com/liuchenlin/chatgptweb/NavigationPolicyTest.java`
- Test: `host-tests/com/liuchenlin/chatgptweb/BackgroundReloadPolicyTest.java`
- Test: `host-tests/com/liuchenlin/chatgptweb/CrashRecoveryPolicyTest.java`
- Create: `tools/run_host_policy_tests.sh`

**Interfaces:**
- Produces: `TrustedOriginPolicy.isTrustedMediaUri(String)`, `NavigationPolicy.classify(String)`, `BackgroundReloadPolicy.shouldReload(long,long)`, and crash-loop decisions independent of Android/Gecko objects.

- [ ] **Step 1: Write failing trusted-origin tests** for root/subdomains and adversarial HTTP/user-info/suffix/malformed/null cases.
- [ ] **Step 2: Run only the origin test and verify RED** for missing production class.
- [ ] **Step 3: Implement the smallest URI/host policy and verify GREEN**.
- [ ] **Step 4: Write failing navigation classification tests** for HTTPS-in-Gecko, HTTP-external, supported custom-scheme external, `file://` deny, `content://` deny, malformed deny.
- [ ] **Step 5: Verify RED, implement `NavigationPolicy`, then verify GREEN**.
- [ ] **Step 6: Write failing background boundary tests** for 0, 599999, 600000 and greater-than-600000 ms plus duplicate-generation suppression.
- [ ] **Step 7: Verify RED, implement the minimal background policy, then verify GREEN**.
- [ ] **Step 8: Write failing crash-window tests** proving automatic recovery for crashes 1–2 and guard activation at the third crash within 60 seconds, while crashes outside the rolling window do not accumulate incorrectly.
- [ ] **Step 9: Verify RED, implement the minimal crash policy, then verify GREEN**.
- [ ] **Step 10: Run every host policy test together** and require zero failures.

### Task 4: Implement process-level runtime and session-state persistence with TDD

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/GeckoRuntimeHolder.java`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/SessionStateStore.java`
- Create: `app/src/test/java/com/liuchenlin/chatgptweb/SessionStateStoreTest.java`
- Test: extend `tests/test_040_contract.py`

**Interfaces:**
- Produces: one process-wide runtime accessor and SharedPreferences-backed serialized Gecko session/background state storage.

- [ ] **Step 1: Add RED static/runtime-holder contracts** that permit only one runtime creation site in production source and prohibit per-Activity recreation.
- [ ] **Step 2: Add RED store tests** for valid/null/invalid serialized state handling and background timestamp persistence.
- [ ] **Step 3: Implement `GeckoRuntimeHolder` and the smallest store API**; runtime settings do not set a UA override.
- [ ] **Step 4: Verify focused tests GREEN**, then run all contract/unit tests.

### Task 5: Implement Android runtime permission and external-flow coordinators with TDD

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/ExternalFlowTracker.java`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/AndroidPermissionCoordinator.java`
- Test: `app/src/test/java/com/liuchenlin/chatgptweb/ExternalFlowTrackerTest.java`
- Test: `app/src/test/java/com/liuchenlin/chatgptweb/AndroidPermissionCoordinatorTest.java`

**Interfaces:**
- Produces: explicit `ANDROID_PERMISSION`/`FILE_PICKER` flow state and one pending Gecko Android-permission callback transaction.

- [ ] **Step 1: Write RED flow tests** for begin/end, nested-invalid transition prevention, and cancellation cleanup.
- [ ] **Step 2: Verify RED, implement tracker, verify GREEN**.
- [ ] **Step 3: Write RED permission tests** for already-granted allowed permissions, dialog grant, dialog denial, unknown permission rejection, superseding request rejection, and lifecycle cancellation.
- [ ] **Step 4: Verify RED, implement the coordinator with the four-item whitelist, verify GREEN**.
- [ ] **Step 5: Add a lifecycle test proving permission-dialog stop/resume does not set `backgroundAt` or trigger ten-minute evaluation**.
- [ ] **Step 6: Run all tests GREEN**.

### Task 6: Implement Gecko media permission selection and recording diagnostics with TDD

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/MediaSelectionPolicy.java`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/GeckoMediaPermissionDelegate.java`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/MediaDiagnostics.java`
- Test: `app/src/test/java/com/liuchenlin/chatgptweb/MediaSelectionPolicyTest.java`
- Test: `tests/test_diagnostics_contract.py`

**Interfaces:**
- Consumes: exact GeckoView `PermissionDelegate.MediaSource[]` and Android permission state.
- Produces: original-object microphone/camera selection and privacy-safe `ChatGPTWeb/*` diagnostic events.

- [ ] **Step 1: Write RED media-source tests** selecting the first offered `TYPE_AUDIO + SOURCE_MICROPHONE`, rejecting `SOURCE_AUDIOCAPTURE`/`SOURCE_OTHER`, rejecting absent requested mic, and proving the returned object is the same instance Gecko offered.
- [ ] **Step 2: Verify RED against the pinned Gecko API**.
- [ ] **Step 3: Implement `MediaSelectionPolicy` and `GeckoMediaPermissionDelegate` minimally, then verify GREEN**.
- [ ] **Step 4: Write RED diagnostics contracts** requiring Android request/result, media request/source/selection, and recording transitions while rejecting labels/APIs that log cookies, authorization, tokens, bodies, SDP, conversation text, or account identifiers.
- [ ] **Step 5: Implement `MediaDiagnostics` and `MediaDelegate.onRecordingStatusChanged` using `RecordingDevice.Type.MICROPHONE` plus `RecordingDevice.Status.RECORDING`**, then verify GREEN.
- [ ] **Step 6: Run the complete test suite and an offline compile**.

### Task 7: Implement navigation delegate with external-intent boundary and real-UA contract

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/GeckoNavigationDelegate.java`
- Test: `app/src/test/java/com/liuchenlin/chatgptweb/GeckoNavigationDelegateTest.java`
- Test: extend `tests/test_040_contract.py`

**Interfaces:**
- Consumes: `NavigationPolicy.classify` and Android intent resolver.
- Produces: `NavigationDelegate.onLoadRequest` decisions without restricting ordinary HTTPS OAuth/verification domains.

- [ ] **Step 1: Write RED delegate tests/contracts** mapping policy classes to Gecko `ALLOW`/`DENY` behavior and requiring external intent resolution before launch.
- [ ] **Step 2: Add a RED static contract forbidding `userAgentOverride`, Chrome UA strings, and equivalent spoofing configuration**.
- [ ] **Step 3: Verify RED, implement the delegate, then verify GREEN**.
- [ ] **Step 4: Run all tests and offline compile**.

### Task 8: Implement file prompts and SAF lifecycle isolation with TDD

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/FilePromptCoordinator.java`
- Test: `app/src/test/java/com/liuchenlin/chatgptweb/FilePromptCoordinatorTest.java`

**Interfaces:**
- Consumes: `PromptDelegate.FilePrompt`, `ExternalFlowTracker`, Activity result URIs.
- Produces: `ACTION_OPEN_DOCUMENT` requests and Gecko prompt confirm/dismiss results.

- [ ] **Step 1: Write RED tests** for single file, multiple files, cancellation, MIME filtering, replacing an outstanding prompt, and external-flow cleanup on every terminal path.
- [ ] **Step 2: Verify RED**.
- [ ] **Step 3: Implement minimal SAF bridge using `content://` results and `FilePrompt.confirm(context, Uri/Uri[])`**.
- [ ] **Step 4: Verify GREEN and confirm manifest contains no broad storage permission**.
- [ ] **Step 5: Run all tests and offline compile**.

### Task 9: Implement Gecko session controller, lifecycle, and content-process recovery with TDD

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java`
- Create/modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Create: `app/src/main/res/layout/view_recovery.xml`
- Test: `app/src/test/java/com/liuchenlin/chatgptweb/GeckoSessionControllerTest.java`
- Test: `app/src/test/java/com/liuchenlin/chatgptweb/LifecyclePolicyTest.java`

**Interfaces:**
- Consumes: all previous policy/coordinator classes and singleton runtime.
- Produces: one current session, delegate registration before open, bind/load/restore, exact background behavior, latest-state persistence, and guarded crash/kill recovery.

- [ ] **Step 1: Write RED session-construction tests/contracts** proving delegates are installed before `open(runtime)` and only one current session is bound to the view.
- [ ] **Step 2: Write RED lifecycle tests** proving genuine background uses `setActive(false)` + `flushSessionState`, `<600000` resumes without navigation, `>=600000` reloads exactly once, and external flows bypass timer evaluation.
- [ ] **Step 3: Write RED state-callback tests** proving `onSessionStateChange` stores the latest serialized state and process recreation restores valid state or loads `https://chatgpt.com/`.
- [ ] **Step 4: Write RED `ContentDelegate.onCrash()` / `ContentDelegate.onKill()` tests** for fresh-session reconstruction, state restore/fallback load, and 3-crashes/60-seconds recovery UI activation.
- [ ] **Step 5: Implement `GeckoSessionController` and thin `MainActivity` wiring only after all focused RED states are observed**.
- [ ] **Step 6: Verify every focused test GREEN, then run all tests and an offline debug build**.

### Task 10: Build and statically verify the Gecko Probe

**Files:**
- Create: `tools/verify_apk_040.py`
- Create: `build/verification/probe/` evidence
- Create: `docs/0.4.0-gecko-probe-device-test-protocol.md`

**Interfaces:**
- Consumes: `probe` variant APK.
- Produces: coexistence-safe diagnostic APK and a repeatable same-device A/B protocol.

- [ ] **Step 1: Build Probe offline** and assert package `com.liuchenlin.chatgptweb.geckoprobe` and debug/non-release signer.
- [ ] **Step 2: Verify ZIP/alignment/manifest/DEX/resources/native libraries and Universal ABI contents**.
- [ ] **Step 3: Scan packaged DEX/resources for every forbidden WebView/dictation/shim identifier and fail on any match**.
- [ ] **Step 4: Write the device protocol** covering install, first Gecko login, ordinary chat, upload, Voice availability, Android permission, offered/selected source, recording event, `<10m`/`>=10m` lifecycle, and process recovery.
- [ ] **Step 5: Do not proceed to formal release labeling until real Probe evidence is returned and classified**.

### Task 11: Build, sign, and certificate-gate the formal 0.4.0 APK

**Files:**
- Create: `tools/verify_release_040.sh`
- Create: `build/verification/formal/` evidence

**Interfaces:**
- Consumes: Probe validation result and externally protected release keystore.
- Produces: formal APK only when the signer identity and all static contracts pass.

- [ ] **Step 1: Build formal release/unsigned artifact offline** and run the same package/version/SDK/ABI/forbidden-code static verifier.
- [ ] **Step 2: Sign using the external protected project keystore without committing or printing secrets**.
- [ ] **Step 3: Run `apksigner verify --verbose --print-certs`** and parse signer SHA-256.
- [ ] **Step 4: Hard-fail unless signer SHA-256 equals `75a7d74cfaefc58a816c8a1b8febe9c13456661b6da3977ff3d2abfe072018f2`**.
- [ ] **Step 5: Re-run the full contract/unit/offline-build/static-verification suite against the signed artifact**.
- [ ] **Step 6: Only after Step 5 may metadata say `IN_PLACE_UPGRADE_COMPATIBLE`**.

### Task 12: Perform formal same-package device validation and classify the media result

**Files:**
- Create: `docs/0.4.0-formal-device-test-protocol.md`
- Create after device test: `evidence/<date>/formal/logcat-media.txt`
- Create after device test: `evidence/<date>/formal/result.md`

**Interfaces:**
- Produces: final evidence for engine root-cause classification and Voice target status.

- [ ] **Step 1: Attempt 0.3.x → 0.4.0 same-package install without uninstalling existing app**; stop if Android reports signer incompatibility.
- [ ] **Step 2: Complete Gecko login if required and verify ordinary chat, upload, persistence, background policy, and recovery**.
- [ ] **Step 3: Capture privacy-filtered `ChatGPTWeb/*` and relevant Gecko media evidence while invoking ChatGPT Voice**.
- [ ] **Step 4: Record independently whether a physical `SOURCE_MICROPHONE` was offered/selected, whether `MICROPHONE + RECORDING` occurred, and whether two-way Voice worked**.
- [ ] **Step 5: Classify the result using the design matrix; do not overclaim WebView causality from Android permission alone**.
- [ ] **Step 6: Run final verification-before-completion checks before assigning any Ready/Validated/Achieved status label**.
