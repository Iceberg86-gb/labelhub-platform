# M6-P3a-2 Scope Budget

## Theme

AI cost computation from M6-P3a token persistence. M6-P3a-2 replaces the fixed `AI_COST_PER_CALL` estimate only when provider token usage is complete enough to compute a real USD cost.

## User Decision

- **A**: Continue to cost computation now. Token persistence is an incomplete cost/performance baseline without the cost layer.
- **USD**: DeepSeek's official English pricing page is the evidence source. CNY v4-flash pricing is not stably documented.
- **A2 fallback**: Compute real cost only when both `promptTokens` and `completionTokens` are present. `cacheHitTokens == null` means zero cache hits. `totalTokens` is display/audit data, not a compute prerequisite.
- **R2 rounding**: Compute internally with high-precision `BigDecimal`; write `cost_decimal` using `setScale(6, HALF_UP)` to preserve the existing `DECIMAL(12,6)` schema.

## Scope

| Item | Type | Budget |
|------|------|--------|
| `application.yml` pricing config in USD per 1M tokens | Additive config | ~25 lines |
| `AiPricingProperties` Spring binding | New file | ~50 lines |
| `AiCallCostCalculator` with A2 fallback and R2 rounding | New file | ~70 lines |
| `AiReviewService.review` cost source switch | Strict-constraint exception | ~5 lines |
| Regression tests | Add/update tests | ~280 lines |
| Decision-log and humanpending updates | Docs | ~140 lines |

Functional estimate: **140-200 lines**. Test and documentation estimate: ~420 lines.

## Pricing Evidence Source

DeepSeek English pricing doc verified 2026-05-25:

- URL: `https://api-docs.deepseek.com/quick_start/pricing`
- `deepseek-v4-flash`
  - 1M input tokens, cache hit: `$0.0028`
  - 1M input tokens, cache miss: `$0.14`
  - 1M output tokens: `$0.28`
- `deepseek-v4-pro`
  - 1M input tokens, cache hit: `$0.003625` with 75% off noted
  - 1M input tokens, cache miss: `$0.435` with 75% off noted
  - 1M output tokens: `$0.87` with 75% off noted
  - DeepSeek notes the v4-pro pricing adjustment after the 75% discount promotion ends on `2026-05-31 15:59 UTC`.

The configuration stores the currently verified official prices. If DeepSeek changes pricing, update the config and refresh this evidence date.

## A2 Fallback Decision Table

| promptTokens | completionTokens | cacheHitTokens | Action |
|---|---|---|---|
| present | present | present | compute real USD cost |
| present | present | null | compute real USD cost with zero cache hits |
| present | null | any | fixed estimate fallback |
| null | present | any | fixed estimate fallback |
| null | null | any | fixed estimate fallback |
| usage is null | n/a | n/a | fixed estimate fallback |
| model pricing missing | any | any | fixed estimate fallback |

This avoids partial-data computation that would understate cost by calculating input while missing output.

## R2 Rounding Strategy

Internal computation keeps more precision than the database column:

```java
BigDecimal cacheHitCost = cacheHitTokens * cacheHitRatePer1m / 1_000_000;
BigDecimal cacheMissCost = cacheMissTokens * cacheMissRatePer1m / 1_000_000;
BigDecimal outputCost = completionTokens * outputRatePer1m / 1_000_000;
BigDecimal dbCost = totalCost.setScale(6, RoundingMode.HALF_UP);
```

Known precision limitation: small cache-hit amounts such as `30 * $0.0028 / 1_000_000 = $0.000000084` round to `0.000000` at DB precision. This is an accepted tradeoff. R3 (`DECIMAL(18,10)` via V11) is rejected for M6-P3a-2 to preserve strict-constraint a'.

## Anti-Scope-Creep Boundaries

M6-P3a-2 intentionally does not:

- Add a V11 migration or alter `cost_decimal`.
- Change V10 token columns.
- Change `AiCallResult` or `AiCallUsage` signatures.
- Add or remove OpenAPI fields.
- Add CNY pricing.
- Fetch pricing dynamically at runtime.
- Add cost alerts, dashboards, metrics endpoints, or hit-ratio counters.

Stop conditions:

- If R2 precision loss proves unacceptable, stop and reopen the R3 discussion instead of auto-implementing V11.
- If pricing config binding requires breaking an existing Spring property API, stop and rescope.
- If functional code exceeds 240 lines, split M6-P3a-2 into smaller phases.

## Strict-Constraint Exception

| Exception | Location | Justification | R10 path |
|-----------|----------|---------------|----------|
| Switch `cost_decimal` write source | `AiReviewService.review()` | M3 fixed estimate was a placeholder; M6-P3a now captures provider tokens, so M6-P3a-2 can compute real cost when prompt and completion tokens are present | Revert the dedicated cost-source switch commit to restore `result.cost()`; pricing config and calculator remain additive and harmless |
| Add `modelName()` to `AiProvider` | `AiProvider.java`, `MockAiProvider.java`, `OpenAiCompatibleProvider.java` | Additive interface evolution mirroring the existing `providerName()` self-describing pattern; both existing providers were updated synchronously, so no breaking change occurred inside LabelHub | Revert the provider-usage/cost commits that introduced the method; both implementations safely lose their `modelName()` overrides |

The `cost_decimal` source switch is the only existing-behavior change in M6-P3a-2. The `modelName()` row is an additive interface evolution, recorded here so future reviews can see every non-file-local surface change.

## Commit Granularity

1. `docs: scope M6-P3a-2 cost computation from token usage`
2. `test: add cost computation regression tests`
3. `feat: add AI pricing properties and configuration`
4. `feat: add AiCallCostCalculator with A2 fallback and R2 rounding`
5. `fix: switch ai_calls cost_decimal source from fixed estimate to calculator`
6. `docs: record M6-P3a-2 verification and pricing evidence`
