# ADR-015 OpenAPI Contract Drift Control

## Status

Accepted

## Context

`packages/contracts/openapi/labelhub.yaml` is the contract source for frontend types and backend generated interfaces. During M0 scaffolding, simplifying the contract can accidentally remove endpoints that are not implemented yet but are required for baseline demos.

## Decision

OpenAPI endpoints tied to the four baseline differentiators must remain in the contract even before implementation:

- Schema historical rendering: `/submissions/{submissionId}/render-schema`
- AI provenance: `/ai-review/field-assist` and `/submissions/{submissionId}/ai-trace`
- Quality Ledger re-derivation: `/adjudication-rules/{ruleId}/recompute`
- Trusted Export diff: `/exports/snapshots/{snapshotId}/diff`

Any future removal or semantic rename of these endpoints must be recorded in `docs/internal/decision-log.md` and requires a matching ADR update.

`scripts/check-protected-endpoints.sh` is the local guard for this ADR. It greps the OpenAPI contract for the five protected endpoint paths and exits non-zero when one is missing.

## Consequences

- The contract remains aligned with the defense baseline.
- M0 can stay skeletal without hiding future demo requirements.
- Contract simplification is allowed only when it is explicit and traceable.
