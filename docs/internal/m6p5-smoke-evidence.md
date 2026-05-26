# M6-P5 Smoke Evidence

Date: 2026-05-26

## Scope

M6-P5 re-runs the core evidence paths after M6-P4b. This smoke evidence file records command-backed verification, browser/screenshot D-口径, and any gaps discovered before the final regression report.

M6-P5 remains a verification phase: no production code, OpenAPI, migration, or new test changes are introduced here.

## Fresh Command Evidence

### Focused Cross-Phase Smoke Suite

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

Coverage:

| Area | Test class coverage | Status |
|------|---------------------|--------|
| Schema versioning + immutable submission facts | `SchemaServiceTest`, `SubmissionServiceTest`, `SessionServiceTest` | Green |
| Quality Ledger + verdict derivation | `LedgerServiceTest`, `VerdictServiceTest`, `ReviewerQueueServiceTest` | Green |
| Trusted Export + M6-P4b cleanup | `ExportServiceTest` | Green |
| AI provenance, token/cost, idempotency metrics, retry evidence | `AiReviewServiceTest`, `AiCallCostCalculatorTest`, `AiRetryPolicyTest`, `FailedAiCallRecorderTest`, `AiIdempotencyMetricsTest`, `ActuatorPrometheusEndpointExposureTest` | Green |

### Full Verification

Full backend, frontend, protected-route, sensitive-scan, migration, and OpenAPI checks are reserved for the final M6-P5 report so the report can cite one fresh final verification block.

## Smoke Checklist

| Track | Check | Evidence | Status |
|-------|-------|----------|--------|
| M2 | 17-step owner/labeler/schema/session smoke | Covered by schema/session/submission focused suite; live browser replay not available in this tool session | Green with D-口径 |
| M3 | AI review provenance and idempotency reuse | `AiReviewServiceTest` focused suite | Green |
| M4 | Reviewer approval/rejection writes ledger and verdict derives from ledger | `LedgerServiceTest`, `VerdictServiceTest`, `ReviewerQueueServiceTest` | Green |
| M5 | Trusted Export reproducibility and cleanup safety | `ExportServiceTest` focused suite | Green |
| M6-P1 | Submitted lifecycle guardrail | `ai_review_does_not_mutate_submission_status` included in focused AI suite | Green |
| M6-P3a | Token persistence remains wired | `AiReviewServiceTest` token persistence cases | Green |
| M6-P3a-2 | A2 fallback and calculator path | `AiCallCostCalculatorTest`, `AiReviewServiceTest` cost cases | Green |
| M6-P3b | Prometheus idempotency counters exposed | `ActuatorPrometheusEndpointExposureTest` | Green |
| M6-P4a | Failed AI attempts persist with retry semantics | `FailedAiCallRecorderTest`, `AiRetryPolicyTest`, `AiReviewServiceTest` | Green |
| M6-P4b | Export failure cleans exact written keys and preserves original exception | `ExportServiceTest` M6-P4b cases | Green |

## Screenshot Status

Browser screenshot capture was not available through the exposed tools in this M6-P5 run. M6-P5 therefore records screenshot targets instead of claiming fresh browser captures.

Existing screenshot archive remains under `docs/screenshots/` and includes M2-M5 evidence such as schema publishing, historical rendering, AI drawer idempotency, reviewer queue, Quality Ledger, Trusted Export, and DeepSeek smoke screenshots.

New screenshot targets are indexed at `docs/screenshots/m6p5-smoke-set/INDEX.md` as a D-口径 artifact. If browser automation becomes available before defense, capture those targets without changing production code.

## Gaps Found During Segment 2

- Fresh browser screenshots were not captured in this tool session. This is a D-口径 limitation, not a product regression.
- Large-task M6-P3c performance evidence remains optional. Segment 2 did not uncover a scale-evidence gap that blocks M6-P5.

