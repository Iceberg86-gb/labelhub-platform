# M6-P6c Verification

## 1. Status

M6-P6c P1 polish closed on 2026-05-26. M6-P6 is now complete across P6a, P6b1, P6b2, and P6c.

- Baseline: `282c297` (`docs: M6-P6b2 verification and M6-P6 phase closure`)
- Final code head: `9cdff86` (`feat: indicate matching manifest hash on export list`)
- Final docs head: this verification commit
- Phase character: optional P1 polish subset, not a new defense-readiness baseline

## 2. Commit Map

| Commit | Cluster | Purpose |
|--------|---------|---------|
| `18bb903` | docs | Scope + pre-estimate |
| `20ada51` | D1 | Export diff modal width `720` -> `840` |
| `f5cc5a2` | D2 | Reviewer queue filter Select `size="small"` |
| `b0567fd` | D3 | Reviewer approve button success-green via `type="tertiary" + className` |
| `00a4185` | D4 | Verdict source emphasis with Tooltip + Text + IconInfoCircle |
| `9cdff86` | D5 | Export list same-hash indicator, page-local |

## 3. Adjudications Applied

| Topic | Decision |
|-------|----------|
| D1 `#34` modal width | Use `width={840}`. |
| D3 `#23` approve button | Use local CSS class `.review-approve-button`; implementation path was `type="tertiary" + className`, no fallback required. |
| D4 `#25` verdict source | Use Tooltip + emphasized `Typography.Text` + `IconInfoCircle`; no Tag pill; mirror P6b2 `.reviewer-ledger-subtitle` pattern. |
| D5 `#28` same-hash tag text | Use `✓ 同 hash`; optional Tooltip skipped to avoid visual noise. |

Mid-cluster stop acceptances:

- D4 landed at 28 changed lines against a revised 10-12 line estimate. User accepted because full mirror-pattern implementation was underestimated, not because the scope expanded.
- D5 landed at 31 changed lines against a 14-19 line estimate. User accepted because the `useMemo` Map, JSX wrapper, and two inline-flex/success rules were all part of the locked implementation.

## 4. P1 Closure Table

| Audit ID | Commit | After screenshot | Status |
|----------|--------|------------------|--------|
| `#16` filter dropdown / refresh button style consistency | `f5cc5a2` | `01-reviewer-queue-after.png` | Closed |
| `#23` approve button success-green semantic | `b0567fd` | `02-reviewer-detail-after.png` | Closed |
| `#25` verdict source provenance line emphasis | `00a4185` | `02-reviewer-detail-after.png` | Closed |
| `#28` export list same-hash indicator | `9cdff86` | `03-export-snapshot-list-after.png` | Closed |
| `#34` diff modal width | `20ada51` | `04-export-diff-modal-after.png` | Closed |

## 5. Success-Green Color Consistency Audit

Verified with `grep -n "#16a34a" apps/web/src/app/styles.css`.

| Line | Selector / declaration | Phase |
|------|------------------------|-------|
| 670 | `.timeline-dot-published background` | Pre-existing M2 timeline dot, not a P6 success surface |
| 1440 | `.review-approve-button border-color` | M6-P6c D3 |
| 1441 | `.review-approve-button background` | M6-P6c D3 |
| 1657 | `.export-same-hash-tag background` | M6-P6c D5 |
| 1725 | `.diff-status-tag--match background` | M6-P6b2 P0 |

Three P6 success-evidence surfaces share the literal `#16a34a`: reviewer approve, export same-hash, and export diff match status.

## 6. Per-Cluster Pre-Estimate vs Actual

Actual counts use insertion + deletion shortstat.

| Cluster | Pre-estimate | Actual | Delta | Stop triggered |
|---------|--------------|--------|-------|----------------|
| D1 `#34` | 1 | 2 | +100% by shortstat, one-line prop replacement | No |
| D2 `#16` | 1-4 | 1 | Within range | No |
| D3 `#23` | 6-10 | 13 | +30% | No |
| D4 `#25` | 10-12 revised | 28 | +133% vs high estimate | Yes, user-adjudicated |
| D5 `#28` | 14-19 | 31 | +63% vs high estimate | Yes, user-adjudicated |
| **Total** | 32-46 | 75 | Within 100-line hard cap | No phase-level stop |

P6c code diff: `5 files changed, 65 insertions(+), 10 deletions(-)` = 75 changed frontend lines.

## 7. Stylistic Guidance Adherence

- Guidance 1: D4 mirrors `.reviewer-ledger-subtitle`. Confirmed: `.verdict-source-line` uses the same inline-flex family, alignment, `6px` gap, `15px` font size, `650` font weight, and blue `.semi-icon` treatment.
- Guidance 2: D3 and D5 share success-green with P6b2 `.diff-status-tag--match`. Confirmed by the `#16a34a` grep audit above.

## 8. Page-Local D-口径 for D5 `#28`

M6-P6c `#28` same-hash indicator is a page-local hint based on `manifestHash` values already rendered in the current list query result. It does not fetch additional snapshots across pages, and it does not substitute for the diff modal authoritative comparison. Snapshots on different list pages with identical manifest hashes will not be cross-marked by this indicator.

## 9. P1 Items Not Entered

P6c P1 entry was limited to five issues: `#16`, `#23`, `#25`, `#28`, and `#34`.

The following P1 items remain optional future work:

- `#13` submission detail tag color semantics
- `#21` reviewer ledger timeline restructure
- `#29` / `#30` export table column / disabled-state polish
- `#39` AI drawer field feedback cards
- `#42` / `#44` owner setup spacing / empty-state copy

## 10. Visual Verification

Four P6c after-screenshots are archived at `docs/screenshots/m6p6c-after-set/`. The auditor signed off on all four:

- `01-reviewer-queue-after.png`: D2 `#16`
- `02-reviewer-detail-after.png`: D3 `#23` and D4 `#25`
- `03-export-snapshot-list-after.png`: D5 `#28`
- `04-export-diff-modal-after.png`: D1 `#34`

## 11. Final Verification Commands

| Command | Result |
|---------|--------|
| `pnpm --filter @labelhub/web typecheck` | Exit 0 |
| `pnpm --filter @labelhub/web build` | Exit 0; Vite large-chunk warning remains non-fatal |
| `bash scripts/check-protected-endpoints.sh` | `Protected OpenAPI endpoints are present.` |
| `mvn -pl services/api test` | First sandbox run failed with socket D-口径; escalated rerun passed `390` tests, `0` failures, `0` errors, `78` skipped |
| `md5sum packages/contracts/openapi/labelhub.yaml` | `c042f8bc62a15efd98bd01363b9e14ff` |
| `find services/api/src/main/resources/db/migration -maxdepth 1 -name 'V*.sql' \| wc -l` | `10` |

Backend D-口径: the first non-escalated Maven run failed with `SocketException: Operation not permitted` for MySQL / provider socket access. The same command passed after sandbox escalation, matching prior M6 socket D-口径 handling.

## 12. Cumulative M6-P6 Frontend Diff

Command:

```bash
git diff a52f084..HEAD --shortstat -- apps/web/src/
```

Result:

```text
20 files changed, 578 insertions(+), 68 deletions(-)
```

Cumulative M6-P6 frontend code changed lines: `646`, below the 900-line cap.

## 13. No-Touch Confirmation

Command:

```bash
git diff 282c297..HEAD --name-only -- services/ packages/contracts/ db/migration/ services/api/src/main/resources/db/migration/
```

Result: no output.

P6c made zero changes under backend, OpenAPI contracts, or migrations.

## 14. M6-P6 Phase Final State

M6-P6 closes with four layers:

- P6a: locked screenshot audit and polish scope
- P6b1: global UI primitives (`RoleBadge`, typography, `TruncatedHash`, logo subtitle)
- P6b2: page-level P0 evidence polish across the six defense paths
- P6c: five-item P1 polish subset (`#16`, `#23`, `#25`, `#28`, `#34`)

This sits on top of the M6-P5/P5.1 defense-readiness baseline as the complete UI experience polish layer.
