#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLKIT_ROOT="${TOOLKIT_ROOT:-/mnt/data/android-build-toolkit-linux-x86_64}"
BASELINE_APK="${1:?usage: run_112_rc2_source_gate.sh BASELINE_APK}"
cd "$ROOT"

python3 -m pytest -q
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/run_all_host_tests.sh
node tools/verify_input_router_node.js app/src/main/assets/chatgpt_bridge/site_adapter.js app/src/main/assets/chatgpt_bridge/content.js
node tools/verify_effect_verification_node.js app/src/main/assets/chatgpt_bridge/site_adapter.js app/src/main/assets/chatgpt_bridge/content.js
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/compile_api37_contract.sh
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/lint_newapi_min26.sh
TOOLKIT_ROOT="$TOOLKIT_ROOT" bash tools/verify_geckoview153_abi.sh "$BASELINE_APK"

echo "CHATGPT_WEB_1_1_0_RC2_VC112_SOURCE_GATE_OK"
