#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
: "${DB_PASSWORD:?Set DB_PASSWORD or create .env}"
export DB_USER="${DB_USER:-root}"
MYSQL_PWD="$DB_PASSWORD" mysql -h 127.0.0.1 -u "$DB_USER" -e "CREATE DATABASE IF NOT EXISTS elder_meal CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
export APP_UPLOAD_PATH="${APP_UPLOAD_PATH:-./uploads/private}"
if [[ "$APP_UPLOAD_PATH" != /* ]]; then export APP_UPLOAD_PATH="$PWD/$APP_UPLOAD_PATH"; fi
./mvnw -B -ntp -pl contracts,algorithms -am install -DskipTests
cd server
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev "$@"
