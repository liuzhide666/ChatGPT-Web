# Debug build payload identity ledger (2026-10-17)

CI build reproducibility needs two separate measurements:

1. **Full APK SHA-256** includes ZIP metadata and v1/v2 debug signatures, and can change between GitHub-hosted runners because a fresh runner creates a fresh **debug** signing certificate. This value remains the correct identifier for the exact APK bytes.
2. **Non-signature payload digest** hashes every APK ZIP member's uncompressed bytes except top-level JAR v1 signing records (`META-INF/MANIFEST.MF`, `.SF`, `.RSA`, `.DSA`, `.EC`). Its canonical representation is sorted UTF-8 entry name, NUL, and each member's raw SHA-256 digest. It is not an APK SHA-256, not a Google Play signature, and not an installed-app attestation.

Two independent runner jobs on 2026-10-17 gave **different debug certificates and full APK hashes** but 0 ordinary payload differences and the same canonical payload digest:

`a6669800cb6188c65552e6535a6de033ba54f1d775a90de3866a991d779345f0`

Evidence: [GitHub #39031442577](https://github.com/liuzhide666/ChatGPT-Web/actions/runs/39031442577), with repeat [#39031593109](https://github.com/liuzhide666/ChatGPT-Web/actions/runs/39031593109). Both reports were produced by real Android builds.

The regular `android-build` workflow now logs both measurements using `tools/record_debug_apk.py` and `tools/report_cross_runner_identity.py`. The report JSON remains within runner temporary storage and is **not** uploaded as an APK, keystore, or user session. The full APK hash remains evidence of exact debug bytes; payload identity is evidence for comparison without ephemeral debug-signer noise. Neither measurement proves compatibility or authenticated vendor provenance.

This change does not rewrite `app/build.gradle`, alter debug/release signing configuration, replace the historical 1.1.0 APK, or claim a signed distributable was created. True release gates still need historical certificate continuity, 16 KiB target-device checks, fresh compatible device testing and independent release approval.
