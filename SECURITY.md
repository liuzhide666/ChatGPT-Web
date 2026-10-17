# Security and release boundaries

## Signing

- The original Android release identity exists outside this repository; **do not** upload the keystore, `.p12`, PEM private key or plaintext signer password.
- Any new signing pipeline must use a restricted secret manager, least-privilege CI environment and explicit release approval. Never print secret values into logs.
- `git commit` != signed APK; `CI PASS` != device compatibility; an installed APK != safe provenance.

## Sensitive user data

- Do not retain cookies, OAuth codes, message text, media recordings, clipboard contents or other account artifacts in Issues, Actions artifacts, logs, screenshots, analytics, or source files.
- Sanitize repro cases. Authentication must remain under the website's origin; do not introduce an application-owned credential collection flow.

## Automation

- Source CI is read-only (`contents: read`) and uses `pull_request` rather than privileged `pull_request_target`.
- No auto-deploy, auto-sign, automatic app upload, or automatic dependency bump on production baseline.
- Verify required checks and exact commit SHA before release; protect `main` and require PR review once the remote repo exists.

## Disclosure

Report security issues privately to the repository owner (do not include secrets in public Issues). No public bug bounty or disclosure SLA is implied.
