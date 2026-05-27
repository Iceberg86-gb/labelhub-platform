# M7-P2 Scope Budget

## Status

This document supersedes the M7-P2 Path B+C scope-budget + pre-estimate
recorded in commit 76d61b2 (76d61b2 docs: scope M7-P2 form virtualization
and performance benchmark). The Path B+C plan is preserved in git history
as the auditor's original recommendation; user adjudication selected
Path A-II per rubric 1.4 "推荐栈: Formily + 拖拽库" interpreted as a
project hard requirement. The supersession is forward-recorded here so
future readers find both decision branches without retroactive edits.

Baseline anchor:

- HEAD before this rewrite: `c669cbd`
- Backend tests: `408 tests, 0 failures, 0 errors, 78 skipped`
- OpenAPI MD5: `b6a8344f2c7cc38db958eb333334ebd1`
- Migration count: `11`
- `humanpending.md` bracketed entries: `131`

## Phase Character

M7-P2 Path A-II targets two rubric criteria:

1. **Rubric 1.4 literal satisfaction**: Formily powers the runtime form
   renderer; dnd-kit remains the drag library for the schema designer.
2. **Rubric 4.2 from 2/5 to 5/5**: Formily reactive isolation replaces the
   custom memo/controller tier; `@tanstack/react-virtual` handles top-level
   large-field rendering at 50+ fields; Vitest bench records 100/500/1000/5000
   field performance.

Path A-II is not a full Designable adoption. It keeps LabelHub's existing
Semi + dnd-kit schema designer and introduces Formily in the runtime renderer.
That is the recommended interpretation of the rubric phrase "Formily + drag
library" after the Formily research commit `c669cbd`.

M7-P2 remains frontend-only. It does not modify backend services, OpenAPI,
database migrations, auth, M7-P1 audit/governance code, or M6 submission
immutability semantics.

## Locked Goal

Three measurable outcomes replace the Path B locked goals:

1. **Per-keystroke isolation**: typing into one field of a 500-field form
   re-renders only that field. Evidence: React DevTools Profiler and Vitest
   render-count bench.
2. **First render time**: a 1000-field form renders under 200ms on the baseline
   laptop. Evidence: Vitest bench results recorded with machine metadata.
3. **AnswerPayload round-trip stability**: trusted-export reproducibility from
   M6-P5 is unchanged for every field type. Evidence: round-trip equality tests
   for all six field types and nested objects.

The phase succeeds only if Formily adoption preserves existing LabelHub
contracts while delivering measured performance improvements.

## Path A-II Architecture Decision

From `docs/internal/m7p2-formily-research.md` Section 7, adopted by user
adjudication:

- Runtime renderer: Formily form + locally-defined Semi x-components.
- Designer: keep current dnd-kit schema designer; do not adopt `@designable/*`.
- Adapter layer: `SchemaField[] -> ISchema`, `AnswerPayload <-> Formily values`.
- Validation: `payloadValidation.ts` remains submit-time authority; Formily
  handles UI-level rules and live feedback.

Rationale:

- `@formily/semi` is rejected because latest `1.0.4-beta.1` peers lock Formily
  packages to `2.2.1`, while LabelHub resolves `2.3.7`.
- `@designable/react` is rejected because it is beta, peers React `16.x || 17.x`,
  and depends on AntD rather than Semi.
- Current dnd-kit designer already satisfies the drag-library half of rubric
  1.4 and already persists the `SchemaField[]` contract used by backend and
  trusted export flows.

## Dependencies

- `@formily/core@^2.3.7`: already resolved from `@formily/core@^2.3.2`; no new
  install required.
- `@formily/react@^2.3.7`: already resolved from `@formily/react@^2.3.2`; no new
  install required.
- `@formily/reactive@^2.3.7`: transitive dependency; already resolved.
- `@tanstack/react-virtual@^3.x`: new dependency accepted by user; expected to
  be small and industry-standard for React virtualization.
- `@formily/semi`: rejected because of peer-version conflict and beta status.
- `@designable/*`: rejected for M7-P2 Path A-II because of React 16/17 + AntD
  design-system incompatibility.
- Vitest bench tooling: accepted by user for benchmark evidence. Exact package
  delta lands in the cluster that introduces the bench harness.

## Forbidden Surfaces

- Modifying `SchemaField` / `SchemaDocument` type aliases in
  `apps/web/src/entities/schema/schemaTypes.ts`.
- Modifying `AnswerPayload` shape in
  `apps/web/src/entities/submission/answerPayload.ts`. Only adapters may touch
  Formily reactive state; persisted JSON shape remains unchanged.
- Any backend code, backend tests, OpenAPI YAML, generated API types, or
  database migrations.
- Modifying stableId-based field identity. M6-P3a/P3a-2 AI findings and Quality
  Ledger references depend on stableId continuity.
- Touching M6 audit log infrastructure or M7-P1 audit code.
- Removing existing custom `SchemaRenderer` before C7 parity confirmation. The
  old renderer remains a fallback path during C2-C6.
- Touching authorization, routing, session/login flows, or unrelated sidebar
  navigation.
- Adding Redux, Zustand, Jotai, or any new global state manager. Formily reactive
  state is scoped to the form runtime; surrounding page state stays in React.
- Introducing `@formily/semi`, `@designable/react`, AntD, or another design
  system in M7-P2.

## Allowed Surfaces

- New `apps/web/src/features/labeling/formily/` directory for all Formily
  runtime code.
- New `apps/web/src/features/labeling/formily/adapters/` for schema and value
  adapters.
- New `apps/web/src/features/labeling/formily/components/` for local Semi
  x-components.
- New `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`.
- New `apps/web/src/features/labeling/__benchmarks__/SchemaRenderer.bench.tsx`.
- Modify consumer pages that currently import `SchemaRenderer`, but only after
  C7 parity confirmation.
- Modify existing `SchemaRenderer.tsx` only to keep fallback compatibility or to
  share non-behavioral utilities; no early removal.
- Modify `apps/web/package.json` and `pnpm-lock.yaml` for
  `@tanstack/react-virtual`, Vitest bench tooling if needed, and no other
  dependency drift.
- Minimal `apps/web/src/app/styles.css` additions for Formily field frames,
  virtual containers, and benchmark display if needed.
- Final M7-P2 verification docs, screenshot index, screenshots, and one
  `[M7-P2 resolved]` humanpending entry in C8.

## Budget

| Tier | Cluster | Estimate |
|---|---|---:|
| Foundations | C1 PoC | throwaway, 180 lines not counted toward cap |
| Adapters | C2 | 500 |
| Renderer | C3 | 650 |
| Validation | C4 | 350 |
| Designer Preview | C5 | 300 |
| Performance | C6 | 450 |
| Regression | C7 | 250 |
| Docs | C8 | N/A code |
| **Code total** | | **2500** |

C1 is intentionally throwaway and uncounted because user adjudicated that PoC
code is not committed. Research doc `c669cbd` estimated `2680` lines including
C1; cap-tracked Path A-II code is therefore `2500` lines.

Soft cap: `3300` changed code lines.
Hard cap: `4000` changed code lines.

Any cluster that exceeds its estimate by 50% must stop before commit and report.
Any cumulative diff above the hard cap stops the phase for re-estimate.

## Phase Cluster Shape

### C1: Throwaway Formily PoC

- Temporarily add `@tanstack/react-virtual` in the working tree.
- Build a minimal Formily + local Semi Input integration in a throwaway route or
  sandbox file.
- Render a 100-field synthetic form.
- Prove `AnswerPayload -> Formily values -> AnswerPayload` round-trip without
  leaking reactive proxies.
- Validate `@tanstack/react-virtual` can coexist with Formily field subscription.
- Revert all throwaway code before any commit.
- Time-box: 4 hours maximum.

### C2: Core Adapters

- Implement `SchemaField[] -> ISchema` for all six field types and nested object
  recursion.
- Implement `AnswerPayload -> Formily initialValues` deep clone.
- Implement `Formily form.values -> AnswerPayload` snapshot extraction, unknown
  key dropping, stableId path preservation, and anti-proxy leakage guards.
- Implement local component registry and FieldFrame decorator skeleton.
- Include round-trip tests for all six field types and nested objects.

### C3: Formily Runtime Renderer

- Add `SchemaFormilyRenderer` with the same external props as `SchemaRenderer`.
- Implement six local Semi x-components: text, number, select, date, file upload
  text, nested object.
- Support read/write mode through the existing `readOnly` prop.
- Display existing `errors` prop and Formily UI-level validation feedback.
- Keep old `SchemaRenderer` intact.

### C4: Validation Migration

- Keep `payloadValidation.ts` as submit-time authority.
- Project subset UI rules into Formily: required, min/max, text length, pattern,
  and basic type guards.
- Document divergence points explicitly.
- Ensure Formily-accepted values still pass `payloadValidation.ts`.

### C5: Designer Preview Integration

- Keep dnd-kit Designer as source of `SchemaField[]`.
- Add Formily renderer preview against the current draft document.
- Do not adopt `@designable/*`.
- Verify designer add/select/edit/delete/reorder still drives preview correctly.

### C6: Virtualization + Benchmark

- Add top-level `@tanstack/react-virtual` layer to `SchemaFormilyRenderer`.
- Render normally for `fields.length <= 50`; virtualize for `fields.length > 50`.
- Nested objects render inline per research decision.
- Add Vitest bench for first render at 100/500/1000/5000 fields.
- Add single-field update isolation bench proving non-target fields do not
  re-render.
- Record benchmark results in durable docs.

### C7: Regression Coverage And Consumer Switch

- Manual regression across Labeler session input, Labeler submitted view, Owner
  AI review/read-only view, Reviewer read-only view, and Designer preview.
- Three viewport widths: 1440 / 1280 / 1024 per M7-P1 Cluster 5b lesson.
- Switch consumer pages from `SchemaRenderer` to `SchemaFormilyRenderer` only
  after parity is confirmed.
- Remove old `SchemaRenderer` only if parity is complete. If retained, add a C8
  humanpending watch note with the reason.

### C8: Verification Docs

- `docs/internal/m7p2-verification.md`.
- `docs/screenshots/m7p2-after-set/INDEX.md`.
- Screenshots and benchmark output evidence.
- One `[M7-P2 resolved]` humanpending entry.

## Risk Register

| Risk | Cluster | Resolution |
|---|---|---|
| C1 PoC reveals Vite + TS + React 18 + Formily incompatibility | C1 | Stop, report, and re-evaluate Path A feasibility with user. |
| Nested object recursive mapping breaks AnswerPayload round-trip | C2 | C2 includes nested-object round-trip test before commit. |
| Formily reactive proxy leaks into emitted AnswerPayload | C2/C3 | Adapter deep-clones or JSON-serializes at emit boundary; anti-leakage tests required. |
| stableId path breaks for nested objects | C2 | Test stableId path mapping against AI findings and Quality Ledger expectations. |
| Formily validation diverges from `payloadValidation.ts` authority | C4 | Keep `payloadValidation.ts` authoritative; Formily handles live UI feedback only. |
| dnd-kit designer + Formily preview synchronization becomes noisy | C5 | One-way data flow from designer state to preview; no preview-to-designer mutation. |
| `@tanstack/react-virtual` + Formily field subscriptions double-render | C6 | C1 validates; fallback is row-group virtualization if field-level virtualization misbehaves. |
| Benchmark numbers vary by hardware | C6 | Record machine metadata and relative improvement alongside absolute ms. |
| C7 reveals visual regression in any of five surfaces | C7 | Block consumer switch; add Cluster 7b hot-fix if needed. |
| Old `SchemaRenderer` must remain after C7 | C7/C8 | Accept with `[M7-P2 watch]` humanpending entry and explicit reason. |

## Stop Conditions

- C1 exceeds 4 hours without a working minimal Formily render + AnswerPayload
  round-trip.
- C1 hits any PoC stop condition.
- Any cluster exceeds estimate by 50%.
- Cumulative diff exceeds 4000 changed code lines.
- Backend tests count changes from 408, indicating forbidden backend touch.
- OpenAPI MD5 changes from `b6a8344f2c7cc38db958eb333334ebd1`.
- Migration count changes from 11.
- `humanpending.md` changes before C8.
- Any M6 closed contract appears silently broken.
- Any consumer page switches to Formily renderer before C7 parity confirmation.

## Verification Plan

Per cluster:

- `pnpm --filter @labelhub/web typecheck` exit 0.
- `pnpm --filter @labelhub/web build` exit 0.
- `bash scripts/check-protected-endpoints.sh` exit 0.
- Verify OpenAPI MD5 unchanged.
- Verify migration count unchanged.
- Verify backend diff empty.
- Three-viewport manual sanity: 1440 / 1280 / 1024.

Specific gates:

- After C2: round-trip equality test passes for all six field types and nested
  objects.
- After C3: old and new renderer render the same fixture form in read/write and
  read-only modes.
- After C4: Formily UI validation and `payloadValidation.ts` authority are both
  exercised.
- After C6: benchmark numbers recorded and compared with C1 baseline.
- After C7: five-surface regression manual sanity passes before consumer switch.
- After C8: verification doc + screenshots + humanpending entry land, no code
  changes.

## User Adjudication State

All five implementation-shaping decisions are already resolved in favor of the
recommendation:

1. Option II: runtime Formily + retained dnd-kit designer.
2. Local Semi x-components: reject `@formily/semi`.
3. C1 PoC is throwaway.
4. `@tanstack/react-virtual` accepted.
5. Vitest bench accepted; soft cap 3300, hard cap 4000.

No additional gate is expected before C1 unless the auditor rejects this revised
cluster breakdown.
