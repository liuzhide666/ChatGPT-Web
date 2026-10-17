# ChatGPT Web — community-maintained Android wrapper

**Status:** GitHub repository onboarding; production baseline is **1.1.0 / versionCode 113** (2026-08-27). This is **not an official OpenAI or Huawei application**.

This repository holds editable **Android/GeckoView source, tests, plans and release metadata**. It is not itself evidence of a newly built or signed APK. The 1.1.0 exact tested release and historical archives remain in the user's ChatGPT Library under `任务项目/01-软件开发/ChatGPT-Web/`.

## Baseline

| Field | Value |
| --- | --- |
| Application ID | `com.liuchenlin.chatgptweb` |
| versionName / versionCode | `1.1.0 / 113` |
| GeckoView | `153.0.20260810162159` |
| WebExtension / bridge | `1.1.2 / protocol 3` |
| min / target / compile SDK | `26 / 28 / 37` |
| Java | 17 |

### Non-negotiable runtime contracts

- **Keyboard transparency:** never intercept or synthesize Enter, Shift+Enter, IME actions or composer DOM editing. The browser/site is the input authority.
- **Session preservation:** bridge failure, inactivity, paint reset or slow-script alone cannot destroy an otherwise usable page.
- **Conservative recovery:** only real browser failure evidence allows controlled recovery; automatic paths must preserve browser data, cache and authentication state.
- **Diagnostic privacy:** do not collect message text, cookies, credentials, clipboard or raw editor/DOM content.

See `docs/architecture.md`, `docs/release-baseline.md`, `docs/roadmap.md` and `docs/test-matrix.md` before changing behavior.

## Read-only checks

```bash
python3 -m pytest -q
node tools/verify_keyboard_neutrality_node.js "$PWD"
bash -n tools/*.sh
python3 tools/check_repository_hygiene.py
```

Android/Gecko full integration requires a separately provisioned Android SDK + JDK 17 + pinned GeckoView dependencies and a verified build toolchain. Legacy release scripts may depend on historical local toolkit paths; do not treat them as portable CI jobs or publish an APK without fresh package/signature and target-device gates.

## Layout

- `app/`: Android application + embedded passive WebExtension
- `host-test-stubs/`, `host-tests/`, `tests/`: test source
- `tools/`: legacy build/verification scripts plus safe repository checks
- `docs/`: design history and current governance / migration plan
- `.github/`: unprivileged source CI and issue templates

## HarmonyOS direction

**Do not rename an APK to HAP.** The GeckoView-backed Android program may be tested in an Android compatibility environment, but a HarmonyOS native variant must be a separate ArkTS/ArkWeb project with independent QA and release identity. No HarmonyOS native implementation is claimed here.

## Governance

Maintain separate source, tested binary, and distribution evidence. Changes go through PRs with a reproducible test report. Never commit signing materials, secret values, user session state, or multi-volume release packages. See `SECURITY.md` and `CONTRIBUTING.md`.

## Downloads and repository visibility

The repository is currently **public**. Do not post private screenshots or account information in Issues. The original 1.1.0 signed APK is intentionally not distributed here. For a folder-free transfer archive use `ChatGPT-Web-ALL-FILES-FLAT-2026-10-17.zip`: the ZIP has no directory entries and contains a path reconstruction manifest for restoration. The older `ChatGPT-Web-GitHub-bootstrap-2026-10-17.zip` is a legacy hierarchical archive retained only for provenance; do not confuse its internal layout with the required flat archive.
