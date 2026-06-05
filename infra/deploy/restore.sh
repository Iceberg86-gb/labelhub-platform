#!/usr/bin/env bash
# Drill only: restores a backup into a temporary MySQL database and temporary MinIO volume for verification.
set -euo pipefail

ENV_FILE="${ENV_FILE:-/opt/labelhub/infra/.env.prod}"
NETWORK="${LABELHUB_DOCKER_NETWORK:-labelhub-net}"
BACKUP_DIR="${1:?usage: restore.sh /opt/labelhub/backups/YYYYmmdd-HHMMSS}"
STAMP="$(date +%Y%m%d%H%M%S)"
TEMP_DB="labelhub_restore_check_${STAMP}"
TEMP_VOLUME="labelhub-minio-restore-check-${STAMP}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

cleanup() {
  docker run --rm --network "$NETWORK" -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD:-}" \
    -e TEMP_DB="$TEMP_DB" \
    mysql:8.0 sh -c 'mysql -h mysql -uroot -e "DROP DATABASE IF EXISTS \`$TEMP_DB\`"' >/dev/null 2>&1 || true
  docker volume rm "$TEMP_VOLUME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ ! -f "$ENV_FILE" ]]; then
  log "missing env file: $ENV_FILE"
  exit 1
fi
if [[ ! -f "$BACKUP_DIR/mysql.sql" || ! -f "$BACKUP_DIR/minio-data.tar.gz" ]]; then
  log "backup directory must contain mysql.sql and minio-data.tar.gz"
  exit 1
fi

# shellcheck source=/dev/null
set -a
. "$ENV_FILE"
set +a

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

log "restore mysql dump into temporary database ${TEMP_DB}"
docker run --rm --network "$NETWORK" \
  -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
  -e TEMP_DB="$TEMP_DB" \
  -v "$BACKUP_DIR:/backup:ro" \
  mysql:8.0 sh -c 'mysql -h mysql -uroot -e "CREATE DATABASE \`$TEMP_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" && mysql -h mysql -uroot "$TEMP_DB" < /backup/mysql.sql && mysql -N -h mysql -uroot -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''$TEMP_DB'\''"'

log "restore minio archive into temporary volume ${TEMP_VOLUME}"
docker volume create "$TEMP_VOLUME" >/dev/null
docker run --rm \
  -v "${TEMP_VOLUME}:/data" \
  -v "$BACKUP_DIR:/backup:ro" \
  alpine:3.20 sh -c 'tar -xzf /backup/minio-data.tar.gz -C /data && find /data -mindepth 1 -maxdepth 2 | head'

log "restore drill complete; temporary resources will be removed"
