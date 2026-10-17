# ChatGPT Web 0.4.0 Beta 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a verified Probe-first Beta 2 with source-converged identity, first-class downloads, standard IME anchoring, and composition-safe Enter-to-send.

**Architecture:** Preserve GeckoView and the existing Android shell. Add one download coordinator fed directly by `ContentDelegate.onExternalResponse`, and extend the existing `ChatGPTGeckoView` input connection rather than injecting JavaScript or controlling third-party IME UI.

**Tech Stack:** Java 17, Android API 26-37, GeckoView 153.0.20260810162159, Python contract tests, Java host tests, Android command-line build/repackaging.

**Spec:** `docs/superpowers/specs/2026-08-26-beta2-download-ime-enter-design.md`

## Global Constraints

- `versionCode=41`, `versionName=0.4.0 Beta 2`.
- Formal package `com.liuchenlin.chatgptweb`; Probe suffix `.geckoprobe`.
- All user-visible app labels equal `ChatGPT Web`.
- No new broad storage permission; API29+ uses MediaStore, API26-28 uses SAF save-as.
- No second HTTP request for Gecko external responses.
- Never synthesize send while IME composition is active.
- No custom/overlay candidate UI.
- Formal release requires signer continuity with Beta1 Formal.

---

### Task 1: Source convergence and Beta 2 identity

**Files:** modify `app/build.gradle`, `app/src/main/res/values/strings.xml`, `tests/test_beta2_contract.py`.

**Interfaces:** source itself produces final brand/version; later packaging must not patch branding.

- [ ] Write failing Python tests asserting vc41/Beta2 and `ChatGPT Web` for both flavor placeholders and string resources, plus no legacy launcher labels.
- [ ] Run focused test and verify RED.
- [ ] Make the minimal source edits.
- [ ] Run focused test and full Python suite; verify GREEN.

### Task 2: Download metadata policy

**Files:** create `app/src/main/java/com/liuchenlin/chatgptweb/DownloadMetadata.java`, create `host-tests/com/liuchenlin/chatgptweb/DownloadMetadataTest.java`, extend stubs only as required for compilation.

**Interfaces:** `DownloadMetadata.from(WebResponse): DownloadMetadata`; fields `fileName`, `mimeType`.

- [ ] Write a host test for filename*, quoted filename, URI fallback, unsafe name sanitization, and MIME parameter stripping; verify RED because class is absent.
- [ ] Implement deterministic parsing/sanitization with no Android dependency.
- [ ] Run host test and verify GREEN.

### Task 3: Download coordinator and external-response plumbing

**Files:** create `DownloadCoordinator.java`; modify `GeckoSessionController.java`, `MainActivity.java`, `ExternalFlowTracker.java`; update `GeckoSession.java`/`WebResponse.java` host stubs; create `DownloadCoordinatorTest.java`; extend `GeckoSessionControllerTest.java`.

**Interfaces:** `DownloadCoordinator.handle(WebResponse)`, `onLegacySaveResult(Uri)`, `abandonPending()`; `GeckoSessionController` receives a `DownloadHandler` and forwards only current-session external responses.

- [ ] Write failing host/controller tests: current session forwards external response once; stale session closes body; missing body is ignored safely; cleanup abandons pending download; legacy save flow keeps session active.
- [ ] Verify RED.
- [ ] Implement coordinator using API29+ MediaStore and API26-28 ACTION_CREATE_DOCUMENT, streaming in a bounded worker thread and deleting pending MediaStore rows on error.
- [ ] Wire lifecycle and activity result handling.
- [ ] Run host/controller tests and Android contract compile; verify GREEN.

### Task 4: IME source cleanup and composition-safe Enter-to-send

**Files:** modify `ChatGPTGeckoView.java`, `MainActivity.java`; create `ImeActionPolicy.java`; create `host-tests/.../ImeActionPolicyTest.java`; extend Python contract test.

**Interfaces:** `ImeActionPolicy.configure(EditorInfo)`; `ChatGPTGeckoView` returns an `InputConnectionWrapper` that delegates normal input and emits Enter down/up for `IME_ACTION_SEND` only when not composing.

- [ ] Write failing policy/contract tests for SEND action, no-enter clearing, no-extract/no-fullscreen preservation, and removal of runtime `setSoftInputMode` mutation.
- [ ] Verify RED.
- [ ] Implement policy and wrapper, tracking composition without storing text.
- [ ] Run host/contract/Android compile; verify GREEN.

### Task 5: Full regression and Probe APK

**Files:** update `tests/test_040_contract.py`/verification tooling only where version-specific expectations require; produce review/final-gate files.

- [ ] Run full Python suite, all Java host tests, Android API37 compile, forbidden-symbol and secret scans.
- [ ] Reconstruct the verified Android build toolkit components if not already present.
- [ ] Reconstruct verified Beta1 Formal APK from its 9-part release and confirm SHA-256 `4324f28b...e110e4` before using it as payload baseline.
- [ ] Build Beta2 app DEX and package Probe identity vc41/Beta2 while preserving Gecko/native/third-party payload.
- [ ] Sign Probe with a newly generated **test-only** certificate if no reusable Probe key exists; never use it for Formal.
- [ ] Verify badging, permissions, v2/v3 signature, 16KiB alignment, service/provider/receiver counts, native `.so` and DEX counts, payload diff, and `adb devices -l`.

### Task 6: Formal-release gate, archive and persistence

**Files:** release review, hashes, source snapshot, 7z parts only when gates allow.

- [ ] Search project/Library for the Beta1 Formal private key without exposing key material.
- [ ] If absent, record `FORMAL_RELEASE_BLOCKED_SIGNING_KEY_UNAVAILABLE` and do not create a Formal APK.
- [ ] Archive source and Probe artifacts with fresh hashes.
- [ ] Upload canonical Beta2 docs/source/release artifacts under `/任务项目/01-软件开发/ChatGPT-Web/` without deleting Beta1.
