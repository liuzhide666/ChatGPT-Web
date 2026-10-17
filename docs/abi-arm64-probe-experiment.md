# Optional ARM64 debug-probe packaging measurement

The current **1.1.0/vc113 Formal release is unchanged**. All ordinary builds still include GeckoView's available ABIs. To measure the binary packaging cost without altering the release path, the CI-only Gradle init script `tools/arm64-probe-only.init.gradle` is supplied explicitly with `-I` and applies `ndk.abiFilters` to the `probe` flavor before variant finalization. The application Gradle file is unchanged, preserving the existing no-ABI-filter source contract.

The separately triggered `abi-probe-experiment` CI compares two APKs from the same Git commit and runner: default universal `:app:assembleProbeDebug` and an opt-in arm64-only debug version after clean. It records ZIP integrity, SHA-256, retained `.so` count and byte difference. It uploads **only the JSON size report**, never the APK or signing keys.

**Release limitations:** reductions in package size do not prove installation or runtime behavior. No ABI is removed from the default build. The ARM64 diagnostic APK must not be distributed as a formal APK and does not replace an Android update package. Actual Android or Huawei compatibility-runtime ABI support, media/login/input behavior, signature continuity and 16KiB requirements are independent gates. A device reporting armeabi-v7a-only would not be a supported deployment target for an arm64-only diagnostic package.

No optimization is authorized until the experiment yields true on-run measurements and Issue #14 records that evidence.
