# M7-P4b1 C4-Fix Scope Budget

## Objective

Repair the export canonicalization drift found at the start of C4 by omitting null values only for newly added nullable AI evidence fields in export output, while keeping the shared `Canonicalizer` and all non-export content hash paths untouched.

This is an inserted repair cluster, analogous to P3b C3.5. It must land before returning to the C4 integration tests.

## Anchor

- HEAD: `772c29f` (`M7-P4b1 C4 gate docs`)
- OpenAPI MD5: `b7df19fdb69f8d22b2f0dbdbc845d95d`
- Backend tests: `534 / 84`
- Frontend tests: `131`
- Migrations: `17`
- humanpending: `146`

## Problem Summary

C3 added nullable `aiReviewRuleId` to `ExportArtifactBuilder.aiCallToCanonical(...)`. The shared `Canonicalizer` does not omit nulls, so no-rule/legacy AI calls serialize as `"aiReviewRuleId":null`. That changes `ai-calls.jsonl` and downstream export hashes while `CANONICALIZATION_VERSION` remains `labelhub-canonical-v1`.

The audit also shows P4a C3 previously introduced the same kind of drift for nullable `promptVersionId` on legacy rows.

The fix must cover both nullable evidence fields:

- `promptVersionId`;
- `aiReviewRuleId`.

## In Scope

### Export-Only Null Omission

Modify export map construction so null `promptVersionId` and null `aiReviewRuleId` are not put into `ai-calls.jsonl`.

Recommended implementation:

- keep the shared `Canonicalizer` unchanged;
- keep the generic `map(...)` helper unchanged;
- add a tiny export-local helper around `aiCallToCanonical(...)`, such as `putIfNotNull(map, key, value)`;
- use that helper only for `promptVersionId` and `aiReviewRuleId`.

### Tests

Add export tests proving:

- no-rule/legacy AI calls do not serialize null `promptVersionId`;
- no-rule/legacy AI calls do not serialize null `aiReviewRuleId`;
- rule-bound AI calls do serialize non-null `aiReviewRuleId`;
- export-only null omission does not affect `SchemaService`, `PromptVersionService`, or `AiReviewService` hash paths.

## Out Of Scope

- Changing `Canonicalizer.java`.
- Changing global Jackson serialization settings.
- Omitting nulls for all export fields.
- Changing OpenAPI.
- Changing migrations.
- Changing humanpending in this cluster.
- Changing schema/prompt/input hash logic.
- Returning to C4 integration scenarios until this repair is approved and implemented.

## Decision Summary

| Decision | Recommendation |
|---|---|
| Isolation approach | Targeted map-level omission in `ExportArtifactBuilder.aiCallToCanonical(...)` |
| Shared `Canonicalizer` | Do not change |
| Field coverage | Omit null only for `promptVersionId` and `aiReviewRuleId` |
| Canonicalization version | Keep `labelhub-canonical-v1` if no stored snapshots require v2 |
| Existing nullable export fields | Preserve their current null serialization |
| humanpending | Defer R8 note to P4b1 C5 if desired |

## Line Budget

| Item | Estimate |
|---|---:|
| Export map helper + targeted field changes | 25 |
| ExportServiceTest canonical shape coverage | 100 |
| Hash isolation tests or assertions | 85 |
| Local snapshot-count check/report wiring | 20 |
| Total hand-authored | ~230 |

Cap proposal:

- Soft cap: 300 hand-authored lines.
- Hard cap: 500 hand-authored lines.

## Risk Register

### Global hash drift

Risk: changing `Canonicalizer` globally would alter schema content hashes, submission content hashes, dataset item hashes, AI input hashes, audit payload hashes, and export hashes.

Mitigation: `Canonicalizer.java` must remain diff-empty. The repair is export-local and map-level.

### Over-omitting existing nullable fields

Risk: globally omitting nulls inside export canonical maps would change old M6/M7 export shapes for fields like `fieldPath`, `verdict`, `latencyMs`, `completedAt`, `responsePayload`, and `scores`.

Mitigation: omit only P4a/P4b-added nullable evidence fields that mean "no such evidence binding".

### Stored export snapshots

Risk: if real `export_snapshots` rows already exist, changing canonical output under v1 may conflict with historical immutable snapshots.

Mitigation: implementation should check local/dev DB where possible. If stored rows exist and the fix changes their shape, stop for versioning adjudication. If no rows exist, keep v1 and document this as a canonical rule clarification.

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

- `Canonicalizer.java` diff is empty.
- OpenAPI MD5 remains `b7df19fdb69f8d22b2f0dbdbc845d95d`.
- migrations remain `17`.
- humanpending remains `146`.
- no-rule export does not contain null evidence fields.
- rule-bound export contains non-null `aiReviewRuleId`.

## Audit Notes

Implementation review should check:

- `promptVersionId` null is omitted.
- `aiReviewRuleId` null is omitted.
- non-null values are still serialized.
- old nullable export fields keep their current behavior.
- `SchemaService`, `PromptVersionService`, `AiReviewService`, `SessionService`, `DatasetImportService`, `AuditLogServiceImpl`, and `TaskService` hash paths are untouched.
