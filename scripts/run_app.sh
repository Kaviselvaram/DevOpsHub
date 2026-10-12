#!/usr/bin/env bash
# Script to launch DevOpsHub JavaFX Desktop Application
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

echo "=========================================================="
echo " Starting DevOpsHub Desktop MVP"
echo " Platform: JavaFX 21 on Java $(java -version 2>&1 | head -n 1)"
echo " Database: MySQL on localhost:3306"
echo "=========================================================="

mvn javafx:run
