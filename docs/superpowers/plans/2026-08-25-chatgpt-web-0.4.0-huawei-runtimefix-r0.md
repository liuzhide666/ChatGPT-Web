# ChatGPT Web 0.4.0 Huawei RuntimeFix R0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a Huawei/HarmonyOS diagnostic Gecko Probe R0 that preserves GeckoView 153, fully rebuilds Chinese app resources/icons, captures the first `GeckoRuntime.create()` failure safely, and never retries runtime creation inside the same failed process.

**Architecture:** Keep the frozen single-GeckoView architecture. Move runtime ownership into a process-scoped state machine; persist only sanitized startup diagnostics; expose a Chinese recovery surface that restarts the app in a fresh process rather than calling `GeckoRuntime.create()` again. Rebuild the resource/manifest layer from source so launcher label and 0.3.1 icon assets are actually present in the APK.

**Tech Stack:** Java 17, Android SDK 37 / targetSdk 28 / minSdk 26, AGP 9.1.1, Gradle 9.3.1, GeckoView `153.0.20260810162159`, Python contract tests, Android build-tools 36.0.0.

**Spec:** Approved in chat on 2026-08-25: Huawei RuntimeFix Probe R0 design.

## Global Constraints

- Formal package remains `com.liuchenlin.chatgptweb`; Probe remains `com.liuchenlin.chatgptweb.geckoprobe`.
- `versionCode=40`, `versionName=0.4.0 Beta`.
- GeckoView remains exactly `org.mozilla.geckoview:geckoview:153.0.20260810162159`.
- No System WebView fallback, Android Components, SpeechRecognizer, old JS permission bridge, native dictation, or Chrome UA spoofing.
- Runtime retry after a failed creation must occur only in a fresh app process.
- Diagnostics must not include cookie, token, account identifiers, chat text, request body, SDP, or sensitive URL query.
- Android-owned visible UI should be Chinese; technical identifiers such as `GeckoRuntime` may remain inside Chinese explanatory text.
- Launcher icon assets must match the known-working 0.3.1 baseline.

---

### Task 1: Capture the first GeckoRuntime failure safely

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/StartupStateStore.java`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/StartupFailureInfo.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/view_startup_recovery.xml`
- Test: `tests/test_runtimefix_r0_contract.py`
- Test: `host-tests/com/liuchenlin/chatgptweb/StartupFailureInfoTest.java`

**Interfaces:**
- Produces `StartupFailureInfo.from(Throwable, StartupStateStore.Stage)` and sanitized display fields.
- Persists `last_failure_type`, `last_failure_message`, `last_failure_cause_type`, `last_failure_cause_message` through `StartupStateStore`.

- [ ] Write Python and Java tests requiring stage, exception class/message, cause class/message, and secret redaction.
- [ ] Run tests and verify RED.
- [ ] Implement `StartupFailureInfo` and persistence without logging raw stack traces.
- [ ] Update recovery UI to show Chinese failure details.
- [ ] Run focused tests and verify GREEN.

### Task 2: Enforce process-scoped GeckoRuntime state and fresh-process retry

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/GeckoRuntimeHolder.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Create: `host-tests/com/liuchenlin/chatgptweb/GeckoRuntimeStateTest.java`
- Modify: `tests/test_runtimefix_r0_contract.py`

**Interfaces:**
- `GeckoRuntimeHolder.State = NOT_CREATED | CREATING | READY | FAILED`.
- `GeckoRuntimeHolder.get(Context)` may create once; after failure it throws a deterministic state exception and never invokes `GeckoRuntime.create()` again in that process.
- Recovery button invokes a fresh-process restart path instead of `initializeGecko()`.

- [ ] Add RED tests for single creation, failed-state lockout, and no same-process retry.
- [ ] Implement minimal state machine.
- [ ] Move `GeckoView` construction until after runtime creation succeeds.
- [ ] Replace retry button behavior with restart-current-task-and-terminate-current-process logic.
- [ ] Run focused and full host tests.

### Task 3: Full Chinese resource/icon contract

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/view_startup_recovery.xml`
- Verify: `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`
- Verify: `app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png`
- Verify: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Verify: `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- Test: `tests/test_chinese_ui_startup_contract.py`
- Test: `tests/test_runtimefix_r0_contract.py`

**Interfaces:**
- Probe launcher label: `ChatGPT 网页版（兼容测试版）`.
- Formal launcher label: `ChatGPT 网页版`.

- [ ] Write/adjust RED resource assertions for final labels and actual icon references.
- [ ] Verify restored icon assets against 0.3.1 hashes.
- [ ] Update resources/manifest/Gradle placeholders.
- [ ] Run resource contracts GREEN.

### Task 4: Build and audit R0 APK

**Files:**
- Modify: `tools/verify_apk_040.py` only if needed for new label/icon assertions.
- Create: `docs/0.4.0-huawei-runtimefix-r0-verification-2026-08-25.txt`

**Interfaces:**
- Output APK name: `ChatGPT-Web-0.4.0-Beta-Huawei-RuntimeFix-R0-vc40.apk`.

- [ ] Run `tools/verify_host_040.sh` and require all gates green.
- [ ] Prefer frozen Gradle offline build if Gecko/AGP cache is available; otherwise rebuild app-owned manifest/resources/classes against the already verified Gecko 153 payload without changing third-party bytes, and document the boundary.
- [ ] Verify package/version/SDK/permissions/ABI/16K alignment/signature/launcher label/icon/Dex boundaries.
- [ ] Record APK SHA-256 and exact build method.

### Task 5: Package and hand off for Huawei test

**Files:**
- Create: `apk/probe/ChatGPT-Web-0.4.0-Beta-Huawei-RuntimeFix-R0-vc40.7z.001...`
- Create: `apk/probe/README-Huawei-RuntimeFix-R0-只解压001.txt`
- Create: `apk/probe/ChatGPT-Web-0.4.0-Beta-Huawei-RuntimeFix-R0-vc40-7Z-PARTS-SHA256.txt`
- Create: `source/ChatGPT-Web-source-0.4.0-dev-2026-08-25-huawei-runtimefix-r0.tar.gz`

**Interfaces:**
- 30 MiB true 7z volumes; `.001` must start with standard 7z signature.

- [ ] Create true 7z archive and 30 MiB volumes.
- [ ] Rejoin, extract, and prove APK SHA-256 round-trip equality.
- [ ] Upload source, verification, README, checksum and volumes to `/工具/ChatGPT-Web/` without deleting the previous Codex or diagnostic baselines.
- [ ] Ask the user to test: launcher icon/name → open shell → start runtime → if failure, photograph the detailed first-failure page.
