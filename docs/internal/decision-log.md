# Decision Log

## 2026-05-22 M0 Scaffold Decisions

- Created a Java 17 + Spring Boot 3 + MyBatis-Plus modular monolith under `services/api`; `services/agent` is a separate Spring Boot worker process.
- Kept `CODEX.md` and `coderules.md` unchanged as protected root contracts.
- Used MySQL outbox polling for async AI/export work; no BullMQ or Kafka in Stage 1.
- Added a real Flyway initial migration with core tables for tasks, schemas, sessions, submissions, AI calls, quality ledger, adjudication rules, exports, audit logs, and outbox.
- Chose shared environment variable names for local Compose and Spring Boot: `MYSQL_*`, `DATABASE_URL`, `JWT_SECRET`, `LABELHUB_*`, and provider-specific LLM variables.
- Kept `SecurityConfig.anyRequest().permitAll()` only as an M0 placeholder. M1 must switch business endpoints to authenticated access before adding real handlers.
- Kept `/internal/**` open only as an M0 placeholder. M1 must enforce `LABELHUB_INTERNAL_TOKEN` before the agent starts writing AI review callbacks.

## 2026-05-22 Contract Correction

- Restored OpenAPI demo-critical endpoints for the baseline differentiators:
  - `/adjudication-rules/{ruleId}/recompute` for Quality Ledger verdict re-derivation.
  - `/exports/snapshots/{snapshotId}/diff` for trusted export diff attribution.
  - `/ai-review/field-assist` and `/submissions/{submissionId}/ai-trace` for AI provenance history.
- Added ADR-015 to make this correction explicit and prevent future silent contract drift.
- Set OpenAPI to 3.0.3 because OpenAPI Generator 7.6.0 is more stable with Spring Boot generation on the current toolchain.
- Renamed the generated model `Schema` to `LabelSchema` to avoid a Java name collision with `io.swagger.v3.oas.annotations.media.Schema`.
- Renamed the MySQL table `schemas` to `label_schemas` because `SCHEMAS` conflicts with MySQL metadata terminology during Flyway migration.
- The API generator uses `${maven.multiModuleProjectDirectory}/packages/contracts/openapi/labelhub.yaml` so the contract source is anchored at the Maven reactor root.
- Added a root `.mvn/` directory so this Maven setup discovers the reactor root when `generate-sources` is launched from `services/api`; the current `.mvn/maven.config` content (`--no-transfer-progress`) only reduces transfer log noise.
- Bumped OpenAPI `info.version` to `0.1.1` after restoring differentiator endpoints and tightening `ExportDiff.attributableTo[]`.
- Kept task status transition as `PATCH /tasks/{taskId}/transition` because it is a guarded partial state mutation with audit semantics, not a full task replacement.
- Added `scripts/check-protected-endpoints.sh` to make ADR-015 enforceable by checking the five protected baseline demo endpoints.

## 2026-05-23 M1 Phase 2 Decisions

- Bumped OpenAPI from `0.1.1` to `0.2.0` for M1 contract additions: `GET /tasks`, `GET /tasks/{taskId}`, `ApiError`, bearer auth, and paginated task listing.
- Added global `bearerAuth` security with `/auth/login` explicitly public via `security: []`.
- Expanded `LoginResponse` with `tokenType`, `expiresAt`, and a minimal user profile so the web app can display the authenticated user without an immediate `/me` call.
- For Task state transitions, the M1 rule is strong consistency: `tasks` update, `task_transitions` insert, and `audit_logs` insert are one transaction. ADR-016 should later distinguish state-machine audit facts from best-effort observational audit logs.
- M1 publish state legality is handled by `TaskStateTransitions`; the publish business guard is intentionally relaxed to `quota_total > 0` and `deadline_at > now`, with dataset, schema version, and adjudication rule checks left as TODOs for M2/M4.
- `GET /tasks` is scoped to the authenticated Owner only; no admin listing path is introduced in M1.
- Demo users use fixed ids `1001/1002/1003` with `INSERT IGNORE`; role ids are resolved by role code subqueries.
- Root `humanpending.md` remains the authoritative pending-decision file because it is more visible than `docs/internal/`; the `coderules.md` ZPR3 wording is deferred to a separate protected-file revision.
- Testcontainers dependencies are deferred to Phase 4 when controller/security integration tests are introduced.

## 2026-05-23 M1 Phase 3 Decisions

- Implemented Task Service and Mapper layer only; Controller, Security, OpenAPI, and frontend remain untouched for Phase 4/5.
- Kept `TaskMapper` as `BaseMapper<TaskEntity>` because `tasks` is a current-state table and status updates are allowed.
- Kept `TaskTransitionMapper` and `AuditLogMapper` as plain `@Mapper` interfaces with only insert/select methods, preserving append-only enforcement for `task_transitions` and `audit_logs`.
- Stored task status in the entity as the OpenAPI `TaskStatus` surface while mapping the database value through the `status` column code.
- Used `@TableName(autoResultMap = true)` plus `JacksonTypeHandler` for `tasks.tags` and `tasks.reward_rule` JSON fields.
- Ordered Task transition writes as transition evidence, audit fact, then task status update so an audit insert failure prevents the current-state update in Phase 3 unit tests.
- Deferred real database transaction rollback verification to Phase 4 Testcontainers integration tests.
- Configured Mockito tests to use `mock-maker-subclass` because the local Java 26 runtime cannot self-attach the inline Byte Buddy agent.

## 2026-05-23 M1 Phase 4 Decisions

- Added Flyway V3 to append `audit_logs.payload_hash` instead of editing V1, preserving migration history.
- Replaced manual audit payload JSON concatenation with Jackson serialization and a canonical SHA-256 hash computed through `Canonicalizer`.
- Replaced the attempted multi-chain Spring Security setup with one explicit stateless filter chain; `/auth/**`, `/actuator/health`, `/actuator/info`, and `/error` are public, `/internal/**` is allowed through the chain but enforced by `InternalTokenFilter`, and business routes require JWT through `JwtAuthenticationFilter`.
- JWT tokens use jjwt 0.12.x builder/parser APIs with HS256 and a startup-length check requiring `JWT_SECRET` to be at least 32 bytes.
- JWT roles are stored as plain role codes in claims and converted to `ROLE_*` authorities inside the filter for `@PreAuthorize("hasRole('OWNER')")`.
- `TasksController` implements the OpenAPI-generated `TasksApi`; M2 endpoints required by the interface return 501 and do not implement business behavior in Phase 4.
- Testcontainers integration tests are present but use `@Testcontainers(disabledWithoutDocker = true)` because the current local environment has no Docker socket.
- Added Flyway V4 to normalize demo bcrypt hashes from Apache `htpasswd` `$2y$` prefix to Spring-compatible `$2a$` without editing the already-applied V2 seed migration.
- Controller `@PathVariable` bindings use explicit names such as `@PathVariable("taskId")` because the local Java compiler did not expose parameter names at runtime.
- Added `GlobalExceptionHandler` mapping for `NoResourceFoundException` so authenticated requests to unimplemented routes return 404 instead of falling through to the generic 500 handler.
- Added MyBatis-Plus pagination configuration and `mybatis-plus-jsqlparser` so `GET /tasks` returns a correct `total` value instead of only the current page records.
- Pinned Testcontainers dependencies to 1.20.4 in the API module to avoid Spring Boot dependency management resolving an older version than the Phase 4 plan.
- Removed the duplicate `status == draft` check from `canPublish`; `TaskStateTransitions` owns state legality, while `canPublish` owns publish business prerequisites such as quota and deadline. This preserves the allowed `paused -> published` resume path.

## 2026-05-23 M1 Phase 5a-0 Decisions

- Added `GET /tasks/{taskId}/transitions` and bumped OpenAPI from `0.2.0` to `0.2.1` so the Owner task detail UI can show the append-only `task_transitions` evidence trail.
- Kept transition history unpaginated and returned `TaskTransition[]` directly because M1 task state changes are expected to stay below 10 rows per task.
- Scoped transition history to the authenticated Owner by adding `TaskService.listTransitions(taskId, ownerId)` with the same ownership guard as task detail access.
- Fixed `TaskTransitionMapper.selectByTaskId` result mapping by aliasing status columns to `fromStatusCode` and `toStatusCode`; without this, MyBatis tried to map lowercase database values like `draft` through enum `valueOf`.

## 2026-05-23 M1 Phase 5a-1 Context Path Decisions

- Chose the `/api` context-path scheme for frontend/backend consistency: OpenAPI `servers.url` is now `http://localhost:8080/api`, and Spring Boot uses `server.servlet.context-path: /api`.
- Bumped OpenAPI from `0.2.1` to `0.2.2`; this is a PATCH-level contract metadata change because no path or schema shape changed.
- Left `M1ApiIntegrationTest` request paths unchanged after `mvn -pl services/api test` passed in the current environment; MockMvc did not require explicit `/api` prefixes for these tests.
- Verified `InternalTokenFilter` still works with the context path because it checks `request.getRequestURI().startsWith(request.getContextPath() + "/internal/")`.
- Smoke verification used port `18080` because local port `8080` was already occupied by an existing `ssh` listener; this did not change committed configuration.

## 2026-05-23 M1 Phase 5a Frontend Shell Decisions

- Added only the approved frontend dependencies: `react-router-dom`, `@douyinfe/semi-ui`, and `@douyinfe/semi-icons`.
- Chose `createBrowserRouter` with `AppLayout` as the root route so Phase 5b/5c can add login, task list, and task detail routes without replacing the router.
- Kept the Phase 5a UI to an Owner shell only: Header, disabled future-role entries, sidebar navigation, and an empty workspace placeholder.
- Centralized JWT localStorage access in `auth-storage.ts`; direct token reads/writes outside that module remain disallowed.
- Kept Vite proxy default target at `http://localhost:8080`, but added `LABELHUB_API_PROXY_TARGET` as a local-only override because this machine already has a non-LabelHub service on port `8080`.
- Semi UI 2.99.2 does not export `dist/css/semi.min.css`; the app imports the package-exported base stylesheet `@douyinfe/semi-ui/lib/es/_base/base.css` instead.

## 2026-05-23 M1 Phase 5b1 Auth Flow Decisions

- Split Phase 5b into 5b1 auth flow and 5b2 task list/create work so the first authenticated frontend path stays reviewable.
- Implemented token expiry as an active + passive pair: `RequireAuth` rejects missing or expired sessions before protected pages issue requests, while the OpenAPI client still handles server-side 401 as the fallback for revoked, malformed, or tampered tokens.
- Kept `apiClient` independent of React Router by dispatching the `labelhub:unauthorized` browser event; `AuthRedirectBridge` handles `queryClient.clear()` and navigation at the app layer.
- Whitelisted `/login` from global 401 redirects so failed login attempts can render the password-field error instead of causing a route bounce.
- Added a `labelhub:session-changed` event because the browser `storage` event does not fire in the same tab that writes localStorage; the Header uses both events to keep the displayed user state current.
- Logout clears the QueryClient before clearing local session and navigating to `/login`, avoiding stale cached Owner data after account changes.
- `AppLayout` currently owns the Header logout action for the M1 shell; if FSD dependency boundaries become stricter later, move the layout to `app/` or inject the action instead of importing a feature hook from `shared/ui`.

## 2026-05-23 M1 Phase 5b2 Owner Task List Decisions

- Chose URL query parameters as the source of truth for Owner task list state: `page`, `size`, and optional `status` are derived from `useSearchParams`, not duplicated into local React state.
- Status changes reset `page` to `1`, while selecting "全部状态" deletes the `status` query parameter instead of sending `all` or an empty string to the API.
- `useTasksQuery` uses `['tasks', { page, size, status }]` with a 30-second `staleTime` so filter/page changes produce distinct cache entries without adding another state store.
- Successful task creation invalidates the `['tasks']` query family but does not rewrite the current URL; the user stays on the same filtered/page context they were viewing.
- `CreateTaskModal` performs client-side validation for title, positive quota, and future deadline, while still mapping API `fieldErrors[]` back to matching camelCase form fields.
- Browser smoke exposed a backend `TaskStatus` request-parameter binding bug for `status=published`; a small Spring `Converter<String, TaskStatus>` was added so lowercase OpenAPI enum values remain the public contract.

## 2026-05-23 M1 Phase 5c Owner Task Detail Decisions

- Added `/owner/tasks/:taskId` as the Owner detail route and kept the list route unchanged so Phase 5b URL-synced filters remain the list source of truth.
- Used one shared `TransitionTaskModal` for publish, pause, resume, and end actions; the modal title, target status, and confirm button are derived from the clicked transition.
- Made the transition reason required with trimmed validation and a 500-character UI limit because it becomes permanent `task_transitions` audit evidence.
- Rendered transition history from `GET /tasks/{taskId}/transitions` as a chronological Timeline, using the target status color as the dot and showing the current actor as `我`.
- Transition success invalidates the task detail, task transitions, and task-list query family so both the detail badge and list row reflect the new status.
- The frontend currently duplicates the baseline Task transition matrix for button enablement; this is acceptable for M1 UI responsiveness but should be replaced by a contract/API capability when task workflow rules become configurable.

## 2026-05-23 M1 Phase 5c Publish Guard Error Semantics

- Split publish business guard failures from illegal state transitions: `IllegalStateTransitionException` remains HTTP 409, while `TaskPublishGuardException` now returns HTTP 400 with code `PUBLISH_GUARD_FAILED`.
- Publish guard responses include `ApiFieldError` entries using OpenAPI camelCase request-field names, currently `quotaTotal` and `deadlineAt`.
- Clarified the OpenAPI `ApiFieldError` description so future validation and business-guard errors use request schema field names rather than database column names.
- Updated `TransitionTaskModal` to inspect `fieldErrors` before generic 409 handling and to keep the resolved error visible inside the Modal while preserving the reason text.

## 2026-05-23 M1 Phase 5d Wrap-Up Decisions

- Added real Labeler and Reviewer placeholder routes instead of disabled sidebar entries so each required role has an authenticated landing surface without fake business data.
- Sidebar navigation is now role-driven from the stored user profile; users with no matching product module see an explicit no-access note instead of an empty or disappearing sidebar.
- Login and root redirects use explicit role priority `OWNER > LABELER > REVIEWER` instead of relying on backend role-array order, which is not an OpenAPI guarantee.
- README now exposes M1 startup, implemented scope, roadmap, and the four differentiator code locations so reviewers can find the baseline evidence quickly.
- No recording is produced in Phase 5d; recording and acceptance scripting remain Phase 6 work after the M1 surface is reviewed as a whole.

## 2026-05-23 M2 Phase 1 Contract And Schema Migrations

- Bumped OpenAPI from `0.2.2` to `0.3.0` for the M2 schema-management and Labeler-preparation contract surface.
- Kept the public model name `LabelSchema` instead of `Schema` to avoid generated Java conflicts with Swagger annotations while still representing the product-level schema concept.
- Defined `SchemaDocument`, recursive `SchemaField`, seven field types, validation rules, and select options as the contract source for both Designer and Renderer; frontend shared types must derive from generated OpenAPI types in M2-P4.
- Removed the unimplemented `excel` value from `DatasetImportRequest.sourceType`; M2 only supports `json` and `jsonl`, and Excel can be reintroduced in M5 when the import implementation exists.
- Added `tasks.current_dataset_id` rather than `dataset_id` to make the active dataset pointer explicit and symmetric with `tasks.current_schema_version_id`.
- Added `label_schemas.description`, `label_schemas.updated_at`, `datasets.source_name`, and `datasets.error_message` so M2 UI and import error reporting can rely on persisted metadata.
- Added schema read/version endpoints in one contract pass so the M2 Designer can list schemas, inspect versions, and fetch a historical `SchemaVersion` without adding ad hoc endpoints later.
- Expanded `GET /submissions/{submissionId}/render-schema` to return `SubmissionRenderSchema` because historical rendering needs both the bound schema version and answer payload.
- Did not add a schema-version deprecate endpoint in M2-P1 because the current contract did not contain one; schema deprecation semantics remain a pending design item before any mutable lifecycle behavior is exposed.

## 2026-05-24 M2 Phase 2a Schema Foundation

- Added only the schema module foundation in P2a: entities, mappers, schema exceptions, `SchemaValidator`, `StableIdExtractor`, and unit tests; `SchemaService`, controllers, OpenAPI, migrations, security, and frontend remain untouched for later phases.
- Kept `LabelSchemaMapper` on `BaseMapper` because `label_schemas` is a current-state parent table whose `current_version_id` changes when a version is published.
- Kept `SchemaVersionMapper` and `SubmissionMapper` as hand-written append-only mappers with no parent interfaces; reflection tests enforce no inherited mutation surface and no `update`/`delete`/`remove`/`save` methods.
- Used `Map<String, Object>` for `schema_versions.schema_json` and `List<String>` for `field_stable_ids` with `JacksonTypeHandler` and `autoResultMap`, preserving the Entity/DTO boundary while still storing MySQL JSON columns.
- Mapped actual database column names explicitly where they differ from service-facing names: `schema_versions.version_no` maps to `versionNumber`, and `submissions.labeler_id` is exposed through `getSubmittedBy()` for the later render-schema service path.
- Marked `SchemaVersionEntity.ownerId` as non-persistent because the current database stores ownership on parent `label_schemas.owner_id`; P2b should derive version ownership through the parent schema rather than duplicating it into `schema_versions`.
- `SchemaValidator` enforces a non-empty document, non-blank labels/stable IDs, globally unique stable IDs across nested fields, select options for select fields, and non-empty children for `nested_object`.
- `StableIdExtractor` preserves deterministic depth-first traversal order so `field_stable_ids` can later be stored as stable evidence for SchemaVersion publication.

## 2026-05-24 M2 Phase 1.5 Current Schema Pointer Alignment

- Added V7 migration `label_schemas.current_version_id` to align the physical database with the OpenAPI `LabelSchema.currentVersionId` contract before implementing `SchemaService.publishVersion`.
- Kept `current_version_id` nullable because schemas may exist before their first published version; `publishVersion` will set the pointer after inserting the append-only `schema_versions` fact.
- Treated this as a database alignment fix for an M0/P1 schema-completion oversight, not a public contract change; OpenAPI remains `0.3.0`.

## 2026-05-24 M2 Phase 2b Schema Service

- Kept schema management in a single `SchemaService` for M2: `LabelSchema` and `SchemaVersion` are a parent/current-pointer pair, and the version-specific logic is still small enough to keep cohesive.
- `publishVersion` validates the generated OpenAPI `SchemaDocument`, locks the parent `label_schemas` row, computes the next per-schema `version_no`, inserts the append-only `schema_versions` fact, and only then updates `label_schemas.current_version_id`.
- Schema version ownership is derived from parent `label_schemas.owner_id`; `SchemaVersionEntity.ownerId` remains a non-persistent convenience field for service/controller conversion.
- `renderForSubmission` returns a service-layer `SubmissionRenderSchemaView` rather than an OpenAPI DTO, preserving the Service/HTTP boundary while combining submission payload and historical schema-version data.
- `renderForSubmission` intentionally does not filter by requester owner in M2; any authenticated user may render the historical schema for a submission, and later review/visibility rules can tighten this in M4.

## 2026-05-24 M2 Phase 3 Schema API Controllers

- Bumped OpenAPI from `0.3.0` to `0.3.1` and removed `fieldStableIds` from `SchemaVersionRequest`; stable IDs are server-derived from `schemaJson` through `StableIdExtractor`, so accepting a client-supplied copy would have created a misleading contract surface.
- Retagged `GET /submissions/{submissionId}/render-schema` under `Submissions` so OpenAPI generates `SubmissionsApi` and the implementation can live in the submission web module instead of being folded into schema controllers.
- Added schema and submission web controllers with a dedicated `SchemaDtoMapper` boundary: service/entity objects remain internal, while OpenAPI DTOs are produced only at the HTTP edge.
- Protected `/schemas/**` with `ROLE_OWNER`; `GET /submissions/{submissionId}/render-schema` remains available to any authenticated role because M2 historical rendering is visibility evidence rather than Owner-only administration.
- Mapped schema owner mismatches to 404 through `SchemaAccessDeniedException` to avoid schema/version enumeration by other Owner accounts, while wrong-role access to `/schemas/**` still fails at SecurityConfig with 403.
- Wired optional `GET /schemas?q=` filtering through `SchemasController` into `SchemaService.list`; the query parameter is owner-scoped and matches schema name or description instead of being accepted and ignored.
- Excluded `com.labelhub.api.generated.*` from Spring component scanning after runtime smoke showed Boot hanging while scanning generated OpenAPI classes; generated models/interfaces are compile-time contracts, not beans.
- Explicit `@Results` type handlers were added to hand-written schema/submission mappers after smoke showed JSON columns selecting as `null`; MyBatis annotations now deserialize `schema_json`, `field_stable_ids`, `answer_payload`, and `provenance` consistently with the entity annotations.

## 2026-05-24 M2 Phase 4a Schema Designer Foundation

- Added `entities/schema` as the frontend's generated-type facade: features import schema aliases and runtime field-type constants from one boundary instead of directly depending on generated OpenAPI paths.
- Defined P4 `nested_object` semantics as same-level editing: nested containers can own children, and children can be ordered within their level, but cross-level drag between top-level fields and nested children is deferred to an M6 enhancement.
- Kept Designer draft state session-scoped for P4; the UI warns that unpublished changes live only in the current page session, and persistent drafts remain a later product decision.
- Started Schema Designer with a two-column operational layout rather than a three-column canvas; JSON is treated as a transparency/debugging surface, not the primary editing workflow.
- Deferred schema creation from `/owner/schemas`; M2-P4a lists existing task-bound schemas and opens the Designer shell, while a first-class creation entry remains pending.

## 2026-05-24 M2 Phase 4b Schema Designer Editing

- Implemented Designer editing as a session-local draft only: field add/update/delete, same-level drag order, validation, and JSON preview mutate React state but do not publish or write backend data until P4c.
- P4b UI 不支持多层 nested_object 或跨级拖拽,这是 Designer UX 简化,不是 schema 契约或后端 Validator 收紧。OpenAPI 仍允许递归 children;后端 SchemaValidator 仍不限制嵌套层数。M6 加分时仅需扩展前端 UI 入口。
- Kept frontend validation to UI-safety checks only: blank labels, empty select options, empty nested objects, and P4b multi-level nested UI limits. The backend `SchemaValidator` remains the authoritative schema contract.
- Used `stableId` as the dnd-kit sortable id and recursive mutation key so ordering, deletion, and nested-child edits preserve schema identity rather than relying on labels or array positions.
- Enabled both pointer and keyboard dnd sensors for `FieldList`; pointer drag remains the primary mouse path, while keyboard sorting provides an accessible and repeatable verification path.
- Kept JSON preview read-only and collapsed by default; it is a transparency/debugging view over the current draft, not a second editing surface.

## 2026-05-24 M2 Phase 4b Responsive Polish

- Kept the Schema Designer as a two-column operational workspace at narrow browser widths by adding a shared minimum-width canvas and allowing horizontal scrolling inside the content area instead of collapsing the editor into one column.
- Unified the Designer heading, warning banner, and two-column grid under the same canvas so borders and content edges scale together rather than drifting independently as the viewport changes.
- Changed field selected/error emphasis from hard full-border color swaps to inset status bars and soft inset outlines, preserving layout dimensions while still making selection and validation states visible.

## 2026-05-24 M2 Phase 4c Schema Publish And Version History

- Chose a publish confirmation Modal with an immutable-version warning and a field summary rather than a full diff; the summary gives reviewers a final check without adding a separate diff algorithm in M2.
- Publishing keeps the Owner in the Designer, resets `isDirty`, and treats the just-published schema as the next editing baseline so users can continue toward the next version without losing context.
- Version history uses a read-only `SideSheet` with newest versions first, current-version tagging, short content-hash display, field counts, and expandable JSON; it proves version content without adding rollback/fork behavior.
- Repeated publish attempts are not blocked by the frontend: the backend owns the content-hash uniqueness invariant and now returns HTTP 409 with `DUPLICATE_SCHEMA_VERSION_CONTENT` instead of leaking a database duplicate as a 500.
- Publish failures render inside the Modal instead of only as Toasts so backend `fieldErrors` and duplicate-content conflicts stay close to the user action and can be corrected without losing Modal context.
- The publish mutation invalidates the schema query family so Designer header state, schema list current-version state, and version-history data all converge on the new current pointer after a successful publish.

## 2026-05-24 M2 Phase 4.5 Schema And Task Pointer Alignment

- Extended `SchemaService.publishVersion` to update both `label_schemas.current_version_id` and the owning `tasks.current_schema_version_id`; this keeps the task-level claim authority aligned with the schema-level current pointer.
- Extended `TaskService.canPublish` to require both `current_schema_version_id` and `current_dataset_id` before a task can move to `published`, preventing Labeler marketplace entries that cannot be claimed or rendered.
- Treated this as a contract-alignment fix left over from P1.5/P4 rather than a new user-visible capability: OpenAPI, migrations, controllers, and frontend code remain unchanged.

## 2026-05-24 M2 Phase 5a Labeler Marketplace And Claim

- Bumped OpenAPI from `0.3.1` to `0.4.0` for the first real Labeler backend surface: marketplace listing, claim, `Session`, `SessionStatus`, and marketplace-specific task DTOs.
- Introduced optimistic quota locking for claim with `UPDATE tasks SET quota_claimed = quota_claimed + 1 WHERE ... quota_claimed < quota_total`; this differs from M1 task transitions, where pessimistic parent-row locking is still appropriate because transitions combine state legality and append-only audit facts.
- Kept dataset item assignment as FIFO with `SELECT ... WHERE status='available' ORDER BY ordinal, id LIMIT 1 FOR UPDATE`; quota is guarded optimistically, while item selection still needs a row lock so two transactions cannot claim the same item.
- Kept `dataset_items.status` single-purpose: it tracks claim availability only. P5a changes `available -> claimed`; submission lifecycle is represented by `sessions.status` and `submissions`, not by adding a second lifecycle source to dataset items.
- Bound `sessions.schema_version_id` at claim time from `tasks.current_schema_version_id`; this makes each Labeler session stable even if an Owner publishes a newer schema while the Labeler is working.
- Marketplace includes only published tasks with schema and dataset pointers, remaining quota, future deadline, and at least one available item. Per-task available item counts are computed with a simple N+1 count in P5a because marketplace pages are small; batching can be optimized later without changing the contract.

## 2026-05-24 M2 Phase 5b Session Detail And Draft Revisions

- Bumped OpenAPI from `0.4.0` to `0.4.1` for the Labeler session/draft read-write surface: session detail, my sessions, latest draft, and append-only draft saving.
- Used a dedicated `SessionTask` DTO inside `SessionDetail` instead of reusing `MarketplaceTask`; session detail needs stable task context, not marketplace availability fields like `availableItemCount`.
- Kept `PUT /sessions/{sessionId}/draft` intentionally non-idempotent: every save creates a new append-only draft revision, and the OpenAPI description tells the frontend to throttle autosaves rather than expect PUT replacement semantics.
- `DraftMapper` is a hand-written append-only mapper with no `BaseMapper` parent; reflection tests guard against inherited update/delete mutation surfaces.
- `saveDraft` locks the parent session with `SELECT ... FOR UPDATE`, verifies Labeler ownership and `claimed` status, computes `MAX(revision_no)+1`, and inserts the new draft in the same transaction.
- Session ownership mismatches continue to return 404, matching the P3 anti-enumeration pattern; submitted sessions reject draft writes with 409 `SESSION_NOT_EDITABLE`, and missing drafts return 404 `DRAFT_NOT_FOUND`.
- Entity/mapper mappings follow the physical V1 schema: `drafts` has `saved_at` but no `created_at`, and `sessions` has `claimed_at/submitted_at` but no `created_at`; no migration was added for fields that are not in the original tables.

## 2026-05-24 M2 Phase 5c Submit And Submission Detail

- Bumped OpenAPI from `0.4.1` to `0.4.2` for final Labeler submission: `SubmitSessionRequest` accepts only `answerPayload`, and `Submission.contentHash` is explicitly server-derived from canonical answer payload JSON.
- `SessionService.submit` inherits `schema_version_id` from the locked `sessions` row and never re-reads `tasks.current_schema_version_id`; this preserves claim-time schema binding even if an Owner publishes a newer version before or after submit.
- Submission write order follows the append-fact-before-current-state pattern: insert the append-only `submissions` row first, then update `sessions.status` to `submitted` and set `submitted_at`.
- P5c does not update `dataset_items.status`; dataset item status remains a single-purpose claim-availability flag, while submission lifecycle is represented by `sessions` and `submissions`.
- `GET /submissions/{submissionId}` is available to authenticated callers at the route layer but only returns rows owned by the requesting Labeler; mismatches return 404 to avoid submission enumeration. `GET /submissions/{submissionId}/render-schema` keeps the P3 cross-role historical-render contract.
- `SubmissionEntity` and `SubmissionMapper` remain in `module/schema` for P5c to avoid a package-move refactor during submit implementation; a later domain-boundary cleanup can move them under `module/submission`.

## 2026-05-24 M2 Phase 6a Labeler Renderer Kernel

- Kept `answerPayload` stableId-keyed, including nested object values as nested stableId-keyed objects; answer binding is independent of field order, label changes, and Designer drag operations.
- Implemented one `SchemaRenderer` with `readOnly` rather than separate edit/read component trees so the Labeler work page and historical Submission detail share the same field rendering logic.
- M2 P6 `FileUploadFieldRenderer` renders only a text placeholder for a URL or filename and does not perform real file upload. OpenAPI `0.4.2` has no multipart upload endpoint and MinIO integration is deferred to M3; this is a contract-aligned simplification, not a narrowing of the `file_upload` schema type.
- `useLatestDraftQuery` maps HTTP 404 to `null` because "no draft saved yet" is a valid state after the caller has already verified session access through `useSessionDetailQuery`; callers that need session existence checks must not use draft 404 as an ownership signal.
- `useAutosave` is designed as debounce plus max-wait with an explicit `disable()` hook for submit-time shutdown, preventing late autosave callbacks from writing to a session after it becomes submitted.
- P6a could not add Vitest fake-timer tests because the web workspace has no test stack installed and dependency installation was blocked; typecheck/build remain the verification for this slice, and adding a frontend test harness is a follow-up before expanding hook tests.

## 2026-05-24 M2 Phase 6b Live Labeling Flow

- Claim success routes directly from the marketplace to `/labeler/sessions/{sessionId}` with a Toast because claiming is an intentional "start working now" action; the marketplace remains a discovery surface, not a post-claim holding page.
- Autosave status is shown as a compact header Tag with Tooltip details, and saved time uses an absolute `Intl.DateTimeFormat` timestamp instead of relative time, avoiding stale "minutes ago" text without adding `dayjs` or an interval timer.
- The session page hydrates `answerPayload` from the latest draft only once through a `hasInitialized` guard; later draft query refetches caused by autosave must not overwrite in-memory edits.
- Submit follows the P6 race-control order: `flush()` pending autosave work, `disable()` future autosaves, then submit the current in-memory `answerPayload`; P6b reports submit failure with a Toast and leaves richer autosave recovery for a follow-up.
- Renderer editability is derived from `session.status`: claimed sessions are editable, while any non-claimed session renders read-only and disables submit even if reached by direct URL.

## 2026-05-24 Build Hygiene: Spring Boot Main Class And iCloud Conflict Artifacts

- Repeated `ApiApplication 2.class` / `com 2/...` build artifacts were traced to the workspace living under an iCloud Drive-backed Desktop folder; clean Maven compilation produces only one `ApiApplication.class`, so the duplicate class files are build-output pollution rather than source or generator output.
- `spring-boot-maven-plugin` now declares `mainClass=com.labelhub.api.ApiApplication` so local `spring-boot:run` does not rely on scanning `target/classes` for a single main class. This mitigates the smoke blocker even if conflict artifacts appear again.
- The source-of-truth fix remains operational: keep active build workspaces outside iCloud/File Provider synced folders, or exclude/relocate build outputs. The Maven mainClass setting is a guardrail, not a cure for filesystem-level duplicate artifacts.

## 2026-05-25 Build Hygiene: Workspace Relocation

- Moved the active LabelHub workspace from the iCloud/File Provider-backed Desktop path to `/Users/gods./Downloads/LabelHub - Platform`; future local commands and smoke runs should use this path as the working directory.
- Verified the old Desktop project path is gone and the new project folder no longer carries the `com.apple.CloudDocs.iCloudDriveFileProvider` marker. Historical Desktop-path references in this log remain as evidence for the original duplicate-class investigation.
- Cleared stale iCloud trash metadata from the moved project folder; the remaining `mainClass` Maven setting stays as a guardrail in case generated build output is polluted again.

## 2026-05-24 M2 Phase 6c History And Evidence Flow

- Added a shared `schemaVersionLabel` helper and used it in both the live session page and historical submission page so schema-version display has one fallback policy: `v{versionNumber}`, then `#{id}`, then `未绑定版本`.
- The Labeler "我的数据" page routes both claimed and submitted sessions to `/labeler/sessions/{id}` because `GET /my/sessions` does not include `submissionId`; submitted sessions already render read-only there, while direct historical submission detail remains available after submit success or by URL.
- The Submission detail page uses `GET /submissions/{id}/render-schema` as its schema source and never reads the task's current schema pointer; this keeps the UI aligned with the P5c submission-time historical-render contract.
- Cross-role access uses the C方案: Labeler pages are guarded by frontend `RequireRole(['LABELER'])`, while cross-labeler ownership still relies on backend 404 responses and page-level "不存在或无权访问" messaging.
- Added the Labeler sidebar "我的数据" entry as the primary route for session history; submission detail remains a child/evidence route rather than a sidebar destination.

## 2026-05-25 M2 Phase 7a Dataset Import Backend

- Bumped OpenAPI from `0.4.2` to `0.5.0` for Owner dataset import, dataset listing/detail, and the dedicated task current-dataset pointer mutation.
- Replaced the exploratory JSON body import draft with the approved multipart contract: `POST /datasets` accepts `file`, `taskId`, optional `sourceName`, and optional `format`; the old `/tasks/{taskId}/datasets:import` route was removed.
- Format resolution is explicit-first and extension fallback second: `format=json/jsonl` wins, otherwise `.json` and `.jsonl` decide; content sniffing is not used as primary behavior.
- JSON import requires a top-level array of objects, JSONL requires one object per nonblank line, and empty datasets fail with a distinct `EMPTY_DATASET` code.
- `dataset_items.ordinal` is 1-based so DB facts and UI language can both say "第 1 条" without frontend offset translation.
- `item_hash` uses the shared canonical JSON SHA-256 path, but duplicate item payloads are accepted because repeated samples can be a deliberate labeling or quality-control fact.
- Dataset import is synchronous in M2: a successful request inserts the `datasets` row and all `dataset_items` rows in one transaction and returns `importStatus=completed`; async jobs and large-file handling remain M5 work.
- `PATCH /tasks/{taskId}/current-dataset` is a dedicated pointer mutation with row lock, Owner ownership guard, `published` lock, and `dataset.task_id == taskId` validation instead of broadening a general task update endpoint.

## 2026-05-25 M2 Phase 7b Dataset Upload UI

- Kept dataset management embedded as a section inside Owner task detail instead of adding tabs or a standalone `/owner/datasets` route; P7b needs task context and should not restructure the page shell.
- Kept upload and "set current dataset" as two explicit operations: upload creates a reusable dataset fact, while the current pointer remains a separate Owner decision.
- Used native `fetch` for multipart upload with the existing `getAccessToken` helper and the same `VITE_API_BASE_URL ?? '/api'` base URL rule as `apiClient`; the request deliberately omits `Content-Type` so the browser supplies the multipart boundary.
- Published tasks still allow dataset uploads because importing data does not change the task pointer, but the "设为当前" action is disabled with the published-task tooltip.
- P7b live smoke exposed a generated-interface validation mismatch in `DatasetsController`; the implementation now mirrors the generated `DatasetsApi` parameter annotations so Hibernate Validator accepts the override.
- P7b live smoke exposed lowercase multipart `format=json/jsonl` not converting through Spring's default enum converter; a request-param converter now delegates to OpenAPI's `DatasetImportFormat.fromValue`.

## 2026-05-25 M2 Quarter Closing Summary

### M2 完成 Phase 列表

- P0 / P1 / P1.5 / P2a / P2b / P3 / P4a / P4b / P4c / P4.5 / P5a / P5b / P5c / P6a / P6b / P6c / P7a / P7b / P8.

### 关键工程决策回顾

1. Schema 版本化采用 append-only `schema_versions` + `label_schemas.current_version_id` 双结构，版本内容不可原地改写。
2. `sessions.schema_version_id` 在 claim 时绑定，`submissions.schema_version_id` 继承 session，而不是读取 task 当前 schema pointer。
3. Dataset item import 使用 1-based ordinal、canonical SHA-256 `item_hash`，且不对重复 payload 去重。
4. Schema Designer 和 Labeler Renderer 都围绕 stable field IDs 工作，避免 label/order 变化破坏答案绑定。
5. `SchemaRenderer` 通过 `readOnly` 复用一棵组件树，live session 和 historical submission 不分叉实现。
6. `PUT /sessions/{id}/draft` 是非幂等 append-only revision 写入，前端用 debounce/max-wait 控制保存频率。
7. 跨角色防御采用 C 方案：前端 `RequireRole` 阻止明显错角色路径，后端 ownership mismatch 继续返回 404。
8. Multipart 上传通过原生 `fetch` + `getAccessToken` + browser-managed boundary，避开 `openapi-fetch` JSON body 假设。

### M2 阶段真实暴露并修复的契约 / 物理 schema gap

1. `label_schemas.current_version_id` 在 V1 缺失，P1.5 通过 V7 migration 补齐。
2. `tasks.current_schema_version_id` 未随 schema publish 维护，P4.5 让 `publishVersion` 同步 task pointer。
3. `sessions.created_at` / `drafts.created_at` 在物理 schema 中不存在，P5b 按 V1 真实列修正 Entity/Mapper。
4. `fieldStableIds` 是 server-derived，不应由 `SchemaVersionRequest` 客户端提交，P3 修正契约。
5. Schema version duplicate content 最初通过 DB unique key 落 500，P4c 映射为 409 `DUPLICATE_SCHEMA_VERSION_CONTENT`。
6. `dataset_items.status` 职责保持单一，只表达可领取性；提交事实由 `sessions` + `submissions` 表达。
7. Multipart form 的 `format=json/jsonl` 小写值无法通过 Spring 默认 enum 转换，P7b 注册 converter。
8. Generated OpenAPI interface 的 multipart validation annotations 需要在 controller override 上 mirror，P7b 对齐 `DatasetsController`。

### 工作流演化里程碑

- M0 主要等待审查，M1 中期开始审完即修。
- M1 后期开始主动 push back prompt 与代码事实不一致处。
- M2-P1.5 主动核对契约漂移并补齐 schema pointer。
- M2-P5 / P6 多次以物理 schema 和真实 smoke 纠正 prompt 假设。
- M2-P6b 严格执行 500 行汇报安全阀。
- M2-P7a 主动暴露预探草稿，并按裁决删除重写。
- M2-P7b 通过真实 live smoke 暴露 multipart 集成坑，并完成 root-cause 修复与透明记录。

### P8 收尾决策

- P8 只整理文档与 evidence 索引，不动代码、不改 OpenAPI、不改 migration、不重新生成截图。
- README 面向评审和答辩复现，不写 marketing copy。
- `docs/m2-acceptance-checklist.md` 成为 M2 17 步 smoke 的机械复现入口。
- `docs/internal/m3-startup-overview.md` 只保留 M3 总览和元问题，M3 代码实施等待 M3-P0 裁决。

## 2026-05-25 M3 Phase 1 AI Contract And Append-Only Mapper Foundation

- Bumped OpenAPI from `0.5.0` to `0.6.0` for the M3 AI review/provenance surface: `POST /submissions/{submissionId}/ai-review` triggers Owner-side review, while `GET /submissions/{submissionId}/ai-review` returns Owner/Labeler-visible provenance.
- Replaced the earlier AI review `verdict` contract with `overallSuggestion` and strongly typed `FieldFinding` records; M3 stores AI suggestions as provenance facts and does not derive Quality Ledger verdicts.
- Added `idempotencyHit` to `AiReviewResult` and reserved `AI_PROVIDER_INPUT_HASH_MISMATCH` for the input-hash guard: same idempotency key plus different input hash must fail instead of silently reusing stale AI evidence.
- Kept M3 AI call states append-only and terminal: `AiCallStatus` contains only `completed` and `failed`; `created/processing` are intentionally deferred until an async M5 worker exists.
- Modeled `ai_calls` and `ai_calls_in_field` with hand-written insert/select mappers and reflection contract tests, avoiding `BaseMapper` so update/delete methods cannot leak into append-only provenance facts.
- Exposed `AiProviderException` with `retryable`, `providerCode`, and `statusCode` fields as provider-facing diagnostics, while public API failures map through `AI_PROVIDER_FAILURE` without exposing raw provider internals.

## 2026-05-25 M3 Phase 2 AI Review Core With Mock Provider

- Implemented only the mock provider path for M3-P2; real Anthropic/OpenAI providers remain M3-P6 work so this phase stays focused on core provenance correctness rather than external API integration.
- Reused the shared `Canonicalizer` for AI input and output hashes, keeping schema versions, submissions, dataset items, and AI provenance on one canonical SHA-256 fact model.
- `MockAiProvider` is deterministic and schema-driven: it traverses top-level and nested schema fields in order and emits stableId-path `FieldFinding` rows without sleeping.
- `AiReviewService.review` excludes labeler identity from provider input; it includes immutable submission facts, historical schema fields, dataset item payload, and task context needed for review.
- Idempotency uses `submission:{id}:provider:{provider}:model:{model}:prompt:{promptVersion}` plus an input-hash guard: same key and same hash returns persisted evidence, while same key and changed input throws `AI_PROVIDER_INPUT_HASH_MISMATCH`.
- M3-P2 intentionally does not persist failed provider calls as `status=failed`; provider failures are wrapped as `AI_PROVIDER_FAILURE`, while failed-call evidence and retry/backoff semantics remain M5 work.

## 2026-05-25 M3 Phase 3 AI Review HTTP Surface And Provenance Read

- Implemented the M3 AI review HTTP surface through the generated `AiReviewApi`: `POST /submissions/{submissionId}/ai-review` triggers Owner review, and `GET /submissions/{submissionId}/ai-review` returns submission-level provenance.
- Kept generated-interface annotation parity on `AiReviewController` method parameters (`@PathVariable`, `@Valid`, `@RequestBody`) to avoid the multipart-style override validation mismatch encountered in P7b.
- Inserted AI review security rules before the authenticated fallback: POST requires `ROLE_OWNER`, while GET requires any authenticated user and delegates Owner/Labeler ownership to `AiReviewService.getProvenance`.
- `AiReviewDtoMapper` maps only public provenance fields. It exposes `providerName`, hashes, status, token/cost/latency metadata, and field-row facts, but does not expose `requestPayload`, `responsePayload`, `scores`, raw prompt, or raw response.
- `outputHash` remains a derived API field: the review path sets it from provider output, and the provenance read path recomputes it from persisted `responsePayload` without storing it in `ai_calls`.
- Existing AIReview draft endpoints in the generated interface (`field-assist`, `ai-trace`, `rules`) are wired only as `501 Not Implemented` stubs; their business behavior remains outside M3-P3.
- Added disabled-without-Docker integration coverage for Owner trigger, Labeler POST 403, cross-owner trigger 404, Owner/Labeler provenance reads, cross-labeler GET 404, idempotency hit reuse with one provider invocation, and different prompt versions creating a new `ai_calls` row.

## 2026-05-25 M3 Phase 3.5 Owner Submission Discovery And Render-Schema Guard

- Bumped OpenAPI from `0.6.0` to `0.6.1` as a patch-level contract补口: `GET /tasks/{taskId}/submissions` gives Owners a paged submission discovery surface without exposing `answerPayload` or `contentHash`.
- Kept Owner submission discovery under the task resource because the UI entry point is Owner task detail; the endpoint returns only summary facts needed for navigation and AI review triggering.
- Added `SubmissionService.listByTaskForOwner` with task ownership validation that maps missing and cross-owner tasks to `TaskNotFoundException`, preserving the platform's 404 anti-enumeration pattern.
- Extended the append-only `SubmissionMapper` with `selectPageByTaskId` and `selectCountByTaskId`; the count method keeps the `select*` prefix so reflection contract tests continue to mechanically forbid update/delete surfaces.
- Patched the M2-P5c `render-schema` ownership gap: `SchemaService.renderForSubmission` now allows only the submission's Labeler or the task Owner. Previously any authenticated user could read any submission's historical schema and bound answer payload.
- Added disabled-without-Docker integration coverage for Owner submission listing, cross-owner/cross-role defenses, cross-labeler render-schema 404, non-owning Owner render-schema 404, and positive Labeler/Owner render-schema reads.

## 2026-05-25 M3 Phase 4 Owner AI Review UI

- Added the Owner AI review UI as a nested route `/owner/tasks/{taskId}/submissions/{submissionId}` instead of a global submission route because the backend discovery contract is task-scoped and there is no Owner "lookup by submission id" endpoint.
- Kept the Owner submission detail page on independent query boundaries: it reads historical render payload through `GET /submissions/{id}/render-schema`, AI metadata through `GET /submissions/{id}/ai-review`, and never calls the Labeler-only `GET /submissions/{id}` endpoint.
- Embedded the task submission list as a Task detail section, matching the P7b dataset section pattern and avoiding a new submission-management top-level route before M4 review workflow is designed.
- The AI review Drawer is intentionally C-simplified: it shows only the current trigger result, including `idempotencyHit`, provider/model metadata, hashes, summary, and field findings. Historical call switching remains later UI work.
- The provenance Card shows only public `AiCall` metadata from the GET endpoint. It does not infer or display `overallSuggestion` because the GET contract deliberately keeps raw `responsePayload` server-only.
- P4 live smoke exposed a backend-derived-field mismatch: first-trigger `outputHash` was computed from provider output before JSON persistence normalization, while GET/idempotency paths hashed the persisted JSON shape. `AiReviewService` now normalizes response payloads through the same JSON shape before storing and hashing, so first POST, repeat POST, and GET provenance share one `outputHash` fact.
- Added a frontend typecheck contract file for the P4 page, Drawer, hooks, and table components because the web workspace still has no component test runner; the contract is compile-time only and not imported at runtime.

## 2026-05-25 M3 Phase 5 Labeler AI Provenance Display

- Extracted the Owner provenance metadata list into a shared `AiProvenanceCard` instead of duplicating JSX in the Labeler submission page; Owner, Labeler, and future Reviewer surfaces now share one public provenance display.
- Moved provenance loading inside `AiProvenanceCard`, so `OwnerSubmissionPage` only blocks on historical render-schema data and the AI history card becomes an independent auxiliary loading unit.
- Labeler submission detail now shows the same provider/model, prompt, status, cost, latency, completed time, input hash, and output hash metadata as Owner detail; LabelHub keeps provenance transparent while still not exposing raw request/response payloads.
- The shared empty state now says "该 submission 暂无 AI 调用记录" instead of referencing the Owner-only AI trigger button, so the component is role-neutral and safe for read-only Labeler use.

## 2026-05-25 M3 Phase 6 OpenAI-Compatible Provider And Evidence Closeout

- Implemented a generic `OpenAiCompatibleProvider` instead of a vendor-specific provider; provider switching is now driven by `labelhub.ai.active-provider` plus env-backed base URL, API key, model name, and provider name.
- Kept committed configuration provider-neutral: `application.yml` defaults to mock and leaves real provider URL/model empty so the repository does not encode a specific vendor preference.
- `AiReviewService` now injects the `AiProvider` interface without a `mockAiProvider` qualifier; Spring conditional beans choose mock or OpenAI-compatible at startup, so business logic does not change when the provider changes.
- `OpenAiCompatibleProvider` fails fast at startup when required config is missing under `active-provider=openai-compatible`, preventing a misconfigured real-provider mode from reaching the first Owner trigger.
- Provider tests use a JDK `HttpServer` fake server instead of adding MockWebServer/WireMock, verifying bearer auth, request body shape, JSON response parsing, retryable 5xx errors, non-retryable 4xx errors, and invalid JSON handling at wire level.
- M3-P6 does not claim real provider smoke. The implementation is complete and mock evidence remains valid, while real OpenAI-compatible smoke is tracked as a pending task until API key/credits are available.
- Added `docs/m3-acceptance-checklist.md` and updated README/screenshot evidence docs so M3 can be reviewed mechanically without inventing screenshots that do not exist.

## 2026-05-25 M3 Quarter Closing Summary

### M3 完成 Phase 列表

- P0 / P1 / P2 / P3 / P3.5 / P4 / P5 / P6.

### 关键工程决策回顾

1. Provider abstraction uses one `AiProvider` interface and config-selected beans; OpenAI-compatible backends can be swapped by env without changing `AiReviewService`.
2. M3 default provider remains mock for deterministic tests and smoke; real providers require explicit env and fail fast when required values are missing.
3. Idempotency key is `submission:{id}:provider:{provider}:model:{model}:prompt:{promptVersion}` so provider/model/prompt changes produce new AI facts.
4. Idempotency reuse is guarded by input hash: same key plus different input hash returns 409 instead of silently reusing stale evidence.
5. Schema, submission, dataset item, and AI input/output facts reuse the same canonical SHA-256 model.
6. AI input excludes labeler identity; provider payloads receive task/submission facts, historical schema, answer payload, and dataset item payload only.
7. `ai_calls` and `ai_calls_in_field` remain append-only through hand-written mappers and reflection contract tests.
8. Raw request/response payloads are persisted for audit but not exposed through public OpenAPI DTOs or frontend components.

### M3 阶段真实暴露并修复的工程问题

1. M3-P3 process note raised a possible 350-line safety-valve deviation; review clarified the M3 line-budget口径 as feature code, while larger integration-test code is tracked separately.
2. M3-P3.5 patched the M2-P5c `render-schema` ownership gap and added cross-identity tests for cross-labeler and non-owning Owner reads.
3. M3-P4 live smoke exposed `outputHash` mismatch between pre-persistence provider output and persisted JSON shape; `AiReviewService` now normalizes response payloads before storing and hashing.
4. M3-P6 provider tests exposed local-sandbox socket binding restrictions; the wire-level JDK `HttpServer` tests require local bind permission and pass when run with that permission.

### 工作流演化里程碑

- M3-P0 restored explicit strategic decisions after "安排任务即可" was rejected as too vague for provider/evidence scope.
- M3-P3 transparently surfaced the safety-valve口径 issue and tightened the distinction between feature code and test code budgets.
- M3-P3.5 turned a P4 prompt gap into a dedicated backend补口 phase rather than hiding Owner discovery and render-schema guard work inside a frontend phase.
- M3-P4 used live smoke to catch a backend hash derivation mismatch that unit tests had not exposed, then added RED/GREEN coverage.
- M3-P5 upgraded the Owner-only provenance markup into a shared role-neutral component only when the Labeler use case became real.
- M3-P6 changed the finish-line from "integrate one vendor" to "prove provider portability", aligning AI provenance with the platform's vendor-neutral evidence model.

## 2026-05-25 M4 Phase 1 Quality Ledger Contract And Append-Only Mapper Foundation

- Bumped OpenAPI from `0.6.1` to `0.7.0` for the M4 Quality Ledger and real-time Verdict surface: Reviewer queue, ledger-entry append/read, and submission-nested verdict derivation are now explicit contract resources.
- Reused the V1 physical `quality_ledger_entries.evidence_type` column instead of adding a cosmetic V8 migration. The API exposes `entryType`, while the Entity keeps `evidenceType` and later DTO mapping will make the conversion explicit, matching the M3 `modelProvider` to `providerName` pattern.
- Kept `QualityLedgerEntryType` contract-scoped to the one implemented M4 value, `reviewer_overall_verdict`; future values such as field decisions or AI findings will be added only when M5 implements their behavior.
- Made `ReviewerOverallVerdictPayload` a typed OpenAPI schema and used it for both create requests and read models, avoiding an unstructured payload object for the M4 ledger write path.
- `selectLatestReviewerOverallVerdict` orders by `created_at DESC, id DESC` so verdict derivation is stable even when multiple ledger entries share the same millisecond timestamp.
- `QualityLedgerEntryMapper` remains append-only through insert/select-only methods and a reflection contract test. It also provides a reviewer queue projection with latest-verdict filtering at SQL level to keep the later service phase from weakening pagination semantics.
- M4-P1 intentionally does not maintain `current_verdicts`; M4 Verdict remains a real-time derived view over the append-only ledger until a later materialized-cache phase has a concrete consistency design.

## 2026-05-25 M4 Phase 2 Quality Ledger Services And Real-Time Verdict Derivation

- Split the M4 quality core into `LedgerService`, `VerdictService`, and `ReviewerQueueService` instead of a single broad service; the boundary keeps ledger append facts, derived verdict reads, and reviewer queue reads independently testable for M5 expansion.
- Services accept `Set<String> requesterRoles` explicitly rather than reading `SecurityContext` or `@PreAuthorize` state. Controllers will own HTTP role extraction in M4-P3, while service tests can exercise Labeler, Owner, and Reviewer access as pure business inputs.
- `SchemaService.renderForSubmission` now has a role-aware overload so Reviewers can read historical submission schema/answer facts during M4 review. The original two-argument method remains for existing Owner/Labeler paths and delegates with an empty role set.
- `VerdictView.derivedAt` uses the query clock, not ledger entry time, because M4 Verdict is a live derived view over the append-only ledger rather than a materialized fact row.
- Ledger writes use double guards: OpenAPI constrains `QualityLedgerEntryType`, while `LedgerService` still enforces the `reviewer_overall_verdict` whitelist and validates `payload.verdict` as `approve` or `reject` before inserting.
- M4 self-review is blocked in the service layer even before assignment semantics exist; a user reviewing their own submission would undermine the Quality Ledger trust boundary.
- Added a mapper SQL contract for `ORDER BY created_at DESC, id DESC` on latest-reviewer verdict selection so the millisecond tie-break remains mechanically visible until integration tests execute the real query.

## 2026-05-25 M4 Phase 3 Quality Ledger HTTP Surface And Reviewer Security

- The generated OpenAPI surface grouped reviewer queue, ledger-entry append/read, verdict read, and adjudication recompute under `ReviewsApi`, even though several paths are submission-nested. M4-P3 implements that generated boundary in one `ReviewerController` and leaves `SubmissionsController` responsible only for the existing submission-render endpoint.
- Controller role extraction uses `JwtPrincipal.roles()` raw role codes (`REVIEWER`, `OWNER`, `LABELER`) rather than `Authentication.getAuthorities()` (`ROLE_*`), because the service boundary established in M4-P2 accepts raw role codes.
- `SubmissionsController.getSubmissionRenderSchema` now calls the role-aware `SchemaService.renderForSubmission(submissionId, userId, roles)` overload, making the M4 Reviewer render-schema path live while preserving the old two-argument service overload for existing callers.
- `QualityDtoMapper` makes physical/API naming conversions explicit: `evidenceType` becomes public `entryType`, `actorId` becomes `actorUserId`, and the JSON payload is converted to/from the strong `ReviewerOverallVerdictPayload` DTO instead of leaking a raw map.
- Security routing splits Quality Ledger writes from reads: `POST /submissions/*/ledger-entries` requires `REVIEWER`, while ledger and verdict GET endpoints require authentication plus service-level role/ownership checks.
- Added disabled-without-Docker HTTP evidence tests for reviewer queue filters, ledger append, self-review 409, live verdict re-derivation, reviewer render-schema access, and cross-identity ledger read defenses.

## 2026-05-25 M4 Phase 4 Reviewer UI

- Reviewer queue state is URL-synced (`page`, `size`, and `verdict`) so refreshes preserve the review filter and the URL itself is useful smoke evidence.
- Approve/reject writes do not use optimistic state; the mutation invalidates the submission verdict, submission ledger entries, and reviewer queue queries so the UI reflects the live derived view returned by the backend.
- Reviewer submission detail uses `/reviewer/submissions/{submissionId}` without task nesting because Reviewers enter from the global review queue, unlike Owner submission detail which is task-contextual.
- The page composes existing primitives (`SchemaRenderer` and `AiProvenanceCard`) instead of extracting a page shell. Owner and Reviewer submission pages share data primitives but keep their role-specific workflow layout independent.
- `roleRoutes` now sends Reviewers to `/reviewer/submissions` instead of the original M1 placeholder, making the Reviewer authenticated entry point real once M4 functionality exists.
- Live browser smoke exposed two read-path gaps after the M4 UI connected everything: `ReviewerController` needed generated `@Min/@Max` annotation parity, and AI provenance needed the same raw-role read path as render-schema. Both now have focused controller/service tests.
- The generic forbidden page now uses role-neutral copy; Owner hitting `/reviewer/**` no longer sees an Owner-specific denial message.

## 2026-05-25 M4 Quarter Closing Summary

### M4 完成 Phase 列表

- P0 / P1 / P2 / P3 / P4 / P5.

### 关键工程决策回顾

1. Quality Ledger 是亮点 2 的工程焦点,不是可以绕开的辅助表;M4 必达是让 ledger -> verdict 派生关系在 UI/API/DB 三层可演示。
2. 复用 V1 `quality_ledger_entries.evidence_type` 物理列,API 暴露 `entryType`,Mapper/DtoMapper 显式转换,不为命名美观添加 V8 migration。
3. `QualityLedgerEntryType` enum 仅暴露 M4 已实现的 `reviewer_overall_verdict`;future values 不写进 OpenAPI,M5 实现新类型时再 bump。
4. `ReviewerOverallVerdictPayload` 强类型,写入和读取都走 OpenAPI DTO,不让 `payload` 漂移成任意 object。
5. Verdict 是实时派生 view,M4 不维护 `current_verdicts` 物化表,保持 `quality_ledger_entries` 是唯一事实源。
6. Verdict 排序使用 `ORDER BY created_at DESC, id DESC`,在毫秒并发时用 id tie-break 保证稳定性;反射 contract test 编译时守门 SQL 字符串。
7. Self-review 在 M4 Service 层实施硬 409,不延期到 M5;reviewer 审核自己的 submission 会污染 Quality Ledger 信任边界。
8. Service 接 `Set<String> requesterRoles` 参数,业务层不读 SecurityContext;Controller 从 `JwtPrincipal.roles()` 取原始 role code,不使用带 `ROLE_` 前缀的 authorities。
9. Reviewer queue latest-verdict filtering 在 mapper SQL 用 window function 实现,避免 Service 层 N+1/内存过滤破坏分页语义。
10. 不抽 `SubmissionDetailShell` page-level shell;Owner 和 Reviewer 详情页复用底层 primitives(`SchemaRenderer` / `AiProvenanceCard`),但保留各自业务组合。

### M4 阶段真实暴露并修复的工程问题

1. M3-P3.5 修了 render-schema 的 Owner/Labeler ownership gap;M4-P2 又补了 `SchemaService.renderForSubmission` roles-aware overload,让 Reviewer 能跨 task 读取历史 schema。
2. M3-P3 `AiReviewController.getProvenance` 没传 roles,M4-P4 live smoke 暴露 Reviewer 详情页看不到 AI provenance;P4 增加 `currentUserRoles()` 和 `AiReviewService` roles overload。
3. M4-P3/M4-P4 暴露 generated controller `@Min/@Max` annotation parity 漏洞;这是 M2-P7b、M3-P3 之后第三次同类坑,已用 focused controller tests 守门并在 humanpending 跟踪自动化检测。
4. M4-P4 forbidden page 文案是 Owner-specific,Reviewer/Owner 跨路径时不准确;已改为 role-neutral 文案,延续 M3-P5 `AiProvenanceCard` empty-state 角色中性化经验。
5. M1 期间留下的 Reviewer placeholder route 在 M4-P4 清理,`roleRoutes` 现在指向真实 `/reviewer/submissions` 入口。

### 工作流演化里程碑

- M4-P0 用户主动 push back S1 选项设计:不能说 Quality Ledger 不实现,修正为最小 ledger fact +实时 Verdict 派生。
- M4-P1 agent 主动 push back N+1 留 P2 的 prompt 建议,把 reviewer queue latest-verdict filtering 提升到 SQL window function 层;这是分页正确性问题,不是提前性能优化。
- M4-P2 主动修补 M3-P3.5 隐性 gap,给 `SchemaService.renderForSubmission` 加 Reviewer roles 通道,未延期到 P3/P4。
- M4-P3 写 `getSubmissionRenderSchema_passes_raw_jwt_roles_to_service` 反 wiring 漂移单测,防止未来误从 authorities 取 `ROLE_*`。
- M4-P4 live smoke 暴露两个 M3/M4 隐性 gap(annotation parity + AI provenance roles),立刻修补并透明记录;R5 verification by execution 跨 M2-P7b / M3-P4 / M4-P4 持续生效。

### 亮点 2 Evidence 包(M4 完整 + M5 扩展候选)

完整 evidence 链:

- SQL contract:`latest_reviewer_verdict_query_tie_breaks_by_id_desc` 反射测试守门 latest verdict tie-break。
- Service:`new_ledger_entry_changes_verdict` 证明 append ledger fact 后实时派生 Verdict 改变。
- HTTP integration:`new_ledger_entry_changes_verdict_via_http` 证明真实 HTTP/DB 路径 append -> re-derive。
- Security:Reviewer route/ledger write RBAC、authenticated read + service ownership/role checks、自审 409。
- UI smoke:`phase-m4p4-reviewer-approve.png` / `phase-m4p4-reviewer-reject.png` 证明浏览器层 Verdict 即时变化。

M5 持续扩展候选:

- `current_verdicts` 物化缓存,需要先设计一致性策略。
- 字段级 ledger entry(`reviewer_field_decision`)。
- AI findings 入 ledger(`ai_field_finding`)。
- Reviewer assignment 机制。
- Self-review policy 多角色细化。
- Labeler 历史质量快照。

## 2026-05-25 M5 Phase 1 Trusted Export Contract And Storage Foundation

- V8 drops the original `uk_export_snapshots_file_hash` unique key because M5 reproducibility requires two independent exports of the same task/source state to insert separate snapshot rows with the same hash. The column keeps a normal `idx_export_snapshots_file_hash` lookup index instead.
- `export_snapshots` now stores `manifest_hash`, `source_state_hash`, `object_key`, `file_manifest`, and `record_counts`; `file_hash` remains as the V1 aggregate hash for compatibility, while the new hashes become the M5 reproducibility evidence.
- OpenAPI moved to `0.8.0` and reshaped the old async export draft into a synchronous task export contract: `POST/GET /tasks/{taskId}/exports`, `GET /exports/snapshots/{snapshotId}`, and a simplified hash-level diff endpoint using `compareWith`.
- `manifestHash` is defined over the stable content section only. Runtime metadata such as `snapshotId`, `exportJobId`, `generatedAt`, and MinIO object paths are intentionally outside the hash boundary so independent exports can prove identical content with different runtime rows/paths.
- The Trusted Export artifact shape is explicitly file-based: `manifest.json`, `task.json`, `schema-versions.jsonl`, `dataset-items.jsonl`, `submissions.jsonl`, `answers.jsonl`, `ai-calls.jsonl`, `ai-call-in-fields.jsonl`, `ledger-entries.jsonl`, and `verdicts.jsonl`. Dataset item facts are included so exported training data has input context, not just labels.
- The API uses the AWS S3 SDK against MinIO's S3-compatible surface rather than the MinIO Java SDK. This keeps object storage provider-agnostic in the same spirit as M3's OpenAI-compatible provider abstraction.
- `ObjectStorageConfig` fail-fast validates endpoint, access key, secret key, and bucket at bean creation. Tests supply dummy credentials via `src/test/resources/application.yml`, while committed runtime config keeps secrets empty and env-driven.
- MinIO docker-compose defaults now initialize `labelhub-exports` and bind the API on `:9000`. This keeps local object-storage infrastructure aligned with the new committed `OBJECT_STORAGE_BUCKET` default instead of the earlier generic `labelhub-files` bucket.

## 2026-05-25 M5 Phase 2 Trusted Export Service And Canonical Artifact Builder

- M5-P2 keeps four separate responsibilities instead of compressing them into one service: `ExportService` orchestrates the transaction, `ExportFactCollector` gathers stable ordered facts, `ExportArtifactBuilder` creates canonical bytes and hashes, and `ObjectStorageWriter` only performs object-storage PUTs.
- Export object keys use `exports/tasks/{taskId}/jobs/{exportJobId}/` rather than snapshot IDs. The job row exists before object writes, the key is unique per independent export, and the key stays outside `manifestHash` so runtime paths cannot break reproducibility.
- `manifestHash` remains the hash of the stable `content` section only. Runtime data such as `exportJobId`, `generatedAt`, and `objectKey` appears only in the uploaded full `manifest.json` runtime section and is excluded from reproducibility hashes.
- The artifact includes a small `source-state.json` content file in addition to the task, schema, dataset-item, submission, answer, AI, ledger, and verdict files. This makes the source-state boundary inspectable and keeps the P2 evidence at ten content files plus `manifest.json`.
- Unit tests use the real `ObjectStorageWriter` with a mocked `S3Client`. P2 proves reproducibility and verifies every S3 PUT invocation without Docker/network state; P3 will own the wire-level MinIO integration test.
- `ExportService.createSnapshot` is transactional for database rows: failed exports roll back `export_jobs` and `export_snapshots`. Object-storage residue cannot participate in the SQL transaction, so failed-upload cleanup remains an explicit later operations task.
- Batch collection mapper methods use `select*` names and stable `ORDER BY ... ASC` clauses so existing append-only reflection contracts keep guarding the expanded fact collection surface.

## 2026-05-25 M5 Phase 3a Trusted Export HTTP And Diff Layer

- `ExportSnapshotEntity.fileManifest` remains the P1/P2 physical shape: a `Map<String,Object>` containing a `"files"` list. P3a DTO and diff mapping read that real shape instead of pretending the JSON column is a bare array.
- `ExportService` now owns list/get/diff read methods as well as creation. Each method rechecks task ownership and returns 404 for cross-owner snapshot access, keeping Owner-only export reads behind both SecurityConfig and service-level防枚举 guards.
- The diff endpoint is intentionally hash-level in M5: it compares `fileHash`, `manifestHash`, `sourceStateHash`, and per-file SHA-256 entries from `fileManifest`. Fact-level attribution remains a later enhancement after the reproducibility evidence path is stable.
- `ExportController` implements the generated `ExportsApi` and mirrors generated validation annotations (`@Min`, `@Max`, `@NotNull`) with a focused reflection test. This continues the M4 fix for generated-controller annotation parity instead of relying on live smoke to catch it.
- SecurityConfig gates `POST/GET /tasks/*/exports` and `GET /exports/snapshots/**` with `OWNER`; the service then validates the current owner actually owns the task behind each snapshot.
- Testcontainers coverage uses MySQL plus a real MinIO-compatible `GenericContainer`. The integration class is disabled without Docker but compiles and contains HTTP evidence for two independent exports producing identical hashes, MinIO object counts, diff equality, diff inequality after a new ledger fact, RBAC 403s, and cross-owner 404.
- `ExportDtoMapper` is the explicit boundary from physical export rows to public DTOs: database hash fields and `fileManifest.files` become the OpenAPI `ExportSnapshot`, and `ExportSnapshotDiffView` becomes the public hash/file-level diff response.

## 2026-05-25 M5 Phase 3b Trusted Export Owner UI

- Trusted Export selection uses two checkboxes plus a compare button. Selecting a third snapshot replaces the earliest selected snapshot, keeping the interaction fast during smoke demos without forcing the user to manually clear an old selection.
- The snapshot list stays intentionally scan-focused: `#id`, shortened `manifestHash`, submission count, generated time, and selection state. Full reproducibility evidence belongs in the diff modal, not in the table.
- The diff modal is the browser-layer evidence surface for highlight 3: it shows a success/warning banner, three hash-match rows (`fileHash`, `manifestHash`, `sourceStateHash`), and the ten content-file SHA-256 matches returned by the backend.
- The UI does not expose `objectKey`; MinIO paths are runtime storage details, while the Owner-facing contract is the immutable snapshot id and content hashes.
- Export creation mirrors the M4 ledger mutation pattern: no optimistic snapshot is inserted locally; the mutation invalidates `['tasks', taskId, 'exports']` so the list reflects the server-created snapshot row.
- Export pagination uses `exportPage` / `exportSize` query parameters so it can coexist with the existing task-detail submission pagination without parameter collisions.

## 2026-05-25 M5 Phase 4 AI Findings Into Quality Ledger

- OpenAPI moved to `0.9.0` because the public ledger read contract now has a second entry type, `ai_field_finding`, and a typed `AiFieldFindingPayload`. `CreateLedgerEntryRequest` intentionally stays reviewer-only so clients cannot forge AI evidence through the reviewer write endpoint.
- `QualityLedgerEntry.actorUserId` is no longer required because `actorType=ai` ledger rows have no human actor. The relationship to AI provenance lives in the existing top-level `aiCallId` field instead.
- AI findings use a dedicated `LedgerService.appendAiFieldFindings` method rather than reusing reviewer `createEntry`. This keeps reviewer-only guards such as self-review and reviewer payload validation out of the AI automatic write path.
- `AiReviewService` appends AI findings to the ledger only on a new provider call. Idempotency hits reuse existing AI provenance and must not create duplicate ledger facts.
- AI call rows, AI field rows, and AI ledger entries share the same transaction boundary: if ledger append fails, the new AI review fact set rolls back together.
- The `ai_field_finding` payload deliberately does not repeat `aiCallId`; duplicating the relationship in both the row column and JSON payload would create drift risk.
- `QualityDtoMapper` now narrows ledger payloads by `entryType`, mapping reviewer verdict rows to `ReviewerOverallVerdictPayload` and AI finding rows to `AiFieldFindingPayload`.

## 2026-05-25 M5 Phase 5 AI Findings Ledger HTTP Evidence And Minimal UI

- P5 keeps the evidence priority on integration tests: new AI review writes AI ledger facts, repeat AI review/idempotency hit does not duplicate them, Owner/Reviewer/own Labeler can read them, and cross-Labeler receives 404.
- The six P5 cases live in `QualityLedgerIntegrationTest` so the cross-highlight evidence stays on the Quality Ledger path rather than being scattered across AI-only tests.
- Reviewer UI changes stay deliberately minimal: AI ledger rows receive an `AI` tag, severity color, `AI Call #id`, field path, finding text, and confidence. Aggregation, filtering, and accept/reject workflows remain later product work.
- `ledger-entry-item--ai` gives AI evidence a light blue visual treatment so reviewer and AI ledger entries are distinguishable at a glance without adding a new card or page shell.
- Owner and Labeler submission pages do not gain ledger cards in P5; the current browser evidence surface remains the Reviewer detail page where mixed reviewer/AI ledger rows naturally appear.
- The P5 typecheck contract pins the OpenAPI `actorUserId: null` shape for `ai_field_finding` rows, preserving the P4 contract correction in the frontend generated types.

## 2026-05-25 M5 Phase 6 Real Provider Smoke And Evidence Closure

- M5-P6 uses DeepSeek through the existing OpenAI-compatible provider abstraction rather than adding a DeepSeek-specific adapter. The provider switch is therefore an environment/configuration proof for the M3 abstraction, not new business logic.
- API keys remain outside the repository and logs. The evidence set records provider name, model, latency, estimated cost, hashes, and DB facts, but never stores the secret value.
- The real provider smoke closes the old M3-P6 resource gap: submission `#5` produced a real `deepseek` / `deepseek-v4-flash` AI call, one persisted field finding, and an idempotency-hit browser path on repeat.
- The DB evidence intentionally shows four layers together: `ai_calls` for provider provenance, `ai_calls_in_field` for field-level findings, `quality_ledger_entries` for AI/reviewer facts in the shared ledger, and zero `current_verdicts` rows because M4/M5 still derive Verdict from ledger facts rather than maintaining a materialized table.
- Live smoke exposed an export metadata gap: V1 `export_jobs.download_count` is NOT NULL and MyBatis inserts explicit column values, so `ExportService` must initialize it to `0`. This small P6 patch unblocked browser export without changing the reproducibility contract.
- Trusted Export screenshots close the P3b D-scope gap: the browser now shows empty state, first snapshot, two selected snapshots, and an equal diff modal with three hash rows plus ten file-level SHA-256 matches.
- Mixed ledger screenshots close the P5 D-scope gap: Reviewer detail now visually shows an AI `ai_field_finding` row and then AI + reviewer `reviewer_overall_verdict` rows coexisting after approval.
- Two polish follow-ups remain deliberately outside P6: the AI Drawer success copy should stop saying "Mock provider" when a real provider is active, and final-defense export smoke should use a task with at least one still-`submitted` submission so the snapshot record count is nonzero.

## 2026-05-25 M5 Quarter Closing Summary

M5 完成 9 个月项目最后两条工程证据建立:Trusted Export 可复现性和 AI findings 入 Quality Ledger 的跨亮点整合;同时用真实 DeepSeek smoke 补齐 M3 真实 provider evidence。

### M5 完成 Phase 列表

- P0 / P1 / P2 / P3a / P3b / P4 / P5 / P6 / P7.

### M5 核心决策

1. **V1 物理 schema 修正**:V8 移除 `export_snapshots.file_hash` UNIQUE KEY,因为 reproducibility 要求两次独立 export 可以插入相同 hash 的不同 snapshot row。
2. **S3=C' 可复现性定义**:M5 不做 regenerate endpoint;真正 evidence 是两次独立同参 `POST /exports` 产出相同 canonical hashes。
3. **manifestHash 排除 runtime metadata**:`snapshotId`、`exportJobId`、`generatedAt`、object path 不进入 content hash,同 source state 才能跨 runtime 复现。
4. **objectKey 使用 jobId**:`exports/tasks/{taskId}/jobs/{exportJobId}/` 解决 snapshotId 生成顺序问题,同时让独立 export 不覆盖对象。
5. **AWS S3 SDK 而非 MinIO SDK**:object storage 走 S3-compatible 抽象,延续 M3 OpenAI-compatible provider 的不绑厂商原则。
6. **Diff endpoint 保持 hash-level**:M5 只比较三层 hash 与 file-level SHA-256;fact-level attribution 等 evidence 路径稳定后再做。
7. **AI findings 走专门 `appendAiFieldFindings`**:不复用 reviewer `createEntry`,避免 self-review guard 和 reviewer payload 校验污染 AI 自动写路径。
8. **idempotency hit 不追加 ledger**:复用历史 AI evidence 不产生新 fact,否则会污染 Quality Ledger 信任边界。
9. **`CreateLedgerEntryRequest` reviewer-only**:客户端不能通过 reviewer write endpoint 伪造 AI evidence;AI evidence 只能由真实 AI review path 写入。
10. **payload 不重复 `aiCallId`**:关系保存在 ledger row 顶层 `ai_call_id`,JSON payload 不双写,避免 drift。

### M5 阶段真实暴露并修复的工程问题

1. V1 `uk_export_snapshots_file_hash` 与 M5 reproducibility 语义冲突;P1 用 V8 drop unique key 并改普通索引。
2. MinIO infra 只部分 ready;P1 补 S3Client config、fail-fast properties、默认 bucket 与 test dummy credentials。
3. 早期 OpenAPI async export 草案与 M5 同步 evidence path 冲突;P1 bump 0.8.0 并重塑 contract。
4. `QualityLedgerEntry.actorUserId` 在 M4 只有 reviewer row 时 required,但 M5 `actorType=ai` 无 human actor;P4 改 nullable。
5. M5-P6 live smoke 暴露 `export_jobs.download_count` NOT NULL gap;P6 初始化 `downloadCount=0` 并编译通过。

### 工作流演化里程碑

- M5-P0 的 12 个元问题先摸清物理事实,把 unique key、MinIO 接入、OpenAPI 草案三个"看起来已有但实际不可用"的问题提前暴露。
- 用户 push back S3=C' 把 reproducibility 从 self-referential regenerate 修正为 forward reproducibility,直接决定 M5 的工程焦点。
- M5-P3a 主动加 `ExportControllerValidationContractTest`,把 M2/M3/M4 多次 live-smoke annotation parity 坑从修补模式提升为预防模式。
- M5-P4 用极少代码完成跨亮点整合,证明 M4 ledger 表的 future entry type 预留在 M5 可以 0 migration 使用。
- M5-P6 把三个 D 口径一次性补完:真实 provider smoke、Trusted Export browser screenshots、mixed AI/reviewer ledger screenshots;同时透明处理 API key 安全提醒和 live bug 修补。

### M5 Evidence 包

Trusted Export evidence:

- Contract:OpenAPI 0.8.0 export endpoints and DTOs.
- Schema:V8 export snapshot reproducibility columns and non-unique hash index.
- Service:`two_independent_exports_for_same_task_produce_identical_hash`,runtime exclusion,source-state change contrast.
- HTTP:`two_independent_http_exports_produce_identical_hash`,diff equal/not-equal,Owner-only RBAC.
- UI:`phase-m5p3b-diff-modal-equal.png`.

AI ledger integration evidence:

- Contract:OpenAPI 0.9.0 `ai_field_finding`,nullable `actorUserId`,payload oneOf.
- Service:`appendAiFieldFindings` and idempotency-hit never append.
- HTTP:`repeat_ai_review_does_not_duplicate_ledger_entries`.
- DB:`phase-m5p6-db-ai-ledger-evidence.png`.
- UI:`phase-m5p5-reviewer-ledger-mixed-after-approve.png`.

Real provider evidence:

- Browser first call:`phase-m5p6-deepseek-first-call.png`.
- Browser idempotency hit:`phase-m5p6-deepseek-idempotency-hit.png`.
- DB provenance/ledger chain:`phase-m5p6-db-ai-ledger-evidence.png`.

### M5 工程量

- Phase 数:9(P0/P1/P2/P3a/P3b/P4/P5/P6/P7)。
- 测试数:308+61 -> 339+75。
- 截图归档:9 张 M5 screenshots。
- 关键 live-smoke 修补:1 个(`downloadCount=0`)。
- D 口径事件:3 个,均在 M5-P6 收口。

### 4 个亮点最终状态

| 亮点 | 状态 | 最终 evidence |
|------|------|---------------|
| Schema 版本化 + 不可变事实 | ✅ 完整(M2) | Historical Renderer 按 submission-bound schema version 渲染 |
| Quality Ledger + Verdict 派生 | ✅ 完整(M4 + M5 扩展) | Reviewer ledger + AI ledger entries + derived verdict |
| Trusted Export 可复现性 | ✅ 完整(M5) | Diff Modal equal + 三层 hash + 10 file matches |
| AI Provenance + 训练污染防控 | ✅ 完整(M3 + M5-P6 real provider) | DeepSeek first call + idempotency hit + DB fact chain |

M5 收尾后,LabelHub 4 个亮点均具备 Contract / Service / HTTP or DB / UI evidence,无未闭合 D 口径。
## 2026-05-25 M6 Phase 0.5 Submission Lifecycle Semantics Final Decision

> M6-P0 audit exposed Bug #001: M3/M4/M5 encoded different assumptions about `submissions.status`.
> M6-P0.5 does not implement code. It locks the system-level lifecycle semantics that M6-P1 must implement.

### Philosophy

Submission is an immutable answer fact. AI review and reviewer verdict are append-only facts around that answer, recorded through `ai_calls`, `ai_call_in_fields`, and Quality Ledger entries. Verdict is derived from ledger facts. M6-P1 explicitly corrects M3's `under_ai_review` naming mistake, normalizes historical rows through V9, aligns task deadline contract behavior, and keeps the lifecycle model minimal instead of preserving legacy noise.

### Physical Evidence

- V1 physical schema defines `submissions.status VARCHAR(48) NOT NULL DEFAULT 'under_ai_review'`.
- `SessionService.submit` writes `submission.status = "under_ai_review"` while writing `session.status = "submitted"`.
- `ReviewerQueueService`, `ReviewerController`, frontend reviewer queue query keys/copy, and `QualityLedgerEntryMapper` default to exact `status = submitted`.
- `ExportFactCollector` delegates to `SubmissionMapper.selectSubmittedByTaskOrderedById`, whose SQL filters exact `status = 'submitted'`.
- `Submission.status`, `OwnerSubmissionSummary.status`, and `ReviewerSubmissionSummary.status` are public free strings, not a `SubmissionStatus` enum.
- Tests currently encode phase-local assumptions: Session tests expect `under_ai_review`, while Quality Ledger and Export fixtures seed `submitted`.

### Final User裁决

| Q | 裁决 | Final rationale |
|---|------|-----------------|
| Q1 submit write status | A: write `submitted` | A normal submit creates the immutable answer fact. `under_ai_review` encoded an AI side process into the answer lifecycle. |
| Q2 `under_ai_review` lifecycle | A: deprecate and V9-normalize historical rows to `submitted` | LabelHub has no production data, the baseline tag gives rollback, and long-term coexistence would preserve the same semantic entropy that caused Bug #001. |
| Q3 reviewer default queue | A: default queue only uses `submitted` | Reviewer queue should read completed answer facts. Transitional multi-status query would hide the state-machine error instead of fixing it. |
| Q4 Trusted Export scope | A: export submitted answer facts | Trusted Export exports canonical source facts; trust is represented by ledger/verdict files, not by mutating `submission.status`. |
| Q5 approved/rejected exportability | A: approved/rejected remain exportable via ledger context | `approved` and `rejected` are verdict facts, not submission lifecycle states. Export can include answer facts plus verdict context. |
| Q6 AI review transition | A: AI review appends facts only | AI review must write AI provenance and `ai_field_finding` ledger entries without changing submission lifecycle status. |
| Q7 legal transition table | A: minimal submitted-only answer lifecycle for M6-P1 | The M6 repair should restore the simplest state machine: submit finalizes the answer; review/evidence flows append facts around it. |
| Q8 `deadlineAt` contract | A: required at create | The frontend already requires `deadlineAt`; backend null handling is a bug. Create-required is the smallest correction and avoids inventing draft-task UX in M6-P1. |
| Q9 repeat claim semantics | A: one session per dataset item per labeler | A labeler may claim multiple dataset items for the same task; repeat claim semantics are item-scoped, not task-scoped. |
| Q10 regression threshold | B: full regression set because functional budget is under 500 lines | The estimated 100-220 functional lines leaves room for the full P0/P1 regression set; the tests should encode the real submit path instead of phase-local fixtures. |

### Strict-Constraint Exceptions For M6-P1

M6 remains strict by default, but Bug #001 and Bug #002 require logged bug-fix exceptions. Each exception needs a regression test and an independent git revert path.

| Exception | Location | Change type | R10 path |
|-----------|----------|-------------|----------|
| Bug #001 | `SessionService.submit` | existing behavior change: write `submitted` for normal submission | dedicated commit + submit/status regression tests |
| Bug #001 | V9 migration | additive migration correcting the physical default and normalizing historical `under_ai_review` rows | dedicated migration commit; rollback through `m5-p7-baseline` tag/dev DB reset |
| Bug #001 | queue/export readers | keep canonical `submitted` scope and add real-submit regressions to prove the scope is now populated | dedicated tests for default queue and nonzero export |
| Bug #002 | task create deadline handling | contract/validation alignment: `deadlineAt` required at create with controlled 400 instead of 500 | dedicated controller/frontend regression |

### M6-P1 Budget

M6-P1 is a bug-fix sprint centered on submission lifecycle semantics and task-create deadline validation. The finalized裁决 keeps the estimated functional code under 500 lines:

- Backend functional code: about 75-155 lines.
- Frontend functional code: about 25-65 lines.
- Total functional code: about 100-220 lines.
- Tests: about 250-450 lines, intentionally larger than the fix.

Cost/performance remains gated until the normal submit -> reviewer queue -> Trusted Export path is semantically correct.

## 2026-05-25 M6 Phase 1 Implementation Verification

M6-P1 implements the M6-P0.5 lifecycle裁决 through a small bug-fix exception set. The implementation keeps the answer lifecycle minimal: normal submit writes `submitted`; AI review and reviewer verdict remain append-only facts around that answer.

### Bug #001 Resolution

- V9 migration `V202611281000__submission_lifecycle_alignment.sql` changes the `submissions.status` physical default to `submitted` and normalizes historical `under_ai_review` rows to `submitted`.
- `SessionService.submit` now writes `SubmissionStatusCodes.SUBMITTED` for the submission row while leaving the session row's submitted status independent.
- Reviewer queue and Trusted Export readers keep their canonical `submitted` scope. They do not add legacy multi-status readers; real-submit regression tests prove the scope is now populated.
- AI review does not mutate `submission.status`. `AiReviewServiceTest.ai_review_does_not_mutate_submission_status` guards that AI provenance and Quality Ledger writes remain side facts.

### Bug #002 Resolution

- OpenAPI is bumped to `0.9.1` as a patch-level contract correction.
- `CreateTaskRequest.required` now includes `deadlineAt`, and generated Java/TypeScript types both treat `deadlineAt` as required.
- `TaskControllerValidationContractTest.create_task_request_keeps_deadline_required_contract` guards the generated Java validation shape so missing `deadlineAt` returns controlled validation errors before controller logic can dereference null.
- `m6p1-submission-lifecycle.contract.tsx` guards the frontend generated type shape.

### Regression Tests Added

- `SessionServiceTest.submit_creates_submission_with_submitted_status_and_inherits_session_schema_version_id` — normal submit creates a `submitted` answer fact.
- `SessionServiceTest.submit_writes_session_status_submitted_independently_from_submission_status` — session and submission status are independent concepts.
- `AiReviewServiceTest.ai_review_does_not_mutate_submission_status` — AI review appends facts without lifecycle mutation.
- `SubmissionLifecycleMigrationContractTest.submission_status_default_is_submitted_and_legacy_under_ai_review_is_normalized` — V9 keeps the physical default and normalization rule.
- `SubmissionLifecycleIntegrationTest.labeler_submit_appears_in_default_reviewer_queue` — real labeler submit path feeds the default reviewer queue.
- `SubmissionLifecycleIntegrationTest.trusted_export_includes_submission_created_by_real_submit_path` — real labeler submit path feeds Trusted Export records.
- `M1ApiIntegrationTest.create_task_requires_deadline_with_400` — missing `deadlineAt` returns 400 rather than a null dereference.
- `TaskControllerValidationContractTest.create_task_request_keeps_deadline_required_contract` and `m6p1-submission-lifecycle.contract.tsx` — generated contract drift guards for backend and frontend.

### M5 Evidence Re-Verification Impact

- The M5 Trusted Export hash/diff evidence remains valid. M6-P1 fixes the skipped state-space gap so future smoke can produce nonzero submitted records through the real labeler submit path.
- M4 Quality Ledger and M5 AI ledger integration remain append-only fact flows; no reviewer queue or export reader widening was needed.
- M6-P2 should re-smoke the Owner task setup UX and may refresh the Trusted Export browser evidence with nonzero records.

## 2026-05-25 M6 Phase 2 Implementation Verification

M6-P2 closes the setup UX items found during the M6-P0 audit without changing backend contracts, migrations, or existing service behavior. The phase stays intentionally UI-only.

### M6-P0 Audit Polish Resolution

- **Polish #001 login autofill validation drift**: the login submit path now reads the actual browser form values through `FormData` before validating/mutating, so browser autofill and React state cannot disagree at submit time.
- **Polish #002 owner task created-time `-`**: the owner task list now renders the missing created-time fallback as `未记录`. The root cause is contract shape, not parsing: the public `Task` schema does not expose `createdAt`, so M6-P2 keeps this as copy polish instead of changing OpenAPI.
- **Bug #003 schema creation discoverability**: draft task detail now shows a scoped `TaskNextStepGuidance` card with exactly three setup CTAs: Schema, Dataset, and Publish.
- **Product Boundary #001 repeat claim semantics**: the Labeler marketplace copy now states the Q9=A semantics directly: each claim assigns one available dataset item, and the same task can be claimed again for different items.

### Anti-Scope-Creep Compliance

- No backend, OpenAPI, migration, or service files changed.
- No new API endpoints, routes, onboarding flow, tutorial, tooltip wizard, or dashboard/sidebar UX was introduced.
- The next-step guidance is visible only on the Owner task detail setup surface and only for draft tasks.
- The CTA cap stayed at three: Schema management, Dataset section, and Publish transition.
- Product Boundary #001 remains copy-only; claim API behavior is unchanged.
- Segment 5 functional output stayed within the approved budget: `TaskNextStepGuidance.tsx` is 96 lines, CSS is 72 lines, and the page integrations are small glue changes.

### Evidence Impact

- M6-P2 does not alter the M5 four-highlight evidence chains.
- M6-P2 does not alter M6-P1 submission lifecycle semantics. The setup guidance derives state from existing `Task` fields and links to existing surfaces.

## 2026-05-25 M6 Phase 3a AI Token Usage Persistence

M6-P3a captures provider-reported token usage into `ai_calls` as the first data layer for the M6+ cost/performance baseline. Cost computation from token usage is intentionally deferred to M6-P3a-2 until the pricing source and billing currency semantics are locked.

### V10 Schema

- V10 migration `V202611291000__ai_calls_token_usage.sql` adds four nullable INT columns on `ai_calls`: `prompt_tokens`, `completion_tokens`, `total_tokens`, and `cache_hit_tokens`.
- The columns have no DEFAULT. `NULL` means the call predates token tracking or the provider omitted usage; `0` means the provider reported a real zero-token value.
- This distinction is required for M6-P3a-2: `NULL` keeps the fixed estimate fallback available, while `0` remains a real usage value.

### Provider Compatibility

- `OpenAiCompatibleProvider` now parses `usage.prompt_tokens`, `usage.completion_tokens`, and `usage.total_tokens`.
- Cache-hit tokens use a defensive parser: it prefers DeepSeek's `prompt_cache_hit_tokens` and falls back to the OpenAI-compatible `cached_tokens` shape used by some providers.
- Evidence source checked on 2026-05-25: official DeepSeek API docs for chat completion usage and context caching token usage.

### Behavior Change Justification

`AiReviewService.review` now calls `aiProvider.invokeWithUsage(...)` internally. This is a data-collection extension, not a contract break:

- The public review endpoint and service return shape remain compatible; OpenAPI 0.10.0 only adds optional `AiReviewResult.usage`.
- `AiProvider.invoke(...)` and `AiCallResult` signatures are unchanged.
- `AiProvider.invokeWithUsage(...)` is a default method returning usage `null`, so existing providers keep working unless they opt into usage parsing.
- AI provenance, Quality Ledger, and submission lifecycle paths are unchanged.
- `cost_decimal` write behavior is unchanged and remains the M3 fixed-estimate path.

R10 path: revert the token persistence commit to restore `AiReviewService` to `invoke(...)`; V10 columns are additive and nullable, so leaving them unused is safe.

### Pricing Computation Deferral

M6-P3a does not introduce an `AiCallCostCalculator` or pricing configuration because pricing is not yet a stable input:

- DeepSeek English docs expose v4-flash style USD pricing, while China-region billing/currency needs confirmation for the user's setup.
- Existing Chinese pricing references focus on other model names or have timing inconsistencies.
- Persisting raw token usage has standalone engineering value and avoids calculating money from uncertain rates.

M6-P3a-2 will add usage-based cost calculation after pricing source, currency, and rounding semantics are confirmed. Until then, `ai_calls.cost_decimal` continues to record the configured fixed estimate.

### What M6-P3a Does Not Do

- No cost computation from usage.
- No pricing config in `application.yml`.
- No metrics endpoint.
- No idempotency hit ratio counter.
- No retry/backoff.

### Verification

- Backend tests: 354 run, 0 failures, 78 skipped in the Docker-disabled local environment.
- Frontend typecheck and production build passed.
- New regression coverage guards provider usage parsing, missing/partial usage payloads, cache-hit field compatibility, V10 nullable/no-default columns, token persistence, null-token persistence, and fixed `cost_decimal` behavior.
- M6-P1 Q6 invariant remains green: AI review does not mutate `submission.status`.

## 2026-05-25 M6 Phase 3a-2 AI Cost Computation From Token Usage

M6-P3a-2 closes the cost layer left open by M6-P3a. It computes USD cost from token usage when token data is complete enough, and falls back to the M3 fixed estimate when it is not. Strict-constraint a' is intentionally relaxed for one logged exception: `AiReviewService.review` changes the `cost_decimal` source from `AiCallResult.cost()` to `AiCallCostCalculator.computeCost(...)`.

### Pricing Evidence Source

- DeepSeek English pricing doc verified 2026-05-25: `https://api-docs.deepseek.com/quick_start/pricing`.
- Currency: USD. CNY v4-flash pricing is not stably documented, so the official English pricing page is the system of record for M6-P3a-2.
- `deepseek-v4-flash`:
  - Input cache hit: `$0.0028` / 1M tokens.
  - Input cache miss: `$0.14` / 1M tokens.
  - Output: `$0.28` / 1M tokens.
- `deepseek-v4-pro`:
  - Input cache hit: `$0.003625` / 1M tokens with DeepSeek's 75% off note.
  - Input cache miss: `$0.435` / 1M tokens with DeepSeek's 75% off note.
  - Output: `$0.87` / 1M tokens with DeepSeek's 75% off note.
  - DeepSeek states the v4-pro price will be adjusted after the 75% promotion ends on `2026-05-31 15:59 UTC`; config refresh is required if the project uses v4-pro after that date.

### A2 Fallback Decision

Cost is computed only when both `promptTokens` and `completionTokens` are present.

- `promptTokens == null` -> fixed estimate fallback.
- `completionTokens == null` -> fixed estimate fallback.
- `usage == null` -> fixed estimate fallback.
- pricing config missing for `modelName` -> fixed estimate fallback.
- `cacheHitTokens == null` -> compute with zero cache-hit tokens.
- `totalTokens` remains display/audit data and is not required for compute.

This avoids partial-data cost computation that would systematically understate cost, such as computing input cost while missing output tokens.

### R2 Rounding

`AiCallCostCalculator` uses `BigDecimal` internally and divides per-million rates at 10 decimal places with `HALF_UP`. The DB write value is rounded with `setScale(6, HALF_UP)` to preserve the existing `DECIMAL(12,6)` column.

Known precision limitation: small cache-hit amounts such as `30 * $0.0028 / 1_000_000 = $0.000000084` round to `0.000000` at DB precision. This is accepted to avoid V11 and keep strict-constraint a'. R3 (`DECIMAL(18,10)`) was explicitly rejected for M6-P3a-2.

### Behavior Change Justification

`AiReviewService.review` now writes `ai_calls.cost_decimal` from `AiCallCostCalculator.computeCost(aiProvider.modelName(), usage)` instead of `AiCallResult.cost()`.

- Backward compatible fallback: mock provider and providers without usage still produce the configured fixed estimate.
- `AiCallResult.cost()` remains in place as M3 evidence and R10 revert support.
- Token persistence from M6-P3a is unchanged.
- AI provenance, Quality Ledger, and submission lifecycle behavior are unchanged.

R10 path: revert the dedicated `fix: switch ai_calls cost_decimal source from fixed estimate to calculator` commit to restore the M3 fixed-estimate write path. Pricing config and calculator files are additive and can remain harmless.

### Regression Coverage

- `AiCallCostCalculatorTest` covers no-cache computation, cache-hit split computation, prompt-missing fallback, completion-missing fallback, null-usage fallback, missing-pricing fallback, and R2 small-cache-hit rounding.
- `AiReviewServiceTest.review_uses_calculator_cost_when_usage_present` proves complete usage writes calculator cost rather than the M3 fixed estimate.
- `AiReviewServiceTest.review_falls_back_to_fixed_estimate_when_usage_incomplete` preserves the A2 fallback path.
- M6-P1 `ai_review_does_not_mutate_submission_status` remains green.

### What M6-P3a-2 Does Not Do

- No V11 migration.
- No CNY pricing.
- No real-time pricing fetch.
- No cost alerting, dashboards, idempotency metrics, or hit-ratio counters.
- No OpenAPI shape change.

### Interface Evolution Record (Backfilled in M6-P3b)

M6-P3a-2 also extended the `AiProvider` interface with a new abstract method `String modelName()`, mirroring the existing self-describing pattern of `String providerName()`.

- Both implementations (`MockAiProvider`, `OpenAiCompatibleProvider`) were updated synchronously, so no breaking change occurred inside LabelHub.
- This is additive interface evolution, not an existing-behavior change.
- It is recorded here as a transparency backfill because the M6-P3a-2 strict-constraint exception table only listed the `cost_decimal` source switch. Listing only the behavior change could mislead future review into missing the additive interface evolution.

Future strict-constraint exception tables should explicitly list both existing-behavior changes and additive interface or contract evolutions so reviewers can see what changed beyond config and new files.

This backfill is the result of审计师 push back during M6-P3a-2 review, accepted by裁决师 as an附加硬要求 for M6-P3b.
