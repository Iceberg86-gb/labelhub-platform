# LabelHub Empty-State Illustrations

Each empty-state illustration is an SVG with a `160 x 120` viewBox and a consistent `1.5` stroke weight.

## Files

- `empty-task-plaza.svg`
- `empty-workbench.svg`
- `empty-designer-canvas.svg`
- `empty-review-queue.svg`
- `empty-export-archive.svg`
- `empty-loading.svg`
- `empty-load-failed.svg`

## Theme Variables

All illustrations use `currentColor` plus CSS variables so React can theme the base stroke, muted stroke, soft surfaces, and accent details separately.

```css
.lh-empty-state {
  width: 128px;
  height: auto;
  color: var(--color-text-secondary);
  --lh-illustration-base: var(--color-text-secondary);
  --lh-illustration-muted: var(--color-text-tertiary);
  --lh-illustration-soft: var(--color-surface-subtle);
  --lh-illustration-accent: var(--color-accent-blue);
  --lh-illustration-success: var(--color-success);
  --lh-illustration-warning: var(--color-warning);
  --lh-illustration-danger: var(--color-danger);
}
```

```tsx
import EmptyDesignerCanvas from "./illustrations/empty-designer-canvas.svg?react";

export function DesignerEmptyState() {
  return <EmptyDesignerCanvas className="lh-empty-state" aria-hidden />;
}
```

