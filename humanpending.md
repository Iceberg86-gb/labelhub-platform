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
- [M7-P4b2 resolved] Owner AI review rule editor UI complete. P4b2 closes the owner-facing rule configuration loop on top of P4b1: C1/C2 added the task-detail entry, save/publish hooks, and append-only save form; C1.5 added first-class `GET /ai-review/rules?taskId=...` with required `isCurrent` and retired the P4b1 OpenAPI anchor `b7df19fdb69f8d22b2f0dbdbc845d95d`; C3 added the version history panel, backend-derived current marker, and publish flow with list invalidation; C4/C5 added an integration guard and closure docs. Final P4b2 OpenAPI MD5 is `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations remain 17, frontend Vitest moved 131 -> 147 across P4b2, and backend full suite passes 549 / 88 in the escalated run.
- [M7-P4b2 R8] Full backend 10-error D-口径 closed for this workspace. The sandbox `mvn -pl services/api test` run reproduced the prior C1.5 10 errors, all from MySQL/Flyway or OpenAI-compatible provider socket operations (`Operation not permitted` / communications link failure). The escalated run completed successfully with 549 tests, 0 errors, and 88 skipped, confirming the prior 10 errors were environment restrictions rather than P4b2 code failures.
- [M7-P4b2 watch] AiReviewRule detail endpoint deferred. P4b2 v1 implements list-based history and publish UI only; `GET /ai-review/rules/{ruleId}` is still intentionally absent. The list response contains all fields needed for the current editor, history, and publish flow. Future deep-linking, single-version inspection, or comparison UX should add a detail endpoint deliberately rather than overloading the list contract.
- [M7-P4b2 watch] Three-viewport browser verification remains D-口径. The requested 1440 / 1280 / 1024 browser pass could not be completed because the Browser runtime returned `Browser is not available: iab`; no screenshots were fabricated. Component/server-render coverage now guards the form/history separation and publish/current controls, but visual viewport proof should be rerun in a Browser-capable session before treating the P4b2 UI as visually certified.
- [M7-P5 resolved] Labeler offline answer draft loss prevention complete. P5 keeps the P4b2 OpenAPI anchor `1acd96fb6c0fd0e7b084245d8ae3fa76` unchanged and adds a pure-frontend local recovery layer around the existing append-only server draft API: C1 added IndexedDB/plain-json-v1 storage with user+session keying, TTL/session/discard helpers, and memory tests; C2 made local pending hydrate win when schemaVersionId matches and buffered failed autosaves; C3 added sync triggers, retry backoff, BroadcastChannel wrapper, single-tab lease, and the 409/auth/not-found/bad-request failure matrix; C4 integrated submit with best-effort pre-sync, blocking only 401/403/404/409, clearing pending only after submit success, and preserving P3a 422 validation mapping; C5 added a cross-layer integration guard and closure docs. Frontend Vitest moved 147 -> 194, migrations stayed 17, and backend behavior stayed unchanged.
- [M7-P5 watch] IndexedDB plaintext answer payload security hardening deferred. P5 v1 stores local pending answer drafts as plaintext JSON scoped by userId + sessionId with TTL and cleanup helpers. True browser-side encryption and logout cleanup UX are deferred to a dedicated security cluster; C1 records include storageVersion and encoding to support a future encrypted-json migration.
- [M7-P5 R8] Browser, real IndexedDB, and multi-tab verification remains D-口径. C5 attempted the in-app Browser runtime and received `Browser is not available: iab`, so no screenshots or live offline-devtools/multi-tab proof were fabricated. Vitest coverage now guards memory-store end-to-end behavior, sync failure classification, retry backoff, submit pre-sync, and current P3a submit validation preservation, but real browser checks for IndexedDB persistence, BroadcastChannel delivery, visibility/online events, lease timing across tabs, and 1440/1280/1024 visual status-tag layout should be rerun in a Browser-capable session.
- [M7-P5 watch] Logout and token-expiry local draft cleanup UX deferred. P5 intentionally provides cleanup helpers and submit/terminal-session cleanup but does not bind local draft deletion to logout or token-expiry events, preserving loss-prevention behavior when auth expires. A later security/UX cluster should decide whether logout should warn, preserve, or clear local plaintext pending drafts.
- [M7-P5 C4.5 follow-up] Labeler dataset item context card repaired the post-closure probe gap where the labeler scoring page rendered the answer form but not the item being judged. C4.5 added a read-only `DatasetItemContextCard` sourced primarily from frozen `claimSnapshot.datasetItemPayload` with live `datasetItem.itemPayload` only as legacy/exception fallback, defensive free-form rendering for known and unknown fields, plain-text `content_markdown`, and no writes to `answerPayload`; frontend Vitest moved 194 -> 200. The older M7-P5 resolved entry is preserved as the pre-C4.5 closure audit record, while `docs/internal/m7p5-verification.md` has been rewritten as the current authoritative P5 closure including C4.5.
- [M7-P5 C4.6 follow-up] Submit validation feedback repaired the post-C4.5 probe gap where client validation could block submit with only the generic `请先修复字段错误再提交` toast and no visible field-level error. Root cause was the dual-track validation path: `validatePayload` correctly blocked submit and Formily validators could display minLength errors, but `handleSubmitClick` never activated the Formily display track for untouched fields. C4.6 implements route 3a by exposing the existing Formily form via `onFormReady`, calling `form.validate()` on the intercept path, showing a concrete `{field label}: {reason}` toast such as `详细评审意见: 最少 5 字`, and scrolling/focusing the first invalid field by stable `data-labeling-field-id`; it does not inject client errors with `setFieldState`, does not change `handleConfirmSubmit`, and preserves the original intercept semantics. Frontend Vitest moved 200 -> 203, and `docs/internal/m7p5-verification.md` has been rewritten as the final authoritative P5 closure including C4.6.
- [project-state watch] Status doc ADR list materially incomplete. The maintained state doc enumerated only ADR-004/005/011, but `docs/adr/` actually holds 15 ADRs (001-015 + template). Two of the omitted ones directly govern P-A: ADR-006 (function calling required; plain text parsing not accepted for verdict decisions) and ADR-008 (MySQL outbox + polling workers with idempotency/retry/dead-letter). ADR-011's real title is "Doubao Default With OpenAI Fallback" (provider-specific prompts/model/output adapters stay in services/agent), not the previously assumed "provider prompt isolation" phrasing. The full ADR list should be backfilled into the state doc so future phases stop rediscovering governing ADRs from scratch.
- [P-A watch] Reject-floor magic number pending justification. The P-A gate scoring map uses `rejectFloor = max(0, threshold - 0.20)`; the 0.20 has no ADR or requirement backing and was fixed at gate time. Implementation should make it configurable or record the scoring-rule version in the ledger so future threshold changes are not silent behavior shifts.
- [P-A watch] Manual AI review endpoint dual-trigger risk. `AiReviewController:47-55` (`triggerSubmissionAiReview` -> `aiReviewService.review`) currently is the only AI trigger. Once submit auto-enqueues via outbox, leaving this manual endpoint active creates a second trigger path that can bypass the outbox idempotency boundary. Implementation must explicitly keep-as-debug-only or deprecate it, not leave both live.
- [P-A R8] Backend full-suite remains D-口径 in audit sandbox. The audit sandbox has no mvn; agent-side `command -v mvn` returns a real binary. P-A backend test counts must come from the implementation agent's escalated mvn run, not the audit sandbox. Worker concurrency/lock-lease and provider network behavior are runtime D-口径, guarded by unit/integration tests with fake/mock providers; no browser available (`Browser is not available: iab`) for any UI viewport proof.
- [P-A watch] Vitest anchor drift 203 -> reported 204. The P-A audit could not run Vitest in-sandbox; the core-requirements self-audit reported 204 passed against the P5 anchor of 203. The +1 source must be confirmed (and the anchor updated) in a node-capable session before treating Vitest counts as a verification gate for P-A.
- [P-A update] P-A governance list backfilled for this phase. This supersedes the earlier `[project-state watch]` P-A-specific gap: `docs/P-A-closure.md` now lists ADR-004/005/006/008/011, including ADR-006 function calling and ADR-008 outbox polling. The broader global state doc still needs a full ADR inventory later.
- [P-A resolved] Reject-floor scoring watch bounded by implementation. The P-A implementation records a scoring rule version and uses configurable scoring properties, so the gate-time `threshold - 0.20` concern is no longer an untracked magic-number behavior for this phase.
- [P-A resolved] Manual AI review dual-trigger watch constrained. The manual trigger endpoint is now deprecated for P-A and marks responses with `X-LabelHub-Debug-Only: manual-ai-review`; submit auto-enqueue through outbox is the production trigger path.
- [P-A resolved] Supersedes the earlier `[P-A R8]` D-口径 entry: backend and agent Maven verification are empirical in the implementation environment. Fresh escalated runs on 2026-05-29 produced API `559 / 0 / 88` (`mvn -pl services/api test`) and agent `3 / 0 / 0` (`mvn -pl services/agent test`).
- [P-A update] Vitest anchor drift remains open. The earlier `[P-A watch]` entry stays valid: the original 203 -> 204 source is still not attributed, and the later 206-pass implementation run is current health evidence rather than a frozen frontend-test anchor.
- [P-A/P5 R8] Deferred commit reconciliation. P5 C2-C4.6 and P-A were implemented but never committed; HEAD had stopped at M7-P5 C1 while b808 OpenAPI and all P-A code lived in an uncommitted working tree. They are now squashed into two commits (P5 C2-C4.6, then P-A). Per owner decision, layers were NOT re-tested individually post-split; the prior green evidence (frontend Vitest, API 559/0/88, agent 3/0/0) was produced on the combined working tree, so per-layer green is accepted as a combined-verification inference, owner-aware. The 4 deleted mX-acceptance-checklist.md are intentional cleanup. A pre-split physical backup exists at /private/tmp/labelhub-platform-working-tree-backup-20260529-pb-stopgap.tgz (sha256 f5603057...).
- [M7-P-B owner decision] Single-batch high-risk scope was owner-adjudicated before implementation: P-B intentionally combined full JSON Schema migration, four missing materials, file_upload de-placeholdering, true canvas drag, multi-Tab layout, Formily x-reactions, and custom validation in one phase despite gate-recorded low cap confidence and regression-localization risk. This entry records that decision append-only after C1-C4 implementation; old humanpending entries were not rewritten.
- [M7-P-B C1 resolved] JSON Schema v2 runtime compatibility and five-downstream guards are implemented in commit `1174563`. Legacy immutable `fields[]` schema_json and new `x-labelhub-schemaFormatVersion: 2` JSON Schema storage are read through the runtime adapter rather than try-parse fallback; old schema_json/content_hash is not rewritten; P-A AI context, submit validation, export manifest schemaJson/contentHash, historical re-render, and P5 schemaVersionId hydrate paths were guarded by targeted tests. ADR-002/004/012/015 remain protected by dual-read and old-hash fixture behavior.
- [M7-P-B C2 resolved] Material completion is implemented in commit `c8c8ca9`: rich_text, json_editor, llm_interaction, show_item, and real file_upload flow now have contract/type/editor/renderer/Formily/backend validation coverage. Field assist reuses P-A provider/provenance boundaries and show_item remains display-only, excluded from outbound answer payload. Verification evidence moved to frontend `211 / 0 / 0` and API `578 / 0 / 88` for that checkpoint.
- [M7-P-B C3 resolved/watch] True Designer palette-to-canvas drag and multi-Tab layout are implemented in commit `e82edef`: material palette, canvas drop/sort, DragOverlay, tab_container schema/runtime rendering, flat tab-child payload, backend validator/stableId/runtime adapter support, and corpus coverage landed. Browser visual proof for drag/drop, Tab layout, rich text, and upload remains D-口径 in this environment (`Browser is not available: iab`); unit/type/full-suite evidence is committed, but live viewport interaction should be rerun in a Browser-capable session.
- [M7-P-B C4 resolved/watch] Formily x-reactions projection and named custom validation functions are implemented in commit `2be23b2`. `visibleWhen`/`requiredWhen` remain the backend submit/publish authority and now project into Formily `x-reactions`; custom validation is a safe named-function whitelist (`nonBlankTrimmed`, `httpsUrl`, `jsonObject`) enforced in frontend UI validation, frontend submit validation, backend schema publish validation, backend submit validation, and JSON Schema extension output. Arbitrary JS custom functions remain intentionally unsupported until a separate sandboxed rule-engine decision exists.
- [M7-P-B-fix resolved/watch] ShowItem true source data, sessions lowercase status binding, and C3 Designer nested drag are closed as the P-B-fix follow-up line. ShowItem now renders configured source data without entering answer payload; sessions `claimed` query no longer becomes a 500 and invalid status is narrowed to a controlled 400 via the scoped `SessionStatusRequestParamConverter` / `GlobalExceptionHandler` path; C3 adds recursive canvas rendering plus parent-aware palette/drop/move for nested_object children and tab children while preserving root palette drop and top-level reorder behavior. C3 is pure frontend and does not alter OpenAPI or migrations; frontend Vitest evidence is `225 / 0 / 0`, OpenAPI MD5 remains `587bdda9132cc8c932099ceda4e704b5`, migrations remain 17, and this append moves humanpending to 180. Browser/live dnd-kit nested mouse drag remains D-口径 in this environment and should be hand-verified in a Browser-capable session.
- [P-C resolved/watch] Multi-role human review flow closed in immediate commits `1e37e3e` through `adee570`, plus regression cleanup before closure: returned-for-revision resubmission creates a new submission and supersedes the old one; reject reason is mandatory; labelers can see the latest reviewer reject feedback; batch review writes per-item results; `review_actions` and audit payloads capture review transitions; export defaults to `approved_only` while retaining `full`; and owner-adjudicated two-tier review adds `SENIOR_REVIEWER`, with initial reviewer approve remaining pending until senior approve. ADR-005 is guarded by reviewer-only/senior-only final verdict derivation and AI evidence remains non-final; ADR-004 is guarded by export mode in snapshot dataScope/manifest/source hash and old snapshots are not rewritten. Backend verification on 2026-05-30: full API `611 / 0 / 90` with escalated Maven, focused P-C regression `112 / 0 / 0`, and frontend typecheck green. OpenAPI MD5 is `4a35f6083bc3be7c0e635fdbdd1668e7`, migrations are 18, and this append moves humanpending to 181. Reviewer/labeler UI visual flow remains D-口径 without browser hand-verification in this environment.
- [P-D resolved/watch] Labeler workspace enhancement closed in immediate commits `6c6f8b5`, `a62e1d0`, `2342d35`, plus regression closure: marketplace search/filter query parameters and card UI are implemented; session-to-session navigation stays within already claimed/returned/submitted sessions and does not touch claim/quota; labeler "my sessions" statistics now expose only senior-final reviewer verdict-derived approved/rejected/pending work status and do not treat AI recommendation or initial reviewer approve as pass; and the MyBatis marketplace XML regression was fixed by escaping the script-local quota comparison. Red lines held: no claim/quota/distribution changes, no migration added, and verdict display follows ADR-005 senior-final accountability. Verification on 2026-05-31: frontend Vitest `228 / 0 / 0`, frontend typecheck green, focused API SessionService `45 / 0 / 0`, and full API `613 / 0 / 90` with escalated Maven after the XML regression fix. OpenAPI MD5 is `7a53f0e1fb05950371fcb0af0d6650d1`, migrations remain 18, and this append moves humanpending to 182. Browser visual validation for marketplace cards, session navigation, and my-data filters remains D-口径 pending owner/manual browser review.
- [P-D sealed] Owner manual validation passed and P-D is sealed at `95/100 APPROVED`. This supersedes the browser D-口径 caveat in the prior `[P-D resolved/watch]` entry: marketplace search/filter/cards, session navigation, and my-data verdict/statistics UI have owner hand-verification coverage. Final P-D commit line is `6c6f8b5` / `a62e1d0` / `2342d35` / `959cbbe`; no claim/quota/distribution changes were made, no migration was added, and labeler-visible pass status remains senior-final reviewer verdict only. Final anchors remain OpenAPI MD5 `7a53f0e1fb05950371fcb0af0d6650d1`, migrations 18, and this append moves humanpending to 183.
- [P-E resolved/watch] Task management completion closed in immediate commits `14ef2ca` through `7e6c71f`, plus closure: owner task field editing now exposes/persists rich instructions and reward rules with draft/paused-only edit guard; tasks.status has DB CHECK constraint via migration 19 while `TaskStateTransitions` remains flow authority; dataset import supports JSON/JSONL/Excel `.xlsx` with formula cells rejected; dataset item preview and available-only batch edit update only `status='available'` rows with `item_hash` recompute and per-row locked skips; distribution strategy was left unchanged because first-come + quota claim already satisfied 4.1. ADR-004 is guarded by never mutating claimed/submitted dataset items and leaving export canonicalization unchanged; ADR-002 unaffected because schema immutability is not touched. Verification on 2026-05-31: focused API regression `112 / 0 / 0`, full API `623 / 0 / 90` with escalated Maven, and frontend typecheck green. OpenAPI MD5 is `43db91c07c501ac41fa2fbf780e0dd9e`, migrations are 19, and this append moves humanpending to 184. Owner UI visual flow, Excel upload interaction, and batch-edit table interaction remain D-口径 without browser hand-verification in this environment.
- [P-F resolved/watch] Multi-format export completion closed in immediate commits `34a2be1` through `a797ef8`, plus closure: immutable export snapshots now include CSV and Excel business-table files alongside the existing JSONL bundle; field mapping selection/renaming is captured in `fieldMappingSnapshot` and participates in manifest/source hashes; export requests are fully async through `outbox.event_type='export.requested'` with an independent export worker and event-type isolation from AI review; and snapshot file download reads only frozen object-storage files, increments `download_count`, and does not recompute or rewrite old snapshots. Red lines held: CSV/Excel business rows are sourced through `ExportFactCollector.collectForTask(taskId, APPROVED_ONLY)`, including full-mode compatibility snapshots; `OutboxAiReviewWorker` is untouched by export events; no synchronous controller path remains for owner export creation; and ADR-004/P-C approved-only boundaries are preserved. Verification on 2026-05-31: focused API export/outbox regression `34 / 0 / 0`, full API `632 / 0 / 90` with escalated Maven after sandbox socket denial, agent `6 / 0 / 0`, and frontend typecheck green. OpenAPI MD5 is `718f5438f596c3e597a51844967468d8`, migrations remain 19, and this append moves humanpending to 185. Browser UI download/field-mapping interaction remains D-口径 pending owner/manual browser validation.
- [P-F-fix R8] Hard snapshot delete rollback. Commit `1c795e2` added a `DELETE /exports/snapshots/{snapshotId}` path that physically deleted object-storage files through `deleteObject`, which violated ADR-004 immutable export snapshots and ADR-009 export snapshots as immutable evidence-critical objects. It has been reverted by commit `ecc5dee` (`M7-P-F revert hard delete snapshot (ADR-004)`); OpenAPI MD5 returned to `718f5438f596c3e597a51844967468d8`, the standalone delete endpoint is absent again, and future owner-visible removal must be implemented as archive/soft-delete rather than destruction. This append moves humanpending to 186.
- [P-F-fix resolved/watch] Export snapshot owner-visible removal is reimplemented as archive/soft-delete after the hard-delete rollback. Migration 20 adds `export_snapshots.archived_at`; owner list/count default to active snapshots (`archived_at IS NULL`) with an archived view; `POST /exports/snapshots/{snapshotId}/archive` only sets `archived_at` and records `export.snapshot_archive`; archived snapshots remain owner-scoped and downloadable by ID, with object-storage files, file hashes, manifest hashes, and snapshot content untouched. ADR-004/ADR-009 guard: the archive path adds no `DELETE FROM export_snapshots` and no snapshot object deletion; existing `deleteObject` usage remains limited to export failure cleanup, and pre-existing task-level hard delete is outside this archive path. Verification on 2026-05-31: frontend typecheck green and focused API export archive regression `31 / 0 / 0`. OpenAPI MD5 is `4467d7da2ac2b7f374edd02394bd70bd`, migrations are 20, and this append moves humanpending to 187. Browser UI for the archive button and archived-view toggle remains D-口径 pending owner/manual browser validation.
- [global-state resolved] Full ADR inventory backfilled into `docs/architecture/labelhub-complete-design-baseline.md`. This closes the earlier `[project-state watch]` / `[P-A update]` gap: the global state document now lists all numbered ADRs present under `docs/adr/` from ADR-001 through ADR-015, including ADR-006 function calling, ADR-008 outbox pattern, ADR-014 scoped zero pause, and ADR-015 OpenAPI contract drift control. `ADR-template.md` remains a template, not a numbered decision. This append moves humanpending to 188.
- [P8 resolved/watch] AI scoring calibration closed in immediate commits `34487dd` through `fe0c992`, plus closure: task-scoped AI review rules now support nullable `pass_threshold` / `reject_threshold` with validation `0 <= rejectThreshold < passThreshold <= 1`; scoring remains equal-weight but conclusion uses the three-zone rule (`>= passThreshold` pass, `<= rejectThreshold` reject, middle manual_review); scoringRuleVersion is bumped to `equal-weight-three-zone-v2`; and internal agent results are normalized by the API-side scoring authority so agent-provided recommendation is no longer trusted as conclusion authority. Legacy rules with null three-zone fields fall back to `threshold` plus the existing global rejectFloor, preserving the P-A no-rule/legacy numeric behavior. ADR-005 guard: P8 only changes `ai_calls.verdict`, AI recommendation payload, scoring provenance, and agent context; `reviewer_overall_verdict`, `VerdictService` final verdict, senior-approved export eligibility, and session final status remain reviewer/senior-derived. Verification on 2026-05-31: focused P8 API/agent regression `63 / 0 / 0` + agent `6 / 0 / 0`, full API `640 / 0 / 90` with escalated Maven after sandbox socket denial, frontend Vitest `228 / 0 / 0`, and frontend typecheck green. OpenAPI MD5 is `5102e4e97b9f842248aca651681b7b82`, migrations are 21, and this append moves humanpending to 189. Browser UI for the pass/reject threshold controls remains D-口径 pending owner/manual browser validation.
- [Backend Batch A resolved/watch] LLM Provider configuration and secure key storage closed in commit `9511662`: provider configs now persist in `llm_provider_configs` with AES-GCM encrypted secret storage, using independent master key env `LABELHUB_LLM_PROVIDER_MASTER_KEY` rather than JWT secret; Provider CRUD and test-connection APIs are owner-only through `@PreAuthorize("hasRole('OWNER')")`; response DTOs expose only `hasSecret`, `secretLast4`, `secretUpdatedAt`, and `secretRef`, never full key or ciphertext; secret input is write-only with no decrypt/display API; `SecretRedactor` and safe audit payloads close the research-identified audit/error secret-leak path; and the Owner LLM settings page now uses the real Provider APIs. Batch A is an ADR-011 extension for provider configurability only: it does not change AI review source-of-truth, `AiReviewService` and `FieldAssistService` have empty diff, and P-A continues to run from the existing env/config provider chain in parallel with the new key cabinet. Closure anchors: OpenAPI MD5 `7103f921bb1c578cff36b39985b0904e`, migrations 22, and this append moves humanpending to 190. Watch: Batch B must switch AI review from env/config provider to DB provider registry as the new source-of-truth, which touches the P-A bearing path and requires ADR-011 revision, owner裁决, and ADR backfill; Owner LLM UI provider add/update/delete/test and last4-only display remain D-口径 without真人手验; generated writeOnly handling should still be spot-checked later even though `LlmProviderConfigDtoMapper` already limits responses to safe fields.
- [Backend Batch B resolved/watch] AI review provider source-of-truth switch closed in commit `eedda98`: automatic AI review now resolves providers registry-first from `llm_provider_configs`, with the agent reading owner-scoped provider config, self-decrypting Batch A AES-GCM ciphertext using independent `LABELHUB_LLM_PROVIDER_MASTER_KEY`, and constructing the OpenAI-compatible runtime client only in memory. Secret safety held across the new agent-held-key surface: plaintext key is used only for the outbound `Authorization` header, never in request body, outbox payload, `ai_calls.request_payload`, quality ledger payload, audit payload, exception messages, logs, responses, or record `toString`; no API-to-agent plaintext key HTTP path was introduced. Fallback is registry-first: no enabled DB provider uses env/config fallback; exactly one enabled DB provider uses DB; multiple enabled providers and permanent config/auth/decrypt errors fail visibly through existing retry/dead-letter without silent env fallback; transient 5xx/timeout uses existing worker retry in Batch B v1, with post-exhaustion env fallback deferred until it can be proven not to hide permanent errors. P-A/P8 evidence semantics stayed unchanged (`AiReviewService`, `FieldAssistService`, `AiReviewScoringPolicy`, `AiReviewScoringProperties`, and `LedgerService` empty diff), ADR-005 remains intact, and ADR-011 is revised to registry-first runtime source. Verification: agent `21 / 0 / 0`; focused P-A/P8 API tests `64 / 0 / 0`; full `mvn test` remains D-口径 in this sandbox because local MySQL/socket provider tests hit `Operation not permitted`. Closure anchors: OpenAPI MD5 `7103f921bb1c578cff36b39985b0904e`, migrations 22, and this append moves humanpending to 191. Watch: real provider live-call smoke with safe credentials and any future transient-failure env fallback policy remain later validation work.
- [ ] UserAuth-merged 封板:本批后端测试 659 pass 系 owner 提权复跑(D-口径采信),审计师沙箱无 mvn 未独立复跑
- [ ] UserAuth-merged 封板:UI 视觉(注册页/授角色页/用户列表/登录入口)系 owner 手验(D-口径),审计师无 browser 未核视觉
- [ ] UserAuth-merged 封板:运行态 HTTP(注册201/列表鉴权403/无密码字段)系 owner curl 输出(D-口径采信),与静态 grep 一致
- [ ] UserAuth-merged 流程偏差:8 个 commit 直接滚 main 未走 feature 分支、范围由"注册+授角色"蔓延至含"登录入口+用户列表",事后以 9b1ca0c 一次性合并补审 PASS;下批起强制 feature 分支审后再 merge
- [ ] UserAuth-2 封板:Owner-only 账号软删除(UPDATE status='deleted'),审计 grep 实证 S1-S7 全 PASS,merge commit 58008a5 入 main
- [ ] UserAuth-2 封板:后端测试 664 pass 系 owner 提权复跑(D-口径采信),审计师沙箱无 mvn 未独立复跑
- [ ] UserAuth-2 封板:/admin/users 页 Owner 可见性 + 软删二次确认弹窗 + 按钮交互 UI 视觉手验为 D-口径未闭项,待 owner 浏览器确认
- [ ] UserAuth-2 已知限制(S7):软删非紧急封禁,被删账号旧 JWT 至过期(≤24h)仍有效;即时阻断/会话治理归 UserAuth-session 后续批次
- [ ] UserAuth-2 流程改进:全程 feature 分支 feat/userauth2-soft-delete 实现、审计 PASS 后 --no-ff 合入 main,纠正上批"8 commit 滚 main"偏差
- [ ] UserAuth-2 UI fix:用户管理页删除确认弹窗溢出截断修复(Popconfirm 挂 body + leftTop + autoAdjustOverflow + token 约束 class),纯前端样式无承重改动,feature 分支 fix/userauth2-confirm-popover 审计 PASS 后 --no-ff 合入 main(merge 2351de5),MD5 未变,owner 手验成功
- [ ] UserAuth-session 封板:refresh token + 服务端 logout 范式 A 合入 main(merge e7a5041, feature df3b712),refresh_tokens 仅存 hash,login/register 发 HttpOnly Secure SameSite=Strict Path=/api/auth cookie,/auth/refresh 轮换,/auth/logout 吊销,软删联动吊销 active refresh;主窗口已闭,残留为 access token ≤1h 内仍有效 + 降权未联动,留待即时阻断/会话治理后续批次
- [ ] UserAuth-username-reuse 封板:软删账号 username 复用闭环按路线乙合入 main(merge c0ac098, feature 026495c),通过 users.active_username VIRTUAL 生成列 + uk_users_active_username 实现仅 active username 唯一,注册查重改为 active-only,软删路径/UserDeletionService/softDeleteUserById 零改,JWT/SecurityConfig 零改;运行态证据:软删后同名注册成功且 id 不同,并发同名注册一成一败(201/409)
- [ ] dev-environment 标准化(chore/dev-environment 合入 main):新增 Makefile(doctor/dev-up/dev-down/verify/migrate-check)+ scripts/dev-up.sh(起库等 MySQL healthy)+ .tool-versions + .devcontainer(JDK17 固化, docker-outside-of-docker)+ docs/dev-environment.md;免 gate dev tooling, 零碰承重(src/migration/契约 diff 空), 三锚 a1e652ef/24/203 未变;揪出本机无真 JDK17(java_home -v 17 误命中 Corretto 11), doctor/verify fail-fast 拒绝用错版本;devcontainer 容器内跑绿待 owner 验证
- [ ] fix-login-active-only 封板:登录查用户改为 active-only(UserService 调 selectActiveByUsername,selectByUsername 保留未改),补 username-reuse 回归漏网;TDD 红灯复现 Optional empty→绿灯通过,运行态 774670647 同名 deleted+active 场景登录 HTTP 200 user.id=1022,merge 22d47a2
- [ ] PA-foundation 封板:PLATFORM_ADMIN 角色创世与治理收权合入 main(merge 459add6, feature 59af187),账户/授权/模型 key 管理由 Owner 迁入 PA-only,创世密码由 LABELHUB_PA_INITIAL_PASSWORD env 注入且缺失 fail-fast/不硬编码弱密码;PA 授权四条红线已测:可授 OWNER 但业务 UserRoleService 不破、拒 AI_AGENT、拒 PLATFORM_ADMIN、防自授;PA 操作审计 actorType=platform_admin,运行态证据含 PA 登录 mustChangePassword=true、Owner 访问 /llm 与旧授权端点 403、ROLE_GRANTED 审计留痕;密钥加解密/业务模块/JWT 热路径/UserRoleService 零碰
- [ ] PA-cost-dashboard-A 封板:PA Token 成本管控看板合入 main(merge 6f5942e, feature c7f3e78),提供总 token/总成本、按天/模型/任务/Owner 四维度聚合,成本只读 SUM(cost_decimal) 不重算单价,未归集成本单列诚实呈现;看板纯 SELECT 不碰 ai_calls 写路径/成本计算器/不可变与 evidence 链,无 migration;前端空状态区分"暂无 AI 调用记录"与 0 值统计,纯事实展示无绩效/效率/扣钱/排名解读;端点 PLATFORM_ADMIN-only,运行态证据 PA 200 空看板、Owner 403
- [ ] PA-metrics-C 封板:PA 人力计量 + Token 效率洞察C 合入 main(merge 7f8fbe3, feature 4dd3da4),人力计量按提交/审核聚合并将返工三口径(被取代/多轮审核/被打回)分列不加总;效率洞察提供 idempotency 复用与单位数据成本,重试消耗因无 retry 计数字段诚实降级 out-of-scope;全批纯 SELECT 不碰 submissions/review_actions/ai_calls 写路径,不读 answer_payload/comment_text/structured_reason/diff_snapshot 等业务内容字段,纯事实无绩效/排名/扣/拖后腿/建议处理解读;端点 PLATFORM_ADMIN-only,运行态证据 PA 200、Owner 403
- [ ] devcontainer Maven cache 修补:chore/devcontainer-maven-cache 审计 PASS 后 --no-ff 合入 main,预热 Maven/Surefire/JUnit 依赖并修复 Colima host.docker.internal 映射为 172.17.0.1;postCreate 实测约 72s→32s,MySQL localhost 握手成功,make doctor JDK 17.0.16,make verify 689 pass;纯 .devcontainer 改动,零碰承重,三锚未变
- [ ] PA-provider-platform-level 封板:LLM Provider 归属改为平台级(scope=platform),新增生成列 platform_enabled_key + 唯一键保证全平台最多一个 enabled provider,agent RuntimeProviderResolver 改为纯平台级解析并保留 env fallback;密钥加解密/master-key 配置、成本/人力/效率看板、PA/业务授权、JWT/Security 与 evidence 链零碰,修复 PA-foundation 中 PA 配置 provider 与 agent 按 owner 查 provider 的跨模块归属盲点(merge 9acefa8, feature 502c988,审计 PASS)
- [ ] AI-prereview-status 封板:AI 预审四状态显性化合入 main(merge 676572d, feature 1fa5fa1),由后端从 outbox + ai_calls + quality_ledger 只读派生 prereviewStatus 与窄 prereviewSignals,状态含待预审/预审中/预审完成/预审失败且复用因无 durable 信号诚实降级不硬编;全批不新增状态列、不加 migration、不碰 outbox/ai_calls/ledger/agent 写链路,派生查询不读 answer_payload/response_payload/scores/comment_text/structured_reason/diff_snapshot 等业务内容字段,前端仅渲染状态 tag 不做派生
- [ ] outbox-last-error 封板:outbox 新增 last_error 可空列并由共用 dead_letter 写点 markDeadLetter 写入,AI 预审与导出失败均可持久化失败原因;last_error 由 OutboxLastErrorBuilder 主动脱敏 key/auth/bearer/业务内容并截断 1000,仅改 dead_letter 写点,成功/claim/重试调度/ai_calls/ledger/provider resolver/密钥/platform 逻辑零碰;last_error 进入 prereviewSignals 供预审失败状态查看原因,历史 dead_letter 不回填(merge d37e68a, feature f7e4755,审计 PASS)
- [ ] reviewer-queue-reviewlevel-fix 封板:真实 e2e 暴露 reviewer 队列 reviewLevel 参数绑定 bug,补 ReviewLevel @RequestParam Converter(@Component + fromValue 同款范式),审核写入/权限/接口签名/AI 预审派生零碰,真实接口不带/reviewer/senior_reviewer=200 且 foo=INVALID_QUERY_PARAMETER,merge 9920220
- [ ] ai-verdict-dual-layer 封板:AI 结论双层呈现合入 main,Reviewer 详情页新增 AI 综合判定（建议/非最终）与人工最终裁决两层卡片,AI 层取 ai_overall_recommendation 展示 pass/0.95/阈值/维度与字段 finding,人工层取 /verdict 与 reviewer_overall_verdict 展示 approved 最终裁决;全批纯前端 apps/web,后端/契约/generated schema/migration 零改,真实接口证据 ledger-entries + verdict 已核
- [ ] labeler-task-detail-drawer 封板:Labeler 任务详情 Drawer 合入 main,纯前端只用 marketplace item 展示任务标题/说明/标签/截止/奖励规则/配额/可领取量,schema 摘要按 owner 裁决降级;labeler-safe 守住,不调 OWNER-only getTask 或 /schemas,不展示答案/schemaJson/AI/别人提交/reviewer 内容;领取按钮复用现有 claim 流程并跳转作答页,后端/契约/migration/generated schema 零改,前端 257 tests pass
- [ ] schema-template-wording 封板:UI 话术统一 Schema→模板（Schema）合入 main,只改 4 个 owner 页面用户可见显示文案并同步测试断言,标识符/className/API 路径/领域模型零改,纯前端后端/契约/generated schema/migration 零改,OpenAPI MD5 未变,前端 257 tests pass
- [ ] reviewer-render-context-fix 封板:真实 UI 暴露 reviewer 看不到题目原文/模型回答/参考答案 bug,根因是 /submissions/{id}/render-schema 未返回 datasetItem;修法为后端在 render-schema 中附带 datasetItem(权限不放宽、纯 SELECT 不写表)并让 reviewer/owner/labeler submission 历史页把 itemPayload 喂给现有 show_item 渲染器,Labeler 作答页与 show_item 内核零碰,真实接口三角色验证通过(merge 5332d8d, feature 60beb0f,审计 PASS)
- [ ] schema-designer-E1 封板:Schema Designer 细节优化合入 main(merge b819a32, feature 230f2ca),顶部未发布提示改轻量 inline 且可当前会话关闭,字段删除按钮 hover/focus 显形并经 Popconfirm 二次确认后才删除;全批纯前端,只动 schema-design 与直接页面测试,contract/generated/后端/migration/labeling 零碰,OpenAPI MD5 与 migrations 未漂,前端 typecheck + 260 tests pass
- [ ] schema-designer-A1-linkage-builder 封板:联动可视化构造器合入 main(feature bb59aa0 + dd0a8b4),纯前端只在 schema-design 侧新增 atomic field/op/value 构造器,op 覆盖 eq/neq/in/notIn/gt/gte/lt/lte/empty/notEmpty,数值 op 仅允许 number 字段,empty/notEmpty 不写 value,高级 JSON 折叠保留 group 且 A8 高级分组占位禁用;一次预览联动异常经三次 live 复测与 dd0a8b4 真绿回归测试未复现,归现场瞬态留痕,R6 盲区已由预览层真实 DOM 显隐测试堵住;contract/generated/后端/migration/labeling 零碰,前端 266 tests pass
- [ ] schema-designer-E2 封板:草稿态视觉一致性合入 main(merge e3c9f5d, feature 70d3175),页头 pill 改为真实三态:尚未发布/已发布 vN/有未发布修改·基于 vN,会话告警改由 isDirty && isSessionNoticeVisible 显隐且 E1 关闭逻辑与样式零回退;全批纯前端仅改 OwnerSchemaDesignerPage 与测试,contract/generated/后端/migration/labeling 零碰,OpenAPI MD5 与 migrations 未漂;live 验证已发布 v1 初始无告警、改字段后草稿态+告警、关闭后不弹回、发布后已发布 v2+告警消失、临时未发布 schema id=13 显示尚未发布且已清理
- [ ] schema-designer-C-material-grouping 封板:物料区按 4 组分段合入 main(merge cd9bde12, feature d4de44b3),4 组为只读材料(show_item)、选择与约束(single_select/multi_select/date)、内容录入(text/number/rich_text/file_upload)、容器与高级组件(nested_object/tab_container/json_editor/llm_interaction);12 项物料无遗漏且有未分组兜底,SCHEMA_FIELD_TYPES 与 SCHEMA_FIELD_TYPE_LABELS 原数组/标签源零改,dnd 仍用 palette:${type} 且拖 text 仍触发 onAddField('text'),designerDragModel/FieldTypePicker/FieldList 零碰;全批纯前端仅改 DesignerFieldBuilder 与 dnd 测试,contract/generated/后端/migration/labeling 零碰,OpenAPI MD5 与 migrations 未漂,前端 typecheck + 270 tests pass
- [ ] schema-preview-PREVIEW-FIX 封板:预览联动可靠化合入 main(merge 2d4fdad, feature 72739456 + b461708a),SchemaFormilyPreviewPanel 复用真实作答页 selector 预过滤可见字段并用 form.values 50ms 快照轮询同步预览值,修复 group allOf/anyOf 在 Designer 预览不生效;三案同源留痕:A1 幽灵失败、72739456 真实 Semi 失效与本批 group 预览失败均收口到预览 onChange 防抖回写链不可靠,b461708a 以 selector 预过滤 + form.values 轮询双修绕开;真实作答页源码/adapter/renderer/selector 本体零碰且 session 18 live 抽验不回归,预览 allOf/anyOf/atomic 与 reset 后联动响应 live 全绿;轮询路径暂无自动化覆盖,依赖 live 背书,后续 preview 测试升级时补;全批白名单内仅改 preview 面板与 previewLinkage 集成测试,contract/generated/后端/migration/schema-design/labeling 核心零碰,前端 typecheck + 275 tests pass
- [ ] schema-designer-A2-linkage-group 封板:联动条件组可视化合入 main(merge eede45bf, feature 44e89a2a, 验证 merge 19f2d056),构造器新增显式单条件/条件组模式,一层 group 输出 allOf 或 anyOf 且 AND/OR 强制互斥,组内多条原子条件复用 A1 field/op/value 规则,单条件添加条件可自然升级为 group,多条 group 切回单条件必须确认且 allOf/anyOf 文案分支正确(取消不改、确认只留首条);关键留痕:首验失败经实证定位为 Designer 预览链而非 A2 构造器,PREVIEW-FIX 插批修复后 A2 原代码零改重验通过;live 七项全绿(allOf 双满足显示、anyOf 任一满足显示、升级路径、互斥、确认弹窗、JSON 折叠可编辑且同步、A1 atomic 不回退),linkageEvaluator/schemaValidation/schemaTypes/FieldEditor/LinkageJsonEditor 零碰,contract/generated/后端/migration/labeling 零碰,前端 279 tests pass
- [ ] schema-designer-LAYOUT-4col 封板:四栏等高布局合入 main(merge 924e1d79, feature 3d272c64),Designer workspace 从三栏+inspector-stack 改为四个 grid 直接子项(物料/画布/字段属性/预览),列配比约 0.5/1/0.75/0.85 且 height 使用 clamp(620px,100vh-250px,860px) 统一等高,四栏独立 overflow-y 滚动;画布列压回合理宽度不再 1.28fr 霸宽,inspector-stack JSX 与 CSS 死代码双零命中,中屏退化 2x2 等高行、窄屏单列;live 验证四栏底齐(826/1012)、列宽约 274/549/412/467、独立滚动成立,拖物料加字段 11->12、选中出属性、联动构造器与预览联动均不回退;全批纯布局仅改 OwnerSchemaDesignerPage/styles/test,数据流/props/handler 零改,schema-design 组件逻辑与 labeling 预览面板本体零碰,contract/generated/后端/migration 零碰,前端 typecheck + 279 tests pass
- [ ] schema-designer-D-rename 封板:物料命名对齐合入 main(merge dc75cc01, feature bf2d47ea + 6288b632),SCHEMA_FIELD_TYPE_LABELS 三值改为 nested_object=字段分组、tab_container=标签页组、llm_interaction=AI 交互,其余 9 个标签与 type 枚举值零改;补刀同步 NestedObject/TabContainer/LlmInteraction 属性面板标题与帮助文案、Owner LLM 设置页说明文案,清除同屏旧名不一致;live 验证物料区三处新名同在容器与高级组件组、字段分组属性面板同名、/platform/llm 显示支持任务负责人加入 AI 交互字段;D2 挂账保留 schemaValidation reason 字符串 4 处、LabelHubTabsContainer fallback 2 处及设置页行标题 Designer LLM 字段可选统一;schemaValidation 与 labeling 全程零碰,契约/generated/后端/migration 零碰,前端 typecheck + 279 tests pass
- [ ] schema-designer-D2-copy 封板:文案收尾合入 main(merge D2, feature 80f8997c + 73d2b5db),schemaValidation 红线文件按 owner 裁决做字符串级破例,仅 4 对 reason 文案改为字段分组/标签页组口径且逻辑/结构/类型/空白零改;Owner LLM 设置页行标题与 Provider 描述统一为 Designer AI 交互字段,测试断言同步,该页旧词零残留;live 验证空字段分组校验真显示"字段分组需要至少一个子字段",/platform/llm 两处显示 AI 交互字段;最终保留项仅 LabelHubTabsContainer fallback 2 处(labeling 红线,field.label 缺失兜底,命名对齐就此收口);LabelHubTabsContainer/labeling、契约/generated/后端/migration 零碰,前端 typecheck + 279 tests pass
- [ ] outbox-last-error 脱敏 live 验证留痕:采用 canary sk-FAKE-CANARY-1234567890abcdef 故意注入本机假 Provider 401 响应体,验证 dead_letter last_error 脱敏;outbox id=9 走真实 agent->provider 401 路径,status=401 且 providerBody 中 key 被 [redacted],canary/Authorization/Bearer 命中计数均为 0;首轮 outbox id=8 因本机假 Provider framing 触发 io_error,同样未泄 canary,作为顺带覆盖留痕;护栏已回滚,Provider id=2 deepseek enabled 快照恢复为 1,pending outbox 4-7 保护后 next_retry_at 恢复原值,agent 与本机假 Provider 均已停止;dead_letter 8/9 保留作验证痕迹不清理,全程不改代码/schema/契约
- [ ] SWITCH-BE activate 封板:平台 Provider 激活切换合入 main(merge ca035b26),契约新增 POST /llm/providers/{providerConfigId}:activate 且复用既有 LlmProviderConfig 返回;服务端以单 @Transactional 先禁用其他 platform provider 再启用目标 provider,避开"先启新撞唯一 enabled key / 先禁旧暴露 0 enabled 窗口"双向死路,live DB 验证 enabled_count 全程恢复为 1;无密钥来源目标 activate 拒绝为 provider_secret_missing,secret_ciphertext 或 secretRef 任一存在才可激活;审计 action=llm_provider_activated,payload 仅含 targetProviderConfigId 与 disabledProviderConfigIds 等 id/布尔信息,SecretRedactor 兜底且 live canary/Authorization/Bearer 命中 0;live 七步使用隔离 18080 API 与 18081 agent,临时 provider id=3(qwen)无密钥 400、补 canary key 后 200 并禁用 id=2,agent 消费隔离 outbox id=10 后返回阿里云 Model Studio 401 链接铁证,证明请求打到 qwen 端点而非 deepseek,随后切回 id=2 并删除测试 provider,pending outbox 4-7 保护后恢复;SecurityConfig 既有 /llm/providers/** 与 controller @PreAuthorize 双层鉴权覆盖,services/agent 与 migrations 零改,前端业务除 generated/schema.d.ts 零碰,密钥链路不解密/不记录/不搬运;OpenAPI MD5 预期漂移 e05270bf -> cceb44d9,新值成为后续锚点,migrations 仍为 27,后端 705 tests 与 gen:api 为 D-口径通过
- [ ] SWITCH-FE cards 封板:Provider 卡片列表与切换合入 main(merge 7461d17, feature 0f4b5821 含返修),平台 LLM 接入页改为卡片列表+使用中 badge+确认切换+无密钥防呆+当前 Provider 删除保护,新建 Provider 使用五厂+自定义图标网格并逐字沿用五厂预设(OpenAI/DeepSeek/Qwen/豆包/Claude,DeepSeek 不含 reasoner,providerType 恒 openai-compatible),activate mutation 只走 generated typed client activateLlmProvider 且成功后 invalidate providers query;关键留痕:首版 a06be2d0 图标为手绘占位,live 暴露后返修为 lobe-icons MIT 真实 SVG path,并以官方源文件逐字比对坐实真实品牌图标,视觉资产审计必须包含渲染确认;Base URL/模型标签和值左对齐返修完成;live 通过隔离 5175->18080 当前分支服务创建 no-key 与 qwen 临时 Provider,无密钥卡设为当前 disabled、enabled deepseek 删除 disabled,UI 双向切换 qwen->deepseek 后 DB enabled 恒为 1 且最终只剩 id=2 deepseek enabled,临时 Provider 已删除;契约/generated/后端/services/migration/labeling 零碰,工作树干净,typecheck 与 284 tests 为 D-口径通过
- [ ] UI-POLISH 封板:九项细节调整合入 main(merge UI-POLISH, feature bfc24b38),owner-driven 清单 1-9 收口:LLM 接入页删除页头重复新建 Provider 入口、配置已保存胶囊轻量化、厂商下拉图标与文字居中且间距 2ch,Token 成本页小节标题降级并与说明左端对齐、四个指标胶囊内容居中、按天趋势条形间距收紧且日期左对齐、右侧数值列与表头居中,人力计量页同类指标卡/小节标题/数字列统一,标注作答页左右栏等高底齐并优化右上未保存/提交操作胶囊去重影;第 10 条 reviewer 详情页工作台布局已在迭代中撤销且经本地 diff 实证未入码;本批纯样式/文案,运行页 .tsx 零碰、逻辑/状态/数据流零改,契约/generated/后端/services/migration/labeling 零碰,ZERO_POLISH_0;审计留痕:本批曾因全文 zip 审计误判既有 reviewer-* 样式为新增,已用本地逐行 diff 修正,后续 polish 类批次以 diff 审计为准
