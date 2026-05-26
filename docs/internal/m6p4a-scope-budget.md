# M6-P4a Scope Budget

## Theme

AI Provider Failure Evidence + Retry Semantics.

M6-P4a implements the AI side of M6-P4 final裁决: failed provider attempts become append-only `ai_calls` facts, timeout/retry are configurable under the existing OpenAI-compatible provider config, deterministic exponential backoff is applied to retryable provider failures, and retry attempts are observed without corrupting M6-P3b hit/miss semantics.

## Final裁决 Applied

| Decision | Implementation boundary |
|----------|-------------------------|
| Q1 = B | Persist failed provider attempts as failed `ai_calls` facts. |
| Q2 = A | Add minimal `AiCallStatusCodes`: `COMPLETED`, `FAILED`. |
| Q3 = C | Add timeout only under existing `openai-compatible` provider config; do not build a provider registry. |
| Q4 = C | Add config-driven max attempts, default 3. |
| Q5 = B | Deterministic exponential backoff, no jitter in P4a. |
| Q6 = B | Retry only `AiProviderException.isRetryable()`. |
| Q7 = B | Retry within one logical MISS; do not re-check idempotency on each retry. |
| Q8 = C | Keep M6-P3b miss once; add separate retry counter. |

## Scope

| File / area | Change type | Strict-constraint class | Estimate |
|-------------|-------------|-------------------------|----------|
| `AiCallStatusCodes` | New constants file | Additive | ~15 lines |
| Failed AI call recorder | New helper/service, likely `REQUIRES_NEW` transaction | Additive + behavior exception | ~70-90 lines |
| `OpenAiCompatibleProperties` / yml | Add timeout, max-attempts, and backoff config | Additive provider-specific config | ~35-55 lines |
| Retry policy helper | New deterministic backoff utility | Additive | ~35-50 lines |
| `AiReviewService.invokeProvider` | Replace single provider attempt with configured retry wrapper | Existing behavior exception | ~45-65 lines |
| Retry metrics | Add provider retry counter, separate from idempotency hit/miss | Additive observability | ~20-35 lines |
| Tests | Failed-row, retryable/non-retryable, timeout config, retry metric, existing invariant guards | Test-only | ~280-380 lines |
| Docs | Verification + failure semantics record | Documentation | ~100-140 lines |

Expected functional budget: **220-310 lines**.

## Required Semantics

### Pre-Implementation Research Findings (M6-P4a Segment 1)

- `ai_calls.idempotency_key` is `VARCHAR(160) NOT NULL` with `UNIQUE KEY uk_ai_calls_idempotency (idempotency_key)` in `V202611220900__init_schema.sql`.
- Canonical key format is `submission:{submissionId}:provider:{providerName}:model:{modelName}:prompt:{promptVersion}` in `AiReviewService.idempotencyKey(...)`.
- Current unit-test canonical example: `submission:300:provider:mock:model:mock-v1:prompt:prompt-v1` = 59 chars.
- Current DeepSeek-style example: `submission:300:provider:openai-compatible:model:deepseek-v4-flash:prompt:prompt-v1` = 82 chars.
- Failed attempt suffix max for default 3 attempts is `#failed-attempt-3` = 17 chars.
- Current known max example: 82 + 17 = 99 chars, within the 160-char DB limit.
- Caveat: `TriggerAiReviewRequest.promptVersion` has no OpenAPI `maxLength`, so P4a implementation must not silently truncate if a future key exceeds 160. Failed-attempt key construction should explicitly validate length and fail fast rather than generating a truncated key.
- `AiReviewService.review(...)` is `@Transactional`; no existing production code uses `REQUIRES_NEW` or `TransactionTemplate`.
- `AiProviderException.isRetryable()` already exists and is set by provider code: timeouts/I/O/interruption are retryable, HTTP 5xx is retryable, malformed/4xx-style provider response errors are non-retryable.

### Logical MISS Compatibility

M6-P3b `labelhub.ai.idempotency.miss` remains a logical review-request counter. A provider retry does not increment miss again.

Retry attempts are observed through a separate metric, for example:

```text
labelhub.ai.provider.retry{provider="deepseek"}
```

### Failed `ai_calls` Evidence

Failed provider attempts are persisted because provider failure is provenance, cost, and retry evidence. Implementation must avoid blocking a future successful call with the canonical idempotency key. If failed rows would collide with the existing unique idempotency key, use a clearly documented attempt-specific key strategy in the P4a implementation prompt and decision-log.

### Timeout Scope

Timeout is provider behavior. M6-P4a adds it only to the existing OpenAI-compatible provider config. Do **not** introduce a generalized provider registry or multi-provider config hierarchy.

### Backoff Scope

Use deterministic exponential backoff for P4a. Jitter is deferred until concurrency evidence justifies it.

## Strict-Constraint Exceptions

| Exception | Location | Reason | R10 path |
|-----------|----------|--------|----------|
| Persist failed provider attempts | `AiReviewService` plus failed-call recorder | Current transaction rolls back before `ai_calls` insert, leaving no failed-call evidence | Dedicated failed-row commit + tests; revert restores no-row behavior |
| Configurable timeout/retry wrapper | OpenAI-compatible provider config + `invokeProvider` path | Replaces M3 hardcoded single attempt with configured deterministic retry | Dedicated retry commit + tests; revert restores single-attempt behavior |
| Retry metrics | observability component | Keeps M6-P3b hit/miss semantics clean while measuring retry attempts separately | Additive metric commit; revert removes retry counter only |

## Test Budget

Required P4a regression coverage:

- Retryable provider error retries up to configured max attempts.
- Non-retryable provider error does not retry.
- Failed final provider attempt persists a failed `ai_calls` row.
- Eventual success after retry preserves one logical miss and writes a completed `ai_calls` row.
- Retry counter increments for retry attempts, not logical miss count.
- Timeout config binds under OpenAI-compatible provider settings.
- Existing Q6 status invariant remains green.
- Existing A2 fallback and calculator-path tests remain green.
- Existing M6-P3b hit/miss/mismatch tests remain green.

## Stop Conditions

Stop and rescope before implementation if:

- Failed-row persistence requires non-additive schema redesign.
- Failed rows cannot avoid colliding with canonical idempotency reuse without broad mapper changes.
- Retry implementation changes existing HIT/MISS/MISMATCH branch semantics.
- Functional code estimate exceeds 330 lines.
- Retry metrics expand into latency/error dashboards or alerting.
