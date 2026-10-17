# Current architecture / contract map

Baseline: Android Java shell → GeckoRuntime / GeckoSession → ChatGPT website, with a narrow passive WebExtension (`app/src/main/assets/chatgpt_bridge`).

| Owner | Authority | Contract |
| --- | --- | --- |
| Android shell | lifecycle, permissions, downloads, file picker, media bridge, window chrome, recovery UX | Must preserve live page and authenticated profile by default |
| GeckoView / website | editing, key events, IME composition, Enter semantics, DOM actions | Android / extension must not synthesize submit/newline commands |
| Passive extension | READY, PING/PONG, composer safety booleans, visibility metadata | No copying message/editor text, credentials or selection to native |
| Browser supervisor | actual failure evidence, safe fallback, diagnostics | Bridge-only failure does not authorize destructive reload |

Current modules: `MainActivity`, `GeckoRuntimeHolder`, `GeckoSessionController`, `BrowserHealthSupervisor`, `ChatGPTContentBridge`, `FilePromptCoordinator`, `DownloadCoordinator`, `GeckoMediaPermissionDelegate`, `SystemBarPolicy`, `TrustedOriginPolicy`.

**State:** stable sessions do not require idle timers; recovery uses default cache and shared Gecko runtime/profile; explicit one-shot force-refetch is the only user-requested cache bypass. Recents/foreground should reuse the live session.

**Scope boundary:** The native Harmony route uses ArkTS / ArkWeb and must independently reproduce user-visible requirements; direct source/binary parity with GeckoView is neither expected nor promised. See `docs/adr/0001-dual-track-platform.md`.
