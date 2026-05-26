# M6-P6b1 Scope Budget

## Theme

Global UI primitives for the M6-P6 defense-path polish pass.

> P6b1 locks primitives; P6b2 proves them on evidence surfaces.

P6b1 implements the shared UI building blocks that P6b2 will reuse for
page-level evidence polish. It intentionally avoids schema, submission,
reviewer, export, AI drawer, and owner-setup page polish.

## Scope

P6b1 covers only the global P0 items from `docs/internal/m6p6-ui-audit.md`:

| Audit ID | Deliverable | Integration allowed in P6b1 |
|----------|-------------|-----------------------------|
| G-P0 #1 | Header logo subtitle fix | Existing app shell header only |
| G-P0 #3 | `<RoleBadge>` | Existing app shell header only |
| G-P0 #4 | Typography scale | Layout/global stylesheet baseline only |
| G-P0 #2 | `<TruncatedHash>` | Scaffold only, no page integration |

## Strict Constraints

Allowed:

- New frontend primitive files under `apps/web/src/shared/ui/`.
- Minimal integration in `apps/web/src/shared/ui/AppLayout.tsx`.
- Minimal global style additions in `apps/web/src/app/styles.css`.
- P6b1 documentation under `docs/internal/`.
- `humanpending.md` update in the final P6b1 docs commit.

Forbidden:

- Any backend code change.
- Any OpenAPI change.
- Any migration.
- Any frontend test file.
- Any page-level polish under schema, submission, export, AI drawer, reviewer,
  or owner-setup pages.
- Any `<TruncatedHash>` import outside its own component file.
- Any new route, fetch, API consumer, or rendered field.
- Any Semi Design global theme token change.
- Any screenshot capture by the agent.

## P6b1 Primitive Boundaries

### Header Logo Subtitle

The broken subtitle is fixed at the shell header. P6b1 may change markup or
CSS inside the existing app shell only. If the root cause requires font loading
or a non-CSS asset change, implementation must stop for user adjudication.

### RoleBadge

`<RoleBadge>` owns role color semantics:

- Owner: purple
- Labeler: blue
- Reviewer: amber

The header may replace the current raw Semi `Tag` role render with
`<RoleBadge>`. Placeholder pages and other page-level role tags remain out of
scope for P6b1.

### Typography Scale

P6b1 defines local typography variables/classes in the existing stylesheet,
not Semi theme tokens:

- H1: 28px
- H2: 22px
- H3: 18px
- body: 14px
- caption: 12px

Only the layout/global baseline may be touched. Hardcoded page-specific
typography stays for P6b2 or later phases.

### TruncatedHash

P6b1 adds the component scaffold only:

- monospace text
- fixed truncation
- tooltip with full hash
- copy-to-clipboard icon affordance

The component is not imported by any page in P6b1. Actual hash replacements
for `#7`, `#12`, `#27`, `#32`, and `#37` are reserved for P6b2.

## Line Budget

| Cluster | Soft estimate | Hard cap |
|---------|---------------|----------|
| Logo subtitle fix | ~10 lines | 30 lines |
| RoleBadge component + header integration | ~40-50 lines | 80 lines |
| Typography variables + baseline | ~50 lines | 80 lines |
| TruncatedHash scaffold | ~80 lines | 120 lines |

P6b1 frontend diff hard cap: **300 lines**.

If cumulative frontend diff approaches 250 lines before all primitives are
done, stop and report. If it exceeds 300 lines, stop hard.

## Verification

Code verification:

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- OpenAPI MD5 remains `c042f8bc62a15efd98bd01363b9e14ff`
- Migration count remains 10
- `grep -rn "TruncatedHash" apps/web/src/` returns only the new component file

Visual verification is not performed by the agent. The user captures a small
after-screenshot set after P6b1 so the reviewer can inspect logo, role badge,
and typography changes.

## Commit Boundary

Expected sequence:

1. `docs: scope M6-P6b1 global primitives and pre-estimate`
2. `fix: repair header logo subtitle render`
3. `feat: add typography scale CSS variables and layout baseline`
4. `feat: add RoleBadge component`
5. `refactor: integrate RoleBadge into app shell header`
6. `feat: add TruncatedHash component scaffold`
7. `docs: M6-P6b1 verification and humanpending update`

This pre-estimate commit is a gate. No code should be written until the user
accepts the file list and line estimates.
