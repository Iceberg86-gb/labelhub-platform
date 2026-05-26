# M6-P6c After Screenshot Set

Purpose: after-screenshot set for the optional M6-P6c P1 polish subset.

Capture metadata:

- Date: 2026-05-26
- Browser: Codex in-app browser against local Vite/API
- Logical viewport during capture: approximately 1707 x 960
- Image dimensions: 2275 x 1280 PNG
- Capture mode: viewport screenshots after P6c D5 code head `9cdff86`

## Screenshot Map

| File | Defense path | Audit IDs verified |
|------|--------------|--------------------|
| `01-reviewer-queue-after.png` | Reviewer queue | `#16` filter Select size aligned with the small Refresh button |
| `02-reviewer-detail-after.png` | Reviewer detail | `#23` approve button success-green; `#25` verdict source emphasis |
| `03-export-snapshot-list-after.png` | Trusted Export list | `#28` same-page manifest hash indicator |
| `04-export-diff-modal-after.png` | Trusted Export diff modal | `#34` diff modal width `840` |

## D-口径 Notes

- `#28` is a page-local hint. It compares `manifestHash` values within the currently rendered export list page only; it does not fetch snapshots across pages and does not replace the diff modal's authoritative comparison.
- `03-export-snapshot-list-after.png` intentionally shows two snapshots with the same `manifestHash`, both marked `✓ 同 hash`.
- `04-export-diff-modal-after.png` preserves the P6b2 diff evidence while showing the widened modal treatment.

For comparison context, see the P6b2 full after set under `docs/screenshots/m6p6-after-set/p6b2-full/`.
