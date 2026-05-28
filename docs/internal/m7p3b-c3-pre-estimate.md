# M7-P3b C3 Pre-Estimate: Frontend Linkage Evaluator, Validation Coupling, And Visibility Filtering

## Status

Pre-estimate gate for M7-P3b C3. No implementation code has landed for this
cluster.

Current anchor: `85d5291`. OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `458/80`. Frontend
Vitest: `65`. Migrations: `11`. humanpending: `135`.

C2 closed the backend runtime semantics cluster. C3 is the frontend mirror:
pure evaluator, frontend `payloadValidation` coupling, and visible field
rendering in the labeler session page.

## Baseline Evidence

### Frontend Payload Validation Shape

`apps/web/src/entities/labeling/payloadValidation.ts` currently mirrors the
P3a validator shape:

```ts
fields.forEach((field) => {
  validateField(field, getFieldValue(payload, field.stableId), errors);
});
```

`validateField(field, value, errors)` computes static required inline:

```ts
const required = field.validation?.required ?? false;
const isEmpty =
  value === null ||
  value === undefined ||
  value === '' ||
  (Array.isArray(value) && value.length === 0) ||
  (isAnswerPayload(value) && Object.keys(value).length === 0);
```

Nested objects recurse through a local nested value:

```ts
field.children?.forEach((child) => validateField(child, childPayload[child.stableId], errors));
```

C3 must preserve this local-value validation while adding a flat value index
for linkage evaluation.

### Renderer Shape

`SchemaFormilyRenderer` builds form and schema from `schemaFields`:

```tsx
const form = useMemo(
  () => createSchemaFormilyForm({ schemaFields, value, onChange, readOnly }),
  [schemaFields, value, onChange, readOnly],
);
const schema = useMemo(() => schemaToFormilyISchema(schemaFields), [schemaFields]);
```

The renderer already contains virtualized and non-virtualized rendering paths.
C3 should not edit it. Visibility should be expressed by changing the
`schemaFields` tree passed from the caller.

### Benchmark Shape

`apps/web/src/features/labeling/__benchmarks__/SchemaRenderer.bench.tsx`
measures Formily single-field change through `createSchemaFormilyForm()`
directly:

```ts
const form = createSchemaFormilyForm({ schemaFields, value, onChange, readOnly: false });
form.setValuesIn(`field_${Math.floor(count / 2)}`, 'changed once');
```

The benchmark does not pass through `LabelerSessionPage`. A page-level visible
schema filter does not invalidate the M7-P2 1-invocation benchmark evidence as
long as C3 leaves `createSchemaFormilyForm`, renderer internals, and the
benchmark file unchanged.

## Recommended Implementation Plan

### 1. Shared Empty Helper

Extract the existing P3a frontend empty semantics into an exported helper
inside `payloadValidation.ts` or a small adjacent helper:

```ts
export function isPayloadValueEmpty(value: unknown): boolean
```

Semantics must stay:

- `null` and `undefined` are empty;
- `""` is empty;
- empty arrays are empty;
- empty answer objects are empty;
- whitespace-only strings are not empty.

The evaluator and `payloadValidation` must call the same helper. This avoids a
second empty definition.

### 2. Frontend LinkageEvaluator

Create:

```text
apps/web/src/entities/labeling/linkageEvaluator.ts
```

Public API:

```ts
export function evaluateLinkageCondition(
  condition: LinkageCondition | null | undefined,
  flatValues: ReadonlyMap<string, unknown> | Record<string, unknown>,
): boolean
```

Recommended helpers:

- `evaluateAtomic`
- `evaluateGroup`
- `isScalarLike`
- `valuesEqual`
- `asScalarArray`
- `asFiniteNumber`

Null / malformed conditions return false. Missing flat values produce
`undefined`, which the shared empty helper treats like backend `null`.

### 3. Flat Value Index For Frontend Validation

Add a helper in `payloadValidation.ts` or next to the evaluator:

```ts
function buildFlatValueIndex(fields: SchemaField[], payload: AnswerPayload): Map<string, unknown>
```

Recursive behavior mirrors C2:

- top-level field stableId maps to `payload[field.stableId]`;
- nested object parent stableId maps to the raw parent value;
- nested child stableId maps to the nested object's child value when the
  parent is an answer payload;
- nested child stableId maps to `undefined` when the parent is missing or not
  an answer payload.

This index is used only for linkage evaluation. P3a local validation keeps
using the local `value`.

### 4. Payload Validation Coupling

Change the internal validation signature:

```ts
function validateField(
  field: SchemaField,
  value: unknown,
  errors: PayloadValidationError[],
  flatValues: ReadonlyMap<string, unknown>,
)
```

At the top:

```ts
if (!isVisible(field, flatValues)) return;
const required = (field.validation?.required ?? false) || isConditionallyRequired(field, flatValues);
```

Hidden fields skip all validation. Visible conditional required reuses
existing P3a required behavior and message `此字段必填`.

Do not rewrite the P3a validation body. The text, number, select, string
shape, and nested object checks should remain structurally unchanged except
for passing `flatValues` through nested child recursion.

### 5. Visible Schema Tree Helper

Create:

```text
apps/web/src/entities/labeling/visibleSchemaFields.ts
```

Public API:

```ts
export function filterVisibleSchemaFields(
  fields: SchemaField[],
  payload: AnswerPayload,
): SchemaField[]
```

Behavior:

- build the same flat value index;
- omit fields with `visibleWhen` false;
- recurse into visible `nested_object` children;
- preserve original field references and arrays when nothing changes;
- shallow-copy a parent only when its filtered child list changes;
- never mutate fields or payload;
- never delete hidden values from payload.

This helper can be reused by `LabelerSessionPage` and tests. Keeping it pure
also makes C4 corpus comparison easier.

### 6. LabelerSessionPage Wiring

In `LabelerSessionPage.tsx`:

- compute `visibleFields = useMemo(() => filterVisibleSchemaFields(fields, answerPayload ?? EMPTY_ANSWER_PAYLOAD), [fields, answerPayload])`;
- pass `visibleFields` to `SchemaFormilyRenderer`;
- keep validation against the original schema `fields`, because
  `validatePayload()` itself now knows how to skip hidden fields;
- keep `SubmitConfirmModal` on original `fields` unless later UX adjudication
  says the confirmation modal should mirror visibility. C3 should not broaden
  modal behavior unless the implementation discovers it is necessary.

### 7. Form Rebuild Mitigation

The visible schema helper should provide structural sharing and reference
stability. It cannot avoid every form rebuild because the renderer also
depends on the `value` prop, but it can avoid additional schema-triggered
rebuilds when visibility does not change.

Recommended mitigation:

1. Make `filterVisibleSchemaFields()` return the original `fields` array when
   all fields are visible and no nested child list changes.
2. When hidden fields exist, return the same filtered tree for the same
   visible stableId shape. If this requires a small memoizing wrapper hook in
   `LabelerSessionPage`, keep it page-local or helper-local and test reference
   stability.
3. Accept a form rebuild when the visible stableId shape actually changes.
   Values are restored from `answerPayload`, which remains the source of
   truth and retains hidden field values.

This preserves correctness without touching renderer internals.

## Op Semantics Mirror

| Op | C3 implementation target |
|---|---|
| `empty` | shared P3a empty helper over `unknown`. |
| `notEmpty` | `!isPayloadValueEmpty(value)`. |
| `eq` | false for empty driver; same-family scalar equality; numbers compare by numeric value; no coercion. |
| `neq` | false for empty driver; true for non-empty scalar values when `eq` is false. |
| `in` | false for empty driver; condition value must be an array of scalar values; scalar driver uses contains; array driver uses intersection. |
| `notIn` | false for empty driver; condition value must be an array of scalar values; scalar driver requires no match; array driver requires no intersection. |
| `gt` / `gte` / `lt` / `lte` | false for empty/non-number/non-finite values; otherwise JS finite number comparison. |
| group | one-level `allOf` / `anyOf`; empty or malformed group false. |

### Numeric Risk Handling

Java `BigDecimal` and JS `number` can diverge only when values exceed JSON-safe
number precision. C3 should:

- reject non-finite numbers in the evaluator;
- avoid string-to-number coercion;
- document the JSON-safe finite-number assumption in the evaluator header;
- leave high-precision edge adjudication for C4 linkage corpus if a practical
  mismatch appears.

### Undefined Risk Handling

Flat index lookups for missing fields produce `undefined`. The shared empty
helper maps `undefined` to empty, making it equivalent to backend `null`.

## Test Estimate

| Test file | Cases | Estimate |
|---|---:|---:|
| `linkageEvaluator.test.ts` | ~12 `it()` blocks | 155 |
| `payloadValidation.linkage.test.ts` | ~7 `it()` blocks | 125 |
| `visibleSchemaFields.test.ts` | ~5 `it()` blocks | 85 |

Expected frontend Vitest count increase: about `+24` tests, from `65` to
roughly `89`. Exact count may vary if cases are grouped for readability.

## Manual Viewport Plan

Implementation should attempt a local browser check at `1440`, `1280`, and
`1024` widths using a labeler session schema with a visibleWhen driver and a
nested condition. Capture screenshots if the local seed path is available.

If browser data remains unavailable, record a D-record with:

- renderer and CSS diff empty;
- `visibleSchemaFields` tests prove fields are omitted before the renderer;
- Formily renderer layout behavior inherited unchanged from M7-P2;
- jsdom tests prove error/validation behavior.

## Estimated Line Budget

| File | Estimate |
|---|---:|
| `apps/web/src/entities/labeling/linkageEvaluator.ts` | 145 |
| `apps/web/src/entities/labeling/payloadValidation.ts` | 80 |
| `apps/web/src/entities/labeling/visibleSchemaFields.ts` | 95 |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | 35 |
| `apps/web/src/entities/labeling/linkageEvaluator.test.ts` | 155 |
| `apps/web/src/entities/labeling/payloadValidation.linkage.test.ts` | 125 |
| `apps/web/src/entities/labeling/visibleSchemaFields.test.ts` | 85 |
| **Hand-authored total** | **~720** |

Recommended cap:

- soft cap: `700` hand-authored lines;
- hard cap: `900` hand-authored lines.

Reason for soft-cap pressure: C3 has three separate proof surfaces
(evaluator, validator coupling, visible schema filtering). Tests are the
majority of the cost and should not be compressed at the expense of semantics.

## Frozen And Verification Expectations

C3 implementation must keep these diffs empty:

- `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`
- `apps/web/src/features/labeling/SchemaRenderer.tsx`
- `apps/web/src/features/labeling/__benchmarks__/SchemaRenderer.bench.tsx`
- backend files under `services/api/**`
- `packages/contracts/openapi/labelhub.yaml`
- `packages/contracts/fixtures/validation-corpus.json`
- generated API files
- `pom.xml`
- migrations
- humanpending

Expected verification:

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- `pnpm --filter @labelhub/web test`
- protected endpoints check
- OpenAPI MD5 remains `890e595c6351ee53788d35354b2412a3`
- migrations remain `11`
- humanpending remains `135`
- git status clean after commit

## Stop Conditions

- The implementation needs renderer internals or virtualization changes.
- Hidden nested children cannot be represented through outer schema-tree
  filtering.
- P3a payload validation corpus changes behavior.
- JS number comparison reveals a high-impact asymmetry with backend
  `BigDecimal` that cannot be deferred to C4 corpus adjudication.
- Hand-authored implementation exceeds `900` lines.

## User Adjudication Checklist

1. Approve recursive outer schema-tree filtering (not renderer internals) for
   both top-level and nested visibility.
2. Approve shared empty helper extraction from `payloadValidation.ts`.
3. Approve JS finite-number comparison as the C3 mirror of backend BigDecimal
   for JSON-safe values, with C4 corpus owning precision edge discovery.
4. Approve keeping `SubmitConfirmModal` on original schema fields for C3,
   while validation itself skips hidden fields.
5. Approve C3 cap: soft `700`, hard `900`.
