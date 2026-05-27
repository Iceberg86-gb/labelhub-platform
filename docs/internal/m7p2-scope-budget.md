# M7-P2 Scope Budget

## Phase Character

M7-P2 is the second sub-phase of the M7 "approach perfection" track. It
targets industry-rubric sub-criterion 4.2, large-form rendering performance,
which the first-pass rubric review scored as a material frontend gap.

Baseline anchor:

- HEAD: `080e9da`
- Backend tests: `408 tests, 0 failures, 0 errors, 78 skipped`
- OpenAPI MD5: `b6a8344f2c7cc38db958eb333334ebd1`
- Migration count: `11`
- `humanpending.md` bracketed entries: `131`

M7-P2 is frontend-only. It does not change backend services, OpenAPI
contracts, database migrations, auth, audit/governance code, or submission
immutability semantics. It is an internal renderer-engineering phase: keep the
schema and answer-payload contracts stable while making large forms measurably
fast.

Primary rubric target:

- 4.2: large-form rendering performance, proposed `2/5 -> 5/5`

Secondary effects:

- 1.1: Designer/Renderer decoupling, proposed `4/5 -> 5/5` if the field
  registry/controller split lands cleanly.
- 1.4: form-engine maturity, proposed `2/5 -> 4/5` if the project chooses the
  custom-engine path and removes or documents the Formily phantom dependency.

## Baseline Evidence

The current large-form hot path is small but fully synchronous:

- `SchemaRenderer` renders every top-level field with `fields.map(...)` at
  `apps/web/src/features/labeling/SchemaRenderer.tsx:26-106`.
- A new per-field `handleFieldChange` closure is created inside that map at
  `SchemaRenderer.tsx:29-31`.
- Each change calls `setFieldValue(value, stableId, newValue)` where
  `setFieldValue` shallow-copies the whole `AnswerPayload` object at
  `apps/web/src/entities/submission/answerPayload.ts:14-16`.
- The six field renderers are plain function components, not `React.memo`
  components:
  - `DateFieldRenderer.tsx:5-20`
  - `FileUploadFieldRenderer.tsx:5-29`
  - `NestedObjectFieldRenderer.tsx:7-28`
  - `NumberFieldRenderer.tsx:5-22`
  - `SelectFieldRenderer.tsx:5-65`
  - `TextFieldRenderer.tsx:5-20`
- `FieldList` in the schema designer renders every field with `fields.map(...)`
  at `apps/web/src/features/schema-design/FieldList.tsx:60-69`, and uses
  `@dnd-kit/core` / `@dnd-kit/sortable` at `FieldList.tsx:3-18`.
- No `@tanstack/react-virtual` or `react-virtual` package is present in
  `apps/web/package.json`, `pnpm-lock.yaml`, or `apps/web/src`.
- No frontend test or benchmark files currently exist under `apps/web/src`.
  `apps/web/vite.config.ts` only configures Vite and the `/api` proxy.

Important precision: the wider frontend does use `useMemo` in list/detail
pages. The performance gap is specifically in the labeling renderer,
field-renderer chain, and schema-designer field list, not a project-wide
absence of memoization.

## Locked Goal

M7-P2 must produce three measurable outcomes:

1. Per-keystroke isolation: typing one character into one field of a 500-field
   form does not re-render sibling fields. Evidence comes from a render-count
   harness and React DevTools Profiler notes.
2. First-render performance: a 1000-field form renders under 200ms on the
   recorded baseline machine, or the verification doc honestly records why the
   threshold was missed and what improvement was measured.
3. Scroll smoothness: a 1000-field form scrolls without page-level jank. Evidence
   is visual plus frame-timing or benchmark output.

The "5/5" bar is not "we added virtualization"; it is the measured behavior
above.

## Phantom Dependency Decision

`apps/web/package.json:18-19` declares `@formily/core` and `@formily/react`.
However, `rg "@formily" apps/web/src` returns zero source usages. The resolved
pnpm install footprint on this machine is approximately `2.7M` via
`du -shL apps/web/node_modules/@formily`.

This is a phantom dependency: installed and locked, but not imported by the app.
The user must adjudicate before code starts:

| Path | Meaning | Estimate | Consequence |
|---|---|---:|---|
| A | Adopt Formily as the actual form engine | 2500-3500 lines | Highest risk. Requires remapping schema field types, validation, answer payloads, nested fields, and designer preview semantics. |
| B | Build equivalent capabilities on the existing custom renderer | ~1230 lines | Recommended. Keeps current schema/answer contracts and adds memoization, subscriptions, virtualization, and benchmarks. |
| C | Remove Formily dependency only | ~5 lines | Cleans dependency debt but does not improve performance. |

Recommended disposition: **B + C**. Keep the custom renderer, implement the
performance work, and remove the unused Formily dependencies in the same phase
after the user adjudicates that Formily will not be adopted.

## Three-Tier Plan

### Tier 1: Memoization And Stable Field Updates

Goal: make one field update touch only that field.

Planned components:

- `useFormController` hook in `apps/web/src/features/labeling/useFormController.ts`
  or similar.
- A small field-store/controller that keeps the latest `AnswerPayload` in a
  ref, exposes stable setters, and lets individual field views subscribe to
  their own stableId.
- `useSyncExternalStore` for field-level subscriptions if the spike confirms it
  is clean in React 18. Fallback is `useReducer` plus per-field memo props.
- `React.memo` wrappers for the six field renderers.
- No change to `AnswerPayload` shape or `schemaTypes.ts` interfaces.

### Tier 2: Virtualization

Goal: make a 1000-field form behave like a small form at first paint and scroll.

Planned components:

- Add `@tanstack/react-virtual` after the C1 spike confirms feasibility.
- Virtualize `SchemaRenderer` only when `fields.length > 50`; below 50, keep
  direct rendering because virtualization overhead can exceed the benefit.
- Virtualize only top-level `SchemaRenderer` fields. `NestedObjectFieldRenderer`
  remains in-line for M7-P2; recursive virtualization is out of scope.
- Spike `FieldList` virtualization with dnd-kit before implementing it, because
  dnd-kit and virtual lists both influence transforms/positioning.

### Tier 3: Benchmark And Evidence

Goal: make the performance story defensible with numbers.

Planned components:

- A frontend benchmark harness under `apps/web/src/features/labeling/__benchmarks__/`
  or a Vite-accessible dev-only benchmark page, depending on what the C1 spike
  finds about the missing frontend test infrastructure.
- Measurements for 100 / 500 / 1000 / 5000 fields.
- Measurements for initial render and single-field update cost.
- README or docs badge/table recording final measured values and the test
  machine.

## Allowed Surfaces

- `apps/web/package.json` and `pnpm-lock.yaml` for `@tanstack/react-virtual`,
  benchmark tooling if adjudicated, and optional Formily removal.
- `apps/web/src/features/labeling/SchemaRenderer.tsx`.
- `apps/web/src/features/labeling/field-renderers/*`.
- New labeling controller/benchmark helper files under
  `apps/web/src/features/labeling/`.
- `apps/web/src/features/schema-design/FieldList.tsx`.
- Minimal CSS changes in `apps/web/src/app/styles.css` for virtual list
  containers.
- README or a small M7-P2 benchmark evidence document if C5 chooses docs over a
  badge.
- Final M7-P2 verification docs, screenshot index, screenshots, and one
  `[M7-P2 resolved]` humanpending entry at closure.

## Forbidden Surfaces

- Any backend code, backend tests, OpenAPI YAML, generated API types, or
  migrations.
- `apps/web/src/entities/schema/schemaTypes.ts` type contract changes.
- `apps/web/src/entities/submission/answerPayload.ts` shape contract changes.
- Behavior changes in field renderers beyond render-control plumbing.
- Replacing the existing answer payload model with Formily runtime objects
  unless the user explicitly chooses Path A.
- New global state management libraries.
- Audit/governance code touched by M7-P1.
- Route/auth/sidebar changes unrelated to benchmark entry points explicitly
  approved for the phase.

## Budget

Default Path B + C budget:

| Cluster | Scope | Estimate |
|---|---|---:|
| C1 | Throwaway spike: react-virtual feasibility, dnd-kit coexistence POC, baseline numbers | 80 |
| C2 | Tier 1: form controller, field subscriptions, memoized renderers, remove Formily if adjudicated | 350 |
| C3 | Tier 2a: SchemaRenderer virtualization with threshold switch | 250 |
| C4 | Tier 2b: schema-designer FieldList virtualization with dnd-kit coexistence | 300 |
| C5 | Benchmark harness + README/docs evidence table | 200 |
| C6 | Manual visual regression fixtures/checklist across three paths and 1440/1280/1024 viewports | 50 |
| C7 | Verification doc + screenshots + humanpending | N/A |
| **Total** | Default code estimate | **1230** |

Hard cap: `1500` changed code lines for Path B + C.

Path A is a different phase shape and must be re-estimated before any code:
`2500-3500` changed lines, likely 8-10 implementation clusters, and a much
higher regression surface.

## Risk Register

| Risk | Resolution |
|---|---|
| `@tanstack/react-virtual` and dnd-kit transform/measurement models collide | C1 spike must prove a minimal sortable virtual list before C4. If it fails, C4 falls back to non-virtual FieldList and records the gap. |
| `useSyncExternalStore` adds complexity or behaves poorly with nested fields | C2 starts with top-level field subscription only; nested object fields stay in-line. Fall back to `useReducer` + memoized per-field props if needed. |
| Memoization hides validation updates | C2 tests and manual checks must change one field and one validation error and verify only affected field feedback updates. |
| Sub-50-field forms regress due to virtualization overhead | C3 threshold keeps direct rendering under or equal to 50 fields; benchmark must include 20 and 50 field cases. |
| Existing answer payload shallow-copy semantics are relied on by parent pages | C2 must preserve the external `onChange(nextPayload)` contract. Ref storage is internal only. |
| Benchmark numbers vary by machine | C5 records machine/OS/browser and reports both absolute numbers and relative improvement from C1 baseline. |
| No existing frontend test/benchmark infrastructure | C1 decides between adding Vitest, using a Vite benchmark page, or a Node/jsdom-free render harness. This is a gate, not an assumption. |
| Formily phantom dependency decision expands scope | If user chooses Path A, stop and rewrite M7-P2. If user chooses B + C, removal is dependency cleanup only. |

## Stop Conditions

- User does not adjudicate Path A/B/C before C1.
- C1 spike shows virtualized dnd-kit is fundamentally incompatible with current
  designer architecture.
- Any cluster exceeds its estimate by 50%.
- Cumulative code diff exceeds `1500` changed lines.
- Any backend, OpenAPI, migration, or backend test file changes.
- Any visual regression in labeling session, schema designer, or reviewer
  payload preview.
- C5 benchmark shows the optimized renderer is slower for small forms and no
  threshold can prevent that regression.
- M7-P1's three-viewport frontend sanity standard (1440 / 1280 / 1024) is not
  executed for C2-C6 frontend checkpoints.

## Verification Plan

After each code cluster:

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- `bash scripts/check-protected-endpoints.sh` only as an invariant check, not
  because M7-P2 changes OpenAPI.
- Verify OpenAPI MD5 remains `b6a8344f2c7cc38db958eb333334ebd1`.
- Verify migration count remains `11`.
- Verify backend diff is empty.

Frontend manual sanity after C2-C6:

- Labeler session form input path at 1440 / 1280 / 1024.
- Owner schema designer field add/reorder/edit/delete path at 1440 / 1280 /
  1024.
- Reviewer or owner read-only payload preview path at 1440 / 1280 / 1024.

Final C7:

- Verification doc with benchmark numbers, phantom dependency disposition, and
  C1 spike outcome.
- Screenshots for unchanged visual rendering and benchmark evidence.
- One `[M7-P2 resolved]` humanpending entry.

## Pre-Code Gate

No code may land until the user adjudicates:

1. Phantom dependency path: A adopt Formily, B custom equivalent, C remove only,
   or B + C.
2. Whether C1 spike code is throwaway-only or whether a committed benchmark
   harness skeleton is allowed.
3. Whether the 1500-line cap is acceptable given M7-P1's actual overrun pattern.
