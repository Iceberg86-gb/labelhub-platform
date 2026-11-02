# M7-P3b C3 Scope-Budget: Frontend Linkage Runtime And Visibility Filtering

## Status

Pre-estimate gate for M7-P3b C3. No implementation code has landed for this
cluster.

Current anchor: `85d5291` (M7-P3b C2). OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `458/80`. Frontend
Vitest: `65`. Migrations: `11`. humanpending: `135`.

C2 is approved. Backend runtime semantics are now executable in
`LinkageEvaluator` and coupled into `AnswerPayloadValidator`. C3 mirrors that
runtime behavior on the frontend and wires field visibility into the labeler
runtime without touching the Formily renderer internals.

## Phase Character

C3 is the highest-risk P3b cluster because it crosses three frontend seams:

1. runtime linkage evaluation against `AnswerPayload`;
2. field-level validation in `payloadValidation.ts`;
3. rendered field visibility in the labeler session page.

The cluster must preserve the M7-P2 Formily renderer and benchmark evidence.
The preferred design is outer filtering: compute a visible schema tree before
calling `SchemaFormilyRenderer`, pass that filtered tree as `schemaFields`, and
leave `SchemaFormilyRenderer.tsx`, virtualization, `createSchemaFormilyForm`,
and benchmark code unchanged.

## Locked Runtime Semantics

C3 must mirror C2 `LinkageEvaluator` exactly. These semantics are the C4
shared linkage corpus baseline.

| Op | Frontend runtime semantics |
|---|---|
| `empty` | true when the referenced field value is P3a-empty: `null`, `undefined`, `""`, empty array, or empty plain answer object. Whitespace-only strings are not empty. |
| `notEmpty` | logical inverse of `empty`; true for any non-empty unknown value. |
| `eq` | false when the field value is empty. Otherwise true only for same-family scalar equality: string-to-string, boolean-to-boolean, or number-to-number. No string/number/boolean coercion. Numeric values compare by numeric value, so `1` equals `1.0`. |
| `neq` | false when the field value is empty. Otherwise true when `eq` is false. This preserves C2's UI-safety rule that unanswered driver fields do not activate dependents. |
| `in` | false when the field value is empty. For scalar field values, true when any condition-array item equals the field value by `eq` equality. For array field values, true when any selected item intersects any condition-array item. |
| `notIn` | false when the field value is empty. For scalar field values, true when no condition-array item equals the field value. For array field values, true when no selected item intersects any condition-array item. |
| `gt` / `gte` / `lt` / `lte` | false when the field value or condition value is non-numeric or empty. Otherwise compare as JavaScript finite numbers, with the known precision caveat documented below. |
| `allOf` / `anyOf` | one-level groups only; `allOf` requires all atomic conditions true, `anyOf` requires at least one true. Empty or malformed groups evaluate false. |
| malformed / null condition | false; the evaluator must not throw from malformed runtime data. Publish-time validation should make malformed DSL unreachable, but runtime remains defensive. |

## Dual-Side Symmetry Risks

### JS Number vs Java BigDecimal

C2 uses `BigDecimal.compareTo()` for backend numeric equality and comparisons.
C3 can only use JavaScript `number` values because generated frontend types
and parsed JSON values are JS numbers. For normal JSON-safe finite numbers,
the semantics are equivalent. Risk appears at:

- integers outside `Number.MAX_SAFE_INTEGER`;
- decimals with precision beyond IEEE-754 representation;
- cases where authoring JSON loses precision before the evaluator sees it.

C3 should implement numeric comparison with `typeof value === "number"`,
`Number.isFinite(value)`, and plain numeric comparison. C4 linkage corpus must
cover representative normal numeric cases. Extreme precision boundaries should
be documented as a known limitation if they prove asymmetric, following the
P3a scientific-notation message record pattern.

### Undefined vs Null

Java sees missing map entries as `null`. JavaScript sees missing object keys as
`undefined`. The existing frontend P3a empty semantics already treat both
`null` and `undefined` as empty, matching backend `null`. C3 must reuse that
same empty helper and must not introduce a second empty definition.

## Allowed Files And Budget

| File | Purpose | Estimate |
|---|---|---:|
| `apps/web/src/entities/labeling/linkageEvaluator.ts` | New pure frontend evaluator mirroring C2 op semantics | 145 |
| `apps/web/src/entities/labeling/payloadValidation.ts` | Build flat value index, add visibility gate, compute effective required | 80 |
| `apps/web/src/entities/labeling/visibleSchemaFields.ts` | Pure outer schema-tree filtering helper for rendering visible fields only | 95 |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Use visible schema tree for `SchemaFormilyRenderer`; preserve values and server errors | 35 |
| `apps/web/src/entities/labeling/linkageEvaluator.test.ts` | C2 op semantics mirror tests | 155 |
| `apps/web/src/entities/labeling/payloadValidation.linkage.test.ts` | Hidden skip, requiredWhen, nested double-view validation tests | 125 |
| `apps/web/src/entities/labeling/visibleSchemaFields.test.ts` | Visible schema tree and reference-stability tests | 85 |
| **Hand-authored total** | | **~720** |

Recommended C3 caps:

- hand-authored soft cap: `700`;
- hand-authored hard cap: `900`.

The estimate is slightly above the soft cap because the tests are doing most
of the safety work. If implementation exceeds `700` because the op semantics
and visibility-tree tests are explicit, that is acceptable while under `900`
and must be reported. No generated code churn is expected in C3.

## Outer Filtering Design

### Why Outer Filtering

`SchemaFormilyRenderer` derives form state, ISchema, and virtualization from
its `schemaFields` prop. It already implements Formily rendering and error
injection. C3 does not need to alter those internals.

The C3 visibility layer should therefore compute:

```text
(schemaFields, answerPayload) -> visibleSchemaFields
```

and pass `visibleSchemaFields` into both `SchemaFormilyRenderer` and
`SubmitConfirmModal`. The modal remains unchanged internally; it receives the
same outer filtered schema tree as the renderer so the confirmation summary
does not resurface fields hidden on the answer page.

### Recursive Tree Filtering

Filtering only top-level fields is insufficient for nested child visibility.
The recommended implementation is a recursive schema-tree filter outside the
renderer:

- if a field's `visibleWhen` is false, omit that field from the returned tree;
- if a `nested_object` is visible, recursively filter its children;
- preserve original field object references when the field and children are
  unchanged;
- create a shallow copied parent only when its child list changes.

This satisfies nested visibility without touching renderer internals.

### Hidden Values

Hidden values remain in `AnswerPayload`. C3 only changes rendering,
confirmation-summary display, and validation participation. It must not strip
values from payload state.

## Form Rebuild And Value Retention

`SchemaFormilyRenderer` currently memoizes the Formily form with:

```tsx
useMemo(
  () => createSchemaFormilyForm({ schemaFields, value, onChange, readOnly }),
  [schemaFields, value, onChange, readOnly],
)
```

Changing `schemaFields` can rebuild the form. C3 should reduce unnecessary
schema reference changes:

- compute visible schema fields with `useMemo`;
- make the visible-field helper structurally share the original schema tree;
- keep the same filtered array reference when the visible stableId tree is
  unchanged;
- only change the filtered tree when visibility actually changes.

When visibility does change, a form rebuild is acceptable because the renderer
receives the current `answerPayload` as `value`, and that payload is the
source of truth. The `onChange` path writes edits into `answerPayload`; if a
visibility change happens after a user edit, the latest payload restores the
visible values on rebuild. Hidden field values remain in payload for later
reveal.

This is a page-level trade-off, not a benchmark regression. The benchmark's
single-field invocation evidence uses `createSchemaFormilyForm` directly and
does not pass through the labeler page filter.

## Payload Validation Coupling

C3 mirrors C2's double-view design:

1. **Local value view**: the existing value passed into `validateField()`.
   This remains the source for P3a type, min/max, pattern, select, string, and
   nested validation.
2. **Flat linkage value index**: a root-snapshot map of
   `stableId -> raw payload value`, including nested child stableIds. This is
   the source for `visibleWhen` and `requiredWhen`.

Required behavior:

- no `visibleWhen`: visible;
- `visibleWhen` false: skip all validation, including static required,
  `requiredWhen`, type checks, nested object shape, and child checks;
- no `requiredWhen`: conditional required false;
- `requiredWhen` true and visible: effective required is
  `staticRequired || conditionalRequired`;
- required message stays P3a's existing `此字段必填`;
- P3a validation body logic and messages remain unchanged.

## Forbidden Surfaces

- `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`
- `apps/web/src/features/labeling/SchemaRenderer.tsx`
- `createSchemaFormilyForm` and virtualization logic
- `apps/web/src/features/labeling/__benchmarks__/SchemaRenderer.bench.tsx`
- backend files under `services/api/**`
- `packages/contracts/openapi/labelhub.yaml`
- generated frontend/backend API files
- `packages/contracts/fixtures/validation-corpus.json`
- backend `LinkageEvaluator.java`
- `pom.xml` / `services/api/pom.xml`
- migrations
- humanpending

`payloadValidation.ts` may change only to add the visibility gate, flat index,
and effective required wrapper. P3a validation bodies and message strings are
frozen.

## Test Plan

### New Frontend Tests

1. `linkageEvaluator.test.ts`
   - no coercion: `"1"` string does not equal `1` number;
   - numeric equality and comparisons;
   - empty driver makes `eq`, `neq`, `in`, `notIn`, and comparisons false;
   - `empty` / `notEmpty` reuse P3a empty semantics;
   - scalar and array `in` / `notIn`;
   - one-level `allOf` / `anyOf`;
   - malformed/null condition false.

2. `payloadValidation.linkage.test.ts`
   - hidden static-required field emits no `此字段必填`;
   - visible static-required field still emits `此字段必填`;
   - `requiredWhen` true emits `此字段必填` for an empty optional field;
   - `requiredWhen` false keeps the optional field valid;
   - hidden nested parent skips parent shape and all children;
   - nested child condition can reference a flat sibling/top-level stableId.

3. `visibleSchemaFields.test.ts`
   - hidden top-level field omitted from rendered schema tree;
   - hidden nested child omitted without renderer changes;
   - hidden values remain present in input payload;
   - helper preserves references when visibility tree is unchanged.

### Existing Tests Must Stay Green And Unmodified

- `apps/web/src/entities/labeling/payloadValidation.corpus.test.ts`
- all `apps/web/src/features/labeling/formily/__tests__/*`
- `apps/web/src/features/labeling/__benchmarks__/SchemaRenderer.bench.tsx`

## Three-Viewport Manual Check Plan

C3 changes visible field layout. Implementation verification should attempt
1440 / 1280 / 1024 viewport checks with a schema containing:

- a driver field;
- a conditionally visible field;
- a conditionally required field;
- a nested object with a conditionally visible child.

Expected visual checks:

- hidden fields leave no stale error or layout residue;
- revealing fields does not break responsive layout;
- nested child visibility renders inside the existing nested frame;
- Chinese error text still wraps within the existing field frame.

If local browser seeding remains blocked, report D-record evidence instead:
renderer/CSS diff empty, visible schema tree unit tests passing, and layout
inherited from the unchanged M7-P2 renderer.

## Verification Plan For Implementation

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- `pnpm --filter @labelhub/web test`
- targeted Vitest for new evaluator / validation / visible-schema tests
- `pnpm --filter @labelhub/web bench` only if the implementation touches any
  forbidden benchmark-adjacent surface; the planned design should not
- `bash scripts/check-protected-endpoints.sh`
- OpenAPI MD5 remains `890e595c6351ee53788d35354b2412a3`
- migrations remain `11`
- humanpending remains `135`
- renderer, benchmark, backend, OpenAPI, corpus, and generated files have
  empty diff

## Stop Conditions

- C3 requires editing `SchemaFormilyRenderer.tsx` or Formily virtualization.
- C3 requires changing `createSchemaFormilyForm`.
- Outer filtering cannot hide nested children without renderer changes.
- Existing P3a frontend corpus behavior changes.
- JS number vs Java BigDecimal reveals a practical non-trivial asymmetry that
  cannot be documented and deferred to C4 adjudication.
- Implementation exceeds `900` hand-authored lines.

## User Adjudication Checklist

1. Approve frontend-only C3 scope: evaluator, `payloadValidation` wrapper,
   visible schema tree helper, labeler page wiring, and tests.
2. Approve recursive outer schema-tree filtering as the renderer-zero-diff
   method for both top-level and nested visibility.
3. Approve C2 op semantics as the exact C3 frontend evaluator contract.
4. Approve JS number comparison for normal JSON-safe finite numbers, with C4
   corpus documenting any extreme precision limitation if discovered.
5. Approve C3 caps: hand-authored soft `700`, hard `900`.
