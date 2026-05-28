# M7-P2 After Screenshot Set

Purpose: archive M7-P2 closure visual evidence for the Formily runtime renderer
phase. Seeded browser data for the live consumer pages was unavailable during
C5-C8, so this set uses explicit evidence cards for blocked page captures and a
benchmark card for the actual C6 Vitest bench result.

Capture metadata:

- Date: 2026-05-27
- Capture mode: generated closure evidence cards rendered to PNG; seeded-data
  gaps are labeled in-image and in this index.
- Logical dimensions: 1280 x 1280 PNG cards.
- Browser/manual viewport note: 1440 / 1280 / 1024 manual browser sanity remains
  pending seeded data; tracked by `[M7-P2 watch]` in `humanpending.md`.

## Screenshot Map

| File | Defense path | Verifies |
|---|---|---|
| `01-formily-labeler-session.png` | M7-P2 labeler editing | `LabelerSessionPage` now imports `SchemaFormilyRenderer`; jsdom integration verifies edit + AnswerPayload persistence |
| `02-formily-labeler-submitted.png` | M7-P2 labeler submitted | Read-only submitted answer path uses `SchemaFormilyRenderer`; seeded browser submitted record pending |
| `03-formily-owner-submission.png` | M7-P2 owner review | Owner context uses Formily renderer; AI findings remain in `AiReviewDrawer` and are renderer-decoupled |
| `04-formily-reviewer-submission.png` | M7-P2 reviewer drawer | External error injection is verified through real Formily field-state API |
| `05-formily-designer-preview-3viewports.png` | M7-P2 designer preview | One-way preview panel renders through Formily; 3-viewport seeded screenshot remains watch evidence |
| `06-formily-benchmark-output.png` | M7-P2 benchmark output | C6 benchmark numbers: Formily 1 invocation vs legacy 500 on 500-field single change |

## D-口径 Notes

- Files `01` through `05` are not pretending to be live page screenshots. They
  are explicit evidence cards documenting code/test verification plus the
  seeded-data browser gap.
- File `06` is the phase's numeric performance evidence: 1 renderer invocation
  for Formily vs 500 for legacy on a 500-field single-field change.
- The C7 `[M7-P2 watch]` entry remains open until seeded browser regression
  screenshots are captured across labeler, owner, reviewer, and designer
  surfaces and the legacy renderer fallback can be safely removed.
