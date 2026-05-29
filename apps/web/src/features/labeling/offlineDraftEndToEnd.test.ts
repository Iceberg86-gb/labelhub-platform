import { describe, expect, it, vi } from 'vitest';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { createMemoryOfflineDraftStore } from './offlineDraft/offlineDraftMemoryStore';
import { bufferPendingOfflineDraft, hydrateOfflineDraft } from './useOfflineDraftBuffer';
import {
  syncOfflineDraftRecord,
  type OfflineDraftSubmitPreSyncResult,
} from './useOfflineDraftSync';
import { runLabelerSubmitWithOfflineDraft } from '../../pages/labeler/labelerSubmitOfflineDraftFlow';

const userId = 10;
const sessionId = 20;
const schemaVersionId = 30;
const offlinePayload = { field_0: 'offline answer' } satisfies AnswerPayload;
const serverPayload = { field_0: 'server answer' } satisfies AnswerPayload;
const submitPayload = { field_0: 'final in-memory answer' } satisfies AnswerPayload;

describe('offline draft end-to-end guard', () => {
  it('buffers failed autosave, restores locally, syncs to server draft, and clears after submit success', async () => {
    const store = createMemoryOfflineDraftStore();

    await bufferPendingOfflineDraft({
      store,
      userId,
      sessionId,
      schemaVersionId,
      payload: offlinePayload,
      now: 1_000,
    });

    await expect(hydrateOfflineDraft({
      store,
      userId,
      sessionId,
      schemaVersionId,
      serverPayload,
    })).resolves.toEqual({
      source: 'local',
      payload: offlinePayload,
      restoredAt: 1_000,
    });

    const record = await store.get(userId, sessionId);
    expect(record).not.toBeNull();
    const saveDraft = vi.fn().mockResolvedValue({ id: 1, revisionNo: 2 });
    const invalidate = vi.fn();

    await expect(syncOfflineDraftRecord({
      store,
      record: record!,
      saveDraft,
      invalidate,
      acquireLease: () => true,
      releaseLease: vi.fn(),
      now: () => 2_000,
    })).resolves.toEqual({ kind: 'synced', sessionId });

    expect(saveDraft).toHaveBeenCalledWith(sessionId, offlinePayload);
    expect(invalidate).toHaveBeenCalledWith(sessionId);
    await expect(store.get(userId, sessionId)).resolves.toBeNull();

    const submit = vi.fn().mockResolvedValue({ id: 77 });
    await runLabelerSubmitWithOfflineDraft({
      sessionId,
      userId,
      schemaVersionId,
      finalPayload: submitPayload,
      flush: vi.fn().mockResolvedValue(undefined),
      disable: vi.fn(),
      bufferPending: (input) => bufferPendingOfflineDraft({ ...input, store, now: 3_000 }),
      preSync: vi.fn().mockResolvedValue({
        kind: 'continue-with-pending',
        reason: 'network',
      } satisfies OfflineDraftSubmitPreSyncResult),
      submit,
      clearPending: (id) => store.deleteBySession(userId, id),
      onSuccess: vi.fn(),
      onBlocked: vi.fn(),
      onValidationError: vi.fn(),
      onGenericError: vi.fn(),
    });

    expect(submit).toHaveBeenCalledWith(submitPayload);
    await expect(store.get(userId, sessionId)).resolves.toBeNull();
  });
});
