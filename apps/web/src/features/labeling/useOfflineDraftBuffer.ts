import { useCallback, useMemo, useState } from 'react';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import {
  createOfflineDraftRecord,
  type OfflineDraftStore,
} from './offlineDraft/offlineDraftTypes';
import {
  OfflineDraftStorageUnavailableError,
  createIndexedDbOfflineDraftStore,
} from './offlineDraft/offlineDraftStore';

export type OfflineDraftBufferStatus =
  | { kind: 'idle' }
  | { kind: 'local-restored' }
  | { kind: 'local-buffered'; savedAt: number }
  | { kind: 'blocked'; reason: 'schema-version-mismatch' }
  | { kind: 'storage-unavailable' };

export type OfflineDraftHydrateResult =
  | { source: 'server'; payload: AnswerPayload }
  | { source: 'local'; payload: AnswerPayload; restoredAt: number }
  | { source: 'blocked'; payload: AnswerPayload; reason: 'schema-version-mismatch' }
  | { source: 'storage-unavailable'; payload: AnswerPayload };

export type OfflineDraftHydrationGuard = {
  cancel: () => void;
  isCancelled: () => boolean;
};

export type HydrateOfflineDraftInput = {
  store: OfflineDraftStore;
  userId: number | null;
  sessionId: number;
  schemaVersionId: number;
  serverPayload: AnswerPayload;
};

export type BufferPendingOfflineDraftInput = {
  store: OfflineDraftStore;
  userId: number | null;
  sessionId: number;
  schemaVersionId: number;
  payload: AnswerPayload;
  now?: number;
};

export type BufferPendingOfflineDraftResult =
  | { buffered: true; savedAt: number }
  | { buffered: false; reason: 'missing-user' | 'storage-unavailable' };

export async function hydrateOfflineDraft({
  store,
  userId,
  sessionId,
  schemaVersionId,
  serverPayload,
}: HydrateOfflineDraftInput): Promise<OfflineDraftHydrateResult> {
  if (!userId) {
    return { source: 'server', payload: serverPayload };
  }

  try {
    const localDraft = await store.get(userId, sessionId);
    if (!localDraft) {
      return { source: 'server', payload: serverPayload };
    }
    if (localDraft.schemaVersionId !== schemaVersionId) {
      return { source: 'blocked', payload: serverPayload, reason: 'schema-version-mismatch' };
    }
    return { source: 'local', payload: localDraft.payload, restoredAt: localDraft.updatedAt };
  } catch (error) {
    if (error instanceof OfflineDraftStorageUnavailableError) {
      return { source: 'storage-unavailable', payload: serverPayload };
    }
    throw error;
  }
}

export async function bufferPendingOfflineDraft({
  store,
  userId,
  sessionId,
  schemaVersionId,
  payload,
  now = Date.now(),
}: BufferPendingOfflineDraftInput): Promise<BufferPendingOfflineDraftResult> {
  if (!userId) {
    return { buffered: false, reason: 'missing-user' };
  }

  try {
    await store.put(createOfflineDraftRecord({
      userId,
      sessionId,
      schemaVersionId,
      payload,
      updatedAt: now,
      status: 'pending',
    }));
    return { buffered: true, savedAt: now };
  } catch (error) {
    if (error instanceof OfflineDraftStorageUnavailableError) {
      return { buffered: false, reason: 'storage-unavailable' };
    }
    throw error;
  }
}

export function createOfflineDraftHydrationGuard(): OfflineDraftHydrationGuard {
  let cancelled = false;
  return {
    cancel() {
      cancelled = true;
    },
    isCancelled() {
      return cancelled;
    },
  };
}

export function applyOfflineDraftHydrationResult(
  result: OfflineDraftHydrateResult,
  guard: OfflineDraftHydrationGuard,
  apply: (payload: AnswerPayload) => void,
) {
  if (guard.isCancelled()) {
    return false;
  }
  apply(result.payload);
  return true;
}

export function useOfflineDraftBuffer(options: { store?: OfflineDraftStore } = {}) {
  const store = useMemo(() => options.store ?? createIndexedDbOfflineDraftStore(), [options.store]);
  const [status, setStatus] = useState<OfflineDraftBufferStatus>({ kind: 'idle' });

  const hydrate = useCallback(async (input: Omit<HydrateOfflineDraftInput, 'store'>) => {
    const result = await hydrateOfflineDraft({ ...input, store });
    if (result.source === 'local') {
      setStatus({ kind: 'local-restored' });
    } else if (result.source === 'blocked') {
      setStatus({ kind: 'blocked', reason: result.reason });
    } else if (result.source === 'storage-unavailable') {
      setStatus({ kind: 'storage-unavailable' });
    } else {
      setStatus({ kind: 'idle' });
    }
    return result;
  }, [store]);

  const bufferPending = useCallback(async (input: Omit<BufferPendingOfflineDraftInput, 'store'>) => {
    const result = await bufferPendingOfflineDraft({ ...input, store });
    if (result.buffered) {
      setStatus({ kind: 'local-buffered', savedAt: result.savedAt });
    } else if (result.reason === 'storage-unavailable') {
      setStatus({ kind: 'storage-unavailable' });
    }
    return result;
  }, [store]);

  return {
    status,
    hydrate,
    bufferPending,
  };
}
