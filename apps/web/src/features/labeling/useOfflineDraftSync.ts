import { useCallback, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { apiClient } from '../../shared/api/client';
import { getUser } from '../../shared/api/auth-storage';
import { latestDraftQueryKey } from './useLatestDraftQuery';
import {
  type OfflineDraftRecord,
  type OfflineDraftStore,
} from './offlineDraft/offlineDraftTypes';
import {
  OfflineDraftStorageUnavailableError,
  createIndexedDbOfflineDraftStore,
} from './offlineDraft/offlineDraftStore';
import {
  acquireOfflineDraftSyncLease,
  releaseOfflineDraftSyncLease,
  type LeaseStorage,
} from './offlineDraft/offlineDraftSyncLease';
import {
  type OfflineDraftSyncChannel,
  createOfflineDraftSyncChannel,
} from './offlineDraft/offlineDraftSyncChannel';
import {
  OfflineDraftSyncHttpError,
  classifyOfflineDraftSyncFailure,
  computeNextRetryAfter,
} from './offlineDraft/offlineDraftSyncPolicy';

export type OfflineDraftSyncStatus =
  | { kind: 'idle' }
  | { kind: 'syncing'; sessionId: number }
  | { kind: 'retry-scheduled'; sessionId: number; retryAfterAt: number }
  | { kind: 'blocked'; sessionId: number; reason: OfflineDraftRecord['blockedReason'] }
  | { kind: 'terminal-cleared'; sessionId: number };

export type OfflineDraftSyncResult =
  | { kind: 'synced'; sessionId: number }
  | { kind: 'retry-scheduled'; sessionId: number; retryAfterAt: number }
  | { kind: 'blocked'; sessionId: number; reason: NonNullable<OfflineDraftRecord['blockedReason']> }
  | { kind: 'terminal-cleared'; sessionId: number }
  | { kind: 'skipped-retry-after'; sessionId: number; retryAfterAt: number }
  | { kind: 'skipped-lease'; sessionId: number };

export type OfflineDraftSubmitPreSyncResult =
  | { kind: 'synced' }
  | { kind: 'no-pending' }
  | { kind: 'storage-unavailable' }
  | { kind: 'missing-user' }
  | { kind: 'continue-with-pending'; reason: 'network' | 'server' | 'bad_request' }
  | { kind: 'block-submit'; reason: 'auth' | 'not_found' | 'terminal'; message: string };

export type SaveOfflineDraft = (sessionId: number, payload: AnswerPayload) => Promise<unknown>;

export async function saveOfflineDraftViaApiClient(sessionId: number, payload: AnswerPayload) {
  const { data, error, response } = await apiClient.PUT('/sessions/{sessionId}/draft', {
    params: { path: { sessionId } },
    body: { payload },
  });

  if (error || !data) {
    throw new OfflineDraftSyncHttpError(response.status, error?.message ?? '草稿同步失败');
  }

  return data;
}

export async function syncOfflineDraftRecord({
  store,
  record,
  saveDraft,
  invalidate,
  acquireLease,
  releaseLease,
  now,
  manual = false,
  channel,
}: {
  store: OfflineDraftStore;
  record: OfflineDraftRecord;
  saveDraft: SaveOfflineDraft;
  invalidate: (sessionId: number) => Promise<void> | void;
  acquireLease: (record: OfflineDraftRecord, now: number) => boolean;
  releaseLease: (record: OfflineDraftRecord) => void;
  now: () => number;
  manual?: boolean;
  channel?: OfflineDraftSyncChannel;
}): Promise<OfflineDraftSyncResult> {
  const attemptAt = now();
  if (!manual && record.retryAfterAt && record.retryAfterAt > attemptAt) {
    return { kind: 'skipped-retry-after', sessionId: record.sessionId, retryAfterAt: record.retryAfterAt };
  }

  if (!acquireLease(record, attemptAt)) {
    return { kind: 'skipped-lease', sessionId: record.sessionId };
  }

  try {
    await store.put({
      ...record,
      status: 'syncing',
      lastSyncAttemptAt: attemptAt,
      retryAfterAt: null,
      blockedReason: undefined,
    });
    channel?.post({ type: 'sync-started', userId: record.userId, sessionId: record.sessionId });

    await saveDraft(record.sessionId, record.payload);
    await store.deleteBySession(record.userId, record.sessionId);
    await invalidate(record.sessionId);
    channel?.post({ type: 'sync-succeeded', userId: record.userId, sessionId: record.sessionId });
    return { kind: 'synced', sessionId: record.sessionId };
  } catch (error) {
    const plan = classifyOfflineDraftSyncFailure(error);
    if (plan.action === 'delete') {
      await store.deleteBySession(record.userId, record.sessionId);
      return { kind: 'terminal-cleared', sessionId: record.sessionId };
    }
    if (plan.action === 'block') {
      await store.put({
        ...record,
        status: 'blocked',
        blockedReason: plan.blockedReason,
        lastSyncAttemptAt: attemptAt,
        retryAfterAt: null,
      });
      channel?.post({
        type: 'sync-blocked',
        userId: record.userId,
        sessionId: record.sessionId,
        reason: plan.blockedReason,
      });
      return { kind: 'blocked', sessionId: record.sessionId, reason: plan.blockedReason };
    }

    const retryAfterAt = computeNextRetryAfter({
      now: attemptAt,
      lastSyncAttemptAt: record.lastSyncAttemptAt,
      retryAfterAt: record.retryAfterAt,
    });
    await store.put({
      ...record,
      status: 'pending',
      blockedReason: undefined,
      lastSyncAttemptAt: attemptAt,
      retryAfterAt,
    });
    channel?.post({ type: 'sync-failed', userId: record.userId, sessionId: record.sessionId, retryAfterAt });
    return { kind: 'retry-scheduled', sessionId: record.sessionId, retryAfterAt };
  } finally {
    releaseLease(record);
  }
}

export function toOfflineDraftSubmitPreSyncResult(
  result: OfflineDraftSyncResult,
): OfflineDraftSubmitPreSyncResult {
  if (result.kind === 'synced') return { kind: 'synced' };
  if (result.kind === 'terminal-cleared') {
    return {
      kind: 'block-submit',
      reason: 'terminal',
      message: '此会话已在别处提交/释放,本地草稿已弃',
    };
  }
  if (result.kind === 'blocked') {
    if (result.reason === 'auth') {
      return {
        kind: 'block-submit',
        reason: 'auth',
        message: '登录状态或权限已失效,请重新登录后再提交',
      };
    }
    if (result.reason === 'not_found') {
      return {
        kind: 'block-submit',
        reason: 'not_found',
        message: '会话不可用,请刷新后重试',
      };
    }
    return { kind: 'continue-with-pending', reason: 'bad_request' };
  }
  return { kind: 'continue-with-pending', reason: 'network' };
}

export function useOfflineDraftSync(options: {
  store?: OfflineDraftStore;
  saveDraft?: SaveOfflineDraft;
  now?: () => number;
  ownerId?: string;
  leaseStorage?: LeaseStorage | null;
  channel?: OfflineDraftSyncChannel;
  intervalMs?: number;
  enabled?: boolean;
  sessionId?: number | null;
} = {}) {
  const queryClient = useQueryClient();
  const store = useMemo(() => options.store ?? createIndexedDbOfflineDraftStore(), [options.store]);
  const saveDraft = options.saveDraft ?? saveOfflineDraftViaApiClient;
  const now = options.now ?? Date.now;
  const ownerId = useMemo(
    () => options.ownerId ?? `tab-${Math.random().toString(36).slice(2)}`,
    [options.ownerId],
  );
  const channel = useMemo(() => options.channel ?? createOfflineDraftSyncChannel(), [options.channel]);
  const intervalMs = options.intervalMs ?? 30_000;
  const enabled = options.enabled ?? true;
  const [status, setStatus] = useState<OfflineDraftSyncStatus>({ kind: 'idle' });

  const invalidate = useCallback(async (sessionId: number) => {
    await queryClient.invalidateQueries({ queryKey: latestDraftQueryKey(sessionId) });
    await queryClient.invalidateQueries({ queryKey: ['my', 'sessions'] });
  }, [queryClient]);

  const syncNow = useCallback(async (targetSessionId?: number, manual = false) => {
    const userId = getUser()?.id ?? null;
    if (!enabled || !userId) return;

    let records: OfflineDraftRecord[];
    try {
      records = await store.listByUser(userId);
    } catch (error) {
      if (error instanceof OfflineDraftStorageUnavailableError) return;
      throw error;
    }

    const candidates = records.filter((record) => {
      if (targetSessionId && record.sessionId !== targetSessionId) return false;
      return record.status === 'pending' || record.status === 'syncing' || (manual && record.status === 'blocked');
    });

    for (const record of candidates) {
      setStatus({ kind: 'syncing', sessionId: record.sessionId });
      const result = await syncOfflineDraftRecord({
        store,
        record,
        saveDraft,
        invalidate,
        now,
        manual,
        channel,
        acquireLease: (draft, at) => acquireOfflineDraftSyncLease({
          storage: options.leaseStorage,
          userId: draft.userId,
          sessionId: draft.sessionId,
          ownerId,
          now: at,
        }),
        releaseLease: (draft) => releaseOfflineDraftSyncLease({
          storage: options.leaseStorage,
          userId: draft.userId,
          sessionId: draft.sessionId,
          ownerId,
        }),
      });

      if (result.kind === 'retry-scheduled') {
        setStatus({ kind: 'retry-scheduled', sessionId: result.sessionId, retryAfterAt: result.retryAfterAt });
      } else if (result.kind === 'blocked') {
        setStatus({ kind: 'blocked', sessionId: result.sessionId, reason: result.reason });
      } else if (result.kind === 'terminal-cleared') {
        setStatus({ kind: 'terminal-cleared', sessionId: result.sessionId });
      } else if (result.kind === 'synced') {
        setStatus({ kind: 'idle' });
      }
    }
  }, [channel, enabled, invalidate, now, options.leaseStorage, ownerId, saveDraft, store]);

  const retryPending = useCallback(async (sessionId: number) => {
    await syncNow(sessionId, true);
  }, [syncNow]);

  const syncPendingForSubmit = useCallback(async (targetSessionId: number): Promise<OfflineDraftSubmitPreSyncResult> => {
    const userId = getUser()?.id ?? null;
    if (!enabled || !userId) return { kind: 'missing-user' };

    let records: OfflineDraftRecord[];
    try {
      records = await store.listByUser(userId);
    } catch (error) {
      if (error instanceof OfflineDraftStorageUnavailableError) return { kind: 'storage-unavailable' };
      throw error;
    }

    const record = records.find((draft) => draft.sessionId === targetSessionId);
    if (!record) return { kind: 'no-pending' };

    const result = await syncOfflineDraftRecord({
      store,
      record,
      saveDraft,
      invalidate,
      now,
      manual: true,
      channel,
      acquireLease: (draft, at) => acquireOfflineDraftSyncLease({
        storage: options.leaseStorage,
        userId: draft.userId,
        sessionId: draft.sessionId,
        ownerId,
        now: at,
      }),
      releaseLease: (draft) => releaseOfflineDraftSyncLease({
        storage: options.leaseStorage,
        userId: draft.userId,
        sessionId: draft.sessionId,
        ownerId,
      }),
    });

    return toOfflineDraftSubmitPreSyncResult(result);
  }, [channel, enabled, invalidate, now, options.leaseStorage, ownerId, saveDraft, store]);

  const discardPending = useCallback(async (sessionId: number) => {
    const userId = getUser()?.id ?? null;
    if (!enabled || !userId) return;
    try {
      await store.deleteBySession(userId, sessionId);
    } catch (error) {
      if (error instanceof OfflineDraftStorageUnavailableError) return;
      throw error;
    }
  }, [enabled, store]);

  useEffect(() => {
    if (!enabled) return undefined;

    const run = () => {
      void syncNow(options.sessionId ?? undefined);
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') run();
    };
    const unsubscribe = channel.subscribe((message) => {
      if (message.type === 'pending-updated' || message.type === 'sync-failed') {
        run();
      }
    });

    window.addEventListener('online', run);
    document.addEventListener('visibilitychange', onVisibilityChange);
    const interval = window.setInterval(run, intervalMs);

    return () => {
      window.removeEventListener('online', run);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.clearInterval(interval);
      unsubscribe();
    };
  }, [channel, enabled, intervalMs, options.sessionId, syncNow]);

  return {
    status,
    syncNow,
    retryPending,
    syncPendingForSubmit,
    discardPending,
  };
}
