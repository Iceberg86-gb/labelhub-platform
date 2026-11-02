# M6-P6b1 Pre-Estimate

Baseline: `a52f084` (M6-P6a approved).

This document is the pre-code gate for P6b1. It records expected file paths,
line budgets, and complexity before any frontend implementation begins.

## Read-Only Structure Findings

| Concern | Current owner |
|---------|---------------|
| Header shell | `apps/web/src/shared/ui/AppLayout.tsx` |
| Current role badge render | `apps/web/src/shared/ui/AppLayout.tsx`, raw Semi `<Tag color="blue">` |
| Header logo/subtitle render | `apps/web/src/shared/ui/AppLayout.tsx` |
| Global styles | `apps/web/src/app/styles.css` |
| Existing shared UI directory | `apps/web/src/shared/ui/` |
| Existing CSS files | `apps/web/src/app/styles.css` only |

No page-level file is required for the P6b1 target scope.

## Target File Paths

| Primitive | Target files | Estimated frontend diff | Complexity |
|-----------|--------------|-------------------------|------------|
| Header logo subtitle | `apps/web/src/shared/ui/AppLayout.tsx`, `apps/web/src/app/styles.css` | ~10-20 lines | standard |
| Typography scale | `apps/web/src/app/styles.css` | ~35-55 lines | standard |
| RoleBadge | `apps/web/src/shared/ui/RoleBadge.tsx`, `apps/web/src/app/styles.css`, later `apps/web/src/shared/ui/AppLayout.tsx` for integration | ~45-70 lines | standard |
| TruncatedHash scaffold | `apps/web/src/shared/ui/TruncatedHash.tsx`, `apps/web/src/app/styles.css` | ~75-110 lines | non-trivial |

Estimated total frontend diff: **~165-255 lines**.

P6b1 hard cap: **300 lines**. This estimate leaves roughly 45 lines of buffer.

## Commit-Level File List

### Commit 2: `fix: repair header logo subtitle render`

Expected files:

- `apps/web/src/shared/ui/AppLayout.tsx`
- `apps/web/src/app/styles.css`

Root-cause investigation happens in this commit. If the cause is font loading
or non-CSS asset behavior, the implementation stops before code changes.

### Commit 3: `feat: add typography scale CSS variables and layout baseline`

Expected files:

- `apps/web/src/app/styles.css`

No page-specific typography overrides.

### Commit 4: `feat: add RoleBadge component`

Expected files:

- `apps/web/src/shared/ui/RoleBadge.tsx`
- `apps/web/src/app/styles.css`

No integration in this commit.

### Commit 5: `refactor: integrate RoleBadge into app shell header`

Expected files:

- `apps/web/src/shared/ui/AppLayout.tsx`

This should be a single integration site. If more files are required, stop and
report.

### Commit 6: `feat: add TruncatedHash component scaffold`

Expected files:

- `apps/web/src/shared/ui/TruncatedHash.tsx`
- `apps/web/src/app/styles.css`

No imports from schema, submission, export, AI drawer, reviewer, or owner setup
pages. The component may import Semi tooltip/button/icon utilities if the
existing dependency supports them.

## Explicit Non-Touches

P6b1 should not touch:

- `apps/web/src/features/schema-design/VersionHistoryDrawer.tsx`
- `apps/web/src/features/ai/AiProvenanceCard.tsx`
- `apps/web/src/features/ai/AiReviewDrawer.tsx`
- `apps/web/src/features/export/TrustedExportCard.tsx`
- `apps/web/src/features/export/ExportSnapshotDiffModal.tsx`
- `apps/web/src/pages/reviewer/*`
- `apps/web/src/pages/labeler/*`
- `apps/web/src/pages/owner/*`, except no owner page touch is expected
- any file under `services/`, `packages/contracts/`, or migrations

## Estimate Risks

| Risk | Impact | Stop condition |
|------|--------|----------------|
| Header subtitle root cause is not CSS/markup | Could require asset/font work | Stop before fix |
| Typography changes need page-specific rewrites | Would violate P6b1 boundary | Stop if more than layout/global CSS needed |
| RoleBadge header integration wants page-level role context | Unexpected routing/auth coupling | Stop if integration exceeds `AppLayout.tsx` |
| TruncatedHash copy affordance needs unsupported icon API | Could bloat component | Keep simple or stop if over 120 lines |

## Pre-Code Statement

No frontend implementation has been made in this pre-estimate. The intended
P6b1 code touch set is limited to:

- `apps/web/src/shared/ui/AppLayout.tsx`
- `apps/web/src/shared/ui/RoleBadge.tsx`
- `apps/web/src/shared/ui/TruncatedHash.tsx`
- `apps/web/src/app/styles.css`

If the user accepts this pre-estimate, P6b1 implementation can proceed. If the
user re-adjudicates, amend this document in a follow-up docs commit before any
code commit.
