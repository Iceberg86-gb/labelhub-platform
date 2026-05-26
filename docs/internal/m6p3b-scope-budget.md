# M6-P3b Scope Budget

## Theme

Idempotency hit/miss/mismatch metrics baseline. M6-P3b closes the cost/performance observability loop opened by M6-P3a and M6-P3a-2:

```text
saved_cost ~= hit_count * average_real_invocation_cost
```

M6-P3a stores token usage. M6-P3a-2 computes real USD cost when usage is complete enough. M6-P3b makes idempotency behavior observable so saved cost can be measured instead of guessed.

## User Decision

- **A**: M6-P3b Idempotency / Metrics Baseline.
- Rationale: metrics must be wired early because hit-ratio data accumulates over time. Code creates the measurement entry point; useful 7-day data arrives only after deployment or sustained local smoke.
- Push-back accepted: M6-P3b starts by backfilling the M6-P3a-2 `AiProvider.modelName()` interface-evolution record.

## Scope

| Item | Type | Budget |
|------|------|--------|
| Backfill M6-P3a-2 `AiProvider.modelName()` transparency record | Docs | ~30 lines |
| `pom.xml`: add `micrometer-registry-prometheus` | Additive dependency | ~5 lines |
| `application.yml`: expose `metrics` and `prometheus` actuator endpoints | Config | ~10 lines |
| `AiIdempotencyMetrics` component | New file | ~50 lines |
| `AiReviewService.review`: increment hit/miss/mismatch counters at existing branch points | Observability-only instrumentation | ~10 lines |
| Regression tests | Add tests | ~180 lines |
| Decision-log and humanpending updates | Docs | ~140 lines |

Functional estimate: **75-110 lines**. Test and documentation estimate: ~320 lines.

## Counter Design

Three counters under the `labelhub.ai.idempotency` namespace:

- `labelhub.ai.idempotency.hit` — existing `ai_call` found and `inputHash` matches.
- `labelhub.ai.idempotency.miss` — no existing `ai_call` found, so a new provider invocation will happen.
- `labelhub.ai.idempotency.mismatch` — existing `ai_call` found but `inputHash` differs. This is a data-integrity warning, not a normal miss.

All counters carry a `provider` tag, such as `mock` or `deepseek`, so provider-specific hit ratios can be compared.

Prometheus exposes Micrometer dots as underscores:

```text
labelhub_ai_idempotency_hit_total
labelhub_ai_idempotency_miss_total
labelhub_ai_idempotency_mismatch_total
```

## Derived Metrics

Hit ratio:

```promql
sum(rate(labelhub_ai_idempotency_hit_total[5m]))
/
(
  sum(rate(labelhub_ai_idempotency_hit_total[5m]))
  + sum(rate(labelhub_ai_idempotency_miss_total[5m]))
)
```

Saved-cost estimate:

```text
saved_cost ~= hit_count * average_real_invocation_cost
```

`average_real_invocation_cost` comes from M6-P3a-2 `ai_calls.cost_decimal` rows for actual provider calls. Mismatch is excluded from hit ratio because it represents integrity drift, not cache effectiveness.

## Anti-Scope-Creep Boundaries

M6-P3b intentionally does **not**:

- Add latency timers.
- Add provider error counters.
- Add cost histograms.
- Add a Grafana dashboard or alerting.
- Add retry/backoff behavior or retry metrics.
- Persist metrics to MySQL.
- Change `AiCallMapper.selectByIdempotencyKey`.
- Change idempotency hit/miss/mismatch branch logic.
- Change OpenAPI. Metrics are exposed through Actuator, not LabelHub's business API.

Stop conditions:

- If `micrometer-registry-prometheus` conflicts with the Spring Boot dependency BOM, stop and rescope.
- If Actuator endpoint exposure requires changing security behavior, record it as a strict-constraint exception before implementation.
- If counter instrumentation requires changing idempotency branch logic, stop and redesign as wrapper/aspect instrumentation.
- If functional code exceeds 150 lines, split M6-P3b into smaller phases.

## Security Consideration

M6-P3b exposes `/actuator/prometheus` for development observability. LabelHub is still a local engineering training project, not a production deployment.

If `SecurityConfig` must be changed to permit Actuator scraping, that is an existing security-pipeline behavior change and must be logged as a strict-constraint exception. If no security change is required, decision-log should explicitly say so.

Production posture is deferred: a real deployment would require an explicit `/actuator/**` security review.

## Strict-Constraint Exceptions

Expected: **none**.

M6-P3b should be purely observational. Counter increments must not influence control flow, persistence decisions, response shape, pricing, token persistence, or Quality Ledger behavior.

If `SecurityConfig` needs an Actuator exception, this table must be updated before the implementation commit:

| Exception | Location | Justification | R10 path |
|-----------|----------|---------------|----------|
| _None expected_ | _n/a_ | _n/a_ | _n/a_ |

## Commit Granularity

1. `docs: backfill M6-P3a-2 AiProvider interface evolution record`
2. `docs: scope M6-P3b idempotency metrics baseline`
3. `test: add idempotency metrics regression tests`
4. `feat: add micrometer prometheus dependency and actuator exposure`
5. `feat: add AiIdempotencyMetrics component`
6. `feat: instrument AiReviewService hit/miss/mismatch counters`
7. `docs: record M6-P3b verification and metrics methodology`
