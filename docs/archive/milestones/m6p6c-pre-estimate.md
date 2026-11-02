# M6-P6c P1 Polish Pre-Estimate

Date: 2026-05-26

Baseline: `282c297` (`docs: M6-P6b2 verification and M6-P6 phase closure`)

## Summary

P6c enters five low-risk P1 issues from the locked M6-P6a audit backlog:
`#16`, `#23`, `#25`, `#28`, and `#34`.

Estimated frontend diff: **34-52 changed lines**.

P6c hard cap: **100 changed lines**.

No P1 work outside the five locked issues is included.

## Target File Verification

### D1: #34 Diff Modal Width

Target file:

- `apps/web/src/features/export/ExportSnapshotDiffModal.tsx`

Verified owner:

- `ExportSnapshotDiffModal` renders the Semi `<Modal>` at lines 21-27.
- Current width is `width={720}` at line 26.

Proposed treatment for user adjudication:

- Increase the modal width from `720` to `840`.
- This is a single prop change and keeps the P6b2 modal layout intact.
- The goal is visual breathing room for the three-hash cards and file-level
  SHA table. No new layout behavior or data access.

Estimate: **1 changed line**.

Complexity: trivial.

### D2: #16 Filter Dropdown / Refresh Button Style Consistency

Target files:

- `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx`
- `apps/web/src/app/styles.css` only if the JSX-only change is not enough

Verified owner:

- Reviewer queue filter Select is in `ReviewerQueuePage.tsx` lines 96-107.
- Refresh Button is in `ReviewerQueuePage.tsx` lines 110-114 and already uses
  `size="small"`.
- Existing filter CSS is `.reviewer-filter-select` at `styles.css` lines
  1330-1332.

Proposed treatment for user adjudication:

- Add `size="small"` to the reviewer verdict `<Select>` so the filter and
  refresh action share the same control scale.
- No CSS is expected unless visual spacing needs one small existing-rule
  adjustment under `.reviewer-filter-select`.
- Do not change filter semantics, option labels, refresh behavior, or toolbar
  layout.

Estimate: **1-4 changed lines**.

Complexity: trivial.

### D3: #23 Approve Button Success-Green Semantic

Target files:

- `apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx`
- `apps/web/src/app/styles.css`

Verified owner:

- Reviewer approve action is the `通过` Button at
  `ReviewerSubmissionPage.tsx` lines 147-150.
- Reject action is the `拒绝` Button at lines 151-153 and already uses
  `type="danger"`.
- Semi Button usage in this codebase does not show an existing `type="success"`
  prop, so a local class is safer than pretending a one-line success prop
  exists.

Proposed treatment for user adjudication:

- Add `className="review-approve-button"` to the approve Button.
- Add a local `.review-approve-button` CSS rule scoped to the reviewer action
  surface, setting a success-green background and border.
- Keep `theme="solid"`, the icon, loading behavior, and `onApprove` unchanged.
- Do not touch the reject Button.

Estimate: **6-10 changed lines**.

Complexity: standard, because the semantic color requires local CSS instead of
an existing Semi success prop.

### D4: #25 Verdict Source Provenance Line Emphasis

Target files:

- `apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx`
- `apps/web/src/app/styles.css`

Verified owner:

- Current provenance line is
  `Typography.Text type="tertiary">Verdict 来源: ...</Typography.Text>` at
  `ReviewerSubmissionPage.tsx` lines 96-98.
- It currently reads either `Verdict 来源: Ledger #<id>` or
  `Verdict 来源: 暂无 ledger entry`.

Proposed treatment for user adjudication:

- Replace the tertiary text with a small provenance pill:
  `Tooltip` + `Tag` + `IconInfoCircle`.
- Tooltip copy: `当前 Verdict 由最新 Quality Ledger entry 派生。`
- Tag text remains exactly `Verdict 来源: Ledger #<id>` or
  `Verdict 来源: 暂无 ledger entry`.
- Add a small local CSS class such as `.verdict-source-tag` to increase
  font-weight and keep the pill aligned in the header.
- This mirrors the P6b2 reviewer queue ledger derivation cue without changing
  verdict behavior or ledger data.

Estimate: **12-18 changed lines**.

Complexity: standard. The audit action only says "emphasis"; this treatment
requires user adjudication before code.

### D5: #28 Export List Same-Hash Indicator

Target files:

- `apps/web/src/features/export/TrustedExportCard.tsx`
- `apps/web/src/app/styles.css`

Verified owner and data availability:

- `TrustedExportCard.tsx` uses `useTaskExportsQuery` and derives
  `items = exportsQuery.data?.items ?? []` at line 33.
- The list row type `ExportSnapshot` includes `manifestHash` in the generated
  contract (`schema.d.ts` lines 1106-1123).
- The Manifest Hash column already renders `TruncatedHash` at
  `TrustedExportCard.tsx` lines 49-52.
- Therefore repeated manifest hashes can be detected client-side from existing
  rendered list data with no new fetch and no new API field consumption.

Proposed treatment for user adjudication:

- Add a `manifestHashCounts` `useMemo` computed from `items`.
- In the Manifest Hash column render, show the existing `<TruncatedHash>` and,
  when `manifestHashCounts.get(value) > 1`, append a subtle tag such as
  `✓ 相同 hash`.
- Use existing list data only. Indicator is page-local; it does not compare
  against snapshots outside the current query page.
- Add a small `.export-same-hash-tag` CSS rule for restrained success styling.

Estimate: **14-19 changed lines**.

Complexity: standard. This is the only P6c cluster with list-row computation.

## Cumulative Budget

| Cluster | Estimate | Complexity |
|---------|----------|------------|
| D1 #34 | 1 | trivial |
| D2 #16 | 1-4 | trivial |
| D3 #23 | 6-10 | standard |
| D4 #25 | 12-18 | standard |
| D5 #28 | 14-19 | standard |
| **Total** | **34-52** | under 100-line hard cap |

P6b1 + P6b2 cumulative frontend diff: 573 changed lines.

Estimated P6c cumulative frontend diff after this subset:

- Low estimate: 607 changed lines
- High estimate: 625 changed lines

Both remain well below the 900-line P0/P1 visual-polish cap.

## #13 Explicit Exclusion

Issue `#13` is not in scope. The user adjudicated that the current submission
detail tags are already semantically aligned enough for P6:

- `已提交` uses success semantics.
- `Session #N` uses informational semantics.
- `Schema 版本` uses a distinct version-oriented color.

Because the audit description for `#13` is underspecified and the current UI is
not visibly broken, changing it in P6c would risk subjective color tuning rather
than targeted polish.

## No-Touch List

P6c must not touch:

- P6b1 primitives:
  - `apps/web/src/shared/ui/RoleBadge.tsx`
  - `apps/web/src/shared/ui/TruncatedHash.tsx`
  - typography variables in `apps/web/src/app/styles.css`
  - `.brand-subtitle`
  - header logo / role badge integration in `AppLayout.tsx`
- P6b2 P0 surfaces except where one of the five P1 issues legitimately
  re-touches that same file.
- P6b2 after-screenshots in `docs/screenshots/m6p6-after-set/p6b2-full/`.
- M6-P6a / P6b1 / P6b2 audit, scope, pre-estimate, and verification docs.
- Any backend, contracts, migrations, or test files.

## P1 Items Still Deferred

The following P1 items remain deferred:

- `#13` submission detail tag color semantics
- `#21` reviewer ledger timeline restructure
- `#29 / #30` export table column / disabled-state polish
- `#39` AI drawer field feedback cards
- `#42 / #44` owner setup spacing / empty-state copy

## Required User Adjudication Before Code

Before implementation begins, the user should confirm:

1. D1 modal width target: `840` is acceptable.
2. D3 approve button should use a local success-green CSS class because there
   is no proven existing Semi `success` button type in this codebase.
3. D4 provenance emphasis treatment: `Tooltip + Tag + IconInfoCircle` is the
   intended interpretation of "emphasis".
4. D5 same-hash indicator text `✓ 相同 hash` is acceptable and page-local
   comparison is sufficient.

No production code should land until these four points are accepted or revised.
