#!/usr/bin/env bash
# Script to execute all DevOpsHub automated unit and integration tests
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

echo "=========================================================="
echo " Running DevOpsHub Automated Test Suite (JUnit 5)"
echo "=========================================================="

mvn test
