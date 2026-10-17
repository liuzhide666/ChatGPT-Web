# Contribution and branching policy

- `main`: reviewed, buildable source and documentation; do not commit unsigned claims of device certification.
- `feature/*`, `fix/*`, `research/*`: short-lived change branches; use PRs into `main`.
- Release source is tagged after all appropriate gates. Proposed Android `v1.1.1-vc114` and native Harmony builds have separate version spaces; tags/releases exist only after independently verified output.
- Every PR must state scope, changed contracts, tests actually run, unrun tests, and rollback path.
- Never merge changes that silently change input authority, clear browser account/profile state, or introduce persistent watchdog reloads.
- Preserve history; retain old APK and source hashes in release ledger rather than overwriting the 1.1.0 baseline.
- Do not use personal signing material for CI without a separate security design and explicit approval.
