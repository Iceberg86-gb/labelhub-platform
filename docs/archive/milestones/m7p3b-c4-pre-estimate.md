# M7-P3b C4 Pre-Estimate: Shared Linkage Corpus And Integration Coverage

## Status

Pre-estimate gate for M7-P3b C4. No implementation code has landed for this
cluster.

Current anchor: `a16fa53`. OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `458/80`. Frontend
Vitest: `81`. Migrations: `11`. humanpending: `135`.

C4 is the shared-corpus proof cluster for the linkage DSL. It should behave
like P3a C4: one truth fixture, two runners, exact dual-side assertions.

## P3a Corpus Pattern To Reuse

P3a backend:

```java
Path corpusPath = findRepoRoot().resolve("packages/contracts/fixtures/validation-corpus.json");
```

`findRepoRoot()` walks upward until `pnpm-workspace.yaml` exists and fails
loudly with searched paths.

P3a frontend:

```ts
import corpus from '../../../../../packages/contracts/fixtures/validation-corpus.json';
```

P3a has one known asymmetry guard. P3b C4 should be stricter: zero known
asymmetry by default.

## Corpus Shape

Use one JSON array:

```text
packages/contracts/fixtures/linkage-corpus.json
```

Common fields:

```ts
interface LinkageCorpusCase {
  caseId: string;
  kind: 'runtime' | 'publish';
  description: string;
  schema: SchemaDocument;
  payload?: AnswerPayload;
  expectedErrors: Array<RuntimeExpectedError | PublishExpectedError>;
  expectSymmetry: boolean;
}
```

Runtime expected error:

```json
{ "stableId": "details", "reason": "此字段必填" }
```

Publish expected error:

```json
{
  "fieldPath": "fields[1].visibleWhen.field",
  "stableId": "details",
  "reason": "联动条件引用的字段不存在"
}
```

`kind` determines which runner consumes the case.

## Case Count Estimate

Recommended target: `28` to `32` total cases.

Runtime cases: `18` to `21`.

Publish cases: `10`.

Expected `expectSymmetry: false` count: `0`.

### Runtime Case List

1. no `visibleWhen` keeps static required behavior.
2. `eq` true reveals a required field and reports `此字段必填`.
3. `eq` false hides the required field and reports no error.
4. `neq` true reveals a required field.
5. empty driver with `neq` stays hidden / reports no error.
6. scalar `in` true reveals a required field.
7. scalar `notIn` true reveals a required field.
8. collection `in` true via intersection reveals a required field.
9. collection `notIn` false via intersection hides / reports no error.
10. numeric `gt` / `gte` true cases.
11. numeric `lt` / `lte` true cases.
12. `empty` true for missing / empty values.
13. `notEmpty` true for whitespace-only string.
14. `allOf` true and false.
15. `anyOf` true and false.
16. hidden static-required field skips `此字段必填`.
17. hidden `requiredWhen` skips `此字段必填`.
18. visible `requiredWhen` true emits `此字段必填`.
19. visible `requiredWhen` false preserves optional behavior.
20. nested child uses flat stableId reference.
21. hidden nested parent skips parent/child validation.
22. numeric boundary: `1` and `1.0` compare equal.
23. numeric boundary: JSON-safe integer comparison near
    `Number.MAX_SAFE_INTEGER`.
24. numeric boundary: high-precision decimal probe, initially symmetric.

Some related cases can be combined if the expected errors remain clear. The
implementation should keep enough case granularity that failures point at the
specific semantic rule.

### Publish Case List

1. missing reference.
2. self reference.
3. cycle.
4. empty group.
5. group with both `allOf` and `anyOf`.
6. atomic condition missing `field` or `op`.
7. `empty` / `notEmpty` with value.
8. scalar operator with array value.
9. membership operator without array value.
10. numeric comparison against non-number field.

## Numeric Boundary Plan

C4 should not assume a numeric asymmetry. The corpus starts with every numeric
case as `expectSymmetry: true`.

Recommended probes:

- `1` equals `1.0` under `eq`;
- `9007199254740991` (`Number.MAX_SAFE_INTEGER`) behaves symmetrically;
- a high-precision decimal such as `0.12345678901234568` is tested as the
  value that JSON/JS can actually represent, not as an arbitrary-precision
  decimal literal.

If Java and JavaScript disagree, STOP and report:

- exact caseId;
- backend actual;
- frontend actual;
- whether the mismatch is due to JSON parsing, Java `BigDecimal`, or JS
  number precision;
- proposed handling. Do not add `expectSymmetry: false` without adjudication.

## Backend Test Design

Create:

```text
services/api/src/test/java/com/labelhub/api/module/submission/validation/LinkageCorpusTest.java
```

Recommended structure:

- record `LinkageCorpusCase`;
- record `ExpectedRuntimeError`;
- record `ExpectedPublishError`;
- one parameterized test for runtime cases;
- one parameterized test for publish cases;
- one regular test asserting zero `expectSymmetry: false`.

Runtime runner:

```java
SchemaDocument schema = OBJECT_MAPPER.convertValue(corpusCase.schema(), SchemaDocument.class);
Map<String, Object> payload = OBJECT_MAPPER.convertValue(corpusCase.payload(), PAYLOAD_TYPE);
List<ExpectedRuntimeError> actual = answerPayloadValidator.validate(schema, payload)
    .stream()
    .map(error -> new ExpectedRuntimeError(error.stableId(), error.reason()))
    .toList();
```

Publish runner:

- run `schemaValidator.validate(schema)`;
- if `expectedErrors` is empty, assert no throw;
- otherwise assert `InvalidSchemaDocumentException`;
- normalize to `ExpectedPublishError(fieldPath, stableId, reason)`.

Backend `InvalidSchemaDocumentException` currently carries `field` and
`reason`. If it does not carry stableId, the backend publish runner can derive
the expected stableId from the corpus case owner path or compare field/reason
only with a documented normalization. Pre-estimate recommendation: inspect
the exception before implementation and avoid changing production exception
shape in C4.

## Frontend Test Design

Create:

```text
apps/web/src/entities/labeling/linkage.corpus.test.ts
```

Runtime runner:

- `validatePayload(testCase.schema.fields, testCase.payload ?? {})`;
- compare `{ stableId, reason }` exactly.

Publish runner:

- `validateSchemaForUI(testCase.schema)`;
- compare `{ fieldPath, stableId, reason }` exactly.

Asymmetry guard:

- `expect(asymmetricCases).toEqual([])`.

## Submit Integration Test Design

Add one linkage-specific backend integration test in the existing P3a submit
validation integration area.

Minimum assertions:

- a visible `requiredWhen` field missing from payload produces HTTP 422;
- `ApiError.fieldErrors[].field` equals the dependent stableId;
- message is `此字段必填`;
- a hidden dependent field does not appear in fieldErrors.

This test proves C3/C4 corpus semantics are also on the real submit path. It
does not replace the corpus proof.

## Estimated Line Budget

| File | Estimate |
|---|---:|
| `packages/contracts/fixtures/linkage-corpus.json` | 260 |
| `services/api/src/test/java/com/labelhub/api/module/submission/validation/LinkageCorpusTest.java` | 260 |
| `apps/web/src/entities/labeling/linkage.corpus.test.ts` | 170 |
| Submit validation integration test addition | 150 |
| **Hand-authored total** | **~840** |

Recommended cap:

- soft cap: `700`;
- hard cap: `900`.

The corpus JSON is verbose by nature. If implementation approaches the hard
cap, prefer combining closely related runtime cases rather than weakening
coverage of the required semantic categories.

## Frozen Boundaries

C4 must not edit:

- `services/api/src/main/java/com/labelhub/api/module/submission/validation/LinkageEvaluator.java`;
- `apps/web/src/entities/labeling/linkageEvaluator.ts`;
- `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java`;
- `apps/web/src/entities/labeling/payloadValidation.ts`;
- backend/frontend schema validators;
- renderer files;
- OpenAPI / generated API files;
- P3a `validation-corpus.json`;
- pom files;
- migrations;
- humanpending.

If the corpus reveals a discrepancy, report it instead of editing these
surfaces.

## Verification Plan

Targeted:

- backend `LinkageCorpusTest`;
- frontend `linkage.corpus.test.ts`;
- backend submit integration test if Docker/Testcontainers are available;
- P3a backend `AnswerPayloadValidatorCorpusTest`;
- P3a frontend `payloadValidation.corpus.test.ts`.

Full:

- `mvn -pl services/api test` when possible;
- `pnpm --filter @labelhub/web test`;
- protected endpoints check.

Frozen:

- OpenAPI MD5 remains `890e595c6351ee53788d35354b2412a3`;
- migrations remain `11`;
- humanpending remains `135`;
- forbidden implementation files have empty diff.

## Stop Conditions

- Any runtime or publish logic must change to make the corpus pass.
- Any `expectSymmetry: false` case appears.
- Numeric boundary mismatch appears.
- Corpus requires splitting into multiple files.
- P3a corpus behavior changes.
- Hand-authored implementation exceeds `900` lines.

## User Adjudication Checklist

1. Approve one-file corpus with `kind: "runtime" | "publish"`.
2. Approve zero default asymmetric cases.
3. Approve runtime corpus scope as final validation errors only.
4. Approve target case count: `28` to `32` total, with required categories
   above.
5. Approve C4 cap: soft `700`, hard `900`.
