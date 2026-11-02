# Backend Batch B Pre-Estimate: AI Review Provider Registry Runtime

## Status

Pre-estimate gate for Backend Batch B. This document is a planning artifact
only; it does not implement provider runtime changes.

Branch: `codex/backend-llm-provider-config`.
Anchor: `d50cb72`.
Baselines: OpenAPI `7103f921bb1c578cff36b39985b0904e`, migrations `22`,
humanpending `190`.

Batch B is the source-of-truth switch that Batch A intentionally deferred.
Automatic AI review should resolve provider config from the encrypted DB
registry first, then use env/config fallback only in approved cases. The AI
review evidence chain must remain unchanged.

## Implementation Shape

Recommended shape:

1. Add an agent-side provider config lookup path that derives owner context from
   review job/task/submission context and reads `llm_provider_configs`.
2. Add an agent-side decryptor compatible with Batch A AES-GCM ciphertext and
   `LABELHUB_LLM_PROVIDER_MASTER_KEY`.
3. Add `RuntimeProviderResolver` that returns either a DB-backed provider source,
   env fallback source, or a safe configuration error.
4. Add a minimal OpenAI-compatible runtime client in `services/agent`.
5. Integrate provider resolution immediately before the model call in the agent
   worker, preserving existing evidence processing.
6. Revise ADR-011 to document registry-first source-of-truth and fallback
   semantics.

The implementation should prefer no OpenAPI and no migration churn. Provider
ordering is out of scope for Batch B v1.

## Non-Negotiable Boundaries

- ADR-005 stays unchanged: AI is auxiliary evidence, not a final decision-maker.
- P-A/P8 evidence semantics stay unchanged.
- No plaintext key over API-to-agent HTTP.
- No plaintext key in durable records or loggable diagnostics.
- Permanent DB provider configuration/auth/decrypt failures do not silently
  fallback to env/config.
- No source-of-truth change for field assist in this batch.

## Recommended Provider Selection Rule

Use one enabled DB provider per owner for Batch B v1:

- zero enabled DB providers: env/config fallback;
- one enabled DB provider: DB provider;
- more than one enabled DB provider: safe configuration error;
- DB decrypt/config/auth failure: safe configuration error, no fallback;
- transient network/5xx/timeout: use existing retry policy first, then apply the
  chosen transient fallback strategy only if tests prove it cannot hide
  permanent configuration errors.

This avoids a migration for provider priority/order in Batch B. If product needs
ordered provider chains now, split that into a `22 -> 23` migration gate.

## Security Design

Architecture A is approved: agent self-decrypts.

Security implications:

- agent must receive `LABELHUB_LLM_PROVIDER_MASTER_KEY`;
- decryptor must be compatible with Batch A but should not create a runtime
  dependency on API internals unless a clean shared module already exists;
- plaintext key must be scoped to the runtime call;
- redaction must be implemented or proven for every new agent diagnostic path.

Leak checks must include outbox payloads, `ai_calls.request_payload`,
`quality_ledger_entries.payload`, audit payloads, exception messages, logs, and
responses/internal DTOs.

## Expected Churn

Expected implementation surfaces:

- `services/agent` config/properties for master key and env fallback provider;
- `services/agent` provider config DB reader;
- `services/agent` decryptor;
- `services/agent` runtime resolver;
- `services/agent` OpenAI-compatible client;
- `services/agent` focused tests;
- ADR-011 revision docs.

Expected no-churn surfaces:

- OpenAPI contract and generated types;
- DB migrations;
- API provider CRUD response DTOs;
- `AiReviewService` evidence handling;
- `FieldAssistService`;
- P8 scoring classes;
- ledger evidence semantics;
- reviewer verdict logic.

## Test Strategy

Focused tests should be written before or alongside implementation:

- resolver tests for zero/one/multiple enabled DB providers;
- decryptor compatibility and master-key failure tests;
- fallback tests for no-config, permanent error, auth failure, decrypt failure,
  and transient provider failure;
- OpenAI-compatible client tests with stubbed HTTP;
- no-leak tests for key material across durable/loggable surfaces;
- worker integration test proving provider provenance changes without evidence
  semantic changes;
- static/diff checks for P-A/P8 no-touch files.

External live provider tests should remain optional unless owner supplies safe
credentials.

## ADR-011 Revision Plan

Batch B must update ADR-011 from env/config runtime source to registry-first
runtime source:

- DB registry is preferred for automatic AI review provider resolution;
- no DB provider falls back to env/config;
- permanent DB provider problems fail visibly and do not fallback;
- transient failures follow approved retry/fallback strategy;
- ADR-005 non-decision-maker principle remains untouched.

## Risk Register

Highest risks:

- leaking plaintext keys after moving decrypt capability into agent;
- accidentally changing P-A/P8 evidence semantics while integrating the runtime
  client;
- hiding broken DB provider configuration by silently falling back to env;
- adding provider-order schema or OpenAPI churn inside a batch intended to be
  runtime-only;
- adding a broad provider platform instead of a minimal OpenAI-compatible client.

Each risk has a corresponding stop condition in the CAP block.

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

Implementation must stop and report if:

- API-to-agent plaintext-key transport becomes necessary;
- OpenAPI or migration churn becomes necessary in the recommended scope;
- multiple DB provider fallback order must be represented now;
- provider runtime changes require altering `AiReviewService`,
  `FieldAssistService`, scoring, ledger semantics, or ADR-005;
- any test or inspection finds plaintext key material in durable/loggable
  surfaces;
- hard cap is exceeded.

## Closure Expectations For Implementation Batch

Implementation closure should report:

- OpenAPI MD5 unchanged or any approved change;
- migrations unchanged at `22` or any approved `22 -> 23` change;
- focused agent tests run;
- security no-leak tests run;
- no-touch diff proof for P-A/P8 files;
- ADR-011 revision commit;
- D-口径 limitations, especially for live provider credentials and full suite.
