# LabelHub Design System (`shared/ui`)

Single source of truth for reusable UI. **New UI must be built from these components and tokens** — do not hand-roll one-off badges, panels, flow strips, empty states, or inline-styled surfaces.

## Import rule

Always import from the barrel, never from individual files:

```ts
import { StatusBadge, EmptyState, FlowStrip, StatTile, SectionCard, TruncatedHash, tokens } from '../../shared/ui';
```

## Tokens (the foundation)

Design tokens live in [`docs/design-assets/tokens/tokens.css`](../../../../../docs/design-assets/tokens/tokens.css) and are loaded globally in `app/main.tsx`. TS references are re-exported as `tokens` (`tokens.fontSize.caption`, `tokens.color.accentBlue`, …) for the rare inline-style case.

- **Type scale (never use raw px):** `display 28 · page-title 22 · section-title 18 · card-title 15 · body 14 · body-strong 14/600 · small 13 · caption 12 · table-header 12/600 · micro-label 11/600`.
- **Fonts:** `--font-family-sans` (Inter), `--font-family-mono` (one canonical mono stack — never inline a `"SF Mono", …` list).
- **Color:** use the semantic/surface/border/text tokens. No raw hex, no `rgba()` glows, no decorative gradients. Background tints come from `--color-*-soft`; status text from `--color-{success,warning,danger,info,accent-blue}`.

## Component catalogue

| Component | Use for | Replaces |
| --- | --- | --- |
| `StatusBadge` | status / semantic pills (`tone` = accent\|info\|success\|warning\|danger\|neutral) | `<Tag className="semantic-tag semantic-tag--X">` |
| `RoleBadge` | user role chips | `.role-badge--*` |
| `TruncatedHash` | hash display + copy | hand-written hash cells |
| `EmptyState` | empty / no-data surfaces (`variant` = block\|inline) | per-feature decorative empty panels |
| `StatTile` | icon + value + caption summary tiles | per-feature "status pill" tiles |
| `SectionCard` | bordered panel with title/subtitle/actions header | per-feature `*-panel` / `*-card` wrappers |
| `FlowStrip` | horizontal step / workflow strips | `review-flow-*`, `trusted-export-flow-*` |

App-shell layouts (`AppLayout`, `PublicLayout`) live in `shared/ui/` but are imported directly (not via the barrel) — they pull in routing + auth infra and are kept out of the lightweight primitive barrel so unit tests stay cheap.

## Authoring rules

1. **Prefer className + `styles.css` over inline `style={{}}`.** Inline styles bypass the token system; reserve them for truly dynamic values, and even then pull from `tokens`.
2. **Semantic colors come from tokens.** If you need a new tint, add a token in `tokens.css` — do not hardcode hex / rgba.
3. **One font size = one token.** No raw `font-size: NNpx` in `styles.css`.
4. **Adding a component:** put it in `shared/ui/`, export it from `index.ts`, add a row above, and write a render test under `pages/**` (vitest `include` covers `pages/**` and `shared/**`).
5. **Migrating a page:** replace hand-written badges/panels/flow/empty with the components above; delete the now-dead BEM CSS.

Run `/design-system-review` (project skill) to audit compliance before committing UI changes.
