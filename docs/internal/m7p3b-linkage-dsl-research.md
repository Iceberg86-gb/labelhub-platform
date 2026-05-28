# M7-P3b Research: Field Linkage DSL And Cross-Field Runtime Rules

## 1. Status

Research output for M7-P3b. No production code change.

Current anchor: `8ad868d` (M7-P3a closure). OpenAPI MD5 remains
`304b6d00e35a3649fd10ae9f01392288` in this research commit.

P3b is the second half of M7-P3. P3a closed the backend answer-validation
security gap for static single-field rules. P3b adds field linkage:
conditional visibility and conditional required behavior, evaluated
symmetrically in frontend and backend.

## 2. Locked P3b V1 Scope

The P3b v1 product scope is already adjudicated:

- Include: OpenAPI DSL, generated types, backend evaluator, frontend
  evaluator, runtime visibility, conditional required, shared corpus, and
  submit-path symmetric validation.
- Minimum configuration entry: schema JSON accepts `visibleWhen` and
  `requiredWhen`. If needed, the existing schema designer can get an
  "advanced JSON" entry point at
  `apps/web/src/features/schema-design/field-editors/FieldEditor.tsx`, or the
  phase can use dev/seed fixtures for demonstration.
- Exclude: visual condition-tree builder, field picker, op-specific editor,
  condition previewer, and complex UX validation. These are deferred to P3b v2.

Planned deferred humanpending/watch wording:

```text
[M7-P3b watch] Visual condition editor deferred; v1 accepts DSL via schema JSON / minimal advanced config only.
```

## 3. Recommendation

Use the constrained declarative rule set, but lock P3b v1 to **one-level
`allOf` / `anyOf` groups plus atomic conditions**.

Do not use string expressions. Do not use eval. Do not implement recursive
condition trees in v1.

Recommended schema shape:

```json
{
  "visibleWhen": {
    "anyOf": [
      { "field": "type", "op": "eq", "value": "other" },
      { "field": "category", "op": "in", "value": ["custom", "manual"] }
    ]
  },
  "requiredWhen": {
    "field": "type",
    "op": "eq",
    "value": "other"
  }
}
```

Why:

- It satisfies the real P3b v1 use case: "field B is visible/required when
  field A has value X", including a modest amount of OR/AND composition.
- It is OpenAPI-describable and produces usable TypeScript types with
  `openapi-typescript`.
- It avoids Java recursive oneOf ambiguity and avoids forcing the designer UI
  to handle arbitrarily nested trees.
- It preserves the P3a lesson: semantic symmetry must be small enough to prove
  with a shared corpus.

## 4. Core Semantics

### Hidden Fields Do Not Validate

This is the load-bearing rule:

- If a field is hidden because `visibleWhen` evaluates to false, all validation
  for that field is skipped. Static `validation.required`, `requiredWhen`, and
  type-specific validation do not run.
- If a field is visible and `requiredWhen` evaluates to true, the field is
  required even when `validation.required` is false.
- If no `visibleWhen` exists, the field is visible.
- If no `requiredWhen` exists, required behavior is exactly P3a static
  `validation.required`.

No new required-message string is needed. Conditional required reuses the P3a
message `此字段必填`.

### Evaluation Reads Raw Answer Payload Values

Recommended v1 rule: condition evaluation reads raw values from the current
`AnswerPayload` snapshot, regardless of whether the referenced field is itself
visible.

This is intentionally simple:

- No topological sort is required at runtime.
- No iterative "evaluate until stable" loop is required.
- Frontend and backend can evaluate every condition from the same payload
  snapshot.
- Hidden field values may still participate in other conditions if present in
  the payload. This is deterministic and avoids visibility-state-dependent
  evaluation.

P3b v1 should not automatically strip hidden field values from the persisted
payload. It should skip validation for hidden fields, but avoid mutating
answers as a side effect of visibility.

### Dependency Order And Cycles

Runtime evaluation should be single-pass per field:

1. Resolve the field's `visibleWhen` against the answer snapshot.
2. If hidden, skip all validation and skip rendering.
3. If visible, resolve `requiredWhen`.
4. Run P3a validation with `required = staticRequired || conditionalRequired`.

Because conditions read payload values rather than other fields' visibility
states, the runtime evaluator does not need recursion or stabilization.

Schema publish/validation should still reject obvious linkage hazards:

- condition references to missing stableIds;
- self-reference, such as field `b.visibleWhen.field = "b"`;
- dependency cycles among `visibleWhen` / `requiredWhen` references, even
  though runtime could technically evaluate from raw values.

Rejecting cycles keeps the mental model simple, preserves future room for
visibility-aware semantics, and makes the designer easier to explain.

## 5. OpenAPI DSL Draft

Recommended one-level schema:

```yaml
LinkageConditionOp:
  type: string
  enum: [eq, neq, in, notIn, gt, gte, lt, lte, empty, notEmpty]

LinkageConditionValue:
  oneOf:
    - type: string
    - type: number
    - type: boolean
    - type: array
      items:
        oneOf:
          - type: string
          - type: number
          - type: boolean

LinkageAtomicCondition:
  type: object
  required: [field, op]
  properties:
    field:
      type: string
      description: SchemaField stableId referenced by this condition.
    op:
      $ref: '#/components/schemas/LinkageConditionOp'
    value:
      $ref: '#/components/schemas/LinkageConditionValue'

LinkageConditionGroup:
  type: object
  properties:
    allOf:
      type: array
      items:
        $ref: '#/components/schemas/LinkageAtomicCondition'
    anyOf:
      type: array
      items:
        $ref: '#/components/schemas/LinkageAtomicCondition'

LinkageCondition:
  oneOf:
    - $ref: '#/components/schemas/LinkageAtomicCondition'
    - $ref: '#/components/schemas/LinkageConditionGroup'
```

`SchemaField` gains:

```yaml
visibleWhen:
  $ref: '#/components/schemas/LinkageCondition'
requiredWhen:
  $ref: '#/components/schemas/LinkageCondition'
```

Notes:

- `empty` and `notEmpty` do not require `value`.
- `eq`, `neq`, `gt`, `gte`, `lt`, `lte` require scalar `value`.
- `in` and `notIn` require array `value`.
- OpenAPI alone cannot enforce every op/value pairing. Backend and frontend
  schema validation must enforce those pairings and reject malformed DSL at
  publish time.

## 6. Codegen Shape PoC

### Boundary

The PoC was throwaway. It wrote temporary OpenAPI variants and generated
temporary TypeScript declarations under `/private/tmp`. No production OpenAPI
or generated source was modified.

Commands used:

```bash
pnpm --filter @labelhub/web exec openapi-typescript /private/tmp/labelhub-p3b-recursive.yaml -o /private/tmp/labelhub-p3b-recursive.d.ts
pnpm --filter @labelhub/web exec openapi-typescript /private/tmp/labelhub-p3b-onelevel.yaml -o /private/tmp/labelhub-p3b-onelevel.d.ts
pnpm --filter @labelhub/web exec tsc --strict --noEmit --skipLibCheck --moduleResolution node --target ES2022 --module ESNext /private/tmp/labelhub-p3b-typecheck.ts
pnpm --filter @labelhub/web exec tsc --strict --noEmit --skipLibCheck --moduleResolution node --target ES2022 --module ESNext /private/tmp/labelhub-p3b-recursive-typecheck.ts
```

All four commands passed after the temporary YAML `$ref` values were properly
quoted.

### Variant 1: Recursive Condition Tree

Generated TypeScript:

```ts
LinkageConditionGroup: {
    allOf?: components["schemas"]["LinkageCondition"][];
    anyOf?: components["schemas"]["LinkageCondition"][];
};
LinkageCondition:
  components["schemas"]["LinkageAtomicCondition"] |
  components["schemas"]["LinkageConditionGroup"];
SchemaField: {
    stableId: string;
    label: string;
    type: components["schemas"]["SchemaFieldType"];
    placeholder?: string;
    help?: string;
    validation?: components["schemas"]["SchemaFieldValidation"];
    options?: components["schemas"]["SchemaFieldOption"][];
    children?: components["schemas"]["SchemaField"][];
    visibleWhen?: components["schemas"]["LinkageCondition"];
    requiredWhen?: components["schemas"]["LinkageCondition"];
};
```

Temporary strict typecheck passed with a recursive `walk(condition)` helper.

Verdict: TypeScript can represent this. The type is readable, but it invites
recursive evaluator/editor complexity that P3b v1 does not need. It also
increases the Java oneOf risk.

### Variant 2: One-Level Group Plus Atomic Conditions

Generated TypeScript:

```ts
LinkageConditionGroup: {
    allOf?: components["schemas"]["LinkageAtomicCondition"][];
    anyOf?: components["schemas"]["LinkageAtomicCondition"][];
};
LinkageCondition:
  components["schemas"]["LinkageAtomicCondition"] |
  components["schemas"]["LinkageConditionGroup"];
SchemaField: {
    stableId: string;
    label: string;
    type: components["schemas"]["SchemaFieldType"];
    placeholder?: string;
    help?: string;
    validation?: components["schemas"]["SchemaFieldValidation"];
    options?: components["schemas"]["SchemaFieldOption"][];
    children?: components["schemas"]["SchemaField"][];
    visibleWhen?: components["schemas"]["LinkageCondition"];
    requiredWhen?: components["schemas"]["LinkageCondition"];
};
```

Temporary strict typecheck passed with:

```ts
type Condition = components["schemas"]["LinkageCondition"];
type Field = components["schemas"]["SchemaField"];

const condition: Condition = {
  anyOf: [{ field: "type", op: "eq", value: "other" }],
};

const field: Field = {
  stableId: "details",
  label: "Details",
  type: "text",
  visibleWhen: condition,
  requiredWhen: { field: "type", op: "eq", value: "other" },
};
```

Verdict: This is the best P3b v1 shape. It is strongly typed enough for a
future minimal editor without handwritten `any`, but bounded enough for
corpus proof and Java implementation.

### Java Codegen D-Record

Java generation against the throwaway schema was not run in this research
commit. The existing backend generator is the Maven
`openapi-generator-maven-plugin` with `generatorName=spring`, fixed to the
production OpenAPI input. Research constraints forbid committing or retaining
the temporary OpenAPI edit, and prior P3a work established Maven Central /
sandbox execution constraints.

Evidence from current generated Java:

- Object `oneOf` in `QualityLedgerEntryPayload` generates a Java interface with
  concrete implementation classes.
- Recursive `SchemaField.children` generates `List<@Valid SchemaField>`.

Inference:

- `LinkageCondition` as a `oneOf` of object schemas is likely to generate a
  Java interface or similar polymorphic shape.
- `LinkageConditionValue` as a primitive/array union is likely to be awkward
  for Java and should be treated at evaluator boundaries as a Jackson
  `Object`-like value (`String`, `BigDecimal`/number, `Boolean`, `List<?>`).
- One-level groups reduce the amount of polymorphic recursion Java must carry.

This is a D-record, not a generated Java result. P3b C1 should regenerate Java
from the final contract and inspect generated model shape before evaluator
implementation proceeds.

## 7. AnswerPayloadValidator Integration

P3b should modify validation as a thin layer around P3a behavior:

```text
validate(schema, payload):
  for each top-level field:
    validateField(field, payload[field.stableId], payloadSnapshot)

validateField(field, value, payloadSnapshot):
  if !isVisible(field.visibleWhen, payloadSnapshot):
    return []

  conditionalRequired = evaluate(field.requiredWhen, payloadSnapshot)
  effectiveRequired = staticRequired(field) || conditionalRequired

  run the existing P3a validation body using effectiveRequired
```

For nested objects:

- If parent `nested_object` is hidden, skip parent and all children.
- If parent is visible, existing nested object validation applies.
- Child `visibleWhen` and `requiredWhen` should evaluate against the same
  payload snapshot convention used by frontend. P3b C1/C2 must choose whether
  nested child references are resolved from the root payload or the current
  nested object payload. Recommendation: flat `stableId` lookup through a
  precomputed stableId -> value map, matching P3a's flat error identity.

No new error messages are required for runtime answer validation. Malformed DSL
should be caught by schema publish validation, not by answer validation.

P3a's 20-case validation corpus should remain green. P3b adds new linkage cases
without rewriting existing P3a cases.

## 8. Frontend Runtime Integration

Frontend integration points:

- `payloadValidation.ts`: add the same visibility/required evaluation wrapper
  used by backend.
- `SchemaFormilyRenderer`: filter hidden top-level fields before direct render
  or virtualized render. Preserve Formily values; do not auto-delete hidden
  values from `AnswerPayload`.
- `schemaToFormilyISchema`: inject conditional required state only if needed
  for UI hints. Submit-time correctness stays in `payloadValidation.ts` and
  backend validator.
- `LabelerSessionPage`: unchanged except it receives the filtered renderer and
  validation behavior through shared utilities.

Performance guard:

- Visibility evaluation should memoize on `schemaFields` + `answerPayload`.
- For >50 fields, keep virtualized rendering. Filtering visible fields must not
  reintroduce all-field rerender churn beyond the existing Formily value-change
  path.

## 9. Schema Publish Validation

P3b needs schema-document validation on both sides:

- referenced `field` stableId exists;
- `field` does not reference itself;
- dependency cycles are rejected;
- op is one of the whitelist values;
- `empty` / `notEmpty` have no required `value`;
- scalar ops have scalar values;
- `in` / `notIn` have array values;
- numeric comparison ops should only target fields whose type is `number`;
- option membership ops should target fields whose values are strings or arrays
  of strings (`single_select`, `multi_select`, `text` if intentionally
  allowed).

This belongs in the schema publish path, not in answer submit. Bad DSL should
be rejected before it becomes a schema version.

## 10. Shared Corpus Plan

Add `packages/contracts/fixtures/linkage-corpus.json`, separate from the P3a
validation corpus.

Minimum cases:

1. no `visibleWhen` -> visible;
2. atomic `eq` true/false;
3. atomic `neq`;
4. `in` and `notIn`;
5. numeric `gt/gte/lt/lte`;
6. `empty` / `notEmpty` with null, "", [], {};
7. top-level `allOf` true/false;
8. top-level `anyOf` true/false;
9. hidden field skips static required;
10. hidden field skips `requiredWhen`;
11. visible field + `requiredWhen` true returns `此字段必填`;
12. visible field + `requiredWhen` false preserves P3a behavior;
13. nested child visible/required using flat stableId lookup;
14. malformed DSL is rejected at schema publish validation;
15. cycle/self-reference schema validation rejects publish.

Both frontend and backend should read the same corpus, as P3a did.

## 11. Cluster Breakdown Recommendation

| Cluster | Scope | Estimate | Risk |
|---|---|---:|---|
| C1 | OpenAPI DSL contract, generated TS/Java types, schema validation for op/value/ref/self/cycle rules | 350 | Codegen + schema validation complexity |
| C2 | Backend `LinkageEvaluator` and `AnswerPayloadValidator` integration; preserve P3a corpus | 350 | Hidden-field skip semantics and nested lookup |
| C3 | Frontend `LinkageEvaluator`, `payloadValidation` integration, and `SchemaFormilyRenderer` visibility filtering | 450 | Renderer virtualization and value preservation |
| C4 | Shared linkage corpus + backend/frontend dual-side tests + submit integration tests | 500 | Corpus coverage and Testcontainers D-records |
| C5 | Minimal configuration ingress: advanced JSON in `FieldEditor.tsx` or dev fixture path; no visual builder | 250 | Preventing UI scope creep |
| C6 | Verification doc, humanpending watch, screenshots/D-record | N/A | Browser seed data |

Suggested cap: 1700 soft / 2300 hard.

P3b is likely larger than P3a in runtime semantics but smaller than a full
designer-builder phase because the visual condition editor is deferred.

## 12. Risk Register

| Risk | Mitigation |
|---|---|
| Frontend/backend evaluator drift | Shared linkage corpus read by both sides; same op table and empty semantics documented. |
| Hidden field skip order differs | Single-pass snapshot semantics; hidden fields do not validate; requiredWhen only runs after visible. |
| Recursive condition tree expands scope | Lock v1 to one-level groups; no recursive groups in final OpenAPI. |
| Java oneOf model awkward | Inspect generated Java in C1 before C2; keep evaluator tolerant of generated model shape. |
| Schema cycles confuse users | Publish-time static cycle/self-reference detection. |
| Hidden stale values affect conditions | Document raw payload snapshot semantics; do not strip hidden values in v1. |
| P3a corpus breaks | C2/C3 must run P3a corpus unchanged; P3b adds separate corpus. |
| Designer scope creeps into visual builder | C5 only advanced JSON or seed fixture; visual builder deferred to P3b v2. |
| Virtualized renderer regresses | C3 reruns M7-P2 benchmark guard and Formily tests. |

## 13. Final Research Decision

Proceed with P3b v1 using:

- declarative JSON conditions;
- one-level `allOf` / `anyOf` groups;
- atomic conditions with whitelist ops;
- raw answer snapshot evaluation;
- publish-time static reference and cycle validation;
- hidden fields skip all validation;
- conditional required reuses `此字段必填`;
- shared linkage corpus separate from P3a validation corpus;
- no visual condition builder in v1.

Next step: user/auditor adjudicates this final shape and cluster plan. If
approved, P3b C1 should start with a scope-budget + pre-estimate gate and then
land the OpenAPI contract and generated types.
