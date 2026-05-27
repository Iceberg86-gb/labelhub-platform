# M7-P2 Pre-Estimate

## Status

Pre-estimate gate only. No code lands until user adjudication.

Baseline anchor:

- HEAD: `080e9da`
- Backend tests: `408 / 78`
- OpenAPI MD5: `b6a8344f2c7cc38db958eb333334ebd1`
- Migration count: `11`
- `humanpending.md` bracketed entries: `131`

M7-P2 is frontend-only unless the user chooses the Formily adoption path, which
would require a rewritten estimate.

## Phantom Dependency Verification

Evidence:

- `apps/web/package.json:18-19` declares `@formily/core` and `@formily/react`.
- `rg "@formily" apps/web/src` returns zero results.
- `pnpm-lock.yaml` resolves Formily packages including `@formily/core`,
  `@formily/react`, `@formily/json-schema`, `@formily/reactive`,
  `@formily/reactive-react`, `@formily/shared`, and `@formily/validator`.
- `du -shL apps/web/node_modules/@formily` reports approximately `2.7M` on this
  workspace.

Conclusion: Formily is installed but unused. This changes the earlier rubric
interpretation from "Formily absent" to "Formily is a phantom dependency".
That is better than missing package availability, but worse than an intentional
form-engine choice because the runtime dependency is not buying product
capability.

User adjudication request:

- Path A: adopt Formily as the real form engine.
- Path B: build Formily-equivalent capabilities on the existing custom renderer.
- Path C: remove Formily dependency only.
- Recommended: Path B + C. Extend the custom renderer and remove unused Formily
  dependencies in the implementation phase.

## Renderer Architecture Analysis

### `SchemaRenderer`

File: `apps/web/src/features/labeling/SchemaRenderer.tsx`.

Findings:

1. Full-tree render: `fields.map(...)` at `SchemaRenderer.tsx:26-106` renders
   every field every time the parent renders.
2. Inline change closure: `handleFieldChange` is created inside the map at
   `SchemaRenderer.tsx:29-31`, so even if field renderers are wrapped in
   `React.memo`, the `onChange` prop identity changes on every parent render.
3. Value lookup in map: `getFieldValue(value, field.stableId)` at
   `SchemaRenderer.tsx:27` means the parent is responsible for slicing every
   field value.
4. Error lookup in map: `errors?.get(field.stableId)` at
   `SchemaRenderer.tsx:28` is cheap alone, but still part of the full-tree pass.
5. No virtual window: there is no threshold or viewport-aware rendering.

Mitigation:

- C2 moves value and update handling behind a form controller that provides
  stable per-field subscriptions.
- C2 memoizes field renderers.
- C3 virtualizes the top-level field list for large schemas.

### `AnswerPayload`

File: `apps/web/src/entities/submission/answerPayload.ts`.

`setFieldValue` returns `{ ...payload, [stableId]: value }` at
`answerPayload.ts:14-16`. This is correct for immutability, but with the current
renderer it also changes the parent `value` object identity for every keystroke,
forcing the top-level renderer to revisit all fields.

Mitigation:

- Preserve this external contract.
- Internally hold latest payload in a ref/store so child field updates can avoid
  sibling re-renders.
- Continue emitting full immutable `AnswerPayload` objects to existing parent
  pages.

### Field Renderers

Plain function components:

- `DateFieldRenderer.tsx:5-20`
- `FileUploadFieldRenderer.tsx:5-29`
- `NestedObjectFieldRenderer.tsx:7-28`
- `NumberFieldRenderer.tsx:5-22`
- `SelectFieldRenderer.tsx:5-65`
- `TextFieldRenderer.tsx:5-20`

They are good memo candidates because they receive explicit props:
`field`, `value`, `onChange`, `readOnly`, `errors`.

Special cases:

- `NestedObjectFieldRenderer.tsx:19-24` recursively renders `SchemaRenderer`.
  M7-P2 virtualizes top-level only; nested objects remain in-line.
- `NumberFieldRenderer.tsx:14-17` and `SelectFieldRenderer.tsx:46-48` create
  local normalization closures. These are inside memoized components and are
  acceptable unless profiling shows they are hot.

### `FieldList`

File: `apps/web/src/features/schema-design/FieldList.tsx`.

Findings:

1. dnd-kit is used through `DndContext`, `SortableContext`, and `useSortable`
   at `FieldList.tsx:3-18` and `FieldList.tsx:57-72`.
2. Full list render uses `fields.map(...)` at `FieldList.tsx:60-69`.
3. `SortableFieldItem` calculates transform style from dnd-kit transform at
   `FieldList.tsx:85-89`.

Risk:

- Virtualization also controls measurement/positioning, while dnd-kit controls
  drag transform. C1 must spike this before C4.

## Three-Tier Optimization Plan

### Tier 1: Memoization + Stable Callbacks

Cluster: C2.

Design:

- Add `useFormController` with a stable public API:
  - `getValue(stableId)`
  - `setValue(stableId, value)`
  - `subscribe(stableId, listener)`
  - `getSnapshot(stableId)`
  - `flushPayload()` or internal equivalent for parent `onChange`
- Use `useSyncExternalStore` for per-field subscriptions if C1 validates the
  shape. This is a React 18 standard API and avoids ad-hoc event emitter
  behavior.
- Keep `SchemaRendererProps` externally compatible.
- Convert current switch body into a small `FieldRendererSlot` that subscribes
  to one field and selects the right renderer.
- Wrap the six field renderers in `React.memo`.

Acceptance criteria:

- A render-count test/harness proves changing field `A` does not re-render field
  `B` in a 500-field schema.
- Existing labeler input behavior is unchanged.
- Validation errors still update for the affected field.

### Tier 2a: Labeling Renderer Virtualization

Cluster: C3.

Design:

- Add `@tanstack/react-virtual` only after C1 confirms viability.
- Virtualize top-level `SchemaRenderer` fields when `fields.length > 50`.
- Keep direct render for `fields.length <= 50`.
- Use estimated row height plus measurement if the library supports it cleanly
  with Semi UI form controls.

Acceptance criteria:

- 1000-field first render is under the target threshold or improves by the
  measured percentage recorded in C5.
- No visual difference for normal schemas under 50 fields.
- Nested objects still render correctly.

### Tier 2b: Designer FieldList Virtualization

Cluster: C4.

Design:

- Use C1 spike result to choose one:
  1. Full dnd-kit + react-virtual integration.
  2. Non-virtual FieldList retained, with a documented reason and possible
     lightweight windowing fallback.
- Keep `FieldList` public props stable.
- Do not change schema field ordering semantics.

Acceptance criteria:

- Add, select, delete, and drag reorder still work.
- Existing validation error highlighting and selected state remain visible.
- No page-level horizontal or vertical layout regression at 1440 / 1280 / 1024.

### Tier 3: Benchmark Evidence

Cluster: C5.

Design:

- C1 determines whether the repo should add Vitest or use a Vite benchmark page.
  There is currently no frontend test runner configured in `apps/web/package.json`.
- Benchmark scenarios:
  - initial render: 20 / 50 / 100 / 500 / 1000 / 5000 fields
  - single-field update: 100 / 500 / 1000 fields
  - virtualized scroll/frame timing for 1000 fields
- Store the numbers in a durable README section or M7-P2 benchmark document.

Acceptance criteria:

- The benchmark can be rerun with a documented command.
- The verification doc records machine/browser/OS.
- Numbers include both baseline and optimized results, not only final results.

## Per-Cluster Estimate

Default path: B + C.

| Cluster | Scope | Estimate |
|---|---|---:|
| C1 | Throwaway spike: baseline measurements, `@tanstack/react-virtual` feasibility, dnd-kit coexistence POC | 80 |
| C2 | Tier 1: `useFormController`, field subscriptions, memoized renderers, optional Formily dependency removal | 350 |
| C3 | Tier 2a: `SchemaRenderer` virtualization with threshold switch | 250 |
| C4 | Tier 2b: `FieldList` virtualization or adjudicated fallback after spike | 300 |
| C5 | Benchmark harness + README/docs evidence table | 200 |
| C6 | Visual regression fixtures/checklist and three-viewport evidence | 50 |
| C7 | Verification doc + screenshots + humanpending | N/A |
| **Total** | Default code estimate | **1230** |

Hard cap: `1500` changed code lines.

Path A estimate: `2500-3500` changed lines and a new architecture plan.
Path C only estimate: about `5` changed lines plus lockfile churn.

## C1 Spike Design

C1 is explicitly a spike gate, not production code.

Tasks:

1. Generate a synthetic schema with 20 / 50 / 100 / 500 / 1000 / 5000 fields.
2. Measure current `SchemaRenderer` first render and one-field update cost.
3. Install or temporarily link `@tanstack/react-virtual` in the working tree.
4. Build a minimal local POC for top-level SchemaRenderer virtualization.
5. Build a minimal local POC for dnd-kit + virtualized FieldList.
6. Report the numbers and compatibility result.
7. Revert throwaway spike code unless the user explicitly authorizes a committed
   benchmark skeleton.

Deliverable:

- Chat report plus, if user approves, a small pre-estimate appendix update.
- No production commit from C1 by default.

Stop if:

- dnd-kit + virtual list cannot preserve reorder semantics.
- benchmark tooling requires a large dependency decision not covered by this
  pre-estimate.

## Risk Register

| Risk | Resolution |
|---|---|
| Formily adoption looks tempting because the package already exists | Treat as Path A and re-estimate. Installed dependency alone is not architecture. |
| `useSyncExternalStore` creates too much code complexity | Fall back to `useReducer` with memoized per-field props if the C2 spike fails. |
| Field renderers memoize incorrectly because `field` objects are recreated | C2 must verify schema field object identity in parent flows; if identity is unstable, add per-field stable selectors keyed by `stableId`. |
| Validation errors do not refresh after field-level subscription | Include error snapshot subscription or pass per-field error arrays with stable identity. |
| Virtual rows have variable height due labels, errors, nested objects, warning text | Use measurement support; if unstable, virtualize only flat simple fields and record nested-object limitation. |
| dnd-kit collision detection ignores off-screen virtual rows | C1 must prove or reject virtualized designer drag. If rejected, C4 does not force it. |
| Benchmark runner is missing | Add minimal tooling only after adjudication; do not silently add Vitest just because the prompt mentioned it. |
| Removing Formily changes lockfile significantly | Accept if user chooses B + C; record lockfile churn separately in C2 report. |

## Stop Conditions

- User has not adjudicated Path A/B/C.
- C1 spike is inconclusive.
- C2 cannot preserve external `SchemaRendererProps` compatibility.
- C3 causes any visual delta for schemas under 50 fields.
- C4 loses drag reorder correctness.
- C5 cannot produce repeatable benchmark numbers.
- Frontend build or typecheck fails.
- Any backend/OpenAPI/migration file changes.
- Cumulative code diff exceeds `1500` lines.

## Verification Plan

After each implementation cluster:

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- OpenAPI MD5 check remains `b6a8344f2c7cc38db958eb333334ebd1`
- Migration count remains `11`
- `git diff <baseline>..HEAD --name-only | grep services/api` returns empty

Manual frontend sanity:

- 1440 / 1280 / 1024 viewport widths, following the M7-P1 Cluster 5b lesson.
- Labeler session data entry.
- Owner schema designer add/select/edit/delete/reorder.
- Reviewer or owner read-only payload display.

Benchmark verification:

- Baseline and optimized numbers stored with machine/browser metadata.
- The 20/50-field cases do not regress.
- The 500/1000/5000-field cases show the intended improvement or the phase
  stops for re-estimate.

## User Adjudication Checklist

Before C1 starts, user must decide:

1. Phantom dependency path: A, B, C, or B + C.
2. Whether C1 spike remains throwaway-only.
3. Whether `@tanstack/react-virtual` is acceptable as a new dependency if C1
   confirms feasibility.
4. Whether adding frontend benchmark tooling is acceptable if the current Vite
   app cannot support reliable measurement without it.
5. Whether the `1500` changed-line hard cap is acceptable given M7-P1's actual
   overrun history.
