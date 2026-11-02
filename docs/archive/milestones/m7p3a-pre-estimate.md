# M7-P3a Pre-Estimate: Backend Answer Payload Validation

## Status

Pre-estimate gate. No code lands until user adjudication.

Baseline anchor: `fe8659e`. Backend tests: `408/78`. OpenAPI MD5:
`b6a8344f2c7cc38db958eb333334ebd1`. Migrations: `11`. humanpending:
`133`. Frontend Vitest: `27`. M7-P2 closed.

## The Gap

Auditor baseline grep of the submit path revealed a real security gap:

```
SessionService.submit (services/api/.../session/service/SessionService.java:195)
only checks `answerPayload != null`. It does NOT validate the answer
payload against the schema's field constraints (required / minLength /
maxLength / min / max / pattern). The backend SchemaValidator.java
validates only schema DOCUMENT structure (stableId uniqueness, label
presence, select-needs-options, nested-needs-children), NOT answer
payloads.

The frontend payloadValidation.ts (apps/web/src/entities/labeling/,
120 lines) is currently the ONLY field-level answer validation. A client
that bypasses the frontend (direct API call) can submit an answer that
violates every field constraint, and the backend will persist it.

M7-P3a closes this gap by adding a backend answer-payload validator that
mirrors the frontend payloadValidation rules, making validation
symmetric across both sides. This directly serves rubric 1.4
"字段联动与运行时校验" — the 运行时校验 (runtime validation) half.
```

Concrete code evidence:

- `SessionService.submit`: locks the session, checks ownership/status, checks
  only `answerPayload == null`, then persists the submission.
- `SchemaValidator.java`: validates schema document structure only.
- `payloadValidation.ts`: validates answer values by field type and is
  currently the only field-level submit authority.

## Frontend Reference Spec Analysis

Reference file:

`apps/web/src/entities/labeling/payloadValidation.ts`

The backend must mirror the behavior below exactly.

| Field type | Frontend behavior | Backend mirror needed |
|---|---|---|
| `text` | required / empty handling; value must be string; `minLength`; `maxLength`; `pattern` using `new RegExp(pattern).test(value)` | yes |
| `number` | required / empty handling; value must be JS number and not `NaN`; `min`; `max` | yes |
| `single_select` | required / empty handling; value must be string and must match an option value | yes |
| `multi_select` | required / empty handling; value must be string array and every item must match an option value | yes |
| `date` | required / empty handling; value must be string | yes |
| `file_upload` | required / empty handling; value must be string | yes |
| `nested_object` | required / empty handling; value must be an answer-payload object; children validate recursively | yes |

Important correction to the initial prompt: the frontend does **not** validate
ISO date format. It only checks that `date` values are strings. P3a must not
add backend-only ISO validation unless the user explicitly adjudicates a
frontend spec change, because that would violate "no false positives".

## Empty Semantics Audit

Frontend `isEmpty` treats these as empty:

- `null`
- `undefined`
- `""` empty string
- `[]` empty array
- `{}` empty object when it is an `AnswerPayload`

Java mirror:

- `value == null`
- `value instanceof String s && s.isEmpty()`
- `value instanceof Collection<?> c && c.isEmpty()`
- `value instanceof Map<?, ?> m && m.isEmpty()`

Do **not** use `String.isBlank()`. The frontend treats `"   "` as non-empty;
using `isBlank()` would make the backend stricter than the frontend.

Required behavior:

- If a required field is empty, emit `{ stableId, reason: "此字段必填" }` and
  stop validating that field.
- If a non-required field is empty, accept it and stop validating that field.
- For optional empty nested objects, children are not validated, matching the
  frontend early return.

## Exact Rule Semantics

### Text

- Non-empty value must be a string; otherwise `必须是文本`.
- `minLength`: Java `String.length()` is acceptable for current UTF-16 code
  unit parity with JavaScript `.length`.
- `maxLength`: same count semantics.
- `pattern`: frontend uses `new RegExp(pattern).test(value)`, which is a
  partial match unless the pattern is anchored. Backend should use
  `Pattern.compile(pattern).matcher(value).find()`, not `matches()`.
- Invalid regex pattern returns `正则表达式无效`.

### Number

- Non-empty value must be numeric, not a string.
- JSON/Jackson may provide `Integer`, `Long`, `Double`, or `BigDecimal`;
  backend should accept `Number` and compare via `BigDecimal`.
- JSON cannot normally carry `NaN`, but if a non-finite value reaches the
  validator it should be rejected as `必须是数字`.
- `min`: `不能小于 N`.
- `max`: `不能大于 N`.

### Select

- `single_select`: value must be a string and must equal one of the field
  option values.
- `multi_select`: value must be an array/list, every item must be a string,
  and every item must equal one of the option values.
- Invalid membership reason: `请从选项中选择`.

### Date And File Upload

- Current frontend behavior: non-empty value must be a string.
- Reason for non-string: `必须是文本`.
- No ISO date or file-key format validation in P3a unless user changes the
  frontend reference spec.

### Nested Object

- Non-empty value must be an answer-payload object/map; otherwise `必须是对象`.
- Children validate recursively against the nested map.
- Child errors use the child's `stableId` as the error key, not dot paths.

### Unknown Payload Keys

The frontend validates only fields present in `SchemaField[]` and ignores
unknown payload keys. P3a mirrors validation behavior only; it does not mutate
or sanitize the submitted payload before persistence.

## Backend Validator Design

New backend module:

`services/api/src/main/java/com/labelhub/api/module/submission/validation/`

Recommended files:

- `AnswerPayloadValidator.java`
  - `List<AnswerValidationError> validate(SchemaDocument schema, Map<String, Object> answerPayload)`
  - Recursive validation for `nested_object`
  - Rule mirror of frontend `payloadValidation.ts`
- `AnswerValidationError.java`
  - `stableId`
  - `reason`
- `AnswerValidationException.java`
  - Carries the validation error list

Global handling:

- `GlobalExceptionHandler` adds an `@ExceptionHandler` for
  `AnswerValidationException`.
- Return HTTP 422 with `ApiError` envelope and `fieldErrors` entries.
- Recommended code: `ANSWER_VALIDATION_FAILED`.

The validator is pure and has no persistence side effects.

## OpenAPI Contract Change

This phase intentionally changes OpenAPI.

Endpoint:

`POST /sessions/{sessionId}/submit`

Recommended change:

- Add response `422`.
- Reuse existing `ApiError` schema, because it already contains
  `fieldErrors: ApiFieldError[]`.
- Populate `ApiFieldError.field = stableId` and
  `ApiFieldError.message = reason`.

Alternative:

- Add dedicated schemas such as `AnswerValidationError` and
  `AnswerValidationProblem`.

Recommendation: reuse `ApiError.fieldErrors`. It is smaller contract surface
and matches existing validation handling.

OpenAPI MD5 will change at C1. The phase-close verification doc records the
new baseline.

## Schema Version Resolution

Submit validation must use the schema version bound to the session, not the
latest published schema.

Existing path:

- `SessionService.claim` stores `task.currentSchemaVersionId` into
  `session.schemaVersionId` when a labeler claims the session.
- `SessionService.getDetail` already loads the bound version via
  `schemaVersionMapper.selectById(session.getSchemaVersionId())`.
- `SessionService.submit` already persists
  `submission.schemaVersionId = session.getSchemaVersionId()`.

C3 should reuse the same bound-version lookup:

1. Lock and validate session as today.
2. Load `SchemaVersionEntity` by `session.getSchemaVersionId()`.
3. Parse its schema document.
4. Run `AnswerPayloadValidator` against that document and the submitted
   `answerPayload`.
5. Throw 422 before `submissionMapper.insert(...)` if errors exist.

This preserves M6 schema versioning: a session claimed under schema v2
validates against v2 even if v3 is later published.

## Cross-Field Constraints Deferred

M7-P3a mirrors only the existing single-field static rules. It does not
implement `requiredWhen`, visibility conditions, computed fields, or other
cross-field constraints.

Those belong to M7-P3b linkage DSL.

## Per-Cluster Plan

| Cluster | Scope | Estimate |
|---|---|---:|
| C1 | OpenAPI 422 contract + generated type refresh | ~80 |
| C2 | Backend answer validator, error model, exception, and 422 handler | ~250 |
| C3 | Submit-path wiring with session-bound schema version resolution | ~40 |
| C4 | Symmetry corpus tests on both sides, submit 422 integration test, schema-version-bound validation test | ~280 |
| C5 | Frontend submit flow maps backend 422 field errors into `SchemaFormilyRenderer` errors prop | ~120 |
| C6 | Verification doc + humanpending + screenshots if seedable | N/A |

Total estimate: ~770 lines. Soft cap: 1000. Hard cap: 1300.

## Symmetry Test Corpus Design

Recommended fixture:

`packages/contracts/fixtures/validation-corpus.json`

Both sides load the exact same file:

- Frontend Vitest runs `validatePayload(fields, payload)`.
- Backend JUnit runs `AnswerPayloadValidator.validate(schema, payload)`.

Each case records expected errors as:

```json
{
  "stableId": "field_text",
  "reason": "此字段必填"
}
```

Initial corpus should include:

1. all field types valid
2. required empty string
3. required empty array
4. required empty object
5. optional empty value accepted
6. text minLength failure
7. text maxLength failure
8. text pattern failure using partial-match semantics
9. invalid regex pattern
10. number string rejected
11. number min failure
12. number max failure
13. single-select invalid option
14. multi-select invalid option
15. date and file-upload non-string rejected
16. nested-object recursive child failure
17. unknown payload keys ignored by validation

The corpus is the machine-executable proof of dual-side symmetry.

## Risk Register

| Risk | Resolution |
|---|---|
| Empty semantics drift | C2 mirrors exact list; C4 corpus covers each empty shape. |
| Backend accidentally stricter on whitespace | Ban `String.isBlank()`; corpus includes `"   "` as non-empty. |
| JS regex partial match vs Java full match | Use `matcher.find()`; corpus covers unanchored pattern. |
| Regex flavor incompatibility | Invalid pattern case returns `正则表达式无效`; flavor mismatch is documented and adjudicated if found. |
| Date ISO validation accidentally added | Explicitly forbidden in P3a unless frontend reference changes. |
| Wrong schema version loaded | C4 claim-under-v2 / publish-v3 test proves session-bound validation. |
| Validation errors lose stableId identity | ApiError fieldErrors map `stableId -> field`; frontend C5 maps this back into renderer errors. |
| Auditing failed submits creates noise | Default defer; user adjudication before C1. |
| Existing submit tests used invalid fixtures | C4 updates only fixtures that should be valid under the documented schema. |

## Stop Conditions

- Any cluster exceeds its estimate by 50%.
- Cumulative implementation diff exceeds 1300 lines.
- Shared corpus reveals a true frontend/backend discrepancy that cannot be
  fixed backend-side without changing `payloadValidation.ts`.
- Backend validation would reject a value that frontend accepts.
- Schema version resolution requires risky changes to schema lifecycle code.
- OpenAPI change affects endpoints other than submit validation response.

## Verification Plan

Per cluster:

- `./mvnw -pl services/api test`
- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- `pnpm --filter @labelhub/web test` after the shared corpus and C5 submit
  handling land
- OpenAPI MD5 recorded after C1 and again in C6
- Migrations remain `11`

## User Adjudication Checklist Before C1

1. Split confirmed: P3a validation before P3b linkage. Recommended answer:
   confirmed.
2. Status code for validation failures. Recommended answer: HTTP 422.
3. Error schema. Recommended answer: reuse `ApiError.fieldErrors` rather
   than introduce dedicated answer-validation schemas.
4. Validation-failure auditing. Recommended answer: defer; no audit event in
   P3a.
5. Symmetry corpus location. Recommended answer:
   `packages/contracts/fixtures/validation-corpus.json`.
6. Budget. Recommended answer: soft cap 1000 / hard cap 1300.
