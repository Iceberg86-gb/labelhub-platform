# M7-P4b1 C4-Fix Pre-Estimate: Export Null Evidence Canonicalization

## Status

Gate only. No production code or tests are changed until this gate is approved.

Current anchor:

- HEAD: `772c29f` (`M7-P4b1 C4 gate docs`)
- OpenAPI MD5: `b7df19fdb69f8d22b2f0dbdbc845d95d`
- Backend tests: `534 / 84`
- Frontend tests: `131`
- Migrations: `17`
- humanpending: `146`

## Scope Summary

C4 found an export canonicalization STOP condition before integration test implementation began. C4-fix should repair the export canonical shape for nullable AI evidence fields without changing shared canonicalization or any content hash path.

The repair is deliberately narrow:

- export-only;
- AI-call canonical map only;
- only nullable fields introduced as evidence bindings in P4a/P4b;
- no OpenAPI or migration changes.

## Verified Current Seams

- `Canonicalizer` is a shared Spring component and does not configure null exclusion.
- `SchemaService` uses `canonicalJson(...)` for `schema_versions.content_hash`.
- `SessionService` uses `canonicalJson(...)` for submission content hash.
- `DatasetImportService` uses `canonicalJson(...)` for dataset item hashes.
- `AuditLogServiceImpl` and `TaskService` use `canonicalJson(...)` for audit payload hashes.
- `AiReviewService` uses `canonicalJson(...)` for AI input hash.
- `PromptVersionService` uses `sha256Hex(content)` directly for prompt content hash.
- `ExportArtifactBuilder.aiCallToCanonical(...)` currently puts both `promptVersionId` and `aiReviewRuleId` into the canonical map even when null.
- Existing `ExportServiceTest` compares export hashes for stability but does not hard-code specific hash values.
- Static repository grep shows no committed `export_snapshots` fixture rows. A live local DB count could not be queried at gate time because the `mysql` CLI is unavailable in this environment; implementation should check if a DB client or Testcontainers path is available.

## Decision 1: Use Export-Local Map Omission, Not A New ObjectMapper

Recommendation: use map-level omission in `ExportArtifactBuilder.aiCallToCanonical(...)`.

Implementation shape:

```java
Map<String, Object> row = map(
    "id", call.getId(),
    "submissionId", call.getSubmissionId(),
    ...
    "promptVersion", call.getPromptVersion()
);
putIfNotNull(row, "promptVersionId", call.getPromptVersionId());
putIfNotNull(row, "aiReviewRuleId", call.getAiReviewRuleId());
row.put("providerAdapterVersion", call.getProviderAdapterVersion());
...
return row;
```

Rationale:

- It affects only export AI-call row construction.
- It leaves the shared `Canonicalizer` untouched.
- It avoids a second ObjectMapper configuration and the risk of drift between export files.
- It makes the semantic distinction explicit: no evidence binding means no evidence key.

Rejected alternative:

- Dedicated export ObjectMapper with `NON_NULL`: safer than touching `Canonicalizer`, but broader than needed. It would omit nulls across every export map, changing historical shape for old nullable fields.

## Decision 2: Omit Only New Nullable Evidence Fields

Recommendation: omit null only for:

- `promptVersionId`;
- `aiReviewRuleId`.

Rationale:

- These fields were introduced after M6 as additive evidence binding fields.
- `null` means the evidence binding did not exist for that call, so omitting the key best preserves historical shape.
- Other nullable fields, such as `fieldPath`, `responsePayload`, `scores`, `verdict`, `tokenInput`, `tokenOutput`, `costDecimal`, `latencyMs`, and `completedAt`, existed in the export canonical shape before this repair. Changing their null behavior would be a much wider canonical migration.

Rejected alternative:

- Omit all null fields in export canonical rows: cleaner as a general rule, but it would alter M6/M7 historical export shape for pre-existing nullable fields and likely requires a canonicalization version bump.

## Decision 3: Keep `CANONICALIZATION_VERSION` v1 If No Stored Snapshot Constraint Appears

Recommendation: keep `labelhub-canonical-v1` for this repair, provided implementation confirms there are no existing stored export snapshots that must preserve the P4a/P4b drifted shape.

Rationale:

- The intended v1 rule is clarified as: absent optional evidence bindings are not serialized.
- The repair restores no-rule/legacy AI-call export rows toward the original no-field shape instead of creating a broader v2 migration.
- Rule-bound AI calls still serialize non-null `aiReviewRuleId`, so new evidence is preserved.

STOP condition:

- If implementation discovers real `export_snapshots` rows whose v1 hashes were created with drifted null evidence fields and must remain comparable under the same version, stop for adjudication. The likely choices are:
  - bump `CANONICALIZATION_VERSION`;
  - keep v1 with documented correction and accept historical mismatch in dev data only;
  - add compatibility handling.

## Decision 4: Isolation Proof Is Part Of The Fix

The implementation report must prove the repair is isolated:

- `Canonicalizer.java` diff is empty.
- `SchemaService.java` diff is empty.
- `SessionService.java` diff is empty.
- `DatasetImportService.java` diff is empty.
- `AuditLogServiceImpl.java` diff is empty.
- `AiReviewService.java` hash/idempotency logic is unchanged.
- `PromptVersionService.java` diff is empty.
- seed prompt hash `fa76977fd0bdc3f0cc7336855006669f2950381f1a0dc4f0803458bb6f06d456` remains in migration/tests.

This is the main guard against accidentally changing P3a/P3b/P4a frozen hash baselines.

## Decision 5: Tests To Add Or Adjust

### Export canonical shape tests

Add tests in `ExportServiceTest` or a focused export builder test:

- no-rule AI call with `promptVersionId = null` and `aiReviewRuleId = null` does not include either key in `ai-calls.jsonl`;
- rule-bound AI call with non-null `aiReviewRuleId` does include `"aiReviewRuleId":<id>`;
- no-rule export still includes pre-existing nullable fields with their current behavior, if the fixture makes them null.

### Isolation tests

Prefer existing focused tests rather than new broad tests:

- `SchemaServiceTest.publishVersion_computes_canonical_content_hash` should remain green;
- `PromptVersionServiceTest` should remain green and seed hash should remain visible in contract tests;
- `AiReviewServiceTest` input hash tests should remain green.

No new business behavior test is required outside export shape unless a regression appears.

## Decision 6: Line Budget

| Area | Files | Estimate |
|---|---:|---:|
| Export map helper + targeted field changes | `ExportArtifactBuilder.java` | 25 |
| No-rule null evidence test | `ExportServiceTest` or focused builder test | 45 |
| Rule-bound evidence-present test | `ExportServiceTest` or focused builder test | 45 |
| Isolation verification / assertions | tests or implementation report evidence | 60 |
| Snapshot-count check/report | implementation report | 20 |

Estimated hand-authored total: about 195 lines.

Recommended cap: soft 300 / hard 500.

## Implementation Order

1. Write the failing export test showing null `promptVersionId`/`aiReviewRuleId` are currently serialized.
2. Run the focused export test and verify it fails for the intended reason.
3. Implement targeted map-level omission in `ExportArtifactBuilder.aiCallToCanonical(...)`.
4. Run the focused export test and verify it passes.
5. Add/verify non-null `aiReviewRuleId` still appears.
6. Run hash-isolation tests.
7. Run broader backend verification.

## Frozen Boundaries

C4-fix should not change:

- `Canonicalizer.java`;
- schema/prompt/submission/dataset/audit/input hash services;
- OpenAPI;
- migrations;
- humanpending;
- frontend;
- P3a/P3b artifacts.

## Verification Plan

Implementation should report:

```bash
mvn -pl services/api -Dtest=ExportServiceTest test
mvn -pl services/api -Dtest=SchemaServiceTest,PromptVersionServiceTest,AiReviewServiceTest test
mvn -pl services/api test
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \\[" humanpending.md
git diff -- services/api/src/main/java/com/labelhub/api/shared/canonical/Canonicalizer.java
git status --short
```

Expected:

- no-rule export omits null `promptVersionId` and null `aiReviewRuleId`;
- rule-bound export includes non-null `aiReviewRuleId`;
- `Canonicalizer.java` diff is empty;
- OpenAPI MD5 remains `b7df19fdb69f8d22b2f0dbdbc845d95d`;
- migrations remain `17`;
- humanpending remains `146`.

## Audit Notes For Implementation Review

Implementation audit should verify:

- the repair covers both P4a `promptVersionId` drift and P4b1 `aiReviewRuleId` drift;
- it does not omit old nullable fields globally;
- it does not alter any content hash or idempotency key algorithm;
- export tests capture the canonical file content, not only DTO shape;
- if live `export_snapshots` rows exist, the implementation report says how that affects the version decision.
