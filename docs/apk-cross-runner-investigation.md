# Two-runner APK identity investigation — 2026-10-17

## Purpose

Same-runner two-clean-build experiment returned byte-identical SHA-256 for the 528,297,891-byte Probe Debug APK. However, earlier separate GitHub Actions runs had different APK hashes. This experiment isolates host-generated **debug signing certificates** from website-independent source payload changes.

## Method

Workflow `apk-cross-runner.yml` starts **two separate GitHub-hosted Ubuntu 24.04 runner jobs** concurrently with the same commit, JDK 17, Android SDK 37, AGP 9.1.1, GeckoView 153, Gradle 9.3.1, SHA-256 verification metadata and the same build command. Only the certificate SHA-256 (public), APK SHA-256 and SHA-256 of per-entry ZIP bytes are collected. There is no APK upload, no keystore upload, no credentials, and no ChatGPT account login.

In each report, v1 JAR signing records such as top-level `META-INF/CERT.SF`, `CERT.RSA`, `MANIFEST.MF` are explicitly excluded from the **non-signature payload** comparison, but all ordinary project and Gecko resources remain covered. V2 signature blocks are outside ZIP member payloads. The two reports are compared by a final job. Possible outcomes:

- `CROSS_RUNNER_BYTE_IDENTICAL`: both binary APK hashes match.
- `SIGNER_CHANGED_PAYLOAD_IDENTICAL`: the package hashes differ and the debug certificate fingerprints differ, but all non-signature members' payloads match. This **strongly suggests signer-identity contribution**, not an independent proof that signer bytes are the sole difference.
- `CONTAINER_OR_PACKAGING_VARIATION`: non-signature members and signer fingerprints match but full APK differs; investigate ZIP metadata and signing-block bytes.
- `NON_SIGNATURE_PAYLOAD_CHANGED`: at least one ordinary entry has different bytes or a changed entry set; investigate compiler/environment and timestamps.

`workflow success` means comparison was performed and saved; it does **not** mean cross-runner hashes matched or that any release can be signed. Even two separate runners may retrieve Gradle dependencies from the shared Actions cache. Cold-cache provenance, historical release signing, installed devices and HarmonyOS 7 are independent NOT_RUN gates.

The actual SHA-256 and outcomes will be recorded from live workflow logs after the run. No formal APK version change or extra permissions are introduced.
