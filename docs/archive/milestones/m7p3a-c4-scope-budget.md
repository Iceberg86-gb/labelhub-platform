# M7-P3a C4 Scope-Budget: Symmetry Corpus And Validation Tests

## Status

C4 pre-estimate gate. No corpus, backend test, frontend test, integration
test, or implementation code lands until user adjudication.

Baseline anchor: `08c7c4f`. Backend tests: `408/78` at C3. OpenAPI MD5:
`304b6d00e35a3649fd10ae9f01392288`. Migrations: `11`. humanpending:
`133`. Frontend Vitest: `27`.

## Phase Character

M7-P3a C4 is the executable proof layer for the backend answer-validation work
landed in C2 and wired in C3.

C2 manually byte-matched backend `AnswerPayloadValidator` messages against
frontend `payloadValidation.ts`. C3 placed the validator on the submit path
using the session-bound schema version. C4 converts that manual symmetry claim
into a shared, corpus-driven regression suite that both sides run.

This cluster is test-heavy by design. Backend test count is expected to
increase from 408; frontend Vitest count is expected to increase from 27.
That is not a frozen-baseline violation. The frozen anchors for C4 are OpenAPI
MD5, migrations, humanpending, `pom.xml`, and the three locked logic files.

## Locked Goal

1. **Shared corpus truth source**: `packages/contracts/fixtures/validation-corpus.json`
   is the single fixture consumed by backend JUnit and frontend Vitest.

2. **Dual-side symmetry**: every `expectSymmetry: true` case must produce
   exactly the same `{ stableId, reason }` errors on frontend and backend.

3. **Known asymmetry surfaced, not hidden**: the scientific-notation
   number-message case is recorded as `expectSymmetry: false`, with backend
   expected errors preserving the current backend wording and frontend tests
   asserting only the rule family.

4. **Submit-path proof**: a real submit request with invalid payload returns
   HTTP 422 and `ApiError.fieldErrors` with `field = stableId`.

5. **Session-bound schema proof**: a session claimed under schema v2 validates
   against v2 even after the task publishes v3.

## Adjudicated Constraints

- Corpus location: `packages/contracts/fixtures/validation-corpus.json`.
- Backend corpus read strategy: Path C. Tests locate the repository root by
  walking upward until `pnpm-workspace.yaml` is found, then read the corpus by
  relative path.
- `pom.xml`: zero changes. No Maven resource-copy setup.
- Scientific-notation message drift: expose with `expectSymmetry: false`; do
  not fix in C4.
- HTTP contract: keep the C1 `422` + reused `ApiError.fieldErrors` contract.

## Frozen Surfaces

- `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java`
- `apps/web/src/entities/labeling/payloadValidation.ts`
- `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java`
- `services/api/pom.xml` and root `pom.xml`
- `packages/contracts/openapi/labelhub.yaml`
- `services/api/src/main/resources/db/migration/`
- `humanpending.md`

C4 verifies the locked logic; it does not revise it.

## Allowed Files

| File | Purpose | Estimate |
|---|---|---:|
| `packages/contracts/fixtures/validation-corpus.json` | Shared validation corpus with 18-20 cases, including one `expectSymmetry: false` scientific-notation case | 260 |
| `services/api/src/test/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidatorCorpusTest.java` | Backend parameterized corpus test, including `findRepoRoot()` Path C helper | 170 |
| `apps/web/src/entities/labeling/payloadValidation.corpus.test.ts` | Frontend Vitest corpus test against the same JSON fixture | 100 |
| `services/api/src/test/java/com/labelhub/api/integration/SubmitValidationIntegrationTest.java` | Submit 422 integration test and session-bound schema-version test | 220 |
| **Total** | | **750** |

Soft cap: 1000 changed lines. Hard cap: 1300 changed lines.

## Corpus Coverage Budget

The corpus must include all backend message families and the C2 symmetry traps:

| Case family | Expected symmetry |
|---|---|
| `required` missing / empty | true |
| text non-string | true |
| number non-number | true |
| single_select invalid option | true |
| multi_select invalid option | true |
| nested_object non-object | true |
| minLength failure | true |
| maxLength failure | true |
| number min ordinary value | true |
| number max ordinary value | true |
| nested child failure using flat child `stableId` | true |
| legal payload | true |
| regex partial match passes (`\d+` with `abc123def`) | true |
| regex no match fails (`abcdef`) | true |
| whitespace-only required string passes | true |
| date non-ISO string passes | true |
| file_upload string passes | true |
| scientific notation min at `0.0000001` | false |
| optional contrast case at `0.001` or `0.000001` | true |

The `expectSymmetry: false` case records backend output
`不能小于 0.0000001`, while frontend JavaScript formats the same number as
`1e-7`. This gap is recorded for later adjudication and is not fixed in C4.

## Risk Register

| Risk | Resolution |
|---|---|
| Corpus is copied or duplicated per side | Both tests must read `packages/contracts/fixtures/validation-corpus.json`. |
| Backend test depends on CWD | Path C `findRepoRoot()` walks upward to `pnpm-workspace.yaml` and fails loudly with searched paths. |
| C4 accidentally changes validator or frontend reference logic | Frozen-surface diff checks reject changes to `AnswerPayloadValidator.java`, `payloadValidation.ts`, and `SessionService.java`. |
| Scientific-notation case causes false C4 failure | Mark exactly that case `expectSymmetry: false`; frontend asserts rule family instead of backend wording. |
| Integration tests require excessive fixture plumbing | Prefer reusing existing integration helpers/patterns from `SessionApiIntegrationTest` and `SubmissionLifecycleIntegrationTest`. |
| Schema-bound test accidentally validates against latest schema | Use a v2/v3 constraint inversion where the submitted payload distinguishes the two versions. |
| ApiError field semantics confuse future readers | The integration test name and assertion state that `fieldErrors[].field` is dynamic `SchemaField.stableId`. |
| Test count change is misread as regression | C4 report records new backend and frontend counts and explains the intended increase. |

## Stop Conditions

- Any implementation estimate exceeds the 1000-line soft cap by 50% or hits
  the 1300-line hard cap.
- Corpus reveals a new `expectSymmetry: true` frontend/backend mismatch that
  cannot be fixed without changing locked logic.
- Integration fixtures require OpenAPI, migration, auth, or audit changes.
- `pom.xml` needs changes to make backend corpus loading work.
- Session-bound schema version cannot be proven without risky schema lifecycle
  edits.

## Verification Plan For C4 Implementation

- `mvn -pl services/api compile`
- `mvn -pl services/api test`
- `pnpm --filter @labelhub/web test`
- `bash scripts/check-protected-endpoints.sh`
- OpenAPI MD5 remains `304b6d00e35a3649fd10ae9f01392288`
- Migration count remains `11`
- humanpending count remains `133`
- `pom.xml` unchanged
- Frozen logic files unchanged:
  - `AnswerPayloadValidator.java`
  - `payloadValidation.ts`
  - `SessionService.java`

## User Adjudication Checklist Before Implementation

1. Approve the C4 file set and 750-line estimate.
2. Approve corpus case count target: 18-20 cases.
3. Approve `expectSymmetry: false` only for the scientific-notation min case.
4. Approve C4 backend/frontend test count increases as expected.
5. Confirm no C4 fix for numeric message formatting; correction, if desired,
   becomes a later adjudicated cluster.
