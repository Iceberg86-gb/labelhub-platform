# M7-P4b1 C2 Pre-Estimate: AiReviewRule Service

## Status

Gate only. No production code is changed in this cluster until this gate is approved.

Current anchor:

- HEAD: `57c8efd` (`M7-P4b1 C1`)
- OpenAPI MD5: `b10b8cf2339f4b01c683eb8b7d12bf2f`
- Backend tests: `522 / 84`
- Frontend tests: `131`
- Migrations: `16`
- humanpending: `146`

## Scope Summary

C2 should turn `POST /ai-review/rules` from a `501` stub into a working Owner API and add a publish/activate endpoint for the rule. It should not change AI review runtime behavior, idempotency, `ai_calls`, task publish guards, or Owner UI.

C2 creates the service layer that bridges owner-facing `promptTemplate` input to P4a's immutable `prompt_versions` table:

1. Owner posts `{ taskId, promptTemplate, dimensions, threshold }`.
2. Backend validates the request.
3. Backend converts `promptTemplate` to a `prompt_versions` row through `PromptVersionService.create(promptTemplate, ownerId)`.
4. Backend writes a new task-scoped `ai_review_rules` version with status `draft`.
5. A separate publish endpoint activates a saved rule by setting `ai_review_rules.status = 'published'`, `activated_at`, and `tasks.current_ai_review_rule_id`.

This keeps P4b1's rule version immutable while preserving P4a's prompt text versioning.

## Verified Current Seams

- `PromptVersionService.create(String content, Long ownerId)` already exists and returns an existing prompt version when `content_hash` matches.
- `AiReviewController.saveAiReviewRule(...)` currently throws `501 NOT_IMPLEMENTED`.
- `AiReviewController.currentUserId()` already extracts `JwtPrincipal.userId()`.
- `AiReviewRuleEntity` and `AiReviewRuleMapper` exist from C1 with insert/select-only methods.
- `tasks.current_ai_review_rule_id` exists and is nullable.
- There is no adjudication-rule service implementation to reuse.
- `TaskService.canPublish(...)` has no AI review rule guard and must remain unchanged in C2.

## Decision 1: Save Draft Plus Publish Endpoint

Recommendation: C2 should implement both:

- `POST /ai-review/rules` as save draft.
- `POST /ai-review/rules/{ruleId}/publish` as publish/activate.

Rationale:

- Research split save draft and publish/activate as separate owner actions.
- C3 runtime integration needs a reliable active rule pointer on `tasks.current_ai_review_rule_id`; pushing publish to C3 would mix runtime behavior with rule lifecycle plumbing.
- P4b2 UI can call the same two endpoints without redefining backend semantics.
- `POST /ai-review/rules` should stay safe for iterative editing: every save creates a new draft rule version, but does not affect active runtime behavior until publish.

Rejected alternatives:

- Save directly publishes: too surprising for an editor workflow; every typo would change runtime configuration.
- Save-only C2 with publish deferred: leaves C3 responsible for lifecycle wiring and makes the runtime cluster too broad.
- Embedding status in `AiReviewRuleRequest`: expands owner input before UI semantics are ready.

OpenAPI impact:

- Add `POST /ai-review/rules/{ruleId}/publish`, operation id `publishAiReviewRule`, response `AiReviewRule`.
- Keep `AiReviewRuleRequest` unchanged.
- MD5 will change again from `b10b8cf...`.

## Decision 2: Request Validation

Recommendation: add service-level validation with a small domain exception mapped to HTTP 400.

Validation rules:

- `promptTemplate` must not be blank after trim.
- `dimensions` must contain at least one item.
- Each dimension must be nonblank after trim.
- Exact duplicate dimension names after trim are rejected.
- `threshold` must be between `0` and `1` inclusive.

The service should store normalized dimensions:

- trim each dimension;
- preserve owner-provided order;
- reject duplicates rather than silently de-duplicating.

Error shape:

- New `InvalidAiReviewRuleException(field, reason)` or equivalent domain exception.
- `GlobalExceptionHandler` maps it to `400 INVALID_AI_REVIEW_RULE` with one `ApiFieldError`.

Suggested reasons:

- `promptTemplate must not be blank`
- `dimensions must contain at least one item`
- `dimensions must not contain blank items`
- `dimensions must not contain duplicates`
- `threshold must be between 0 and 1`

Rationale:

- OpenAPI's schema only gives type-level validation; C2 needs semantic validation before writing immutable versions.
- Threshold is treated as a normalized AI confidence/score threshold. This matches existing AI result confidence language (`0..1`).
- Rejecting duplicates prevents ambiguous scoring dimensions without inventing UI behavior.

Rejected alternatives:

- Let database constraints reject data: error quality would be poor and incomplete.
- Add deep conclusion strategy validation: conclusion strategy is deliberately deferred from C1.
- Return `422`: this is not answer payload field validation; existing request validation uses `400`.

## Decision 3: Authorization

Recommendation: only the task owner can save or publish an AI review rule for that task.

Semantics:

- Missing task: `TaskNotFoundException`.
- Task exists but belongs to another owner: also `TaskNotFoundException`.
- Rule id missing or belongs to another owner: `AiReviewRuleNotFoundException` or equivalent 404.

Rationale:

- AI review rule configuration belongs to a task.
- Returning 404 for cross-owner access matches the AI review/provenance style of not exposing cross-owner resources.
- This is stricter than some Owner task APIs that use `403`, but safer for this new AI-review resource.

Implementation note:

- `AiReviewRuleService` should load the task through `TaskMapper.selectById(taskId)` and compare `task.ownerId`.
- Publishing should load the rule and task under the same service method; if the task owner does not match, return not found.

## Decision 4: Repeated Save Creates New Rule Version

Recommendation: every successful save creates a new immutable `ai_review_rules` row.

Semantics:

- `version_no` is task-scoped and increments via `selectMaxVersionByTaskId(taskId) + 1`.
- Same prompt text with changed threshold/dimensions reuses the same prompt version but creates a new rule version.
- Changed prompt text creates or reuses a new prompt version through content hash, then creates a new rule version.
- Draft rows are append-only; C2 should not update old drafts.

Concurrency:

- Use a bounded retry around `uk_ai_review_rules_task_version`.
- On duplicate `(task_id, version_no)`, recompute `selectMaxVersionByTaskId + 1` and retry.
- This mirrors `PromptVersionService`'s bounded retry pattern and is sufficient for low-frequency owner saves.

Rejected alternatives:

- Update latest draft in place: weakens evidence history and makes later activation ambiguous.
- One rule row per task with mutable fields: contradicts the versioning model and P4b research.

## Decision 5: DTO Prompt Template Display Join

Recommendation: introduce a small view model for DTO mapping:

```java
record AiReviewRuleView(AiReviewRuleEntity rule, PromptVersionEntity promptVersion) {}
```

`AiReviewRuleDtoMapper` should map:

- `id` from rule id
- `taskId` from rule task id
- `versionNo` from rule version number
- `promptVersionId` from rule current prompt version id
- `promptTemplate` from `promptVersion.content`
- `dimensions` by parsing `rule.dimensionsJson`
- `threshold` from rule threshold
- `status` from rule status
- `createdAt` / `activatedAt` from rule timestamps

Rationale:

- `ai_review_rules` intentionally stores only `current_prompt_version_id`, not prompt text.
- Response still needs `promptTemplate` for display and for P4b2 editor initialization.
- Two simple queries are acceptable in C2; save/publish operations are low-volume.

Rejected alternative:

- SQL join in `AiReviewRuleMapper`: efficient but couples mapper result shape to DTO needs. Keep C2 clear and explicit.

## Decision 6: Publish Semantics

Recommendation: publish a draft or previously published rule idempotently.

Rules:

- If rule is `draft`, set `status='published'` and `activated_at=NOW(3)`.
- If rule is already `published`, keep `activated_at` unchanged.
- Set `tasks.current_ai_review_rule_id = ruleId` every time publish is called.
- Allow publishing an older published rule to switch the active pointer back to it.
- Multiple rules for the same task may have `published` status; the task pointer defines the active rule.

Rationale:

- C1 only has `draft` and `published`, not `active`/`superseded`.
- Pointer-on-task is the source of truth for current active rule.
- Idempotent publish makes repeated UI clicks safe.

Implementation:

- `@Transactional` service method.
- Add mapper method such as `markPublished(ruleId)` and a task mapper method such as `updateCurrentAiReviewRuleId(taskId, ruleId)`.
- Preserve `TaskService.canPublish`: AI review rule remains optional.

## Files And Estimated Changes

| Area | Files | Estimate |
|---|---|---:|
| OpenAPI publish endpoint + generated types | `packages/contracts/openapi/labelhub.yaml`, generated Java/TS | 45 hand-authored + generated churn |
| Service | `AiReviewRuleService.java`, `AiReviewRuleView.java` | 190 |
| DTO mapper | `AiReviewRuleDtoMapper.java` | 90 |
| Controller wiring | `AiReviewController.java` | 45 |
| Mapper extensions | `AiReviewRuleMapper.java`, `TaskMapper.java` | 80 |
| Exceptions + handler | `InvalidAiReviewRuleException`, `AiReviewRuleNotFoundException`, `GlobalExceptionHandler.java` | 65 |
| Tests | service tests, mapper contract update, controller tests, OpenAPI contract tests | 250 |

Estimated hand-authored total: about 765 lines.

Recommended cap: soft 800 / hard 1000, generated churn separate.

Reasoning:

- This is larger than C1 because it includes lifecycle behavior, DTO joins, validation, authz, version retry, and endpoint tests.
- It is still smaller than runtime C3 because it does not touch `AiReviewService`, `ai_calls`, trigger flow, frontend UI, or task publish guard behavior.

## Test Plan

Service tests:

- save creates a draft rule, creates/reuses prompt version, stores dimensions JSON and threshold.
- same prompt with changed threshold creates a new rule version but reuses prompt version id.
- changed prompt creates a new prompt version and new rule version.
- repeated saves increment task-scoped `version_no`.
- duplicate version retry path uses the next available version.
- invalid prompt/dimensions/threshold produces `400`-mapped exception.
- cross-owner task returns not found.
- publish sets rule status and task pointer transactionally.
- publish already published rule is idempotent.

Controller tests:

- `POST /ai-review/rules` returns `201` and response includes prompt text, promptVersionId, versionNo, status `draft`.
- `POST /ai-review/rules/{ruleId}/publish` returns `200` and status `published`.
- unauthenticated request fails through existing security.
- cross-owner save/publish returns 404.

Contract tests:

- OpenAPI includes `publishAiReviewRule` endpoint.
- Generated DTO shape remains consistent.
- Mapper remains append-only except explicit publish/status update methods. The C1 mapper contract should be updated to allow `markPublished` or similarly named status update, while still rejecting deletes/removes.

Verification commands:

- `mvn -pl services/api -Dtest=AiReviewRuleServiceTest,AiReviewRuleControllerTest,AiReviewRuleMapperContractTest,AiReviewRuleInfrastructureContractTest test`
- `mvn -pl services/api test`
- `pnpm gen:api`
- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web test`
- `bash scripts/check-protected-endpoints.sh`

## Frozen Boundaries

Do not change:

- `AiReviewService`, idempotency, providers, failed-call recorder, `ai_calls`.
- `TaskService.canPublish`.
- P4a `PromptVersionService` semantics.
- P3a/P3b runtime linkage and answer validation.
- Owner frontend UI.
- migrations.
- humanpending.

C2 may change:

- OpenAPI only for the publish endpoint.
- Generated types.
- `AiReviewController.saveAiReviewRule` and publish endpoint wiring.
- AI review rule service/mapper/DTO/exception/test files.
- `TaskMapper` only for `current_ai_review_rule_id` pointer update.

## Expected Anchors After Implementation

- OpenAPI MD5: changes from `b10b8cf2339f4b01c683eb8b7d12bf2f` to a new value.
- Migrations: remain `16`.
- humanpending: remains `146`.
- Backend test count: rises from `522`.
- Frontend test count: likely remains `131`; generated types may change but no frontend business tests should be added.

## Stop Conditions

Stop and report before implementation if:

- Adding publish endpoint causes generated controller changes that require frontend business code.
- The DTO join requires storing prompt text in `ai_review_rules`.
- Runtime `AiReviewService` needs to be touched.
- Task publish guard behavior changes.
