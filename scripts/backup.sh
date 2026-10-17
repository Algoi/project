#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"
if [[ -f .env ]]; then set -a; source .env; set +a; fi
exec python3 scripts/backup-manager.py "$@"
