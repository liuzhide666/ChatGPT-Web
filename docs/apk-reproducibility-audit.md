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

The actual result and a pointer to its run and artifact will be appended only after GitHub returns the completed run evidence. If a mismatch occurs, create a targeted fix instead of artificially normalizing the report.
