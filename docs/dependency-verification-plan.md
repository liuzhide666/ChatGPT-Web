# Gradle dependency verification bootstrap — work in progress

The user-maintained Android/GeckoView app must record the SHA-256 of the artifacts and Gradle plugin metadata resolved in real CI before accepting Gradle dependency verification. Issue #4 remains open until this is enforced and reviewed.

- `gradle-dependency-bootstrap.yml` is a **temporary read-only artifact-capture gate**. Its checksums are initially trust-on-first-use: being in a published Maven repository does **not** prove author identity or an uncompromised download. The workflow has no signing secrets and does not publish an APK.
- After retrieving the candidate metadata, check for unexpected coordinates, versions, artifacts and wildcard exemptions. Independently verify key artifacts against trusted publisher releases if possible. Record limits honestly.
- Commit reviewed `gradle/verification-metadata.xml`; make regular `android-build` CI enforce it *without* `--write-verification-metadata`.
- Changes to the dependency graph must become explicit PR diffs. No automatic trusting unknown artifacts, no insecure `--dependency-verification=off` fallback.
- Exact first-run integrity proof is restricted to resolution of the inspected artifacts. Native .so 16KiB alignment, signing, reproducible APK bytes and real-device compatibility remain separate acceptance gates.
