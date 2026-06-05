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
ssh_cmd="ssh -i ~/.ssh/labelhub-deploy.pem"

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
rsync "${rsync_flags[@]}" --delete -e "$ssh_cmd" \
  "$repo_root/apps/web/dist/" \
  "$remote:/opt/labelhub/infra/web-dist/"

echo "Syncing source tree"
rsync "${rsync_flags[@]}" -e "$ssh_cmd" \
  --exclude=node_modules \
  --exclude=.git \
  --exclude=dist \
  --exclude=.env.prod \
  --exclude=web-dist \
  --exclude=.DS_Store \
  --exclude=.pnpm-store \
  --exclude=submission \
  --exclude=docs/screenshots \
  --exclude=docs/design-assets \
  "$repo_root/" \
  "$remote:/opt/labelhub/"
