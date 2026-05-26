# M6-P5 Decision-Log And Humanpending Audit

Date: 2026-05-26

## Scope

This audit checks whether M6-P5 is closing a coherent engineering history rather than a pile of isolated fixes. The key question: can a reviewer trace every M6 decision from scope budget to implementation verification to humanpending state?

## Decision-Log Audit

### Command Evidence

```bash
rg -n "^## .*M6|^### .*M6|Final Decision|Implementation Verification|Status: draft|draft pending|pending user裁决|TODO|FIXME|@todo|false symmetry|Submission is an immutable answer fact" docs/internal/decision-log.md
rg -n "Status: draft|draft pending|pending user裁决|Draft pending|Draft Pending" docs/internal/decision-log.md docs/internal humanpending.md
rg -n "^## " docs/internal/decision-log.md
```

### Results

| Check | Result |
|-------|--------|
| M6 Phase 0.5 Final Decision present | Yes |
| M6 Phase 4.0 Final Decision present | Yes |
| M6 implementation sections present | P1, P2, P3a, P3a-2, P3b, P4a, P4b |
| `Status: draft` / `draft pending user裁决` markers | 0 |
| Philosophy statements preserved | 2: M6-P0.5 immutable answer fact; M6-P4.0 false symmetry / failure semantics |
| Strict-constraint exception tables with R10 paths | Present for P1, P3a-2, P3b, P4.0/P4a/P4b |

### M6 Decision-Log Sections

- `2026-05-25 M6 Phase 0.5 Submission Lifecycle Semantics Final Decision`
- `2026-05-25 M6 Phase 1 Implementation Verification`
- `2026-05-25 M6 Phase 2 Implementation Verification`
- `2026-05-25 M6 Phase 3a AI Token Usage Persistence`
- `2026-05-25 M6 Phase 3a-2 AI Cost Computation From Token Usage`
- `2026-05-26 M6 Phase 3b Idempotency Metrics Baseline`
- `2026-05-26 M6 Phase 4.0 Robustness Failure Semantics Final Decision`
- `2026-05-26 M6 Phase 4a AI Provider Failure Evidence + Retry Semantics`
- `2026-05-26 M6 Phase 4b Trusted Export Inline Cleanup`

### Draft / TODO Interpretation

No active M6 draft-pending markers remain.

The decision-log still contains one historical M1 line mentioning TODOs for M2/M4 publish guard follow-up:

> M1 publish state legality is handled by `TaskStateTransitions`; the publish business guard is intentionally relaxed to `quota_total > 0` and `deadline_at > now`, with dataset, schema version, and adjudication rule checks left as TODOs for M2/M4.

This is historical context, not an active M6 blocker. It predates the M6 final-decision sections and is preserved as engineering history rather than rewritten.

## Humanpending Audit

### Command Evidence

```bash
rg -n "^- \\[" humanpending.md
rg -c "^- \\[" humanpending.md
rg -c "resolved" humanpending.md
rg -c "ready" humanpending.md
rg -c "watch" humanpending.md
rg -c "deferred|Deferred|defer|deferred" humanpending.md
```

### Results

| Category | Count / State |
|----------|---------------|
| Bracketed entries | 118 |
| Entries containing `resolved` | 26 |
| Entries containing `ready` | 3 |
| Entries containing `watch` | 4 |
| Entries containing deferred/defer wording | 4 |
| M6-P0 through M6-P4b resolved entries | Present |
| M6-P5 ready entry | Present before final report |
| M6-P3c optional/ready state | Present |
| Metrics data accumulation watch | Present |
| Production actuator security watch | Present |
| False symmetry deferred entry | Present |

### M6 Humanpending State

- M6-P0, P0.5, P1, P2, P3a, P3a-2, P3b, P4.0, P4a, and P4b are all explicitly resolved.
- M6-P5 is ready and becomes resolved in the final report commit.
- M6-P3c remains optional/ready, not required before defense unless M6-P5 discovers a scale-evidence gap.
- Long-running watches remain intentionally visible:
  - 7-day metrics data accumulation.
  - Production actuator security review.
  - DeepSeek pricing refresh / v4-pro discount expiry.
  - Docker-backed integration tests in an environment with Docker.

## Scope-Budget Audit

### Command Evidence

```bash
find docs/internal -maxdepth 1 -name 'm6p*.md' -print
```

Result: 14 M6-related internal docs after adding M6-P5 files in progress.

Key scope-budget / research docs:

- `m6p0-smoke-audit-report.md`
- `m6p0.5-lifecycle-research.md`
- `m6p1-scope-budget.md`
- `m6p2-scope-budget.md`
- `m6p3a-scope-budget.md`
- `m6p3a2-scope-budget.md`
- `m6p3b-scope-budget.md`
- `m6p4-failure-research.md`
- `m6p4-scope-budget.md`
- `m6p4a-scope-budget.md`
- `m6p4b-scope-budget.md`
- `m6p5-scope-budget.md`
- `m6p5-smoke-evidence.md`
- `m6p5-4-light-evidence-revalidation.md`

## Cross-Reference Audit

| Check | State |
|-------|-------|
| Every M6 phase has decision-log coverage | Yes |
| Every M6 implementation phase has a scope-budget or research doc | Yes |
| Every strict-constraint exception has R10 wording | Yes |
| M6-P3a-2 interface evolution backfill is recorded | Yes, in decision-log and `m6p3a2-scope-budget.md` |
| M6-P4.0 false symmetry decision is recorded in decision-log and humanpending | Yes |
| D-口径 items are not hidden | Yes: Docker skips, screenshot/tool limitation, metrics accumulation, production actuator security |

## Discovered Gaps

| Gap | Classification | Handling |
|-----|----------------|----------|
| Fresh browser screenshot capture unavailable in this tool context | D-口径 | Recorded in `m6p5-smoke-evidence.md` and screenshot target index. |
| M6-P3c large-task performance baseline not run | Optional deferred | No blocking scale-evidence gap discovered so far; keep optional. |
| 7-day idempotency hit ratio not yet mature | Watch | Endpoint exists; stable claims require real traffic over time. |
| Docker-backed Testcontainers skipped locally when Docker is unavailable | D-口径 | Keep humanpending Docker watch; command-backed non-Docker suite remains green. |
| Historical M1 TODO wording remains in decision-log | Historical context | Not rewritten; not an active M6 blocker. |

## Audit Summary

M6-P5 found no active M6 draft-pending decision and no missing M6 scope/decision/humanpending link. Remaining items are explicitly classified as D-口径, watch, optional, or historical context.

