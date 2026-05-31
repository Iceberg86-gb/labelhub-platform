# LabelHub Welcome Hero

`welcome-hero.svg` is the preferred welcome/login visual. It is a vector product mockup rather than a marketing illustration.

## Design Notes

- The left third stays quiet for future login copy.
- The main mockup combines Designer canvas and human review workflow scenes.
- AI pre-review appears as a weak evidence card and does not use final-decision styling.
- No readable text is embedded in the SVG.

## Theme

The SVG uses the same CSS variable names that will be defined in `tokens/`:

```tsx
import WelcomeHero from "./hero/welcome-hero.svg?react";

export function WelcomePanel() {
  return <WelcomeHero className="welcome-hero" aria-hidden />;
}
```

```css
.welcome-hero {
  width: min(760px, 100%);
  height: auto;
  display: block;
}
```

