# M6-P5.1 Scope Budget

## Theme

Runtime Startup Hotfix.

M6-P5.1 is a narrow hotfix phase opened after M6-P6 screenshot preflight attempted to start the API and surfaced a Spring Boot runtime context startup failure:

```text
Error creating bean with name 'aiRetryPolicy': Failed to instantiate [AiRetryPolicy]: No default constructor found
```

M6-P5 test/build evidence remains valid for the scope it covered, but it did not include a full Spring Boot context startup guardrail. M6-P5.1 fixes the runtime startup failure and adds that guardrail.

## Scope

| Item | Type | Budget |
|------|------|--------|
| Runtime startup investigation | New docs file | ~80-140 lines |
| `ApplicationContextStartupTest` | New test | ~15-30 lines |
| Root-cause wiring fix | Minimal production fix | ~1-20 lines |
| M6-P5 report amendment | Docs update | ~20-40 lines |
| Decision-log + humanpending update | Docs update | ~30-60 lines |

Functional code budget: **0-20 lines**. Test budget: **15-30 lines**. Documentation budget: **130-240 lines**.

## Strict Constraints

Allowed:

- Reproduce and root-cause the `spring-boot:run` startup failure.
- Apply the minimum-surface wiring fix targeting the actual root cause.
- Add `ApplicationContextStartupTest` as the 12th cross-phase guardrail.
- If the new full-context test exposes an additional runtime wiring issue, apply the smallest possible fix and record it as a separate sub-item.
- Amend `m6p5-final-regression-report.md` transparently with the M6-P5.1 runtime startup gap and fix.
- Add a short M6-P5.1 decision-log section.
- Update `humanpending.md` to mark P5.1 resolved and unblock M6-P6.

Forbidden:

- UI polish. M6-P6 remains paused until M6-P5.1 closes.
- OpenAPI changes.
- Migration changes.
- AI business-logic changes beyond what is required for context startup.
- Unrelated refactors, formatting sweeps, import-order churn, or "while here" cleanup.
- Applying the suspected `@Autowired` fix before reproducing the failure and reading the full stack trace.

## Investigation Discipline

The headline error is not enough. M6-P5.1 must capture the full `Caused by` chain and identify the actual root cause before implementation.

Known hypotheses:

- `AiRetryPolicy` has multiple constructors and no explicit Spring constructor selection.
- `OpenAiCompatibleProperties` has record constructor-binding ambiguity.
- Another downstream bean fails and surfaces as a misleading `AiRetryPolicy` creation error.

If the root cause is outside those hypotheses, stop and report before widening scope.

## Guardrail Requirement

M6-P5.1 must add a real full-context startup guardrail:

```java
@SpringBootTest
class ApplicationContextStartupTest {
    @Test
    void context_loads() {
        // Empty body: failure to load context fails the test
    }
}
```

The test must be committed before the production fix and must fail with the same root cause before the fix. It must not mock `AiRetryPolicy`, `OpenAiCompatibleProperties`, or AI provider beans.

## Verification

Required before closure:

- `mvn -pl services/api test` passes with `ApplicationContextStartupTest` included.
- Backend test count is `390` or higher, with `0` failures and `0` errors.
- `mvn -pl services/api spring-boot:run` starts cleanly and logs `Started ApiApplication`.
- OpenAPI remains `0.10.0`.
- Migration count remains `10`.
- Git status is clean after commits.

If `spring-boot:run` exposes a second startup bug after the first fix, stop and report. Do not auto-expand P5.1 without user裁决.

## Commit Granularity

1. `docs: scope M6-P5.1 runtime startup hotfix`
2. `docs: M6-P5.1 runtime startup investigation`
3. `test: add ApplicationContextStartupTest as runtime guardrail`
4. `fix: <root-cause-specific description>`
5. `docs: M6-P5.1 verification + decision-log + final report amendment`

One additional `fix:` commit is allowed only if the investigation proves more than one independent root cause. Each fix must have a separate decision-log sub-item.
