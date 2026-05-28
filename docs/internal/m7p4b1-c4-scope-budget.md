# M7-P4b1 C4 Scope Budget

## Objective

Add integration coverage for P4b1 active AI review rule runtime behavior, and audit the export canonicalization impact of C3's new `aiReviewRuleId` evidence field.

C4 is a verification cluster. It should prove the C3 runtime path against real database state and should not change production behavior. If the export canonical audit exposes a production invariance issue, C4 should stop and report it for adjudication instead of silently fixing it.

## Anchor

- HEAD: `6a280b3` (`M7-P4b1 C3`)
- OpenAPI MD5: `b7df19fdb69f8d22b2f0dbdbc845d95d`
- Backend tests: `534 / 84`
- Frontend tests: `131`
- Migrations: `17`
- humanpending: `146`

## In Scope

### Runtime Integration Tests

Extend the existing `AiReviewIntegrationTest` because it already owns the HTTP trigger/provenance path and has helpers for submissions, prompt versions, legacy AI calls, and P4a idempotency assertions.

Add coverage for:

- active rule wins over request `promptVersionId`;
- no-rule task preserves exact P4a promptVersionId fallback and idempotency key;
- no-rule key and rule-bound key are isolated for the same submission;
- dangling `tasks.current_ai_review_rule_id` returns 404 through the real service path;
- mixed provenance returns legacy/null-rule and rule-bound AI calls together.

### Export Canonical Audit

Add a focused export canonical characterization test, most likely in `ExportServiceTest`, because it already builds an `ExportFactBundle`, captures written files, and inspects export hashes without Docker.

Audit:

- whether `aiReviewRuleId = null` appears in `ai-calls.jsonl`;
- whether a no-rule/legacy export canonical shape changes solely because C3 added the key;
- whether `CANONICALIZATION_VERSION` remaining `labelhub-canonical-v1` is still acceptable.

If the audit shows null `aiReviewRuleId` changes canonical shape while the version remains `v1`, C4 must stop with a report. Candidate follow-up decisions are version bump, omitting null evidence fields, or explicitly accepting the hash change.

## Out Of Scope

- Production code changes in `AiReviewService`, `AiReviewRuleService`, mappers, DTO mappers, `ExportArtifactBuilder`, or frontend.
- OpenAPI changes.
- Migration changes.
- humanpending updates.
- P3a/P3b changes.
- Fixing export canonicalization during C4 without a new adjudication.

## Decision Summary

| Decision | Recommendation |
|---|---|
| Test organization | Extend `AiReviewIntegrationTest`; add focused export test in `ExportServiceTest` |
| Active rule setup | Use real save + publish HTTP path where possible |
| Dangling setup | Directly set `tasks.current_ai_review_rule_id` to a missing id |
| Export audit order | Do canonical characterization first during implementation |
| Business code changes | Forbidden unless C4 stops for adjudication |
| Docker behavior | Testcontainers cases may skip locally; committed tests remain permanent guards |

## Line Budget

| Item | Estimate |
|---|---:|
| `AiReviewIntegrationTest` active/no-rule/dangling/mixed cases | 230 |
| Integration helpers for save/publish active rule and task pointer setup | 70 |
| Export canonical characterization test | 90 |
| Small assertion/helper cleanup | 35 |
| Total hand-authored | ~425 |

Cap proposal:

- Soft cap: 450 hand-authored lines.
- Hard cap: 650 hand-authored lines.

## Risk Register

### Export canonical version drift

Risk: `ExportArtifactBuilder.aiCallToCanonical(...)` now puts `aiReviewRuleId` into the canonical map. `Canonicalizer` does not configure null exclusion, so null values likely serialize. That can change no-rule export hashes while `CANONICALIZATION_VERSION` remains `labelhub-canonical-v1`.

Mitigation: C4 starts with a focused characterization test. If the result indicates drift, stop and report.

### Integration tests accidentally bypass runtime

Risk: active rule tests hand-construct entities and miss the DB pointer path.

Mitigation: use real HTTP save/publish for normal active rule cases; use direct SQL only for the intentional dangling-pointer corruption case.

### Legacy and no-rule behavior regression

Risk: rule-bound idempotency changes accidentally invalidate no-rule P4a behavior.

Mitigation: include an explicit no-rule fallback assertion and a no-rule-before-rule key isolation scenario.

### Docker D-口径

Risk: `AiReviewIntegrationTest` uses Testcontainers and may skip in this sandbox.

Mitigation: committed test code is still permanent; report skip honestly. The export canonical unit test should be runnable without Docker.

## Verification Plan

Gate implementation should eventually report:

```bash
mvn -pl services/api -Dtest=ExportServiceTest test
mvn -pl services/api -Dtest=AiReviewIntegrationTest test
mvn -pl services/api test
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \\[" humanpending.md
git status --short
```

Expected if no export STOP is triggered:

- OpenAPI MD5 remains `b7df19fdb69f8d22b2f0dbdbc845d95d`.
- migrations remain `17`.
- humanpending remains `146`.
- business code diffs are empty.
- backend tests rise modestly from `534 / 84`.

## Audit Notes

Implementation audit should verify:

- C4 only changes test files unless it stops for export adjudication.
- Active-rule tests verify the rule prompt version wins over request prompt version.
- No-rule tests verify the P4a key has no `ruleVersionId` segment.
- Key isolation creates two rows when a rule is published after a no-rule trigger.
- Dangling pointer returns 404 through `GlobalExceptionHandler`.
- Mixed provenance contains both null and non-null `aiReviewRuleId` rows.
- Export audit states whether `aiReviewRuleId:null` is serialized and whether canonicalization version handling is safe.
