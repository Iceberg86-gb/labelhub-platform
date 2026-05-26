# M6-P6b2 Full After Screenshot Set

Purpose: full after-screenshot set for M6-P6b2 page-level P0 visual
verification across the six locked defense paths.

## Capture Metadata

- Capture date: 2026-05-26.
- Browser: Codex in-app browser against local LabelHub dev services.
- Image format: PNG.
- Image dimensions: 2560 x 1600.
- Zip bundle: `docs/screenshots/m6p6-after-set/m6p6-p6b2-full-after.zip`.
- Images `08-export-diff-modal-after.png` and
  `09-ai-drawer-idempotency-hit-after.png` were re-captured after nits commit
  `5c03dcf`.

## Screenshot Map

| File | Defense path | Audit IDs verified |
|------|--------------|--------------------|
| `01-owner-schema-typical-after.png` | Owner schema designer | G-P0 #1, G-P0 #3, G-P0 #4 |
| `02-owner-schema-version-history-after.png` | Owner schema version history | #7, #8, #10, G-P0 #2 |
| `03-labeler-submission-detail-after.png` | Labeler submission detail | #11, #12, #15, G-P0 #2 |
| `04-labeler-historical-render-after.png` | Labeler historical render | #15 |
| `05-reviewer-queue-list-after.png` | Reviewer queue | #17, #18, G-P0 #3 |
| `06-reviewer-detail-ledger-after.png` | Reviewer detail + ledger | sanity: #11 propagation to historical schema render; no P1 drift |
| `07-export-snapshot-list-after.png` | Trusted Export list | #26, #27, G-P0 #2 |
| `08-export-diff-modal-after.png` | Trusted Export diff modal | #31, #32 after nits, #33, G-P0 #2 |
| `09-ai-drawer-idempotency-hit-after.png` | AI review drawer | #35, #36, #37, #38 after nits, G-P0 #2 |
| `10-owner-setup-3cta-after.png` | Owner setup 3-CTA | #40 |

## Notes

- This set is the P6b2 full after set. The four-image lite P6b1 sanity set in
  the parent directory is preserved as the P6b1 primitive sanity baseline.
- #28 same-hash indicator is intentionally absent; it was demoted to P1 before
  P6b2 implementation.
- P1 reviewer detail items (#21, #23, #25) remain untouched; screenshot 06 is a
  sanity check for no P1 drift.
