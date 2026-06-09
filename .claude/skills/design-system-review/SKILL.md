---
name: design-system-review
description: Audit apps/web UI for Design System compliance — flags raw px font-sizes, hardcoded/garish colors (hex/rgba glows, decorative gradients), inline styles that bypass tokens, and hand-written components (badges, flow strips, empty states, panels) that should use shared/ui. Use when reviewing UI changes, before committing front-end work, or when asked to "check design system / DS compliance / UI consistency".
---

# Design System review

Audit the LabelHub web app (`apps/web`) against the Design System defined in
`apps/web/src/shared/ui/README.md` and the tokens in `docs/design-assets/tokens/tokens.css`.
Report violations with file:line and a concrete fix. Do **not** auto-fix unless asked.

## Scope

By default review the working-tree diff (`git diff --name-only` + staged). If asked for a full
audit, scan all of `apps/web/src`. Focus on `.tsx` and `apps/web/src/app/styles.css`.

## Checks (run these, report every hit)

Run from `apps/web/src`.

1. **Raw font-size (must use the type-scale tokens)** — any literal px is a violation:
   ```bash
   grep -nE "font-size:\s*[0-9]" app/styles.css
   grep -rnE "fontSize:\s*['\"]?[0-9]" --include="*.tsx" .
   ```
   Fix: map to the nearest `var(--font-size-*)` token (display 28 / page-title 22 / section-title 18 / card-title 15 / body 14 / small 13 / caption 12 / micro-label 11).

2. **Hardcoded / garish colors** — raw hex/rgba, decorative gradients, glow orbs:
   ```bash
   grep -nE "#[0-9a-fA-F]{3,8}\b|rgba?\(" app/styles.css | grep -v "var(--"
   grep -nE "radial-gradient|linear-gradient" app/styles.css
   grep -rnE "color:\s*['\"]#|background:\s*['\"]#" --include="*.tsx" .
   ```
   Fix: use `--color-*` tokens (`*-soft` for tints, semantic for text). Remove `radial-gradient` glow orbs and decorative blue/neon gradients — these are the "low-level AI" look. Functional canvas grids (schema designer dot patterns) are allowed; note them but don't flag as garish.

3. **Non-canonical mono font** — must be the token:
   ```bash
   grep -nE "monospace" app/styles.css | grep -v "var(--font-family-mono)"
   ```

4. **Inline styles that bypass tokens**:
   ```bash
   grep -rn "style={{" --include="*.tsx" . | grep -vE "node_modules"
   ```
   Fix: move to a className in `styles.css`; for truly dynamic values pull from `shared/ui` `tokens`.

5. **Hand-written components that should use `shared/ui`**:
   ```bash
   grep -rn "semantic-tag semantic-tag--" --include="*.tsx" .   # -> <StatusBadge tone=...>
   grep -rnE "flow-strip|flow-node|flow-step" --include="*.tsx" . # -> <FlowStrip steps=...>
   grep -rnE "<Empty\b" --include="*.tsx" .                       # -> <EmptyState ...>
   grep -rnE "role-badge--" --include="*.tsx" .                   # -> <RoleBadge role=...>
   ```
   Any direct usage outside `shared/ui/` is a violation — replace with the component.

6. **New shared component not exported from the barrel**:
   ```bash
   ls shared/ui/*.tsx | grep -vE "\.test\." 
   grep -n "export" shared/ui/index.ts
   ```
   Every reusable component under `shared/ui/` must be re-exported from `shared/ui/index.ts`
   (except app-shell layouts, which are imported directly by design).

## Report format

Group findings by check. For each: `file:line` · the offending snippet · the one-line fix.
End with: a count per category, and a verdict — `PASS` (no violations in scope) or
`N violations` with the top 3 to fix first. If asked, after reporting run:
```bash
cd apps/web && pnpm typecheck && pnpm test
```
to confirm the current tree is green before/after fixes.
