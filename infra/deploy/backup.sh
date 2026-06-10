#!/usr/bin/env bash
# Install with: (crontab -l; echo "0 4 * * * /opt/labelhub/infra/deploy/backup.sh >> /var/log/labelhub-backup.log 2>&1") | crontab -
set -euo pipefail
# Backups contain plaintext data (mysql dump + minio objects); keep them owner-only.
umask 077

ENV_FILE="${ENV_FILE:-/opt/labelhub/infra/.env.prod}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/labelhub/backups}"
NETWORK="${LABELHUB_DOCKER_NETWORK:-labelhub-net}"
MINIO_VOLUME="${LABELHUB_MINIO_VOLUME:-labelhub-minio-data}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="${BACKUP_ROOT}/${STAMP}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

if [[ ! -f "$ENV_FILE" ]]; then
  log "missing env file: $ENV_FILE"
  exit 1
fi

# shellcheck source=/dev/null
set -a
. "$ENV_FILE"
set +a

MYSQL_DATABASE="${MYSQL_DATABASE:-labelhub}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

mkdir -p "$BACKUP_DIR"
# Restrict directory access so the plaintext dump/objects inside are not readable by other local
# users (container-written files may not inherit the host umask, but a 700 dir gates access).
chmod 700 "$BACKUP_ROOT" "$BACKUP_DIR"

log "dump mysql database ${MYSQL_DATABASE}"
docker run --rm --network "$NETWORK" \
  -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
  -e MYSQL_DATABASE="$MYSQL_DATABASE" \
  -v "$BACKUP_DIR:/backup" \
  mysql:8.0 sh -c 'mysqldump --single-transaction --routines --triggers -h mysql -uroot "$MYSQL_DATABASE" > /backup/mysql.sql'

log "archive minio volume ${MINIO_VOLUME}"
docker run --rm \
  -v "${MINIO_VOLUME}:/data:ro" \
  -v "$BACKUP_DIR:/backup" \
  alpine:3.20 tar -C /data -czf /backup/minio-data.tar.gz .

log "write manifest"
{
  printf 'created_at=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  printf 'mysql_database=%s\n' "$MYSQL_DATABASE"
  printf 'minio_volume=%s\n' "$MINIO_VOLUME"
} > "$BACKUP_DIR/manifest.txt"

log "retain latest 7 backups"
mapfile -t old_dirs < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d | sort | head -n -7)
for old_dir in "${old_dirs[@]}"; do
  log "remove old backup ${old_dir}"
  rm -rf "$old_dir"
done

log "backup complete: $BACKUP_DIR"
