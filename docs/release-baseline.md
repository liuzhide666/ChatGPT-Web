# Immutable formal baseline — 1.1.0 / vc113

Source of record: user's ChatGPT Library, `任务项目/01-软件开发/ChatGPT-Web`.

| Artifact | Identity / digest |
| --- | --- |
| Exact APK (inside 8-part 7z) | `ChatGPT-Web-1.1.0-RC3-vc113.apk` |
| APK SHA-256 | `9136ef03010302df2e527fca5b664d4d479f216cbbad5670ca0307ea5ac328ac` |
| Formal source tar.gz | `ChatGPT-Web-source-1.1.0-Formal-vc113-2026-08-27.tar.gz` |
| Source tar.gz SHA-256 | `c059f4c509f1be723982831ad25e32339df75016a47a22ec06553568c4b3ab17` |
| Logical 7z SHA-256 | `d0d92964dbffe26aea0084297bc096143cbd55124cf808cf7fdededb72994e8e` |
| Package | `com.liuchenlin.chatgptweb` |
| versionName / code | `1.1.0 / 113` |
| GeckoView / extension / bridge | `153.0.20260810162159 / 1.1.2 / 3` |
| SDK | min 26 / target 28 / compile 37 |
| Date promoted | 2026-08-27 |

The release was promoted from exactly tested RC3 bytes without rebuild/resign. Original audit states: 174 pytest, 30 Java-host tests, Node keyboard-neutrality, API37 compilation, 16 KiB alignment, v2/v3 signatures and deterministic APK checks; target-device result was *user-confirmed* rather than independently captured telemetry. The current repository bootstrap has **not** reconstructed or independently validated that APK.

Release vault in Library: `releases/2026-08-27-1.1.0-formal/` with `.7z.001` through `.7z.008`, checksum and audit. These distribution packages are deliberately excluded from Git to avoid large binaries, duplicate storage, and accidental public dissemination.

**Rollback**: 1.0.3 Formal is retained in the Library. Do not rewrite released hashes, mutate the old binary or retag historical versions.
