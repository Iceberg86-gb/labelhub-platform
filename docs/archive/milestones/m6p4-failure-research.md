# M6-P4.0 Failure Path Research

> M6-P4.0 is a decision phase. It does not implement retry, timeout, failed-call persistence, or export cleanup. It records current failure behavior so M6-P4 can be scoped from evidence instead of intuition.

## 1. AI Provider Failure Paths

### Physical Evidence

| Location | Evidence | Current behavior |
|----------|----------|------------------|
| `AiReviewService.review` lines 93-124 | `@Transactional`; idempotency check happens before provider invocation; `metrics.recordMiss(...)` is called at line 123 before `invokeProvider(...)` | A logical miss is counted before the provider attempt starts. |
| `AiReviewService.review` lines 130-151 | `AiCallEntity` is built and inserted only after `invokeProvider(...)` returns successfully | Provider failure throws before any `ai_calls` row is inserted. |
| `AiReviewService.invokeProvider` lines 194-199 | Catches `AiProviderException` and throws `AiProviderFailureException` | The provider diagnostic object is not persisted. |
| `OpenAiCompatibleProvider.invokeWithUsage` lines 77-83 | Timeout, IO, and interrupted calls throw retryable `AiProviderException` with codes `timeout`, `io_error`, `interrupted` | Transient failure classification already exists. |
| `OpenAiCompatibleProvider.providerHttpError` lines 181-188 | HTTP >= 500 is retryable; 4xx is not retryable | Retry semantics are partially encoded in `AiProviderException.retryable`. |
| `OpenAiCompatibleProvider.parseJsonObject` lines 210-215 | Invalid JSON throws non-retryable `AiProviderException` code `invalid_json` | Provider response parsing failure is terminal. |
| `GlobalExceptionHandler.aiProviderFailure` lines 116-119 | Maps provider failure to HTTP 502 `AI_PROVIDER_FAILURE` with public message `AI provider unavailable` | User sees a controlled public error, but no persistent evidence row. |

### Current Failure Taxonomy

- **Retryable**: timeout, IO/network failure, interrupted request, HTTP 5xx.
- **Non-retryable**: HTTP 4xx, missing choices/message/content, invalid provider JSON, request serialization failure.
- **User-visible result**: HTTP 502 `AI_PROVIDER_FAILURE`.
- **DB result**: no failed `ai_calls` row, no `ai_calls_in_field`, no ledger entry.
- **Metrics result**: M6-P3b miss counter increments before provider failure.

### Key Gap

M6-P3b now records a miss for provider failure, but the database has no corresponding `ai_calls` fact. This is not a correctness bug for current behavior, but it prevents later analysis from answering: "How many misses failed before producing a provider result?"

## 2. Idempotency x Failure Interaction

### Physical Evidence

| Branch | Location | Metrics | Provider call? | DB write? |
|--------|----------|---------|----------------|-----------|
| HIT | `AiReviewService.review` lines 110-117 | `recordHit` before return | No | Reuses existing row |
| MISMATCH | lines 110-120 | `recordMismatch` before throw | No | None |
| MISS | lines 123-124 | `recordMiss` before `invokeProvider` | Yes | Only after provider success |

### Observations

1. Current metrics are **logical review attempt** metrics, not provider-attempt metrics.
2. If retry is added inside the MISS branch, `recordMiss` should not automatically become "number of retry attempts" unless explicitly redesigned.
3. A retry loop that re-checks `selectByIdempotencyKey` on every attempt could convert a retry into a HIT if a concurrent request commits the row. This is safer for concurrency but complicates metrics interpretation.
4. A retry loop that does not re-check idempotency preserves one logical miss per user request, but can duplicate provider calls in a narrow concurrent race.

## 3. Export Failure Paths

### Physical Evidence

| Location | Evidence | Current behavior |
|----------|----------|------------------|
| `ExportService.createSnapshot` lines 50-67 | `@Transactional`; inserts `export_jobs` row with `status="created"` before artifact writing | SQL rows roll back if a later runtime exception escapes. |
| `ExportService.createSnapshot` lines 74-79 | Writes each artifact file and `manifest.json` via `storageWriter.putObject(...)` | Object storage writes occur inside SQL transaction scope but are not transaction-aware. |
| `ExportService.createSnapshot` lines 98-100 | Catches runtime exceptions and wraps as `ExportFailureException` | SQL transaction rolls back; written objects remain unless cleaned manually. |
| `ObjectStorageWriter.putObject` lines 19-25 | Calls S3 `putObject`; no delete method exists | Current writer cannot clean up partial PUTs. |
| `ExportJobMapper` lines 18-28 | Insert-only mapper; no update status method | Failed jobs are not persisted today. |
| V1 schema lines 271-287 | `export_jobs.status` exists with default `created`; index by `(task_id,status)` | Status column exists, but sync export only writes `created`. |
| `GlobalExceptionHandler.exportFailure` lines 152-155 | Maps failure to HTTP 500 `EXPORT_FAILED` | User sees controlled error; failed job row is rolled back. |

### Current Failure Taxonomy

- **SQL-only failure before object writes**: clean rollback, no object residue.
- **Object write failure after some PUTs**: SQL rollback, previous objects remain orphaned.
- **Manifest serialization failure after content PUTs**: SQL rollback, all content files remain orphaned.
- **Snapshot insert failure after all PUTs**: SQL rollback, full object prefix remains orphaned.

### Key Gap

M5 already documented that object storage cannot participate in the SQL transaction. M6-P4 must decide whether to handle cleanup inline, as background reconciliation, or as a documented operational risk.

## 4. Other Failure Surfaces

| Surface | Evidence | Current failure behavior | M6-P4 relevance |
|---------|----------|--------------------------|-----------------|
| Dataset upload | `DatasetsController.uploadDataset` lines 52-63 catches multipart `IOException`; `DatasetImportService.importDataset` lines 51-85 is transactional SQL only | Parse/read failures produce controlled dataset errors; SQL inserts happen after parse succeeds | Not priority for M6-P4; no object storage residue. |
| Task transitions | `TaskService.transition` lines 99-118 writes transition + audit + task status in one transaction | Any DB failure rolls back together | Not priority; no remote resource. |
| Audit log writes | `TaskService.transition` lines 113-114 requires audit insert before status update | Audit failure blocks transition rather than best-effort | Not M6-P4 unless audit durability policy changes. |
| Schema/session operations | Transactional SQL-only flows | Rollback is enough at current scale | Not priority. |

## 5. Cross-Phase Failure Semantics Matrix

| Concern | Current phase origin | Current state | M6-P4 decision pressure |
|---------|----------------------|---------------|-------------------------|
| `AiProviderException.retryable` | M3 | Exists but unused by retry logic | Decide retry/backoff behavior. |
| Failed `ai_calls` facts | M3/M5 humanpending | Explicitly deferred | Decide persistence shape. |
| `labelhub.ai.idempotency.miss` | M6-P3b | Counts logical MISS before provider invocation | Decide retry metrics separation. |
| Token/cost persistence | M6-P3a/P3a-2 | Only success path writes tokens/cost | Decide failed-row cost/token semantics. |
| Export orphan objects | M5 | Documented operational gap | Decide cleanup strategy. |

## 6. Ten Decision Questions

### Q1. Should AI provider failure record a failed `ai_calls` row?

**Evidence:** `AiReviewService.review` inserts `ai_calls` only after provider success (lines 130-151). Provider failure is thrown from `invokeProvider` (lines 194-199) before insert.

- **A. Do not record failed rows.** Simple and preserves current rollback behavior, but misses remain observable only in metrics/logs.
- **B. Record failed `ai_calls` row outside the main transaction.** Gives append-only evidence for failed provider attempts and aligns miss metrics with DB facts, but needs a new write path and failed-row payload semantics.
- **C. Insert partial row inside the main transaction and commit before throwing.** Easier to query, but splits transaction discipline in a hard-to-reason way.

**Recommendation:** B, but only if M6-P4 is split into AI-failure scope first. Failed provider attempts deserve facts, and M3 already reserved `failed` status.

### Q2. Should `AiCallStatusCodes` be introduced?

**Evidence:** V1 `ai_calls.status` defaults to `created`; success path writes literal `"completed"` at `AiReviewService.review` line 147. Decision-log M3 says completed/failed were intended terminal states.

- **A. Minimal constants: `COMPLETED`, `FAILED`.** Low churn and aligns with M3 terminal-state decision.
- **B. Detailed status codes: `FAILED_TIMEOUT`, `FAILED_4XX`, `FAILED_5XX`, `FAILED_PARSE`.** Easier dashboards, but status becomes provider taxonomy.
- **C. Keep status coarse and put failure class in payload/diagnostic columns.** Requires deciding where diagnostics live.

**Recommendation:** A plus diagnostic fields/payload if Q1 chooses failed rows. Keep `status` terminal and simple.

### Q3. Should AI timeout become configurable?

**Evidence:** `AiReviewService.invokeProvider` passes `Duration.ofSeconds(30)` (line 196); provider also defaults to 30s when request timeout is null.

- **A. Keep hardcoded 30s.** Zero churn, but operationally rigid.
- **B. Add global `labelhub.ai.timeout-seconds`, default 30.** Simple and testable.
- **C. Add per-provider timeout under `labelhub.ai.openai-compatible.timeout-seconds`.** More precise, but slightly more config surface.

**Recommendation:** C. Timeout is provider behavior, and existing provider config already owns base URL/model/cost.

### Q4. How many retry attempts?

**Evidence:** `AiProviderException.isRetryable()` exists, but no retry loop consumes it.

- **A. No retry / one attempt.** Current behavior; simplest.
- **B. Fixed 3 attempts.** Practical default, but hardcoded.
- **C. Config-driven max attempts, default 3.** Operationally clear and testable.

**Recommendation:** C with default 3. Retry is a robustness control and should be configurable.

### Q5. What backoff strategy?

**Evidence:** No current backoff component exists.

- **A. Fixed interval.** Simple but synchronized retries can stampede.
- **B. Exponential backoff.** Better default without randomness.
- **C. Exponential backoff with jitter.** Production-grade, but harder to test deterministically.

**Recommendation:** B for M6-P4. Add jitter later only if real load needs it.

### Q6. Which failures are retryable?

**Evidence:** Provider already marks timeout/IO/interrupted/5xx retryable and malformed response/4xx non-retryable.

- **A. Retry everything.** Dangerous; repeats bad requests.
- **B. Retry only `AiProviderException.isRetryable()`.** Uses existing M3 diagnostics cleanly.
- **C. Config-driven retryable provider codes.** Flexible but too much surface for M6-P4.

**Recommendation:** B. Consume the existing provider contract instead of inventing a second taxonomy.

### Q7. Retry and idempotency semantics?

**Evidence:** Idempotency check happens once before provider invocation; miss counter is recorded once before invocation.

- **A. Re-check idempotency before every retry.** Concurrency-safe but complicates hit/miss accounting.
- **B. First miss owns retry loop; retries invoke provider without re-check.** Keeps one logical review attempt, simpler metrics.
- **C. Retry is outside `AiReviewService` as a provider wrapper.** Hides retry from service, but makes failed-row persistence harder.

**Recommendation:** B for M6-P4. Treat retry as attempts inside one logical MISS, not as new reviews.

### Q8. Retry metrics semantics?

**Evidence:** M6-P3b counters are logical idempotency outcomes, not provider-attempt counters.

- **A. Increment miss for every retry attempt.** Overstates user-level misses and breaks M6-P3b hit ratio.
- **B. Keep miss at one per logical review.** Preserves M6-P3b semantics but hides retries.
- **C. Keep miss at one and add separate retry counters.** Best observability without corrupting hit ratio.

**Recommendation:** C. Add `labelhub.ai.provider.retry` or equivalent only in M6-P4 if retry is implemented.

### Q9. How should export object-storage residue be handled?

**Evidence:** `ObjectStorageWriter` has only `putObject`; `ExportService` wraps failure but does not delete written object keys.

- **A. Ignore and document.** Current behavior, but leaves known orphan objects.
- **B. Inline cleanup: track written keys and delete them in catch before rethrow.** Deterministic for sync export, needs delete method and tests.
- **C. Background/startup orphan scanner.** More complete but significantly larger.

**Recommendation:** B. M5 export is synchronous and already has the object key prefix in scope.

### Q10. Should export failures be persisted?

**Evidence:** `export_jobs.status` exists but `ExportService` inserts job inside a transaction and rollback removes the row on failure.

- **A. Keep current behavior: exception only.** Simple; cleanup can still be inline.
- **B. Persist failed `export_jobs` with status `failed`.** More evidence, but requires transaction boundary changes and UI/API decisions.
- **C. Log + metric only; no failed job row.** Observable without expanding export schema behavior.

**Recommendation:** A for M6-P4 if Q9 chooses inline cleanup. Persisted failed export jobs can be an async-export phase, not necessary for sync cleanup.

