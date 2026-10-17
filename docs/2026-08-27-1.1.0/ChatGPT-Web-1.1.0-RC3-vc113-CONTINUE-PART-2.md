# Continue ChatGPT Web RC3 / vc113 — Part 2

Resume from the **Part-1 frozen source snapshot**, not vc112 and not the 1.0.3 canonical source.

## Required project sources

- Final design: `/任务项目/01-软件开发/ChatGPT-Web/docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-RC3-vc113-FINAL-DESIGN-2026-08-27.md`
- Three-part plan: `/任务项目/01-软件开发/ChatGPT-Web/docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-RC3-vc113-THREE-PART-IMPLEMENTATION-PLAN-2026-08-27.md`
- Part-1 source: `/任务项目/01-软件开发/ChatGPT-Web/source/1.1.0-work/ChatGPT-Web-source-1.1.0-RC3-vc113-Part1-2026-08-27.tar.gz`
- Part-1 report: `/任务项目/01-软件开发/ChatGPT-Web/docs/2026-08-27-1.1.0/ChatGPT-Web-1.1.0-RC3-vc113-PART-1-REPORT-2026-08-27.md`

## Frozen Part-1 invariants

- app versionName `1.1.0`, versionCode `113`;
- extension `1.1.2`, bridge protocol `3`;
- GeckoView `153.0.20260810162159`;
- zero semantic keyboard ownership in production;
- no NativeKeyIngress / ComposerInputRouter / InputIntentTransaction / InputEventDeduplicator / ChatGPTInputConnection / InputRoutingState / InputTraceBuffer / InputDiagnosticsDialog / ImeActionPolicy;
- ChatGPTGeckoView has no key-dispatch or InputConnection override;
- MainActivity key dispatch is generic activity bookkeeping + `super.dispatchKeyEvent` only;
- bridge has READY/PONG/COMPOSER_STATE and PING/SYNC_COMPOSER_STATE only; no submit/line-break command channel;
- WebExtension has no `keydown`/`beforeinput` semantic handler, no submit/line-break/editor-effect executor;
- Site Adapter is read-only snapshot only;
- Native line-break button and input diagnostics are removed;
- BrowserHealthSupervisor algorithm is intentionally still the Part-1/vc112-derived health implementation and must be redesigned only in Part 2.

## Part-1 fresh gate

- pytest: 159 PASS;
- Java Host: 28 PASS;
- Node keyboard neutrality: PASS;
- Android API37 compile: PASS;
- minSdk26 NewApi lint: PASS, no issues;
- real GeckoView153 ABI gate: PASS.

## Start Part 2 exactly here

Implement Tasks 7–14 from the three-part plan:

1. Browser Diagnostics replacing input diagnostics concept;
2. passive composer-safety state cleanup and remove `sendReady` recovery authority;
3. navigation generation;
4. timerless STABLE + operation-scoped deadlines;
5. bridge/page-health decoupling and `DEGRADED_STABLE`;
6. strong-failure / weak-signal quorum recovery ladder;
7. cache/network/lifecycle invariants;
8. full Part-2 source/compile gate.

Do not package a release APK in Part 2. Do not restore any keyboard semantic path. Canonical remains 1.0.3 Formal.
