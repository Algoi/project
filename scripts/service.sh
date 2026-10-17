#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
# Only pass the port from .env to the manager; the backend loads its own credentials.
service_backend_port="$(
  if [[ -f "$project_root/.env" ]]; then source "$project_root/.env" >/dev/null || exit 1; fi
  printf '%s' "${SERVER_PORT:-8080}"
)"
exec python3 "$project_root/scripts/service-manager.py" --backend-port "$service_backend_port" "$@"
