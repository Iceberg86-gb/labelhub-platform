# Submission State Machine

PDF 4.5 uses "人工复审" to mean the full human-review stage after AI pre-review. LabelHub models that stage internally as `initial -> review -> final`, with final review enabled for disputes or demo scenarios.

| State | Can Move To | Guard | Actor |
|------|-------------|-------|-------|
| `created` | `under_ai_review` | submission persisted and outbox event written | API |
| `under_ai_review` | `under_human_initial` | AI verdict pass/manual or fail-to-human | AI Agent |
| `under_ai_review` | `needs_revision` | AI reject and rule allows direct return | AI Agent |
| `under_human_initial` | `under_human_review` | initial reviewer approves | Reviewer |
| `under_human_initial` | `needs_revision` | reject reason provided | Reviewer |
| `under_human_review` | `accepted` | default two-level flow approves | Reviewer |
| `under_human_review` | `under_human_final` | dispute or configured final gate | Reviewer |
| `under_human_final` | `accepted` | final reviewer approves | Reviewer |
| `under_human_final` | `needs_revision` | reject reason provided | Reviewer |
| `needs_revision` | `superseded` | labeler submits corrected version | Labeler |

AI and human evidence is append-only in `quality_ledger_entries`; `current_verdicts` is derived.
