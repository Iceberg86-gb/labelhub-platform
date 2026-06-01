#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/infra/docker-compose.yml"
TIMEOUT_SECONDS="${DEV_UP_TIMEOUT_SECONDS:-60}"

ensure_devcontainer_mysql_loopback() {
  if [ ! -f /.dockerenv ]; then
    return 0
  fi
  if nc -z localhost 3306 >/dev/null 2>&1; then
    return 0
  fi
  if ! nc -z host.docker.internal 3306 >/dev/null 2>&1; then
    return 0
  fi
  if ! command -v socat >/dev/null 2>&1; then
    echo "host.docker.internal:3306 is reachable, but socat is missing; localhost forwarding skipped." >&2
    return 1
  fi

  if ! pgrep -f "socat TCP-LISTEN:3306.*host.docker.internal:3306" >/dev/null 2>&1; then
    nohup socat TCP-LISTEN:3306,fork,reuseaddr TCP:host.docker.internal:3306 \
      >/tmp/labelhub-mysql-forward.log 2>&1 &
    sleep 1
  fi

  if nc -z localhost 3306 >/dev/null 2>&1; then
    echo "MySQL is reachable on localhost:3306 via host.docker.internal."
    return 0
  fi

  echo "Failed to forward localhost:3306 to host.docker.internal:3306." >&2
  return 1
}

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
      ensure_devcontainer_mysql_loopback
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
