# Human Pending

## M6 工程加固(P0/P1)

- [M6-P0 resolved] Baseline lock and full smoke audit completed on `m6-engineering-hardening`; `m5-p7-baseline` tag exists, and `docs/internal/m6p0-smoke-audit-report.md` records 53 audit checks.
- [M6-P0.5 resolved] Submission lifecycle semantics decision is complete: Q1-Q9=A, Q10=B; `submitted` is the immutable answer fact, `under_ai_review` will be V9-normalized, AI/reviewer facts stay append-only, and `deadlineAt` is required at task create.
- [M6-P1 resolved] Submission Lifecycle + Default Flow Repair implemented: V9 normalizes `under_ai_review` to `submitted`, normal submit writes `submitted`, real-submit reviewer/export regressions were added, AI review is guarded as a side fact, and create-task `deadlineAt` is contract-required with controlled validation.
- [M6-P2 resolved] Owner Setup UX Repair implemented: login autofill submit reads actual browser form values, owner task created-time fallback is explicit, draft task detail shows three setup CTAs, and repeat-claim semantics are stated in Labeler marketplace copy.
- [M6-P3a resolved] AI token usage persistence implemented: V10 adds nullable token columns, OpenAI-compatible provider usage is parsed defensively, `ai_calls` persists prompt/completion/total/cache-hit tokens when present, and `cost_decimal` remains the M3 fixed estimate.
- [M6-P3a-2 resolved] AI cost computation from usage implemented: USD pricing config from DeepSeek official English pricing, A2 fallback (prompt+completion required), R2 rounding (BigDecimal internal, `DECIMAL(12,6)` DB), and `AiReviewService.review` now writes calculator output when usage is complete.
- [M6-P3b resolved] Idempotency metrics baseline implemented: hit/miss/mismatch Micrometer counters with provider tags are exposed through `/actuator/prometheus`, and saved-cost derivation is now possible from hit count plus M6-P3a-2 real cost.
- [M6-P3c ready] Large-task export and Quality Ledger performance baseline remain ready after token persistence.
- [M6-P4.0 resolved] Robustness failure semantics final裁决 is complete: Q1=B, Q2=A, Q3=C, Q4=C, Q5=B, Q6=B, Q7=B, Q8=C, Q9=B, Q10=A, S1=B split into P4a/P4b.
- [M6-P4a resolved] AI Provider Failure Evidence + Retry Semantics implemented: failed `ai_calls` rows use attempt-specific idempotency keys, `AiCallStatusCodes` is explicit, OpenAI-compatible timeout/retry config is scoped without a provider registry, retryable failures use deterministic exponential backoff, miss remains one logical review, and `labelhub.ai.provider.retry` records retry attempts separately.
- [M6-P4b resolved] Trusted Export Inline Cleanup implemented: exact object-storage keys written by a failed sync export attempt are cleaned best-effort, cleanup failures do not mask the original `ExportFailureException`, and failed export jobs remain intentionally unpersisted.
- [Signal 1 B class complete] M6-P4a + M6-P4b close the 60-day M6+ robustness track: AI provider failures now have evidence/retry semantics, and Trusted Export failure residue is cleaned inline without false-symmetry failed-job persistence.
- [M6-P5 resolved] Final regression + operational verification complete: 4 highlight evidence chains green, Signal 1 A/B class closed, decision-log/humanpending consistent, backend `389+78` green after socket-bind D-口径 rerun, frontend typecheck/build green.
- [M6-P5.1 resolved] Runtime startup hotfix complete: `spring-boot:run` startup gap was reproduced, root-caused to `AiRetryPolicy` constructor selection, fixed with explicit constructor injection, and guarded by `ApplicationContextStartupTest`.
- [Runtime startup guardrail] `ApplicationContextStartupTest` is now the 12th cross-phase guardrail; it loads the real Spring Boot context with runtime-equivalent datasource, security, and object-storage properties and brings the backend suite to `390+78` green.
- [Runtime startup guardrail D-口径] `ApplicationContextStartupTest` requires local MySQL and MinIO/object storage services because it loads the real Spring Boot context; missing local infra can fail the guardrail before application-code wiring is reached.
- [Defense ready] M6-P5 final regression report is archived at `docs/internal/m6p5-final-regression-report.md` and serves as the defense readiness baseline.
- [M6-P6a resolved] UI Audit + Polish Plan is locked: 10 before screenshots archived, 5 unavailable states recorded as D-口径, P0/P1/out-of-scope issue lists finalized, and P6b remains frontend-only under the 900-line P0 / 1500-line total cap.
- [M6-P6b ready] UI Experience Polish implementation may begin from the locked P0 list: global primitives first, then schema, AI provenance, export, reviewer, and owner-setup defense surfaces.
- [M6-P6b1 resolved] Global UI primitives are implemented under the frontend-only P6b boundary: header subtitle contrast repaired, local typography scale added, `RoleBadge` integrated in the app shell, and `TruncatedHash` scaffolded without page imports; frontend diff is 182/300 changed lines.
- [M6-P6b2 ready] Page-level evidence polish may begin: schema version history, labeler submission AI evidence, reviewer queue/detail anchors, Trusted Export surfaces, AI drawer, and owner setup CTA lock state remain scoped by the P6a audit list.
- [M6-P6 audit D-口径] Reviewer detail issue `#22` was removed after re-audit; "提示 / 通过 / 拒绝" were legitimate ledger-card tags, not render residue.
- [M6-P6b2 resolved] Page-level P0 evidence polish closed: 18 P0 issues landed after #28 demotion, 5 `TruncatedHash` integration sites are visible, #32/#38 visual nits were fixed in `5c03dcf`, cumulative P6 frontend diff is 573/900 changed lines, and P1 was not entered.
- [M6-P6 resolved] UI Experience Polish complete: P6a locked the screenshot audit, P6b1 landed global primitives, P6b2 polished the six defense-path evidence surfaces, 10 full after screenshots are archived, and the M6-P5/P5.1 defense-ready baseline is extended with a targeted UI layer.
- [M6-P6c resolved] Optional P1 polish subset closed: #16/#23/#25/#28/#34 landed with 75/100 P6c frontend changed lines, cumulative M6-P6 frontend diff is 646 changed lines, success-green consistency is shared across reviewer approve / export same-hash / diff match surfaces, and #28's same-hash indicator is explicitly page-local; remaining P1 items #13/#21/#29/#30/#39/#42/#44 stay optional future work.
- [M6-P7 resolved] Owner can now hard-delete any task and all 17 task-scoped facts (sessions, submissions, ai_calls, ai_calls_in_field, quality_ledger_entries, current_verdicts, review_actions, export_snapshots, export_jobs with after-commit S3 cleanup, drafts, dataset_items, datasets, schema_versions, label_schemas, adjudication_rules, task_transitions, tasks) via DELETE /tasks/{taskId}. This explicitly supersedes the M6-P0.5 submission immutability decision for whole-task removal; Quality Ledger append-only semantics continue to hold inside any alive task. Auditor's draft-only recommendation was adjudicated against by the user. OpenAPI baseline anchor changes to dc4a91c6471b3cbbf0bc0ba62139087e from M6-P7 forward. New protected-endpoint-guard caveat: script does not verify the new DELETE endpoint; OWNER protection enforced by @PreAuthorize + global bearerAuth. See m6p7-verification.md for the full R8 records.
- [M7-P1 resolved] Audit log coverage expanded from 1 action (task.transition) to 11 implemented action types across 6 categories (schema lifecycle, submission lifecycle, AI review, review action, export, hard delete), plus 3 deferred constants (schema.archive, submission.supersede, export.snapshot_diff) reserved in AuditActions namespace. AuditLogService.record fail-fast default applies to 10 of 11 writes; ai_review.failed uses recordRequiresNew (@Transactional REQUIRES_NEW) so failure audit commits across business rollback. V11 adds idx_audit_logs_action_time. Owner /owner/audit-logs page provides query + CSV export with 50000-row cap returning 413 on overflow. OpenAPI baseline anchor changes to b6a8344f2c7cc38db958eb333334ebd1 from M7-P1 forward. Frontend multi-viewport manual sanity (1440/1280/1024) added to verification checklist per Cluster 5b lesson learned. See m7p1-verification.md for the five R8 records.
- [M6-P3c optional] Large-task performance baseline remains optional; M6-P5 did not uncover a scale-evidence gap that requires P3c before defense.
- [False symmetry deferred] Export failed-job persistence is intentionally not mirrored to failed AI call persistence; defer until async export/job化 creates a real API/UI consumer.
- [Metrics data accumulation watch] Idempotency hit ratio needs 100+ AI review attempts over a 7-day observation window before the metric is stable enough for claims; M6-P5 confirmed the endpoint/counters are ready, but long-window data is still pending.
- [Production posture watch] `/actuator/prometheus` and `/actuator/metrics` are exposed without auth for local development observability; productionization requires a separate actuator security review.
- [Pricing follow-up] If DeepSeek changes official USD pricing or a stable CNY v4-flash pricing source appears, refresh `application.yml` and the decision-log evidence date.
- [v4-pro discount watch] DeepSeek notes v4-pro pricing adjusts after the 75% discount promotion ends on `2026-05-31 15:59 UTC`; update config before relying on v4-pro cost values after that date.
- [M6-P5 screenshot D-口径] Fresh browser screenshots were not captured because browser automation tools were not exposed in this session; screenshot targets are indexed at `docs/screenshots/m6p5-smoke-set/INDEX.md`.

## M3 启动前必做(P0)

- [M3 启动前] Ensure M2-P4 frontend schema types derive from generated OpenAPI types instead of hand-written interfaces.
- [M3 启动前] Run `mvn -pl services/api test` on a machine with Docker available so `SchemaApiIntegrationTest` and other `@Testcontainers(disabledWithoutDocker = true)` integration tests execute instead of being skipped.
- [M3 启动前] Run `mvn -pl services/api test` on a machine with Docker available so `M1ApiIntegrationTest` executes instead of being skipped by `@Testcontainers(disabledWithoutDocker = true)`.
- [M3 启动前] Prepare the next acceptance script and defense recording plan; actual recording remains deferred until the broader M3-M5 feature set is ready.
- [M3 启动前] Add a stable cleanup guard for stale duplicate `* 2.class` artifacts under `services/api/target/classes`; the active workspace has moved out of iCloud/File Provider Desktop, but long smoke sessions should still fail fast if polluted build output appears.

## M3 期间(P1)

### AI / Provenance

- [M3 计划] Define the minimal AI supervision flow around `ai_calls`, `ai_calls_in_field`, idempotency keys, prompt versioning, and field-level provenance.
- [M3 计划] Decide whether M3 uses one provider directly or introduces a provider abstraction from the start.
- [M3 计划] Decide synchronous vs asynchronous AI calls for first integration; synchronous is simpler, async needs polling and job state.
- [M3 计划] Define AI failure, timeout, and cost-limit behavior before exposing UI triggers.
- [M5 计划] Implement provider retry/backoff behavior using `AiProviderException.retryable`, `providerCode`, and `statusCode`; M3 only records diagnostics and maps public provider failure.
- [M4 计划] Revisit idempotency key semantics if returned/resubmitted submissions mutate in place; M3-P1 reserves the input-hash mismatch guard to avoid stale AI evidence reuse.
- [M3-P6 计划] Implement the OpenAI-compatible provider path after the mock-backed service/controller/UI path is stable. 结果:M3-P6 已完成代码层 provider abstraction,M5-P6 已用 DeepSeek 完成真实 smoke。
- [M3-P5 计划] Decide the safe read shape for field-finding text in Submission detail UI; M3-P3 keeps raw `responsePayload` server-only, so frontend should not depend on raw provider payload leakage.
- [M3-P5 计划] Decide whether `GET /submissions/{id}/ai-review` should expose historical `overallSuggestion`, `summary`, and field-finding text as a safe read DTO; M3-P4 shows only public `AiCall` metadata in the provenance Card.
- [M4 计划] Reuse or extend the shared `AiProvenanceCard` for Reviewer review pages once Reviewer visibility and accept/reject flows are designed.
- [M4 计划] Decide whether Labeler should see cost/latency in production mode or whether those fields become Owner/Reviewer-only while hashes and timestamps remain visible to all permitted readers.
- [M3-P3 后续验证] Run `AiReviewIntegrationTest` on a Docker-enabled machine or local MySQL/API smoke environment so the new AI review HTTP/DB path executes instead of being skipped.
- [M5 计划] Persist failed AI provider attempts as append-only `ai_calls.status=failed` facts once retry/backoff and failure evidence semantics are designed.
- [M5 计划] Replace synchronous AI review with async job state only when outbox/worker/polling behavior is implemented; M3 keeps calls synchronous and terminal.
- [M3-P6 resolved by M5-P6] 真实 OpenAI-compatible provider smoke 已用 DeepSeek (`deepseek-v4-flash`) 补齐；截图为 `phase-m5p6-deepseek-first-call.png` 和 `phase-m5p6-deepseek-idempotency-hit.png`,DB 证据为 `phase-m5p6-db-ai-ledger-evidence.png`。
- [M6-P3a partial resolved by M6-P3a-2] Provider `usage` token counts are persisted to `ai_calls`; M6-P3a-2 now computes USD `cost_decimal` from complete prompt/completion token usage and falls back to the fixed estimate when usage is incomplete.
- [M5 计划] Add streaming response support only after the synchronous provenance path and async job semantics are stable.
- [M5 计划] Add encrypted API key storage / management UI instead of requiring raw provider keys in local env.
- [M5 计划] Add provider-specific adapters only when OpenAI-compatible behavior is insufficient, e.g. native Anthropic messages or provider tool-use APIs.

### Frontend / Auth / Shell

- [M3 计划] Implement login-after-redirect: `RequireAuth` should preserve the intended protected URL and `LoginPage` should return there after successful login.
- [M3 计划] Revisit `AppLayout` ownership if FSD boundaries become stricter: move the layout to `app/` or inject logout behavior instead of importing a feature hook from `shared/ui`.
- [M3 计划] Replace the frontend-local Task transition matrix with an API/OpenAPI capability if task workflow rules become configurable.
- [M3 计划] Replace temporary `actorId` Timeline display (`我` / `用户 #id`) with user display names once a user lookup endpoint exists.
- [M3 计划] Let `ForbiddenPage` distinguish "role cannot access this page" from "account has no available product modules" if no-role users become possible.
- [M3 计划] Add a frontend test harness for hooks/components (Vitest plus React Testing Library or equivalent) so `useAutosave` fake-timer tests and dataset upload UI tests can run inside the web workspace.

### Labeler / Submission / Dataset

- [M3 计划] Consider moving `SubmissionEntity` and `SubmissionMapper` from `module/schema` to `module/submission` once M2 submit/render flows settle, so the package boundary matches the submission domain.
- [M3 计划] Add draft revision history and rollback UI for Labeler sessions; P6 uses only the latest draft revision.
- [M3 计划] Improve submit-failure recovery in the Labeler session page by re-enabling autosave or exposing an explicit retry/edit state after a failed submit attempt.
- [M3 计划] Evaluate batch claim / claim-and-continue workflows for Labelers who want to reserve multiple dataset items from the same task.
- [M3 计划] Add a dataset-items list/preview endpoint and Owner UI so uploaded datasets can be inspected without direct SQL.
- [M3 计划] Add dataset edit/delete/archive policy and an optional standalone dataset management page if Owners need cross-task dataset operations.
- [M3 计划] Add an integration or controller smoke check that exercises generated OpenAPI multipart method validation annotations against the concrete controller implementation.
- [M3 计划] Add explicit request-parameter conversion tests for lowercase OpenAPI enum wire values such as `DatasetImportFormat` so multipart form fields cannot regress to Java enum-name conversion.
- [M3 计划] Add schema compatibility checks for dataset item payloads against the task's current schema before allowing publish or import confirmation.
- [M3 计划] Enrich `GET /my/sessions` with task title and `submissionId` so submitted rows in "我的数据" can link directly to `/labeler/submissions/{submissionId}` instead of the read-only session page.
- [M3 计划] Add a Submission detail comparison between the current task schema and the historical submission schema, e.g. "task 当前已是 v2", once the frontend has a supported current-schema contract.
- [M4 启动前] Run a cross-identity sweep over M2/M3 GET endpoints to catch ownership gaps similar to the M2-P5c `render-schema` gap fixed in M3-P3.5.
- [M4 计划] Design an Owner-specific submission review detail endpoint instead of reusing the Labeler-only `GET /submissions/{id}` contract when M4 opens review workflow.
- [M4 计划] Add AI field-finding history navigation and optional click-to-field linking once the safe historical findings DTO is defined.

### Schema Designer

- [M3 计划] Decide schema version deprecation semantics before exposing any lifecycle mutation: separate event table vs metadata status, and impact on Labeler visibility.
- [M3 计划] Add a first-class schema creation UI from `/owner/schemas`; P4a lists existing schemas and enters Designer only.
- [M3 计划] Evaluate select option value uniqueness in Designer; P4b auto-generates option values but does not enforce uniqueness before publish.
- [M3 计划] Optimize marketplace available-item counts with a batched query if P5/P6 pages grow beyond small demo-sized result sets; P5a intentionally uses simple per-task counts.

## M4 期间(P2)

- [M4 计划] Implement Reviewer review queue, review actions, and submission review transitions.
- [M4 计划] Implement Quality Ledger writes from review actions and derive `current_verdicts` from ledger facts.
- [M4 计划] Define submission return / resubmit behavior if Reviewer rejects or requests correction.
- [M4 计划] Decide Reviewer visibility into Labeler drafts, final submissions, AI traces, and schema-history facts.

### M4 Phase 4 Resolved

- 已解决: Reviewer queue now replaces the placeholder route and supports URL-synced verdict filtering.
- 已解决: Reviewer submission detail composes historical schema rendering, shared AI provenance, ledger history, and approve/reject actions without extracting a rigid page shell.
- 已解决: Approve/reject appends Quality Ledger entries and refetches derived verdict, ledger history, and reviewer queue state from server truth.
- 已解决: Live smoke repaired generated-controller validation annotation parity and Reviewer AI provenance read access.

### M4 Phase 5 Resolved

- 已解决: M4 acceptance checklist is published at `docs/m4-acceptance-checklist.md`.
- 已解决: README now marks highlight 2 as "M4 完整,M5 持续完善" and documents M4 Reviewer/Quality Ledger capabilities.
- 已解决: Screenshot INDEX references the six M4-P4 Reviewer evidence screenshots and marks approve/reject as the亮点 2 UI evidence.
- 已解决: Decision log contains the M4 quarter closing summary, including decisions, live-smoke fixes, workflow milestones, and the亮点 2 evidence chain.

## M5 期间(P3)

- [M5-P1 后续] Implement async export jobs only after synchronous task export proves the canonical artifact path; M5-P1 keeps the old V1 job table as metadata while the API contract is synchronous.
- [M5-P2 后续] Add fact-level export diff attribution after the hash-level diff endpoint is stable; M5-P1/P3 diff stays at file/hash equality for reproducibility evidence.
- [M5-P2 后续] Add a real AWS S3 or other S3-compatible storage switch smoke once credentials and bucket policy are available; M5-P1 uses the AWS S3 SDK against MinIO-compatible configuration.
- [M5-P2 后续] Decide export retention and object-key cleanup policy before adding delete/archive behavior; append-only snapshot rows currently assume immutable artifacts.
- [M5-P3 后续] Add object-storage residue cleanup for failed exports; M5-P2 rolls back SQL rows transactionally, but MinIO/S3 PUTs cannot be rolled back with the database transaction.
- [M5-P3 resolved by M5-P6] Trusted Export path was exercised against local MinIO through the browser smoke; `phase-m5p3b-diff-modal-equal.png` verifies the uploaded artifact hashes and file list at the UI level.
- [M5-P3 后续] Consider a read model or additional indexes for `export_snapshots.manifest_hash` / `source_state_hash` only after real export volume is known.
- [M5-P3b 后续] Design a download endpoint or signed URL strategy for exported artifacts; P3a exposes snapshot metadata and object keys but does not stream/download MinIO objects.
- [M5-P3b 后续] Add fact-level diff attribution UI after the hash-level modal evidence is stable; P3b intentionally shows three hash rows plus file-level SHA-256 matches only.
- [M5-P3b 后续] Add batch export and export-management views only after single-task Trusted Export has enough real usage; P3b keeps exports inside the task detail page.
- [M5-P3b resolved by M5-P6] Trusted Export browser screenshot smoke was rerun locally and archived: empty state, one snapshot, two selected snapshots, and equal diff modal.
- [M5-P3a resolved] `ExportIntegrationTest` is implemented and Docker-disabled in constrained environments; M5-P6 browser smoke supplied the live MinIO evidence needed for M5 acceptance.
- [M5-P5 resolved] Reviewer ledger history UI exposes `ai_field_finding` entries with payload narrowing by `entryType`; Owner/Labeler ledger cards remain a later product surface.
- [M5-P5 resolved] HTTP/DB integration tests now cover new AI review -> `ai_field_finding` rows and idempotency hit -> no duplicate ledger entries.
- [M5+ 计划] Add reviewer accept/reject decisions for individual AI findings as separate `reviewer_field_decision` ledger entries instead of mutating AI facts.
- [M5+ 计划] Add click-to-field navigation from AI finding ledger rows once the field-level ledger UI is stable.
- [M5-P5 resolved] Reviewer ledger UI now displays `ai_field_finding` rows with AI/severity tags, `AI Call #id`, field path, finding text, and confidence; integration tests now cover AI review ledger writes, idempotency non-duplication, three-role reads, and cross-labeler 404.
- [M5-P5 resolved by M5-P6] Reviewer mixed AI/reviewer ledger UI screenshots were captured locally: `phase-m5p5-reviewer-ledger-mixed.png` and `phase-m5p5-reviewer-ledger-mixed-after-approve.png`.
- [M5-P6 resolved] Real DeepSeek smoke completed with provider metadata, idempotency hit, sanitized DB evidence, Trusted Export screenshots, and mixed AI/reviewer ledger screenshots.
- [M5-P7 resolved] M5 acceptance checklist, README highlight status, screenshot INDEX evidence chains, M5 quarter summary, and humanpending cleanup are complete.
- [M6-P3a partial] OpenAI-compatible response usage payload is normalized and persisted; replacing fixed `AI_COST_PER_CALL` estimates with token-based pricing remains M6-P3a-2.
- [M5+ 计划] Add retry/backoff and provider-specific JSON hardening for real provider responses after the first DeepSeek smoke proved the happy path.
- [M5+ 计划] Re-run Trusted Export final-defense smoke with at least one still-`submitted` submission in scope so the snapshot list demonstrates nonzero submitted-record counts as well as hash reproducibility.
- [M5+ 计划] Replace the AI Drawer success copy that still says "Mock provider" when the active provider is OpenAI-compatible/DeepSeek.
- [M5+ 计划] Add Owner/Labeler ledger history cards after the Reviewer evidence surface proves the mixed reviewer/AI entry shape in real usage.
- [M5+ 计划] Add AI finding aggregation and ledger entry-type filters only after the minimal mixed-entry UI is stable.
- [M5 计划] Materialize `current_verdicts` cache for hot reviewer queue queries; M4 derives Verdict from ledger on every read, sufficient for demo scale.
- [M5 resolved] AI findings (`ai_field_finding`) are now field-level ledger entries and `QualityLedgerEntryPayload` is oneOf; future `reviewer_field_decision` remains a product enhancement.
- [M5 计划] Reviewer assignment mechanism (`reviewer_assignments` table + UI), replacing M4's "any REVIEWER role sees all" model.
- [M5 计划] Self-review policy refinements for multi-role users beyond M4's hard 409 block, including admin override or assignment-level exceptions if governance requires them.
- [M5 计划] Reviewer queue SQL/read-model optimization with dedicated indexes after real reviewer usage volume is known.
- [M5 resolved] Automated validation annotation parity checks now cover export controller query constraints; continue adding focused reflection tests for new generated controllers.
- [M5 计划] Add a confirmation modal for Reviewer approve/reject once review actions become higher-volume or non-demo irreversible actions.
- [M5 计划] Add auto-advance to the next pending submission after approve/reject for reviewer throughput workflows.
- [M5 计划] Add status filtering UI to the Reviewer queue if non-submitted review states become visible.
- [M5 计划] Reintroduce Excel dataset import only when M5 has an actual parser/import implementation and large-file handling decision.
- [M5 计划] Replace synchronous 10MB dataset import with async import jobs, progress polling, and larger-file handling.
- [M5 计划] Revisit dataset import mutability; M2 stores `import_status` on `datasets`, but an append-only import-event table may better preserve import lifecycle evidence once async jobs exist.
- [M5-P7 resolved] Trusted Export business logic over `export_snapshots`, file hashes, canonicalization version, and hash-level diff evidence is complete.
- [M5-P7 resolved] AI provenance and training-pollution control now include provider metadata, model/prompt version tracking, idempotency reuse, real provider smoke, and AI findings exposed through ledger/review evidence.

## M6 加分项(P4)

- [M6 加分] Add cross-level Designer drag-and-drop between top-level fields and `nested_object` children; P4 only supports same-level ordering.
- [M6 加分] Add Designer draft persistence through localStorage or a backend draft endpoint; P4 keeps drafts in the current page session only.
- [M6 加分] Add "load historical version into Designer" for fork/rollback workflows after version semantics are designed.
- [M6 加分] Add bidirectional Designer JSON editing once validation and conflict UX are designed.
- [M6 加分] Define a real file-upload endpoint (`POST /uploads` or session-scoped multipart upload), integrate MinIO storage, and replace the P6 `FileUploadFieldRenderer` text placeholder with actual upload and preview behavior.

## 已解决(归档)

### M4 Phase 3 Resolved

- 已解决: Quality Ledger and real-time Verdict HTTP endpoints are implemented through generated `ReviewsApi` in `ReviewerController`.
- 已解决: `SecurityConfig` now gates Reviewer queue and ledger writes with `REVIEWER`, while ledger/verdict reads remain authenticated and service-guarded.
- 已解决: Controllers extract raw role codes from `JwtPrincipal.roles()` and pass them to `LedgerService`, `VerdictService`, and `SchemaService.renderForSubmission`.
- 已解决: `SubmissionsController.getSubmissionRenderSchema` now uses the role-aware render-schema overload, enabling Reviewer review pages without changing existing Owner/Labeler service callers.
- 已解决: `QualityDtoMapper` converts physical ledger fields to public DTO fields and keeps the ledger payload strongly typed as `ReviewerOverallVerdictPayload`.
- 已解决: Disabled-without-Docker integration tests cover reviewer queue filters, ledger append, self-review 409, HTTP-level verdict re-derivation, reviewer render-schema access, and ledger read defenses.

### M4 Phase 2 Resolved

- 已解决: `LedgerService` writes append-only `reviewer_overall_verdict` entries with entry-type whitelist validation, payload shape validation, task-id alignment, and self-review blocking.
- 已解决: `VerdictService` derives live `pending` / `approved` / `rejected` verdicts from the latest ledger entry without maintaining `current_verdicts`.
- 已解决: `ReviewerQueueService` delegates to the SQL-level reviewer queue projection while preserving default submitted-status filtering and paged totals.
- 已解决: Services accept explicit requester role sets so M4-P3 can keep HTTP/security role extraction outside business services.
- 已解决: `SchemaService.renderForSubmission` has a reviewer-aware overload, allowing M4 reviewer pages to reuse historical schema rendering without reopening the M2-P5c ownership gap.
- 已解决: Unit tests cover `new_ledger_entry_changes_verdict`, self-review rejection, payload validation, reviewer access, and the latest-verdict SQL tie-break contract.

### M4 Phase 1 Resolved

- 已解决: OpenAPI is now `0.7.0` with Quality Ledger, real-time Verdict, Reviewer queue, and submission-nested ledger/verdict contracts.
- 已解决: V1 `quality_ledger_entries.evidence_type` is reused as the physical ledger type column; public API exposes it as `entryType` without adding V8 migration.
- 已解决: `QualityLedgerEntryMapper` is append-only and reflection-guarded with insert/select-only methods.
- 已解决: Verdict latest-entry selection uses `ORDER BY created_at DESC, id DESC` for deterministic tie-breaking.
- 已解决: M4 reserves 409 `SELF_REVIEW_NOT_ALLOWED` and 400 `LEDGER_ENTRY_TYPE_NOT_SUPPORTED` public errors for the Service phase.

### M3 Phase 1 Resolved

- 已解决: OpenAPI is now `0.6.0` with M3 AI review trigger and provenance contracts.
- 已解决: AI review output uses typed `FieldFinding` and `overallSuggestion`, not M4 `Verdict`.
- 已解决: `ai_calls` and `ai_calls_in_field` have append-only hand-written mapper contracts with no `BaseMapper` parent.
- 已解决: Public AI provider failures map to 502 `AI_PROVIDER_FAILURE`, and the idempotency input-hash guard has a reserved 409 `AI_PROVIDER_INPUT_HASH_MISMATCH` code.

### M3 Phase 2 Resolved

- 已解决: `AiProvider` interface and deterministic `MockAiProvider` are implemented for M3 service tests.
- 已解决: `AiReviewService.review` writes canonical-hashed `ai_calls` and field-level `ai_calls_in_field` rows with Owner ownership validation.
- 已解决: Idempotency hit reuses persisted AI evidence without invoking the provider again.
- 已解决: Same idempotency key with changed input hash throws `AiInputHashMismatchException` instead of silently reusing stale evidence.

### M3 Phase 3 Resolved

- 已解决: Owner can trigger AI review through `POST /submissions/{submissionId}/ai-review` and receive `AiReviewResult` with `idempotencyHit`.
- 已解决: Owner and the submitting Labeler can read AI provenance through `GET /submissions/{submissionId}/ai-review`; cross-identity reads return 404.
- 已解决: Route security distinguishes trigger vs read: POST requires Owner, GET requires authentication plus Service-level ownership.
- 已解决: `AiReviewDtoMapper` exposes public provenance metadata and derived hashes without exposing raw request/response payloads.
- 已解决: Disabled-without-Docker integration tests cover AI review trigger, provenance read, idempotency reuse, and cross-role/cross-identity defenses.

### M3 Phase 3.5 Resolved

- 已解决: Owner can discover task submissions through `GET /tasks/{taskId}/submissions` without exposing answer payloads or content hashes.
- 已解决: M2-P5c `GET /submissions/{submissionId}/render-schema` ownership gap is patched; only the submission Labeler or task Owner can read the historical render payload.
- 已解决: `SubmissionMapper` remains append-only and reflection-guarded after adding paged select queries for Owner submission discovery.
- 已解决: Disabled-without-Docker integration tests cover Owner submission discovery and render-schema cross-identity defenses.

### M3 Phase 4 Resolved

- 已解决: Owner task detail now lists task submissions and links to the nested `/owner/tasks/{taskId}/submissions/{submissionId}` route.
- 已解决: Owner submission detail renders historical schema answers through the guarded render-schema endpoint without using the Labeler-only submission detail endpoint.
- 已解决: Owner can trigger mock-backed AI review from the UI and see first-call vs idempotency-hit states in the AI result Drawer.
- 已解决: P4 live smoke fixed the AI `outputHash` normalization gap so first trigger, idempotency hit, and provenance GET share the same derived hash.

### M3 Phase 5 Resolved

- 已解决: `AiProvenanceCard` is now a shared read-only component for Owner and Labeler submission detail pages.
- 已解决: Labeler submission detail shows AI provenance metadata without exposing any AI trigger entry point.
- 已解决: Owner submission detail no longer blocks the main historical Renderer on provenance loading.

### M3 Phase 6 Resolved

- 已解决: `OpenAiCompatibleProvider` implements the `AiProvider` interface for OpenAI-compatible chat-completions backends.
- 已解决: Provider selection is configuration-driven through `labelhub.ai.active-provider`; default behavior remains mock, and real-provider mode fails fast when required env is missing.
- 已解决: `AiReviewService` now depends only on the `AiProvider` interface, so switching providers requires no business-service code changes.
- 已解决: Wire-level provider tests use JDK `HttpServer` without adding MockWebServer/WireMock dependencies.
- 已解决: M3 evidence docs now distinguish completed mock/provider-abstraction verification from pending real API smoke.

### M2 Phase 7b Resolved

- 已解决: Owner task detail now has an embedded dataset section for JSON/JSONL upload, dataset list display, and explicit current-dataset selection.
- 已解决: Multipart upload uses native fetch with the existing auth token helper and browser-managed multipart boundaries.
- 已解决: Browser smoke replaced the remaining SQL/JDBC dataset preparation step with real Owner UI upload and current pointer selection.
- 已解决: P7b live smoke fixed two backend contract edges: generated-interface validation annotations on `DatasetsController`, and lowercase multipart `DatasetImportFormat` conversion.
- 已解决: Published tasks still accept dataset uploads but disable current-dataset switching in the UI.

### M2 Phase 7a Resolved

- 已解决: Backend dataset import now uses multipart `POST /datasets` with JSON and JSONL support.
- 已解决: JSON/JSONL parsing rejects empty datasets and non-object items, and JSONL errors include the failing line number.
- 已解决: Imported dataset items use 1-based ordinal values and canonical SHA-256 item hashes; duplicate item payloads are preserved.
- 已解决: Owner task current-dataset selection is a dedicated `PATCH /tasks/{taskId}/current-dataset` mutation with published-task lock and dataset/task ownership validation.
- 已解决: OpenAPI is now `0.5.0`; frontend upload UI and task-detail dataset selection were completed in P7b.

### M2 Phase 6c Resolved

- 已解决: Labeler "我的数据" lists claimed and submitted sessions.
- 已解决: Submission detail uses `GET /submissions/{submissionId}/render-schema` and renders the historical schema version instead of task current schema.
- 已解决: Shared `schemaVersionLabel` keeps live session and historical submission version copy in sync.
- 已解决: Owner access to Labeler routes is stopped by frontend role guards, and cross-labeler submission access returns backend 404.

### M2 Phase 6b Resolved

- 已解决: Labeler marketplace, claim navigation, live session workspace, autosave status, submit Modal, and validation-blocking flow are implemented.
- 已解决: Cross-labeler session access returns 404 at the API boundary.
- 已解决: Autosave success and failure UI states were smoke-tested.

### M2 Phase 6a Resolved

- 已解决: Shared `SchemaRenderer` supports all seven M2 schema field types with edit and read-only modes.
- 已解决: Answer payloads are keyed by stable field IDs, including nested object children.
- 已解决: P6 file upload field is intentionally a URL/filename placeholder until real upload storage is implemented.

### M2 Phase 5c Resolved

- 已解决: Labeler can submit a claimed session exactly once; duplicate submit returns 409.
- 已解决: Submission inherits `schema_version_id` from the locked session row and stores a server-derived canonical content hash.
- 已解决: Historical render smoke verified a v1 submission still renders v1 after Owner publishes v2.

### M2 Phase 5b Resolved

- 已解决: Labeler can fetch owned session detail with task context, schema version, dataset item payload, and latest draft.
- 已解决: Draft saves are append-only: two saves to the same claimed session create revision 1 and revision 2, and latest draft returns revision 2.
- 已解决: Draft writes lock the parent session, reject non-owned sessions with 404, and reject non-editable sessions with 409.
- 已解决: `DraftMapper` is append-only and reflection-guarded against inherited update/delete surfaces.
- 已解决: `SessionDetail.task` uses a lightweight session-specific DTO instead of fake marketplace availability fields.

### M2 Phase 5a Resolved

- 已解决: Labeler marketplace lists published tasks with schema/dataset pointers and available items.
- 已解决: Claim creates a session, binds `schema_version_id` at claim time, reserves quota, and marks one dataset item claimed.
- 已解决: Quota claim uses optimistic update; dataset item assignment uses row locking.

### M2 Phase 4.5 Resolved

- 已解决: Schema publish now keeps `label_schemas.current_version_id` and `tasks.current_schema_version_id` aligned for P5 claim-time schema binding.
- 已解决: Task publish guard now rejects tasks without a current schema version or current dataset before they can appear in the Labeler marketplace.

### M2 Phase 4c Resolved

- 已解决: Owner Designer can publish a new immutable SchemaVersion from the browser UI, and the current-version pointer updates in the header.
- 已解决: Duplicate schema content now returns HTTP 409 `DUPLICATE_SCHEMA_VERSION_CONTENT` and is shown inside the publish Modal instead of leaking as a 500.
- 已解决: Version history SideSheet shows newest-first versions, current-version tag, content hash prefix, field counts, and expandable read-only schema JSON.
- 已解决: Browser smoke captured v1/v3 JSON evidence for SchemaVersion immutability and current pointer behavior.

### M2 Phase 4b Resolved

- 已解决: Designer supports seven field editors, same-level ordering, nested-object children, validation highlights, and read-only JSON preview.
- 已解决: Keyboard dnd path was added for repeatable smoke after pointer drag proved unreliable for automation.

### M2 Phase 4a Resolved

- 已解决: Schema Designer shell and Owner schema list route are implemented.
- 已解决: Labeler role access to Owner schema route returns frontend 403.

### M2 Phase 3 Resolved

- 已解决: `GET /submissions/{submissionId}/render-schema` is implemented through `SubmissionsController` and `SchemaService.renderForSubmission`.
- 已解决: Local smoke verified a submission bound to schema v1 renders v1 after schema v2 becomes current.
- 已解决: Schema request contract corrected: `fieldStableIds` is no longer accepted from clients because it is derived server-side from `schemaJson`.

### M2 Build Hygiene Resolved

- 已解决: Moved the active LabelHub workspace out of iCloud/File Provider synced Desktop into `/Users/gods./Downloads/LabelHub - Platform`; continue watching for duplicate `ApiApplication 2.class` artifacts, but the known Desktop CloudDocs trigger has been removed.
- 已解决: `spring-boot-maven-plugin` declares `mainClass=com.labelhub.api.ApiApplication` so local runs do not depend on scanning polluted `target/classes` output.

### M1 Phase 5d Resolved

- 已解决: Labeler and Reviewer now have authenticated placeholder routes without fake business behavior.
- 已解决: Login and root redirects route users by explicit role priority rather than backend role-array order.
- 已解决: Sidebar menu items are derived from the current user's roles; no-access users get an explicit empty-state note.
- 已解决: README now includes local startup, implemented scope, roadmap, and differentiator code locations.
- 已解决: Screenshot index now maps current demo images to the contracts they prove.

### M1 Phase 5c Resolved

- 已解决: Owner task detail route `/owner/tasks/:taskId` shows task fields, status badge, transition actions, and transition history.
- 已解决: Shared transition reason modal is used for publish, pause, resume, and end; reason is required and whitespace-only input is rejected.
- 已解决: Browser smoke verified publish -> pause -> resume -> end, with four append-only Timeline entries and stored reasons.
- 已解决: Returning to the task list shows the same task as `已结束`, confirming task-list invalidation after detail-page transitions.
- 已解决: API-level illegal transition check verified an ended task cannot transition back to published and returns 409.
- 已解决: Publish guard failures now return 400 + camelCase `fieldErrors` and render a persistent Modal error such as `无法发布: 配额必须大于 0。`.
- 已解决: Phase 5d remained scoped to Labeler/Reviewer placeholders, screenshot inventory, and M1 frontend wrap-up.

### M1 Phase 5b2 Resolved

- 已解决: Owner task list now uses URL-synced `page`, `size`, and optional `status` parameters as the single source of truth.
- 已解决: Status filtering resets to page 1 and omits `status` entirely for the "全部状态" view.
- 已解决: Create Task modal supports title, description, quota, deadline, and tags with field-level validation and query invalidation after successful creation.
- 已解决: Browser smoke verified created draft tasks appear in the all-status list and disappear from the `status=published` filtered view.
- 已解决: Browser smoke verified the paused empty state and past-deadline field validation.
- 已解决: Backend `TaskStatus` request-parameter conversion now preserves lowercase OpenAPI enum values such as `published`.
- 已解决: Phase 5c remained scoped to Owner task detail, state-transition actions, and transition timeline.

### M1 Phase 5b1 Resolved

- 已解决: Login flow implemented: `owner_demo / demo1234` reaches `/owner/tasks` and renders authenticated Header state.
- 已解决: Wrong-password login stays on `/login` and renders "用户名或密码错误" under the password field instead of firing the global 401 redirect.
- 已解决: Logout clears QueryClient/session state and returns to `/login`.
- 已解决: `RequireAuth` actively rejects missing or expired sessions before protected pages issue business requests; OpenAPI client 401 handling remains as fallback.
- 已解决: `RequireRole` blocks `labeler_demo` from the Owner route and shows the 403 placeholder.
- 已解决: Phase 5b2 remained scoped to Owner task list, status filtering, pagination, and task creation modal.

### M1 Phase 5a-1 Resolved

- 已解决: Context path B方案 selected and verified: OpenAPI server URL and Spring Boot now use `/api`; old non-`/api` runtime paths return 404.
- 已解决: Internal token path check verified with context path: `/api/internal/**` reaches the application only after `X-Internal-Token` passes.

### M1 Phase 5a Resolved

- 已解决: Frontend shell routes now use `createBrowserRouter` with `AppLayout` as the root route.
- 已解决: Vite `/api` proxy verified through the browser console against the local API.
- 已解决: Owner shell navigation is in place; Login, task list, task detail, Labeler, and Reviewer business pages remain for later Phase 5 steps.

### M1 Phase 4 Resolved

- 已解决: SecurityConfig hardening: business endpoints now require JWT authentication; Owner Task endpoints additionally require `ROLE_OWNER`.
- 已解决: Internal API hardening: `/internal/**` is gated by `X-Internal-Token` matching `LABELHUB_INTERNAL_TOKEN`.
- 已解决: Audit payload integrity: `audit_logs` has `payload_hash`, and Task transitions serialize payload JSON via Jackson instead of manual string concatenation.
- 已解决: Real local MySQL smoke passed: `/actuator/health`, Owner login, task create, publish transition, task list pagination, missing-token 401, wrong-role 403, and internal-token 404 for unimplemented internal routes.
- 已解决: Security chain debugging resolved: the attempted multi-chain setup was simplified to one explicit stateless chain after public-route smoke tests returned 401 through error dispatch.
- 已解决: Controller runtime binding fixed: generated endpoint implementations now use explicit `@PathVariable("taskId")` names.
- 已解决: Task list pagination fixed: MyBatis-Plus pagination interceptor and `mybatis-plus-jsqlparser` are configured so `total` reflects real matching rows.
- 已解决: Task resume guard fixed: `paused -> published` is covered by TaskService combination tests, and `canPublish` no longer duplicates state-transition legality checks.

### M1 Phase 1 Resolved

- 已解决: Audit transaction semantics: Task state-machine transitions use strong consistency. `tasks` update + `task_transitions` insert + `audit_logs` insert are one transaction; observational audit remains best-effort. ADR-016 is deferred to Phase 6.
- 已解决: Publish guard depth: M1 state legality is handled by `TaskStateTransitions`; publish business guards check only `quota_total > 0` and `deadline_at > now`; dataset, schema version, and adjudication rule guards moved to later phases.
- 已解决: Task list scope: `GET /tasks` lists only tasks owned by the authenticated Owner; no admin view in M1.
- 已解决: OpenAPI response shape: Phase 2 adds `GET /tasks`, `GET /tasks/{taskId}`, `PagedTasks`, `ApiError`, shared error responses, and bearer auth.
- 已解决: Login response shape: Phase 2 adds `tokenType`, `expiresAt`, and minimal user profile.
- 已解决: OpenAPI version bump: M1 additions bump the contract from `0.1.1` to `0.2.0`.
- 已解决: Demo user seed IDs: use fixed ids `1001/1002/1003` with `INSERT IGNORE` and role-code subqueries.
- 已解决: `humanpending.md` location: root `humanpending.md` remains authoritative; `coderules.md` wording is deferred to a separate protected-file revision.
- 已解决: Testcontainers dependency timing: add Testcontainers only in Phase 4 with controller/security integration tests.
- [M7-P2 watch] SchemaRenderer + field-renderers retained as fallback and benchmark baseline after C7 consumer page swap; remove in a future cleanup phase once Formily renderer has seeded browser regression evidence across labeler, owner, reviewer, and designer surfaces.
- [M7-P2 resolved] Formily runtime form adoption complete per rubric 1.4 hard-requirement adjudication. 4 consumer pages (LabelerSession, LabelerSubmission, OwnerSubmission, ReviewerSubmission) plus OwnerSchemaDesigner preview now render via SchemaFormilyRenderer with 6 local Semi x-components covering 7 SchemaFieldTypes. Virtualization at >50 fields via @tanstack/react-virtual. Vitest + Vitest bench tooling adopted; 27 tests pass; bench evidence shows 1 renderer invocation per single-field change in a 500-field form (vs 500 in legacy). Schema-versioning asymmetry (outbound preserves all keys, inbound filters to current schema) protects M6-P0.5 immutability and M6-P5 trusted-export reproducibility. payloadValidation.ts remains submit-time authority; Formily x-validator is UI projection subset. Path B+C auditor recommendation at 76d61b2 preserved as superseded R8 trail. M7-P2 watch tracks future cleanup of legacy SchemaRenderer. OpenAPI/migrations/backend unchanged; new deps @tanstack/react-virtual, vitest, jsdom.
- [M7-P3a resolved] Dual-side answer validation symmetry complete. The backend submit security gap is closed: `SessionService.submit` now validates answer payload field constraints before persistence, so frontend validation is no longer the only line of defense. C1 added the submit 422 contract reusing `ApiError.fieldErrors`; C2 added `AnswerPayloadValidator`, `AnswerValidationException`, and 422 handler mapping; C3 wired validation into submit using the session-bound schema version; C4 added the shared 20-case validation corpus plus backend/frontend symmetry tests and submit/version integration tests; C5 preserved backend 422 `fieldErrors` through `SubmitValidationError` and maps them into `SchemaFormilyRenderer` errors on the labeler submit page. Submit-context `ApiFieldError.field` means `SchemaField.stableId`. Session-bound version validation protects M6 schema versioning and M6-P0.5 immutability. Shared corpus proof covers the mirrored 11 message families; backend tests moved 408 -> 431 locally and frontend Vitest moved 27 -> 48 -> 54. `payloadValidation.ts` remains the frontend submit-time reference/projection; backend `AnswerPayloadValidator` is its authoritative mirror at the persistence boundary. OpenAPI MD5 shifted once in C1 to `304b6d00e35a3649fd10ae9f01392288`; migrations stayed 11 and `pom.xml` stayed unchanged.
- [Scientific-notation message asymmetry deferred] The shared validation corpus exposes one known message-formatting mismatch without fixing it: backend `formatNumber()` renders a tiny threshold such as `1e-7` as `0.0000001`, producing `不能小于 0.0000001`, while frontend JavaScript `String(1e-7)` produces `1e-7`, yielding `不能小于 1e-7`. Corpus case `number-min-scientific-known-asymmetry` is the only `expectSymmetry:false` case and records the backend wording while frontend asserts the same rule family. A future adjudication should choose whether to make backend formatting emulate JavaScript, change frontend formatting, or accept this as a documented limitation.
- [M7-P3b resolved] Field linkage DSL complete. P3b adds `visibleWhen` and `requiredWhen` with a constrained JSON AST: one-level `allOf` / `anyOf`, atomic conditions, 10 op whitelist (`eq`, `neq`, `in`, `notIn`, `gt`, `gte`, `lt`, `lte`, `empty`, `notEmpty`), flat `SchemaField.stableId` references, no eval, and no recursive condition tree in v1. C1 added the OpenAPI DSL, generated types, and dual-side publish validation; C2 added backend `LinkageEvaluator` plus `AnswerPayloadValidator` double-view coupling; C3 added the frontend evaluator, `payloadValidation` coupling, renderer-external visibility filtering, and minimal-B modal consistency; C3.5 fixed stored schema_json -> `SchemaDocument` linkage deserialization through a shape-based Jackson module; C4 added the shared 31-case linkage corpus (21 runtime + 10 publish, zero asymmetric cases) plus submit integration through the real DB JSON round-trip path; C5 added the owner designer advanced JSON entry point. Hidden fields skip validation but values are not stripped; visible `requiredWhen` reuses `此字段必填`; session-bound schema version validation remains intact. Backend tests moved 431 -> 494 across P3b and frontend Vitest moved 54 -> 120. OpenAPI MD5 shifted once in C1 from `304b6d00e35a3649fd10ae9f01392288` to `890e595c6351ee53788d35354b2412a3`; migrations stayed 11.
- [M7-P3b watch] Visual condition editor deferred. P3b v1 accepts linkage DSL via schema JSON / the owner designer advanced JSON entry point only. A visual condition-tree builder, field picker, operator-specific editor, condition previewer, and richer UX validation are deferred to P3b v2 or a later UI phase.
- [M7-P3b R8] C2/C3 testing blind spot and C3.5 repair trail. C1-C3 linkage tests mostly constructed generated Java/TypeScript condition objects directly and did not exercise the production path where persisted schema_json is converted back into `SchemaDocument`. C4's round-trip probe exposed that `LinkageCondition` was a marker interface that Jackson could not instantiate, affecting `SessionService.validateAnswerPayload` and `SchemaDtoMapper` conversion paths. C3.5 fixed this with shape-based deserialization (`field`/`op` -> atomic, `allOf`/`anyOf` -> group) registered on the real Spring `ObjectMapper`, with tests proving the application mapper path rather than a local test-only mapper.
- [M7-P3b R8] SessionService convenience constructor ObjectMapper watch. `SessionService` still has a convenience constructor that creates `new ObjectMapper()` without the linkage module. Production submit uses the autowired main constructor and the Spring-configured ObjectMapper, so the production path is safe after C3.5. If the convenience constructor is used with linkage schemas in future tests or utilities, it can reintroduce the deserialization failure; this was recorded rather than fixed in C3.5 because changing that compatibility constructor was outside the narrow deserialization repair scope.
- [M7-P3b R8] Numeric precision boundary recorded. C4's linkage corpus proved Java BigDecimal comparison and JavaScript finite-number comparison agree for the JSON-safe probes included in the corpus, including `1` versus `1.0`, `Number.MAX_SAFE_INTEGER`, and high-precision decimal values that round into JS number semantics. There are zero `expectSymmetry:false` linkage cases. Extremely high precision values outside JS double semantics remain a theoretical boundary, related to the P3a scientific-notation class of risks, but no P3b runtime evaluator mismatch was observed.
- [fix watch] Task-to-schema global uniqueness is not yet enforced at the database/API boundary. The task detail "去设计" hotfix reuses an existing schema by scanning `GET /schemas` before creating one, which prevents normal UI duplicates, but `label_schemas.task_id` still has no unique index and direct/concurrent API calls can create multiple schema families for one task. A future backend hardening cluster should add the adjudicated global rule (likely unique task_id for task-bound schemas, with a migration and conflict response) if one-schema-per-task is meant to be an invariant.
- [M7-P4a resolved] Prompt version foundation complete. P4a upgrades AI evidence from a plain `ai_calls.prompt_version` string label into immutable prompt-version binding: `prompt_versions` is now the owner business prompt asset table, `ai_calls.prompt_version_id` binds new AI calls to that asset, and `ai_calls.provider_adapter_version` records the provider-adapter provenance slot while preserving legacy `prompt_version` labels for old and new rows. Research approved the P4a/P4b split and the split-source architecture: owner business prompt versions live in LabelHub DB, provider-specific prompts/adapters remain in `services/agent` per ADR-011. C1 added the OpenAPI additive fields, `prompt_versions`, and `ai_calls` FK/default migrations; C2 added `PromptVersionService`, a seeded default published prompt, direct `sha256Hex(content)` hashing, and `GET /prompt-versions/default`; C3 hard-switched trigger requests and `AiReviewService` to `promptVersionId`, changed idempotency keys to include promptVersionId plus adapter, updated failed-call and export evidence, and kept provider behavior unchanged; C4 surfaced Prompt ID and Adapter in provenance UI with legacy-null fallback; C5 locked legacy old-key behavior so historical rows remain readable but are not reused by new id-based keys. Backend tests moved 494 -> 516 and frontend Vitest moved 120 -> 131 across P4a. OpenAPI MD5 moved `2482d531df39e9e12613bf964f3618ea` -> `c1ee4d213661b881344e59f0ab079f4a` -> `b58f005b7dbfecb487b35e7255bb36d5` -> `23a67e2cad632b3e9cfaff03c5d05dd7`; migrations moved 11 -> 14.
- [M7-P4a watch] provider_adapter_version is a constant placeholder in P4a. New `ai_calls` rows write `agent-default-v1`, and the value is included in the new idempotency key so evidence has a future-proof slot for provider adapter provenance. It is not yet linked to a real `services/agent` adapter release or provider-system-prompt version. A later agent-versioning cluster should replace the constant with a real adapter version source.
- [M7-P4a watch] Default prompt content is a placeholder. P4a seeds the published prompt version with content `m3-owner-review-v1` so promptVersionId-based evidence can be wired end to end before Owner prompt editing exists. Real owner-defined prompt content, variables, scoring dimensions, and rule editing are deferred to P4b.
- [M7-P4b watch] AiReviewRule and owner prompt editor deferred. P4a only delivers immutable prompt-version infrastructure and AI-call evidence binding. `ai_review_rules`, rule family/root semantics, owner prompt/rule editing UI, prompt version publishing UI, and any task publish guard that requires a review rule remain P4b work. P4a's global `prompt_versions` evidence rows should not be retroactively reinterpreted; P4b can add rule pointers or a rule-layer version display on top.
- [M7-P4a R8] Docker D-口径 for prompt-version integration tests. P4a's permanent Testcontainers coverage includes `AiReviewIntegrationTest` for id-based review trigger and C5 legacy idempotency behavior plus prompt-version API integration coverage. In this local environment Docker was unavailable or incompatible, so these integration tests skip under `@Testcontainers(disabledWithoutDocker = true)`. The tests remain committed as regression guards and should run in Docker-capable CI/local environments.
- [M7-P4b1 resolved] AiReviewRule backend foundation complete. P4b1 adds task-scoped AI review rule versioning and closes the prompt-only evidence half-binding: `prompt_versions` remains the global immutable prompt-text asset, `ai_review_rules` stores task-scoped dimensions/threshold plus a prompt-version pointer, and rule-bound `ai_calls` now write `ai_review_rule_id` and a rule-bound idempotency segment so prompt text, dimensions, and threshold are reproducible. RESEARCH split P4b into P4b1 backend and P4b2 UI; C1 added AiReviewRule contract, `ai_review_rules`, and `tasks.current_ai_review_rule_id`; C2 implemented save/publish endpoints and promptTemplate -> PromptVersion conversion; C3 made active rule win at runtime while preserving no-rule P4a fallback; C4-fix repaired export null evidence canonicalization without touching shared `Canonicalizer`; C4 added integration guards for active rule, no-rule fallback, key isolation, dangling pointer, and mixed provenance. Review rules remain optional and `TaskService.canPublish` is unchanged. Backend tests moved 516 -> 540 with skips 84 -> 88 across P4b1; frontend stayed 131. OpenAPI MD5 moved `23a67e2cad632b3e9cfaff03c5d05dd7` -> `b10b8cf2339f4b01c683eb8b7d12bf2f` -> `7c9358b2b2d5a1079de8f768a243841a` -> `b7df19fdb69f8d22b2f0dbdbc845d95d`; migrations moved 14 -> 17.
- [M7-P4b1 watch] Conclusion strategy remains threshold-derived in v1. P4b1 stores prompt text, dimensions, and threshold; the AI conclusion states (pass, return, human review) are treated as output classification derived from threshold and scoring behavior, not as an independent owner-configurable rule field. Configurable three-zone thresholds or custom conclusion mapping are deferred until scoring calibration or P8 needs them.
- [M7-P4b1 R8] Export canonical null-omit correction. P4b1 C3 added nullable `aiReviewRuleId` to export canonical AI-call rows, and shared `Canonicalizer` preserves null map entries, so no-rule/legacy rows would have shifted from no field to `"aiReviewRuleId":null` without a canonicalization-version bump. Audit also found the same drift class from P4a C3's nullable `promptVersionId`. C4-fix uses export-local targeted omission (`putIfNotNull`) for only `promptVersionId` and `aiReviewRuleId`, repairing both drifts while leaving old nullable export fields unchanged. `Canonicalizer` remains untouched and schema/prompt/submission/dataset/audit/input hash paths are isolated. `CANONICALIZATION_VERSION` stays v1, but this is recorded as a correction of v1 optional-evidence semantics, not a claim that v1 always behaved that way.
- [M7-P4b1 R8] export_snapshots stock not verified. During C4-fix there was no local MySQL CLI path to inspect real `export_snapshots` row count, so the fix assumes the dev environment has no stored v1 snapshots that must preserve the drifted null-evidence shape. Production deployments with existing v1 export snapshots containing drifted null `promptVersionId` or `aiReviewRuleId` should review whether to bump `CANONICALIZATION_VERSION` or add compatibility handling.
- [M7-P4b1 R8] `ai_calls.ai_review_rule_id` evidence binding closes the half-binding. C1 deliberately deferred the nullable AI-call rule FK until runtime integration; C3 added it once active-rule resolution existed. Legacy and no-rule calls keep `ai_review_rule_id = NULL`; rule-bound calls record the exact rule version and include the rule segment in the idempotency key, so dimensions/threshold changes create new evidence instead of reusing prompt-only evidence.
- [M7-P4b2 watch] Owner prompt/rule editor UI deferred. P4b1 delivers backend rule infrastructure and runtime binding only. Owner-facing prompt textarea, dimensions editor, threshold control, save-draft flow, publish button, and version history UI remain P4b2 work. The backend `saveAiReviewRule` and `publishAiReviewRule` endpoints are ready for that UI.
- [M7-P4b1 R8] Docker D-口径 for AI review rule integration tests. P4b1's permanent `AiReviewIntegrationTest` cases cover active rule wins, no-rule fallback, key isolation, dangling active rule pointer, and mixed legacy/rule-bound provenance, but they use Testcontainers and skip locally when Docker is unavailable. The tests are committed as regression guards for Docker-capable environments. Export canonical tests run without Docker and were part of the local backend verification.
