# M7-P5 C1 Scope Budget

## 1. Purpose

M7-P5 C1 creates the local persistence foundation for labeler answer drafts. It adds an IndexedDB-backed pending draft store, an in-memory test double, and cleanup helpers. It does **not** wire the store into `LabelerSessionPage`, `useAutosave`, `useSaveDraftMutation`, submit, UI, or any sync engine.

## 2. Frozen Baseline

| Anchor | Must Remain |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 147 -> expected to rise after C1 tests |
| Migrations | 17 |
| humanpending | 157 |
| generated churn | 0 |

C1 is pure frontend/local-storage code. It must not modify backend files, OpenAPI, migrations, generated `schema.d.ts`, labeler page wiring, `useAutosave`, or `useSaveDraftMutation`.

## 3. In Scope

| Item | Scope |
|---|---|
| IndexedDB adapter | Add async get/put/delete/list helpers for local pending draft records, hiding browser API details behind a small interface. |
| Record schema | Define the pending draft record with user/session keying, payload metadata, `storageVersion`, and `encoding`. |
| Keying | Require `userId + sessionId` as the storage identity; record reads must verify user id before returning data. |
| Cleanup helpers | Add TTL cleanup, by-session delete, and explicit discard helpers. These are helper functions only; no logout/event wiring in C1. |
| In-memory test double | Add a test double implementing the same adapter interface so unit tests do not require real IndexedDB. |
| Tests | Cover adapter CRUD, cross-user isolation, TTL cleanup, by-session delete, explicit discard, and test double parity. |

## 4. Out Of Scope

- UI changes or labeler page wiring.
- `useAutosave` changes.
- `useSaveDraftMutation` changes.
- Hydration from local pending draft.
- Online/offline detection.
- Retry/backoff sync engine.
- Multi-tab coordination, BroadcastChannel, or sync leases.
- Submit integration.
- Logout wiring.
- Encryption.
- Backend, OpenAPI, generated types, or migrations.

## 5. Record Schema

C1 stores plaintext JSON with lifecycle controls and future migration hooks:

| Field | Type | Required | Notes |
|---|---|---:|---|
| `storageVersion` | `number` | yes | Starts at `1`; reserved for future migrations. |
| `encoding` | `'plain-json-v1'` | yes | Fixed in C1; reserves space for future `encrypted-json-v1`. |
| `userId` | `number` | yes | Part of the storage key and read-time guard. |
| `sessionId` | `number` | yes | Part of the storage key. |
| `schemaVersionId` | `number` | yes | Stored as a tripwire for C2 hydration; C1 only persists it. |
| `payload` | `AnswerPayload` | yes | Plain JSON answer payload. |
| `updatedAt` | `number` | yes | Epoch milliseconds for last local update and TTL cleanup. |
| `lastSyncAttemptAt` | `number \| null` | yes | Reserved for later sync clusters. |
| `retryAfterAt` | `number \| null` | yes | Reserved for later sync clusters. |
| `status` | `'pending' \| 'syncing' \| 'blocked'` | yes | C1 mainly writes `pending`; other values support later clusters. |
| `blockedReason` | `'auth' \| 'not_found' \| 'terminal' \| 'bad_request' \| undefined` | no | Reserved for later failure matrix handling. |

Suggested key string: `user:${userId}:session:${sessionId}`. The adapter may use this as the IndexedDB primary key while preserving `userId` and `sessionId` fields inside the record for defensive validation.

## 6. Cleanup Policy

TTL: **7 days** from `updatedAt`.

Rationale: 7 days protects normal browser crashes, travel/offline windows, and short interruptions without allowing local answer payloads to accumulate indefinitely. It also matches the RESEARCH recommendation for blocked/unsynced records.

Helpers:

- `deleteExpired(now: number, ttlMs = 7 * 24 * 60 * 60 * 1000): Promise<number>`
- `deleteBySession(userId: number, sessionId: number): Promise<void>`
- `discard(userId: number, sessionId: number): Promise<void>` as a user-intent alias over by-session delete

C1 does not call these helpers from logout, submit, online events, or page lifecycle. Later clusters wire those lifecycle events.

## 7. Privacy And Security Boundary

C1 intentionally stores plaintext JSON in IndexedDB. This is not encryption and should not be described as encryption. Risk is reduced by:

- User-scoped keying.
- Read-time user id validation.
- TTL cleanup.
- Explicit deletion helpers.
- `storageVersion` and `encoding` migration hooks for future encryption.

True encryption and logout cleanup policy are deferred to a security follow-up. The planned R8/watch wording is:

`[M7-P5 watch] IndexedDB plaintext answer payload security hardening deferred. P5 v1 stores local pending answer drafts as plaintext JSON scoped by userId + sessionId with TTL and cleanup helpers. True browser-side encryption and logout cleanup UX are deferred to a dedicated security cluster; C1 records include storageVersion and encoding to support a future encrypted-json migration.`

## 8. Cap

Generated churn is expected to be 0 and is tracked separately from hand-authored code.

| Metric | Soft | Hard |
|---|---:|---:|
| Hand-authored files | 5 | 7 |
| Hand-authored net LOC | 420 | 560 |
| Generated files | 0 | 0 |

If implementation naturally exceeds a soft cap, stop and report before continuing. The hard cap is the true ceiling.

## 9. Expected Files

Create-only expected paths:

- `apps/web/src/features/labeling/offlineDraft/offlineDraftTypes.ts`
- `apps/web/src/features/labeling/offlineDraft/offlineDraftStore.ts`
- `apps/web/src/features/labeling/offlineDraft/offlineDraftMemoryStore.ts`
- `apps/web/src/features/labeling/offlineDraft/offlineDraftCleanup.ts` if cleanup is not small enough to live with the store
- `apps/web/src/features/labeling/offlineDraft/offlineDraftStore.test.ts`

No existing production file should need modification in C1. If wiring requires modifying `LabelerSessionPage`, `useAutosave`, or API hooks, that is C2 scope and should STOP.
