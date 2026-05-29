# M7-P5 C2 Pre-Estimate

## 1. Cluster Summary

C2 connects the C1 offline draft storage foundation to the labeler session page. It adds local-pending hydration before the existing server draft fallback, writes a pending local draft when server autosave fails, and surfaces a local-buffered status. The cluster is additive: it does not change submit, validation, server draft APIs, sync/retry, backend, OpenAPI, migrations, or generated types.

Frozen anchors:

- OpenAPI MD5 `1acd96fb6c0fd0e7b084245d8ae3fa76`
- Frontend Vitest 157, expected to rise after C2 tests
- Migrations 17
- humanpending 157
- generated churn 0

## 2. File Plan

| File | Action | Estimate | Responsibility |
|---|---|---:|---|
| `apps/web/src/features/labeling/useOfflineDraftBuffer.ts` | create | 145 | Composition hook around C1 store: local hydrate decision, failure buffering, storage-unavailable handling, and local buffer status. |
| `apps/web/src/features/labeling/useOfflineDraftBuffer.test.ts` | create | 185 | Unit tests for hydration branches, user isolation, schema mismatch blocking, save-failure buffering, and storage-unavailable fallback behavior. |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | modify | +70 | Insert local-pending hydration before current server fallback; wrap autosave `onSave` failure path to buffer pending drafts; keep submit path unchanged. |
| `apps/web/src/features/labeling/AutosaveStatusTag.tsx` | modify | +35 | Add optional offline-buffer display state such as `本地已暂存,未同步` without changing existing idle/saving/saved/error behavior. |
| `apps/web/src/features/labeling/AutosaveStatusTag.test.tsx` | create if needed | 45 | Server-render or component smoke for local-buffered status precedence and existing state fallback. |

Estimated hand-authored total: 435 LOC if the status tag test is separate. This stays within the soft LOC cap. If implementation can cover the tag state through `useOfflineDraftBuffer.test.ts`, the total should be closer to 390 LOC.

## 3. Cap

Generated churn is expected to be 0 and is tracked separately from hand-authored code.

| Metric | Soft | Hard |
|---|---:|---:|
| Hand-authored files | 6 | 8 |
| Hand-authored net LOC | 500 | 700 |
| Generated files | 0 | 0 |

The cap matches the scope-budget. If implementation naturally exceeds a soft cap, report before continuing. Do not move C3 sync or C4 submit behavior into C2 to justify more files.

## 4. `useOfflineDraftBuffer` Interface

Proposed hook shape:

```ts
type HydrateInput = {
  userId: number | null;
  sessionId: number;
  schemaVersionId: number;
  serverPayload: AnswerPayload;
};

type HydrateResult =
  | { source: 'local'; payload: AnswerPayload; restoredAt: number }
  | { source: 'server'; payload: AnswerPayload }
  | { source: 'blocked'; payload: AnswerPayload; reason: 'schema-version-mismatch' }
  | { source: 'storage-unavailable'; payload: AnswerPayload };

type OfflineDraftBufferStatus =
  | { kind: 'idle' }
  | { kind: 'local-restored' }
  | { kind: 'local-buffered'; savedAt: number }
  | { kind: 'blocked'; reason: 'schema-version-mismatch' }
  | { kind: 'storage-unavailable' };
```

The hook should expose:

- `hydrate(input): Promise<HydrateResult>` for initial page hydration.
- `bufferPending(input): Promise<void>` for autosave failure fallback.
- `status` for `AutosaveStatusTag`.

The hook may accept an injected `OfflineDraftStore` for tests and default to `createIndexedDbOfflineDraftStore()` in production. It should not modify the C1 store implementation.

## 5. Hydration Decision Tree

`LabelerSessionPage` currently hydrates once:

```ts
coerceAnswerPayload(draftQuery.data?.payload ?? detail?.latestDraft?.payload ?? EMPTY_ANSWER_PAYLOAD)
```

C2 must preserve this as the server fallback. The page should first compute:

```ts
const serverPayload = coerceAnswerPayload(
  draftQuery.data?.payload ?? detail?.latestDraft?.payload ?? EMPTY_ANSWER_PAYLOAD,
);
```

Then call the offline buffer:

1. `userId` missing: return `{ source: 'server', payload: serverPayload }`; do not read local storage.
2. storage unavailable: return `{ source: 'storage-unavailable', payload: serverPayload }`; do not crash.
3. local pending missing: return `{ source: 'server', payload: serverPayload }`.
4. local pending has matching `schemaVersionId`: return `{ source: 'local', payload: local.payload }`.
5. local pending has mismatched `schemaVersionId`: return `{ source: 'blocked', payload: serverPayload, reason: 'schema-version-mismatch' }`; do not hydrate the local payload.

Use cancellation inside the page effect so an older async hydrate result cannot overwrite a newer session load.

## 6. Autosave Failure Buffering

Preferred C2 design keeps `useAutosave` internals unchanged:

```ts
const autosave = useAutosave({
  value: answerPayload ?? EMPTY_ANSWER_PAYLOAD,
  enabled: hasInitialized && isClaimed,
  onSave: async (payload) => {
    if (!sessionId) return;
    try {
      await saveDraftMutation.mutateAsync({ sessionId, payload });
    } catch (error) {
      await offlineDraftBuffer.bufferPending({ userId, sessionId, schemaVersionId, payload, error });
      throw error;
    }
  },
});
```

`useAutosave` still catches the rethrown error and sets its existing `status='error'`. `AutosaveStatusTag` uses the offline buffer status to display local-buffered messaging when appropriate. This preserves debounce 3s, maxWait 15s, flush, and disable behavior.

If implementation cannot show local-buffered status without changing `useAutosave`, STOP and report. Do not silently alter autosave timing.

## 7. Status Display

Extend `AutosaveStatusTag` with an optional prop, for example:

```ts
export function AutosaveStatusTag({
  autosave,
  offlineDraft,
}: {
  autosave: UseAutosaveResult;
  offlineDraft?: OfflineDraftBufferStatus;
})
```

Display precedence:

1. `offlineDraft.kind === 'local-buffered'`: show warning/yellow tag `本地已暂存` with tooltip `服务器草稿保存失败,当前答案已暂存在本机,恢复网络后会同步。`
2. `offlineDraft.kind === 'local-restored'`: show warning/yellow tag `已恢复本地草稿` with tooltip explaining it is not yet synced.
3. `offlineDraft.kind === 'blocked'`: show red/orange tag `本地草稿未恢复` with schema mismatch tooltip.
4. Otherwise retain existing autosave states exactly: saving, error, saved, idle.

Do not describe local-buffered state as server-saved. Do not imply submit is blocked in C2.

## 8. User Id Handling

Use `getUser()?.id` from `apps/web/src/shared/api/auth-storage.ts`.

Rules:

- Missing user id means no local read/write.
- Hydration uses `(userId, sessionId)`.
- Failure buffering uses the same `(userId, sessionId)`.
- Tests must prove missing user id falls back to server payload and does not call the store.

## 9. Storage Unavailable Handling

If `OfflineDraftStorageUnavailableError` occurs:

- Hydration returns the server fallback payload.
- Failure buffering should not crash the page. It may keep the autosave error state and optionally expose storage-unavailable status.
- No `sessionStorage` or `localStorage` fallback is added.
- No UI should block labeling.

This keeps C2 behavior safe in private browsing or locked-down browser storage.

## 10. Submit Path

C2 does not modify:

- `handleConfirmSubmit`
- `autosave.flush()`
- `autosave.disable()`
- `useSubmitMutation`
- `SubmitValidationError`
- `fieldErrorsToStableIdMap`
- `serverValidationErrors`

Submit continues to send the current in-memory `answerPayload`. C4 decides whether pending local drafts must be force-synced before submit.

## 11. P3a/P3b Guardrails

No local pending:

- Hydration path is the same server draft/latestDraft/empty fallback.
- `validatePayload`, `errorsByStableId`, `selectVisibleFieldErrors`, and `createVisibleSchemaFieldsSelector` stay untouched.
- Runtime linkage visibility and server 422 mapping stay untouched.

Local pending:

- Payload is still passed through the existing `coerceAnswerPayload` / renderer / validation path after hydration.
- C2 does not bypass submit validation.

## 12. Tests

Required tests:

- Matching local pending hydrates before server draft.
- No local pending falls back to the current server draft chain.
- `schemaVersionId` mismatch does not hydrate local pending and exposes blocked status.
- Missing `userId` does not read or write local store.
- `OfflineDraftStorageUnavailableError` during hydration falls back to server payload without throwing.
- Server autosave failure writes pending local draft with `userId`, `sessionId`, `schemaVersionId`, payload, `status='pending'`, and fresh `updatedAt`.
- Server autosave failure rethrows so existing autosave error handling remains active.
- `AutosaveStatusTag` shows local-buffered/restored states and preserves existing idle/saving/saved/error states.
- Submit path remains untouched: no tests should import or mock `useSubmitMutation` for C2 changes unless asserting it is not called by offline buffer code.

Use the C1 memory store for hook tests. Do not add new IndexedDB dependencies in C2.

## 13. Risks And STOP Conditions

STOP and report if:

- Hydration cannot preserve the no-pending server fallback behavior.
- Implementation requires changing `handleConfirmSubmit`, `useSubmitMutation`, or submit validation.
- Implementation requires backend, OpenAPI, generated type, or migration changes.
- Local pending recovery requires list/sync/retry/multi-tab behavior. That is C3 scope.
- The only viable path requires changing `useAutosave` timing semantics.

## 14. Frozen Checks For Implementation Report

Implementation should report:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml      # remains 1acd96fb6c0fd0e7b084245d8ae3fa76
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l   # remains 17
grep -cE "^- \\[" humanpending.md   # remains 157
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
git diff --stat
git diff --check
```

Implementation should also state that generated churn is 0 and list all modified production files touching labeler/autosave.
