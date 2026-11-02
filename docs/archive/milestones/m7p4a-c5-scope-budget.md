# M7-P4a C5 Scope Budget: Legacy AI Call Integration Coverage

## Status

Pre-estimate gate. No implementation until adjudication.

Anchor after C4:

- HEAD: `24e7ae2`
- OpenAPI MD5: `23a67e2cad632b3e9cfaff03c5d05dd7`
- Backend tests: `515 / 83`
- Frontend Vitest: `131`
- Migrations: `14`
- humanpending: `141`

## Goal

C5 is an integration-test cluster. It verifies the historical data behavior defined in M7-P4a research §13.4:

- Existing legacy `ai_calls` rows with old idempotency keys and `prompt_version_id = NULL` must not be reused by the new promptVersionId idempotency key.
- Provenance reads must still return legacy rows with their legacy `prompt_version` label.
- New AI review calls must persist `prompt_version_id`, `provider_adapter_version`, and the new idempotency key format through the real DB/service/controller path.

C5 is not a business-logic cluster. If these tests expose a product bug, implementation should stop and report rather than patching service logic inside C5.

## Existing Coverage

`AiReviewIntegrationTest` already covers:

- Owner trigger creates AI call + field rows using `promptVersionId: 1`.
- Same `promptVersionId` second trigger returns idempotency hit.
- Different `promptVersionId` creates a second AI call.
- Provenance reads AI calls for owner and labeler.
- Permission boundaries.

C5 should not duplicate those tests. It should add the missing historical-row behavior.

## Planned Test Additions

Primary target:

- `services/api/src/test/java/com/labelhub/api/integration/AiReviewIntegrationTest.java`

Add one core integration test:

1. Create a normal submitted submission fixture.
2. Seed a legacy `ai_calls` row by direct SQL:
   - `idempotency_key = submission:{id}:provider:mock:model:mock-v1:prompt:prompt-v1`
   - `prompt_version = 'm3-owner-review-v1'`
   - `prompt_version_id = NULL`
   - `provider_adapter_version = 'agent-default-v1'` or let DB default apply.
3. Trigger the AI review through the real HTTP endpoint with `promptVersionId: 1`.
4. Assert the response is a miss, not an idempotency hit.
5. Assert the database now has two AI calls for the submission.
6. Assert the legacy row is unchanged.
7. Assert provenance returns both rows:
   - legacy row has `promptVersionId: null` and readable legacy `promptVersion`.
   - new row has non-null `promptVersionId` and `providerAdapterVersion`.

Add one deeper E2E assertion test only if it is not already covered cleanly by the legacy test:

- Trigger review with `promptVersionId`.
- Assert the DB row has:
  - `prompt_version_id = 1`
  - `provider_adapter_version = 'agent-default-v1'`
  - idempotency key contains `promptVersionId:1:adapter:agent-default-v1`
- Assert provenance returns the same fields.

Optional only if absent:

- Invalid `promptVersionId` returns 404. If C3 already has service/controller coverage, do not duplicate unless an integration gap remains.
- `GET /prompt-versions/default` returns seed row. If C2 already has controller integration coverage, do not duplicate.

## Forbidden Surfaces

C5 must not modify:

- `AiReviewService`
- `AiReviewController`
- `AiCallMapper`
- `FailedAiCallRecorder`
- `PromptVersionService`
- Any frontend file
- OpenAPI
- Migrations
- P3a/P3b code or corpus files
- `humanpending.md`

## Risks

| Risk | Resolution |
|---|---|
| Test accidentally uses the new idempotency key and does not simulate legacy data | Hard-code the exact C3-predecessor key shape with `:prompt:prompt-v1` and no `promptVersionId` segment |
| Test passes by hand-constructed objects, not the production path | Use real MockMvc trigger and real `selectByIdempotencyKey` behavior through `AiReviewService` |
| Docker unavailable | Keep `@Testcontainers(disabledWithoutDocker = true)` and report D-口径 consistently |
| C5 exposes real business bug | STOP and report; do not fix business code in C5 |

## Estimate

Hand-authored estimate:

| Area | Estimate |
|---|---:|
| Legacy seed helper | 45 |
| Legacy integration test | 120 |
| E2E deepening assertions | 70 |
| Minor fixture/readability adjustments | 45 |
| Total | 280 |

Recommended cap:

- Soft cap: `400`
- Hard cap: `600`

Generated churn: none.

## Verification Plan

- Targeted integration test command if Docker is available.
- Full backend test command if feasible.
- OpenAPI MD5 remains `23a67e2cad632b3e9cfaff03c5d05dd7`.
- Migrations remain `14`.
- humanpending remains `141`.
- Business-code git diff remains empty.
