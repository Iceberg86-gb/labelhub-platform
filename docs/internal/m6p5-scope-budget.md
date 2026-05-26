# M6-P5 Scope Budget

## Theme

Final Regression + Operational Verification.

M6-P5 is a verification phase. It re-runs the evidence paths built across M2-M6, checks that the four defense highlights still hold after M6-P4b, audits the decision-log and humanpending history, and produces the final defense-readiness report.

## What M6-P5 Is

- Re-run backend, frontend, protected-route, OpenAPI, migration, and sensitive-scan verification.
- Re-validate the four highlight evidence chains with command-backed evidence where possible.
- Confirm M6 Signal 1 A/B class closure:
  - A class: token persistence, USD cost computation, idempotency metrics.
  - B class: AI provider failure evidence/retry and Trusted Export cleanup.
- Audit decision-log entries for final decisions, implementation verification sections, and remaining draft markers.
- Audit humanpending entries for clear resolved / ready / pending / watch / deferred state.
- Capture screenshot status and document any D-口径 limitation rather than overstating evidence.
- Produce final regression and defense-readiness docs.

## What M6-P5 Is Not

- No new product feature.
- No OpenAPI bump.
- No V11 migration.
- No new status enum or schema contract.
- No large-task performance benchmark unless P5 uncovers a specific scale-evidence gap.
- No retroactive philosophy rewrite.

## Strict Constraints

Allowed:

- Documentation updates.
- Screenshot index / evidence index updates.
- Verification command output summarized in docs.
- Minor documentation corrections discovered by audit.

Forbidden:

- Production code changes.
- OpenAPI fields or semantic changes.
- Database migrations.
- New backend/frontend behavior.

If a P5 smoke uncovers a P0/P1 bug, stop and scope a dedicated follow-up instead of silently fixing it inside P5. If functional code changes exceed 0 lines, the exception must be recorded in decision-log with an R10 path.

## Cross-Phase Coverage

| Highlight | M6-P5 verification target |
|-----------|---------------------------|
| Highlight 1: Schema versioning + immutable facts | Verify schema-version and submission renderer guardrails remain covered; V9/V10 did not touch schema immutability. |
| Highlight 2: Quality Ledger + verdict derivation | Verify ledger/verdict tests and M6-P1 submitted lifecycle guardrails still pass. |
| Highlight 3: Trusted Export reproducibility | Verify export hash/reproducibility tests still pass and M6-P4b cleanup preserves successful export behavior. |
| Highlight 4: AI provenance + provider evidence | Verify token persistence, cost calculator, idempotency metrics, retry evidence, and failed-attempt rows still pass. |

## Verification Commands

Required fresh commands:

- `mvn -pl services/api test`
- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- `bash scripts/check-protected-endpoints.sh`
- sensitive scan for API-key material
- migration count check
- OpenAPI version check
- git cleanliness / generated artifact cleanup

Known D-口径:

- Docker-backed Testcontainers are expected to skip in this local environment when Docker is unavailable.
- Browser screenshot capture depends on browser automation availability. If unavailable, record screenshot targets and carry the M6-P2 browser screenshot watch into final readiness notes.

## Deliverables

- `docs/internal/m6p5-smoke-evidence.md`
- `docs/internal/m6p5-4-light-evidence-revalidation.md`
- `docs/internal/m6p5-audit.md`
- `docs/internal/m6p5-final-regression-report.md`
- `docs/screenshots/m6p5-smoke-set/INDEX.md`

## Commit Granularity

1. `docs: scope M6-P5 final regression`
2. `docs: M6-P5 regression smoke evidence capture`
3. `docs: M6-P5 4-light evidence re-verification`
4. `docs: M6-P5 decision-log and humanpending audit`
5. `docs: M6-P5 final regression report`
