# M7-P3b C1 Scope-Budget: Linkage DSL Contract And Publish-Time Validation

## Status

Pre-estimate gate for M7-P3b C1. No implementation code has landed for this
cluster. Current anchor: `7e73e4b` (M7-P3b research). OpenAPI MD5 remains
`304b6d00e35a3649fd10ae9f01392288`; migrations `11`; humanpending `135`.

C1 is the first implementation cluster of P3b and is expected to change the
OpenAPI contract. The MD5 change is expected and becomes the new P3b contract
anchor after implementation.

## Phase Character

M7-P3b implements field linkage and cross-field runtime rules. C1 does not
implement runtime evaluation yet. It establishes the contract and publish-time
schema guardrails required before evaluators can safely exist:

- Add the linkage DSL contract to `SchemaField`.
- Regenerate frontend and backend generated types.
- Extend backend and frontend schema-document validation so malformed DSL
  cannot be published.
- Keep runtime validator, renderer, corpus, and designer-builder work out of
  C1.

## Locked DSL Shape

C1 implements the research-approved P3b v1 shape:

- Declarative JSON condition objects only; no string expression DSL and no
  `eval`.
- Atomic condition: `{ field, op, value? }`.
- One-level group: `{ allOf: AtomicCondition[] }` or
  `{ anyOf: AtomicCondition[] }`.
- No recursive groups in v1.
- Whitelist ops: `eq`, `neq`, `in`, `notIn`, `gt`, `gte`, `lt`, `lte`,
  `empty`, `notEmpty`.
- `field` is a `SchemaField.stableId`, including nested fields through the
  existing flat stableId identity.
- `visibleWhen` and `requiredWhen` live on `SchemaField`.

## Allowed Surfaces

Contract and generated types:

- `packages/contracts/openapi/labelhub.yaml`
- `apps/web/src/shared/api/generated/schema.d.ts`
- `services/api/target/generated-sources/openapi/...` generated Java models

Backend publish-time validation:

- `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaValidator.java`
- `services/api/src/test/java/com/labelhub/api/module/schema/util/SchemaValidatorTest.java`

Frontend publish-time validation:

- `apps/web/src/entities/schema/schemaValidation.ts`
- new or existing frontend schema-validation test under `apps/web/src/entities/schema/`
- `apps/web/vitest.config.ts` only if needed to collect the new test

## Forbidden Surfaces

- `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java`
- `apps/web/src/entities/labeling/payloadValidation.ts`
- `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`
- `apps/web/src/features/labeling/SchemaRenderer.tsx`
- `packages/contracts/fixtures/validation-corpus.json`
- `packages/contracts/fixtures/linkage-corpus.json` (C4 owns it)
- `pom.xml` / `services/api/pom.xml` unless codegen truly cannot proceed and
  user adjudicates first
- migrations
- humanpending

## Scope

### 1. OpenAPI Contract

Add:

- `LinkageConditionOp`
- `LinkageConditionValue`
- `LinkageAtomicCondition`
- `LinkageConditionGroup`
- `LinkageCondition`
- `SchemaField.visibleWhen`
- `SchemaField.requiredWhen`

The final contract should match the research one-level shape.

### 2. Generated Types

Run frontend generation:

```bash
pnpm --filter @labelhub/web gen:api
```

Run backend generation through the existing Maven OpenAPI generator path. If
Maven Central or sandbox limitations block it, report a D-record and stop for
adjudication before hand-writing or patching generated model code.

C1 must inspect the generated Java model shape before C2 is authorized.

### 3. Backend Publish-Time DSL Validation

Extend `SchemaValidator.validate(SchemaDocument)` because
`SchemaService.publishVersion()` already calls it before persistence.

Validate:

- referenced stableId exists;
- no self-reference;
- no dependency cycle through `visibleWhen` / `requiredWhen`;
- group has exactly one of `allOf` or `anyOf`;
- group condition arrays are non-empty;
- atomic condition has `field` and `op`;
- `empty` / `notEmpty` do not set `value`;
- scalar ops (`eq`, `neq`, `gt`, `gte`, `lt`, `lte`) set scalar `value`;
- set ops (`in`, `notIn`) set array `value`;
- numeric comparison ops (`gt`, `gte`, `lt`, `lte`) reference `number` fields.

Recommended C1 boundary for type-pairing:

- Do numeric-op target-type validation in C1.
- Allow `eq` / `neq` / `in` / `notIn` for scalar-like fields in C1.
- Defer nuanced evaluator-specific membership semantics to C2/C4 corpus.
- Do not block C1 on a full type-matrix editor policy.

### 4. Frontend Publish-Time DSL Validation

Extend `validateSchemaForUI()` with the same checks and the same user-visible
messages. This is not runtime validation. It is schema authoring feedback.

### 5. Tests

Add or extend tests to cover:

- generated TS type presence is covered by `typecheck`;
- backend rejects bad op/value shape;
- backend rejects missing field reference;
- backend rejects self-reference;
- backend rejects cycle;
- backend accepts a valid one-level group;
- frontend reports matching reasons for the same invalid DSL shapes.

## Message Policy

C1 introduces new DSL publish-validation messages. The backend and frontend
messages must match byte-for-byte for the new DSL rules even though older
schema-document messages are not currently bilingual-symmetric.

Proposed message set:

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

Implementation may refine field paths, but must not create separate backend
and frontend wording.

## Budget

C1 has two budget buckets:

| Bucket | Estimate | Notes |
|---|---:|---|
| Hand-authored contract + validation + tests | 620 | OpenAPI, Java validator logic, TS validator logic, focused tests |
| Generated code churn | 400-900 | Depends on Java oneOf output; audited but not used as complexity signal |

Recommended C1 caps:

- Hand-authored soft cap: 700 lines.
- Hand-authored hard cap: 900 lines.
- Generated churn watch: report separately; stop if generated output is
  unexpectedly unreadable or exceeds expectations by creating broad unrelated
  model churn.

This is higher than the initial 500/700 instinct because C1 includes dual-side
publish validation, cycle detection, and test coverage, not only OpenAPI.

## Stop Conditions

- Java codegen fails and cannot be rerun with allowed escalation.
- Generated Java model shape makes C2 evaluator infeasible without contract
  redesign.
- Any implementation requires `pom.xml` changes.
- Runtime files are touched (`AnswerPayloadValidator`, `payloadValidation`,
  renderer files).
- OpenAPI diff expands beyond linkage DSL schemas and `SchemaField`
  `visibleWhen` / `requiredWhen`.
- Hand-authored diff exceeds 900 lines.
- OpenAPI MD5 changes more than once because of unrelated contract edits.

## Verification Plan

Implementation C1 must verify:

- frontend `gen:api`, `typecheck`, `build`;
- backend generated models compile;
- backend schema validator tests pass;
- frontend schema validation tests pass under Vitest;
- protected endpoints check passes;
- OpenAPI MD5 changes from `304b6d00e35a3649fd10ae9f01392288` to a new
  recorded value;
- migrations remain `11`;
- humanpending remains `135`;
- forbidden files have empty diff.

## Gate Questions

1. Approve C1 as contract + generated types + dual-side publish-time DSL
   validation?
2. Approve hand-authored cap 700 soft / 900 hard, with generated code churn
   reported separately?
3. Approve doing minimal type-pairing in C1: numeric comparison ops only target
   `number`, while nuanced membership semantics defer to evaluator/corpus?
4. Approve the proposed Chinese DSL publish-validation message set as the
   dual-side wording baseline?
5. Approve STOP if Java codegen remains D-record only after reasonable
   escalation, rather than proceeding into evaluator implementation?
