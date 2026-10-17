#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 -m pytest -q
bash tools/run_all_host_tests.sh
node tools/verify_input_router_node.js app/src/main/assets/chatgpt_bridge/site_adapter.js app/src/main/assets/chatgpt_bridge/content.js
bash tools/compile_api37_contract.sh
bash tools/lint_newapi_min26.sh

echo "CHATGPT_WEB_1_1_0_FORMAL_SOURCE_GATE_OK"
