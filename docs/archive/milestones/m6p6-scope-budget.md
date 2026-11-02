# M6-P6 Scope Budget

## Theme

UI Experience Polish for the defense demo path.

M6-P6 is scoped as a frontend experience polish sequence after M6-P5.1 closed
the runtime startup gap. It is intentionally split:

- **M6-P6a**: UI audit and polish plan only. No production code.
- **M6-P6b**: implementation of the user-adjudicated polish list.

This split is a strict R10 boundary. P6a locks what P6b may touch; P6b may not
invent new polish work outside the locked audit list.

## Phase Character

Decision: **A + D**.

- **A**: pure visual/experience polish.
- **D**: targeted defense-readiness improvement for the UI/UX scoring surface.

M6-P6 is not a feature phase. It does not add backend behavior, data model
fields, OpenAPI fields, or new user flows.

## Locked Defense Paths

P6 polish is limited to the six defense demo paths that support the four
LabelHub highlights and M6 setup flow:

| Path | Defense purpose |
|------|-----------------|
| Owner schema designer | Highlight 1: schema versioning and immutable facts |
| Labeler submission detail | Highlight 1: historical schema render and answer fact |
| Reviewer queue + ledger | Highlight 2: append-only Quality Ledger and derived verdict |
| Trusted Export + diff modal | Highlight 3: reproducible export evidence |
| AI review drawer | Highlight 4 + Signal 1A: provenance, cost, idempotency |
| Owner setup 3-CTA | M6-P2 setup guidance and task readiness |

Explicitly out of scope:

- Login and auth screens
- 404 or error pages
- User settings
- General home/dashboard polish outside the six paths
- Demo data renaming or seed-data cleanup

## Strict Constraints

### Allowed in M6-P6a

- Add `docs/internal/m6p6-scope-budget.md`.
- Add `docs/internal/m6p6-ui-audit.md`.
- Add `docs/screenshots/m6p6-before-set/INDEX.md`.
- Track the already-captured P6 before screenshots.
- Update `humanpending.md` for M6-P6a/P6b state.

### Allowed in M6-P6b

- Frontend-only polish under `apps/web/`.
- Documentation updates for verification and before/after screenshot evidence.
- Component/page-level style overrides tied to locked audit issues.

### Forbidden

- Any backend production code change.
- Any OpenAPI change.
- Any migration.
- Any new backend or frontend test file unless the user re-adjudicates scope.
- Any new route.
- Any new API field consumption path.
- Any Semi Design theme token change.
- Any unlisted "while here" polish.
- Any component scaffold during P6a.

## E-lite Boundary

P6 may make already-rendered evidence more visible.

Allowed:

- Improve the visual treatment of evidence already rendered in the current UI.
- Reuse existing on-screen values such as hashes, cost, latency, verdict,
  schema version, idempotency/cache state, and export match status.

Forbidden by default:

- Rendering an API field that is returned but not currently shown.
- Adding a new fetch, endpoint, query parameter, or API consumer path.
- Adding a new UI panel for backend-only evidence.

If P6b discovers that an audit issue requires new data consumption, it must
stop and ask for re-adjudication.

## Style Constraints

- Do not change Semi Design global theme tokens.
- Prefer component/page-level styles or existing local style files.
- Do not add new CSS files unless there is no existing style home; if needed,
  record the reason in P6b verification.
- Every P6b code change must map to a locked audit issue ID.
- No all-site aesthetic rewrite. P6b fixes evidence visibility and obvious
  brokenness, not general taste.

## Line Budgets

| Scope | Hard budget |
|-------|-------------|
| P0 implementation | 900 frontend diff lines |
| P0 + P1 total | 1500 frontend diff lines |
| P6a production code | 0 lines |

If P0 exceeds 900 lines, P6b stops and reports for re-scope before continuing.
If P0 closes under budget, selected P1 items may enter while staying below the
1500-line total cap.

## P0 Implementation Order

P6b must implement P0 in dependency order:

1. Header/logo, role badge, and typography.
2. Truncated hash component and hash replacements.
3. Schema version history and historical render banner.
4. AI provenance surfaces in submission detail and drawer.
5. Trusted Export list and diff modal.
6. Reviewer queue/detail targeted fixes.
7. Owner setup CTA lock state.

P1 work may start only after P0 is complete and verified under budget.

## Verification Gates for P6b

P6b must pass:

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- Protected endpoint guard
- Backend full suite: 390 tests + 78 skipped (or documented D-口径 delta)
- OpenAPI version remains `0.10.0`
- Migration count remains `10`
- Before/after screenshot comparison for the six locked defense paths

If browser automation or screenshot capture is blocked, record the D-口径 and
do not claim visual verification beyond what was actually captured.

## Before Screenshot D-口径

Captured at 1440x900 logical viewport (Retina screenshots are 2880x1800).
Ten before screenshots are archived under
`docs/screenshots/m6p6-before-set/`.

Unavailable states are documented rather than fabricated:

| Missing screenshot | Reason |
|--------------------|--------|
| `01-owner-schema-empty.png` | No empty schema designer state in current dev data |
| `08-reviewer-queue-empty.png` | Reviewer queue currently has submitted items |
| `11-ai-drawer-first-call.png` | Existing submission already has AI result; drawer opens as idempotency hit |
| `13-ai-drawer-failure.png` | Provider failure state was optional and not forced |
| `15-owner-setup-mid-step.png` | No real mid-step setup state was available |

Audit correction D-口径:

- Reviewer detail issue `#22` was removed after re-audit. The words
  "提示 / 通过 / 拒绝" were legitimate ledger-card tags, not render residue.

## R10 Boundary

P6a is reversible as docs-only audit state:

- Revert P6a commits to remove audit docs and screenshot archive.
- No app behavior changes.

P6b must use one issue or tightly-coupled issue cluster per commit. A P6b
commit should be revertable without removing unrelated UI fixes.
