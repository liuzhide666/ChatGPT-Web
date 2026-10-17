# ChatGPT Web 1.1.0 RC2 / vc112 — Target Device Failure Record

Date: 2026-08-27

- versionName=1.1.0
- versionCode=112
- APK SHA-256=`469343cd23129073c2f2735d6c218fb417a0c7c3da758fdb3e168aa04c7324ad`
- TARGET_DEVICE_GATE=FAIL
- SHIFT_ENTER_TARGET_DEVICE_FIXED=NO
- Failure observation: on the actual 卓易通 target environment, the application-owned Enter/Shift+Enter routing still did not produce the required behavior; the user reported no effective improvement compared with the prior build.
- Architectural decision: do not add another Shift+Enter patch. vc113 removes application semantic ownership of Enter/Shift+Enter/IME action and delegates the input path to GeckoView + the ChatGPT website.
- Any source change after this failure uses `versionCode=113` or later. vc112 exact bytes remain immutable evidence and are not eligible for Formal promotion.
- Canonical remains 1.0.3 Formal until a later exact candidate APK passes its target-device gate.
