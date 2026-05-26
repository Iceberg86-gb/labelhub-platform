# M6-P6b1 Lite After Screenshots

## Purpose

Lite after-screenshot set for M6-P6b1 sanity verification of the four global UI primitives.

This set validates the global primitives landed in M6-P6b1 before the page-level P6b2 polish begins.

## Capture Metadata

- Capture date: 2026-05-26
- Browser: Chrome
- Browser zoom: 100%
- Capture mode: Retina full-screen
- Image size: 3840x2160
- Scope: lite sanity set, not the full P6b2 after-screenshot set

## Screenshots

| File | Primitive verification |
|------|------------------------|
| `01-owner-schema-typical-after.png` | G-P0 #1 logo subtitle readable; G-P0 #3 OWNER badge purple; G-P0 #4 H1/H2 typography on schema designer |
| `02-labeler-submission-detail-after.png` | G-P0 #3 LABELER badge blue; G-P0 #4 typography on a labeler page |
| `03-reviewer-queue-after.png` | G-P0 #3 REVIEWER badge amber; G-P0 #4 typography and table header sizing on the reviewer queue |
| `04-owner-ai-evidence-after.png` | G-P0 #4 typography density check on the dense AI/evidence page; secondary OWNER badge consistency check |

## Out Of Scope For This Set

- This is not the full M6-P6b2 after-screenshot set. M6-P6b2 will produce its own after-screenshots covering all six locked defense paths.
- `<TruncatedHash>` is intentionally invisible in these screenshots because M6-P6b1 scaffolded the component only; integration is deferred to M6-P6b2 evidence-surface clusters.
- Hashes may still appear in their old truncated, non-copyable form in this set. That is expected and is not a P6b1 regression.

## D-Koujing Notes

- `03-reviewer-queue-after.png` shows the Verdict and action columns outside the visible viewport because the screenshot window was narrower than the before-screenshot capture. `apps/web/src/pages/reviewer/ReviewerQueuePage.tsx` still renders the Verdict column and start-review action column.
- `02-labeler-submission-detail-after.png` does not show the red `必填` marker because Submission #3 uses a different field configuration than Submission #6. The `必填` render path remains unchanged and M6-P6b2 still owns P0 #11.
