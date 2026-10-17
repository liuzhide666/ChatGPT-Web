# GitHub repository handoff / 2026-10-17

Repository: https://github.com/liuzhide666/ChatGPT-Web

## Verified remote starting point

- Remote account: `liuzhide666`; repository name: `ChatGPT-Web`.
- Repository visibility at import: **PUBLIC**. This differs from the earlier private-repository plan. Treat all commits and Issues as public; do not post credentials, tokens, keystores, account data, crash reports with private content, or device identifiers.
- Initial remote `main` contained `README.md` and `ChatGPT-Web-GitHub-bootstrap-2026-10-17.zip` (the latter is a **legacy hierarchical** bootstrap archive, not the latest flat delivery).
- The 1.1.0/vc113 baseline APK itself, private release-signing identity and signed release evidence remain under the project Library, not in Git.

## Repository roles

- Editable source under `app/`, tests under `tests/` and `host-tests/`, source workflow under `.github/`.
- `docs/` is the versioned design, compatibility matrix, release provenance, and plan record.
- The **flat ZIP delivery** is `ChatGPT-Web-ALL-FILES-FLAT-2026-10-17.zip`; every ZIP entry is a root-level file. Its `FILE-MAP.csv` and `restore_flat.py` reconstruct the editable layout outside the archive.
- The older `ChatGPT-Web-GitHub-bootstrap-2026-10-17.zip` was supplied before the flat deliverable. Keep it as legacy provenance, but use the flat ZIP or repository source tree for ongoing work.

## Review and verification

1. Check repository visibility and selected branch before any write.
2. Compare GitHub tree paths/blob hashes with the local validated source manifest.
3. Run source unit tests and keyboard transparency checks. CI does not certify an Android APK/Harmony HAP.
4. Inspect GitHub Actions results; failures must be reported, never labeled PASS.
5. Register issue backlog and run a real target-device A/B before any version promotion.
6. For private distribution, change visibility through GitHub settings separately, then confirm it changed. This document does not change repository visibility.

## Boundaries

- Do not force-push over the existing history.
- Do not upload credentials, raw user diagnostics, APK/HAP binaries, unverified vendor binary sources, or private build configurations.
- Publishing source does not make this an official OpenAI or Huawei application.
