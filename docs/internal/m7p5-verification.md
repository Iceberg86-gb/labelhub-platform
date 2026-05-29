# M7-P5 Verification

## Closure Summary

M7-P5 delivers labeler answer draft loss prevention and closes two manual-probe gaps in the labeler scoring page: labelers now see the dataset item being judged, and submit-time validation now surfaces the concrete field error instead of only a generic toast. The server-side append-only draft API remains canonical; the frontend adds local IndexedDB buffering, local-first recovery, retrying sync, multi-tab coordination guards, submit-path cleanup/retention rules, a read-only dataset item context card sourced from the frozen claim snapshot, and submit validation feedback driven by Formily's native validators.

## Final Anchors

| Anchor | P5 Start | P5 Final Close |
|---|---:|---:|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 147 | 203 |
| Migrations | 17 | 17 |
| humanpending | 157 | 163 |

Backend runtime behavior was not changed in P5. The P4b2 escalated backend anchor remains 549 tests / 0 errors / 88 skipped.

## Cluster Map

| Cluster | Scope |
|---|---|
| RESEARCH | Chose labeler answer drafts, local loss-prevention plus existing server draft sync, IndexedDB, local-pending-wins hydrate, failure matrix, best-effort submit pre-sync, and a C1-C5 split. |
| C1 | Added local storage foundation: IndexedDB adapter, memory store, user+session keying, record schema with `storageVersion` and `encoding`, TTL/session/discard helpers, and tests. |
| C2 | Wired local pending into the labeler page: local pending hydrate wins when schema version matches, schema mismatch blocks local hydrate, autosave failure buffers local pending, status tag surfaces local states, and no-pending behavior remains the existing server draft chain. |
| C3 | Added sync engine: online/visibility/manual/interval triggers, BroadcastChannel wrapper, single-tab lease, retry backoff no faster than five seconds, failure matrix, and status UI. |
| C4 | Integrated submit path: pre-buffer current in-memory payload, best-effort pre-sync, block only 401/403/404/409, allow network/5xx/400, clear pending only after submit success, preserve P3a 422 mapping, and keep submit body sourced from in-memory `answerPayload`. |
| C4.5 | Inserted the Labeler Dataset Item Context Card after a manual probe found labelers could not see the item being scored. The card renders frozen `claimSnapshot.datasetItemPayload` first, falls back to live `datasetItem.itemPayload` only for legacy/exception cases, uses defensive free-form rendering, keeps `content_markdown` plain text, and never writes `answerPayload`. |
| C4.6 | Added submit validation feedback after a manual probe found submit could be blocked by client validation while only showing a generic toast. The fix exposes the existing Formily form through `onFormReady`, calls `form.validate()` on the client-validation intercept path, shows the first concrete field label and real reason, and scrolls/focuses the first invalid field without changing the true submit path. |
| Final C5 | Reissues closure after C4.6, preserving old humanpending entries as audit history while rewriting this verification document as the current authoritative P5 closure. |

## Invariants

- OpenAPI and backend draft semantics remain unchanged. `PUT /sessions/{sessionId}/draft` is still append-only and creates a new revision on each accepted call.
- Local pending records are scoped by `userId + sessionId`; cross-user hydrate is rejected.
- Local storage is plaintext JSON in P5 v1. Records include `storageVersion = 1` and `encoding = 'plain-json-v1'` so a later encrypted format can migrate deliberately.
- `schemaVersionId` is stored as a tripwire. A mismatch does not hydrate local pending.
- No-pending hydrate remains the original server latest draft chain.
- `409` during draft sync is terminal: pending is deleted and not retried.
- `401/403` keep pending but stop blind retry until re-authentication.
- Network/5xx retry uses bounded backoff and does not run faster than the server draft throttle floor.
- A single `(userId, sessionId)` sync lease prevents concurrent tabs from pushing the same pending draft at the same time.
- Submit sends the current in-memory answer payload, not a local pending record or server draft payload.
- Submit 201 clears pending. Submit 422/network/5xx preserves pending.
- P3a submit validation remains intact: `SubmitValidationError -> fieldErrorsToStableIdMap -> setServerErrors -> serverValidationErrors` was not changed.
- `AnswerPayloadValidator`, linkage visibility filtering, and submit 422 behavior were not weakened.
- The dataset item card uses frozen `claimSnapshot.datasetItemPayload` as the primary judging source, not the live dataset item payload.
- The dataset item card defensively handles unknown, missing, scalar, array, object, and malformed payload shapes without blanking the labeler page.
- `content_markdown` is rendered as plain/preformatted text and does not use unsafe HTML.
- The dataset item card is read-only and does not write to `answerPayload` or derive default answers from the dataset item payload.
- Submit-click client validation feedback uses `form.validate()` to activate Formily's native field validation display; it does not inject client errors through external `setFieldState` / `selfErrors`.
- The client-validation intercept still blocks submit when `validatePayload` reports errors; C4.6 only adds field display, concrete toast copy, and scroll/focus.
- Submit feedback toast text is derived from the real first `PayloadValidationError` field label and reason, such as `详细评审意见: 最少 5 字`.
- The scroll target uses stable `data-labeling-field-id` anchors rather than label text.
- `handleConfirmSubmit`, `runLabelerSubmitWithOfflineDraft`, P5 offline sync, server 422 mapping, and submit payload sourcing were not changed by C4.6.

## Verification Evidence

- C4.6 targeted tests: `pnpm --filter @labelhub/web test -- src/features/labeling/formily/__tests__/renderer.test.tsx src/features/labeling/submitValidationFeedback.test.ts` passed with 2 files / 8 tests.
- Full frontend test after C4.6 and Formily SSR test cleanup: `pnpm --filter @labelhub/web test` passed with 31 files / 203 tests and no Formily SSR `useLayoutEffect` warnings.
- Frontend typecheck: `pnpm --filter @labelhub/web typecheck` passed.
- OpenAPI MD5 remained `1acd96fb6c0fd0e7b084245d8ae3fa76`.
- Migration count remained 17.
- `git diff --check` was clean.

## Browser And Multi-tab R8

The Browser runtime was not available in this session for final visual verification. Earlier attempts returned `Browser is not available: iab`, and no screenshots or live offline-devtools/multi-tab proof were fabricated.

Vitest coverage guards memory-store end-to-end behavior, sync failure classification, retry backoff, submit pre-sync, P3a submit validation preservation, dataset item context rendering, and C4.6 submit feedback mechanics. True browser validation for offline devtools mode, real IndexedDB persistence, BroadcastChannel delivery, lease timing across tabs, 1440/1280/1024 visual status-tag layout, 1440/1280/1024 dataset item card layout, and C4.6 scroll/focus behavior remains D-port and should be rerun in a Browser-capable session.

## Security Watch

P5 v1 intentionally stores pending answer drafts as plaintext JSON in IndexedDB. Risk is reduced by user+session scoping, read-time user checks, TTL cleanup helpers, and explicit delete helpers. Browser-side encryption and logout cleanup UX are deferred to a dedicated security-hardening cluster. The record schema already includes `storageVersion` and `encoding` for a future encrypted migration.
