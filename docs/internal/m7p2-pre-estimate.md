# M7-P2 Pre-Estimate

## Status

This document supersedes the M7-P2 Path B+C scope-budget + pre-estimate
recorded in commit 76d61b2 (76d61b2 docs: scope M7-P2 form virtualization
and performance benchmark). The Path B+C plan is preserved in git history
as the auditor's original recommendation; user adjudication selected
Path A-II per rubric 1.4 "推荐栈: Formily + 拖拽库" interpreted as a
project hard requirement. The supersession is forward-recorded here so
future readers find both decision branches without retroactive edits.

Pre-estimate gate for Path A-II. Awaiting auditor confirmation of the
cluster-level breakdown and risk register before C1 spike begins.

Baseline anchor:

- HEAD before this rewrite: `c669cbd`
- Backend tests: `408 / 78`
- OpenAPI MD5: `b6a8344f2c7cc38db958eb333334ebd1`
- Migration count: `11`
- `humanpending.md` bracketed entries: `131`

## Path A Adjudication Trail

M7-P2 Path A adjudication recorded by the user:

```
M7-P2 Path A adjudication recorded by the user:

1. Path A locked, with rubric 1.4 截图 cited as evidence that
   "Formily + drag library" is a project hard requirement.
2. Auditor's Path B+C push-back was reviewed and rejected; user
   re-confirmed Path A regardless of cost (~2.5x scope vs Path B).
3. Option II selected over Option I: runtime Formily + retained
   dnd-kit designer. Designable + AntD route REJECTED on React
   18 + Semi incompatibility grounds.
4. @formily/semi REJECTED: peer-version conflict with project's
   @formily/core@2.3.7 (semi binding peers locked to 2.2.1).
5. Local Semi x-components ACCEPTED as the binding strategy.
6. C1 PoC is throwaway: baseline numbers update this doc, no
   production code commits from C1.
7. @tanstack/react-virtual@^3.x ACCEPTED as new dependency
   (~5KB gzipped, industry standard).
8. Vitest bench ACCEPTED for benchmark tooling (existing dep,
   no new framework).
9. Soft cap: 3300 changed code lines. Hard cap: 4000.
```

Note: the user described these as 5 decisions, with one repeated item in the
chat. This pre-estimate expands them into the 9 concrete implementation
constraints above so each downstream cluster has unambiguous guidance.

## Compatibility Evidence Recap

Full evidence is in `docs/internal/m7p2-formily-research.md` Section 3.

Key points:

- `@formily/react@2.3.7` peers React/ReactDOM `>=16.8.0`, so React 18 is not a
  peer-dependency blocker.
- `@formily/core@2.3.7` has no React peer dependency.
- `@formily/semi@1.0.4-beta.1` peers Formily packages at `2.2.1`, while LabelHub
  resolves `2.3.7`; rejected.
- `@designable/react@1.0.0-beta.45` peers React `16.x || 17.x` and AntD
  `^4.15.4`; rejected for M7-P2 Path A-II.
- Existing dnd-kit designer already satisfies the drag-library requirement.

## Per-Cluster Plan

### C1: Throwaway PoC

Scope:

- Temporarily install or link `@tanstack/react-virtual` in the working tree.
- Build minimal Formily + local Semi Input integration in a throwaway route or
  sandbox module.
- Render 100-field synthetic form.
- Round-trip `AnswerPayload -> Formily values -> AnswerPayload`.
- Validate that emitted JSON contains no Formily reactive proxies.
- Validate that `@tanstack/react-virtual` can coexist with Formily field
  subscriptions.
- Revert all code before commit.

Estimate: 180 throwaway lines, not counted toward cap.

Time-box: 4 hours. If a minimal Formily render + AnswerPayload round-trip is not
working within 4 hours, stop and report.

Stop conditions:

- Vite + TS + React 18 build fails under the Formily minimal integration.
- AnswerPayload round-trip leaks Formily reactive proxies or changes JSON shape.
- `@tanstack/react-virtual` cannot coexist with Formily field subscription in the
  minimal test.

Output:

- C1 chat report.
- Baseline numbers appended to this pre-estimate doc only if the auditor asks for
  a follow-up docs commit.
- No production code commit.

### C2: Core Adapters

Scope:

- `SchemaField[] -> ISchema` mapper for 6 field types and nested object
  recursion.
- `AnswerPayload -> Formily initialValues` adapter with deep clone.
- `Formily form.values -> AnswerPayload` snapshot extraction.
- Unknown-key dropping so stale UI/form keys do not enter persisted JSON.
- StableId path preservation for AI findings and Quality Ledger references.
- Local component registry skeleton.
- FieldFrame decorator mirroring existing `FieldFrame` label, required marker,
  help, and error display behavior.

Estimate: 500 lines.

Critical risks:

- Recursive nested-object mapping.
- Unknown-key dropping while preserving historical-submission rendering.
- StableId path notation for nested object children.

Stop conditions:

- Round-trip equality fails for any of the 6 field types.
- Nested object round-trip cannot preserve current AnswerPayload shape.
- Any adapter requires changes to `schemaTypes.ts` or `answerPayload.ts` shape.

### C3: Renderer Replacement

Scope:

- New `SchemaFormilyRenderer` with the same external props as `SchemaRenderer`.
- Internally uses Formily form and C2 adapters.
- Six local Semi x-components:
  - text
  - number
  - select single/multi
  - date
  - file upload text
  - nested object
- Read/write mode toggle via existing `readOnly` prop.
- Display existing external `errors` prop.

Estimate: 650 lines.

Critical rule:

- `SchemaRenderer` is not removed in C3. Both renderers coexist until C7 declares
  parity.

Stop conditions:

- Read-only mode allows edits.
- Any current renderer behavior cannot be reproduced without changing the API.
- Field-level stableId identity is lost.

### C4: Validation Migration

Scope:

- `payloadValidation.ts` remains submit-time authority.
- Formily UI rules project only a subset:
  - required
  - min/max number
  - minLength/maxLength
  - text pattern
  - basic type guards
- Submission still calls existing validation path before submit.
- Document every rule that is not projected to Formily UI validation.

Estimate: 350 lines.

Stop conditions:

- Formily validation accepts a value rejected by `payloadValidation.ts` and the
  UI does not surface the submit-time error.
- Formily validation becomes the only authority.

### C5: Designer Preview Integration

Scope:

- dnd-kit Designer remains the source of `SchemaField[]`.
- Add a live preview panel powered by `SchemaFormilyRenderer` and current draft
  schema state.
- Preview updates on add/select/edit/delete/reorder.
- No `@designable/*` adoption.

Estimate: 300 lines.

Stop conditions:

- Designer state and preview state can diverge.
- Preview mutates designer state directly.
- Existing add/select/edit/delete/reorder UX regresses.

### C6: Virtualization + Benchmark

Scope:

- Add `@tanstack/react-virtual` to `SchemaFormilyRenderer` top-level field list.
- Threshold: full render for `fields.length <= 50`, virtualized render for
  `fields.length > 50`.
- Nested objects render inline.
- Add Vitest bench suite:
  - first render at 100/500/1000/5000 fields
  - single-field update isolation
  - optional 20/50 field anti-regression samples
- Record benchmark table in durable docs.

Estimate: 450 lines.

Stop conditions:

- Virtualization breaks validation display or read-only rendering.
- Sub-50-field forms regress and threshold cannot prevent it.
- Bench numbers cannot be reproduced with a documented command.

### C7: Regression Coverage

Scope:

- Manual regression paths:
  - LabelerSessionPage: all 6 field types in write mode
  - LabelerSubmissionPage: historical read-only render
  - OwnerSubmissionPage: AI review path and read-only render
  - ReviewerSubmissionPage: read-only render plus review panel
  - OwnerSchemaDesignerPage: designer preview
- Three viewport widths: 1440 / 1280 / 1024.
- Consumer pages switch from `SchemaRenderer` to `SchemaFormilyRenderer` only
  after parity is confirmed.
- Old `SchemaRenderer` may be removed only if no fallback need remains.

Estimate: 250 lines.

Stop conditions:

- Any visual or behavioral regression on the five surfaces.
- Consumer page switch happens before parity confirmation.
- Old `SchemaRenderer` cannot be removed but no humanpending watch entry is
  added.

### C8: Verification Docs

Scope:

- `docs/internal/m7p2-verification.md` with commit map and R8 records.
- `docs/screenshots/m7p2-after-set/INDEX.md`.
- 5-7 after-screenshots including large-form benchmark output.
- One `[M7-P2 resolved]` humanpending entry.

Estimate: N/A code.

Stop conditions:

- Any code file changes in C8.
- Verification doc does not record the Path A adjudication trail and `76d61b2`
  supersession.

## Risk Register

| Risk | Cluster | Resolution |
|---|---|---|
| C1 PoC reveals fundamental Vite + TS + React 18 + Formily incompatibility | C1 | STOP, report, re-evaluate Path A feasibility with user. |
| Nested object recursive mapping breaks AnswerPayload round-trip | C2 | C2 includes nested-object round-trip test before C2 commit. |
| Formily reactive proxy leaks into emitted AnswerPayload | C2/C3 | Adapter MUST deep-clone or JSON-serialize at emit boundary; C2 + C3 each add anti-leakage tests. |
| stableId path breaks for nested objects (e.g., parent.child notation) | C2 | C2 explicitly tests AI-findings + Quality-Ledger references against the new path notation. |
| Formily validation diverges from `payloadValidation.ts` authority | C4 | C4 documents projection rules explicitly; round-trip test ensures Formily-validated values pass payloadValidation. |
| dnd-kit designer + Formily preview synchronization drifts | C5 | C5 uses one-way subscription from designer state to preview; no preview-to-designer mutation. |
| `@tanstack/react-virtual` + Formily field subscriptions cause double-render | C6 | C1 validates this combination; if it fails, virtualize row groups rather than individual fields. |
| Benchmark numbers vary by hardware | C6 | Document test machine and record relative improvement, not absolute ms alone. |
| C7 reveals visual regression in any of 5 page surfaces | C7 | Block consumer-page switch and create a Cluster 7b hot-fix cluster. |
| Old `SchemaRenderer` must be kept beyond C7 | C7/C8 | Acceptable; humanpending entry `M7-P2 watch: SchemaRenderer retained pending [reason]`. |

## Stop Conditions

- Any cluster exceeds estimate by 50%.
- Cumulative diff exceeds 4000 lines hard cap.
- C1 PoC reveals any of its three incompatibilities.
- Backend test count changes from 408, meaning backend was accidentally touched.
- OpenAPI MD5 changes from `b6a8344f2c7cc38db958eb333334ebd1`.
- Migration count changes from 11.
- `humanpending.md` changes before C8.
- Any M6 closed contract appears silently broken.
- Any consumer route switches to the new renderer before C7 parity confirmation.

## Verification Plan

Per cluster:

- `pnpm --filter @labelhub/web typecheck` exit 0.
- `pnpm --filter @labelhub/web build` exit 0.
- `bash scripts/check-protected-endpoints.sh` exit 0.
- 3-viewport manual sanity per M7-P1 Cluster 5b lesson: 1440 / 1280 / 1024.
- Backend tests not re-run unless backend was touched, which is forbidden.

Additional gates:

- After C2: round-trip equality tests pass for all 6 field types and nested
  objects.
- After C3: old/new renderer parity on a fixture form in read/write and
  read-only modes.
- After C4: Formily UI validation + submit-time `payloadValidation.ts` both
  exercised.
- After C6: benchmark numbers recorded.
- After C7: five-page regression manual sanity passes before consumer switch.
- After C8: verification doc, screenshots, and humanpending entry land.

## C1 PoC Results

Status: completed within the 4-hour time-box. PoC source was reverted; no
production code, route, package, OpenAPI, migration, backend, or humanpending
change remains.

Test machine:

- Model: MacBook Pro, Mac16,8
- Chip: Apple M4 Pro, 12 cores (8 performance / 4 efficiency)
- Memory: 24 GB
- OS: Darwin 25.5.0 arm64
- Node: `v26.0.0`

### V1: Vite + TS + React 18 + Formily Compatibility

Result: PASS.

Evidence:

- Temporary `FormilyCompileProbe.tsx` imported `createForm`, `FormProvider`,
  `Field`, local Semi `Input`, and `useVirtualizer`.
- `pnpm --filter @labelhub/web typecheck` exited 0.
- `pnpm --filter @labelhub/web build` exited 0 with the existing Vite chunk
  size warning only.
- A React `StrictMode` + `FormProvider` + native `Field` SSR smoke script
  preserved `form.values.strict_field === "ok"`.

Finding: Formily React types resolve under the current React 18 + Vite 5 + TS 5
stack. Local Semi x-components remain the correct binding strategy; no
`@formily/semi` package was used.

### V2: AnswerPayload Round-Trip Integrity

Result: PASS.

Fixed payload covered all current field value shapes:

- text: `text_1`
- number: `number_1`
- single select: `single_1`
- multi select: `multi_1`
- date: `date_1`
- file upload: `file_1`
- nested object: `parent.child_a`, `parent.child_b`

Round-trip sequence:

1. `AnswerPayload -> Formily initialValues` via JSON deep clone.
2. `form.setValuesIn("text_1", "hello changed")`.
3. `form.setValuesIn("parent.child_a", "nested changed")`.
4. `form.values -> AnswerPayload` via JSON deep clone.

Observed result:

- Only `text_1` and `parent.child_a` changed.
- `number_1`, `single_1`, `multi_1`, `date_1`, `file_1`, and
  `parent.child_b` stayed byte-for-byte equivalent under JSON comparison.
- `JSON.stringify` and reparse succeeded with no Formily reactive proxy leakage.

Adapter implication for C2: emit-boundary snapshot extraction can use a deep
clone / JSON-serializable snapshot strategy. C2 still needs production-grade
typed helpers and unknown-key pruning, but no fundamental proxy leak was found.

### V3: stableId Path Preservation

Result: PASS.

Observed Formily nested path format: `parent.child_a`.

Validation:

- `form.setValuesIn("parent.child_a", "nested changed")` updated
  `form.values.parent.child_a`.
- Extracted `AnswerPayload.parent.child_a` matched the changed value.
- Sibling `AnswerPayload.parent.child_b` remained unchanged.

Compatibility implication: the existing M6-P3a / P3a-2 stableId reference model
can be preserved with dot-joined nested paths. C2 must make this explicit in the
adapter tests so AI findings and quality-ledger references continue to resolve.

### V4: @tanstack/react-virtual + Formily Co-Existence

Result: PASS with C1-scoped temporary dependency.

Because `@tanstack/react-virtual` is not yet in project dependencies, C1 installed
`@tanstack/react-virtual@3.13.26` into `/private/tmp/m7p2-react-virtual-poc`
only, then symlinked it into `apps/web/node_modules` for the temporary compile
probe. The symlink was removed before this doc commit; `package.json` and
`pnpm-lock.yaml` were not changed.

Validation:

- Temporary compile probe using `useVirtualizer` + Formily passed typecheck and
  production build.
- Headless `Virtualizer` smoke with 200 synthetic Formily fields mounted 12 rows
  at scroll offset 0 and 14 rows around offset 4000.
- Formily retained off-screen state: `field_0`, `field_100`, and `field_199`
  values survived virtual window movement and targeted updates.

Observed virtual windows:

| Scroll offset | Mounted row count | First index | Last index |
|---:|---:|---:|---:|
| 0 | 12 | 0 | 11 |
| 4000 | 14 | 98 | 111 |

Implication for C6: field-level virtualization remains viable. Row-group
virtualization is still kept as a fallback if browser profiler results show
double-render behavior, but C1 found no fundamental state-retention blocker.

### V5: Baseline Benchmark Measurements (Current Renderer)

Result: EXECUTED with tooling caveat.

C1 discovered a planning mismatch: Vitest is not currently installed in the
workspace and there is no existing `vitest.config.*` or web `test`/`bench`
script. Therefore a real `vitest bench` run is not possible in C1 without adding
a new dev dependency, which C1 explicitly forbids.

Additional baseline harness note: a direct Vite SSR benchmark of the real
`SchemaRenderer.tsx` failed because Semi's SSR ESM path imports extensionless
`lodash/noop`, which Node 26 rejects. This is a benchmark-harness limitation,
not a Formily or LabelHub source failure; normal Vite production build passes.

To avoid changing dependencies in C1, baseline was captured with a Node
`react-dom/server` proxy of the current `SchemaRenderer` map/closure behavior
using lightweight field stubs. This proxy measures the core O(N) behavior:
current `SchemaRenderer` maps every field on a parent value change and recreates
a per-field `handleFieldChange` closure.

| Field count | First render min ms | First render median ms | First render max ms | Field renderer invocations |
|---:|---:|---:|---:|---:|
| 100 | 0.222 | 0.295 | 2.229 | 100 |
| 500 | 1.150 | 1.512 | 4.218 | 500 |
| 1000 | 1.852 | 2.127 | 2.448 | 1000 |
| 5000 | 9.480 | 10.265 | 12.853 | 5000 |

Single-field-change proxy at 500 fields:

| Scenario | Min ms | Median ms | Max ms | Field renderer invocations |
|---|---:|---:|---:|---:|
| Change one field in 500-field payload | 0.972 | 1.000 | 1.286 | 500 |

Interpretation:

- The absolute timings are proxy timings, not final browser profiler numbers.
- The invocation count is the important baseline: a single-field change still
  re-renders all 500 top-level field renderers in the current architecture.
- C6 must install/enable Vitest bench or explicitly re-adjudicate benchmark
  tooling. The earlier "Vitest already exists" assumption is false.

### C1 Path Adjustments

- C2 may proceed with Path A-II adapters; no Formily compatibility blocker was
  found.
- C2 must include dot-path nested stableId adapter tests for `parent.child`
  notation.
- C6 must add or otherwise explicitly enable Vitest benchmark tooling, because
  it is not present today. This is a new C1 finding and should be recorded in
  the C6 cluster prompt before implementation.
- C6 should prefer browser/profiler or jsdom-based benchmark harnesses for real
  Semi/Formily rendering. The C1 proxy numbers are baseline shape evidence, not
  final performance evidence.

## User Adjudication Checklist Before C1

All five decisions are already resolved. No additional gates are expected before
C1. If C1 PoC surfaces unforeseen incompatibility, the agent stops and reports.
