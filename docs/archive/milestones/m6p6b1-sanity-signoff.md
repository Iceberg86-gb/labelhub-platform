# M6-P6b1 Sanity Sign-Off

## Status

M6-P6b1 visual sanity is APPROVED on 2026-05-26.

Baseline: `d0773c9` after the M6-P6b1 verification commit.

This document records the user-adjudicated visual sanity pass over the four lite after-screenshots in `docs/screenshots/m6p6-after-set/`.

## Per-Primitive Sign-Off

| Primitive | Audit ID | Sign-off |
|-----------|----------|----------|
| Header logo subtitle | G-P0 #1 | Approved. `AI 监督信号治理系统` is readable on the dark header at the new scoped `.brand-subtitle` color. |
| RoleBadge | G-P0 #3 | Approved. OWNER purple, LABELER blue, and REVIEWER amber are clearly distinct across the three role pages. |
| Typography scale | G-P0 #4 | Approved. H1/H2 hierarchy and body density are consistent across the four representative pages, including the dense AI-evidence page. |
| TruncatedHash | G-P0 #2 | Scaffold-only. Visual integration is deferred to M6-P6b2 page-level clusters; no after-screenshot evidence is expected here. |

## Non-Regression Analysis

### Reviewer Queue Verdict Column

Observation: `03-reviewer-queue-after.png` does not visibly show the Verdict column or the start-review action column.

Classification: not a P6b1 code regression. The after-screenshot viewport is narrower than the before-screenshot capture, so the Semi table's right-side columns sit outside the visible viewport.

Code evidence:

- `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:56` still defines the `Verdict` column.
- `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:58` still renders `VerdictTag`.
- `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx:65` still renders the `开始审核` action.

M6-P6b2 may still improve table polish where already scoped, but this observation does not indicate a P6b1 regression.

### Labeler Submission Required Marker

Observation: `02-labeler-submission-detail-after.png` does not show the red `必填` marker that appeared in the before screenshot for Submission #6.

Classification: data-driven field configuration difference, not a P6b1 fix of P0 #11. Submission #3 uses a different schema field configuration from Submission #6.

Code evidence:

- `apps/web/src/features/labeling/field-renderers/rendererUtils.tsx:18` still renders `必填` when `field.validation?.required` is true.
- M6-P6b1 did not touch `rendererUtils.tsx`.

P0 #11 remains in M6-P6b2 scope: submitted submission details should not present `必填` as an error-like red marker.

## M6-P6b2 Readiness

Page-level polish may proceed against the locked M6-P6a audit P0 list.

`<TruncatedHash>` integration enters M6-P6b2 at:

- `#7` schema version history content hash
- `#12` submission AI provenance hashes
- `#27` export list manifest hash
- `#32` export diff three-hash card
- `#37` AI drawer input/output hash

The lite P6b1 screenshots validate only the global primitive layer. M6-P6b2 remains responsible for the full after-screenshot set across all six locked defense paths.
