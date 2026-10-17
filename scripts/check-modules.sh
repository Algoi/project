#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec 9>server/.test.lock
flock 9
# Pure algorithms, architecture boundaries and filesystem adapters; no DB or .env needed.
exec ./mvnw -B -ntp -pl server -am test \
  -Dtest=AlgorithmContractTest,ModuleBoundaryTest,PrivateStorageTest \
  -Dsurefire.failIfNoSpecifiedTests=false
