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

## User Adjudication Checklist Before C1

All five decisions are already resolved. No additional gates are expected before
C1. If C1 PoC surfaces unforeseen incompatibility, the agent stops and reports.
