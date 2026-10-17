# Acceptance test matrix

| Case | Android GeckoView | Harmony native ArkWeb | Gate |
| --- | --- | --- | --- |
| Fresh install and signed in-place upgrade | required | required (native installer) | G3/G4 |
| ChatGPT login, persistent authenticated profile | required | feasibility critical | G4 |
| Enter / Shift+Enter / Chinese IME candidates | compare original site | compare native browser behavior | G2/G4 |
| Cursor, paste, undo, multiline input | required | required | G2/G4 |
| Long conversation + idle without spurious reload | required | required | G4 |
| Recents, foreground, background, network restore | required | required | G4 |
| Cookie/localStorage preservation during recovery | required | required | G2/G4 |
| File picker, download, attachment | required | required | G4 |
| Recording/mic, media permissions | required | feasibility critical | G4 |
| External OAuth / app link round-trip | required | feasibility critical | G4 |
| Real browser failure & recovery ladder | required | to design after feasibility | G4 |
| No messages/tokens in diagnostic logs | required | required | G0/G3 |

**Evidence template per row:** date; device/model; OS build; Android compatibility host or ArkWeb version; app binary SHA-256; input method; test action; expected outcome; actual outcome; logs with sensitive fields removed; PASS/FAIL/NOT_RUN; observer. A prior `PASS_USER_CONFIRMED` is not independent telemetry.

Do not conflate emulator/host tests with original device verification. Exact APK/HAP files must be tested after their last code or signing change.
