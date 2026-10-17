# ChatGPT Web 0.4.0 Beta 1 BrowserFit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 将 R4 MediaGuard 收敛为 0.4.0 Beta 1，完成华为平板系统栏/IME 适配，并减少无意义 reload 与状态写入以提升二次加载和后台恢复速度。

**Architecture:** R5A 只在 Android 壳层处理系统栏、软键盘和 GeckoView 输入连接，不改 ChatGPT 网页和第三方输入法窗口。R5B 不自建 HTTP 缓存，而是保留 Gecko 原生缓存/站点数据，通过减少外部流程 flush、重复 SessionState 写入和无必要后台 reload 提升恢复速度。

**Tech Stack:** Java 17, Android API 26-37, GeckoView 153.0.20260810162159, Python contract tests, Java host tests, hybrid APK repackaging.

**Spec:** Approved in chat on 2026-08-26: R5A UI/IME followed by R5B Browser/Cache, final version named 0.4.0 Beta 1.

## Global Constraints

- GeckoView 固定 `org.mozilla.geckoview:geckoview:153.0.20260810162159`。
- `minSdk 26`, `targetSdk 28`, `compileSdk 37`, Java 17。
- Probe package 仍为 `com.liuchenlin.chatgptweb.geckoprobe`，versionCode 40。
- versionName 改为 `0.4.0 Beta 1`。
- 不引入 WebView fallback、UA spoof、SpeechRecognizer 或 JS permission bridge。
- 保留 R4 MediaGuard、权限集合、39 个 Gecko native `.so`、文件上传和物理麦克风选择规则。
- 第三方输入法候选窗位置不可由应用强制；应用只负责标准 Window/IME 条件。

---

### Task 1: Beta 1 identity and system bars

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/SystemBarPolicy.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Test: `tests/test_beta1_browserfit_contract.py`
- Test: `host-tests/com/liuchenlin/chatgptweb/SystemBarPolicyTest.java`

**Interfaces:**
- Produces: `SystemBarPolicy.isDarkMode(int uiMode): boolean` and `MainActivity.applySystemBars()`.

- [x] Write failing tests requiring versionName `0.4.0 Beta 1`, white bars/dark icons in light mode and black bars/light icons in dark mode.
- [x] Run tests and confirm RED.
- [x] Implement `SystemBarPolicy` and programmatic Window colors/system UI flags for API 26+.
- [x] Run focused tests and confirm GREEN.

### Task 2: IME/tablet input adaptation

**Files:**
- Create: `app/src/main/java/com/liuchenlin/chatgptweb/ChatGPTGeckoView.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Test: `tests/test_beta1_browserfit_contract.py`

**Interfaces:**
- Produces: `ChatGPTGeckoView.onCreateInputConnection(EditorInfo)` adding `IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN`.

- [x] Write RED tests requiring custom GeckoView and programmatic `SOFT_INPUT_ADJUST_RESIZE`.
- [x] Implement custom GeckoView and Window soft-input mode.
- [x] Run contract and Android API 37 compile tests.

### Task 3: Cache-friendly lifecycle and SessionState writes

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/SessionStateStore.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/GeckoSessionController.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/BackgroundReloadPolicy.java`
- Test: `host-tests/com/liuchenlin/chatgptweb/SessionStateStoreTest.java`
- Test: `host-tests/com/liuchenlin/chatgptweb/GeckoSessionControllerTest.java`
- Test: `host-tests/com/liuchenlin/chatgptweb/BackgroundReloadPolicyTest.java`

**Interfaces:**
- `SessionStateStore.hasSerializedSessionState(): boolean`.
- `BackgroundReloadPolicy.consumeReload(backgroundAt, now, generation, hasRestorableState): boolean` reloads only after 600000ms when no restorable state exists.

- [x] Write RED tests: duplicate serialized state does not rewrite backend; external permission/file-picker stop does not deactivate/flush; >=600000ms with restorable state resumes without reload; >=600000ms without state reloads once.
- [x] Implement minimal lifecycle and state-store changes.
- [x] Run all Java host tests.

### Task 4: Full regression and Beta 1 APK

**Files:**
- Modify: `tests/test_040_contract.py`
- Modify: `tools/verify_apk_040.py` if version-name verification requires Beta 1.
- Create: `docs/0.4.0-beta1-browserfit-test-android-apps-review-2026-08-26.md`

- [x] Run full `tools/verify_host_040.sh`.
- [x] Rebuild application DEX with real Android API 37 + Gecko stubs.
- [x] Rebuild binary Manifest so versionName becomes `0.4.0 Beta 1`; preserve R4 merged Gecko components and permissions.
- [x] Repack from R4 APK, preserving third-party DEX/native/resources and replacing only allowed app artifacts.
- [x] Verify package/version/label/icon/permissions/signature/16KiB and R4→Beta1 content diff.
- [x] Run `adb devices -l`; do not claim device pass if count is 0.

### Task 5: 7z delivery and persistence

- [x] Create true 7z archive and 30 MiB `.001...` volumes.
- [x] Rejoin volumes, extract APK, verify SHA-256 exact match.
- [x] Archive Beta 1 source.
- [x] Upload volumes/source/review/final-gate to `/工具/ChatGPT-Web/` without deleting R4.


## Execution note

Completed inline on 2026-08-26 from the isolated `/mnt/data/beta1-work` archive workspace. Final Probe APK uses a hybrid rebuild from the verified R4 Gecko payload: only binary `AndroidManifest.xml` and application `classes3.dex` changed; Gecko/native/third-party payload remained byte-identical. Because the R4 test private key was intentionally not persisted, Beta 1 uses a new temporary Probe signing certificate and therefore requires uninstalling the old Probe package before install if Android reports a signature mismatch.
