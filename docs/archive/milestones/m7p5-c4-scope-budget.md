# M7-P5 C4 Scope Budget

## 1. Purpose

M7-P5 C4 integrates offline draft safety into the labeler submit path. It preserves the existing P3a/P3b submit validation behavior while adding a submit-time local pending write, a best-effort draft pre-sync, branch-specific blocking for session/auth failures, and local pending cleanup only after submit succeeds. It is the first P5 cluster that intentionally touches `handleConfirmSubmit`.

## 2. Frozen Baseline

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 178 after C3 implementation |
| Migrations | 17 |
| humanpending | 157 |

C4 must leave OpenAPI, generated files, backend code, migrations, and humanpending unchanged. Frontend tests are expected to increase.

## 3. Scope

| Item | In / Out | Notes |
|---|---|---|
| Pre-submit local pending write | In | Before submit, persist current in-memory `answerPayload` with C2 `bufferPendingOfflineDraft`/`bufferPendingOfflineDraft`-backed hook using authenticated `userId`, `sessionId`, and session `schemaVersionId`. |
| Best-effort pre-sync | In | Attempt one draft sync before submit using C3 sync policy/engine. It may require exposing a submit-specific sync function/result from `useOfflineDraftSync`; do not alter C3's failure matrix internals. |
| Blocking matrix | In | Pre-sync 401/403/404/409 blocks submit; network/5xx does not block submit. |
| Submit success cleanup | In | On submit 201 success, call C1 store cleanup for that `(userId, sessionId)` before or alongside the existing Toast/navigate success path. |
| Submit failure retention | In | Submit 422/network/5xx keeps local pending. 422 must preserve P3a field error mapping. |
| P3a submit validation guard | In | Keep `SubmitValidationError -> fieldErrorsToStableIdMap -> setServerErrors` semantics intact. |
| Backend / OpenAPI / generated / migrations | Out | No contract/API/schema/migration/generated changes. |
| C3 sync internals rewrite | Out | C4 may expose a narrow submit-sync entry point if needed, but must not change C3 retry policy, lease, backoff, or failure classification behavior. |
| C2 hydrate / C1 store interface changes | Out | Do not change local-wins hydrate, record schema, or store interface. |
| Logout cleanup / encryption | Out | Remain deferred to security/cleanup work. |

## 4. Submit Sequence

| Step | Action | Result Handling |
|---|---|---|
| 1 | Keep existing `autosave.flush()` call. | Existing autosave behavior remains. |
| 2 | Keep existing `autosave.disable()` call. | Submit still freezes autosave before final submit. |
| 3 | Persist current in-memory `answerPayload` to local pending if `userId` exists and storage is available. | Storage unavailable or missing userId must not block submit. |
| 4 | Attempt one pre-sync of that pending draft. | Classification determines block/continue below. |
| 5 | If pre-sync returns 401/403/404/409. | Block submit, show session/auth/access/terminal message, and keep pending except 409 which is deleted by C3 policy. |
| 6 | If pre-sync returns network/abort/5xx/storage unavailable. | Continue submit with current in-memory `answerPayload`; local pending remains for retry. |
| 7 | Submit existing `answerPayload` via `submitMutation.mutateAsync`. | Data source remains in-memory payload, not server draft or local pending. |
| 8 | Submit 201 success. | Delete local pending with `deleteBySession`, then existing success Toast + navigate. |
| 9 | Submit 422. | Keep pending; existing `SubmitValidationError` mapping and UI remain unchanged. |
| 10 | Submit network/5xx/other failure. | Keep pending and show existing generic submit failure message. |

## 5. Blocking Matrix

| Pre-Sync Outcome | Submit? | Pending Action | Message |
|---|---:|---|---|
| 201/synced | Yes | Pending deleted by sync success; submit success cleanup is idempotent. | No extra blocking message. |
| Network/abort | Yes | Keep pending for C3 retry. | Existing submit flow proceeds. |
| 5xx | Yes | Keep pending for C3 retry. | Existing submit flow proceeds. |
| 400 | Yes | Keep blocked pending; this is a draft-cache issue, not submit payload validation. | Do not block submit; submit remains authoritative. |
| 401/403 | No | Keep pending as blocked/auth. | Show login/permission prompt; do not submit. |
| 404 | No | Keep pending as blocked/not_found. | Show session unavailable message; do not submit. |
| 409 | No | Delete pending via C3 terminal handling. | `此会话已在别处提交/释放,本地草稿已弃`; do not submit. |

Rationale: 401/403/404/409 indicate the session is unavailable or access is no longer trustworthy, so the final submit should not be attempted. Network/5xx are transport/server availability failures for the draft cache only; submit remains canonical because it carries the current in-memory answer payload.

## 6. Expected Files And Caps

### Hand-Written Cap

| Metric | Soft | Hard |
|---|---:|---:|
| Files touched | 5 | 7 |
| Net LOC | 450 | 650 |

Full-counting rule: created files count all lines; modified files count net added lines. If the soft cap is exceeded during implementation, stop and report before continuing. The hard cap is the true ceiling.

### Generated Churn

Generated churn is expected to be 0. Any generated diff is abnormal and must be reported.

### Expected Touch List

| Path | Action | Responsibility |
|---|---|---|
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Modify | Integrate submit sequence around `handleConfirmSubmit`; preserve existing 422 mapping and in-memory submit payload. |
| `apps/web/src/features/labeling/useOfflineDraftSync.ts` | Modify if needed | Expose a narrow submit-time sync result API, reusing existing `syncOfflineDraftRecord`/policy without changing C3 internals. |
| `apps/web/src/features/labeling/useOfflineDraftSync.test.tsx` | Modify if sync API exposed | Cover submit-sync result mapping and ensure C3 retry/lease behavior remains intact. |
| `apps/web/src/pages/labeler/LabelerSessionPage.submitOfflineDraft.test.tsx` | Create | Submit integration tests for pre-store, pre-sync branches, cleanup, 422 preservation, and fallback behavior. |
| `apps/web/src/features/labeling/useOfflineDraftBuffer.ts` | Modify only if unavoidable | Prefer no change. If submit needs a helper wrapper, document why and keep C2 hydrate behavior untouched. |

## 7. Out Of Scope

- Changing `useSubmitMutation`, submit API body, or server-side submit semantics.
- Changing `SubmitValidationError`, `fieldErrorsToStableIdMap`, `serverValidationErrors`, or visible field/linkage behavior.
- Changing C3 retry/backoff/lease/failure classification internals.
- Changing C2 hydrate local-wins behavior or C1 store interface/record schema.
- Adding strong submit sync guarantees beyond the one pre-sync attempt.
- Any backend/OpenAPI/generated/migration work.

## 8. Frozen Checks For Implementation

Implementation report must include:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \[" humanpending.md
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
git diff --stat
git diff --check
```

Expected anchors: MD5 remains `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations remain 17, humanpending remains 157, generated churn remains 0, frontend tests increase from 178.

