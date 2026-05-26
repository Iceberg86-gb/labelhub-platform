# M6-P5 Four-Light Evidence Revalidation

Date: 2026-05-26

## Purpose

M6-P5 revalidates the four defense highlights after the M6+ hardening work. The goal is not to add new claims, but to prove the original evidence chains still hold after lifecycle repair, AI token/cost/metrics additions, retry semantics, and export cleanup.

Fresh focused smoke evidence:

```bash
mvn -pl services/api -Dtest=SchemaServiceTest,SubmissionServiceTest,SessionServiceTest,LedgerServiceTest,VerdictServiceTest,ReviewerQueueServiceTest,ExportServiceTest,AiReviewServiceTest,AiCallCostCalculatorTest,AiRetryPolicyTest,FailedAiCallRecorderTest,AiIdempotencyMetricsTest,ActuatorPrometheusEndpointExposureTest test
```

Result: `156` tests, `0` failures, `0` errors, `0` skipped.

## Highlight 1: Schema Versioning + Immutable Facts

### Original Claim

LabelHub treats published schema versions and submitted answers as immutable evidence. A labeler submission points to the schema version used when it was created; later schema changes do not rewrite historical answers.

### M6-P5 Verification Targets

| Target | Evidence | Status |
|--------|----------|--------|
| Schema publishing and version history still covered | `SchemaServiceTest` focused smoke | Green |
| Session/submission still locks schema version | `SessionServiceTest`, `SubmissionServiceTest` focused smoke | Green |
| Historical renderer evidence remains archived | Existing screenshots under `docs/screenshots/phase-m2p6c-*` | Archived |
| M6 migrations do not alter schema-version semantics | Migration count remains V1-V10; V9 submission lifecycle and V10 AI token columns do not touch schema tables | Green |

### M6 Interaction Check

- M6-P1 changed submission lifecycle defaults, not schema-version binding.
- M6-P3a added token columns to `ai_calls`, not schema/submission schema-version tables.
- M6-P4a/P4b added failure evidence and cleanup behavior, not schema rendering.

### Defense Readiness Note

The answer to "can old labels be reinterpreted by a new schema?" remains no. Submitted facts retain their schema-version pointer; M6 did not introduce a path that rewrites historical schema references.

## Highlight 2: Quality Ledger + Verdict Derivation

### Original Claim

Reviewer actions and AI review evidence are append-only facts. Verdict is derived from ledger state rather than blindly trusting mutable submission status.

### M6-P5 Verification Targets

| Target | Evidence | Status |
|--------|----------|--------|
| Ledger entries are appended for reviewer actions | `LedgerServiceTest` focused smoke | Green |
| Verdict derives correctly from ledger state | `VerdictServiceTest` focused smoke | Green |
| Reviewer queue reads submitted lifecycle consistently | `ReviewerQueueServiceTest` focused smoke | Green |
| M6-P1 lifecycle guardrail remains active | `AiReviewServiceTest.ai_review_does_not_mutate_submission_status` in focused smoke | Green |

### M6 Interaction Check

- M6-P1 repaired the status boundary: labeler submit writes `submitted`; AI review does not mutate submission status.
- M6-P3b metrics observe idempotency branches without changing ledger semantics.
- M6-P4a failed AI attempts are evidence rows, not verdict entries and not cache entries.

### Defense Readiness Note

The answer to "what decides the final reviewer outcome?" remains the ledger-derived verdict. M6-P1 made that answer cleaner by removing the old `under_ai_review` default-flow ambiguity.

## Highlight 3: Trusted Export Reproducibility

### Original Claim

Trusted Export is reproducible: same task state yields the same exported artifacts and hashes, making evidence packages verifiable.

### M6-P5 Verification Targets

| Target | Evidence | Status |
|--------|----------|--------|
| Export artifact generation still passes | `ExportServiceTest` focused smoke | Green |
| Reproducibility tests remain intact | `ExportServiceTest` focused smoke | Green |
| M6-P4b cleanup does not affect successful export hash path | `successful_export_does_not_trigger_cleanup` and existing export success cases | Green |
| Failed export cleanup deletes exact written keys only | M6-P4b cleanup tests in `ExportServiceTest` | Green |

### M6 Interaction Check

- M6-P1 aligned export source status with `submitted`.
- M6-P4b changed only the failure catch path and exact-key cleanup behavior.
- M6-P4b explicitly does not persist failed export jobs; this preserves current synchronous export API shape and avoids false symmetry.

### Defense Readiness Note

The answer to "is export reproducible, and what happens if storage fails halfway?" is now stronger than M5: success remains reproducible, and partial object-storage writes are cleaned up best-effort on failure without masking the original export error.

## Highlight 4: AI Provenance + Real Provider Evidence

### Original Claim

AI review is traceable: provider request/response evidence, idempotency, cost, and provenance facts can be audited instead of treated as invisible side effects.

### M6-P5 Verification Targets

| Target | Evidence | Status |
|--------|----------|--------|
| AI review writes provenance rows | `AiReviewServiceTest` focused smoke | Green |
| Token persistence remains wired | M6-P3a token persistence cases in `AiReviewServiceTest` | Green |
| USD cost calculator and fallback rules remain guarded | `AiCallCostCalculatorTest`, `AiReviewServiceTest` cost cases | Green |
| Idempotency hit/miss/mismatch counters remain exposed | `AiIdempotencyMetricsTest`, `ActuatorPrometheusEndpointExposureTest` | Green |
| Failed AI provider attempts persist attempt-suffixed rows | `FailedAiCallRecorderTest`, `AiReviewServiceTest` M6-P4a cases | Green |
| Retry policy remains deterministic and retryable-only | `AiRetryPolicyTest`, `AiReviewServiceTest` M6-P4a cases | Green |

### M6 Interaction Check

- M6-P3a added raw token persistence without changing cost.
- M6-P3a-2 switched cost to calculator output when prompt+completion tokens are complete.
- M6-P3b added hit/miss/mismatch counters and a Prometheus endpoint.
- M6-P4a added failed-attempt evidence and provider retry metrics while preserving M6-P3b hit/miss semantics.

### Defense Readiness Note

The answer to "what happens if DeepSeek is slow or fails?" is now concrete: timeout and retry are configurable under the existing OpenAI-compatible provider config, only retryable exceptions are retried, failed attempts persist as evidence rows with attempt-specific keys, and retry attempts are measured separately from idempotency misses.

## Deferred / Watch Items

| Item | Status | Defense wording |
|------|--------|-----------------|
| M6-P3c large-task performance baseline | Optional deferred | M6-P5 did not uncover a blocking scale-evidence gap; P3c remains a targeted follow-up if final review needs large-volume numbers. |
| 7-day idempotency hit-ratio data | Watch | Endpoint and counters are ready; stable hit ratio requires real usage over time. |
| CNY/v4-flash pricing source | Deferred | USD pricing is official English-doc source; CNY v4-flash remains unresolved until reliable source exists. |
| Async export failed-job persistence | Deferred | M6-P4.0 Q10=A: failed export persistence would be false symmetry until async export/job UI exists. |

## Revalidation Summary

All four highlight chains remain green against the focused M6-P5 smoke suite. Full backend/frontend verification is recorded in the final regression report.

