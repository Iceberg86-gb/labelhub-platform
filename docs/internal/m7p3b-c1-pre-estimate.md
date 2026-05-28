# M7-P3b C1 Pre-Estimate: Contract, Codegen, Publish-Time DSL Validation

## Status

Pre-estimate gate for M7-P3b C1. No implementation code has landed for this
cluster. Current anchor: `7e73e4b`. OpenAPI MD5:
`304b6d00e35a3649fd10ae9f01392288`. Migrations: `11`. humanpending: `135`.

Research `7e73e4b` is approved. C1 is the first P3b implementation cluster
and is expected to produce a new OpenAPI MD5.

## Baseline Anchors

### OpenAPI Insertion Point

Current contract:

```yaml
SchemaFieldValidation:
  type: object
  properties:
    required:
      type: boolean
    minLength:
      type: integer
      minimum: 0
    maxLength:
      type: integer
      minimum: 0
    min:
      type: number
    max:
      type: number
    pattern:
      type: string
SchemaFieldOption:
  type: object
  required: [label, value]
  properties:
    label:
      type: string
    value:
      type: string
SchemaField:
  type: object
  required: [stableId, label, type]
  properties:
    ...
    children:
      type: array
      items:
        $ref: '#/components/schemas/SchemaField'
```

C1 inserts linkage schemas between `SchemaFieldOption` and `SchemaField`, then
adds `visibleWhen` and `requiredWhen` to `SchemaField`.

### Backend Publish Path

`SchemaService.publishVersion(schemaId, schemaDocument, ownerId)` already calls:

```java
schemaValidator.validate(schemaDocument);
```

So backend DSL publish validation belongs in:

```text
services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaValidator.java
```

Existing checks: non-empty fields, stableId required/unique, label required,
type required, select options required, nested_object children required, nested
stableId uniqueness.

### Frontend Publish Validation

Frontend schema authoring validation currently lives in:

```text
apps/web/src/entities/schema/schemaValidation.ts
```

`OwnerSchemaDesignerPage` already calls `validateSchemaForUI(draftDocument)`.
C1 extends this path for DSL authoring errors.

### Existing Tests

Backend:

- `services/api/src/test/java/com/labelhub/api/module/schema/util/SchemaValidatorTest.java`
  already covers schema-document validator behavior.

Frontend:

- There is no dedicated `schemaValidation.test.ts` today. C1 should add one
  because the new DSL checks are semantic enough to require a frontend unit
  guard.

## Locked Contract Shape

OpenAPI draft to implement:

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

`SchemaField`:

```yaml
visibleWhen:
  $ref: '#/components/schemas/LinkageCondition'
requiredWhen:
  $ref: '#/components/schemas/LinkageCondition'
```

## Java Codegen Plan

Research was D-record for Java. C1 must turn that into evidence.

Implementation order:

1. Apply the OpenAPI DSL schema.
2. Run frontend generation with `pnpm --filter @labelhub/web gen:api`.
3. Run backend generation through Maven (`mvn -pl services/api generate-sources`
   or compile path that invokes the OpenAPI generator).
4. Inspect generated models for:
   - `LinkageConditionOp`
   - `LinkageAtomicCondition`
   - `LinkageConditionGroup`
   - `LinkageCondition`
   - `LinkageConditionValue`
   - new `SchemaField.getVisibleWhen()` / `getRequiredWhen()`
5. If generated Java shape is not usable for C2 evaluator without
   hand-editing generated files, STOP and report contract alternatives.

Expected risk:

- `LinkageCondition` oneOf of object schemas may generate an interface.
- `LinkageConditionValue` primitive/array union may generate an awkward model.
- If primitive union becomes unusable, fallback candidate is to model `value`
  as an unconstrained schema/object with publish-time validation. That fallback
  requires user adjudication before implementation proceeds.

## Publish-Time DSL Validation Design

### Shared Rule Table

Use the same conceptual rule table in Java and TypeScript:

| Op family | Ops | Required value shape |
|---|---|---|
| Empty | `empty`, `notEmpty` | no value |
| Scalar equality/comparison | `eq`, `neq`, `gt`, `gte`, `lt`, `lte` | scalar string/number/boolean |
| Set membership | `in`, `notIn` | array of string/number/boolean |

Type-pairing scope for C1:

- `gt` / `gte` / `lt` / `lte` may only reference `number` fields.
- `eq` / `neq` / `in` / `notIn` may reference non-`nested_object` fields.
- `empty` / `notEmpty` may reference any field type.
- Full evaluator-specific membership semantics are deferred to C2/C4.

### Reference Validation

Build a flat stableId index for every field, including nested children. For
each condition:

- referenced `field` must exist;
- referenced `field` must not equal the owner field stableId;
- malformed group or atomic condition produces a deterministic fieldPath;
- the stableId path stays flat to match P3a/P3b field identity.

### Cycle Detection

Build a directed graph:

```text
ownerStableId -> referencedStableId
```

for both `visibleWhen` and `requiredWhen` conditions.

Run DFS color marking:

- white: unvisited;
- gray: visiting;
- black: done.

Finding an edge to gray means a cycle. Reject publish with
`联动条件存在循环依赖`.

This is intentionally publish-time only. Runtime evaluators in C2/C3 still use
single-pass raw answer snapshot evaluation; they do not run graph algorithms.

### Message Baseline

Backend and frontend must use identical messages for new DSL rules:

| Rule | Message |
|---|---|
| missing referenced field | `联动条件引用的字段不存在` |
| self-reference | `联动条件不能引用自身` |
| cycle | `联动条件存在循环依赖` |
| group empty | `联动条件分组至少需要一个条件` |
| both/none allOf anyOf | `联动条件分组必须且只能设置 allOf 或 anyOf` |
| missing field/op | `联动条件必须包含 field 和 op` |
| empty/notEmpty has value | `empty/notEmpty 不应设置 value` |
| scalar op missing/non-scalar value | `联动操作符需要标量 value` |
| in/notIn missing/non-array value | `联动操作符需要数组 value` |
| numeric op references non-number field | `数值比较只能引用数字字段` |

Existing older schema-validation messages remain as-is. C1 only establishes
byte-matched wording for new P3b DSL messages.

## File Estimate

| File | Estimate | Notes |
|---|---:|---|
| `packages/contracts/openapi/labelhub.yaml` | 80 | Linkage schemas + two SchemaField refs |
| `apps/web/src/shared/api/generated/schema.d.ts` | generated | Expected readable TS matching research PoC |
| backend generated Java models | generated | Risk item; inspect before proceeding |
| `SchemaValidator.java` | 180 | stableId index, condition extraction, op/value validation, DFS cycle detection |
| `SchemaValidatorTest.java` | 140 | backend publish-time invalid/valid DSL cases |
| `schemaValidation.ts` | 160 | frontend matching checks and cycle detection |
| `schemaValidation.test.ts` | 110 | frontend matching cases |
| optional `vitest.config.ts` | 5 | only if needed to include new test |
| **Hand-authored total** | **~675** | before generated churn |

Recommended cap:

- hand-authored soft cap: 700;
- hand-authored hard cap: 900;
- generated churn reported separately.

## Drift Prevention

C1 cannot share code between Java and TypeScript, but it can reduce drift:

- use the same rule table structure and message constants in both files;
- use the same stableId graph model;
- add mirrored backend/frontend tests for the same invalid DSL shapes;
- document message constants in the pre-estimate and commit message;
- C4 later adds shared linkage corpus as the long-term proof.

## Verification Plan For Implementation

Implementation should run:

- `pnpm --filter @labelhub/web gen:api`
- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- `pnpm --filter @labelhub/web test`
- backend OpenAPI generation / compile (`mvn -pl services/api compile`, or
  report D-record if Maven Central blocks)
- `mvn -pl services/api test` or focused backend schema validator tests plus
  the accepted D-record if full backend tests need escalation
- `bash scripts/check-protected-endpoints.sh`

Frozen checks:

- `AnswerPayloadValidator.java` unchanged;
- `payloadValidation.ts` unchanged;
- `SchemaFormilyRenderer.tsx` unchanged;
- `SchemaRenderer.tsx` unchanged;
- `validation-corpus.json` unchanged;
- migrations still `11`;
- humanpending still `135`;
- `pom.xml` unchanged unless separately adjudicated.

## Expected OpenAPI Baseline Shift

C1 implementation will change:

```text
304b6d00e35a3649fd10ae9f01392288 -> <new P3b C1 MD5>
```

That is expected and is not a regression. The new MD5 becomes the P3b contract
anchor until the next contract-changing cluster.

## Stop Conditions

- Java codegen remains unavailable after allowed escalation.
- Java generated model shape is too awkward to support C2 without changing the
  contract.
- `pom.xml` changes appear necessary.
- More than the planned OpenAPI schemas/fields change.
- Any runtime validator or renderer file is touched.
- Hand-authored lines exceed 900.
- Backend/frontend message wording diverges for new DSL checks.

## User Adjudication Checklist

1. Approve C1 scope: OpenAPI DSL + generated types + dual-side publish-time
   DSL validation.
2. Approve hand-authored cap 700 soft / 900 hard, generated churn reported
   separately.
3. Approve minimal C1 type pairing: numeric comparison refs must target
   `number`; full membership semantics defer to evaluator/corpus.
4. Approve the P3b DSL publish-validation message set.
5. Approve STOP if Java codegen cannot produce usable generated models without
   contract redesign.
