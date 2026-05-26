# M6-P6 Before Screenshot Set

Captured for M6-P6a UI audit on 2026-05-26.

- Logical viewport: 1440x900.
- Retina PNG dimensions: 2880x1800.
- Theme: current default light UI.
- Purpose: raw before-state evidence for P6 UI Experience Polish.

## Captured Screenshots

| # | File | Defense path | State | Audit issues surfaced |
|---|------|--------------|-------|-----------------------|
| 2 | `02-owner-schema-typical.png` | Owner schema designer | Typical schema state with fields | G-P0 #1, G-P0 #3, G-P0 #4, #7 |
| 3 | `03-owner-schema-version-history.png` | Owner schema designer | Version history drawer open | G-P0 #1, G-P0 #2, G-P0 #4, #7, #8, #10 |
| 4 | `04-labeler-submission-detail.png` | Labeler submission detail | Submitted detail with AI record | G-P0 #1, G-P0 #2, G-P0 #4, #11, #12 |
| 5 | `05-labeler-historical-render.png` | Labeler submission detail | Historical v1 render after v2 exists | G-P0 #1, G-P0 #4, #15 |
| 6 | `06-reviewer-queue-list.png` | Reviewer queue | Queue list with submitted items | G-P0 #1, G-P0 #3, G-P0 #4, #17, #18 |
| 7 | `07-reviewer-detail-ledger.png` | Reviewer detail + ledger | Submission detail with ledger panel | G-P0 #1, G-P0 #3, G-P0 #4, #21 (P1), #25 (P1) |
| 9 | `09-export-snapshot-list.png` | Trusted Export | Snapshot list with matching manifest hashes | G-P0 #1, G-P0 #2, G-P0 #4, #26, #27, #28 |
| 10 | `10-export-diff-modal.png` | Trusted Export diff | Matching snapshot diff modal | G-P0 #2, G-P0 #4, #31, #32, #33 |
| 12 | `12-ai-drawer-idempotency-hit.png` | AI review drawer | Idempotency-hit cached result | G-P0 #2, G-P0 #4, #35, #36, #37, #38 |
| 14 | `14-owner-setup-3cta.png` | Owner setup guidance | 3-CTA next-step guidance | G-P0 #1, G-P0 #3, G-P0 #4, #40 |

## Unable to Capture

| Expected file | Reason | P6a handling |
|---------------|--------|--------------|
| `01-owner-schema-empty.png` | Current dev data has no empty schema designer state | D-口径, do not fabricate |
| `08-reviewer-queue-empty.png` | Reviewer queue currently has submitted items | D-口径, do not fabricate |
| `11-ai-drawer-first-call.png` | Existing submission already has AI result; drawer opens as idempotency hit | D-口径, do not fabricate |
| `13-ai-drawer-failure.png` | Optional failure state was not forced | D-口径, do not fabricate |
| `15-owner-setup-mid-step.png` | Current draft tasks lack a real mid-step setup state | D-口径, do not fabricate |

## Audit Correction

The original audit listed reviewer-detail issue `#22` as possible
"提示 / 通过 / 拒绝" render residue. Re-review showed these were legitimate
ledger-card tags for Ledger #9/#10/#11, not a rendering bug. Issue `#22` is
removed from P0 and recorded only as an R8 audit correction.
