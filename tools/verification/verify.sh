#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
./gradlew --no-daemon --max-workers=2 build
python3 -m pip install -r tools/verification/requirements.txt
python3 tools/verification/validate_contract.py
node tools/verification/ui_security_test.cjs
node tools/verification/operations_ui_test.cjs
python3 tools/verification/stage1_baseline.py --next --java "${JAVA_HOME:?}/bin/java"
