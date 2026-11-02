# M7-P5 Offline Draft Research

## 1. Scope And Current Facts

M7-P5 is scoped to **labeler answer drafts**. Owner-side configuration drafts for schema design or AI review rules are out of scope and should be handled by a future owner-draft phase.

The target product behavior is **local loss prevention plus synchronization to the existing server draft API**. The server draft remains the canonical synchronized draft. The local draft is a pending buffer for network loss, tab refresh, tab close, browser crash, or transient save failure. P5 is expected to preserve the current OpenAPI contract unless a later gate explicitly decides otherwise.

Frozen P5 starting anchors:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 147 |
| Backend | 549 / 0 / 88, privileged D-port |
| Migrations | 17 |
| humanpending | 157 |

Verified contract facts:

- `GET /sessions/{sessionId}/draft` returns the latest `Draft` and declares 401 / 403 / 404.
- `PUT /sessions/{sessionId}/draft` returns 201 `Draft`, declares 401 / 403 / 404 / 409, and is explicitly append-only.
- `PUT /sessions/{sessionId}/draft` creates a new `revision_no` on every call, even when the payload is identical; the OpenAPI description recommends frontend throttling around 3-5 seconds.
- `POST /sessions/{sessionId}/submit` is the final persistence path and declares 409 plus 422 field validation.

Verified backend facts:

- The `drafts` table stores `session_id`, `draft_payload`, `revision_no`, and `saved_at`; `uk_drafts_session_revision(session_id, revision_no)` enforces append-only ordering.
- `SessionService.saveDraft` locks the session row, checks ownership, rejects non-`claimed` sessions, computes `MAX(revision_no) + 1`, and inserts a new draft.
- `saveDraft` does **not** run `AnswerPayloadValidator`; any JSON object accepted by `SaveDraftRequest.payload` can be saved as a draft.
- `SessionEntity.schemaVersionId` is bound when a session is claimed. A claimed session does not switch schema version while the user is answering.
- `SessionService.submit` validates the final `answerPayload` against the session-bound schema version and marks the session `submitted`.

Verified frontend facts:

- `useAutosave` already exists with 3 second debounce and 15 second max wait.
- `useAutosave` currently marks `status='error'` and stores `lastError` when `onSave` fails; it does not retry, persist locally, detect offline state, or maintain a queue.
- `useSaveDraftMutation` is a thin wrapper around `PUT /sessions/{sessionId}/draft`.
- `useLatestDraftQuery` maps draft 404 to `null`.
- `LabelerSessionPage` hydrates answer state once from `draftQuery.data?.payload ?? detail?.latestDraft?.payload ?? EMPTY_ANSWER_PAYLOAD`.
- `LabelerSessionPage` calls `await autosave.flush(); autosave.disable();` before submit, but draft flush failure is not separately classified; submit itself sends the in-memory `answerPayload`.

Important implications:

- There is no real same-session schema-version drift scenario. Local pending draft recovery only has to prove the record belongs to the same authenticated user and same `sessionId`; storing `schemaVersionId` is still useful as a defensive metadata check.
- Because server draft save does not validate field constraints, local pending synchronization should not be blocked by answer shape. Final submit remains the validation authority.
- Retry logic must collapse to the latest pending payload and respect the append-only draft table; blind high-frequency retry would create excessive revisions.
- A 409 from draft save means the session is no longer editable. Pending local drafts for that session must leave the sync queue and must not be pushed again.

## 2. Decision 1: Local Storage Medium And Scope

Options:

- `localStorage`: simple and easy to inspect, but synchronous, lower practical quota, string-only, blocks the main thread for larger answer payloads, and is clumsy for metadata and multiple pending sessions.
- `sessionStorage`: avoids long-lived residue but fails the main requirement because it does not survive browser/tab close reliably across restart.
- IndexedDB: asynchronous, suited to structured payloads plus metadata, works across refresh/restart, has better capacity, and supports per-record updates without serializing the whole queue.

**Recommendation: use IndexedDB as the primary store.**

Use one object store, for example `labelhub_offline_drafts_v1`, keyed by `(userId, sessionId)`. Store only the latest pending payload per session, not a historical queue:

```ts
type OfflineDraftRecord = {
  userId: number;
  sessionId: number;
  schemaVersionId: number;
  baseServerRevisionNo: number | null;
  payload: AnswerPayload;
  updatedAt: number;
  lastSyncAttemptAt: number | null;
  retryAfterAt: number | null;
  status: 'pending' | 'syncing' | 'blocked';
  blockedReason?: 'auth' | 'not_found' | 'terminal' | 'bad_request';
};
```

Scope the key by authenticated user and session. This prevents one user account from hydrating another user's pending draft in the same browser profile. The schema version id should be stored for defensive diagnostics and guard tests, but it should not be treated as a normal conflict dimension because session schema versions are claim-bound and stable.

IndexedDB should be wrapped behind a small adapter so unit tests can use an in-memory fake. Do not spread raw IndexedDB calls through `LabelerSessionPage`.

Fallback behavior: if IndexedDB is unavailable, keep the current server autosave behavior and show a non-blocking warning that offline draft persistence is unavailable. Do not silently fall back to `sessionStorage`, because that would give a false sense of crash safety.

## 3. Decision 2: Local Pending Versus Server Latest Draft

Options:

- Server always wins on page load. This avoids accidental overwrite from stale local data but loses unsynchronized work, which is the problem P5 is meant to solve.
- Local always wins. This is safest for loss prevention, but it can hide a newer server draft from another device.
- Timestamp or revision based choice. This is tempting but cannot fully prove semantic freshness across clocks and devices.
- User prompt on every divergence. This is safest but too much interaction for P5 v1 and hard to make reliable without a full version history UI.

**Recommendation: local pending wins hydration when it exists for the same `userId` + `sessionId`, and synchronization creates a new append-only server revision.**

Rationale:

- A local pending record exists only when prior server synchronization failed or has not completed. It represents the only copy of potentially lost work.
- The server draft API is append-only. Synchronizing local pending after a server draft exists does not mutate the older server draft; it creates a later revision.
- Session schema version is stable, so recovery into the same session is safe from schema drift.

Hydration flow:

1. Load session detail and server latest draft as today.
2. Load local pending record for `(currentUserId, sessionId)`.
3. If local pending exists and `schemaVersionId` matches the session, hydrate `answerPayload` from local pending and show an "未同步本地草稿已恢复" status.
4. If local pending exists but `schemaVersionId` does not match, do not hydrate automatically; mark it blocked and surface a recovery warning. This should be rare and indicates a wrong key or corrupted local state.
5. If no local pending exists, hydrate from server latest as today.

Synchronization triggers:

- Immediately after hydrating a pending record, with debounce/backoff.
- On `online` event.
- On page visibility returning to visible.
- On a capped interval while pending exists.
- On manual retry from the autosave status UI.
- Before submit as a best-effort preflight, with the submit behavior defined in Decision 4.

Synchronization success clears the local pending record after the 201 response. It should also invalidate `latestDraftQueryKey(sessionId)` and `['my', 'sessions']`, consistent with the existing `useSaveDraftMutation` behavior.

Multi-tab policy:

- Use `BroadcastChannel` where available to broadcast pending draft updates and sync results for the same `(userId, sessionId)`.
- Use last-writer-wins by `updatedAt` for local pending payloads. This matches current single in-memory answer state semantics and avoids attempting field-level merge.
- Use a short sync lease in local storage or IndexedDB metadata, with an expiry, so only one tab performs server synchronization at a time. Other tabs may write newer pending payloads and broadcast them, but they should not simultaneously push identical PUT requests.
- If `BroadcastChannel` is unavailable, rely on the shared IndexedDB record plus visibility/interval polling. This is acceptable for v1 and should be captured as browser-behavior D-port in verification.

## 4. Decision 3: Failure Classification Matrix

P5 must classify failures because retrying every failure is wrong. The following matrix is the recommended v1 behavior:

| Failure | Keep Pending? | Retry? | User Message / Action |
|---|---:|---:|---|
| Offline / network error / request aborted | Yes | Yes, with backoff | Show offline/pending status; manual retry available. |
| 5xx | Yes | Yes, with backoff | Show temporary server error; do not lose local draft. |
| 400 | Yes, blocked | No automatic retry until payload changes | Treat as unexpected client/payload bug; show "草稿格式异常,请继续编辑或稍后重试". |
| 401 | Yes, blocked | Pause until auth is restored | Show login/session expired message. Do not discard unsynced work. |
| 403 | Yes, blocked | Pause | Show no-permission message. Do not discard automatically because auth state may be stale. |
| 404 | Yes, blocked with TTL | No automatic retry | Session is unavailable or hidden; keep a short-lived recovery copy, show cannot sync. |
| 409 `SESSION_NOT_EDITABLE` | Remove from sync queue | No | Session is terminal/not editable. Discard pending and show explicit message that local draft cannot be applied. |

Backoff:

- Respect the contract's 3-5s throttling guidance as a lower bound for server PUT attempts.
- Collapse retries to the latest payload. Never enqueue multiple payload revisions for the same session.
- Use bounded exponential backoff, for example 5s, 10s, 20s, 40s, max 60s, with manual retry bypassing the waiting period.

The 409 case is intentionally stricter than 404/403. A 409 means the parent session is no longer editable, typically because it was submitted elsewhere or otherwise moved out of `claimed`. Pushing a stale local draft after that would violate the session lifecycle. P5 should remove that record from the sync queue and clearly tell the user that the local draft cannot be applied.

## 5. Decision 4: Submit-Time Handling Of Pending Drafts

Options:

- Require pending draft sync to succeed before submit. This makes server draft history complete but can block final submission when the draft endpoint has a transient issue, even though the submit request carries the authoritative `answerPayload`.
- Keep today's behavior and ignore pending sync. This preserves submit availability but may leave stale pending records after submit failures.
- Best-effort draft sync before submit, then submit with the current in-memory answer payload unless the draft failure proves the session is not editable or not accessible.

**Recommendation: best-effort pre-submit synchronization, but final submit does not require a successful draft PUT for network/5xx failures.**

Submit sequence:

1. Persist the current in-memory `answerPayload` to local pending storage.
2. Attempt a draft sync flush.
3. If draft sync returns 409, 403, 404, or 401, stop and surface the appropriate session/auth/access message. Do not submit because the same session state is likely invalid or the user cannot prove access.
4. If draft sync fails due network or 5xx, keep pending and allow the user to continue to submit. The final submit itself is canonical and includes the current `answerPayload`.
5. On submit success, clear local pending for that session.
6. On submit 422, keep the local pending record and show server validation errors as today.
7. On submit network/5xx failure, keep local pending and keep the user on the page.

This preserves the current architecture: draft is an intermediate recovery cache; final submission is the authoritative persisted answer and still runs backend validation.

## 6. Decision 5: Pending Draft Cleanup

Cleanup must prevent local records from accumulating forever while protecting unsynchronized work.

**Recommended cleanup rules:**

- Clear pending record after successful `PUT /draft` 201.
- Clear pending record after successful `POST /submit` 201.
- Clear pending record immediately after draft sync 409, because the session is no longer editable.
- Clear pending record when the user explicitly discards it from the recovery UI.
- Expire blocked or unsynced records after 7 days by `updatedAt`, with a boot-time cleanup pass.
- Do not silently clear pending records on 401/403/404; mark them blocked and let TTL or explicit discard remove them.
- Do not automatically clear all pending records on token expiry. Token expiry is not the same as explicit intent to discard work.

Explicit logout is a privacy tradeoff. For v1, keep pending records scoped by `userId` and session and rely on TTL cleanup. The logout flow should not hydrate records for another user. A future security-hardening pass can add a logout warning or "clear local drafts on logout" preference. Record this as an R8 watch because local IndexedDB answer data can contain sensitive labeling content.

## 7. Decision 6: Wiring Into Existing Hooks

Options:

- Modify `useAutosave` to own offline persistence. This centralizes timing but mixes a generic autosave hook with LabelHub-specific session, user, and draft API concerns.
- Build a new `useOfflineDraftBuffer` hook around the existing autosave flow. This keeps `useAutosave` as the timing primitive and moves storage/sync policy into a labeler-specific hook.
- Put local persistence in `useSaveDraftMutation.onError`. This is small, but it cannot handle hydration, online events, multi-tab coordination, submit cleanup, or manual retry cleanly.

**Recommendation: add a new labeler-specific `useOfflineDraftBuffer` layer that composes the existing hooks.**

Responsibilities:

- Read/write IndexedDB records for `(userId, sessionId)`.
- Hydrate local pending state before/alongside the existing latest draft hydration.
- Expose a save function used by `useAutosave.onSave`: attempt server save; on retryable failure persist pending and update offline status.
- Expose `syncNow`, `discardPending`, and current offline status for `AutosaveStatusTag` or a nearby status control.
- Listen for `online`, visibility changes, and BroadcastChannel updates.
- Integrate submit-time behavior by exposing a `prepareForSubmit(answerPayload)` function that follows Decision 4.

Keep `useSaveDraftMutation` as the thin server mutation and keep `useLatestDraftQuery` as the server latest query. This minimizes risk to P3a/P3b validation and submit error handling.

## 8. Cluster Split

Recommended P5 cluster sequence:

| Cluster | Scope |
|---|---|
| RESEARCH | This document: decisions, storage model, failure matrix, split. |
| C1 | Local persistence foundation: IndexedDB adapter, record schema, keying, in-memory test double, cleanup helpers, no UI wiring. |
| C2 | Labeler page hydration and autosave wiring: `useOfflineDraftBuffer`, local pending wins hydration, autosave status integration, no submit behavior changes yet except preserving pending on save failure. |
| C3 | Sync engine and failure matrix: online/visibility/manual retry, backoff, BroadcastChannel/lease, 401/403/404/409 handling, multi-tab tests. |
| C4 | Submit integration: best-effort pre-submit sync, submit-success cleanup, submit failure preservation, guard P3a 422 behavior. |
| C5 | Browser/integration tests and verification: offline/online manual pass, multi-tab D-port, verification doc, humanpending entries. |

If implementation pressure is high, C2 and C3 can be split further into UI status versus sync engine. Do not combine C1 and C2: storage correctness is foundational and should be reviewed before wiring it into live autosave.

## 9. Contract And Migration Assessment

Expected OpenAPI impact: **none**.

The existing `GET /sessions/{sessionId}/draft`, `PUT /sessions/{sessionId}/draft`, and `POST /sessions/{sessionId}/submit` endpoints are sufficient for P5 v1. The local pending draft layer can operate entirely in the browser and synchronize through the existing append-only server draft route.

Expected migration impact: **none**.

The server already stores append-only draft revisions. P5 v1 does not need server-side queue metadata, conflict bodies, device ids, or draft revision history endpoints. If a later design needs cross-device conflict UI or explicit "latest server revision" comparison beyond the current `Draft.revisionNo`, that should be a separate contract decision.

Frozen anchors during implementation gates should therefore remain:

- OpenAPI MD5 `1acd96fb6c0fd0e7b084245d8ae3fa76`
- migrations `17`

Any proposal to add draft metadata, conflict response bodies, service worker endpoints, or server-side local draft state should STOP and become a new gate decision.

## 10. Test Strategy

Unit tests:

- IndexedDB adapter with an in-memory fake: put/get/delete/listExpired by `(userId, sessionId)`.
- Keying: records for different users with same session id never hydrate across users.
- Cleanup helper: success, submit success, 409, explicit discard, TTL.
- Failure matrix classification for network, 5xx, 400, 401, 403, 404, 409.
- Hydration selector: local pending wins over server latest; no pending falls back to server latest; schemaVersionId mismatch blocks hydration.

Hook/component tests:

- `useOfflineDraftBuffer` persists pending on save failure and clears after server 201.
- `useOfflineDraftBuffer` schedules retry on online/visibility events.
- Autosave status displays pending/offline/syncing/error states without removing existing saved/error semantics.
- Labeler session initializes from local pending when present.
- Submit success clears local pending; submit 422 keeps pending and preserves server validation errors.

Integration tests:

- Existing `useSubmitMutation` and server validation 422 tests remain unchanged.
- Labeler session flow: edit answer, simulate save network failure, reload, recover local pending.
- Offline then online: pending sync creates exactly one new server draft revision for latest payload.
- 409 from draft save: pending removed and warning shown.
- Multi-tab D-port: BroadcastChannel/lease behavior should be tested where browser runtime is available; otherwise include a deterministic unit test around lease metadata.

Manual/browser checks:

- Use DevTools offline mode or request interception to block `PUT /draft`.
- Verify local pending recovers after refresh.
- Verify reconnect syncs and clears local pending.
- Verify final submit works with unsynced pending if draft save is network-failing but submit succeeds.

## 11. Cross-Phase Guards

- Do not weaken P3a backend submit validation. `AnswerPayloadValidator` remains authoritative at submit time.
- Do not change `SubmitValidationError` or `serverValidationErrors` mapping semantics.
- Do not strip hidden linkage values or alter P3b visibility validation behavior.
- Do not change server draft append-only semantics.
- Do not make final submit depend on draft validation; server draft save does not validate payload shape, while submit does.
- Do not assume task schema version can change inside an existing session; it is claim-bound.

## 12. R8 And D-Port Notes

- IndexedDB capacity and persistence behavior varies by browser, profile mode, and storage pressure. Actual capacity must be browser-verified in implementation; research-level estimates are D-port.
- Multi-tab behavior depends on `BroadcastChannel`, visibility events, and fallback polling. Browser verification is required before declaring it fully certified.
- Local IndexedDB answer payloads can contain sensitive labeling content. P5 v1 protects against cross-user hydration by keying records by user id and session id, but it does not encrypt local records. This should be recorded as a security/privacy watch unless a later cluster adds encryption or explicit logout cleanup UX.
- The provided code slice is partial; absence of unrelated files in the slice must not be treated as proof that they do not exist.
- Server-side draft history/rollback UI remains out of scope. humanpending already has older draft history/rollback watch entries; P5 should not silently satisfy or remove them.

