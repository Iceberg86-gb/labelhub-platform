# M6-P4 Scope Budget Index (Final Split)

M6-P4.0 final裁决 split robustness hardening into two independent implementation phases:

- **M6-P4a:** AI Provider Failure Evidence + Retry Semantics.
- **M6-P4b:** Trusted Export Inline Cleanup.

The split is intentional R10 discipline: AI provider failure and export object-storage residue are separate failure surfaces, with separate tests, commits, rollback paths, and review criteria.

## Final裁决 Snapshot

| Question | Final裁决 |
|----------|-----------|
| Q1 failed AI provider attempts | B: persist failed `ai_calls` rows |
| Q2 AI call status model | A: minimal `COMPLETED` / `FAILED` constants |
| Q3 timeout config | C: provider-specific, limited to existing `openai-compatible` config |
| Q4 retry attempts | C: config-driven, default 3 |
| Q5 backoff | B: deterministic exponential backoff |
| Q6 retryable failures | B: retry only `AiProviderException.isRetryable()` |
| Q7 idempotency during retry | B: retry within one logical MISS, no per-retry idempotency re-check |
| Q8 retry metrics | C: miss stays once, add separate retry counter |
| Q9 export residue cleanup | B: inline cleanup of exact written object keys |
| Q10 export failure visibility | A: exception-only; no failed export-job persistence |
| S1 implementation shape | B: split P4a / P4b |

## Budget Files

- [m6p4a-scope-budget.md](m6p4a-scope-budget.md) — AI failure evidence, timeout, retry/backoff, retry metrics.
- [m6p4b-scope-budget.md](m6p4b-scope-budget.md) — Trusted Export inline cleanup.

## Non-Mirrored Decision

M6-P4 does **not** persist failed export jobs just because failed AI calls are persisted. That would be false symmetry:

- AI failure rows have current consumers across provenance, retry, cost, and metrics.
- Export failed jobs have no current synchronous API/UI consumer.
- Export failed-job persistence is deferred until async export/job化 exists.

## Cross-Phase Guardrails

- M6-P4a must preserve M6-P3b hit/miss semantics: one logical MISS per review request, retry attempts counted separately.
- M6-P4a must preserve M6-P1 Q6: AI review does not mutate `submission.status`.
- M6-P4a must preserve M6-P3a-2 A2 fallback and calculator-path tests.
- M6-P4b must not add OpenAPI, migrations, background scanners, or failed export-job persistence.

