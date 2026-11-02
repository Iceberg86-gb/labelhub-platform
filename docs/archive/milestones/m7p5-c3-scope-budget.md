# M7-P5 C3 Scope Budget

## 1. Purpose

M7-P5 C3 adds the offline draft synchronization layer on top of the C1 store and C2 buffer. It synchronizes local pending answer drafts back to the existing append-only `PUT /sessions/{sessionId}/draft` path, classifies save failures, coordinates multiple tabs, and exposes retry/status hooks for the labeler page. It does **not** change submit behavior, backend contracts, migrations, generated types, or the C1/C2 hydrate/store interfaces.

## 2. Frozen Baseline

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 166 after C2 implementation report |
| Migrations | 17 |
| humanpending | 157 |

C3 must leave OpenAPI, generated files, backend code, migrations, and humanpending unchanged. Frontend tests are expected to increase.

## 3. Scope

| Item | In / Out | Notes |
|---|---|---|
| Sync engine hook | In | Create `useOfflineDraftSync` or equivalent to list pending records for the current user, acquire a sync lease, call the existing draft PUT path, update record status, and clear on success. |
| Trigger sources | In | Online event, visibility returning to visible, capped interval while pending exists, and manual retry. Submit-time strong sync is out of scope. |
| Failure matrix | In | Classify 400 / 401 / 403 / 404 / 409 / network / 5xx into clear, blocked, or retryable outcomes. |
| Backoff and throttling | In | Use `retryAfterAt` and `lastSyncAttemptAt`; respect the append-only draft contract and avoid retry storms. |
| Multi-tab coordination | In | BroadcastChannel when available plus a short sync lease so only one tab PUTs a given `(userId, sessionId)` draft at a time. |
| Labeler page sync wiring | In | Mount the sync hook, pass current `userId`, surface manual retry/status, and do not alter submit. |
| Autosave status UI | In | Add sync-specific status/manual retry affordance only if needed; keep existing autosave states and C2 offline states honest. |
| Submit integration | Out | C4 owns pre-submit sync, submit-success cleanup, 422 preservation, and submit failure behavior. |
| Backend / OpenAPI / generated / migrations | Out | No contract, API, schema, migration, or generated type changes. |
| C1 store interface changes | Out | C3 may write existing `status`, `blockedReason`, `retryAfterAt`, and `lastSyncAttemptAt`; it must not change the store interface or record schema. |
| C2 hydrate decision changes | Out | Local-wins hydrate, schema mismatch blocking, and storage-unavailable fallback remain unchanged. |

## 4. Failure Matrix

| Failure | Keep Pending? | Retry? | Record State | User Message / Action |
|---|---:|---:|---|---|
| 201 success | No | No | Delete local record with `deleteBySession`; invalidate latest draft and my sessions. | Show synced/saved state via existing autosave/sync status. |
| Network error / request aborted | Yes | Yes, with backoff | `status='pending'`, `retryAfterAt` set. | Show pending/offline; manual retry available. |
| 5xx | Yes | Yes, with backoff | `status='pending'`, `retryAfterAt` set. | Temporary server error; keep local draft. |
| 400 | Yes, blocked | No automatic retry until payload changes/manual action | `status='blocked'`, `blockedReason='bad_request'`. | "草稿格式异常,请继续编辑或稍后重试". |
| 401 / 403 | Yes, blocked | No blind retry | `status='blocked'`, `blockedReason='auth'`. | Login/session/permission message; preserve local draft. |
| 404 | Yes, blocked until TTL/explicit discard | No automatic retry | `status='blocked'`, `blockedReason='not_found'`. | Session not available; keep short-lived recovery copy. |
| 409 | No | No | Delete local record; do not requeue. | "此会话已在别处提交/释放,本地草稿已弃". |

409 is terminal because backend `saveDraft` rejects non-`claimed` sessions. C3 must not keep pushing a terminal session.

## 5. Backoff And Throttle

- Treat the OpenAPI 3-5 second throttling guidance as the **minimum delay** between PUT attempts for a given `(userId, sessionId)`.
- Use bounded exponential backoff: 5s, 10s, 20s, 40s, then cap at 60s.
- Store the next attempt in `retryAfterAt`; store attempt time in `lastSyncAttemptAt`.
- Collapse retries to the latest pending record. Do not enqueue multiple payload versions for the same session.
- Manual retry may bypass `retryAfterAt` for a user-initiated attempt, but still must respect the lease so two tabs do not PUT concurrently.

## 6. Multi-Tab Coordination

C3 should add a small sync coordination layer:

- BroadcastChannel name: `labelhub-offline-drafts`.
- Message types: `pending-updated`, `sync-started`, `sync-succeeded`, `sync-blocked`, `sync-failed`.
- Lease key: derived from `userId + sessionId`, separate from the draft record key.
- Lease content: `{ ownerId: string; expiresAt: number }`.
- Lease duration: 15 seconds; stale leases may be replaced.
- If BroadcastChannel is unavailable, fall back to shared IndexedDB plus visibility and capped interval polling. This is safe but less immediate and should be recorded as browser D-port in later verification.

Only the lease holder may call `PUT /draft` for a record. Other tabs may keep writing newer pending payloads and broadcasting, but they must not run the same sync concurrently.

## 7. Expected Files And Caps

### Hand-Written Cap

| Metric | Soft | Hard |
|---|---:|---:|
| Files touched | 8 | 11 |
| Net LOC | 850 | 1150 |

Full-counting rule: created files count all lines; modified files count net added lines. If the soft cap is exceeded during implementation, stop and report before continuing. The hard cap is the true ceiling.

### Generated Churn

Generated churn is expected to be 0. Any generated diff is abnormal and must be reported.

### Expected Touch List

| Path | Action | Responsibility |
|---|---|---|
| `apps/web/src/features/labeling/useOfflineDraftSync.ts` | Create | Hook that owns triggers, pending scan, sync orchestration, status, and manual retry. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftSyncPolicy.ts` | Create | Failure classification, backoff, retryAfterAt, and blocked state helpers. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftSyncLease.ts` | Create | Short lease acquisition/release logic for single-tab sync. |
| `apps/web/src/features/labeling/offlineDraft/offlineDraftSyncChannel.ts` | Create | BroadcastChannel wrapper with safe no-op fallback. |
| `apps/web/src/features/labeling/useOfflineDraftSync.test.tsx` | Create | Sync engine, failure matrix, backoff, trigger, and lease tests. |
| `apps/web/src/features/labeling/AutosaveStatusTag.tsx` | Modify | Add sync/manual-retry messaging only if the sync hook needs UI surfacing there. |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Modify | Mount sync hook and pass `userId/sessionId`; do not touch submit handler. |
| `apps/web/src/features/labeling/useOfflineDraftBuffer.ts` | Modify only if unavoidable | C3 should not change hydrate policy. If small status plumbing is necessary, document why. |

If implementation can merge policy/lease/channel cleanly without reducing clarity, it may use fewer files. Do not merge by hiding complexity in `LabelerSessionPage`.

## 8. Out Of Scope

- `handleConfirmSubmit`, `useSubmitMutation`, `SubmitValidationError`, `fieldErrorsToStableIdMap`, or submit-side cleanup.
- `useAutosave` debounce/maxWait/flush/disable timing.
- C2 hydrate decision tree or C1 store interface/record schema.
- Logout cleanup and security encryption.
- Backend error-body changes or new API fields.

## 9. Frozen Checks For Implementation

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

Expected anchors: MD5 remains `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations remain 17, humanpending remains 157, generated churn remains 0, frontend tests increase from 166.
