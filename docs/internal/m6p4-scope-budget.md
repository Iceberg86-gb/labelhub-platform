# M6-P4 Scope Budget (Draft Based on M6-P4.0 Research)

> This is a draft budget. It becomes actionable only after the user裁决 final Q1-Q10 failure-semantics decisions.

## Theme

Robustness hardening for failure paths that became visible after M6-P3b observability:

- AI provider timeout/retry/failure evidence.
- Idempotency metrics compatibility with retry.
- Trusted Export object-storage residue cleanup.

## Provisional Implementation Tracks

### Track A: AI Provider Failure Evidence And Retry

| File / area | Change type | Strict-constraint class | Estimate |
|-------------|-------------|-------------------------|----------|
| `AiCallStatusCodes` | New constants file | Additive | ~15 lines |
| Failed call recorder | New helper or service method | Additive + behavior exception | ~60 lines |
| Provider retry policy config | New yml/properties fields | Additive config | ~50 lines |
| `AiReviewService.invokeProvider` | Replace single attempt with retry policy | Existing behavior exception | ~60 lines |
| Retry metrics | Add counters | Additive observability | ~40 lines |
| Tests | Failed-row, retryable/non-retryable, timeout config, metrics | Test-only | ~250-350 lines |

Draft total: **225-260 functional lines**.

### Track B: Export Object-Storage Cleanup

| File / area | Change type | Strict-constraint class | Estimate |
|-------------|-------------|-------------------------|----------|
| `ObjectStorageWriter` | Add delete method | Additive | ~25 lines |
| `ExportService.createSnapshot` | Track written keys and cleanup on failure | Existing behavior exception | ~45 lines |
| Tests | Partial write failure + cleanup verification | Test-only | ~120-180 lines |

Draft total: **70-90 functional lines**.

## V11 Migration

Pending Q1/Q10:

- If failed AI attempts need new diagnostic columns, V11 may add nullable failure diagnostics to `ai_calls`.
- If minimal failed rows can use existing `status`, `request_payload`, `response_payload`, and timestamps, V11 may not be required.
- Export cleanup recommendation does not require V11 if failed export-job persistence is deferred.

## OpenAPI

Pending Q1/Q10:

- AI failed-row persistence does not require OpenAPI if failed calls are visible through existing provenance lists.
- Retry/backoff behavior does not require OpenAPI.
- Export cleanup does not require OpenAPI.
- New public failure diagnostics would require a later compatible OpenAPI addition, but M6-P4.0 does not recommend that for the first implementation.

## Draft Commit Granularity

If recommendations are accepted and M6-P4 is not split:

1. `docs: finalize M6-P4 failure semantics decision`
2. `test: add AI robustness regression tests`
3. `feat: add AI failure status and retry configuration`
4. `feat: record failed AI provider attempts`
5. `feat: add retry/backoff and retry metrics`
6. `test: add export residue cleanup regressions`
7. `feat: cleanup export objects on snapshot failure`
8. `docs: record M6-P4 verification`

If total functional code exceeds 400 lines, split into:

- `M6-P4a`: AI failure evidence + retry.
- `M6-P4b`: Export residue cleanup.

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Failed `ai_calls` rows collide with idempotency unique key | Could block future successful retry with same idempotency key | Decide whether failed rows use same idempotency key, a suffixed attempt key, or are excluded from reuse queries. Must be settled before implementation. |
| Retry changes M6-P3b hit/miss semantics | Hit ratio could become attempt-based instead of review-based | Keep miss counter at one logical review and add separate retry counters. |
| Retrying non-retryable provider errors | Repeats bad requests and increases cost | Use existing `AiProviderException.isRetryable()`. |
| Failed-row persistence inside same transaction | Rollback may erase failure evidence | Use separate transaction or dedicated recorder if Q1=B. |
| Inline export cleanup failure | Cleanup itself can fail, leaving residue and masking original error | Cleanup should be best-effort and preserve the original `ExportFailureException` cause. |
| Export cleanup deletes valid files | Could destroy a completed snapshot if prefix tracking is wrong | Track exact keys written in current attempt, not broad prefixes. |

## Scope Stop Conditions

Stop and rescope before implementation if any condition triggers:

- Final裁决 requires both detailed failed AI statuses and failed export-job persistence.
- Failed `ai_calls` evidence requires non-additive schema changes or idempotency-key redesign.
- Retry implementation changes existing HIT/MISS/MISMATCH branch logic.
- Export cleanup needs background scanner/startup reconciliation.
- Total functional code estimate exceeds 400 lines.
- Test designs for retry and failed-row persistence contradict M6-P3b metric semantics.

## Verification Budget

Expected verification after implementation:

- Backend tests increase by at least 10-14 cases.
- Existing M6-P1 Q6 status invariant remains green.
- Existing M6-P3a-2 A2 fallback and calculator-path tests remain green.
- Existing M6-P3b hit/miss/mismatch tests remain green.
- Protected endpoint script passes.
- Sensitive scan passes.

