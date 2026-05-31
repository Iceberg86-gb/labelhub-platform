# LabelHub Design Assets

This directory contains the implementation-ready design assets for LabelHub's React frontend.

## Structure

- `icons/` — hand-authored 24px outline SVG icons using `currentColor`.
- `illustrations/` — empty-state SVG illustrations using themeable CSS variables.
- `status/` — review workflow and status tag SVG assets.
- `hero/` — vector welcome/login hero visual.
- `tokens/` — CSS variables and TS token object.
- `imagegen-2026-05-31/` — archived approved image-generation motherboards for visual reference.

## Recommended App Setup

Import the tokens once at the application root:

```ts
import "./docs/design-assets/tokens/tokens.css";
```

For Vite/SVGR-style SVG components:

```tsx
import TaskIcon from "./docs/design-assets/icons/icon-task.svg?react";
import EmptyDesignerCanvas from "./docs/design-assets/illustrations/empty-designer-canvas.svg?react";
import WelcomeHero from "./docs/design-assets/hero/welcome-hero.svg?react";

export function Example() {
  return (
    <section>
      <TaskIcon className="lh-icon" aria-hidden />
      <EmptyDesignerCanvas className="lh-empty-state" aria-hidden />
      <WelcomeHero className="welcome-hero" aria-hidden />
    </section>
  );
}
```

```css
.lh-icon {
  width: 18px;
  height: 18px;
  color: var(--color-text-secondary);
}

.is-active .lh-icon .icon-accent {
  color: var(--color-accent-blue);
}

.lh-empty-state {
  width: 128px;
  height: auto;
  --lh-illustration-base: var(--color-text-secondary);
  --lh-illustration-muted: var(--color-text-tertiary);
  --lh-illustration-soft: var(--color-surface-subtle);
  --lh-illustration-accent: var(--color-accent-blue);
  --lh-illustration-success: var(--color-success);
  --lh-illustration-warning: var(--color-warning);
  --lh-illustration-danger: var(--color-danger);
}

.welcome-hero {
  width: min(760px, 100%);
  height: auto;
}
```

## Product Principle

AI pre-review assets are intentionally lower contrast and neutral. Human review and final manual decision states carry stronger visual hierarchy.

