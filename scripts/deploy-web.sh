#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=1
  shift
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: scripts/deploy-web.sh [--dry-run]" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
remote="root@120.26.182.61"
ssh_key="${HOME}/.ssh/labelhub-deploy.pem"
ssh_cmd=(ssh -i "$ssh_key")
rsync_ssh_cmd="ssh -i $ssh_key"

rsync_flags=(-az)
if [[ "$DRY_RUN" -eq 1 ]]; then
  rsync_flags+=(-n --itemize-changes)
  echo "Dry run: rsync uses -n; local web build still runs so dist is current."
fi

echo "Building web assets"
(
  cd "$repo_root"
  pnpm --filter @labelhub/web build
)

echo "Syncing web dist"
rsync "${rsync_flags[@]}" --delete -e "$rsync_ssh_cmd" \
  "$repo_root/apps/web/dist/" \
  "$remote:/opt/labelhub/infra/web-dist/"

echo "Syncing source tree"
rsync "${rsync_flags[@]}" -e "$rsync_ssh_cmd" \
  --include=.env*.example \
  --exclude=node_modules \
  --exclude=.git \
  --exclude=dist \
  --exclude=.env \
  --exclude=.env.* \
  --exclude=.env.prod \
  --exclude=web-dist \
  --exclude=.DS_Store \
  --exclude=.pnpm-store \
  --exclude=.claude \
  --exclude=.codex \
  --exclude=application-secrets.yml \
  --exclude=logs \
  --exclude=*.log \
  --exclude=coverage \
  --exclude=*.tsbuildinfo \
  --exclude=mysql-data \
  --exclude=minio-data \
  --exclude=target \
  --exclude=/*.bundle \
  --exclude=/submission \
  --exclude=docs/screenshots \
  --exclude=docs/design-assets \
  "$repo_root/" \
  "$remote:/opt/labelhub/"

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "Dry run: skipped remote nginx reconciliation."
  exit 0
fi

echo "Reconciling nginx config bind mount"
"${ssh_cmd[@]}" "$remote" 'bash -se' <<'REMOTE'
set -euo pipefail

cd /opt/labelhub/infra

# Reconcile nginx config bind mount after source sync so file-level bind mounts
# cannot keep serving a stale inode after rsync replaces nginx/labelhub.conf.
host_hash="$(sha256sum nginx/labelhub.conf | awk '{print $1}')"
container_hash="$(docker exec infra-nginx-1 sha256sum /etc/nginx/conf.d/default.conf 2>/dev/null | awk '{print $1}' || true)"

if [[ "$host_hash" != "$container_hash" ]]; then
  docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --force-recreate nginx
else
  docker exec infra-nginx-1 nginx -s reload
fi

docker exec infra-nginx-1 nginx -t
container_hash="$(docker exec infra-nginx-1 sha256sum /etc/nginx/conf.d/default.conf | awk '{print $1}')"
if [[ "$host_hash" != "$container_hash" ]]; then
  echo "ERROR: nginx container config still differs from host config after reconciliation" >&2
  exit 1
fi

health_status="$(curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:8443/api/actuator/health)"
prometheus_status="$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8443/api/actuator/prometheus)"
internal_status="$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8443/api/internal/ai-review/context)"
web_status="$(curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:8443/)"

if [[ "$health_status" != "200" || "$web_status" != "200" || "$prometheus_status" != "404" || "$internal_status" != "404" ]]; then
  echo "ERROR: nginx edge probes failed: health=${health_status}, web=${web_status}, prometheus=${prometheus_status}, internal=${internal_status}" >&2
  exit 1
fi

printf 'nginx reconciled: health=%s web=%s prometheus=%s internal=%s\n' \
  "$health_status" "$web_status" "$prometheus_status" "$internal_status"
REMOTE
