# M6-P3a Scope Budget

## Theme

AI token usage persistence: capture real provider usage tokens into `ai_calls`, leaving cost computation as a separate follow-up phase.

## Scope

Confirmed裁决: S1=C / S2=A / S3=A, with cost computation deferred as the C candidate.

| Item | Type | Budget |
|------|------|--------|
| V10 migration: 4 nullable token columns on `ai_calls` | Additive migration | ~10 SQL |
| `AiCallUsage` record + provider parsing | Add new file + extend provider | ~50 lines |
| Persist tokens to `ai_calls` row | Extend existing AiReviewService path | ~25 lines |
| Add `usage` field to AI review response DTO | OpenAPI minor bump 0.10.0 | ~30 yaml |
| Frontend AI Drawer: show tokens read-only | UI display only | ~30 lines |
| Regression tests | Add tests | ~200 lines |
| Decision-log + token persistence methodology | Add docs | ~100 lines |

Total functional estimate: **140-180 lines**. Tests and docs are expected to be larger than functional code.

## Cost Computation Deferral

Pricing config and cost-from-usage computation are deferred:

- The token usage persistence layer has standalone value: it records provider-reported prompt, completion, total, and cache-hit tokens.
- China-region billing/pricing source still needs confirmation before converting tokens into money.
- `cost_decimal` keeps the existing M3 fixed-estimate path. M6-P3a must not change it.
- M6-P3a-2 will add `AiCallCostCalculator` only after pricing source and currency semantics are locked.

## Anti-Scope-Creep Boundaries

M6-P3a is intentionally narrow:

- No `AiCallCostCalculator`.
- No pricing config in `application.yml`.
- No metrics endpoint.
- No idempotency hit ratio counter.
- No retry/backoff behavior.
- No `cost_decimal` behavior change.

Stop conditions:

- If usage parsing requires changing `AiCallResult` signature, push back and use a wrapper record.
- If persistence requires changing the `cost_decimal` path, push back and defer that to M6-P3a-2.
- If OpenAPI bump requires removing required fields, push back.
- If total functional code exceeds 220 lines, split P3a-1 / P3a-2.

## Strict-Constraint Variant a'

Engineering-enhancement variant:

- ADD: new file, V10 nullable columns, compatible OpenAPI field, new tests.
- ALLOW: `AiProvider` default method and `AiReviewService.review` internal call-site extension for usage collection.
- FORBID: changing existing method signatures, existing OpenAPI field semantics, existing column constraints, or existing `cost_decimal` behavior.

## V10 Migration SQL

```sql
ALTER TABLE ai_calls
    ADD COLUMN prompt_tokens INT NULL AFTER cost_decimal,
    ADD COLUMN completion_tokens INT NULL AFTER prompt_tokens,
    ADD COLUMN total_tokens INT NULL AFTER completion_tokens,
    ADD COLUMN cache_hit_tokens INT NULL AFTER total_tokens;
```

Semantics:

- `NULL` = call predates token tracking or provider omitted usage.
- `0` = provider reported a real zero-token count.
- No default values, preserving the distinction between "untracked" and "zero".

## M1 / M2 Decisions

M1: `AiReviewService.review` will internally switch from `aiProvider.invoke(...)` to `aiProvider.invokeWithUsage(...)`. This is a data-collection extension; the public service signature and API response shape remain compatible.

M2: usage parsing is defensive. DeepSeek official Context Caching docs use `prompt_cache_hit_tokens`, while some OpenAI-compatible providers use `cached_tokens`. The parser will prefer `prompt_cache_hit_tokens` and fall back to `cached_tokens`.

## Commit Granularity

1. docs: scope M6-P3a AI token usage persistence
2. test: add token usage parsing and persistence regression tests
3. feat: add V10 token columns migration
4. feat: parse provider usage in OpenAI-compatible provider
5. feat: persist tokens to ai_calls and expose in API response
6. docs: record M6-P3a verification and pricing-deferral rationale
