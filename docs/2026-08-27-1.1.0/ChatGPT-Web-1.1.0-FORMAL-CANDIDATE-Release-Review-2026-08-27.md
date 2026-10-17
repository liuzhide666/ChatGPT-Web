# ChatGPT Web 1.1.0 Formal Candidate — Release Review

**Date:** 2026-08-27  
**Candidate identity:** `com.liuchenlin.chatgptweb` / `versionName 1.1.0` / `versionCode 111`  
**Status:** **Formal Candidate — target-device Gate F pending**  
**Canonical baseline:** `1.0.3 Formal — known Shift+Enter target-device runtime defect` remains installed/canonical until Gate F passes.  
**GeckoView:** frozen at `153.0.20260810162159`.

## 1. Why vc111

RC1 already used `versionCode=110`. A true 1.1.0 Formal must be installable as an Android in-place update from both 1.0.3 and RC1, so the Formal candidate advances monotonically to `versionCode=111` while keeping the user-facing `versionName=1.1.0`.

## 2. Input architecture frozen for Formal

The candidate no longer relies on DOM `shiftKey` to infer the user's intent. `ChatGPTGeckoView` / `ChatGPTInputConnection` classify real Android input signals, then `ComposerInputRouter` emits only semantic actions. Strict native authority requires a generation-matched Web composer-focus handshake; generic multiline `EditorInfo` never authorizes Enter or Shift+Enter.

Native semantic commands now use a correlated `commandId` envelope. ACK correlation is strict on both `commandId` and the expected command type; a mismatched command name is rejected even when the numeric ID matches. A successful native `Port.postMessage()` is recorded only as `REQUESTED`; the input trace becomes `ACK_APPLIED` only after the content script executes the Site Adapter operation and returns a matching `COMMAND_ACK`. Rejected, timed-out, disconnected and stale commands remain distinguishable, eliminating RC1's false "transport success = applied" evidence level.

Native and Web composition states are independent safety signals. Successful `setComposingText` / non-empty `setComposingRegion` activate native composition metadata; `finishComposingText`, zero-sized composing region and successful ordinary `commitText` clear it. Prompt/candidate text is never stored or logged.

For line breaks, the Formal candidate deliberately removes RC1's default raw `InputConnection.commitText("\n")` executor. Native detects and consumes the intent; `ChatGPTContentBridge -> Site Adapter` applies `INSERT_LINE_BREAK`. This matches the architecture boundary and avoids treating an unverified raw Gecko edit as equivalent to a ProseMirror hard break. The native `换行` control uses the same semantic route for software IMEs that do not expose Shift.

## 3. Browser health / recovery

`BrowserHealthSupervisor` remains event/deadline driven; the fixed 5-second polling loop is absent. Gecko `onPaintStatusReset` is treated as fresh content-liveness evidence, while `onSlowScript` is recorded as a metadata-only "content process alive but JS may be blocked" diagnostic signal and never directly triggers reload. Crash/kill retain their dedicated recovery path. Bridge-only staleness is handled with rebind/PING before page reload. Destructive recovery is blocked while the default network is known unavailable and while draft/composition/send-ready/media/external/background/untrusted blockers are active. Cache/Cookie/Web storage are not purged and ordinary recovery keeps default Gecko cache semantics.

## 4. Release-build pipeline correction discovered during Formal audit

Formal audit found that RC1's offline DEX pipeline had compiled production code using the Host-test `R.java` stub (`activity_main=1`, `gecko_container=4`, etc.). The actual linked resource table uses IDs such as `layout/activity_main=0x7f070000` and `id/gecko_container=0x7f050030`; RC1's DEX therefore contained `setContentView(1)` / `findViewById(4)`. RC1 and any candidate inheriting that DEX build method are not acceptable as a new canonical release.

The Formal pipeline fixes this structurally:

- `tools/generate_release_r.py` reads the already-linked baseline APK resource table and generates only the production `R` constants actually referenced by source.
- `tools/build_111_release_dex.sh` compiles against the real Android 37 API plus Gecko ABI source stubs, explicitly excluding Host Android/R stubs.
- The resulting DEX contains the real resource constants and is reproducible byte-for-byte.
- `tools/build_111_formal_candidate.sh` patches the merged binary Manifest to vc111, replaces only the app DEX and bridge content asset, preserves the Gecko/resource payload, performs 16 KiB alignment, and signs with the canonical release identity.

## 5. Fresh source gate

Current clean-source gate covers **154 Python contracts and 31 Java Host tests**, plus:

- Node Site Adapter + content-script simulation with both JS inputs explicitly supplied;
- Android API 37 compile contract;
- minSdk 26 NewApi lint;
- release-R generator and release-DEX/candidate-builder contracts;
- actual packaged GeckoView 153 ABI descriptors for Port messaging, `onPaintStatusReset`, `onSlowScript` and `SlowScriptResponse.CONTINUE`.

The source gate is invoked through `tools/run_110_formal_source_gate.sh`; package-specific rebuild uses `tools/build_111_release_dex.sh` and `tools/build_111_formal_candidate.sh`.

## 6. Binary candidate audit

Candidate APK SHA-256 at current freeze: `4532ee94a5e469972daaa58701e1ce7181d7b5a2c5e7eb3167c20fa8b9d10dd8`.

Verified properties:

- package `com.liuchenlin.chatgptweb`, vc111 / 1.1.0;
- minSdk 26, targetSdk 28, compileSdk 37;
- `android:debuggable=false`, `allowBackup=false`, `usesCleartextTraffic=false`;
- APK Signature Scheme v2 and v3 verified with one RSA-4096 signer;
- signer certificate SHA-256 `23130c6a6b7b0969d4c76ac9e7278f9b4c26c77a897d0c4a0de514a9c3a4e70c`;
- 16 KiB zip/page alignment verified;
- component counts: 2 activities / 89 services / 1 provider / 1 receiver;
- permissions identical to RC1 (10 uses-permission entries);
- 39 native `.so`, all ZIP STORED;
- 5 DEX files; `resources.arsc`, four Gecko/other DEX files and all 39 `.so` remain byte-identical to RC1;
- intended content differences from RC1: merged `AndroidManifest.xml` (vc111), `classes3.dex`, and `assets/chatgpt_bridge/content.js`; old v1/JAR signature entries are removed because this build uses v2/v3 signing;
- application DEX: 144 class definitions, all in `com.liuchenlin.chatgptweb`, zero foreign packaged classes;
- no direct `MediaStore.Downloads` type descriptor, WebView, legacy SpeechRecognizer, synthetic KEYCODE_ENTER, SessionStateStore or cache-bypass token in app DEX;
- `layout/activity_main=0x7f070000`, `layout/view_recovery=0x7f07000a`, `id/gecko_container=0x7f050030`, `id/recovery_retry=0x7f05003f` are consistent between resources.arsc and compiled MainActivity DEX.

## 7. Evidence boundary

No online ADB target is attached in the current execution environment. Therefore this package is not yet entitled to the word **Formal** in the canonical pointer. Automated gates establish source/package correctness and eliminate several prior false-positive build paths, but they cannot prove how the target 卓易通 keyboard/IME transports Shift and composition through Android -> GeckoView on the actual device.

The exact remaining promotion condition is `ChatGPT-Web-1.1.0-FORMAL-Target-Device-Gate-2026-08-27.md`. If every required target-device row passes on the same APK bytes identified above, those bytes may be promoted to `ChatGPT Web 1.1.0 Formal` without a code rebuild; only release naming/index state changes. Any target-device failure returns to diagnosis and invalidates promotion.
