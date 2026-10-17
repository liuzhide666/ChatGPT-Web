# G1 APK size and ABI analysis — implementation receipt

Date: 2026-10-17. Issue: [#14](https://github.com/liuzhide666/ChatGPT-Web/issues/14).

## Why this analysis exists

Prior GitHub Debug Probe build output was 528297891 bytes. That size was observed but not yet broken down by ABI and resource class. This report adds a **read-only analysis gate** before any attempt to remove or compress GeckoView native code.

## Implementation

- `tools/analyze_probe_apk.py`: enumerates ZIP classes, groups native `.so` by ABI, reports compressed/uncompressed byte totals, ELF `PT_LOAD` segment alignment (16 KiB compatibility indicator), and calls installed Android Build Tools `zipalign -c -P 16 -v 4` when available.
- `tests/test_apk_abi_size_report.py`: synthetic tests for 16 KiB and 4 KiB ELF PT_LOAD alignment, malformed ELF NOT_VERIFIED, ZIP path traversal rejection, and output evidence boundaries.
- CI executes the report **after successful Probe Debug APK build** and prints `APK_ABI_SIZE_REPORT` JSON. A nonzero `zipalign` result is reported as `FAIL` rather than hidden, but does not yet fail the baseline build; this is an **audit** before establishing a device acceptance gate. The script errors on malformed APK and unsafe/duplicate entries.

## Non-certification and next decision

A green CI report alone is **not** evidence of real-device support. 16 KiB eligibility requires correctly aligned unpacked `.so` ZIP offsets, ELF PT_LOAD alignments and functional runtime tests. If the report finds ABI/ELF/ZIP misalignment, open a specific remedial issue rather than claiming success. A size change is only allowed after identifying target ABI support on the actual Huawei runtime.

No signed release, GeckoView upgrade, `targetSdk` change or package identity change is included in this PR.
