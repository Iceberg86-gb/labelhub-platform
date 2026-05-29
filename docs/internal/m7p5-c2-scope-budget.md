# M7-P5 C2 Scope Budget

## 1. Purpose

M7-P5 C2 wires the C1 offline draft store into the labeler answer page. It adds a small `useOfflineDraftBuffer` composition layer, uses local pending drafts during initial hydration when safe, writes local pending drafts when server autosave fails, and exposes a local-buffered status in the existing autosave status tag. It does **not** add a sync engine, retry loop, submit integration, backend changes, or OpenAPI changes.

## 2. Frozen Baseline

| Anchor | Must Remain |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 157 -> expected to rise after C2 tests |
| Migrations | 17 |
| humanpending | 157 |
| generated churn | 0 |

C2 is pure frontend wiring. It must not modify backend files, OpenAPI, migrations, generated `schema.d.ts`, submit behavior, sync engine behavior, logout wiring, or P3a/P3b validation logic.

## 3. In Scope

| Item | Scope |
|---|---|
| `useOfflineDraftBuffer` | Add a composition hook around the C1 store. It handles local hydration decisions, server-save failure buffering, storage-unavailable degradation, and local buffer status. |
| Hydrate local pending wins | In `LabelerSessionPage`, before the current server draft fallback hydrates `answerPayload`, check `(userId, sessionId)` local pending. Matching `schemaVersionId` hydrates local pending; mismatch blocks local hydration; no pending falls back to the existing server chain. |
| Autosave failure buffering | When `useSaveDraftMutation` fails inside the autosave `onSave`, write the current payload to the C1 store as a pending local draft, then rethrow so existing autosave error semantics remain intact. |
| Status display | Extend `AutosaveStatusTag` to show a local-buffered state such as `本地已暂存,未同步` when server autosave failed but the payload is safely stored locally. |
| IndexedDB unavailable degradation | If `OfflineDraftStorageUnavailableError` occurs, fall back to the existing server-only behavior without crashing or blocking labeling. |
| Tests | Cover local pending hydration, schema mismatch blocking, no-pending server fallback, autosave failure buffering, IndexedDB unavailable degradation, missing user behavior, and submit-path non-interference. |

## 4. Out Of Scope

- Online/offline detection.
- Retry/backoff sync engine.
- Multi-tab coordination, `storage` events, or `BroadcastChannel`.
- Submit preflight changes or submit-before-sync decisions.
- Changes to `useSubmitMutation`, `SubmitValidationError`, `serverValidationErrors`, or submit 422 handling.
- Cleanup wiring to logout, token expiry, submit success, or lifecycle events.
- Encryption.
- Backend, OpenAPI, generated types, or migrations.

## 5. Existing Connection Points

Confirmed code paths:

- `apps/web/src/pages/labeler/LabelerSessionPage.tsx`: current hydrate path is `coerceAnswerPayload(draftQuery.data?.payload ?? detail?.latestDraft?.payload ?? EMPTY_ANSWER_PAYLOAD)`.
- `apps/web/src/pages/labeler/LabelerSessionPage.tsx`: autosave `onSave` calls `saveDraftMutation.mutateAsync({ sessionId, payload })`.
- `apps/web/src/features/labeling/useAutosave.ts`: debounce 3s, maxWait 15s, flush, and disable are stable and should remain unchanged.
- `apps/web/src/features/labeling/AutosaveStatusTag.tsx`: existing states are idle, saving, saved, and error.
- `apps/web/src/shared/api/auth-storage.ts`: authenticated user id comes from `getUser()?.id`.

## 6. Hydration Scope

Hydration must follow this order:

1. If there is no authenticated `userId`, do not read local storage. Use the existing server draft chain unchanged.
2. If local storage is unavailable, use the existing server draft chain unchanged.
3. If local pending exists and its `schemaVersionId` matches `detail.schemaVersion.id`, hydrate from local pending and mark the page as restored from unsynced local draft.
4. If local pending exists but its `schemaVersionId` does not match the session schema version, do not hydrate it. Show or expose a blocked/corruption-tripwire state, then fall back to the server chain.
5. If no local pending exists, use the existing server chain unchanged.

The fallback chain must preserve the current behavior: `coerceAnswerPayload(draftQuery.data?.payload ?? detail?.latestDraft?.payload ?? EMPTY_ANSWER_PAYLOAD)`.

## 7. Autosave Failure Scope

C2 should not change `useAutosave` debounce, maxWait, flush, disable, or scheduling. Preferred design:

- Add `useOfflineDraftBuffer`.
- In `LabelerSessionPage`, wrap the existing autosave `onSave` callback.
- On server draft save failure, call `offlineDraftBuffer.bufferPending(payload, error)` and then rethrow the original error.
- `useAutosave` continues to set `status='error'`, preserving the existing error path.
- `AutosaveStatusTag` receives optional offline buffer status and displays local-buffered status when the payload is safely stored locally.

This keeps C2 additive. If implementation proves this wrapper cannot preserve correct status display, STOP and report before modifying `useAutosave` internals.

## 8. Cap

Generated churn is expected to be 0 and is tracked separately from hand-authored code.

| Metric | Soft | Hard |
|---|---:|---:|
| Hand-authored files | 6 | 8 |
| Hand-authored net LOC | 500 | 700 |
| Generated files | 0 | 0 |

If implementation naturally exceeds a soft cap, stop and report before continuing. The hard cap is the true ceiling.

## 9. Expected Files

Expected create/modify paths:

- Create: `apps/web/src/features/labeling/useOfflineDraftBuffer.ts`
- Create: `apps/web/src/features/labeling/useOfflineDraftBuffer.test.ts`
- Modify: `apps/web/src/pages/labeler/LabelerSessionPage.tsx`
- Modify: `apps/web/src/features/labeling/AutosaveStatusTag.tsx`
- Modify or create tests around `AutosaveStatusTag` or `LabelerSessionPage` only if needed to lock the new local-buffered state.

No implementation should modify `useSubmitMutation`, `useSaveDraftMutation`, backend files, generated files, or OpenAPI.

## 10. Guardrails

- No pending means behavior is unchanged: server draft hydration, payload validation, visible field errors, server validation errors, and submit behavior must continue through the existing paths.
- `schemaVersionId` is a tripwire only; mismatch blocks local hydration and does not attempt migration.
- `userId` is mandatory for local read/write. Missing `userId` means no local storage access.
- C2 does not bind cleanup to logout, token expiry, online/offline, or submit events.
- P3a/P3b guards remain intact: `AnswerPayloadValidator`, visible-field linkage behavior, `serverValidationErrors`, and submit 422 handling are out of scope.
