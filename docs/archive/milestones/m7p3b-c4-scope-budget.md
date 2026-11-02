# M7-P3b C4 Scope-Budget: Shared Linkage Corpus Symmetry Proof

## Status

Pre-estimate gate for M7-P3b C4. No implementation code has landed for this
cluster.

Current anchor: `a16fa53` (M7-P3b C3). OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `458/80`. Frontend
Vitest: `81`. Migrations: `11`. humanpending: `135`.

C2 added the backend linkage evaluator and validator coupling. C3 added the
frontend linkage evaluator, frontend validation coupling, and outer visible
schema filtering. C4 turns the manual mirroring between C2 and C3 into a
machine-executable dual-side proof.

## Phase Character

C4 is P3b's load-bearing symmetry proof cluster. It creates one shared
`linkage-corpus.json` fixture that both sides read and execute. This mirrors
the P3a C4 pattern, but P3b covers two semantic boundaries:

1. runtime answer validation after visibility / `requiredWhen` evaluation;
2. publish-time DSL validation for malformed linkage definitions.

C4 is a test/corpus cluster. It must not change C1/C2/C3 runtime logic.

## Corpus Decision

Use one shared corpus file:

```text
packages/contracts/fixtures/linkage-corpus.json
```

Each case carries:

```json
{
  "caseId": "hidden-required-skips",
  "kind": "runtime",
  "description": "...",
  "schema": { "fields": [] },
  "payload": {},
  "expectedErrors": [],
  "expectSymmetry": true
}
```

or:

```json
{
  "caseId": "cycle-rejected",
  "kind": "publish",
  "description": "...",
  "schema": { "fields": [] },
  "expectedErrors": [
    {
      "fieldPath": "fields[1].visibleWhen",
      "stableId": "a",
      "reason": "联动条件存在循环依赖"
    }
  ],
  "expectSymmetry": true
}
```

One corpus file keeps runtime and publish coverage tied to the same DSL
contract. Splitting into separate fixtures would make it easier for coverage
to drift.

## Asymmetry Policy

Default: **zero** `expectSymmetry: false` cases.

Numeric boundary cases start as `expectSymmetry: true`. If implementation
discovers a real Java `BigDecimal` vs JavaScript `number` divergence, C4 must
STOP and report before adding any false case. The allowed false-case count is
not pre-approved. This is stricter than P3a because no confirmed P3b
asymmetry exists yet.

## Runtime Cases

Runtime cases execute the final validation result only:

```text
schema + payload -> expected AnswerPayload validation errors
```

They do not assert rendered `visibleFields`. C3 already covers visible schema
tree filtering directly. C4 proves visibility indirectly through validation:
hidden fields do not emit errors, and visible conditional-required fields do.

Required runtime coverage:

- no `visibleWhen` means visible;
- atomic `eq` true and false;
- `neq`;
- `in` / `notIn` for scalar values;
- `in` / `notIn` for collection intersection;
- `gt` / `gte` / `lt` / `lte`;
- `empty` / `notEmpty` for `null`, `""`, `[]`, `{}`, and whitespace-only string;
- top-level `allOf` true and false;
- top-level `anyOf` true and false;
- hidden field skips static required;
- hidden field skips `requiredWhen`;
- visible field with `requiredWhen` true emits `此字段必填`;
- visible field with `requiredWhen` false preserves P3a optional behavior;
- nested child can use flat stableId references;
- hidden nested parent skips parent and child validation;
- empty driver makes `neq` and `notIn` false;
- numeric boundaries:
  - normal integer comparison;
  - `1` equals `1.0`;
  - safe-integer boundary within JSON-safe values;
  - one high-precision decimal probe, initially `expectSymmetry: true`.

## Publish Cases

Publish cases execute schema-definition validation:

```text
schema -> expected publish-time DSL errors
```

Expected errors use:

```json
{ "fieldPath": "...", "stableId": "...", "reason": "..." }
```

Required publish coverage:

- missing field reference;
- self reference;
- cycle;
- empty group;
- group with both `allOf` and `anyOf`;
- atomic condition missing `field` or `op`;
- `empty` / `notEmpty` with value;
- scalar operator with array value;
- membership operator without array value;
- numeric comparison against non-number field.

These lock the C1 publish-time DSL validation messages across Java and
TypeScript.

## Allowed Files And Budget

| File | Purpose | Estimate |
|---|---|---:|
| `packages/contracts/fixtures/linkage-corpus.json` | Shared runtime + publish linkage cases with `kind` discriminator | 260 |
| `services/api/src/test/java/com/labelhub/api/module/submission/validation/LinkageCorpusTest.java` | Backend runner for runtime and publish corpus cases, using Path C repo-root discovery | 260 |
| `apps/web/src/entities/labeling/linkage.corpus.test.ts` | Frontend runner for the same runtime and publish corpus cases | 170 |
| `services/api/src/test/java/com/labelhub/api/module/submission/SubmitValidationIntegrationTest.java` or existing submit validation test | Submit-path integration coverage for linkage 422 behavior | 150 |
| **Hand-authored total** | | **~840** |

Recommended C4 caps:

- hand-authored soft cap: `700`;
- hand-authored hard cap: `900`.

This estimate is near the hard cap because the shared fixture is intentionally
explicit. If implementation can factor case builders or reuse an existing
submit integration test, it should land closer to the soft cap.

## Backend Runner Design

Create a backend corpus test that reuses the P3a Path C root discovery
pattern:

- start from `Path.of("").toAbsolutePath()`;
- walk parents until `pnpm-workspace.yaml` is found;
- fail loudly with the searched path list if not found;
- read `packages/contracts/fixtures/linkage-corpus.json`.

Runtime case runner:

- convert `schema` to generated `SchemaDocument`;
- convert `payload` to `Map<String, Object>`;
- run `AnswerPayloadValidator.validate(schema, payload)`;
- compare `{ stableId, reason }` errors exactly.

Publish case runner:

- convert `schema` to `SchemaDocument`;
- run `SchemaValidator.validate(schema)`;
- convert thrown `InvalidSchemaDocumentException` to
  `{ fieldPath, stableId, reason }`;
- compare expected errors exactly.

Asymmetry guard:

- assert the corpus contains zero `expectSymmetry: false` cases;
- if that fails, C4 implementation should already have stopped for user
  adjudication.

## Frontend Runner Design

Create:

```text
apps/web/src/entities/labeling/linkage.corpus.test.ts
```

Runtime case runner:

- import the same JSON fixture;
- run `validatePayload(testCase.schema.fields, testCase.payload)`;
- compare expected `{ stableId, reason }` exactly.

Publish case runner:

- run `validateSchemaForUI(testCase.schema)`;
- compare expected `{ fieldPath, stableId, reason }` exactly.

Asymmetry guard:

- assert `corpus.filter(c => !c.expectSymmetry)` is `[]`.

## Submit 422 Integration Coverage

Add one backend integration scenario for linkage-specific submit behavior:

- create/publish schema with driver + dependent `requiredWhen`;
- claim a session;
- submit payload where dependent is visible and empty;
- assert HTTP 422 with `ApiError.fieldErrors` containing the dependent
  stableId and `此字段必填`;
- submit or validate a hidden dependent scenario where the hidden field does
  not appear in fieldErrors.

Use the existing integration-test style from P3a C4. If Docker/Testcontainers
are unavailable in sandbox, preserve the established D-record pattern and
report the actual condition.

## Forbidden Surfaces

- backend `LinkageEvaluator.java`
- frontend `linkageEvaluator.ts`
- `AnswerPayloadValidator.java`
- frontend `payloadValidation.ts`
- backend `SchemaValidator.java`
- frontend `schemaValidation.ts`
- `SchemaFormilyRenderer.tsx`
- `SchemaRenderer.tsx`
- OpenAPI YAML and generated API files
- `packages/contracts/fixtures/validation-corpus.json`
- `pom.xml` / `services/api/pom.xml`
- migrations
- humanpending

C4 verifies C1/C2/C3. It does not tune them. If the corpus exposes a real
dual-side discrepancy, STOP and report instead of changing logic or weakening
expected results.

## Risk Register

| Risk | Resolution |
|---|---|
| Runtime and publish cases make one JSON schema awkward | Use a `kind` discriminator and case-specific optional fields; document runner behavior clearly. |
| Numeric boundary case exposes Java/JS precision mismatch | Default true; STOP if mismatch appears before adding any false case. |
| Publish expected stableId differs by side | Normalize frontend/backend errors into `{ fieldPath, stableId, reason }`; choose expected values from existing C1 test behavior. |
| Submit integration is blocked by local Docker/Testcontainers | Follow existing D-record; corpus tests still provide the core dual-side proof. |
| Corpus becomes too broad by also testing rendered visibility | Keep runtime cases to final validation errors only; C3 unit tests own visible field tree behavior. |

## Verification Plan For Implementation

- backend corpus test only;
- frontend linkage corpus test only;
- P3a backend `AnswerPayloadValidatorCorpusTest`;
- P3a frontend `payloadValidation.corpus.test.ts`;
- full backend tests if sandbox permits, or established escalated/D-record path;
- full frontend Vitest;
- protected endpoints check;
- OpenAPI MD5 remains `890e595c6351ee53788d35354b2412a3`;
- migrations remain `11`;
- humanpending remains `135`;
- forbidden implementation files have empty diff;
- git status clean.

## Stop Conditions

- Any C1/C2/C3 runtime or publish logic needs changes.
- Any `expectSymmetry: false` case appears without explicit user adjudication.
- Numeric boundary divergence appears.
- Linkage corpus design needs a second fixture file.
- P3a validation corpus behavior changes.
- C4 exceeds `900` hand-authored lines.

## User Adjudication Checklist

1. Approve one `linkage-corpus.json` file with `kind: "runtime" | "publish"`.
2. Approve zero default asymmetric cases; any discovered asymmetry triggers
   STOP before changing the corpus.
3. Approve runtime cases asserting only final validation errors, not
   rendered `visibleFields`.
4. Approve runtime + publish case coverage list.
5. Approve C4 caps: hand-authored soft `700`, hard `900`.
