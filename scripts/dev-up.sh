#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/infra/docker-compose.yml"
TIMEOUT_SECONDS="${DEV_UP_TIMEOUT_SECONDS:-60}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is not installed or not on PATH." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "docker is not reachable. If you use Colima, run: colima start" >&2
  exit 1
fi

docker compose -f "${COMPOSE_FILE}" up -d

deadline=$((SECONDS + TIMEOUT_SECONDS))
mysql_container=""

while [ "${SECONDS}" -lt "${deadline}" ]; do
  mysql_container="$(docker compose -f "${COMPOSE_FILE}" ps -q mysql 2>/dev/null || true)"
  if [ -n "${mysql_container}" ]; then
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${mysql_container}" 2>/dev/null || true)"
    if [ "${health}" = "healthy" ]; then
      echo "MySQL is healthy."
      exit 0
    fi
    if [ "${health}" = "running" ] && nc -z localhost 3306 >/dev/null 2>&1; then
      echo "MySQL is reachable on localhost:3306."
      exit 0
    fi
    echo "Waiting for MySQL readiness: ${health:-unknown}"
  else
    echo "Waiting for MySQL container..."
  fi
  sleep 2
done

docker compose -f "${COMPOSE_FILE}" ps mysql || true
echo "Timed out waiting for MySQL readiness after ${TIMEOUT_SECONDS}s." >&2
exit 1
