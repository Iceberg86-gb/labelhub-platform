# M6-P5 Final Regression Report

Date: 2026-05-26

## Executive Summary

M6-P5 completed the final regression and operational verification pass for the LabelHub 9-month engineering project and M6+ hardening quarter.

Final verification on 2026-05-26:

- Four defense highlight evidence chains are green against focused smoke coverage.
- M6 Signal 1 A/B class is closed:
  - A class: token persistence, USD cost computation, idempotency metrics.
  - B class: AI provider failure evidence/retry and Trusted Export inline cleanup.
- Backend full suite: `389` tests, `0` failures, `0` errors, `78` skipped after rerunning with local socket permission.
- Frontend `typecheck` passes.
- Frontend `build` passes with the known Vite chunk-size warning.
- Protected OpenAPI endpoint guard passes.
- OpenAPI remains `0.10.0`.
- Migration count remains `10`.
- Sensitive scan found only placeholder environment variable names, not secrets.
- No production code, OpenAPI, migration, or behavior changes were made in M6-P5.

## Verification Commands

### Focused Cross-Phase Smoke

Command:

```bash
mvn -pl services/api -Dtest=SchemaServiceTest,SubmissionServiceTest,SessionServiceTest,LedgerServiceTest,VerdictServiceTest,ReviewerQueueServiceTest,ExportServiceTest,AiReviewServiceTest,AiCallCostCalculatorTest,AiRetryPolicyTest,FailedAiCallRecorderTest,AiIdempotencyMetricsTest,ActuatorPrometheusEndpointExposureTest test
```

Result:

- `BUILD SUCCESS`
- `Tests run: 156`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 0`

### Backend Full Suite

Initial sandbox run:

- Command: `mvn -pl services/api test`
- Result: `389` tests started, but `OpenAiCompatibleProviderTest` hit 9 `Socket Operation not permitted` errors while binding a local test HTTP server.
- Classification: D-口径 sandbox socket-bind limitation, matching previous M6-P3/P4 behavior.

Escalated rerun:

- Command: `mvn -pl services/api test`
- Result: `BUILD SUCCESS`
- `Tests run: 389`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 78`

The 78 skipped tests are Docker/Testcontainers-gated integration tests in this local environment.

### Frontend Verification

Typecheck:

```bash
pnpm --filter @labelhub/web typecheck
```

Result: exit `0`; OpenAPI TypeScript generation completed and `tsc` passed.

Build:

```bash
pnpm --filter @labelhub/web build
```

Result: exit `0`; Vite built the application successfully.

Known warning:

- Vite reports chunks larger than 500 kB after minification.
- Classification: existing build-size warning, not a P5 regression.

### Contract / Migration / Guard Checks

Protected endpoints:

```bash
bash scripts/check-protected-endpoints.sh
```

Result:

- `Protected OpenAPI endpoints are present.`

OpenAPI version:

```bash
rg -n "version: 0\.10\.0" packages/contracts/openapi/labelhub.yaml
```

Result:

- `4:  version: 0.10.0`

Migration count:

```bash
find services/api/src/main/resources/db/migration -maxdepth 1 -name 'V*.sql' -print | wc -l
```

Result:

- `10`

Sensitive scan:

```bash
rg -n "sk-[A-Za-z0-9]{20,}|AI_API_KEY|OPENAI_API_KEY|DEEPSEEK_API_KEY" docs apps services packages scripts
```

Result:

- Placeholder env var references only:
  - `services/api/src/main/resources/application.yml`: `${AI_API_KEY:}`
  - `services/agent/src/main/resources/application.yml`: `${OPENAI_API_KEY:}`
  - docs references to `AI_API_KEY` / `OPENAI_API_KEY`
- No literal API key material detected.

## Four Defense Highlights

### Highlight 1: Schema Versioning + Immutable Facts

Status: Green.

Evidence:

- `SchemaServiceTest`, `SubmissionServiceTest`, and `SessionServiceTest` passed in focused smoke.
- Existing historical-render screenshots remain archived under `docs/screenshots/`.
- M6 migrations V9/V10 do not alter schema-version immutability.

Defense answer:

Old submissions are not reinterpreted by new schema versions. The submitted answer fact keeps its schema-version pointer.

### Highlight 2: Quality Ledger + Verdict Derivation

Status: Green.

Evidence:

- `LedgerServiceTest`, `VerdictServiceTest`, and `ReviewerQueueServiceTest` passed in focused smoke.
- M6-P1 guardrail `ai_review_does_not_mutate_submission_status` remains covered by `AiReviewServiceTest`.
- Submission lifecycle now consistently uses `submitted` for reviewer/export visibility.

Defense answer:

Reviewer verdict is derived from append-only ledger facts, not from mutable submission status alone.

### Highlight 3: Trusted Export Reproducibility

Status: Green.

Evidence:

- `ExportServiceTest` passed in focused smoke and full backend suite.
- M6-P4b cleanup tests passed:
  - partial failure deletes exact written keys,
  - cleanup does not delete unrelated objects,
  - cleanup failure preserves original `ExportFailureException`,
  - successful export does not trigger cleanup,
  - failed export jobs are not persisted.

Defense answer:

Successful export remains reproducible. Failed object-storage writes are cleaned inline best-effort, and failed export-job persistence is intentionally deferred as false symmetry until async export/job化 exists.

### Highlight 4: AI Provenance + Real Provider Evidence

Status: Green.

Evidence:

- `AiReviewServiceTest`, `AiCallCostCalculatorTest`, `AiRetryPolicyTest`, `FailedAiCallRecorderTest`, `AiIdempotencyMetricsTest`, `ActuatorPrometheusEndpointExposureTest`, and `OpenAiCompatibleProviderTest` passed in final backend suite.
- Token persistence, USD cost computation, idempotency metrics, retry policy, and failed-attempt rows all remain covered.

Defense answer:

AI review has full provenance: token usage, USD cost, idempotency hit/miss/mismatch metrics, failed-attempt evidence rows, and retry semantics.

## Signal 1 Closure

### A Class: Cost / Performance Quantification

Closed by:

- M6-P3a: provider token usage persistence.
- M6-P3a-2: USD cost computation from complete prompt+completion usage with fixed-estimate fallback.
- M6-P3b: idempotency hit/miss/mismatch metrics exposed through Prometheus.

Saved-cost derivation:

```text
saved_cost = hits_total * avg(cost_decimal for real provider invocations)
```

Current caveat: stable hit-ratio claims require accumulated real usage, ideally 100+ review attempts over a 7-day window.

### B Class: Robustness / Failure Semantics

Closed by:

- M6-P4.0: failure semantics final裁决.
- M6-P4a: failed AI provider attempts persist as evidence rows; retry uses deterministic exponential backoff and `isRetryable()`.
- M6-P4b: Trusted Export cleans exact written object keys on failure and preserves the original export exception.

False symmetry decision:

AI provider failures are provenance/cost/retry evidence. Synchronous export failures currently have no API/UI consumer for failed job facts, so failed export-job persistence is intentionally deferred.

## Decision-Log + Humanpending Consistency

See `docs/internal/m6p5-audit.md`.

Summary:

- No active `Status: draft` or `draft pending user裁决` marker remains in M6 docs.
- M6-P0.5 and M6-P4.0 final decisions are recorded.
- M6 implementation phases P1/P2/P3a/P3a-2/P3b/P4a/P4b have decision-log coverage.
- Humanpending top-level M6 entries are now resolved/ready/watch/deferred with explicit state.
- D-口径 items are explicit: Docker/Testcontainers skip, browser screenshot tooling limitation, metrics accumulation, production actuator security review.

## Discovered Gaps

| Gap | Status | Handling |
|-----|--------|----------|
| Fresh browser screenshots unavailable in this tool context | D-口径 | Screenshot targets indexed in `docs/screenshots/m6p5-smoke-set/INDEX.md`; existing screenshots remain archived. |
| Docker/Testcontainers skipped | D-口径 | Full non-Docker backend suite green; Docker-enabled machine remains a humanpending watch. |
| 7-day idempotency hit-ratio data not mature | Watch | Metrics endpoint is ready; stable claim requires real usage window. |
| M6-P3c large-task performance baseline not run | Optional deferred | M6-P5 did not uncover a blocking scale-evidence gap requiring P3c before defense. |
| CNY v4-flash pricing source unavailable | Deferred | USD official English pricing remains current system of record. |
| v4-pro discount expiry | Watch | Config refresh needed if v4-pro is used after `2026-05-31 15:59 UTC`. |
| Production actuator security | Watch | `/actuator/prometheus` and `/actuator/metrics` are dev-observability exposed; productionization needs a separate review. |

No P0/P1 issue was discovered during M6-P5.

## Defense Readiness Summary

### Question: What are the four highlights?

1. Schema versioning + immutable submitted answer facts.
2. Quality Ledger + verdict derivation.
3. Trusted Export reproducibility.
4. AI provenance + real provider evidence.

### Question: How do you know AI cost is real?

M6-P3a persists provider token usage. M6-P3a-2 computes USD cost from prompt/completion tokens when both are present, with fixed-estimate fallback when data is incomplete. R2 rounding uses high-precision internal `BigDecimal` and writes to `DECIMAL(12,6)`.

### Question: How do you know idempotency saves money?

M6-P3b exposes hit/miss/mismatch counters. Combined with M6-P3a-2 cost rows:

```text
saved_cost = hits_total * avg(real invocation cost)
```

The endpoint is ready; a stable percentage needs real usage accumulation.

### Question: What happens if DeepSeek fails?

M6-P4a uses configurable timeout/retry under `openai-compatible`, deterministic exponential backoff, and `AiProviderException.isRetryable()`. Failed attempts persist as `ai_calls` rows with `{canonical_key}#failed-attempt-{n}` keys. Retry attempts are counted separately through `labelhub.ai.provider.retry`.

### Question: What happens if export fails halfway?

M6-P4b tracks exact written object keys and cleans them best-effort before rethrowing the original `ExportFailureException`. Cleanup failure is logged and does not mask the original export failure.

### Question: Why are failed AI calls persisted but failed export jobs are not?

Because mirroring them would be false symmetry. AI failures are consumed by provenance/cost/retry evidence. Synchronous export failed-job rows have no current API/UI consumer and would introduce extra transaction/state-machine debt. Failed export persistence is deferred to a future async export/job phase.

### Question: Where is the engineering discipline?

- Two system-level semantics decisions: M6-P0.5 lifecycle and M6-P4.0 failure semantics.
- Strict-constraint exceptions are recorded with R10 paths.
- Cross-phase guardrails are tested: lifecycle Q6, A2 cost fallback, calculator path, idempotency hit/miss/mismatch, retry counter, failed-attempt key strategy, and export cleanup.
- Humanpending keeps deferred/watch items visible instead of hiding them.

## Recommended Talk Track

1. Start with the four product highlights and show the evidence chain behind each.
2. Show M6 as the 60-day hardening quarter:
   - lifecycle repair,
   - cost/performance quantification,
   - robustness/failure semantics.
3. Use M6-P0.5 and M6-P4.0 as examples of system-level semantics decisions before code.
4. Use the discovered gaps table to show calibrated reporting rather than overclaiming.

## Final State

M6-P5 did not require production code changes. LabelHub is defense-ready with known optional/deferred items clearly recorded.

