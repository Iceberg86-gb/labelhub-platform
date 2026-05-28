# M7-P3b C2 Pre-Estimate: Backend Linkage Evaluator And Validator Coupling

## Status

Pre-estimate gate for M7-P3b C2. No implementation code has landed for this
cluster.

Current anchor: `23f2ffa`. OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `442/80`. Frontend
Vitest: `65`. Migrations: `11`. humanpending: `135`.

C1 is approved. Java codegen shape is no longer a blocker:

- `LinkageCondition` is a generated marker interface.
- `LinkageAtomicCondition` and `LinkageConditionGroup` implement it.
- `LinkageAtomicCondition.value` is generated as `Object` per Path F.

C2 can therefore use the same `instanceof` model proven in C1
`SchemaValidator`.

## Baseline Evidence

### Current AnswerPayloadValidator Entry

`AnswerPayloadValidator.validate()` currently validates top-level fields with
local field values only:

```java
return fields.stream()
    .flatMap(field -> validateField(field, payload.get(field.getStableId())).stream())
    .toList();
```

`validateField(field, value)` computes required from static field validation:

```java
boolean required = isRequired(field);
boolean empty = isEmpty(value);
```

Nested children recurse through the local nested object:

```java
return children.stream()
    .flatMap(child -> validateField(child, nested.get(child.getStableId())).stream())
    .toList();
```

That structure preserves P3a validation but does not provide the whole payload
snapshot required by `visibleWhen` / `requiredWhen`.

### C2 Coupling Consequence

C2 must change the `validateField` signature to pass a flat linkage value
index:

```java
private List<AnswerValidationError> validateField(
    SchemaField field,
    Object value,
    Map<String, Object> flatValues
)
```

The local `value` still drives P3a validation. The flat `flatValues` map drives
linkage evaluation only.

## Implementation Plan

### 1. Create LinkageEvaluator

File:

```text
services/api/src/main/java/com/labelhub/api/module/submission/validation/LinkageEvaluator.java
```

Public API:

```java
boolean evaluate(LinkageCondition condition, Map<String, Object> flatValues)
```

Recommended internal helpers:

- `evaluateAtomic(LinkageAtomicCondition, Map<String, Object>)`
- `evaluateGroup(LinkageConditionGroup, Map<String, Object>)`
- `isEmpty(Object)` matching P3a semantics
- `valuesEqual(Object left, Object right)`
- `asBigDecimal(Object value)`
- `asScalarList(Object conditionValue)`

Null condition returns false. Callers decide null semantics:

- `visibleWhen == null` means visible.
- `requiredWhen == null` means not conditionally required.

The evaluator is defensive. If generated model data is malformed despite C1
publish validation, it returns false instead of throwing an uncaught exception.

### 2. Build Flat Value Index In AnswerPayloadValidator

Add a private method:

```java
private Map<String, Object> buildFlatValueIndex(List<SchemaField> fields, Map<String, Object> payload)
```

Recursive behavior:

```text
top field stableId -> payload.get(stableId)
nested parent stableId -> parent raw value
child stableId -> nestedMap.get(childStableId) when parent value is a Map
child stableId -> null when parent value is missing or not a Map
```

This deliberately separates condition lookup from P3a local validation.

### 3. Gate Visibility Before P3a Validation

At the top of `validateField()`:

```java
if (!isVisible(field, flatValues)) {
    return List.of();
}
```

`isVisible` semantics:

```java
return field.getVisibleWhen() == null || linkageEvaluator.evaluate(field.getVisibleWhen(), flatValues);
```

Hidden fields skip all remaining validation. This includes hidden
`nested_object` children.

### 4. Replace Static Required With Effective Required

Replace:

```java
boolean required = isRequired(field);
```

with:

```java
boolean required = isRequired(field) || isConditionallyRequired(field, flatValues);
```

`isConditionallyRequired`:

```java
return field.getRequiredWhen() != null
    && linkageEvaluator.evaluate(field.getRequiredWhen(), flatValues);
```

The required error remains P3a's existing `此字段必填`.

### 5. Keep P3a Validation Body Intact

Do not rewrite:

- `validateText`
- `validateNumber`
- `validateSingleSelect`
- `validateMultiSelect`
- `validateStringShape`
- `validateNestedObject` body except passing `flatValues` into recursive
  child calls
- P3a message strings
- P3a `isEmpty` semantics

This cluster wraps P3a; it does not replace it.

## Op Semantics Table

This table is the C2 runtime contract and should be copied into C3/C4 as the
frontend/corpus baseline.

| Op | Missing / empty driver | Valid driver behavior | Type mismatch |
|---|---|---|---|
| `empty` | true | true for `null`, `""`, empty `Collection`, empty `Map`; false for whitespace-only strings | false unless the value is P3a-empty |
| `notEmpty` | false | inverse of `empty` | true for any non-empty unknown type |
| `eq` | false | scalar equality; numeric values compare by `BigDecimal.compareTo()`; no string/number/boolean coercion | false |
| `neq` | false | true when non-empty and `eq` is false | false for empty, true for non-empty mismatched scalar types |
| `in` | false | scalar: any condition item equals field value; collection: any selected item intersects condition array | false |
| `notIn` | false | scalar: no condition item equals field value; collection: no selected item intersects condition array | false for empty, true for non-empty values with no matches |
| `gt` | false | numeric field value > numeric condition value | false |
| `gte` | false | numeric field value >= numeric condition value | false |
| `lt` | false | numeric field value < numeric condition value | false |
| `lte` | false | numeric field value <= numeric condition value | false |

Rationale for `neq` / `notIn` on empty values:

Unanswered driver fields should not make dependent fields visible or
conditionally required merely because the missing value is "not equal" to a
target. This is a deliberate UI-safety choice and must be preserved in the C4
linkage corpus.

## Test Plan

### LinkageEvaluatorTest

Create:

```text
services/api/src/test/java/com/labelhub/api/module/submission/validation/LinkageEvaluatorTest.java
```

Minimum cases:

1. `eq` / `neq` for string, boolean, and numeric values.
2. Numeric equality treats `1` and `1.0` as equal.
3. String `"1"` does not equal number `1`.
4. Missing values make `eq`, `neq`, `in`, `notIn`, and comparisons false.
5. `empty` treats `null`, `""`, empty collection, and empty map as empty.
6. Whitespace-only string is not empty.
7. `in` / `notIn` work for scalar values.
8. `in` / `notIn` work for collection values by intersection.
9. `gt` / `gte` / `lt` / `lte` use numeric comparison and return false for
   non-numeric field values.
10. `allOf` and `anyOf` groups evaluate one-level atomic conditions.
11. Malformed / unknown condition implementation returns false.

### AnswerPayloadValidatorLinkageTest

Create:

```text
services/api/src/test/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidatorLinkageTest.java
```

Minimum cases:

1. Hidden static-required field does not emit `此字段必填`.
2. Visible static-required field still emits `此字段必填`.
3. `requiredWhen` true emits `此字段必填` for empty optional field.
4. `requiredWhen` false keeps optional field optional.
5. Hidden field skips type/min/max/pattern validation.
6. Hidden `nested_object` parent skips child required validation.
7. Visible `nested_object` child can use `requiredWhen` against a top-level
   flat stableId.
8. Nested child `visibleWhen` can reference a sibling child by flat stableId.
9. No linkage fields preserves P3a behavior for a representative invalid
   text/number/select case.

### Existing Guard

Run existing C4/P3a corpus unchanged:

```bash
mvn -pl services/api -Dtest=AnswerPayloadValidatorCorpusTest test
```

This is the proof that schemas without linkage fields behave exactly as P3a.

## File Estimate

| File | Estimate | Notes |
|---|---:|---|
| `LinkageEvaluator.java` | 190 | condition dispatch, op evaluation, numeric/equality helpers, defensive false semantics |
| `AnswerPayloadValidator.java` | 130 | constructor injection, flat index, signature threading, visibility gate, effective required |
| `LinkageEvaluatorTest.java` | 190 | runtime op table coverage |
| `AnswerPayloadValidatorLinkageTest.java` | 150 | validator coupling and nested double-view coverage |
| **Hand-authored total** | **~660** | below hard cap, near soft cap |

Recommended caps:

- soft: `600`
- hard: `800`

## Drift Prevention

C2 is backend-only, but its semantics are the future C3/C4 baseline:

- Put op semantics in focused evaluator tests with explicit method names.
- Keep message strings unchanged by reusing P3a required behavior.
- Do not add new runtime messages in C2.
- Ensure C4 linkage corpus copies the C2 semantics table, especially empty
  driver behavior for `neq` / `notIn`.

## Risk Register

| Risk | Resolution |
|---|---|
| Flat index loses nested child values | Recursive flat-index test through nested child `requiredWhen`. |
| Local nested validation and flat condition lookup get mixed up | `validateField(field, localValue, flatValues)` signature makes the two views explicit. |
| Empty semantics diverge from P3a | Reuse the same helper semantics and test whitespace-only strings. |
| Missing values make dependent fields unexpectedly visible through `neq` | Lock `neq` / `notIn` false for empty drivers. |
| Conditional required introduces a new message | Reuse existing P3a required branch, no new message. |
| Hidden field still runs nested/type validation | Visibility gate is the first line in `validateField()`. |
| C2 breaks P3a corpus | Run `AnswerPayloadValidatorCorpusTest` unchanged before commit. |

## Verification Plan For Implementation

- `mvn -pl services/api -Dtest=LinkageEvaluatorTest test`
- `mvn -pl services/api -Dtest=AnswerPayloadValidatorLinkageTest test`
- `mvn -pl services/api -Dtest=AnswerPayloadValidatorCorpusTest test`
- `mvn -pl services/api test` with escalation if sandbox socket restrictions
  recur
- `bash scripts/check-protected-endpoints.sh`
- OpenAPI MD5 remains `890e595c6351ee53788d35354b2412a3`
- migrations remain `11`
- humanpending remains `135`
- forbidden frontend/contract/corpus/submit-wiring files have empty diff

## Stop Conditions

- Any P3a corpus case fails.
- Runtime evaluator requires OpenAPI or generated type changes.
- Frontend/renderer changes become necessary.
- Implementing C2 requires touching `SessionService.java`.
- Hand-authored lines exceed `800`.
- Op semantics cannot be tested without ambiguity.

## User Adjudication Checklist

1. Approve backend-only C2 scope: new `LinkageEvaluator` plus
   `AnswerPayloadValidator` coupling.
2. Approve the C2 op semantics table, including non-empty-only behavior for
   `neq` and `notIn`.
3. Approve the double-view payload design: local values for P3a validation,
   flat stableId index for linkage conditions.
4. Approve C2 cap: soft `600`, hard `800`.
