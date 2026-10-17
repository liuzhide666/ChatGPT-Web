#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
BASELINE_APK="${1:?usage: run_113_part3_source_gate.sh BASELINE_APK}"
cd "$ROOT"
PYTHONPATH=. python3 -m pytest -q
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/run_all_host_tests.sh
node tools/verify_keyboard_neutrality_node.js "$ROOT"
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/compile_api37_contract.sh
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/lint_newapi_min26.sh
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/verify_geckoview153_abi.sh "$BASELINE_APK"
echo "CHATGPT_WEB_1_1_0_RC3_VC113_PART3_SOURCE_GATE_OK"
