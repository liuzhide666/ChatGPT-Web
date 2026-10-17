# Gradle Wrapper provenance / G1 continuation, 2026-10-17

- Gradle version: **9.3.1**; AGP: **9.1.1**; JDK: **17**.
- Gradle executable `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` are copied verbatim from the upstream **gradle/gradle** Git tag `v9.3.1`, via the connected GitHub API. This is not independently a signed release until source verification is performed.
- Official Gradle Wrapper JAR SHA-256: `b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`.
- Gradle `9.3.1-bin.zip` SHA-256: `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`.
- Origin of hashes: https://gradle.org/release-checksums/ and https://docs.gradle.org/current/userguide/gradle_wrapper.html
- Verification: `python3 tools/verify_gradle_wrapper.py` checks exact JAR digest and HTTPS distribution policy; `./gradlew --version` verifies actual bootstrapping, and CI `android-build` executes the Gradle app task via Wrapper.
- GitHub `gradle/actions/setup-gradle@...` independently validates checked-in Wrapper jars; the Android build action is read-only and does not sign/upload an APK.

This is a **build-chain integrity check**, not Gradle dependency checksum locking, artifact reproducibility across cold caches, ELF 16KiB verification, a signed package, or device compatibility. These are separate later gates.
