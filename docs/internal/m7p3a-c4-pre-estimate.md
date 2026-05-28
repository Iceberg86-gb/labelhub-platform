# M7-P3a C4 Pre-Estimate: Shared Validation Corpus

## Status

Pre-estimate gate for M7-P3a C4. No code or tests have landed for this
cluster. C4 awaits user adjudication of the corpus shape, line estimate, and
the scientific-notation known-asymmetry handling.

Current anchor: `08c7c4f` (C3 submit-path wiring). OpenAPI MD5:
`304b6d00e35a3649fd10ae9f01392288`. Migrations: `11`. humanpending:
`133`.

## Current Evidence

### Locked Logic Files

- Frontend reference: `apps/web/src/entities/labeling/payloadValidation.ts`
- Backend mirror: `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java`
- Submit wiring: `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java`

C4 verifies these files and must not edit them.

### Backend Validator Test Gap

`AnswerPayloadValidator` currently has no dedicated corpus or unit test file.
Its correctness has been protected by C2 manual audit and C3 submit-path
fixture behavior. C4 is the first machine-executable symmetry proof for the
validator itself.

## Message Baseline

The frontend and backend currently share these exact reason strings:

| Rule family | Message |
|---|---|
| required | `此字段必填` |
| text non-string | `必须是文本` |
| number non-number | `必须是数字` |
| select invalid | `请从选项中选择` |
| nested object invalid | `必须是对象` |
| pattern no match | `格式不正确` |
| invalid regex | `正则表达式无效` |
| minLength | `最少 N 字` |
| maxLength | `最多 N 字` |
| number min | `不能小于 X` |
| number max | `不能大于 X` |

Template families are minLength, maxLength, number min, and number max.

## Known Scientific-Notation Asymmetry

The known C2 residual gap is numeric message formatting for small values.

Backend:

```java
"不能小于 " + formatNumber(min)
```

`formatNumber()` uses:

```java
BigDecimal normalized = value.stripTrailingZeros();
if (normalized.scale() < 0) {
    normalized = normalized.setScale(0);
}
return normalized.toPlainString();
```

For `min = 0.0000001`, backend output is:

```text
不能小于 0.0000001
```

Frontend:

```ts
`不能小于 ${field.validation.min}`
```

JavaScript `String(0.0000001)` renders as:

```text
1e-7
```

So the frontend message is:

```text
不能小于 1e-7
```

C4 records this as a single `expectSymmetry: false` case. It does not fix the
gap. A future adjudication can choose whether to emulate JavaScript number
formatting in Java, change frontend formatting, or document the limitation.

## Corpus Shape

Path:

`packages/contracts/fixtures/validation-corpus.json`

Each case:

```json
{
  "caseId": "number-min-scientific-known-asymmetry",
  "description": "Backend formats 1e-7 as 0.0000001; frontend formats it as 1e-7.",
  "schema": {
    "fields": []
  },
  "payload": {},
  "expectedErrors": [
    { "stableId": "score", "reason": "不能小于 0.0000001" }
  ],
  "expectSymmetry": false
}
```

`expectedErrors` always stores the backend canonical result. For
`expectSymmetry: true`, frontend must match it exactly. For
`expectSymmetry: false`, frontend tests assert the rule family and document the
known message drift.

## Planned Corpus Cases

| Case ID | Purpose | expectSymmetry |
|---|---|---|
| `valid-all-field-types` | legal payload for all static field types | true |
| `required-missing` | required absent value | true |
| `required-empty-string` | required empty string | true |
| `text-non-string` | text receives a number/object | true |
| `number-non-number` | number receives a string | true |
| `single-select-invalid-option` | value outside options | true |
| `multi-select-invalid-option` | one selected item outside options | true |
| `nested-object-non-object` | nested field receives string/number | true |
| `text-min-length` | `最少 N 字` | true |
| `text-max-length` | `最多 N 字` | true |
| `number-min-ordinary` | `不能小于 10` | true |
| `number-max-ordinary` | `不能大于 100` | true |
| `nested-child-flat-stable-id` | child error uses child stableId, not dot path | true |
| `regex-partial-match-pass` | `\d+` accepts `abc123def` | true |
| `regex-partial-match-fail` | `\d+` rejects `abcdef` | true |
| `required-whitespace-passes` | `"   "` is non-empty; protects against `isBlank()` | true |
| `date-non-iso-passes` | date string shape only; no ISO validation | true |
| `file-upload-string-passes` | file_upload string shape only | true |
| `number-min-scientific-known-asymmetry` | backend `0.0000001` vs frontend `1e-7` | false |
| `number-min-small-decimal-symmetric-control` | ordinary small decimal such as `0.001` remains symmetric | true |

Target: 20 corpus cases. If implementation can combine equivalent cases
without losing coverage, minimum acceptable count is 18.

## Backend Test Design

File:

`services/api/src/test/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidatorCorpusTest.java`

Responsibilities:

- Load `packages/contracts/fixtures/validation-corpus.json`.
- Convert each `schema` object to generated `SchemaDocument`.
- Convert each `payload` object to `Map<String, Object>`.
- Run `new AnswerPayloadValidator().validate(schema, payload)`.
- Assert backend errors exactly equal `expectedErrors` for every case,
  including `expectSymmetry: false` cases.

Path C helper:

```java
private static Path findRepoRoot() {
    Path current = Path.of("").toAbsolutePath();
    List<Path> searched = new ArrayList<>();
    while (current != null) {
        searched.add(current);
        if (Files.exists(current.resolve("pnpm-workspace.yaml"))) {
            return current;
        }
        current = current.getParent();
    }
    throw new AssertionError("Unable to find repo root. Searched: " + searched);
}
```

No `pom.xml` changes are allowed.

## Frontend Test Design

File:

`apps/web/src/entities/labeling/payloadValidation.corpus.test.ts`

Responsibilities:

- Load the same JSON fixture from `packages/contracts/fixtures/validation-corpus.json`.
- Run `validatePayload(corpusCase.schema.fields, corpusCase.payload)`.
- For `expectSymmetry: true`, assert exact `expectedErrors`.
- For `expectSymmetry: false`, assert that frontend produces a min failure
  whose reason includes `不能小于`, without asserting backend wording.

This preserves the frontend as a reference while still surfacing the known
formatting mismatch in a non-flaky way.

## Integration Test Design

File:

`services/api/src/test/java/com/labelhub/api/integration/SubmitValidationIntegrationTest.java`

Two test families:

1. **Submit 422 contract**
   - Create or reuse integration fixtures for a task, dataset item, schema, and
     session.
   - Claim a session.
   - Submit invalid answer payload.
   - Assert HTTP 422.
   - Assert `ApiError.fieldErrors` contains `{ field: stableId, message:
     reason }`.
   - This explicitly documents that `ApiFieldError.field` means dynamic
     `SchemaField.stableId` for submit validation.

2. **Session-bound schema version**
   - Publish or create v2 schema.
   - Claim a session under v2.
   - Publish or switch the task to v3 with different constraints.
   - Submit a payload that distinguishes v2 from v3.
   - Assert validation follows v2, because C3 uses
     `session.getSchemaVersionId()`.

The fixture style will follow existing integration patterns in:

- `services/api/src/test/java/com/labelhub/api/integration/SessionApiIntegrationTest.java`
- `services/api/src/test/java/com/labelhub/api/integration/SubmissionLifecycleIntegrationTest.java`
- `services/api/src/test/java/com/labelhub/api/integration/SchemaApiIntegrationTest.java`

## Estimate

| File | Estimate |
|---|---:|
| `validation-corpus.json` | 260 |
| `AnswerPayloadValidatorCorpusTest.java` | 170 |
| `payloadValidation.corpus.test.ts` | 100 |
| `SubmitValidationIntegrationTest.java` | 220 |
| **Total** | **750** |

Soft cap: 1000. Hard cap: 1300.

This is larger than the original C4 estimate because the C4 prompt now
requires both corpus symmetry tests and two integration proofs. It remains
within the M7-P3a cap.

## Expected Test Count Changes

Backend tests:

- Current C3 baseline: `408/78`.
- Expected increase: corpus parameterized cases plus two integration tests.
- New total depends on whether JUnit counts each corpus case as a separate
  parameterized invocation. This increase is expected and must be reported.

Frontend Vitest:

- Current baseline: `27`.
- Expected increase: one test per exact-symmetry corpus case plus one known
  asymmetry test group, or a smaller parameterized count if implemented with
  `it.each`.

## Frozen Checks

C4 implementation must report:

- `git diff -- services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java` is empty.
- `git diff -- apps/web/src/entities/labeling/payloadValidation.ts` is empty.
- `git diff -- services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java` is empty.
- `git diff -- services/api/pom.xml pom.xml` is empty.
- OpenAPI MD5 remains `304b6d00e35a3649fd10ae9f01392288`.
- Migration count remains `11`.
- humanpending count remains `133`.

## Adjudication Questions

1. Approve C4 as a 750-line test-and-corpus cluster under soft cap 1000 /
   hard cap 1300?
2. Approve target corpus count of 20 cases, minimum 18 if equivalent cases are
   combined?
3. Approve exactly one `expectSymmetry: false` case:
   `number-min-scientific-known-asymmetry`?
4. Approve Path C `findRepoRoot()` implementation and zero `pom.xml` changes?
5. Approve backend/frontend test count increases from C3 baselines as expected?

## Stop Conditions For Implementation

- More than one case needs `expectSymmetry: false`.
- A corpus mismatch appears in an ordinary case and cannot be resolved without
  changing locked logic.
- Integration setup requires contract, migration, auth, audit, or service logic
  changes.
- Path C corpus loading fails without `pom.xml` changes.
- Changed lines exceed 1000 before the integration tests are complete.
