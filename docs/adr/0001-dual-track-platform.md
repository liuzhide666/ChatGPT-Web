# ADR-0001: Dual-track Android compatibility and HarmonyOS native validation

- Date: 2026-10-17
- Status: Proposed; technical direction approved for planning, not a native compatibility claim.

## Context

1.1.0 Formal is an Android APK with GeckoView and a carefully constrained input/recovery contract. The requested target is HarmonyOS 7. Compatibility-runtime behavior and native Harmony app behavior are different execution models.

## Decision

Keep Android GeckoView in a maintenance branch and assess an independent ArkTS + ArkWeb HAP. Reuse *requirements and verification tests* rather than Gecko native binaries. Do not embed or modify ChatGPT credentials, prompt contents, or input semantics.

## Alternatives rejected (for now)

- Rename APK to HAP: invalid packaging/runtime assumption.
- Embed GeckoView as Harmony native WebView: not a safe porting premise.
- Rewrite keyboard semantics for ArkWeb up front: violates verified user-value contract and risks IME regressions.
- Ship a new Android targetSdk + Gecko version + input rewrite simultaneously: regression attribution would be poor.

## Open verification

Exact HarmonyOS 7 build/toolchain and ArkWeb website compatibility require device tests; no native HAP is built or passed.
