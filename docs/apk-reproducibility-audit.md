# Android Probe Debug reproducibility experiment (2026-10-17)

**Status: diagnostic / not release certification.** Follow-up to Issue #4.

## Motivation

Across previous successful GitHub Actions runs, the Probe Debug APK byte count was consistently 528,297,891 bytes but its SHA-256 was **not** constant. That prevents claiming bit-for-bit deterministic output based on a successful Gradle build alone. Initial build logs may show differences caused by signing, ZIP metadata, compiler output, or a changing dependency; the actual cause must be measured, not guessed.

## Controlled experiment

`apk-reproducibility.yml` runs only when its diagnostics are changed in a PR or triggered manually. It uses pinned JDK 17, SDK 37.0, Build Tools 36, Gradle Wrapper 9.3.1, existing Gradle artifact SHA-256 metadata and unchanged GeckoView 153. It performs two independent `clean :app:assembleProbeDebug` invocations with `--no-build-cache` on the **same GitHub runner and shared dependency cache**.

Each APK must pass `apksigner verify`; certificate SHA-256 must match between builds. The two APKs are then compared by exact SHA-256 and, if different, by each archive entry's uncompressed SHA-256, ZIP metadata and file set. The JSON artifact contains **only the diagnostic report, never APK bytes or credentials**.

Diagnostic statuses: `BYTE_IDENTICAL`, `ENTRY_SET_CHANGED`, `ENTRY_CONTENT_CHANGED`, `ZIP_METADATA_CHANGED`, `OTHER_CONTAINER_BYTES_CHANGED`. `OTHER_CONTAINER_BYTES_CHANGED` is not equivalent to a proven signature-block cause; further binary offset analysis would be required. `--require-identical` can turn mismatch into a failure in a future *release* gate, once deterministic sources are established. In this experiment, a mismatch is reported without suppressing evidence, and a successful workflow does not signify identical APK output.

## Release limitations

- This is a Probe Debug variant signed with a debug identity, not the formal application's private release key.
- Shared runner/cache builds are not two independent clean hosts with independent dependency downloads. **Cross-runner cold-cache repeatability is NOT_RUN**.
- Matching debug signatures are not historical release-signature continuity.
- Real device, 16 KiB native runtime compatibility, upgrade/install and HarmonyOS 7 validation are NOT_RUN.
- No release version, `targetSdk`, GeckoView, cookie persistence or keyboard semantics are changed.

## First observed GitHub result — 2026-10-17

- PR run [#39030997866](https://github.com/liuzhide666/ChatGPT-Web/actions/runs/39030997866), commit `4451278578a1645b7c76fe4206f9c7c11b28a77d`: **BYTE_IDENTICAL**, diagnostic job PASS.
- Both builds produced **528,297,891 bytes** with **180 APK entries** and 13 native libraries each in `arm64-v8a`, `armeabi-v7a` and `x86_64`.
- Build A and B SHA-256 both `9b9a270bc1540ec451a314078b747fd4a3b92ae4527ad4f92f046be655233017`.
- Independent `apksigner verify` succeeded for both, and **their signer certificate SHA-256 values matched** (the signer is a temporary debug identity, not the historical release key).
- No ZIP entry content differences or ZIP metadata differences were found. Downloadable GitHub run artifact `apk-reproducibility-3b8d6681b5e538acb140f3e3e7c36c39fc079443` is a JSON report only.

**Interpretation:** two clean build tasks using a shared Runner/cache achieved byte-level equality. Prior builds in different CI runs had different APK SHA-256 values; potential variations in ephemeral debug signer identities must be tested independently rather than asserted as the cause. A later experiment should use **two isolated GitHub runners** and compare signer certificate fingerprints and source-derived payload digests.

