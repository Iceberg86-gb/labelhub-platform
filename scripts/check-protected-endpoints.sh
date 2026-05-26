#!/usr/bin/env sh
set -eu

CONTRACT_FILE="packages/contracts/openapi/labelhub.yaml"

if [ ! -f "$CONTRACT_FILE" ]; then
  echo "Missing OpenAPI contract: $CONTRACT_FILE" >&2
  exit 1
fi

missing=0

check_endpoint() {
  endpoint="$1"
  if ! grep -Fq "$endpoint" "$CONTRACT_FILE"; then
    echo "Missing protected endpoint: $endpoint" >&2
    missing=1
  fi
}

check_endpoint "/submissions/{submissionId}/render-schema"
check_endpoint "/ai-review/field-assist"
check_endpoint "/submissions/{submissionId}/ai-trace"
check_endpoint "/adjudication-rules/{ruleId}/recompute"
check_endpoint "/exports/snapshots/{snapshotId}/diff"

if [ "$missing" -ne 0 ]; then
  exit 1
fi

echo "Protected OpenAPI endpoints are present."
