# Backend Batch B Scope-Budget: AI Review Provider Registry Source Of Truth

## Status

Pre-estimate gate for Backend Batch B. No implementation code has landed in
this batch.

Current branch: `codex/backend-llm-provider-config`.
Current anchor: `d50cb72`.
OpenAPI MD5 remains `7103f921bb1c578cff36b39985b0904e`; migrations `22`;
humanpending `190`.

Batch A built the LLM Provider "key cabinet": encrypted provider config
storage, Owner-only CRUD/test-connection API, and the Owner LLM connection page.
Batch B is the first runtime switch: automatic P-A AI review resolves its
runtime provider from the DB registry first, while preserving env/config
fallback for owners who have not configured a DB provider.

## Phase Character

Batch B is highest-bearing and security-sensitive:

- changes the P-A AI review provider source-of-truth;
- revises ADR-011 from env/config-only runtime source to registry-first runtime
  source;
- lets `services/agent` hold `LABELHUB_LLM_PROVIDER_MASTER_KEY` and decrypt DB
  provider secrets locally;
- adds minimal real provider runtime in `services/agent`;
- must not change ADR-005, AI evidence semantics, idempotency, scoring, ledger,
  or reviewer adjudication behavior.

The batch is deliberately narrow: switch "who is called" for the model call. Do
not change "what the AI evidence means" or "how evidence is stored/scored."

## Research Premises To Re-Verify Before Implementation

- Automatic AI review execution is in `services/agent`, centered on
  `OutboxAiReviewWorker`.
- Batch A secret decryption exists in `services/api`; `services/agent` must not
  call API to receive plaintext keys over HTTP.
- Agent runtime currently has fake/local provider behavior but no production
  OpenAI-compatible/Doubao client.
- Batch A provider table is owner-scoped and does not express provider priority
  or fallback order.
- P-A/P8 evidence chain is the no-touch boundary: idempotency, retry/dead-letter
  behavior, failed-call recording semantics, P8 three-zone scoring thresholds,
  quality ledger payload semantics, `ai_overall_recommendation`, and review UI
  evidence display must remain behaviorally unchanged.

## Owner Decisions For Batch B

### Cross-Service Secret Architecture

Use architecture A: agent self-decrypts.

- `services/agent` reads the owner provider config from DB.
- `services/agent` uses the same encryption format and
  `LABELHUB_LLM_PROVIDER_MASTER_KEY` to decrypt.
- Plaintext key exists only in agent memory long enough to build/call the
  provider client.
- Plaintext key must not be put into outbox payloads, `ai_calls`,
  `quality_ledger_entries`, audit payloads, exception messages, logs, responses,
  or frontend-visible data.

Reject architecture B for Batch B: API must not return plaintext keys to agent
over internal HTTP.

### Runtime Fallback Policy

Use registry-first plus env fallback:

- owner has no enabled DB provider: use existing env/config provider path so AI
  review is not interrupted;
- owner has exactly one enabled DB provider: use the DB provider;
- DB provider has permanent configuration/auth/decrypt errors: do not silently
  fallback to env/config, because that hides a broken new configuration;
- DB provider has transient network/5xx/timeout errors: retry through existing
  worker policy, then allow env fallback only if the implementation can record a
  safe provider-source diagnostic without changing evidence semantics.

### Provider Ordering

Batch B v1 should not add provider order/fallback-order schema.

Recommended rule:

- exactly one enabled DB provider per owner is the supported runtime shape for
  Batch B;
- zero enabled DB providers means env fallback;
- multiple enabled DB providers is a permanent configuration error for runtime
  resolution, not an env fallback.

If owner requires multiple DB providers with explicit fallback order in this
batch, stop and rescope with migration `22 -> 23`.

### Minimal Real Runtime

Add minimal agent-side OpenAI-compatible chat-completions runtime:

- use provider config endpoint/model/key;
- keep fake/local provider for tests and local development;
- treat Doubao as OpenAI-compatible if the configured endpoint works with the
  same request/response contract;
- stop and rescope if Doubao requires a broad bespoke adapter beyond a minimal
  compatibility shim.

## Allowed Surfaces

Agent runtime:

- new provider config reader/repository under `services/agent`;
- new agent-side decryptor compatible with Batch A ciphertext format;
- new runtime provider resolver;
- new OpenAI-compatible provider client and focused tests;
- worker integration limited to provider resolution before the model call;
- agent config for `LABELHUB_LLM_PROVIDER_MASTER_KEY` and safe env fallback
  provider settings;
- secret redaction helper/test guard for agent logs/errors/diagnostics.

Docs:

- ADR-011 revision or a new ADR that explicitly supersedes the runtime
  source-of-truth section of ADR-011;
- implementation closure docs after the implementation batch, if separately
  requested.

Tests:

- agent resolver/decrypt/fallback/security tests;
- focused regression tests proving no key leaks into durable payloads/loggable
  diagnostics;
- focused no-diff checks for P-A/P8 evidence surfaces.

## Forbidden Surfaces

Strictly do not change these in Batch B:

- ADR-005 semantics: AI recommendation remains evidence only, never final
  adjudication;
- `services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java`
  evidence handling, idempotency key format, scoring, failed-call recorder
  behavior, ledger writes, and DTO mapping semantics;
- `services/api/src/main/java/com/labelhub/api/module/ai/service/FieldAssistService.java`;
- `services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewScoringPolicy.java`;
- `services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewScoringProperties.java`;
- `services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java`
  evidence type semantics;
- review queue/UI evidence meaning;
- Batch A provider management OpenAPI response safety guarantees;
- provider CRUD API response shape unless a separate owner gate expands it.

OpenAPI should remain unchanged in the recommended scope.

## Scope

### 1. Agent RuntimeProviderResolver

Add an agent-side resolver that determines runtime provider source per AI review
job.

Required behavior:

- derive owner context without putting owner secrets into outbox payloads;
- preferred lookup: use task/submission context already available to the worker
  and DB query owner-scoped provider config;
- when zero enabled DB providers exist for the owner, return env fallback
  provider source;
- when exactly one enabled DB provider exists, decrypt and return DB provider
  source;
- when multiple enabled DB providers exist, fail with a safe configuration error;
- when DB provider secret is missing/malformed or decrypt fails, fail with a
  safe configuration error and do not env-fallback;
- resolver diagnostics must include provider source/category, not key material.

No OpenAPI call should be introduced for agent to fetch plaintext secrets.

### 2. Agent Decryptor And Master Key

Add agent-side decryptor compatible with Batch A encryption format.

Requirements:

- master key comes from `LABELHUB_LLM_PROVIDER_MASTER_KEY`;
- do not reuse JWT/object-storage/runtime secrets;
- missing/malformed master key produces a predictable startup or first-use
  failure;
- decrypt only inside the provider resolution/call path;
- do not expose a decrypt/display API;
- plaintext key lifetime is local to provider client construction/call.

Implementation should avoid a runtime dependency from `services/agent` on
`services/api` internals unless the repository already has a clean shared Java
module. Prefer a small compatible agent decryptor plus compatibility tests.

### 3. Secret Redaction Guard

Batch B creates a new leakage surface because agent will hold the master key.
Redaction is a hard gate item.

Required guard:

- a reusable agent-side redactor for fields named or shaped like `secret`,
  `apiKey`, `authorization`, `bearer`, `ciphertext`, and submitted provider key
  values; or
- focused tests proving all Batch B diagnostics/loggable/error paths exclude the
  real key value.

Required negative checks:

- outbox payload does not contain plaintext key;
- `ai_calls.request_payload` does not contain plaintext key;
- `quality_ledger_entries.payload` does not contain plaintext key;
- audit payload does not contain plaintext key;
- exception message and log event do not contain plaintext key;
- any HTTP response or internal result DTO does not contain plaintext key.

### 4. Minimal OpenAI-Compatible Runtime Client

Add a narrow production provider runtime in agent.

Requirements:

- send a minimal chat-completions request to configured `baseUrl`/`modelName`;
- use decrypted DB secret or env fallback secret only in the outbound
  Authorization header;
- parse the response into the existing agent AI review result shape;
- keep token usage/provenance behavior compatible with current evidence mapping;
- classify failures into permanent configuration/auth/decrypt errors and
  transient network/5xx/timeout errors;
- never include request Authorization headers or full request body with key in
  logs/errors.

This client must not call `AiReviewService` or field-assist runtime paths. It is
only a model client for the agent worker.

### 5. Fallback Semantics

Implement the owner-approved fallback policy exactly:

- no enabled DB provider: env fallback;
- exactly one enabled DB provider: DB provider;
- multiple enabled DB providers: permanent configuration error;
- decrypt failure: permanent configuration error, no env fallback;
- auth failure/invalid key from DB provider: permanent configuration error, no
  env fallback;
- malformed endpoint/model config: permanent configuration error, no env
  fallback;
- transient network/5xx/timeout from DB provider: retry by existing worker
  semantics; if fallback is implemented after retry exhaustion, it must be
  diagnostic-only and must not change evidence schema or hide permanent errors.

Gate recommendation: implement retry by existing worker policy first, then env
fallback only for clearly classified transient failures if this can be covered
by focused tests. If classification cannot be made safely, skip transient
fallback and record a safe worker failure for owner follow-up.

### 6. P-A/P8 Evidence Chain No-Touch Boundary

Batch B must not change:

- idempotency key generation and uniqueness behavior;
- existing retry/dead-letter policy shape except for provider resolution
  failures flowing through the same worker failure channel;
- failed-call recording semantics in API manual/debug path;
- P8 three-zone scoring thresholds and scoring classes;
- quality ledger evidence types and payload meaning;
- `ai_overall_recommendation` derivation;
- review UI distinction between AI evidence and human verdict.

Implementation may update the model provider/model name values that already
represent which runtime provider was used. That is provenance, not evidence
semantics.

### 7. ADR-011 Revision

Batch B revises ADR-011.

New rule to document:

- runtime AI review provider source is registry-first;
- owner with no enabled DB provider uses env/config fallback;
- owner with exactly one enabled DB provider uses DB provider;
- permanent DB provider errors do not silently fallback;
- transient runtime failures follow the approved retry/fallback classification;
- ADR-005 remains unchanged.

Stop if implementation needs a different source-of-truth rule.

<!-- BACKEND-BATCH-B-CAP-BEGIN -->
## CAP Block

Baseline anchor: `d50cb72`.
Baseline OpenAPI MD5: `7103f921bb1c578cff36b39985b0904e`.
Baseline migrations: `22`.
Expected migration count after recommended Batch B implementation: `22`.
Baseline humanpending: `190`.

Hand-authored estimate:

| Surface | Estimate |
|---|---:|
| Agent owner/provider lookup and DB reader | 260 |
| Agent AES-GCM decryptor and master-key config | 220 |
| Agent secret redaction guard | 220 |
| RuntimeProviderResolver and fallback policy | 300 |
| OpenAI-compatible runtime client and response parser | 360 |
| Env/config fallback runtime adapter | 180 |
| Worker integration with provider provenance only | 220 |
| Provider selection ambiguity handling | 120 |
| ADR-011 revision docs | 90 |
| Agent resolver/client/fallback tests | 460 |
| Security leak regression tests | 310 |
| P-A/P8 no-touch regression checks | 140 |
| Closure/reporting docs | 60 |
| **Hand-authored total** | **2940** |

Generated churn:

| Generated surface | Budget |
|---|---:|
| OpenAPI generated Java/TS types | `0` expected in recommended scope |

Recommended caps:

- soft cap: `3100` hand-authored lines;
- hard cap: `3600` hand-authored lines;
- generated churn must be reported separately;
- if hand-authored diff exceeds `3600`, stop and rescope before continuing.

OpenAPI churn:

- expected `0`;
- OpenAPI MD5 should remain `7103f921bb1c578cff36b39985b0904e`;
- any API shape change requires stop-and-report because Batch B should be an
  agent runtime source-of-truth switch, not a contract batch.

Migration churn:

- expected `0`; migrations remain `22`;
- no provider-order/default-provider migration in recommended v1;
- if explicit provider fallback order is required, stop and rescope migration
  `22 -> 23`.

Regression guard:

- `AiReviewService` evidence handling has empty diff;
- `FieldAssistService` has empty diff;
- `AiReviewScoringPolicy` and `AiReviewScoringProperties` have empty diff;
- `LedgerService` evidence type semantics have empty diff;
- P-A AI review idempotency, retry, failed-call recording, P8 scoring, ledger,
  `ai_overall_recommendation`, and reviewer UI evidence semantics stay
  unchanged;
- ADR-005 remains unchanged.

Security tests required:

- plaintext key never appears in outbox payloads;
- plaintext key never appears in `ai_calls.request_payload`;
- plaintext key never appears in `quality_ledger_entries.payload`;
- plaintext key never appears in audit payloads, exception messages, logs, or
  any response/internal result DTO;
- agent master key missing/malformed path fails predictably;
- decrypt failure is a permanent provider configuration error and does not env
  fallback;
- DB provider auth failure is a permanent provider configuration error and does
  not env fallback;
- owner with no enabled DB provider uses env fallback;
- owner with multiple enabled DB providers fails safely without leaking secrets;
- transient provider failure retry/fallback behavior is covered by focused tests
  for the chosen strategy.

D-口径:

- no Browser requirement is expected for Batch B;
- full Maven/backend integration suite may remain D-口径 if sandbox blocks DB or
  network resources, but focused agent tests and static leak checks must be run
  where available;
- external provider live calls may remain mocked unless owner supplies safe test
  credentials;
- no passing test claim may be made without captured command output.
<!-- BACKEND-BATCH-B-CAP-END -->

## Stop Conditions

Stop and ask for owner adjudication if:

- implementation changes ADR-005 or AI evidence/final verdict semantics;
- plaintext key enters any durable payload, response, exception message, audit
  payload, or log;
- agent needs API to return plaintext provider key over HTTP;
- P8 scoring classes or thresholds must change;
- idempotency key generation or uniqueness behavior must change;
- implementation needs provider ordering/default schema in Batch B;
- OpenAPI contract changes become necessary;
- hand-authored diff exceeds the hard cap in the CAP block.

## Verification Plan For Implementation

Required before implementation closure:

- record OpenAPI MD5 and prove it remains
  `7103f921bb1c578cff36b39985b0904e`;
- record migrations and prove they remain `22` unless owner approved `22 -> 23`;
- run focused agent tests for resolver, decryptor, fallback policy, runtime
  client, and redaction;
- run focused no-leak tests for outbox, `ai_calls`, ledger, audit, exception,
  log, and response surfaces;
- run focused no-touch checks for P-A/P8 files and methods listed in this gate;
- run available backend test/typecheck commands and report D-口径 limitations;
- record ADR-011 revision location and summary.
