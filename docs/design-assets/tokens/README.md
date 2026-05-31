# LabelHub Design Tokens

These tokens encode the approved LabelHub design language: minimal light surfaces, cool gray-green sidebar gradient, restrained blue accents, black primary actions, low-saturation semantic colors, soft radii, and quiet typography.

## CSS Variables

Import `tokens.css` once in the app shell or global stylesheet.

```css
@import "./design-assets/tokens/tokens.css";

body {
  margin: 0;
  font-family: var(--font-family-sans);
  color: var(--color-text-primary);
  background: var(--color-app-background);
}

.app-sidebar {
  background: var(--color-sidebar-gradient);
}

.primary-button {
  height: 40px;
  border: 0;
  border-radius: var(--radius-button-default);
  color: var(--color-text-inverse);
  background: var(--color-primary-black);
}

.primary-button:hover {
  background: var(--color-primary-black-hover);
}
```

## TS Object

Use `tokens.ts` when styling through React, CSS-in-JS, chart config, canvas, or any place where CSS variables are inconvenient.

```ts
import { tokens } from "./design-assets/tokens/tokens";

export const navItemStyle = {
  borderRadius: tokens.radius.buttonDefault,
  color: tokens.color.text.secondary,
  background: tokens.color.background.hoverSurface,
};
```

## Exact Value Source

The values in `tokens.css` and `tokens.ts` are kept in sync with the approved first-part design specification. The sidebar gradient tokens are:

- `#F7F9F8`
- `#F1F5F3`
- `#ECF2EF`

