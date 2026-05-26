# M6-P6b2 Pre-Estimate

Baseline: `7d4d187` after M6-P6b1 sanity sign-off.

This document is the required pre-code gate for M6-P6b2. It verifies target files, estimates line budget, and surfaces data-shape issues before implementation begins.

## Summary

| Item | Value |
|------|-------|
| P6b1 frontend diff consumed | 182 changed lines |
| P0 hard cap | 900 changed frontend lines |
| Remaining P0 headroom | 718 changed frontend lines |
| P6b2 estimated P0 total | ~660 changed frontend lines, before adjudication buffers |
| P0 headroom after estimate | ~58 lines |
| P1 included | No |
| #28 included | No, demoted to P1 |
| Backend/OpenAPI/migration touched | No |

Two items require user adjudication before code:

- C2 #15 exact current-vs-historical schema condition is not available from current frontend data without a new fetch or field consumption.
- C3 #32 raw three-hash values are not available in the current diff payload; only match booleans are exposed.

## Cluster Estimates

### C1: Schema Version History + Drawer

Issues: #7, #8, #10.

Target files verified:

- `apps/web/src/features/schema-design/VersionHistoryDrawer.tsx`
- `apps/web/src/app/styles.css`
- Possible: `apps/web/src/shared/ui/TruncatedHash.tsx` only if first integration needs a generic prop extension.

Read-only evidence:

- `VersionHistoryDrawer.tsx` currently shortens `contentHash` locally via `version.contentHash.slice(0, 8)`.
- Existing styles already include `.version-history-list`, `.version-history-card`, `.version-history-meta`, and `.schema-version-json-preview`.
- The drawer currently uses `SideSheet` without a local mask-opacity override.

Planned work:

- Replace local `contentHash` shortening with `<TruncatedHash>`.
- Strengthen current-version card hierarchy.
- Add field-count and timestamp icon prefixes using existing data.
- Add vertical timeline treatment in CSS.
- Clean up "展开 JSON" styling as a secondary action, with no new buttons.
- Deepen drawer mask opacity if supported locally; otherwise implement the smallest scoped CSS override.

Estimate: ~185 changed lines.

Complexity: non-trivial, because it is the first `<TruncatedHash>` integration and may validate the component API.

TruncatedHash extension allowance: up to +20 lines here only, if needed.

### C2: Labeler Submission + Historical Render Banner

Issues: #11, #12, #15.

Target files verified:

- `apps/web/src/features/labeling/field-renderers/rendererUtils.tsx`
- `apps/web/src/features/labeling/field-renderers/TextFieldRenderer.tsx`
- `apps/web/src/features/labeling/field-renderers/NumberFieldRenderer.tsx`
- `apps/web/src/features/labeling/field-renderers/SelectFieldRenderer.tsx`
- `apps/web/src/features/labeling/field-renderers/DateFieldRenderer.tsx`
- `apps/web/src/features/labeling/field-renderers/FileUploadFieldRenderer.tsx`
- `apps/web/src/features/labeling/field-renderers/NestedObjectFieldRenderer.tsx`
- `apps/web/src/features/labeling/SchemaRenderer.tsx`
- `apps/web/src/features/ai/AiProvenanceCard.tsx`
- `apps/web/src/pages/labeler/LabelerSubmissionPage.tsx`
- `apps/web/src/app/styles.css`

Read-only evidence:

- `rendererUtils.tsx` renders the red `必填` marker unconditionally when `field.validation?.required`.
- `SchemaRenderer` already passes `readOnly` to every field renderer, but `FieldFrame` does not receive it.
- `AiProvenanceCard.tsx` owns the submission "AI 检查记录" card and locally shortens input/output hashes.
- `SubmissionRenderSchema` exposes `submissionId`, bound `schemaVersion`, `answerPayload`, and optional `provenance`; it does not expose the current schema version.

Planned work:

- Hide the required marker in read-only submitted detail contexts while preserving it for active labeling views.
- Convert `AiProvenanceCard` cost/latency/completed status into stat cards.
- Replace AI input/output hash text with `<TruncatedHash>`.

Adjudication needed for #15:

The exact requested banner condition is "show only when submission.schema_version < current schema version" and includes current version `vM` in the copy. Current frontend data does not expose current schema version on `SubmissionRenderSchema`, and adding a fetch to `/schemas/{schemaId}/versions` or consuming a new field would violate P6b2 constraints.

Options for user adjudication:

- A. Keep #15 blocked in P6b2 P0 until a later backend/API-visible phase.
- B. Allow a reduced banner using only existing data: "此 submission 按提交时的 Schema vN 渲染,历史答案不会改写。" It would be visible on submitted detail pages without claiming current version `vM`.
- C. Allow a new frontend fetch/consumer path to compute current version, which violates the current strict constraints and would require scope re-adjudication.

Estimate without #15: ~125 changed lines.
Estimate with reduced #15 option B: ~165 changed lines.

Complexity: non-trivial because #11 touches the shared field-frame path across all renderer types.

### C3: Trusted Export List + Diff Modal

Issues: #26, #27, #31, #32, #33. Issue #28 is not included.

Target files verified:

- `apps/web/src/features/export/TrustedExportCard.tsx`
- `apps/web/src/features/export/ExportSnapshotDiffModal.tsx`
- `apps/web/src/app/styles.css`

Read-only evidence:

- `TrustedExportCard.tsx` owns the Trusted Export heading/subtitle and Manifest Hash column.
- `ExportSnapshotDiffModal.tsx` owns the success banner, three-hash match cards, and file-level SHA table.
- `ExportSnapshotDiff.hashMatches` exposes booleans only: `fileHash`, `manifestHash`, `sourceStateHash`.
- `ExportSnapshotDiff.fileLevelMatches` exposes file-level base/compare SHA-256 values.

Planned work:

- Strengthen Trusted Export heading/subtitle hierarchy.
- Replace Manifest Hash short text with `<TruncatedHash>`.
- Promote success banner description treatment.
- Replace three-hash card `✓ ...` style with explicit `✓ 一致` / `✗ 不一致` labels because raw three-hash values are not available.
- Add "N/N 一致" hero count badge for file-level table.
- Use filled success/danger status tags and subtle row tint for file match state.
- Replace file-level SHA text with `<TruncatedHash>` if this is accepted as part of #33's SHA-256 table polish.

Adjudication needed for #32:

The prompt's cluster table says #32 should integrate `<TruncatedHash>` for the three-hash card. Current data does not expose the raw three hash values, only booleans. The feasible strict-scope implementation is explicit match labels for #32 plus `<TruncatedHash>` on #27 Manifest Hash and file-level SHA values under #33.

Options for user adjudication:

- A. Accept `✓ 一致` labels for #32 and count `<TruncatedHash>` integration in C3 as #27 plus file-level SHA values under #33.
- B. Require raw three-hash display, which needs new API data or field consumption and therefore exits P6b2 scope.

Estimate with option A: ~135 changed lines.

Complexity: standard.

### C4: AI Drawer

Issues: #35, #36, #37, #38.

Target files verified:

- `apps/web/src/features/ai/AiReviewDrawer.tsx`
- `apps/web/src/app/styles.css`

Read-only evidence:

- `AiReviewDrawer.tsx` owns the idempotency-hit banner.
- It renders advice, provider, prompt, cost, latency, completed, input hash, output hash, and usage metrics in one meta grid.
- It locally shortens input/output hashes.

Planned work:

- Strengthen idempotency-hit banner treatment.
- Convert cost/latency/completed into stat cards.
- Use existing `result.idempotencyHit` and existing `aiCall.cost` value for "本次未产生 cost" copy.
- Replace input/output hash text with `<TruncatedHash>`.
- Rephrase suggestion label as one coherent phrase.

Estimate: ~105 changed lines.

Complexity: standard.

No `<TruncatedHash>` extension allowed here.

### C5: Reviewer Queue Surface

Issues: #17, #18.

Target files verified:

- `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx`
- `apps/web/src/app/styles.css`

Read-only evidence:

- `ReviewerQueuePage.tsx` owns the "从 append-only Quality Ledger 派生当前 Verdict。" subtitle.
- The table is wrapped in `.task-table-surface`.

Planned work:

- Add row hover background via scoped CSS, using Semi table structure where possible.
- Increase subtitle prominence and add info tooltip explaining ledger-derived verdict.

Estimate: ~45 changed lines.

Complexity: standard.

### C6: Owner Setup CTA Lock

Issue: #40.

Target files verified:

- `apps/web/src/features/task/task-detail/TaskNextStepGuidance.tsx`
- `apps/web/src/app/styles.css`

Read-only evidence:

- `statusTag('blocked')` currently returns a grey `待前置` tag.
- `.task-setup-step--blocked` already exists and provides a subtle background.
- The publish button is already disabled when prerequisites are missing.

Planned work:

- Add a lock visual to blocked status.
- Keep disabled behavior unchanged.
- Strengthen visual distinction between blocked and actionable pending states.

Estimate: ~25 changed lines.

Complexity: trivial.

## Cumulative Budget

| Cluster | Estimate |
|---------|----------|
| C1 | ~185 |
| C2 | ~125 without #15 / ~165 with reduced #15 |
| C3 | ~135 with #32 option A |
| C4 | ~105 |
| C5 | ~45 |
| C6 | ~25 |

Total:

- Without #15: ~620 lines.
- With reduced #15 option B: ~660 lines.

P6b1 consumed 182 lines. The combined P0 estimate is therefore:

- Without #15: ~802 / 900.
- With reduced #15 option B: ~842 / 900.

Headroom:

- Without #15: ~98 lines.
- With reduced #15 option B: ~58 lines.

## Explicit Non-Touch List

Do not touch during P6b2 P0:

- `apps/web/src/shared/ui/RoleBadge.tsx`
- P6b1 typography variables in `apps/web/src/app/styles.css`
- `.brand-subtitle`
- Header/app shell logo and role badge markup
- `apps/web/src/app/AppLayout.tsx`
- Login pages
- 404/not-found pages
- User settings pages
- General dashboard pages
- Any test file
- Any generated API schema file

Do not touch P1-only surfaces unless later adjudicated:

- Export same-hash indicator (#28)
- Reviewer ledger timeline restructure (#21)
- Owner setup spacing/empty-state copy (#42/#44)
- AI drawer field-feedback card conversion (#39)
- Export modal width (#34)
- Export table column-width polish (#29/#30)

## TruncatedHash Integration Plan

Planned strict-scope integrations:

| Audit ID | File | Integration |
|----------|------|-------------|
| #7 | `VersionHistoryDrawer.tsx` | schema version `contentHash` |
| #12 | `AiProvenanceCard.tsx` | submission AI input/output hashes |
| #27 | `TrustedExportCard.tsx` | manifest hash column |
| #33 | `ExportSnapshotDiffModal.tsx` | file-level base/compare SHA values, if user accepts C3 option A |
| #37 | `AiReviewDrawer.tsx` | drawer input/output hashes |

Pre-estimate correction:

- #32 three-hash cards cannot use `<TruncatedHash>` without raw hash values. Current data supports explicit `✓ 一致` labels only.

## Pre-Code Adjudication Required

Please adjudicate before code begins:

1. C2 #15: choose A (block), B (reduced existing-data banner), or C (expand scope with new fetch/API consumption).
2. C3 #32: choose A (explicit match labels; TruncatedHash only on available manifest/file hashes) or B (require raw three-hash display, which exits strict P6b2 scope).

Recommended path:

- C2 #15: B, reduced existing-data banner.
- C3 #32: A, explicit match labels.

This keeps P6b2 inside the zero-backend, zero-OpenAPI, no-new-fetch boundary and leaves ~58 lines of P0 budget headroom.
