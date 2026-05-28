# M7-P3a Scope-Budget: Dual-Side Validation Symmetry

## Status

Pre-estimate gate for M7-P3a. No code has landed from this phase.

Baseline anchor: `fe8659e`. Backend tests: `408/78`. OpenAPI MD5:
`b6a8344f2c7cc38db958eb333334ebd1`. Migrations: `11`. humanpending:
`133`. Frontend Vitest: `27`. M7-P2 closed.

## Phase Character

M7-P3a targets rubric 1.4, specifically the runtime validation half of
"字段联动与运行时校验". It adds server-side answer-payload validation that is
symmetrical with the existing frontend `payloadValidation.ts`.

This is a security-hardening phase. Today, `SessionService.submit` checks
only that `answerPayload != null`; a client bypassing the frontend can submit
constraint-violating answers and have them persisted. M7-P3a closes that gap
before M7-P3b introduces linkage DSL / cross-field constraints.

The phase introduces a backend `AnswerPayloadValidator` mirroring the current
frontend validation behavior:

- required / empty handling
- text `minLength`, `maxLength`, `pattern`
- number `min`, `max`
- select option membership
- date and file-upload string-shape checks
- nested-object recursive validation

The submit endpoint gains a structured 422 response for answer validation
failures. This is an intentional OpenAPI contract change; the new MD5 is
recorded at phase close.

## Locked Goal

1. **Symmetry**: every rule currently enforced by frontend
   `apps/web/src/entities/labeling/payloadValidation.ts` is enforced by the
   backend `AnswerPayloadValidator`. A shared JSON corpus validates identical
   accept/reject decisions on both sides.

2. **Submit-path enforcement**: `SessionService.submit` rejects
   constraint-violating payloads with structured HTTP 422 before persistence.
   No invalid answer reaches the `submissions` table.

3. **No false positives**: every answer accepted by the frontend is also
   accepted by the backend. The backend must not become stricter than the
   frontend reference unless the user explicitly adjudicates a frontend spec
   change.

## Forbidden Surfaces

- Any change to frontend `payloadValidation.ts`. It is the reference spec for
  P3a. If a discrepancy is found, document it and stop for adjudication.
- Any change to `SchemaValidator.java` document-structure validation. Schema
  document validation and answer-payload validation are separate concerns.
- Any change to the Formily renderer, x-components, virtualization, or
  Formily x-validator projection from M7-P2.
- Any change to submission immutability. Validation happens before persistence
  and must not mutate already-persisted submissions.
- Any migration unless a new persisted column is explicitly adjudicated. None
  is expected.
- Any authorization, routing, session ownership, or audit-governance change.
- Any validation-failure audit event unless explicitly adjudicated. Default is
  defer, because failed submits are not state changes and may be high-volume.

## Dependencies

No new runtime dependency is expected. The six static rule families are simple
enough for plain Java, Spring, Jackson, and `java.util.regex.Pattern`.

If implementation appears to require a JSONPath, expression, or rules-engine
library, the cluster must stop and report. That would indicate scope creep
into M7-P3b linkage DSL.

## OpenAPI Budget

M7-P3a is expected to change `packages/contracts/openapi/labelhub.yaml`.

Recommended contract:

- Add a `422` response to `POST /sessions/{sessionId}/submit`.
- Reuse the existing `ApiError` envelope and its `fieldErrors` array.
- Map backend validation errors as `field = stableId`, `message = reason`.
- Use an error code such as `ANSWER_VALIDATION_FAILED`.

This avoids inventing a second error envelope while still providing stable,
field-addressable validation feedback to the frontend.

Alternative: introduce dedicated `AnswerValidationError` /
`AnswerValidationProblem` schemas if the user wants names that explicitly use
`stableId` and `reason`. This is more explicit but adds contract surface.

## Per-Cluster Plan

| Cluster | Scope | Estimate |
|---|---|---:|
| C1 | OpenAPI 422 contract for submit validation failures + regenerate backend/frontend generated types | ~80 |
| C2 | `AnswerPayloadValidator.java`, `AnswerValidationError`, `AnswerValidationException`, and `GlobalExceptionHandler` 422 handling | ~250 |
| C3 | Wire validator into `SessionService.submit` using the session-bound schema version | ~40 |
| C4 | Backend + frontend shared symmetry corpus tests; submit-path 422 integration test; schema-version-bound validation test | ~280 |
| C5 | Frontend submit flow handles backend 422 `fieldErrors` and passes them into `SchemaFormilyRenderer` errors prop | ~120 |
| C6 | Verification doc, screenshots if seedable, and humanpending phase entry | N/A |

Total estimate: ~770 changed lines. Soft cap: 1000. Hard cap: 1300.

## Shared Symmetry Corpus

The load-bearing artifact is a shared JSON fixture read by both frontend
Vitest and backend JUnit tests.

Recommended path:

`packages/contracts/fixtures/validation-corpus.json`

Each case should include:

- `name`
- `schema.fields`
- `answerPayload`
- `expectedErrors`: array of `{ stableId, reason }`

Initial corpus should cover 12-15 cases:

- required pass and fail
- frontend empty semantics: `null`, `""`, `[]`, `{}`
- text `minLength`, `maxLength`, and `pattern`
- invalid regex pattern behavior
- number type, `min`, and `max`
- single-select and multi-select option membership
- date and file-upload string-shape checks
- nested-object recursion and optional empty object behavior
- unknown payload keys ignored by validation

The corpus is the executable proof of dual-side validation symmetry.

## Risk Register

| Risk | Resolution |
|---|---|
| Frontend/backend empty semantics differ subtly | Pre-estimate records exact semantics; C4 corpus includes each empty shape. |
| Java uses `String.isBlank()` and becomes stricter than frontend | C2 must use `String.isEmpty()` only. Frontend treats `"   "` as non-empty. |
| JS regex `.test()` differs from Java regex matching | C2 should use `Pattern.matcher(value).find()`, not `matches()`, and corpus covers pattern cases. |
| Invalid regex pattern behavior diverges | Frontend returns `正则表达式无效`; backend must mirror this reason. |
| Schema version resolution loads latest published schema | C4 includes a claim-under-v2 / publish-v3 / submit-under-v2 test. |
| Number coercion differs | Backend accepts JSON numeric types (`Number`) and rejects strings; corpus includes numeric edge cases. |
| 422 vs 400 status code disagreement | Recommend 422 Unprocessable Entity; user adjudication required before C1. |
| Validation failure auditing creates high-volume audit noise | Recommend defer. Failed submits are not persisted state changes. |
| Existing submit tests relied on invalid payloads slipping through | C4 updates fixtures to valid payloads and documents any discovered test debt. |
| OpenAPI change ripples beyond submit endpoint | C1 stop condition if contract changes more than submit 422 + generated type updates. |

## Stop Conditions

- Any cluster exceeds estimate by 50%.
- Cumulative implementation diff exceeds the 1300-line hard cap.
- Shared corpus reveals a frontend/backend discrepancy that cannot be resolved
  without changing `payloadValidation.ts`.
- Backend validator would need to become stricter than frontend to pass a
  proposed rule.
- Schema-version resolution requires risky edits to M6 schema lifecycle code.
- OpenAPI changes ripple beyond submit validation failure handling.
- Backend test count regresses or existing tests fail without a clear, scoped
  fixture update.

## Verification Plan

Per implementation cluster:

- Backend tests: `./mvnw -pl services/api test` (or the existing full backend
  test command if a cluster touches generated sources across modules).
- Frontend typecheck/build when generated frontend types or submit flow changes:
  `pnpm --filter @labelhub/web typecheck` and
  `pnpm --filter @labelhub/web build`.
- Frontend Vitest when the shared corpus or C5 submit handling lands:
  `pnpm --filter @labelhub/web test`.
- OpenAPI MD5 tracked from C1 onward; the phase-close verification doc records
  the new baseline.
- Migrations remain `11`.

## User Adjudication Checklist Before C1

1. Split confirmed: P3a validation before P3b linkage.
2. Validation failure status: recommend HTTP 422.
3. Error contract: recommend reusing `ApiError.fieldErrors`; alternative is a
   dedicated answer-validation problem schema.
4. Validation-failure auditing: recommend defer.
5. Shared corpus location: recommend
   `packages/contracts/fixtures/validation-corpus.json`.
6. Budget: recommend soft cap 1000 / hard cap 1300.
