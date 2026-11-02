# M7-P4b1 C4 Pre-Estimate: Rule Runtime Integration And Export Canonical Audit

## Status

Gate only. No production code, tests, OpenAPI, or migrations are changed until this gate is approved.

Current anchor:

- HEAD: `6a280b3` (`M7-P4b1 C3`)
- OpenAPI MD5: `b7df19fdb69f8d22b2f0dbdbc845d95d`
- Backend tests: `534 / 84`
- Frontend tests: `131`
- Migrations: `17`
- humanpending: `146`

## Scope Summary

C4 should prove C3's active AI review rule runtime binding through real integration paths and should characterize the export canonicalization impact of adding `aiReviewRuleId` to AI call evidence.

C4 remains a test cluster. If the export audit or integration tests expose a production bug, C4 should stop for adjudication instead of changing production code.

## Verified Current Seams

- `AiReviewIntegrationTest` already uses `@Testcontainers(disabledWithoutDocker = true)` and has helpers for:
  - `submissionFixture(...)`;
  - `triggerReview(submissionId, promptVersionId)`;
  - `insertPromptVersion(...)`;
  - `insertLegacyAiCall(...)`;
  - legacy P4a idempotency-key behavior.
- C2 exposes real HTTP endpoints for `POST /ai-review/rules` and `POST /ai-review/rules/{id}/publish`.
- C3 runtime resolves `tasks.current_ai_review_rule_id`, writes `ai_calls.ai_review_rule_id`, and uses the rule-bound idempotency key segment.
- `ExportArtifactBuilder.aiCallToCanonical(...)` now includes `aiReviewRuleId`.
- `Canonicalizer` sorts properties and map entries but does not configure null exclusion.
- `ExportArtifactBuilder.CANONICALIZATION_VERSION` remains `labelhub-canonical-v1`.

## Decision 1: Extend `AiReviewIntegrationTest`

Recommendation: extend the existing integration test rather than create a new runtime integration class.

Rationale:

- The existing file already owns the owner/labeler HTTP paths, JWT helpers, fixture setup, prompt version helper, and legacy AI call helper.
- Adding active rule helpers there keeps the comparison between P4a no-rule and P4b1 rule-bound behavior in one place.
- A separate file would duplicate setup and increase the chance that one path uses a subtly different fixture.

Rejected alternative:

- New `AiReviewRuleRuntimeIntegrationTest`: cleaner file name, but more duplicated setup and weaker proximity to the existing P4a idempotency tests.

## Decision 2: Use Real Save And Publish For Normal Active Rule Cases

Recommendation: use `POST /ai-review/rules` and `POST /ai-review/rules/{id}/publish` for normal active rule setup.

Rationale:

- C2 service/publish behavior is part of the runtime chain: saving converts prompt text to a prompt version, and publishing sets `tasks.current_ai_review_rule_id`.
- Using the real HTTP path avoids the C3.5 failure mode where tests hand-constructed Java objects and missed production deserialization behavior.
- It verifies auth, service, prompt version pointer, and task pointer setup in the same flow that runtime later consumes.

Exception:

- The dangling-pointer case should use direct SQL to set `tasks.current_ai_review_rule_id` to a non-existent id. That scenario is intentionally corrupt data and cannot be created through valid endpoints.

## Decision 3: Runtime Cases To Add

### Active Rule Wins

Flow:

1. Create normal submission fixture.
2. Create a distinct prompt version id `X` for the request fallback.
3. Save and publish an AI review rule with prompt content that resolves to prompt version id `Y`.
4. Trigger AI review with request `promptVersionId = X`.
5. Assert the AI call uses `Y`, writes `aiReviewRuleId = rule.id`, and has a rule-bound key.

Expected assertions:

- response `aiCall.promptVersionId == Y`;
- response `aiCall.aiReviewRuleId == rule.id`;
- DB `ai_calls.ai_review_rule_id == rule.id`;
- idempotency key contains `:ruleVersionId:{rule.id}`;
- request fallback prompt id `X` does not win.

### No-Rule Fallback

Flow:

1. Create normal submission fixture with no active rule.
2. Trigger AI review with prompt version id `1`.
3. Assert exact P4a behavior.

Expected assertions:

- `aiReviewRuleId` is absent/null;
- `promptVersionId == 1`;
- idempotency key equals the P4a format and does not contain `ruleVersionId`.

### Key Isolation

Flow:

1. Trigger no-rule review first.
2. Save and publish an active rule.
3. Trigger again for the same submission.

Expected assertions:

- second trigger is not an idempotency hit;
- DB has two AI call rows;
- first row key has no `ruleVersionId`;
- second row key has `ruleVersionId:{rule.id}`.

### Dangling Pointer

Flow:

1. Create normal submission fixture.
2. Directly set `tasks.current_ai_review_rule_id` to a missing id.
3. Trigger AI review.

Expected assertions:

- HTTP 404;
- this runs through real `AiReviewService` + mapper lookup + `GlobalExceptionHandler`.

### Mixed Provenance

Flow:

1. Insert one legacy AI call using the true old key shape and `promptVersionId = null`.
2. Publish active rule.
3. Trigger rule-bound AI review.
4. Read provenance.

Expected assertions:

- provenance returns two AI calls;
- legacy row keeps null/absent `aiReviewRuleId`;
- new row has `aiReviewRuleId`;
- both rows keep readable prompt/provenance fields.

## Decision 4: Export Canonical Audit Comes First In Implementation

Recommendation: start C4 implementation with the export canonical characterization before expanding Testcontainers coverage.

Rationale:

- Export canonical behavior is the only C4 item that may reveal a required production design decision.
- The audit can run without Docker in `ExportServiceTest`.
- If it exposes a canonicalization version problem, stopping early avoids spending time on integration tests that may later be rebased around a version-bump decision.

Characterization target:

- Build or inspect an export with an AI call whose `aiReviewRuleId` is null.
- Inspect `ai-calls.jsonl`.
- Determine whether it includes `"aiReviewRuleId":null`.
- Compare this with `CANONICALIZATION_VERSION = "labelhub-canonical-v1"`.

Preliminary read:

- `Canonicalizer` does not disable null serialization.
- `ExportArtifactBuilder.aiCallToCanonical(...)` explicitly adds `aiReviewRuleId`.
- Therefore `aiReviewRuleId:null` likely appears in canonical output for no-rule calls. C4 must prove this and stop if the versioning implication is unsafe.

STOP condition:

- If a no-rule export includes a new null field and that changes the canonical shape under `labelhub-canonical-v1`, C4 should report:
  - affected file (`ai-calls.jsonl`);
  - whether `fileHash` / `manifestHash` would change;
  - current canonicalization version;
  - possible decisions: bump version, omit null evidence fields, or accept documented hash change.

## Decision 5: Test Files And Estimated Changes

| Area | Files | Estimate |
|---|---:|---:|
| Runtime integration cases | `AiReviewIntegrationTest` | 230 |
| Rule save/publish helpers and pointer assertions | `AiReviewIntegrationTest` | 70 |
| Export canonical characterization | `ExportServiceTest` or a focused export builder/service test | 90 |
| Assertion/helper cleanup | test files only | 35 |

Estimated hand-authored total: about 425 lines.

Recommended cap: soft 450 / hard 650.

## Docker D-口径

`AiReviewIntegrationTest` remains a Testcontainers test and may skip in this local sandbox if Docker is unavailable or incompatible. That is acceptable if:

- the committed tests are permanent;
- the implementation report distinguishes skipped Testcontainers from runnable unit tests;
- the export canonical characterization test runs without Docker.

## Frozen Boundaries

C4 should not change:

- `AiReviewService`;
- `AiReviewRuleService`;
- `AiCallMapper`;
- `AiReviewDtoMapper`;
- `ExportArtifactBuilder`;
- `Canonicalizer`;
- OpenAPI;
- migrations;
- frontend;
- humanpending;
- P3a/P3b artifacts.

If any of those need changes, C4 should stop for adjudication.

## Verification Plan

Implementation should eventually run or report:

```bash
mvn -pl services/api -Dtest=ExportServiceTest test
mvn -pl services/api -Dtest=AiReviewIntegrationTest test
mvn -pl services/api test
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \\[" humanpending.md
git status --short
```

Expected if the export audit does not trigger a STOP:

- OpenAPI MD5 remains `b7df19fdb69f8d22b2f0dbdbc845d95d`.
- migrations remain `17`.
- humanpending remains `146`.
- only test files change.
- backend tests rise modestly from `534 / 84`.

## Audit Notes For Implementation Review

Implementation audit should verify:

- active rule integration uses the rule's prompt version even when request prompt id differs;
- no-rule fallback key is byte-for-byte the P4a key shape;
- key isolation creates a second row after publishing a rule;
- dangling pointer returns 404 instead of falling back;
- mixed provenance includes both null and non-null `aiReviewRuleId`;
- export audit explicitly states whether `aiReviewRuleId:null` is serialized;
- if canonicalization version is implicated, the implementation stops instead of changing production code.
