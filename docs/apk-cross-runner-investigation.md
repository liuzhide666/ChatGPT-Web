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

## Actual GitHub experiment result — 2026-10-17

Run [#39031442577](https://github.com/liuzhide666/ChatGPT-Web/actions/runs/39031442577), source commit `b2cc0168d36a89fb21d55fc9bc398d285bac4130`:

- The two separate build jobs and the comparison job all **PASS**. Both APKs are **528,297,891 bytes**.
- Runner A APK SHA-256: `fcf0895c50a819b5b0ef70bdec102b3af13bedf1ee843abce88f03dfb5b43405`.
- Runner B APK SHA-256: `81b1731a606404a12d07c1327f53663110c5b800a88ef7cfc17376e88ce3cde8`.
- Runner A debug certificate SHA-256: `83c60c92564104a8aad0929a3a137deb855b41bd88eb84734e464ca0a53d3954`.
- Runner B debug certificate SHA-256: `2e7e1d526ddeebc2fb1265a2fc62e53323f553c632a279ecf03a3cfeefa6718b`.
- **Non-signature payload SHA-256 fingerprint on both runners:** `a6669800cb6188c65552e6535a6de033ba54f1d775a90de3866a991d779345f0`. Changed non-signature ZIP entries: **0**; missing/additional entries: **0**.
- Automated classification: **`SIGNER_CHANGED_PAYLOAD_IDENTICAL`**.

Interpretation: host-generated debug certificate identity differs across isolated runners while the inspected non-signature ZIP entry payloads are byte-for-byte identical. This strongly explains the historically different debug APK hashes; **it does not independently prove that signer bytes are the only changed container bytes**, nor certify release-signing continuity, cold-cache reproducibility, installed-device behavior or HarmonyOS 7 support.

Artifact: [comparison JSON #12100313679](https://github.com/liuzhide666/ChatGPT-Web/actions/runs/39031442577/artifacts/12100313679). Debug APKs/keystores were never uploaded.

No formal APK version change or extra permissions were introduced.
