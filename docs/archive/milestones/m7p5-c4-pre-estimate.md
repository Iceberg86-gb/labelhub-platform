# M7-P5 C4 Pre-Estimate

## 1. Summary

C4 wires P5 offline draft safety into the final labeler submit path. It stores the current in-memory answer locally before submit, performs one best-effort draft pre-sync, blocks submit only for session/auth/access terminal pre-sync outcomes, preserves existing P3a submit validation behavior, and clears local pending only after submit success.

Frozen anchors for C4:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 178 after C3 implementation |
| Migrations | 17 |
| humanpending | 157 |

## 2. File Plan

| File | Action | Estimate | Responsibility |
|---|---|---:|---|
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Modify | +120 | Replace the linear submit handler with the C4 sequence: existing flush/disable, local pending write, pre-sync branch, existing submit, success cleanup, and unchanged 422 mapping. |
| `apps/web/src/features/labeling/useOfflineDraftSync.ts` | Modify if needed | +90 | Expose a narrow submit pre-sync API/result if page-level submit needs branch outcomes. Reuse `syncOfflineDraftRecord`, failure policy, lease, and invalidate behavior; do not change matrix internals. |
| `apps/web/src/features/labeling/useOfflineDraftSync.test.tsx` | Modify if needed | +80 | Add coverage for submit pre-sync result mapping without weakening C3 failure matrix/lease tests. |
| `apps/web/src/pages/labeler/LabelerSessionPage.submitOfflineDraft.test.tsx` | Create | 230 | Integration-style page/handler tests for blocking matrix, success cleanup, 422 preservation, storage unavailable fallback, and missing-user fallback. |
| `apps/web/src/features/labeling/useOfflineDraftBuffer.ts` | Modify only if unavoidable | +40 | Prefer no change. If needed, add a small helper for submit-time local pending write while keeping C2 hydrate behavior unchanged. |

Estimated net LOC: about 430. Cap: soft 450 / hard 650, files soft 5 / hard 7. Generated churn remains 0.

If implementation can avoid modifying `useOfflineDraftBuffer.ts`, it should. If exposing a submit pre-sync result from `useOfflineDraftSync` pushes file count/LOC above soft cap, stop and report rather than hiding the logic inside `LabelerSessionPage`.

## 3. Submit Handler Sequence

The target submit sequence is:

1. Validate that `sessionId` exists; otherwise return as today.
2. Run existing `await autosave.flush()`.
3. Run existing `autosave.disable()`.
4. Compute `finalPayload = answerPayload ?? EMPTY_ANSWER_PAYLOAD`.
5. If `userId`, session detail, and storage are available, write `finalPayload` to local pending using C2/C1 buffer/store.
6. Attempt one pre-sync for that session using C3 sync/policy.
7. If pre-sync outcome is auth/access/session blocking (`401`, `403`, `404`, `409`), show a specific message and return without submit.
8. If pre-sync outcome is network/abort/5xx/storage unavailable/400 draft-cache blocked, continue to submit.
9. Call existing `submitMutation.mutateAsync({ sessionId, answerPayload: finalPayload })`.
10. On submit success, delete local pending for `(userId, sessionId)` if possible, then run existing success Toast + navigation.
11. On `SubmitValidationError`, keep local pending and run existing `setServerErrors(fieldErrorsToStableIdMap(error.fieldErrors)); Toast.error(error.message); return;`.
12. On other submit errors, keep local pending and run existing generic Toast failure.

The submit payload remains the in-memory answer payload. C4 must not submit from local pending or latest server draft.

## 4. Pre-Sync Outcome Handling

Implementation should avoid duplicating C3's classification logic. Preferred approach:

- Add a narrow `syncPendingForSubmit(sessionId)` or similarly named function/result to `useOfflineDraftSync`.
- Internally reuse the existing store scan, lease, `syncOfflineDraftRecord`, `classifyOfflineDraftSyncFailure`, and query invalidation.
- Return a result small enough for submit branching:

```ts
type OfflineDraftSubmitPreSyncResult =
  | { kind: 'synced' | 'no-pending' | 'storage-unavailable' | 'missing-user' }
  | { kind: 'continue-with-pending'; reason: 'network' | 'server' | 'bad_request' }
  | { kind: 'block-submit'; reason: 'auth' | 'not_found' | 'terminal'; message: string };
```

Exact naming may differ, but the semantics must match the matrix below.

| Pre-Sync Result | Submit? | Pending Action |
|---|---:|---|
| no pending | Yes | No local action. |
| synced | Yes | Sync has cleared local pending. |
| storage unavailable / missing user | Yes | Offline layer disabled; keep existing submit behavior. |
| network/abort | Yes | Keep pending for C3 retry. |
| 5xx | Yes | Keep pending for C3 retry. |
| 400 | Yes | Keep blocked pending; submit remains authoritative and may return 422 if payload invalid. |
| 401/403 | No | Keep pending blocked/auth; prompt login/permission issue. |
| 404 | No | Keep pending blocked/not_found; prompt session unavailable. |
| 409 | No | Pending is deleted by C3 terminal handling; prompt terminal message. |

## 5. P3a/P3b Submit Guard

C4 must not alter these paths:

- `SubmitValidationError` class.
- `fieldErrorsToStableIdMap(error.fieldErrors)`.
- `setServerErrors(...)`.
- `serverValidationErrors` and visible field error derivation.
- Linkage/visible field evaluation.
- Submit request body shape.

For submit 422, the only C4 addition is that the local pending copy remains available. The error display and field mapping stay byte-for-byte equivalent in behavior.

## 6. UserId And Storage Degradation

- Use `getUser()?.id` for local pending write and cleanup.
- If userId is missing, skip local pending write/pre-sync/cleanup and use existing pure submit behavior.
- If IndexedDB/local store is unavailable, skip local pending write/pre-sync/cleanup and use existing pure submit behavior.
- These degradation paths must not block the user from submitting.

## 7. Cleanup Semantics

| Event | Cleanup? | Reason |
|---|---:|---|
| Pre-sync success before submit | Yes via C3 sync | Server draft has captured pending state, but final submit still proceeds with in-memory payload. |
| Submit 201 success | Yes, idempotently `deleteBySession(userId, sessionId)` | Submission is canonical; local recovery copy is no longer needed. |
| Submit 422 | No | User must correct fields; local copy remains useful. |
| Submit network/5xx/other failure | No | Work is not confirmed submitted. |
| Pre-sync 409 | Yes via C3 terminal handling | Session is no longer editable; pending must not be pushed. |
| Pre-sync 401/403/404/400/network/5xx | No | Preserve recovery copy unless terminal. |

## 8. Testing Plan

Use existing Vitest style and memory/mocked stores. Do not add dependencies.

Required tests:

- Pre-submit local pending write receives `userId`, `sessionId`, `schemaVersionId`, and current in-memory payload.
- Pre-sync 401 blocks submit and preserves pending.
- Pre-sync 403 blocks submit and preserves pending.
- Pre-sync 404 blocks submit and preserves pending.
- Pre-sync 409 blocks submit and deletes pending through terminal handling.
- Pre-sync network failure allows submit and keeps pending.
- Pre-sync 5xx allows submit and keeps pending.
- Pre-sync 400 allows submit and keeps blocked pending.
- Submit success deletes local pending and then navigates as before.
- Submit 422 keeps pending and preserves existing `SubmitValidationError` -> `fieldErrorsToStableIdMap` -> `setServerErrors` behavior.
- Submit generic failure keeps pending and shows existing generic failure toast.
- userId missing falls back to current submit flow and does not read/write local store.
- IndexedDB/storage unavailable falls back to current submit flow.
- `autosave.flush()` and `autosave.disable()` are still called before submit attempt.
- Submit request still uses current in-memory `answerPayload`.

Regression tests should assert the submit path does not import or call new backend/OpenAPI behavior and does not use local pending as the submit payload source.

## 9. Risks And STOP Conditions

- If implementing C4 requires backend changes, OpenAPI changes, generated changes, or migrations, STOP.
- If C4 requires changing C3 failure matrix/lease/backoff internals rather than exposing a narrow result, STOP and report.
- If C4 requires changing `useSubmitMutation` or submit API body shape, STOP.
- If P3a 422 mapping cannot be preserved, STOP.
- If the implementation cannot preserve `autosave.flush()`/`autosave.disable()` semantics, STOP.

## 10. Caps

| Metric | Soft | Hard |
|---|---:|---:|
| Hand-written files touched | 5 | 7 |
| Hand-written net LOC | 450 | 650 |
| Generated files | 0 expected | 0 expected |

Cap accounting uses full-counting for created files and net additions for modified files. Generated churn must remain 0.

## 11. Frozen Checks

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

Expected anchors: MD5 `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations 17, humanpending 157, generated churn 0, frontend tests increase from 178.

