# Backend Batch A Pre-Estimate: LLM Provider Config And Secret Vault

## Status

Pre-estimate gate for Backend Batch A. No implementation code has landed in
this batch.

Current branch: `codex/backend-llm-provider-config`.
Current anchor: `042e66d`.
OpenAPI MD5: `5102e4e97b9f842248aca651681b7b82`.
Migrations: `21`.
humanpending: `189`.

Expected implementation effect: OpenAPI MD5 changes, migrations become `22`,
and provider CRUD/test-connection APIs plus frontend LLM settings API wiring are
added. P-A AI review runtime behavior remains unchanged.

## Current Evidence Summary

### Provider And Key Source

Current provider/key behavior is env/config driven:

- `labelhub.ai.active-provider` defaults to `mock`;
- `labelhub.ai.openai-compatible.api-key` reads `AI_API_KEY`;
- OpenAI-compatible mode fails fast when base URL/key/model are missing;
- `OpenAiCompatibleProvider` sends `Authorization: Bearer <apiKey>`.

This means Batch A must add provider persistence, but must not assume the whole
provider abstraction is absent.

### P-A Boundary

P-A AI review currently receives an injected `AiProvider`. The review service
does not directly read a key. The key is inside `OpenAiCompatibleProvider`
properties.

Batch A must leave that runtime path unchanged. The DB-backed provider config
created here is intentionally not the AI review source-of-truth until Batch B.

### Security Gap

The audit service persists caller-provided payloads as-is. Therefore provider
management implementation cannot rely on generic audit infrastructure to strip
secrets. It must avoid adding secrets to audit payloads and prove the provider
create/update/test-connection flows do not leak secret values through audit,
errors, or exception messages.

## Recommended Contract Shape

### Provider Config Response

Response DTO should expose only safe secret metadata:

```yaml
LlmProviderConfig:
  type: object
  required:
    [id, providerType, providerName, modelName, enabled, hasSecret, createdAt, updatedAt]
  properties:
    id:
      type: integer
      format: int64
    providerType:
      type: string
    providerName:
      type: string
    baseUrl:
      type: string
      nullable: true
    modelName:
      type: string
    enabled:
      type: boolean
    hasSecret:
      type: boolean
    secretLast4:
      type: string
      nullable: true
    secretUpdatedAt:
      type: string
      format: date-time
      nullable: true
    secretRef:
      type: string
      nullable: true
    createdAt:
      type: string
      format: date-time
    updatedAt:
      type: string
      format: date-time
```

Hard exclusions:

- no plaintext `secret`, `apiKey`, `token`, or `password` field in response;
- no `secretCiphertext`, IV, tag, or encryption metadata in response;
- no response echo of create/update/test request body.

### Create/Update Request

Requests may accept plaintext secret because the user must be able to set a key:

```yaml
LlmProviderConfigRequest:
  type: object
  required: [providerType, providerName, modelName]
  properties:
    providerType:
      type: string
    providerName:
      type: string
    baseUrl:
      type: string
      nullable: true
    modelName:
      type: string
    enabled:
      type: boolean
    secret:
      type: string
      nullable: true
      writeOnly: true
    secretRef:
      type: string
      nullable: true
```

The implementation should confirm generated server/client handling does not
accidentally serialize `writeOnly` fields into responses. The controller mapper
must still explicitly omit secrets.

### Test Connection Response

```yaml
LlmProviderTestConnectionResponse:
  type: object
  required: [ok, providerName, modelName]
  properties:
    ok:
      type: boolean
    providerName:
      type: string
    modelName:
      type: string
    latencyMs:
      type: integer
      nullable: true
    providerStatus:
      type: integer
      nullable: true
    providerCode:
      type: string
      nullable: true
    message:
      type: string
      nullable: true
```

`message` must be controlled text, not raw provider response body if that body
can include request echo or sensitive context.

## Recommended Migration Shape

Use one migration for Batch A:

```sql
CREATE TABLE llm_provider_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    provider_type VARCHAR(40) NOT NULL,
    provider_name VARCHAR(120) NOT NULL,
    base_url VARCHAR(512),
    model_name VARCHAR(160) NOT NULL,
    secret_ciphertext TEXT,
    secret_last4 VARCHAR(8),
    secret_updated_at DATETIME(3),
    secret_ref VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_llm_provider_configs_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    KEY idx_llm_provider_configs_owner_enabled (owner_id, enabled),
    UNIQUE KEY uk_llm_provider_configs_owner_name (owner_id, provider_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Do not add any plaintext key column. Do not seed real provider credentials.

## Implementation Risk Decisions

### 1. Master Key Configuration

Recommendation: add a dedicated property such as
`labelhub.secrets.llm-provider-master-key` backed by an env var such as
`LABELHUB_LLM_PROVIDER_MASTER_KEY`.

Do not reuse `JWT_SECRET`. JWT signing and DB encryption have different
rotation/audience semantics.

### 2. Encryption Primitive

Recommendation: use a standard authenticated encryption primitive available in
the JDK, for example AES-GCM with random nonce per write, or an equivalent
project-approved authenticated scheme.

Store enough encoding metadata inside `secret_ciphertext` to decrypt later
during Batch B/test connection, but do not expose that metadata through API.

### 3. Test Connection Isolation

Recommendation: implement a provider test client separate from P-A review logic.
It may share low-level HTTP request utilities, but it must not invoke
`AiReviewService`, write `ai_calls`, write ledger entries, or increment review
metrics.

### 4. Audit Redaction

Recommendation: add an explicit provider-management audit payload builder or
redaction helper. Tests should use a unique fake secret string and assert it is
absent from audit payload, error response, and thrown exception message.

### 5. Frontend Secret Handling

Recommendation: after save/test, clear the secret input and re-render from API
metadata. Never keep the saved secret in React state longer than the current
input interaction.

## Required Tests

### Backend Unit / Slice Tests

- encryption round trip through service internals, without exposing plaintext
  from public provider config service methods;
- encryption changes ciphertext on update and updates last4/updatedAt;
- missing master key fails predictably;
- malformed master key fails predictably;
- mapper/service stores ciphertext and never plaintext;
- response mapper returns hasSecret/last4/updatedAt/ref only;
- metadata-only update preserves existing secret metadata;
- secret update overwrites metadata and does not support plaintext read-back.

### Backend Security / Permission Tests

- Owner can create/list/get/update/delete or disable provider config;
- Labeler forbidden;
- Reviewer forbidden;
- Senior reviewer forbidden;
- unauthenticated rejected;
- owner A cannot operate owner B provider config;
- create/update/test audit payload does not contain fake secret;
- error response/exception message does not contain fake secret.

### Backend Test Connection Tests

- required field validation fails before network call;
- stored encrypted secret can be used for test connection;
- one-shot test secret can be used without saving it;
- provider 2xx returns success metadata;
- provider 4xx/5xx returns controlled diagnostic without secret;
- timeout/network failure returns controlled diagnostic without secret.

### Frontend Tests

- page loads provider configs from API;
- save calls create/update API and clears secret input;
- test connection calls backend endpoint;
- UI displays only last4/hasSecret metadata;
- failed test connection shows controlled error without rendering the entered
  secret;
- role route keeps LLM settings Owner-only.

<!-- BACKEND-BATCH-A-CAP-BEGIN -->
## CAP Block

Baseline anchor: `042e66d`.
Baseline OpenAPI MD5: `5102e4e97b9f842248aca651681b7b82`.
Baseline migrations: `21`.
Expected migration count after Batch A implementation: `22`.
Baseline humanpending: `189`.

Hand-authored estimate:

| Surface | Estimate |
|---|---:|
| OpenAPI provider schemas and paths | 180 |
| Provider config migration `21 -> 22` | 90 |
| Provider entity/mapper/repository/service | 260 |
| Secret encryption config/service | 220 |
| CRUD controller and DTO mapping | 240 |
| Test connection service/client/endpoint | 220 |
| Owner authorization and owner scoping | 90 |
| Audit/log/error redaction guard | 150 |
| Frontend LLM settings API hooks/page wiring | 300 |
| Backend tests | 430 |
| Frontend tests | 180 |
| Docs/report updates | 60 |
| **Hand-authored total** | **2420** |

Generated churn:

| Generated surface | Budget |
|---|---:|
| OpenAPI generated Java/TS types | Report separately; not counted in hand-authored cap |

Recommended caps:

- soft cap: `2600` hand-authored lines;
- hard cap: `3200` hand-authored lines;
- generated churn must be reported separately;
- if hand-authored diff exceeds `3200`, stop and rescope before continuing.

OpenAPI churn:

- expected to change from MD5 `5102e4e97b9f842248aca651681b7b82` to a recorded new value;
- must be limited to provider config/test-connection schemas and paths;
- must not alter P-A AI review trigger/result contracts unless owner explicitly re-adjudicates Batch B.

Migration churn:

- expected `21 -> 22`;
- migration must create provider config storage only;
- migration must not add plaintext secret columns.

Regression guard:

- `AiReviewService` has empty diff;
- `FieldAssistService` has empty diff;
- P-A AI review idempotency, retry, scoring, ledger, and provider source-of-truth stay unchanged;
- existing auth, task, designer, labeler, reviewer, export, and config pages keep current routes unless only adding the LLM provider API wiring.

Security tests required:

- API responses never include plaintext secret or ciphertext;
- create/update/test connection audit payloads do not contain submitted secret values;
- exception messages and error responses do not contain submitted secret values;
- master key missing/malformed path fails predictably;
- secret update overwrites ciphertext/last4/updatedAt and provides no read-back plaintext;
- owner can manage providers; labeler/reviewer/senior reviewer cannot;
- test connection request/response/log/audit path does not leak full key.

D-口径:

- Browser verification for the LLM settings page may remain D-口径 if Browser is unavailable;
- full Maven backend suite may remain D-口径 if sandbox blocks DB/network resources, but focused tests must be identified and run where available;
- no screenshots or passing test claims may be fabricated.
<!-- BACKEND-BATCH-A-CAP-END -->

## Stop Conditions

- Any implementation changes P-A AI review source-of-truth before owner approves
  Batch B.
- Response DTO needs to include plaintext secret or ciphertext.
- Provider management audit/error path cannot prove secret redaction.
- Migration introduces plaintext secret fields.
- Master key design reuses JWT secret.
- Test connection writes AI review evidence rows or review metrics.
- OpenAPI churn touches AI review trigger/result schemas.
- Hand-authored diff exceeds the hard cap.

## Verification Required For Implementation

- Record new OpenAPI MD5 after contract generation.
- Confirm migrations count `21 -> 22`.
- Confirm provider migration has no plaintext secret columns.
- Run focused backend tests for crypto, provider config persistence, CRUD,
  permissions, test connection, and audit/error redaction.
- Run focused frontend tests for Owner LLM settings API wiring.
- Confirm `AiReviewService` and `FieldAssistService` have empty diff.
- Confirm API responses and UI snapshots/test output never include fake full
  secret values.
- Confirm `git status --short` clean after implementation commit.

## Gate Summary

Batch A should land as a security-first provider configuration foundation:

```text
Provider config table + encrypted write-only secret storage + Owner CRUD API +
test connection + frontend LLM page API wiring.
```

It intentionally leaves P-A AI review using the old env/config provider source.
That keeps the sealed AI review path stable and gives Batch B a clean,
auditable switch point.
