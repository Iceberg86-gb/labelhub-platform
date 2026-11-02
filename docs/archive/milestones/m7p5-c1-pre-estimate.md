# M7-P5 C1 Pre-Estimate

## 1. Cluster Summary

C1 builds the local pending draft persistence foundation for P5. It is intentionally narrow: define the record, implement an IndexedDB adapter, provide an in-memory test double, and add cleanup helpers. It does not connect to autosave, labeler pages, submit, online/offline listeners, or logout.

Frozen anchors:

- OpenAPI MD5 `1acd96fb6c0fd0e7b084245d8ae3fa76`
- Frontend Vitest 147, expected to rise after C1 tests
- Migrations 17
- humanpending 157
- generated churn 0

## 2. File Plan

| File | Action | Estimate | Responsibility |
|---|---|---:|---|
| `apps/web/src/features/labeling/offlineDraft/offlineDraftTypes.ts` | create | 65 | Record type, constants, key builder, basic validation helpers. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftStore.ts` | create | 125 | IndexedDB adapter implementing async get/put/delete/list/deleteExpired. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftMemoryStore.ts` | create | 70 | In-memory implementation of the same interface for unit tests. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftCleanup.ts` | create if needed | 35 | Shared cleanup helpers if they would otherwise make the store file too broad. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftStore.test.ts` | create | 150 | CRUD, cross-user isolation, TTL cleanup, by-session delete, discard, memory-store parity. |

Estimated hand-authored total: 410 LOC if cleanup is split, or about 375 LOC if cleanup stays in the store file. This fits within the soft LOC cap. Implementation should keep helper names concise and avoid over-abstracting, but must not drop coverage to fit.

## 3. Cap

Generated churn is expected to be 0 and is tracked separately from hand-authored code.

| Metric | Soft | Hard |
|---|---:|---:|
| Hand-authored files | 5 | 7 |
| Hand-authored net LOC | 420 | 560 |
| Generated files | 0 | 0 |

The file cap matches the scope-budget. If the implementation estimate appears likely to exceed 420 LOC after concrete coding starts, report before exceeding the soft LOC cap. Do not move C2 wiring into C1 to justify more files.

## 4. Adapter Interface

Define a small interface, for example:

```ts
export interface OfflineDraftStore {
  get(userId: number, sessionId: number): Promise<OfflineDraftRecord | null>;
  put(record: OfflineDraftRecord): Promise<void>;
  delete(userId: number, sessionId: number): Promise<void>;
  listByUser(userId: number): Promise<OfflineDraftRecord[]>;
  deleteExpired(now: number, ttlMs?: number): Promise<number>;
}
```

The IndexedDB implementation should:

- Open one database, for example `labelhub-offline-drafts`, with one object store `pendingDrafts`.
- Use `user:${userId}:session:${sessionId}` as the primary key.
- Store `userId` and `sessionId` inside each record and validate them on read.
- Reject or ignore records whose `storageVersion` or `encoding` are not supported by C1.
- Throw a typed or clearly named `OfflineDraftStorageUnavailableError` when IndexedDB is missing or open fails.

IndexedDB unavailable/private-mode behavior:

- C1 only exposes errors; it does not wire fallback UI.
- Later clusters decide whether to show a warning and continue server-only autosave.
- Do not silently fall back to `sessionStorage`.

## 5. Record Schema And Key Rules

Record shape:

```ts
export const OFFLINE_DRAFT_STORAGE_VERSION = 1;
export const OFFLINE_DRAFT_ENCODING = 'plain-json-v1';

export type OfflineDraftEncoding = typeof OFFLINE_DRAFT_ENCODING;
export type OfflineDraftStatus = 'pending' | 'syncing' | 'blocked';
export type OfflineDraftBlockedReason = 'auth' | 'not_found' | 'terminal' | 'bad_request';

export type OfflineDraftRecord = {
  storageVersion: 1;
  encoding: OfflineDraftEncoding;
  userId: number;
  sessionId: number;
  schemaVersionId: number;
  payload: AnswerPayload;
  updatedAt: number;
  lastSyncAttemptAt: number | null;
  retryAfterAt: number | null;
  status: OfflineDraftStatus;
  blockedReason?: OfflineDraftBlockedReason;
};
```

Keying rules:

- Primary key must include `userId` and `sessionId`.
- `get(userId, sessionId)` must return `null` if the stored record exists but its `userId` does not match the requested user.
- No API should expose "get by session id only".
- Tests must prove two users can have records for the same session id without cross-hydration.

`schemaVersionId` is stored but not interpreted in C1. C2 uses it as a tripwire when hydrating against the session's bound schema version.

## 6. Cleanup Helpers

TTL helper:

- Default TTL: 7 days from `updatedAt`.
- Delete records where `updatedAt < now - ttlMs`.
- Return the count of deleted records for tests and future telemetry.

By-session helper:

- Delete exactly one `(userId, sessionId)` record.
- This is intended for submit success and 409 terminal cleanup in later clusters.

Discard helper:

- Alias or wrapper for by-session delete with user-intent naming.
- C1 only exposes it; it does not add UI.

Logout:

- C1 does not register these helpers with logout or token expiry events.
- This preserves the loss-prevention goal across auth expiry and avoids making a security policy decision inside the storage foundation.

## 7. In-Memory Test Double

The memory store must implement the same `OfflineDraftStore` interface. It should use the same key builder and validation helpers as the IndexedDB store so tests do not diverge from production keying.

Use it for most unit tests. If jsdom's IndexedDB support is missing or inconsistent, avoid adding a new dependency in C1; test the IndexedDB adapter through a thin mocked global or isolate production adapter tests to behavior that can run in the existing Vitest environment. If real IndexedDB cannot be exercised without new dependency, mark that as D-port and keep the interface and memory parity tests strong.

## 8. Privacy Boundary

C1 stores plaintext answer payloads in IndexedDB using `encoding: 'plain-json-v1'`. This is a deliberate scope decision:

- Browser-side encryption without external key management would provide limited protection.
- Memory-only keys would break crash/restart recovery, which is the goal of P5.
- C1 therefore implements lifecycle control and user/session isolation, not pseudo-encryption.

Future encryption migration hook:

- `storageVersion` starts at `1`.
- `encoding` starts at `'plain-json-v1'`.
- Future security clusters may add `encrypted-json-v1` and a migration path without changing keying or consumers.

Planned humanpending watch:

`[M7-P5 watch] IndexedDB plaintext answer payload security hardening deferred. P5 v1 stores local pending answer drafts as plaintext JSON scoped by userId + sessionId with TTL and cleanup helpers. True browser-side encryption and logout cleanup UX are deferred to a dedicated security cluster; C1 records include storageVersion and encoding to support a future encrypted-json migration.`

## 9. Tests

Required tests:

- `buildOfflineDraftKey(1, 2)` contains both user and session ids and has no session-only lookup path.
- `put` then `get` returns the record for the same user/session.
- Same session id under different users returns the matching user's record only.
- Mismatched stored user id is not returned.
- `delete(userId, sessionId)` removes only that record.
- `deleteExpired(now, ttl)` removes old records and keeps fresh records.
- `discard(userId, sessionId)` removes the pending record.
- Unsupported `storageVersion` or `encoding` is rejected or ignored predictably.
- Memory store and production store interface semantics match for core operations.

No test should import or render `LabelerSessionPage`, call `useAutosave`, call `useSaveDraftMutation`, or touch submit. Those are later clusters.

## 10. STOP Conditions

Stop and report if:

- Implementing the adapter requires backend or OpenAPI changes.
- A generated file changes.
- C1 needs to modify `LabelerSessionPage`, `useAutosave`, or `useSaveDraftMutation`.
- The only viable test strategy requires adding a new IndexedDB dependency; this should be a gate decision, not a hidden dependency change.
- The storage key cannot reliably include authenticated `userId` in future wiring.

## 11. Frozen Checks For Implementation Report

Implementation should report:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml      # remains 1acd96fb6c0fd0e7b084245d8ae3fa76
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l   # remains 17
grep -cE "^- \\[" humanpending.md   # remains 157 unless watch is intentionally appended later
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
git diff --stat
git diff --check
```

Implementation should also state actual hand-authored files/LOC versus caps and confirm generated churn is 0.
