# LabelHub Status Visualization

Status assets preserve the review product principle: AI evidence is auxiliary, and human review is the final decision.

## Files

- `review-workflow-linear.svg` — submit to initial review to senior review to final pass/reject.
- `status-tag-pending-initial-review.svg`
- `status-tag-initial-review-passed.svg` — non-terminal, includes continuation arrow.
- `status-tag-pending-senior-review.svg`
- `status-tag-final-passed.svg` — terminal, includes end cap.
- `status-tag-returned-for-modification.svg` — non-terminal, includes return + continuation cue.
- `status-tag-final-rejected.svg` — terminal, includes stop/end cue.
- `status-tag-manual-review-needed.svg`
- `status-tag-ai-reference.svg` — intentionally lower contrast and non-decisive.

## Theme Variables

```css
.lh-status-asset {
  color: var(--color-text-secondary);
  --lh-status-muted: var(--color-text-tertiary);
  --lh-status-neutral: var(--color-text-secondary);
  --lh-status-neutral-soft: var(--color-surface-subtle);
  --lh-status-active: var(--color-accent-blue);
  --lh-status-active-soft: var(--color-accent-blue-soft);
  --lh-status-success: var(--color-success);
  --lh-status-success-soft: var(--color-success-soft);
  --lh-status-warning: var(--color-warning);
  --lh-status-warning-soft: var(--color-warning-soft);
  --lh-status-danger: var(--color-danger);
  --lh-status-danger-soft: var(--color-danger-soft);
  --lh-status-ai: var(--color-info);
  --lh-status-ai-soft: var(--color-info-soft);
}
```

