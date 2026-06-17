#!/usr/bin/env bash
set -euo pipefail

# Keep this rsync exclude list in sync with scripts/deploy-web.sh.
# In particular, /submission must stay anchored so same-named source modules are not skipped.
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
remote="root@120.26.182.61"
remote_root="/opt/labelhub"
ssh_key="${HOME}/.ssh/labelhub-deploy.pem"
ssh_cmd=(ssh -i "$ssh_key")
rsync_ssh_cmd="ssh -i $ssh_key"

sync_started="$(date +%s)"

echo "Syncing source tree to ${remote}:${remote_root}/"
rsync -az -e "$rsync_ssh_cmd" \
  --exclude=node_modules \
  --exclude=.git \
  --exclude=dist \
  --exclude=.env.prod \
  --exclude=web-dist \
  --exclude=.DS_Store \
  --exclude=.pnpm-store \
  --exclude=/submission \
  --exclude=docs/screenshots \
  --exclude=docs/design-assets \
  "$repo_root/" \
  "$remote:${remote_root}/"

sync_finished="$(date +%s)"
sync_duration="$((sync_finished - sync_started))"

echo "Building and restarting api/agent on remote"
remote_output="$("${ssh_cmd[@]}" "$remote" 'bash -se' <<'REMOTE'
set -euo pipefail

cd /opt/labelhub/infra

docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build api agent

deadline="$((SECONDS + 120))"
status=""
while (( SECONDS < deadline )); do
  status="$(docker ps --filter name=infra-api-1 --format '{{.Status}}' || true)"
  if [[ "$status" == *"(healthy)"* ]]; then
    break
  fi
  sleep 3
done

if [[ "$status" != *"(healthy)"* ]]; then
  echo "ERROR: infra-api-1 did not become healthy within 120s. Last status: ${status:-missing}" >&2
  exit 1
fi

probe_output="$(docker exec infra-api-1 sh -c 'wget -qO- --server-response http://localhost:8080/api/sessions/1/attachments/xx 2>&1 | head -1' || true)"
if [[ "$probe_output" != *"401"* ]]; then
  echo "ERROR: attachment auth probe expected 401, got: ${probe_output:-<empty>}" >&2
  exit 1
fi

# Reconcile nginx config bind mount after source sync so file-level bind mounts
# cannot keep serving a stale inode after rsync replaces nginx/labelhub.conf.
host_nginx_hash="$(sha256sum nginx/labelhub.conf | awk '{print $1}')"
container_nginx_hash="$(docker exec infra-nginx-1 sha256sum /etc/nginx/conf.d/default.conf 2>/dev/null | awk '{print $1}' || true)"
if [[ "$host_nginx_hash" != "$container_nginx_hash" ]]; then
  docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --force-recreate nginx
else
  docker exec infra-nginx-1 nginx -s reload
fi

docker exec infra-nginx-1 nginx -t
container_nginx_hash="$(docker exec infra-nginx-1 sha256sum /etc/nginx/conf.d/default.conf | awk '{print $1}')"
if [[ "$host_nginx_hash" != "$container_nginx_hash" ]]; then
  echo "ERROR: nginx container config still differs from host config after reconciliation" >&2
  exit 1
fi

edge_health_status="$(curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:8443/api/actuator/health)"
edge_prometheus_status="$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8443/api/actuator/prometheus)"
edge_internal_status="$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8443/api/internal/ai-review/context)"
if [[ "$edge_health_status" != "200" || "$edge_prometheus_status" != "404" || "$edge_internal_status" != "404" ]]; then
  echo "ERROR: nginx edge probes failed: health=${edge_health_status}, prometheus=${edge_prometheus_status}, internal=${edge_internal_status}" >&2
  exit 1
fi

printf 'REMOTE_BUILD_RESULT=%s\n' 'docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build api agent completed'
printf 'REMOTE_CONTAINER_STATUS=%s\n' "$status"
printf 'REMOTE_PROBE_RESULT=%s\n' "$probe_output"
printf 'REMOTE_NGINX_RESULT=%s\n' "health=${edge_health_status}, prometheus=${edge_prometheus_status}, internal=${edge_internal_status}"
REMOTE
)"

build_result="$(printf '%s\n' "$remote_output" | sed -n 's/^REMOTE_BUILD_RESULT=//p' | tail -1)"
container_status="$(printf '%s\n' "$remote_output" | sed -n 's/^REMOTE_CONTAINER_STATUS=//p' | tail -1)"
probe_result="$(printf '%s\n' "$remote_output" | sed -n 's/^REMOTE_PROBE_RESULT=//p' | tail -1)"
nginx_result="$(printf '%s\n' "$remote_output" | sed -n 's/^REMOTE_NGINX_RESULT=//p' | tail -1)"

echo "同步耗时: ${sync_duration}s"
echo "构建结果: ${build_result:-unknown}"
echo "容器状态: ${container_status:-unknown}"
echo "探针结论: ${probe_result:-unknown}"
echo "Nginx 边界: ${nginx_result:-unknown}"
