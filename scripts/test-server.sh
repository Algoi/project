#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec 9>server/.test.lock
flock 9
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
: "${DB_PASSWORD:?Set DB_PASSWORD or create .env}"
export DB_USER="${DB_USER:-root}"
test_database="elder_meal_test_$(date +%s)_${RANDOM}"
mysql_args=(-h 127.0.0.1 -u "$DB_USER")
cleanup() { MYSQL_PWD="$DB_PASSWORD" mysql "${mysql_args[@]}" -e "DROP DATABASE IF EXISTS \`$test_database\`"; }
trap cleanup EXIT
MYSQL_PWD="$DB_PASSWORD" mysql "${mysql_args[@]}" -e "CREATE DATABASE \`$test_database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
export TEST_DB_URL="jdbc:mysql://127.0.0.1:3306/$test_database?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
./mvnw -B -ntp -pl server -am -Dsurefire.failIfNoSpecifiedTests=false "${@:-test}"
