# M6-P5 Smoke Screenshot Targets

Date: 2026-05-26

## Status

Fresh browser screenshots were not captured during this M6-P5 run because browser automation was not exposed in the active tool context. This index records the screenshot targets that should be captured if browser tooling becomes available before defense.

Existing screenshots remain available in `docs/screenshots/` and continue to provide historical evidence for M2-M5 UI flows.

## Target Set

| Target | Purpose | Existing related evidence |
|--------|---------|---------------------------|
| Owner schema designer with published v1/v2 schema | Highlight 1: schema versioning and immutable facts | `phase-m2p4c-publish-success.png`, `phase-m2p4c-version-history.png`, `phase-m2p6c-historical-render-after-v2.png` |
| Labeler submission detail after v2 publish still rendering v1 | Highlight 1: submission locked to schema version | `phase-m2p6c-submission-v1-before-v2.png`, `phase-m2p6c-historical-render-after-v2.png` |
| Reviewer queue showing submitted item | Highlight 2 + M6-P1 submitted lifecycle | `phase-m4p4-reviewer-queue.png`, `phase-m4p4-reviewer-detail-pending.png` |
| Reviewer approve/reject with ledger visible | Highlight 2: Quality Ledger + verdict derivation | `phase-m5p5-reviewer-ledger-mixed.png`, `phase-m5p5-reviewer-ledger-mixed-after-approve.png` |
| Trusted Export snapshot list and diff modal | Highlight 3: reproducible export hashes | `phase-m5p3b-trusted-export-one-snapshot.png`, `phase-m5p3b-trusted-export-two-selected.png`, `phase-m5p3b-diff-modal-equal.png` |
| AI review drawer first call with provenance | Highlight 4: AI provenance evidence | `phase-m3p4-ai-drawer-first.png`, `phase-m3p5-owner-shared-provenance.png` |
| AI review drawer idempotency hit | Highlight 4 + M6-P3b hit-ratio evidence | `phase-m3p4-ai-drawer-idempotency-hit.png`, `phase-m5p6-deepseek-idempotency-hit.png` |
| DeepSeek first-call smoke evidence | Highlight 4 real-provider smoke | `phase-m5p6-deepseek-first-call.png`, `phase-m5p6-db-ai-ledger-evidence.png` |
| Actuator Prometheus scrape | M6-P3b metrics endpoint | No browser screenshot; command/test evidence in `ActuatorPrometheusEndpointExposureTest` |
| Failed AI call evidence SQL view | M6-P4a failed-attempt rows | No screenshot target exists yet; command/test evidence in `FailedAiCallRecorderTest` and `AiReviewServiceTest` |
| Export failure cleanup evidence | M6-P4b exact-key cleanup | No screenshot target; command/test evidence in `ExportServiceTest` |

## D-口径

Screenshot capture is optional for M6-P5 if browser tooling is unavailable. Command-backed regression evidence remains the source of truth for M6-P5 final readiness.

