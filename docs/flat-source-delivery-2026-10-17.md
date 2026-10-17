# Flat ZIP generation from the latest GitHub source

Generated on demand/PR/main by [flat-source workflow](https://github.com/liuzhide666/ChatGPT-Web/actions/workflows/flat-source.yml).

1. Open a successful `flat-source` run for the required commit SHA.
2. Download the artifact named `ChatGPT-Web-ALL-FILES-FLAT-<commit sha>`.
3. The artifact is a **single ZIP**, with all sources and metadata in the root and **zero nested paths**. Unlike GitHub's built-in `Download ZIP`, the file entries do not have directory prefixes.
4. After extracting it, run `python3 restore_flat.py . OUTPUT_DIRECTORY` to restore `ChatGPT-Web/` source hierarchy, checking source SHA-256 and executable file modes.

Each generated ZIP is tied to a Git commit SHA and includes the verified Gradle Wrapper JAR and other tracked sources, but intentionally excludes the legacy root-level `ChatGPT-Web-ALL-FILES-FLAT-2026-10-17.zip` to prevent nesting archives and shipping stale source. This is **source only**, not a release-signed APK/HAP. No credentials, private signing keys, user data, release APK binaries or historical multi-volume archives belong in this public repository or its artifacts.

GitHub Actions artifacts have a retention limit (currently configured to 30 days). For durable retention, store the validated ZIP under the project Library `source/` alongside an independently recorded SHA-256. Older root-level ZIP remains historical; always prefer the latest successful artifact whose SHA matches the selected source commit.
