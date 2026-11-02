# M6-P6c P1 Polish Scope Budget

Date: 2026-05-26

## Phase Character

M6-P6c is an optional P1 polish subset after M6-P6b2 P0 closure at
`282c297`. It is not part of the defense-readiness baseline. The phase only
addresses five low-risk visual consistency issues already recorded in the
locked M6-P6a UI audit backlog.

The phase does not reopen P0 decisions and does not introduce new behavior,
new data access, or new contracts.

## Locked P1 Subset

| ID | Issue | Surface |
|----|-------|---------|
| #16 | Filter dropdown / refresh button style consistency | Reviewer queue |
| #23 | Approve button success-green semantic | Reviewer detail |
| #25 | Verdict source provenance line emphasis | Reviewer detail |
| #28 | Export list same-hash indicator | Trusted Export list |
| #34 | Diff modal width | Trusted Export diff modal |

## Explicitly Not Entered

| ID | Reason |
|----|--------|
| #13 | Current submission detail tags are already semantically aligned; audit action is underspecified. |
| #21 | Reviewer ledger timeline restructure is a larger visual rewrite. |
| #29 / #30 | Export table column and disabled-state polish have lower marginal payoff after P0. |
| #39 | AI drawer field feedback cards are a larger component rewrite. |
| #42 / #44 | Owner setup spacing and empty-state copy are low marginal payoff. |

## Budget

| Item | Value |
|------|-------|
| P6b1 + P6b2 frontend diff used | 573 changed lines |
| P0 cap | 900 changed lines |
| Remaining headroom | 327 changed lines |
| P6c expected frontend diff | 34-52 changed lines |
| P6c hard cap | 100 changed lines |

If cumulative P6c frontend diff reaches 100 changed lines, implementation
must stop and wait for user re-adjudication.

## Allowed Surfaces

P6c may touch only these production frontend surfaces, and only for the locked
issue attached to each:

- `apps/web/src/features/export/ExportSnapshotDiffModal.tsx`
- `apps/web/src/features/export/TrustedExportCard.tsx`
- `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx`
- `apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx`
- `apps/web/src/app/styles.css`

P6c may also write:

- `docs/internal/m6p6c-*.md`
- `docs/screenshots/m6p6c-after-set/` after user screenshot capture
- `humanpending.md` in the final docs commit

## Forbidden Surfaces

- `services/`
- `packages/contracts/`
- `db/migration/`
- backend or frontend test files
- new routes
- new fetches
- new OpenAPI consumers
- new API field consumption
- Semi Design global theme tokens
- P6b1 primitives: `<RoleBadge>`, typography variables, `.brand-subtitle`,
  header subtitle CSS, and `<TruncatedHash>` itself
- prior P6a/P6b scope, audit, pre-estimate, or verification docs
- P6b2 after-screenshot files

## Stop Conditions

Implementation must stop and report if any of these occur:

1. A cluster actual diff exceeds its pre-estimate by 50% or more.
2. Cumulative P6c frontend diff reaches 100 changed lines.
3. A cluster requires a file outside the locked target paths.
4. A cluster requires a new fetch, new field, or new OpenAPI consumer.
5. A cluster requires a Semi theme token change.
6. Same-hash detection cannot be computed from existing export list data.
7. typecheck or build fails after any code commit.
8. A change would include formatting, import sorting, or unrelated cleanup.
9. A P0 visual regression is observed while touching reviewer/export surfaces.
10. Any of the five P1 action descriptions is still ambiguous after
    pre-estimate adjudication.

## Verification Gates

After every code commit:

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- cumulative P6c diff with
  `git diff 282c297..HEAD --shortstat -- apps/web/src/`

Final P6c docs commit must additionally record:

- frontend typecheck and build
- backend full suite (`390 + 78` expected, or documented D-口径)
- protected endpoint guard
- OpenAPI MD5 unchanged
- migration count unchanged
- no-touch confirmation for backend/contracts/migrations
- visual verification references to user-captured screenshots

## Status

This document is the docs-only pre-estimate gate. No P6c production code has
landed yet. User adjudication is required before implementation begins.
