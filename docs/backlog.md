# Initial issue backlog (ready to transfer into GitHub Issues)

These are planned tasks, **not** existing GitHub Issues. Create as tracked issues after the private repository exists. All changes require appropriate review and verification.

| ID | Priority | Issue title | Definition of done |
| --- | --- | --- | --- |
| REPO-01 | P0 | Establish private repository and branch protection | private visibility verified, main review/check policy confirmed |
| CI-01 | P0 | Baseline source CI and toolchain parity | GitHub workflow green for known tests, Android build gate explicitly NOT_RUN if unavailable |
| SEC-01 | P0 | Separate release signing and credential custody | signing material absent from Git history, access plan documented |
| AND-01 | P0 | Reproducible Android build outside historical toolkit | fresh environment builds debug APK and reports provenance |
| AND-02 | P1 | Android targetSdk and system bar modernization spike | no behavior regression; device screenshots on window/IME cases |
| AND-03 | P1 | GeckoView upgrade isolation | abi/tests/login/media/device A/B documented before upgrade |
| AND-04 | P1 | Comprehensive input/file/media session regression | test matrix rows filled by observed device behavior |
| HAR-01 | P0 | Harmony 7 / ArkWeb login + core feature feasibility | device build, login, editor, media capability PASS/FAIL records |
| HAR-02 | P1 | ArkTS shell and privacy-safe browser integration | independent native app, no Gecko reuse, basic UX + tests |
| REL-01 | P1 | Artifact hash ledger and reproducible release procedure | source ↔ SHA ↔ binary ↔ signer ↔ device gate traceable |
