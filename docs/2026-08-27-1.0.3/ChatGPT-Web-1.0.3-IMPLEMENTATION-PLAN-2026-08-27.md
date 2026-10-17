# ChatGPT Web 1.0.3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 发布 `ChatGPT Web 1.0.3 / vc105`，稳定实现 `Enter=发送 / Shift+Enter=换行`，并以更保守的默认缓存 page-health 策略减少长对话误刷新。

**Architecture:** 保留 RC4 的主 composer semantic submit router。Shift+Enter 不再向页面 handler 放行，而是在 capture 阶段消费并使用 Gecko/DOM 编辑命令插入 hard line break；page-health 只调整纯策略阈值，不改变 shared profile/default cache 架构。

**Tech Stack:** Java 17, Android API 37 / minSdk 26 / targetSdk 28, GeckoView `153.0.20260810162159`, built-in WebExtension MV2, Python/Java host contracts, Node event simulation, D8, apksigner, 7-Zip.

**Spec:** `docs/2026-08-27-1.0.3/ChatGPT-Web-1.0.3-ShiftEnter-Freshness-DESIGN-2026-08-27.md`

## Global Constraints

- `applicationId=com.liuchenlin.chatgptweb`, label `ChatGPT Web`。
- `versionName=1.0.3`, `versionCode=105`, bridge version `1.0.5`。
- GeckoView 保持 `153.0.20260810162159`，不升级、不新增 runtime dependency。
- Enter/Shift+Enter 仅作用于确认的 ChatGPT 主 composer；不得拦任意 textarea/contenteditable。
- 不生成 Android `KeyEvent`；中文 composition 不允许误发送。
- 无 `LOAD_FLAGS_BYPASS_CACHE`、无 Cookie/cache/storage purge、无生命周期 reload、无后台刷新 worker/service。
- soft/hard recovery 每 incident 各最多一次。
- 使用与 1.0.1 canonical 相同 release certificate。

---

### Task 1: Shift+Enter hard-line-break ownership

**Files:**
- Modify: `app/src/main/assets/chatgpt_bridge/content.js`
- Modify: `tests/test_102_bridge_contract.py`
- Modify/Create: `tests/test_103_shift_enter_contract.py`
- Modify/Create: `tools/node_input_router_qa.js`

**Interfaces:**
- Produces: `insertComposerLineBreak(editor): boolean` and `lineBreakInsertionActive` reentrancy guard.
- Preserves: `submitMainComposer(source): boolean` and existing Native `SUBMIT_COMPOSER` path.

- [x] Write failing contracts requiring Shift+Enter to consume the original event and call `insertComposerLineBreak` instead of merely setting a grace token.
- [x] Extend Node QA so Shift+Enter proves zero send-button clicks and one line-break mutation; command failure must still prove zero submit.
- [x] Run targeted Python + Node tests and observe RED.
- [x] Implement textarea `setRangeText` path and contenteditable `execCommand("insertLineBreak")` with `insertText("\n")` fallback and reentrancy guard.
- [x] Run targeted tests and observe GREEN.
- [x] Harden web event ownership to `window` capture and add a 150 ms trailing-beforeinput guard.
- [x] Make native editor-action fallback hardware-Shift-aware and verify Shift-modified SEND/DONE is rejected.
- [x] Verify Shift+Enter line-break does not require the send button to be mounted; plain Enter still requires confirmed send control.

### Task 2: Conservative freshness/cache policy

**Files:**
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/PageHealthPolicy.java`
- Modify: `app/src/main/java/com/liuchenlin/chatgptweb/MainActivity.java`
- Modify: `tests/java/com/liuchenlin/chatgptweb/PageHealthPolicyTest.java`
- Modify: `tests/test_102_page_health_contract.py`

**Interfaces:**
- `STALE_AFTER_MS=45000`, `SOFT_GRACE_MS=30000`, `HARD_GRACE_MS=45000`.
- `PAGE_HEALTH_RESUME_GRACE_MS=15000`.

- [x] Write failing tests for the new exact thresholds and verify ordinary lifecycle still has no reload/bypass cache.
- [x] Run RED.
- [x] Change only timing constants; do not change recovery topology or cache mode.
- [x] Run Java/Python targeted tests GREEN.

### Task 3: Version/final-release identity

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/assets/chatgpt_bridge/manifest.json`
- Update version/static contracts.

**Interfaces:** `1.0.3 / vc105 / extension 1.0.5`.

- [x] Add failing version contracts for `105 / 1.0.3 / 1.0.5`.
- [x] Run RED.
- [x] Update production version fields only.
- [x] Run GREEN and full source gate.

### Task 4: Formal package and comprehensive audit

**Files:**
- Create: `docs/2026-08-27-1.0.3/ChatGPT-Web-1.0.3-Formal-Release-Review-2026-08-27.md`
- Create: `docs/2026-08-27-1.0.3/ChatGPT-Web-1.0.3-FINAL-GATE-2026-08-27.txt`
- Create: final clean source archive and release 7z volumes.

- [ ] Re-run clean-archive Python/Java/API37/Node/lint gates.
- [ ] Compile app-only DEX against real API37 + GeckoView 153 ABI and audit foreign classes/forbidden symbols.
- [ ] Build vc105 APK from canonical payload boundary, zipalign 16 KiB, sign v2/v3 with canonical signer.
- [ ] Audit manifest identity, permissions, 89/1/1 components, 39 `.so`, five DEX and byte-diff allowlist.
- [ ] Generate true 7z DEFLATE archive and 30 MiB `.7z.001...` volumes; verify archive/APK round trip.
- [ ] Persist Design/Plan/source/Review/Gate/release volumes to Library and re-read.
- [ ] Only after 1.0.3 persistence is verified, remove superseded RC4 candidate release/source/review/gate while preserving design history and 1.0.1 canonical history.
