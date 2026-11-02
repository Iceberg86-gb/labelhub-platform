# M6-P6b2 Scope Budget

## Theme

Page-Level UI Polish for the six locked defense paths.

M6-P6b1 landed the global UI primitives. M6-P6b2 applies those primitives to the evidence surfaces identified in the M6-P6a UI audit, without expanding product behavior or backend contracts.

## Phase Character

M6-P6b2 is an A-class UI polish phase with D-class defense-readiness positioning:

- It improves evidence visibility on already-rendered UI surfaces.
- It repairs obvious visual brokenness on the defense path.
- It makes the six demo paths feel like finished product surfaces.

M6-P6b2 is not a site redesign, IA rewrite, or evidence-feature phase.

## Strict Constraints

Allowed:

- Frontend implementation under `apps/web/src/` for the six locked P0 clusters only.
- Documentation under `docs/internal/`.
- Final screenshot archive updates under `docs/screenshots/m6p6-after-set/` after user capture.
- Final `humanpending.md` status update.

Forbidden:

- Any change under `services/`, `packages/contracts/`, or `db/migration/`.
- Any backend or frontend test file creation.
- Any new route, fetch, OpenAPI consumer, or API field consumption path.
- Any Semi Design global theme token change.
- Any P1 work without explicit post-P0 user adjudication.
- Any change to P6b1-frozen primitives: `<RoleBadge>`, typography variables, header subtitle CSS, or `.brand-subtitle`.
- Any `<TruncatedHash>` extension outside C1.
- Any page outside the six locked defense paths.

## Locked Defense Paths

| Path | Defense role |
|------|--------------|
| Owner schema designer + version history | Highlight 1: schema versioning and immutable facts |
| Labeler submission detail | Highlight 1 + 4: historical rendering and AI provenance |
| Reviewer queue | Highlight 2: Quality Ledger derived verdict |
| Trusted Export list + diff modal | Highlight 3: reproducible export evidence |
| AI review drawer | Highlight 4 + Signal 1 A: provenance, cost, idempotency hit |
| Owner setup 3-CTA guidance | M6-P2 product readiness path |

Out of scope: login, 404, user settings, general dashboard, demo data naming.

## P0 Cluster Map

Implementation order is locked:

| Cluster | Issues | Target surface | Estimate |
|---------|--------|----------------|----------|
| C1 | #7, #8, #10 | Schema version history drawer | ~185 lines |
| C2 | #11, #12, #15 | Labeler submission detail + AI provenance card | ~165 lines, pending #15 adjudication |
| C3 | #26, #27, #31, #32, #33 | Trusted Export list + diff modal | ~135 lines, pending #32 hash-value adjudication |
| C4 | #35, #36, #37, #38 | AI review drawer | ~105 lines |
| C5 | #17, #18 | Reviewer queue | ~45 lines |
| C6 | #40 | Owner setup CTA guidance | ~25 lines |

Estimated P6b2 total after removing #28 from P0: ~660 lines before adjudication buffers.

P6b1 frontend diff already consumed 182 changed lines. P0 cap is 900 changed frontend lines, leaving 718 lines for P6b2 P0.

## P1 Boundary

P1 is blocked by default.

Demoted issue #28 ("same hash" indicator on Export list) is P1. It is not included in C3 P0. P1 work can only begin after all six P0 clusters land, typecheck/build are green, cumulative diff is reported, and the user explicitly names the P1 issues to implement.

## E-Lite Boundary

Allowed:

- Improve the visual treatment of evidence already rendered.
- Replace existing short hash text with `<TruncatedHash>`.
- Rephrase existing visible labels for clarity.
- Add icons/tooltips that explain already-rendered evidence.

Forbidden by default:

- Rendering an API field that is returned but not currently shown.
- Fetching new data to support a polish surface.
- Adding a new endpoint consumer.
- Inferring hidden API semantics from untyped `provenance` objects.

## TruncatedHash Rule

`<TruncatedHash>` was scaffolded in M6-P6b1 and is integrated for the first time in C1.

- C1 may extend `<TruncatedHash>` by up to 20 lines if the first real integration exposes a generic API gap.
- After C1, `<TruncatedHash>` is frozen.
- C2, C3, and C4 must consume the existing API. If they need another prop or behavior, stop for user adjudication.

Pre-estimate finding: C3's three-hash summary currently exposes boolean match fields, not raw hash values. See `m6p6b2-pre-estimate.md` for the required adjudication before code.

## Stop Conditions

Stop and report if:

- Any cluster actual diff exceeds pre-estimate by 30% or more.
- Cumulative P6b1+P6b2 frontend diff reaches 700 lines before all six clusters complete.
- Cumulative frontend diff would exceed the 900-line P0 hard cap.
- A cluster needs a file outside the locked target paths.
- A cluster needs a new fetch, new API field, or new OpenAPI consumer.
- A cluster needs a Semi theme token change.
- C2/C3/C4 needs to extend `<TruncatedHash>`.
- The audit action cannot be implemented from current frontend data.
- `pnpm --filter @labelhub/web typecheck` or `pnpm --filter @labelhub/web build` fails after a cluster.

## Verification Gates

After every cluster:

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- Cumulative frontend diff check with `git diff a52f084..HEAD --shortstat -- apps/web/src/`

Final verification:

- frontend typecheck/build green
- backend full suite `mvn -pl services/api test` expected 390 tests, 0 failures, 0 errors, 78 skipped
- protected endpoint guard
- OpenAPI MD5 remains `c042f8bc62a15efd98bd01363b9e14ff`
- migration count remains 10
- full after-screenshot set archived after user capture

## Pre-Estimate Gate Findings

Two findings require user adjudication before code begins:

1. C2 #15 asks for a conditional historical-render banner comparing submission schema version with current schema version. The current `SubmissionRenderSchema` payload exposes the bound historical `schemaVersion` but not the current schema version. Implementing the exact condition would require a new fetch or new API field consumption, which is forbidden.
2. C3 #32 asks for three-hash card treatment with `<TruncatedHash>` in the cluster table, but `ExportSnapshotDiff.hashMatches` exposes booleans only (`fileHash`, `manifestHash`, `sourceStateHash`). The feasible P0 implementation is replacing `✓ ...` with explicit `✓ 一致` labels and using `<TruncatedHash>` for available manifest/file hashes only.

No code should be written until these are adjudicated.

## R10 Boundary

This pre-estimate commit is docs-only and reversible. P6b2 implementation begins only after user review of:

- target files
- line estimates
- data-shape blockers
- P0/P1 boundaries
