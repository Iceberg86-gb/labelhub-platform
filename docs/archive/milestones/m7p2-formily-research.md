# M7-P2 Formily Research

## 1. Status

Research phase output only. No production code, package manifest, OpenAPI,
migration, backend, frontend implementation, or humanpending change lands in
this commit.

Baseline anchor:

- HEAD before research commit: `76d61b2`
- Superseded planning commit: `76d61b2` (Path B/B+C custom renderer plan)
- Backend tests: `408 / 78`
- OpenAPI MD5: `b6a8344f2c7cc38db958eb333334ebd1`
- Migration count: `11`
- `humanpending.md` bracketed entries: `131`

This document supersedes the Path B implementation plan forward, without
amending `76d61b2`. The earlier plan stays in git history as the R8 trail:
auditor recommended B/B+C, user adjudicated Path A.

## 2. Path A Adjudication Record

User adjudication:

```
Path A 锁定。rubric 截图明确表示 "推荐栈: Formily + 拖拽库"
是项目硬性要求,不只是参考。auditor push back 已完成 + user 二次确认。
Cost is not a constraint.
```

R8 interpretation:

- The rubric phrase "推荐栈: Formily + 拖拽库" is treated as a hard project
  requirement, not an optional implementation hint.
- `dnd-kit` is already present and used by the schema designer.
- Formily must move from phantom dependency to real runtime architecture.
- The previous B/B+C recommendation is rejected by user adjudication and must
  be recorded in M7-P2 verification docs.

## 3. Compatibility Audit

### Existing Stack

Evidence from `apps/web/package.json`:

- React: `^18.3.1` (`apps/web/package.json:22`)
- React DOM: `^18.3.1` (`apps/web/package.json:23`)
- Vite: `^5.4.10` (`apps/web/package.json:33`)
- TypeScript: `^5.6.3` (`apps/web/package.json:32`)
- Semi UI: `^2.85.0` (`apps/web/package.json:16-17`)
- dnd-kit: `@dnd-kit/core ^6.1.0`, `@dnd-kit/sortable ^8.0.0`
  (`apps/web/package.json:14-15`)
- Formily phantom deps: `@formily/core ^2.3.2`, `@formily/react ^2.3.2`
  (`apps/web/package.json:18-19`)

Resolved lockfile versions:

- `@formily/core@2.3.7`
- `@formily/react@2.3.7`
- React / ReactDOM `18.3.1`
- Vite `5.4.21`
- TypeScript `5.9.3`
- Semi UI `2.99.2`

### `pnpm view` Results

`pnpm view @formily/core@2.3.7 peerDependencies dependencies version --json`:

```json
{
  "dependencies": {
    "@formily/reactive": "2.3.7",
    "@formily/shared": "2.3.7",
    "@formily/validator": "2.3.7"
  },
  "version": "2.3.7"
}
```

`pnpm view @formily/react@2.3.7 peerDependencies dependencies version --json`:

```json
{
  "peerDependencies": {
    "@types/react": ">=16.8.0",
    "@types/react-dom": ">=16.8.0",
    "react": ">=16.8.0",
    "react-dom": ">=16.8.0",
    "react-is": ">=16.8.0"
  },
  "dependencies": {
    "@formily/core": "2.3.7",
    "@formily/json-schema": "2.3.7",
    "@formily/reactive": "2.3.7",
    "@formily/reactive-react": "2.3.7",
    "@formily/shared": "2.3.7",
    "@formily/validator": "2.3.7",
    "hoist-non-react-statics": "^3.3.2"
  },
  "version": "2.3.7"
}
```

Compatibility verdict:

- `@formily/react@2.3.7` peer range supports React 18 because it declares
  React/ReactDOM `>=16.8.0`.
- `@formily/core@2.3.7` has no React peer dependency.
- Vite and TypeScript compatibility must be validated by C1 PoC build, but no
  peer-dependency blocker is visible from npm metadata.

### Semi Binding

`pnpm view @formily/semi peerDependencies dependencies version --json` reports
latest `@formily/semi@1.0.4-beta.1`, with peer dependencies pinned to
`@formily/core`, `@formily/react`, `@formily/reactive`, and related packages
at exactly `2.2.1`. The project resolves `2.3.7`.

Risk verdict:

- Do **not** adopt `@formily/semi` as the primary binding in M7-P2.
- It is beta, version-skewed against the project's existing Formily 2.3.7 lock,
  and would add more dnd-kit version pressure (`@dnd-kit/sortable ^7.0.0` vs
  project `^8.0.0`).
- Use `@formily/core` + `@formily/react` and implement local Semi
  `x-component` bindings instead.

### Designable / Form Builder Packages

`pnpm view @formily/designable ...` returned npm `404`. The Designable packages
are under `@designable/*`:

- `@designable/core@1.0.0-beta.45`
- `@designable/react@1.0.0-beta.45`
- `@designable/formily-transformer@1.0.0-beta.45`

`@designable/react` peer dependencies include:

- `react: 16.x || 17.x`
- `antd: ^4.15.4`

Risk verdict:

- Option I (`@designable/*` full visual designer adoption) is not compatible
  with the current React 18 + Semi UI stack without a separate major research
  phase.
- It would also introduce Ant Design design-system drift into a Semi UI app.

### Primary-Source Notes

The Formily GitHub README describes the project as supporting React and
dynamic JSON Schema forms, and explicitly frames whole-tree controlled render
performance as a problem Formily addresses through independently managed field
state: https://github.com/alibaba/formily.

The GitHub issues page shows active recent React/Formily risks, including a
React 19 `createRoot` issue and a "Reaction quantity too large" stack-depth
issue. The React 19 item is not directly applicable to LabelHub's React 18
stack, but the reaction-volume issue is relevant to large forms and must be
included in C1/C6 stress tests: https://github.com/alibaba/formily/issues.

## 4. Field Type Mapping

Source field types are declared in
`apps/web/src/entities/schema/schemaTypes.ts:11-19`.

| LabelHub Field Type | Existing Properties | Formily ISchema Mapping | Mapping Notes |
|---|---|---|---|
| `text` | `stableId`, `label`, `placeholder`, `help`, `validation.required`, `minLength`, `maxLength`, `pattern` | `{ type: "string", name: stableId, title: label, "x-component": "TextInput", "x-component-props": { placeholder }, "x-decorator": "FieldFrame" }` | Direct. Custom Semi Input component needed to preserve current visual shell and error display. |
| `number` | `validation.min`, `validation.max`, required/help | `{ type: "number", name: stableId, title: label, "x-component": "NumberInput" }` | Direct. Must preserve current coercion behavior from `NumberFieldRenderer.tsx:14-17`. |
| `single_select` | `options: {label,value}[]`, required/help | `{ type: "string", enum: options, "x-component": "Select" }` | Direct with enum conversion. Must preserve option value strings exactly for export reproducibility. |
| `multi_select` | `options: {label,value}[]`, required/help | `{ type: "array", enum: options, "x-component": "Select", "x-component-props": { multiple: true } }` | Mostly direct. Adapter must preserve `string[]` AnswerPayload values. |
| `date` | required/help | `{ type: "string", format: "date", "x-component": "DateInput" }` | Direct if local component uses Semi Input `type=date` or Semi DatePicker. Existing AnswerPayload stores string. |
| `file_upload` | required/help | `{ type: "string", "x-component": "FileUploadText" }` | Direct only if M7-P2 keeps current "URL/file name text" semantics. Real upload remains outside scope. |
| `nested_object` | `children: SchemaField[]` | `{ type: "object", name: stableId, properties: mapChildren(children) }` | Recursion required. Current UI forbids multi-layer nested objects in frontend validation, but backend permits recursive validation. M7-P2 should preserve one-level UI support unless user expands scope. |

Unmappable field types:

- None are fundamentally unmappable to Formily ISchema.
- The risk is not mapping; it is preserving LabelHub-specific contracts:
  `stableId` as field path/name, existing answer shape, existing validation
  messages, and M6 read-only historical rendering.

## 5. AnswerPayload Adapter Design

Current shape:

- `AnswerPayload` is a plain object index signature:
  `apps/web/src/entities/submission/answerPayload.ts:4-6`.
- `getFieldValue` reads `payload[stableId]` at `answerPayload.ts:10-12`.
- `setFieldValue` shallow-copies into a new object at `answerPayload.ts:14-16`.
- Nested object values are also plain nested `AnswerPayload` objects.

Formily shape:

- Formily form state is reactive internal state (`form.values`, field paths,
  and path-based setters/getters).
- LabelHub must not leak Formily reactive objects into persisted submission
  facts.

Adapter direction:

1. Submission load:
   - `AnswerPayload` from draft/submission is deep-cloned into initial Formily
     values.
   - Field path is `stableId`; nested path is `parentStableId.childStableId`.
2. Runtime change:
   - Formily mutates internal reactive state.
   - Adapter emits plain immutable `AnswerPayload` snapshots to existing parent
     pages through the existing `onChange(nextPayload)` prop.
3. Submission save:
   - Formily values are normalized back into LabelHub `AnswerPayload`.
   - Unknown keys are dropped unless they correspond to current schema stableIds.

M6-P0.5 compatibility:

- Submission immutability is a persistence and lifecycle guarantee. It is not
  violated by using reactive internal UI state while the session is alive.
- The mitigation is to treat Formily state as a transient editing buffer only.
  Persisted drafts/submissions must remain plain JSON `AnswerPayload` snapshots.

Estimated files/lines:

- `formilySchemaAdapter.ts`: 120-160 lines
- `answerPayloadFormilyAdapter.ts`: 120-180 lines
- Adapter tests/harness: 120-180 lines

Total adapter estimate: 360-520 lines including tests.

## 6. Validation Migration

### Current Frontend Validation

Schema-design validation:

- `apps/web/src/entities/schema/schemaValidation.ts:9-56`
- Checks:
  - label required
  - select fields require at least one option
  - nested objects require children
  - UI disallows multi-layer nested objects

Payload validation:

- `apps/web/src/entities/labeling/payloadValidation.ts:10-120`
- Checks:
  - required
  - text type, minLength, maxLength, pattern
  - number type, min, max
  - select/multi-select membership
  - date/file_upload string type
  - nested object recursive validation

Backend schema validation:

- `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaValidator.java:15-56`
- Checks:
  - schema has at least one field
  - stableId required and unique
  - label/type required
  - select options non-empty
  - nested object children non-empty

### Migration Strategy

Recommendation:

- Keep custom `schemaValidation.ts` for schema-designer structural validation.
  Formily should not own the schema-authoring rules.
- Keep custom `payloadValidation.ts` as authoritative submit-time validation in
  C2/C3, while optionally projecting simple rules into Formily `x-validator`
  for immediate UI feedback.
- Do **not** remove custom validation in M7-P2. It protects M6 compatibility and
  avoids divergence with backend `SchemaValidator`.

Formily rule mapping:

- required -> `x-validator` required rule
- text min/max/pattern -> Formily validator rules
- number min/max -> Formily validator rules
- select membership -> custom validator because option value semantics are
  LabelHub-specific
- nested object -> custom recursive validation, not Formily-only

Estimated line count:

- Rule projection helper: 100-140 lines
- Submit-time validation adapter glue: 50-80 lines
- Tests/harness: 100-160 lines

Total validation estimate: 250-380 lines.

## 7. Designer Architecture Decision

### Current Designer

Files:

- `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx`
- `apps/web/src/features/schema-design/FieldList.tsx`
- `apps/web/src/features/schema-design/field-editors/*`

Current architecture:

- Custom schema editor state is `SchemaDocument`.
- Field list uses dnd-kit sortable list.
- Field editors directly edit `SchemaField` objects.
- Designer output is already the API contract consumed by backend and renderer.

### Option I: Adopt Designable For Designer + Renderer

Pros:

- Closest to full Formily ecosystem.
- Better marketing alignment with "Formily form builder" story.

Cons:

- `@formily/designable` package name does not exist on npm.
- Actual `@designable/react@1.0.0-beta.45` peers React `16.x || 17.x` and AntD
  `^4.15.4`, not React 18 + Semi.
- Introduces Ant Design design-system drift.
- Requires rewriting current field editors, schema persistence, designer layout,
  dnd-kit story, and validation story.
- Likely 2500-3500 lines just for designer adoption.

### Option II: Keep Current Designer, Adopt Formily Runtime Renderer

Pros:

- Satisfies rubric's literal "Formily + drag library" stack:
  - Formily powers runtime form rendering.
  - dnd-kit remains the drag library for schema design.
- Preserves current `SchemaField[]` / `SchemaDocument` API contract.
- Avoids beta Designable + React 18 incompatibility.
- Keeps M6 schema version and trusted export story stable.
- Lower risk and still visibly adopts Formily where it matters: form runtime.

Cons:

- Designer is not a Formily visual builder.
- Requires a robust `SchemaField -> ISchema` adapter.

Recommendation: **Option II**.

User adjudication required:

- If the user interprets "Formily + drag library" as "runtime renderer uses
  Formily and designer uses dnd-kit", choose Option II.
- If the user requires a Formily/Designable designer, stop and create a new
  Path A-I plan. That plan is much larger and should not be folded into the
  current M7-P2 estimate.

## 8. Field Renderer Rewrite Estimates

Recommended directory:

- `apps/web/src/features/labeling/formily/`

Proposed files:

| Renderer | New Component | Estimate | Risk |
|---|---|---:|---|
| Text | `TextInput.tsx` | 35-50 | Preserve placeholder, help, required marker, Semi validation state. |
| Number | `NumberInput.tsx` | 45-65 | Preserve current numeric coercion and zero fallback semantics. |
| Select | `SelectInput.tsx` | 70-100 | Single/multi mode, enum conversion, empty-options warning. |
| Date | `DateInput.tsx` | 35-55 | Existing string date contract; avoid Date object drift. |
| FileUpload | `FileUploadTextInput.tsx` | 40-60 | Keep M2 text placeholder semantics; no real upload in M7-P2. |
| NestedObject | `NestedObjectRenderer.tsx` | 90-140 | Recursive schema rendering, one-level UI support, nested payload path conversion. |
| FieldFrame/decorator | `FieldFrameDecorator.tsx` | 70-100 | Must preserve current LabelHub label/help/error/read-only visual language. |
| Registry | `components.ts` / `registry.ts` | 30-50 | Central x-component registry for Formily `SchemaField`. |

Renderer total estimate: 415-620 lines.

## 9. Performance Layer Decisions

Formily's architecture directly addresses the Tier 1 problem: it manages field
state independently rather than forcing a controlled parent to re-render the
whole tree. The Formily README specifically describes controlled-mode whole-tree
rendering as a performance issue and positions independent field state as the
solution.

Decision:

- Path A replaces Tier 1 custom `useFormController` with Formily's field-level
  reactive model.
- We still need a LabelHub adapter layer to emit immutable `AnswerPayload`
  snapshots.
- Virtualization is still needed for 1000+ visible fields. Formily improves
  update isolation, but it does not by itself mean thousands of DOM nodes are
  cheap to mount or scroll.
- Therefore:
  - C2/C3: Formily runtime renderer and adapter.
  - C5: virtualization layer, probably around the rendered top-level field list
    or via a Formily-compatible custom layout component.

Tier 1 from Path B is no longer implemented as custom subscriptions. Tier 2
still applies.

## 10. Revised Cluster Breakdown

Recommended Path A-II: keep current designer, use Formily runtime renderer,
keep dnd-kit designer.

| Cluster | Scope | Estimate | Risk |
|---|---|---:|---|
| C1 | Compatibility PoC: add local Formily Semi bindings, render text/number/select in a throwaway route, prove Vite/TS build and AnswerPayload round-trip | 180 | Medium |
| C2 | Core adapters: `SchemaField[] -> ISchema`, `AnswerPayload <-> Formily values`, component registry, FieldFrame decorator | 500 | High |
| C3 | Runtime renderer replacement: `SchemaRenderer` uses Formily, read/write modes, nested object support, all 6 field components | 650 | High |
| C4 | Validation migration: project LabelHub rules into Formily UI validation while preserving `payloadValidation.ts` submit-time authority | 350 | Medium |
| C5 | Designer integration sanity: keep current dnd-kit designer, add Formily preview panel or renderer preview using generated ISchema; no Designable adoption | 300 | Medium |
| C6 | Performance virtualization + benchmark: Formily-compatible top-level virtualization, 20/50/100/500/1000/5000 field benchmarks | 450 | High |
| C7 | Regression tests/manual evidence: labeler session, labeler submitted view, owner AI review view, reviewer read-only view, designer preview, 1440/1280/1024 | 250 | Medium |
| C8 | Final verification docs + screenshots + humanpending | N/A | Low |
| **Total** | Path A-II code estimate | **2680** | — |

Hard cap proposal: `3300` changed code lines.

Stop conditions:

- C1 cannot render a Formily form with React 18 + Vite + TS.
- C1 cannot preserve `AnswerPayload` round-trip without reactive-object leakage.
- C2 adapter cannot map nested objects without changing persisted answer shape.
- C3 breaks any M6 read-only historical rendering path.
- C6 benchmarks show update isolation but unacceptable first-render/scroll cost
  and no virtualization path.

Path A-I (`@designable/*` designer adoption) rough estimate:

- 4500-6000 changed lines.
- Separate phase or sub-track recommended.
- Requires AntD/Designable compatibility decision.

## 11. M6 Compatibility

### M6-P0.5: Submission Immutable Answer Fact

Risk: Formily uses reactive internal values.

Mitigation:

- Treat Formily form state as transient UI state only.
- Persist only plain JSON `AnswerPayload` snapshots.
- Deep-clone inbound/outbound values at adapter boundary.

### M6-P1: Form Rendering Basics

Risk: visual and interaction parity regressions for basic field types.

Mitigation:

- One acceptance checklist per field type.
- Before/after screenshots for 6 field types.

### M6-P3a / P3a-2: AI Review Field Findings

Risk: field-level AI findings and ledger references rely on stableId matching.

Mitigation:

- Formily field name/path must be `stableId`.
- No generated Formily name may replace stableId.
- AI payload and Quality Ledger references remain stableId-based.

### M6-P5: Trusted Export Reproducibility

Risk: adapter normalizes values differently from old renderer, changing exported
answer JSON.

Mitigation:

- Round-trip tests compare pre-Formily and post-Formily `AnswerPayload` for all
  field types.
- Do not change option values, date string shape, nested object shape, or
  file-upload text semantics.

### M6-P6c: Reviewer Drawer / Read-Only Rendering

Risk: Formily read-only mode could look different or allow accidental editing.

Mitigation:

- Implement explicit read-only renderer behavior, not merely disabled controls.
- Test reviewer and owner historical submission pages.

### M6-P7 / M7-P1: Audit/Governance

Risk: none expected if no backend/audit code changes.

Mitigation:

- Verify no OpenAPI/backend/migration diff in every cluster.

## 12. Rubric 1.4 Confirmation

Path A-II satisfies the rubric wording:

- Formily: runtime form rendering uses `@formily/core` + `@formily/react`.
- Drag library: schema designer continues using `@dnd-kit/core` and
  `@dnd-kit/sortable` in `FieldList.tsx`.

This is a literal stack match without "equivalent custom engine" framing.

Important caveat:

- Path A-II does not use a Formily visual designer. It uses Formily at runtime
  and keeps dnd-kit for the existing LabelHub schema designer.
- If the user expects Formily/Designable as the designer too, Path A-I must be
  separately adjudicated.

## 13. Open Questions For User Adjudication

1. Designer architecture:
   - Option II recommended: keep current dnd-kit designer, adopt Formily runtime.
   - Option I not recommended: adopt `@designable/*` designer despite React 18 /
     AntD / beta-package risks.
2. UI binding:
   - Recommended: do not use `@formily/semi`; implement local Semi x-components.
   - Confirm acceptable.
3. Hard cap:
   - Recommended Path A-II cap: `3300` changed code lines.
   - Confirm acceptable before C1.
4. C1 policy:
   - Recommended: C1 may temporarily modify package/route files but reverts
     throwaway code before commit; committed output is a PoC findings doc or
     updated scope/pre-estimate only.
   - Confirm whether user wants C1 PoC code committed or discarded.
5. Benchmark tooling:
   - Current frontend has no test runner. Confirm whether adding Vitest or a
     dev-only Vite benchmark route is acceptable after C1.
6. Designable:
   - Does the rubric require Formily runtime only, or a Formily/Designable
     designer too?

## 14. Next Gate

Do not start implementation until the user adjudicates:

1. Option II vs Option I.
2. Local Semi x-components vs `@formily/semi`.
3. C1 throwaway policy.
4. `3300` line hard cap.
5. Benchmark tooling allowance.
