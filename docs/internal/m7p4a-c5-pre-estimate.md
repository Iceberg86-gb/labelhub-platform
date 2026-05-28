# M7-P4a C5 Pre-Estimate: Legacy AI Call Integration Coverage

## Status

Pre-estimate gate. Awaiting adjudication before implementation.

## Scope

C5 should add integration coverage only. It verifies the C3 idempotency-key migration against historical `ai_calls` rows that predate `prompt_version_id`.

The cluster should add or extend tests, most likely in:

- `services/api/src/test/java/com/labelhub/api/integration/AiReviewIntegrationTest.java`

No business code changes are planned or allowed.

## Current Test Baseline

`AiReviewIntegrationTest` already covers:

- AI review trigger creates rows using `promptVersionId`.
- Re-trigger with the same `promptVersionId` hits idempotency.
- Trigger with a different `promptVersionId` creates a second AI call.
- Provenance can be read by owner and labeler.

Those tests validate new data behavior. They do not validate historical legacy rows.

## Core Missing Scenario

Research §13.4 defines the expected behavior:

- A legacy row with old idempotency key shape remains readable.
- A new promptVersionId review does not reuse that legacy row, because the new key format is intentionally different.
- The second call creates a new `ai_calls` row.

Legacy row shape for C5:

```text
idempotency_key = submission:{submissionId}:provider:mock:model:mock-v1:prompt:prompt-v1
prompt_version = m3-owner-review-v1
prompt_version_id = NULL
provider_adapter_version = agent-default-v1
```

New key shape:

```text
submission:{submissionId}:provider:mock:model:mock-v1:promptVersionId:1:adapter:agent-default-v1
```

## Proposed Tests

### Test 1: Legacy Row Is Not Reused By New Key

Add a test in `AiReviewIntegrationTest`:

1. Create a submitted submission through existing `submissionFixture`.
2. Insert a legacy `ai_calls` row directly with the old key shape.
3. Trigger `POST /submissions/{id}/ai-review` with `{ "promptVersionId": 1 }`.
4. Assert:
   - `idempotencyHit = false`
   - provider was invoked once
   - `COUNT(*) FROM ai_calls WHERE submission_id = ?` is `2`
   - the legacy row still has `prompt_version_id IS NULL`
   - the legacy row still has the old idempotency key

This is the load-bearing C5 case.

### Test 2: Provenance Returns Legacy And New Evidence

This can be part of Test 1 or a separate test if readability is better:

1. Read `GET /submissions/{id}/ai-review`.
2. Assert provenance contains both calls.
3. Assert one call has:
   - `promptVersion = 'm3-owner-review-v1'`
   - `promptVersionId = null`
4. Assert the new call has:
   - `promptVersionId = 1`
   - `providerAdapterVersion = 'agent-default-v1'`

This closes the backend half of C4's legacy UI fallback.

### Test 3: Deep New-Path DB Assertions

Only add if not naturally covered by Test 1:

- Assert the new `ai_calls` row has `prompt_version_id = 1`.
- Assert `provider_adapter_version = 'agent-default-v1'`.
- Assert `idempotency_key` includes `promptVersionId:1:adapter:agent-default-v1`.

## What C5 Should Not Repeat

Do not duplicate:

- Existing same-prompt idempotency hit test.
- Existing different-promptVersionId creates second row test.
- Existing permission tests.
- Existing default prompt endpoint test if C2 already covers it.
- Existing not-found promptVersionId service/controller tests if C3 already covers them.

## Guardrails

If the new test fails because production code mishandles legacy rows:

1. Stop.
2. Report the failing case, actual DB rows, and expected behavior.
3. Do not patch `AiReviewService`, mappers, or controllers inside C5.

This is intentionally the same discipline as P3b C3.5: C5 is a probe for production-path blind spots.

## Docker D-口径

`AiReviewIntegrationTest` is Testcontainers-based with `disabledWithoutDocker`.

If Docker is unavailable or the local Docker API version blocks execution:

- The test may be skipped in the sandbox.
- Implementation report must say so explicitly.
- The test logic still must be committed as a permanent regression guard.

## Estimate

Hand-authored estimate:

| Work | Estimate |
|---|---:|
| Legacy SQL seed helper | 45 |
| Legacy no-hit integration test | 120 |
| Provenance assertions | 65 |
| New-row DB assertion refinements | 45 |
| Total | 275 |

Recommended cap:

- Soft cap: `400`
- Hard cap: `600`

## Adjudication Items

1. Approve C5 as tests-only.
2. Approve the legacy seed shape using the exact old key with `:prompt:prompt-v1`.
3. Approve no business-code fixes inside C5; any discovered bug stops for adjudication.
4. Approve cap `400 / 600`.
5. Approve Docker D-口径 if Testcontainers cannot run locally.

## Frozen Checks For Implementation

- OpenAPI MD5 remains `23a67e2cad632b3e9cfaff03c5d05dd7`.
- Migrations remain `14`.
- humanpending remains `141`.
- Backend business-code diff is empty.
- Frontend diff is empty.
- P3a/P3b diff is empty.
