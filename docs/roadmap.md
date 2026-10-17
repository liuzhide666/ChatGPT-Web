# Engineering roadmap — dated 2026-10-17

**Goal:** modernize the working Android wrapper for compatible environments while investigating a separate HarmonyOS native ArkWeb variant. This is a plan, not a claim that work has been deployed.

## Hard gates

- G0: source identity, repo access and secret hygiene documented; public visibility is observed and must be respected; no secrets or private account data may be published.
- G1: same baseline tests pass in a reproducible CI; distinguish source-only checks from Android compilation and device certification.
- G2: user-visible changes protected by regression tests (keyboard transparency, recovery, auth preservation, attachments, media).
- G3: a clean build produces artifact + version + SHA-256 + package and signer info, no credential leak.
- G4: target-device A/B matches original website for input and lifecycle; include Android compatibility host version / HarmonyOS version in evidence.
- G5: explicit release approval; preserve 1.1.0 and 1.0.3 rollback resources.

## Phase 0 — repository governance (P0)

Deliverables: GitHub repository (currently PUBLIC), imported sanitized 1.1.0 source, immutable baseline manifest, least-privilege source CI, protected `main` (manual configuration), contributor and release policy, seed backlog.

**Acceptance:** fetched default-branch files match prepared source; CI runs on GitHub (not inferred from local results); remote visibility recorded accurately; no private key or signer password in history.

## Phase 1 — Android maintenance (proposed v1.1.1 / vc114)

1. Secure reproducible SDK/JDK/Gradle toolchain and build provenance; remove hard dependency on obsolete absolute local paths.
2. Review targetSdk 28 migration and modern window insets/edge-to-edge; separate behavior migrations by testable batches, not one silent jump.
3. Evaluate GeckoView 153 → candidate newer release with release notes and ABI/API checks; keep Gecko 153 until device pass.
4. Regression tests: Chinese IME/Enter/Shift+Enter, long conversation, foreground/Recents, no data loss, file/media permission and external OAuth.
5. Test signed update/install continuity in Android compatible runtime, then only if passed create v1.1.1 release.

**Acceptance:** all existing tests pass, clean Android build passes, signing continuity and full device A/B pass; no automatic cache/cookie purge or new keyboard interception.

## Phase 2 — Harmony native feasibility (research spike)

1. Inventory device: exact HarmonyOS 7 build/API, device form factor and available official DevEco/ArkWeb tooling.
2. Minimal independent ArkTS/ArkWeb HAP: open trusted `https://chatgpt.com`, inspect login and redirect, persistence, editor/IME, long answer rendering, attachment picker, microphone and download.
3. Record each pass/fail including service/browser restrictions; investigate whether site features depend on unsupported Web APIs.
4. Produce go/no-go ADR and separate target-device test report; do not assume prior GeckoView successes transfer.

**Acceptance:** reproducible native build and installation + critical login/input/page lifecycle tests pass before claiming native compatibility. Unsupported essential website behavior blocks native release.

## Phase 3 — Native alpha (proposed Harmony v0.1.0)

If feasibility passes: isolate ArkWeb UI shell, permission mediation, external authentication flow, file/media bridge and limited privacy-safe diagnostics. No Java/Gecko binary reuse. Use independent signing, package identity and release checklists.

**Acceptance:** real-device end-to-end A/B and crash/restore tests; documented binary provenance and independent rollback.

## Phase 4 — Release hygiene

Use protected review, versioned changelog, pinned dependencies, reproducible checks, permission audit and SHA-256 release manifest. APK/HAP artifacts belong in dedicated private release storage with digest links; never infer success from tag/CI alone.

## Critical design decisions still open

- Whether Android compatibility runtime remains primary or only fallback.
- Exact HarmonyOS 7 target hardware/build and documented ArkWeb feature gaps.
- OpenAI website behavior may change without notice; keep adaptation limited to browser-shell responsibilities.
- Whether the currently public repository should be made private; visibility changes require an explicit GitHub settings action and fresh verification.
