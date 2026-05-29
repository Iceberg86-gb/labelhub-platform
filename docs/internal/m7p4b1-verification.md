# M7-P4b1 Verification: AI Review Rule Runtime Binding

## 1. Status

M7-P4b1 closes the backend half of AiReviewRule: task-scoped rule
versions, runtime active-rule resolution, and AI-call evidence binding for the
rule configuration that shaped a review.

Final anchors after C4, before this C5 closure cluster:

| Anchor | Value |
|---|---|
| Code head | `0b00b69` |
| OpenAPI MD5 | `b7df19fdb69f8d22b2f0dbdbc845d95d` |
| Backend tests | `540 / 88` |
| Frontend Vitest | `131` |
| Migrations | `17` |
| humanpending | `153` after this C5 update |

Note: the C5 prompt listed `536 / 88`, but C4 added four permanent
Testcontainers integration tests. The verified full backend run after C4 was
`540` tests with `88` skipped.

## 2. Goal

P4a bound AI calls to immutable prompt versions, but that still left a
half-binding: dimensions and threshold lived outside AI-call evidence. P4b1
closes that gap by adding task-scoped `ai_review_rules` and binding rule-bound
AI calls to the exact rule version through `ai_calls.ai_review_rule_id`.

The final evidence chain is:

```text
ai_calls.ai_review_rule_id
  -> ai_review_rules(id, task_id, version_no, dimensions_json, threshold, current_prompt_version_id)
  -> prompt_versions(id, content, content_hash)
```

This makes prompt text, dimensions, and threshold reproducible for rule-bound
AI calls.

## 3. Architecture

P4b1 preserves the P4a split-source model:

| Layer | Owner | Storage |
|---|---|---|
| Owner business prompt text | LabelHub | global `prompt_versions` |
| Owner review rule configuration | LabelHub | task-scoped `ai_review_rules` |
| Provider adapter behavior | `services/agent` | P4a constant `agent-default-v1` placeholder |

`prompt_versions` remains global. P4b1 does not add a family id or reinterpret
P4a evidence rows. `ai_review_rules` provides the task-scoped family/version
layer on top by pointing to a prompt version.

## 4. Commit Map

| Commit | Cluster | Purpose |
|---|---|---|
| `aaf02ea` | Research | P4b1/P4b2 split, rule-to-prompt pointer design, dimensions/threshold ownership, optional review-rule publish guard |
| `57c8efd` | C1 | OpenAPI AiReviewRule response, `AiReviewRuleStatus`, `ai_review_rules`, `tasks.current_ai_review_rule_id`, entity/mapper infrastructure |
| `e762d81` | C2 | `AiReviewRuleService`, save/publish endpoints, promptTemplate -> PromptVersion conversion, DTO prompt join |
| `6a280b3` | C3 | Runtime active-rule resolution, `ai_calls.ai_review_rule_id`, rule-bound idempotency segment, dangling-rule fail-fast |
| `af5dd08` | C4-fix | Export canonical null-evidence omission for `promptVersionId` and `aiReviewRuleId` without touching shared `Canonicalizer` |
| `0b00b69` | C4 | Integration coverage for active rule, no-rule fallback, key isolation, dangling pointer, mixed provenance |
| (this commit) | C5 | Verification doc and humanpending closure records |

Gate-only commits:

| Commit | Gate |
|---|---|
| `5c3a270` | C1 pre-estimate |
| `34d71ed` | C2 pre-estimate |
| `e64da5c` | C3 pre-estimate |
| `772c29f` | C4 pre-estimate |
| `819ed0f` | C4-fix pre-estimate |

## 5. MD5 And Migration Chain

| Step | OpenAPI MD5 | Migrations | Why |
|---|---|---:|---|
| P4a final | `23a67e2cad632b3e9cfaff03c5d05dd7` | `14` | PromptVersion foundation |
| C1 | `b10b8cf2339f4b01c683eb8b7d12bf2f` | `16` | AiReviewRule contract and tables |
| C2 | `7c9358b2b2d5a1079de8f768a243841a` | `16` | save/publish endpoints |
| C3 | `b7df19fdb69f8d22b2f0dbdbc845d95d` | `17` | AiCall rule evidence FK and response field |
| C4-fix / C4 / C5 | `b7df19fdb69f8d22b2f0dbdbc845d95d` | `17` | No contract or DB changes |

Migration chain:

```text
14 -> 15: ai_review_rules
15 -> 16: tasks.current_ai_review_rule_id
16 -> 17: ai_calls.ai_review_rule_id
```

## 6. Cluster Delivery

### 6.1 Research

Research closed six design questions:

- P4b is split into P4b1 backend rule foundation and P4b2 owner UI.
- Owner input still sends `promptTemplate`; backend converts it into a
  `prompt_versions` row and stores a pointer on the rule.
- `dimensions` and `threshold` live on `ai_review_rules`, not inside prompt
  text.
- Review rules are optional; task publish does not require one.
- P4b1 v1 does not add configurable conclusion strategy. Conclusion remains
  threshold-derived output behavior.
- P4b2 owns owner-facing prompt/rule editing UI.

### 6.2 C1: Contract And Tables

C1 added backend infrastructure:

- OpenAPI `AiReviewRule` as an independent response schema;
- `AiReviewRuleStatus`;
- task-scoped `ai_review_rules` with `version_no`, `current_prompt_version_id`,
  `dimensions_json`, `threshold`, `status`, `created_by`, and `activated_at`;
- nullable `tasks.current_ai_review_rule_id` with the circular FK added after
  table creation;
- `AiReviewRuleEntity` and `AiReviewRuleMapper`;
- generated types.

P4b1 intentionally did not add a publish guard. `TaskService.canPublish`
remains unchanged and review rules are optional.

### 6.3 C2: Rule Service

C2 implemented rule creation and activation:

- `saveRule` checks task ownership and creates a new immutable rule version;
- `promptTemplate` is converted through `PromptVersionService.create`;
- repeated prompt text reuses the same prompt version while still creating a
  new rule version when dimensions or threshold change;
- `publishRule` marks the rule published and sets
  `tasks.current_ai_review_rule_id`;
- save and publish are transactional and rollback-safe;
- controller endpoints replace the old `501` stub;
- DTO mapping joins through `prompt_versions` so responses can include
  `promptTemplate` display text even though the rule stores only the prompt id.

Validation is owner-facing and conservative: non-empty prompt, non-empty
dimensions, and threshold range checks return client errors; cross-owner access
follows the existing not-found style.

### 6.4 C3: Runtime Binding

C3 wired active rules into review runtime:

- if a task has `current_ai_review_rule_id`, the active rule wins;
- the rule's `current_prompt_version_id` overrides the request
  `promptVersionId`;
- if no active rule exists, P4a fallback behavior remains intact and the
  request prompt version is used;
- dangling `current_ai_review_rule_id` fails fast with not found;
- `ai_calls.ai_review_rule_id` records the rule version for rule-bound calls;
- idempotency keys include a `ruleVersionId` segment for rule-bound calls;
- no-rule calls keep the P4a key shape without the rule segment;
- provenance/export DTOs include the nullable rule id.

Provider behavior remains unchanged. Business prompt content is still not fed
into provider adapters, preserving the ADR-011 boundary.

### 6.5 C4-Fix: Export Canonical Null Evidence

C4's export audit found a canonicalization drift:

- P4b1 C3 added nullable `aiReviewRuleId` to export AI-call rows.
- Shared `Canonicalizer` serializes null map entries.
- no-rule and legacy rows would shift from "no field" to
  `"aiReviewRuleId": null`.
- The same class of drift had already happened in P4a when nullable
  `promptVersionId` was added.

C4-fix repaired both by omitting null optional evidence keys locally in export
AI-call map construction:

- omit null `promptVersionId`;
- omit null `aiReviewRuleId`;
- keep old nullable fields such as `fieldPath` unchanged;
- do not change `Canonicalizer`;
- do not change schema/prompt/submission/dataset/audit/input hash paths.

`CANONICALIZATION_VERSION` stays v1. This is recorded as a correction to the
intended v1 optional-evidence shape, not as a broad canonicalization rewrite.

### 6.6 C4: Integration Coverage

C4 added permanent Testcontainers coverage for runtime rule behavior:

- active rule wins over request prompt id and binds `ai_review_rule_id`;
- no-rule task uses the P4a fallback prompt path;
- no-rule key and rule-bound key are isolated for the same submission;
- dangling active rule pointer returns not found;
- mixed provenance returns legacy/no-rule rows and rule-bound rows together.

Local verification compiled these tests, but Docker is unavailable in this
environment, so Testcontainers cases skip under `disabledWithoutDocker`. The
full backend run after C4 was `540` tests with `88` skipped and no failures.

## 7. Key Invariants

### Two-Layer Versioning

Prompt text and review-rule configuration version independently:

| Change | Result |
|---|---|
| Prompt text changes | new/reused `prompt_versions` row plus new rule version |
| Threshold changes only | same prompt version can be reused, new rule version |
| Dimensions change only | same prompt version can be reused, new rule version |

The rule version is the full review configuration binding. The prompt version
is the immutable prompt text binding.

### Active Rule Wins

Runtime precedence:

```text
task.current_ai_review_rule_id present
  -> use rule.current_prompt_version_id
  -> write ai_calls.ai_review_rule_id
  -> idempotency key includes ruleVersionId

task.current_ai_review_rule_id null
  -> use request promptVersionId
  -> ai_calls.ai_review_rule_id null
  -> P4a idempotency key shape
```

### Review Rule Is Optional

`tasks.current_ai_review_rule_id` is nullable. `TaskService.canPublish` is not
changed in P4b1, and tasks without AI review rules can still publish.

### Evidence Half-Binding Closed

P4a evidence had prompt id plus provider adapter, but dimensions/threshold were
not bound. P4b1 adds `ai_calls.ai_review_rule_id`, so rule-bound evidence can
recover the prompt text, dimensions, threshold, and rule version.

### Export Isolation

C4-fix is export-local. Shared `Canonicalizer` remains untouched so existing
content hashes remain isolated from the export shape correction.

## 8. Verification And D-Portions

Verified locally during P4b1:

- OpenAPI MD5 final: `b7df19fdb69f8d22b2f0dbdbc845d95d`;
- migrations final: `17`;
- frontend Vitest final: `131`;
- full backend run after C4: `540 / 88`, no failures;
- export canonical tests run without Docker;
- Testcontainers integration tests are committed but skip locally without
  Docker.

D-portions:

- C4 active-rule integration tests require Docker/Testcontainers.
- export snapshot row count could not be verified with a live MySQL CLI during
  C4-fix; this remains recorded in humanpending.

## 9. Remaining Watch Items

- P4b2 must add the owner prompt/rule editor UI: prompt textarea, dimensions,
  threshold, save draft, publish, and version history.
- conclusion strategy remains threshold-derived. Richer configurable outcome
  mapping belongs with scoring calibration if needed.
- provider adapter version is still the P4a constant placeholder until an
  agent-versioning cluster provides a real adapter version source.
- production deployments with existing export snapshots should decide whether
  the C4-fix v1 correction needs compatibility handling or a future v2 export
  canonicalization.

