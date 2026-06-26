#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

assert_has_exclude() {
  local script_path="$1"
  local pattern="$2"

  if ! grep -F -- "--exclude=${pattern}" "$script_path" >/dev/null; then
    printf 'Expected %s to exclude %s from production source sync.\n' "$script_path" "$pattern" >&2
    return 1
  fi
}

assert_has_include() {
  local script_path="$1"
  local pattern="$2"

  if ! grep -F -- "--include=${pattern}" "$script_path" >/dev/null; then
    printf 'Expected %s to keep %s available for production source sync.\n' "$script_path" "$pattern" >&2
    return 1
  fi
}

for script in "$repo_root/scripts/deploy-web.sh" "$repo_root/scripts/deploy-api.sh"; do
  assert_has_include "$script" ".env*.example"

  assert_has_exclude "$script" "target"
  assert_has_exclude "$script" "/*.bundle"
  assert_has_exclude "$script" ".env"
  assert_has_exclude "$script" ".env.*"
  assert_has_exclude "$script" ".claude"
  assert_has_exclude "$script" ".codex"
  assert_has_exclude "$script" "application-secrets.yml"
  assert_has_exclude "$script" "logs"
  assert_has_exclude "$script" "*.log"
  assert_has_exclude "$script" "coverage"
  assert_has_exclude "$script" "*.tsbuildinfo"
  assert_has_exclude "$script" "mysql-data"
  assert_has_exclude "$script" "minio-data"
done
