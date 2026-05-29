# M7-P5 C3 Pre-Estimate

## 1. Summary

C3 introduces an offline draft synchronization engine for labeler answer drafts. It consumes the C1 `OfflineDraftStore` and C2 buffer layer, synchronizes pending records to the existing append-only draft endpoint, and coordinates multiple tabs. It is pure frontend work and must not alter backend contracts, submit flow, generated files, migrations, or the C1/C2 interfaces.

Frozen anchors for C3:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 166 after C2 implementation report |
| Migrations | 17 |
| humanpending | 157 |

## 2. File Plan

| File | Action | Estimate | Responsibility |
|---|---|---:|---|
| `apps/web/src/features/labeling/offlineDraft/offlineDraftSyncPolicy.ts` | Create | 170 | Pure helpers for HTTP/error classification, blockedReason mapping, retryAfterAt/backoff calculation, and sync outcome records. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftSyncLease.ts` | Create | 140 | Single-tab lease helper with owner id, expiry, acquire/release/refresh semantics, and stale lease replacement. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftSyncChannel.ts` | Create | 105 | BroadcastChannel wrapper with message types and no-op fallback when unavailable. |
| `apps/web/src/features/labeling/useOfflineDraftSync.ts` | Create | 250 | React hook for current user pending scan, trigger registration, manual retry, lease-protected sync, invalidation, and status exposure. |
| `apps/web/src/features/labeling/useOfflineDraftSync.test.tsx` | Create | 300 | Failure matrix, backoff, manual retry, trigger, lease, BroadcastChannel fallback, and user isolation coverage. |
| `apps/web/src/features/labeling/AutosaveStatusTag.tsx` | Modify | +35 | Render retryable/blocked/syncing status and manual retry if UI plumbing is placed in the existing autosave tag. |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Modify | +45 | Mount `useOfflineDraftSync` with `getUser()?.id`, `sessionId`, and current status; do not touch submit. |

Estimated hand-written total: 1045 file lines before netting deletes, but expected net LOC is about 820 after keeping tests compact and status UI small. Cap remains soft 850 / hard 1150. Generated churn remains 0.

If implementation discovers the status UI can be rendered entirely through existing `AutosaveStatusTag` props without modifying the page beyond hook mount, it may reduce LOC. If lease/channel helpers need more than estimated, do not compress them into one unreadable file; report cap pressure.

## 3. Sync Engine Design

### Hook Interface

Proposed hook:

```ts
type OfflineDraftSyncStatus =
  | { kind: 'idle' }
  | { kind: 'syncing'; sessionId: number }
  | { kind: 'retry-scheduled'; sessionId: number; retryAfterAt: number }
  | { kind: 'blocked'; sessionId: number; reason: OfflineDraftBlockedReason }
  | { kind: 'terminal-cleared'; sessionId: number };

function useOfflineDraftSync(options?: {
  store?: OfflineDraftStore;
  saveDraft?: (sessionId: number, payload: AnswerPayload) => Promise<unknown>;
  now?: () => number;
  ownerId?: string;
  intervalMs?: number;
}): {
  status: OfflineDraftSyncStatus;
  syncNow: (sessionId?: number) => Promise<void>;
  retryPending: (sessionId: number) => Promise<void>;
};
```

Production should default to:

- `createIndexedDbOfflineDraftStore()`.
- Authenticated user from `getUser()?.id`.
- Existing draft PUT path. It can either reuse mutation-like API client logic or call `apiClient.PUT` directly inside the sync engine. It must preserve `useSaveDraftMutation` behavior by invalidating `latestDraftQueryKey(sessionId)` and `['my', 'sessions']` after success.

The hook should:

1. Read pending records with `store.listByUser(userId)`.
2. Skip records whose `retryAfterAt` is in the future unless manual retry requested.
3. Acquire the per `(userId, sessionId)` sync lease.
4. Mark the record `syncing` with `lastSyncAttemptAt=now`.
5. Call `PUT /sessions/{sessionId}/draft` with the latest record payload.
6. On 201, `deleteBySession` and invalidate draft/session queries.
7. On failure, classify and update/delete the record according to the matrix.
8. Release the lease and broadcast the result.

## 4. Failure Matrix

| Failure | Record Update | Retry | User Status |
|---|---|---|---|
| 201 success | `deleteBySession(userId, sessionId)` | No | synced/saved |
| Network error/request aborted | `status='pending'`, set `lastSyncAttemptAt`, set `retryAfterAt` | Yes | retry scheduled/offline |
| 5xx | `status='pending'`, set `retryAfterAt` | Yes | temporary server error |
| 400 | `status='blocked'`, `blockedReason='bad_request'`, clear `retryAfterAt` | No auto retry | "草稿格式异常,请继续编辑或稍后重试" |
| 401/403 | `status='blocked'`, `blockedReason='auth'`, clear `retryAfterAt` | No blind retry | auth/permission warning, preserve pending |
| 404 | `status='blocked'`, `blockedReason='not_found'`, clear `retryAfterAt` | No auto retry | session unavailable, preserve until TTL/discard |
| 409 | Delete record | No | "此会话已在别处提交/释放,本地草稿已弃" |

Rationale:

- 409 means the session is terminal/not editable. Keeping the record would cause a permanent retry loop against `SessionNotEditableException`.
- 401/403 may be transient auth state. Preserve the pending draft but stop blind retry until auth/user action.
- 404 could be hidden by permission or deleted/unavailable session. Preserve for TTL/explicit discard because it is not safe to prove user intent to drop work.
- 400 is a local payload/client inconsistency. Preserve but block automatic retry until payload changes or manual action.

## 5. Backoff Algorithm

Use a bounded exponential backoff:

```ts
const RETRY_DELAYS_MS = [5_000, 10_000, 20_000, 40_000, 60_000];
```

C3 does not need to add a retry counter to the C1 record schema. It can compute an approximate next delay from the existing `lastSyncAttemptAt`/`retryAfterAt` or maintain an in-memory attempt map for the page lifetime. The durable safety requirement is `retryAfterAt`: if it is in the future, normal background sync must skip the record.

Manual retry may bypass the waiting period, but it must still acquire the lease before calling PUT.

The 5 second first retry is the floor and aligns with the OpenAPI throttle guidance. Never retry more frequently than 5 seconds per `(userId, sessionId)` from background triggers.

## 6. Multi-Tab Coordination

### Lease

Lease helper API:

```ts
type OfflineDraftSyncLease = {
  ownerId: string;
  expiresAt: number;
};

async function acquireOfflineDraftSyncLease(
  storeOrLeaseStorage: LeaseStorage,
  key: { userId: number; sessionId: number },
  ownerId: string,
  now: number,
  ttlMs: number,
): Promise<boolean>;
```

Implementation can keep lease metadata in a separate localStorage key or a small IndexedDB metadata record. If using localStorage, it must be scoped by userId/sessionId and must tolerate JSON parse errors by replacing stale/corrupt leases. Lease duration should be 15 seconds.

The lease is a coordination guard, not a security mechanism. It prevents duplicate PUT attempts from multiple tabs under normal browser behavior.

### BroadcastChannel

Channel name: `labelhub-offline-drafts`.

Messages:

```ts
type OfflineDraftSyncMessage =
  | { type: 'pending-updated'; userId: number; sessionId: number; updatedAt: number }
  | { type: 'sync-started'; userId: number; sessionId: number }
  | { type: 'sync-succeeded'; userId: number; sessionId: number }
  | { type: 'sync-blocked'; userId: number; sessionId: number; reason: OfflineDraftBlockedReason }
  | { type: 'sync-failed'; userId: number; sessionId: number; retryAfterAt: number };
```

If BroadcastChannel is unavailable, use no-op channel behavior plus visibility/interval polling. This is acceptable for v1 and should be recorded as D-port in later browser verification.

## 7. Trigger Sources

C3 should wire:

- `window.addEventListener('online', ...)`: call `syncNow`.
- `document.visibilitychange`: when visible, call `syncNow`.
- Capped interval: only while current user has pending records; suggested 30 seconds. Each record still respects its `retryAfterAt`.
- Manual retry: exposed via sync status UI.
- BroadcastChannel `pending-updated` or `sync-failed`: schedule a sync check, respecting lease and `retryAfterAt`.

C3 must **not** wire submit-time strong sync. C4 owns submit integration.

## 8. UI And Status

Status must be honest:

- "本地草稿同步中" for active sync.
- "本地草稿待同步" or "稍后自动重试" for retry-scheduled pending.
- "本地草稿无法同步" for blocked states, with reason-specific tooltip/message.
- 409 terminal clearing should explicitly say the local draft was discarded because the session is no longer editable.

Do not imply server save success for a locally pending record.

Manual retry should call the sync hook, not trigger submit or change the in-memory answer payload.

## 9. Integration Points

### `LabelerSessionPage`

Mount the sync hook with `getUser()?.id` and current `sessionId`. Keep the existing `handleConfirmSubmit`, `autosave.flush`, `autosave.disable`, `useSubmitMutation`, `SubmitValidationError`, `fieldErrorsToStableIdMap`, and server validation error mapping unchanged.

### `AutosaveStatusTag`

If used for sync status, it should receive a new optional sync-status prop. Existing C2 local-buffered/local-restored/blocked states must remain available. Do not collapse sync status into `autosave.status='saved'`.

### Queries

On sync success, invalidate:

- `latestDraftQueryKey(sessionId)`.
- `['my', 'sessions']`.

This mirrors `useSaveDraftMutation` and keeps server draft UI/data coherent.

## 10. Testing Plan

Tests should use the C1 memory store and mocked saveDraft function. Do not add a new IndexedDB dependency.

Required test cases:

- Sync success clears local pending and invalidates latest draft + my sessions query keys.
- Network error keeps pending and sets `retryAfterAt`.
- 5xx keeps pending and sets `retryAfterAt`.
- 400 marks `blocked/bad_request` and does not auto retry.
- 401 and 403 mark `blocked/auth` and preserve pending.
- 404 marks `blocked/not_found` and preserves pending.
- 409 deletes pending and does not retry.
- `retryAfterAt` in the future skips normal sync.
- Manual retry bypasses `retryAfterAt` but still requires lease.
- Lease prevents two sync attempts for the same `(userId, sessionId)`.
- BroadcastChannel unavailable falls back without crashing.
- `online` and visibility visible triggers call sync.
- UserId missing does not sync.
- Submit flow is not imported or called.

Existing C2 tests must continue to pass: hydrate local-wins, no-pending server fallback, schema mismatch blocked, storage-unavailable fallback, autosave failure buffering and rethrow.

## 11. Risks And STOP Conditions

- If implementing C3 requires a backend field, new OpenAPI error body, or new endpoint, STOP.
- If submit handling must change, STOP; C4 owns it.
- If the C1 `OfflineDraftStore` interface or record schema must change, STOP.
- If a reliable lease is impossible without adding a dependency or backend state, STOP and report.
- Browser behavior for BroadcastChannel, visibility, online events, and actual multi-tab lease timing is D-port until browser verification.
- If any generated file changes, STOP and investigate; C3 expects generated churn 0.

## 12. Caps

| Metric | Soft | Hard |
|---|---:|---:|
| Hand-written files touched | 8 | 11 |
| Hand-written net LOC | 850 | 1150 |
| Generated files | 0 expected | 0 expected |

Cap accounting uses full-counting for created files and net additions for modified files. If soft cap pressure appears, report rather than compressing lease/channel/policy into unreadable code.

## 13. Frozen Checks

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

Expected anchors: MD5 `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations 17, humanpending 157, generated churn 0, frontend tests increase from 166.
