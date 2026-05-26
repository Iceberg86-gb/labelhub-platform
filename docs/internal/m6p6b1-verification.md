# M6-P6b1 Verification

Baseline: `a52f084` (M6-P6a approved).

Head before final docs commit: `3ec160a` (`feat: add TruncatedHash component scaffold`).

## Scope

M6-P6b1 implemented the global primitives needed before page-level UI polish:

- G-P0 #1: header logo subtitle contrast repair.
- G-P0 #4: local typography scale variables and layout baseline.
- G-P0 #3: `RoleBadge` component and shell-header integration.
- G-P0 #2: `TruncatedHash` scaffold only, with no page imports.

No schema, submission, reviewer, export, AI drawer, owner-setup page, backend,
OpenAPI, migration, or test file was changed.

## Commit Sequence

| Commit | Purpose |
|--------|---------|
| `c9dc854` | `docs: scope M6-P6b1 global primitives and pre-estimate` |
| `41096fa` | `fix: repair header logo subtitle render` |
| `cd76f12` | `feat: add typography scale CSS variables and layout baseline` |
| `0313c26` | `feat: add RoleBadge component` |
| `fed07d7` | `refactor: integrate RoleBadge into app shell header` |
| `3ec160a` | `feat: add TruncatedHash component scaffold` |

## Audit Wording Drift

M6-P6a audit text described the faint header subtitle as
`AI 智能化标注管理系统` or similar because the screenshot showed only pixel
fragments. The actual code literal in
`apps/web/src/shared/ui/AppLayout.tsx` is `AI 监督信号治理系统`.

This drift was handled forward in commit `41096fa`; the P6a audit and P6b1
pre-estimate remain unchanged as audit-time snapshots.

## Header Subtitle Root Cause Record

Actual subtitle text:

- `AI 监督信号治理系统`

Audit wording drift:

- The audit wording was a screenshot-pixel guess; the actual literal differs.

Root cause:

- The subtitle used Semi `Typography.Text type="tertiary"`, which resolves to
  `.semi-typography-tertiary` / `var(--semi-color-text-2)`.
- Semi's light-theme tertiary text token is a dark translucent color intended
  for light backgrounds.
- On `.app-header` (`#111827`), the token produced low contrast and rendered as
  faint fragments.

Fix:

- Removed the tertiary text prop.
- Added a local `.brand-subtitle` color override in `apps/web/src/app/styles.css`.
- No Semi theme token, font asset, page-level file, or route was changed.

## Line Budget

P6b1 frontend hard cap: 300 changed lines under `apps/web/src/`.

Actual frontend diff against `a52f084`:

```text
4 files changed, 179 insertions(+), 3 deletions(-)
```

Changed frontend lines: 182 / 300.

| Primitive | Pre-estimate | Actual | Delta |
|-----------|--------------|--------|-------|
| Header logo subtitle | 10-20 lines | 6 changed lines | Lower than estimate; contrast fix was local. |
| Typography scale | 35-55 lines | 27 insertions | Lower than estimate; layout baseline did not need page rewrites. |
| RoleBadge component + integration | 45-70 lines | 58 changed lines | Within estimate. |
| TruncatedHash scaffold | 75-110 lines | 91 insertions | Within estimate. |

No primitive exceeded its cluster cap. The cumulative frontend diff stayed below
the 250-line pause threshold and the 300-line hard cap.

## Verification Commands

### Frontend Typecheck

Command:

```bash
pnpm --filter @labelhub/web typecheck
```

Result:

- Exit code: 0.
- `openapi-typescript` regenerated the local generated schema from the unchanged
  OpenAPI contract.

### Frontend Build

Command:

```bash
pnpm --filter @labelhub/web build
```

Result:

- Exit code: 0.
- Vite completed with the existing large-chunk warning:
  `Some chunks are larger than 500 kB after minification`.

### Contract And Migration Invariants

OpenAPI MD5:

```text
MD5 (packages/contracts/openapi/labelhub.yaml) = c042f8bc62a15efd98bd01363b9e14ff
```

Migration count:

```text
10
```

### TruncatedHash Isolation

Command:

```bash
grep -rn "TruncatedHash" apps/web/src/
```

Result:

```text
apps/web/src/shared/ui/TruncatedHash.tsx:4:type TruncatedHashProps = {
apps/web/src/shared/ui/TruncatedHash.tsx:20:export function TruncatedHash({
apps/web/src/shared/ui/TruncatedHash.tsx:26:}: TruncatedHashProps) {
```

`TruncatedHash` exists only in its own scaffold file. It is not imported by any
schema, submission, export, AI drawer, reviewer, owner setup, or shell page.

## No-Touch Confirmation

`git diff a52f084..HEAD --name-only -- services/api services/agent packages/contracts db/migration services/api/src/main/resources/db/migration apps/web/src/test`
returned no paths.

P6b1 did not touch:

- `services/api/`
- `services/agent/`
- `packages/contracts/`
- `db/migration/`
- `services/api/src/main/resources/db/migration/`
- `apps/web/src/test/`

## Visual Verification D-口径

The agent did not start a browser, run Playwright, or capture screenshots in
P6b1. This is intentional: P6b1 visual verification is reserved for the user
after the code-level checks complete. The user should capture a lightweight
after-screenshot set for representative Owner, Labeler, Reviewer, and Export
screens before P6b2 page-level polish begins.

## Next Phase

M6-P6b2 can start from the locked P6a P0 list. P6b2 owns page-level evidence
surfaces and will integrate `TruncatedHash` at the audited hash render sites
(`#7`, `#12`, `#27`, `#32`, `#37`).
