# Android build portability — implementation slice A (2026-10-17)

Status: **CI under real GitHub verification; first run exposed multi-line SDK package argument error, corrected in the next revision**. Baseline version stays `1.1.0/vc113` and GeckoView remains `153.0.20260810162159`. This change does **not** produce a formally signed 1.1.1 APK or certify HarmonyOS 7.

## Toolchain

- Gradle: `9.3.1` through official `gradle/actions/setup-gradle` in CI (no unverified wrapper JAR checked in)
- Android Gradle Plugin: `9.1.1` (unchanged)
- JDK: Temurin Java 17
- Android SDK: API `37`, Build Tools `36.0.0`
- GeckoView: `153.0.20260810162159` (unchanged)

The CI job installs official tools and runs `gradle --no-daemon :app:assembleProbeDebug`. An Android debug/probe APK is a **non-release** test artifact and is not published or signed with the production keystore. It prints SHA-256 and verifies ZIP integrity and manifest/DEX presence. Source-only CI remains a separate required check. Host Java/Android API37 contracts run in the Android CI job.

The Gradle Wrapper (`gradle/wrapper/gradle-wrapper.jar`, `gradlew`, etc.) is **not yet provided**: this environment cannot retrieve and independently verify the official Gradle 9.3.1 wrapper JAR and distribution checksum. Add the Wrapper only after byte-checking official published hashes and running it in a clean environment; never fabricate a wrapper binary. GitHub CI uses pinned Gradle 9.3.1 via its verified action in the interim.

## Portable local use

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
bash tools/resolve_android_toolchain.sh
bash tools/run_all_host_tests.sh
bash tools/compile_api37_contract.sh
# When gradle 9.3.1 is installed and relevant Maven dependencies are reachable:
gradle --no-daemon :app:assembleProbeDebug
```

Alternatively, `JDK_ROOT` or `ANDROID_JAR` may be set explicitly; `TOOLKIT_ROOT` is still accepted for historical offline-toolkit scripts. Legacy formal release builders are intentionally unchanged and may still require their historical SDK toolkit. In this phase `settings.gradle` project name is normalized to `ChatGPT-Web` (Gradle display name only, not application ID).

## Gate results

- `RED`: 4 new portability tests failed before implementation because resolver was missing, old project name, and scripts depended on historical paths.
- `GREEN` in local environment: 178 pytest cases, Node keyboard neutrality, bash syntax, path hygiene. Local machine has JDK 21 and no Android API37 SDK; it cannot establish Android Host/API37/Gradle compile success.
- `NOT_RUN`: real APK compile until GitHub workflow runs; signed upgrade, 16KiB native-library audit, and HarmonyOS 7 device A/B.

## Next tasks

1. Require GitHub Android CI to actually complete and inspect test/assembly logs before merging.
2. Verify official Wrapper JAR and distribution SHA and commit via a separate reviewable PR.
3. Pin dependency verification metadata and review Gecko artifact provenance after a real clean build.
4. Do not change baseline versionCode `113` until a new release candidate is approved.
