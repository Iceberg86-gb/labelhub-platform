# M7-P3b C2 Scope-Budget: Backend Linkage Evaluator And Answer Validation Coupling

## Status

Pre-estimate gate for M7-P3b C2. No implementation code has landed for this
cluster.

Current anchor: `23f2ffa` (M7-P3b C1). OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `442/80`. Frontend
Vitest: `65`. Migrations: `11`. humanpending: `135`.

C1 closed the Java codegen blocker. Generated backend models are usable for
C2: `LinkageCondition` is a marker interface,
`LinkageAtomicCondition` / `LinkageConditionGroup` implement it, and
`LinkageAtomicCondition.value` is `Object` per Path F. C2 therefore does not
need contract changes.

## Phase Character

C2 is the backend runtime semantics cluster for P3b. It adds the Java
`LinkageEvaluator` and couples linkage visibility / conditional required into
the existing P3a `AnswerPayloadValidator`.

The cluster is backend-only. It does not touch frontend evaluator work,
renderer visibility, linkage corpus, designer configuration, OpenAPI, or the
submit wiring. P3a submit wiring already routes every submit through
`AnswerPayloadValidator`; C2 changes what that validator does internally when
fields carry `visibleWhen` / `requiredWhen`.

## Locked Runtime Semantics

Research `7e73e4b` and C1 adjudication lock the runtime model:

- Single-pass evaluation over the raw `AnswerPayload` snapshot.
- No topology sorting, no iterative stabilization, and no runtime cycle
  detection. C1 publish-time validation rejects missing refs, self-refs, and
  cycles before schemas can be published.
- Conditions read raw payload values by flat `SchemaField.stableId`, including
  nested child stableIds.
- Hidden field values are not stripped from the payload. C2 only decides
  whether validation runs.
- If `visibleWhen` evaluates false, the field skips all validation, including
  static `validation.required`, `requiredWhen`, type checks, nested child
  checks, and min/max/pattern checks.
- If a field is visible and `requiredWhen` evaluates true, effective required
  is `staticRequired || conditionalRequired`.
- Conditional required reuses P3a's existing message: `此字段必填`.

## Existing Backend Coupling Point

Current `AnswerPayloadValidator` shape:

```java
public List<AnswerValidationError> validate(SchemaDocument schema, Map<String, Object> answerPayload) {
    List<SchemaField> fields = schema == null || schema.getFields() == null ? List.of() : schema.getFields();
    Map<String, Object> payload = answerPayload == null ? Map.of() : answerPayload;
    return fields.stream()
        .flatMap(field -> validateField(field, payload.get(field.getStableId())).stream())
        .toList();
}

private List<AnswerValidationError> validateField(SchemaField field, Object value) {
    boolean required = isRequired(field);
    ...
}
```

Nested objects currently recurse with a local nested map:

```java
return children.stream()
    .flatMap(child -> validateField(child, nested.get(child.getStableId())).stream())
    .toList();
```

C2 must preserve local-value validation for P3a rules while adding a separate
flat value index for linkage evaluation.

## Double-View Design

C2 needs two payload views:

1. **Local value view**: the existing value passed into `validateField()`.
   This remains the source of truth for P3a type and rule validation. Nested
   child validation still reads `nested.get(child.stableId)`.

2. **Flat linkage value index**: a root-snapshot map of
   `stableId -> raw payload value` for all schema fields, including nested
   children. This is the source of truth for `visibleWhen` and `requiredWhen`.

Flat index construction:

- Top-level field: `field.stableId -> payload.get(field.stableId)`.
- Nested object parent: parent stableId maps to its raw parent value.
- Nested child: if the parent value is a map, child stableId maps to the nested
  map's child value; otherwise child stableId maps to `null`.
- StableId collisions are not handled in C2 because C1/P3a
  `SchemaValidator` already enforces global stableId uniqueness across nested
  fields before publish.

This keeps C2 aligned with P3a's flat child error identity while preserving
P3a's local nested validation behavior.

## Allowed Files And Budget

| File | Purpose | Estimate |
|---|---|---:|
| `services/api/src/main/java/com/labelhub/api/module/submission/validation/LinkageEvaluator.java` | New runtime evaluator for `LinkageCondition` AST against flat values | 190 |
| `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java` | Build flat index, inject evaluator, gate hidden fields, compute effective required | 130 |
| `services/api/src/test/java/com/labelhub/api/module/submission/validation/LinkageEvaluatorTest.java` | Op semantics table tests: equality, numeric compare, membership, empty, missing/type mismatch, groups | 190 |
| `services/api/src/test/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidatorLinkageTest.java` | Coupling tests: hidden required skip, requiredWhen, nested parent/child visibility, P3a behavior preservation | 150 |
| **Hand-authored total** | | **~660** |

Recommended C2 caps:

- hand-authored soft cap: `600`;
- hand-authored hard cap: `800`.

The estimate is intentionally near the soft cap because the evaluator is small
but the semantics tests must be explicit. If implementation exceeds `600`
lines for clarity while staying below `800`, that is acceptable but should be
reported.

## Forbidden Surfaces

- `packages/contracts/openapi/labelhub.yaml`
- generated frontend/backend types
- `apps/web/**`
- `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`
- `apps/web/src/features/labeling/SchemaRenderer.tsx`
- `apps/web/src/entities/labeling/payloadValidation.ts`
- `packages/contracts/fixtures/validation-corpus.json`
- any future `linkage-corpus.json` (C4 owns it)
- `SessionService.java` submit wiring
- `pom.xml` / `services/api/pom.xml`
- migrations
- humanpending

## Runtime Op Semantics Baseline

These semantics become the backend contract for C2 and the implementation
target for C3 frontend evaluator and C4 shared linkage corpus.

| Op | Semantics |
|---|---|
| `empty` | true when the referenced field value is P3a-empty: `null`, `""`, empty `Collection`, or empty `Map`. Whitespace-only strings are not empty. |
| `notEmpty` | logical inverse of `empty`. |
| `eq` | false when the field value is empty. Otherwise true when field value and condition value are equal with no string/number/boolean coercion. Numeric values compare by numeric value through `BigDecimal.compareTo()`, so `1` equals `1.0`. |
| `neq` | false when the field value is empty. Otherwise true when `eq` is false. This intentionally avoids showing fields merely because the driver field is unanswered. |
| `in` | false when the field value is empty. For scalar field values, true when any condition-array item equals the field value using `eq` equality. For collection field values, true when any selected item intersects any condition-array item. |
| `notIn` | false when the field value is empty. For scalar field values, true when no condition-array item equals the field value. For collection field values, true when no selected item intersects any condition-array item. |
| `gt` / `gte` / `lt` / `lte` | false when the field value or condition value is non-numeric or empty. Otherwise compare through `BigDecimal`. |

Malformed condition values should not throw from the evaluator. Publish-time
validation should make them unreachable, but runtime evaluator code remains
defensive and evaluates malformed / type-mismatched operations to false.

## Required Coupling Behavior

- No `visibleWhen`: field is visible.
- `visibleWhen` false: return `List.of()` before required/type/nested checks.
- No `requiredWhen`: conditional required is false.
- `requiredWhen` true and field visible: reuse P3a required behavior and
  message `此字段必填`.
- Hidden parent `nested_object`: parent and all children skip validation.
- Visible parent `nested_object`: parent object shape validates first; then
  each child runs through the same visibility/effective-required gate.

## Guardrails

P3a's shared validation corpus is a guardrail:

- For schemas without linkage fields, behavior must be byte-identical to P3a.
- `AnswerPayloadValidatorCorpusTest` must still pass without modifying
  `packages/contracts/fixtures/validation-corpus.json`.
- Existing P3a messages must remain unchanged.

## Risk Register

| Risk | Resolution |
|---|---|
| Flat index does not include nested child stableIds | Build flat index from schema + root payload recursively; add nested requiredWhen/visibleWhen tests. |
| Runtime evaluator accidentally uses local nested map for condition refs | Pass the same flat index through every `validateField()` call; tests reference sibling/top-level fields from nested children. |
| Missing driver value makes `neq` / `notIn` unexpectedly true | Lock C2 semantics: all non-empty ops are false for empty driver values; corpus in C4 preserves this. |
| Numeric equality differs between Java numeric types | Convert numeric values through `BigDecimal`; compare with `compareTo()` instead of `equals()`. |
| Hidden nested parent still validates children | Visibility gate runs before nested object shape/children validation. |
| C2 weakens P3a static validation | Existing P3a corpus test remains unchanged and green. |
| Malformed published DSL reaches runtime | C1 publish validator rejects it; C2 evaluator still returns false defensively instead of throwing. |

## Verification Plan For Implementation

- `mvn -pl services/api -Dtest=LinkageEvaluatorTest test`
- `mvn -pl services/api -Dtest=AnswerPayloadValidatorLinkageTest test`
- `mvn -pl services/api -Dtest=AnswerPayloadValidatorCorpusTest test`
- `mvn -pl services/api test` (sandbox socket D-record accepted only after
  attempted run and escalated rerun if needed)
- `bash scripts/check-protected-endpoints.sh`
- OpenAPI MD5 remains `890e595c6351ee53788d35354b2412a3`
- migrations remain `11`
- humanpending remains `135`
- forbidden frontend/contract/corpus/submit-wiring files have empty diff

## Stop Conditions

- Any P3a corpus case changes behavior.
- `AnswerPayloadValidator` needs broader refactor beyond flat index +
  visibility gate + effective required.
- Runtime evaluator requires contract/schema changes.
- Frontend or renderer work appears necessary.
- C2 implementation exceeds `800` hand-authored lines.
- Op semantics prove ambiguous enough that they cannot be captured in tests.

## User Adjudication Checklist

1. Approve C2 as backend-only: `LinkageEvaluator` + `AnswerPayloadValidator`
   coupling, with no frontend/renderer/contract/corpus work.
2. Approve the runtime op semantics table, especially:
   - empty driver values make `neq` / `notIn` false;
   - numeric equality uses `BigDecimal.compareTo()`;
   - multi-select `in` / `notIn` uses intersection semantics.
3. Approve the double-view design: local nested values for P3a validation,
   flat stableId index for linkage conditions.
4. Approve C2 cap: hand-authored soft `600`, hard `800`.
