import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { createMemoryOfflineDraftStore } from './offlineDraft/offlineDraftMemoryStore';
import { createOfflineDraftRecord } from './offlineDraft/offlineDraftTypes';
import {
  classifyOfflineDraftSyncFailure,
  computeNextRetryAfter,
  OfflineDraftSyncHttpError,
} from './offlineDraft/offlineDraftSyncPolicy';
import {
  acquireOfflineDraftSyncLease,
  offlineDraftSyncLeaseKey,
  releaseOfflineDraftSyncLease,
  type LeaseStorage,
} from './offlineDraft/offlineDraftSyncLease';
import { createOfflineDraftSyncChannel } from './offlineDraft/offlineDraftSyncChannel';
import {
  syncOfflineDraftRecord,
  toOfflineDraftSubmitPreSyncResult,
  type OfflineDraftSyncStatus,
} from './useOfflineDraftSync';
import { AutosaveStatusTag } from './AutosaveStatusTag';
import type { UseAutosaveResult } from './useAutosave';

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, onClick }: { children?: ReactNode; onClick?: () => void }) => (
    <button type="button" onClick={onClick}>{children}</button>
  ),
  Space: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Tooltip: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
}));

const payload = { field_0: 'local' } satisfies AnswerPayload;

describe('offline draft sync policy', () => {
  it('maps terminal 409 to a delete outcome with no retry', () => {
    expect(classifyOfflineDraftSyncFailure(new OfflineDraftSyncHttpError(409, 'conflict'))).toEqual({
      action: 'delete',
      message: '此会话已在别处提交/释放,本地草稿已弃',
    });
  });

  it('blocks auth, not_found, and bad_request failures without blind retry', () => {
    expect(classifyOfflineDraftSyncFailure(new OfflineDraftSyncHttpError(401, 'unauthorized'))).toMatchObject({
      action: 'block',
      blockedReason: 'auth',
    });
    expect(classifyOfflineDraftSyncFailure(new OfflineDraftSyncHttpError(403, 'forbidden'))).toMatchObject({
      action: 'block',
      blockedReason: 'auth',
    });
    expect(classifyOfflineDraftSyncFailure(new OfflineDraftSyncHttpError(404, 'missing'))).toMatchObject({
      action: 'block',
      blockedReason: 'not_found',
    });
    expect(classifyOfflineDraftSyncFailure(new OfflineDraftSyncHttpError(400, 'bad'))).toMatchObject({
      action: 'block',
      blockedReason: 'bad_request',
    });
  });

  it('retries network and 5xx failures no faster than five seconds', () => {
    expect(classifyOfflineDraftSyncFailure(new Error('offline'))).toMatchObject({ action: 'retry' });
    expect(classifyOfflineDraftSyncFailure(new OfflineDraftSyncHttpError(503, 'down'))).toMatchObject({ action: 'retry' });
    expect(computeNextRetryAfter({ now: 1_000, lastSyncAttemptAt: null, retryAfterAt: null })).toBe(6_000);
    expect(computeNextRetryAfter({ now: 20_000, lastSyncAttemptAt: 1_000, retryAfterAt: 6_000 })).toBe(30_000);
  });
});

describe('offline draft sync lease', () => {
  it('allows one owner per user/session and replaces stale leases', () => {
    const storage = createMemoryLeaseStorage();

    expect(acquireOfflineDraftSyncLease({ storage, userId: 10, sessionId: 20, ownerId: 'tab-a', now: 1_000 })).toBe(true);
    expect(acquireOfflineDraftSyncLease({ storage, userId: 10, sessionId: 20, ownerId: 'tab-b', now: 2_000 })).toBe(false);
    expect(acquireOfflineDraftSyncLease({ storage, userId: 10, sessionId: 20, ownerId: 'tab-b', now: 20_000 })).toBe(true);

    releaseOfflineDraftSyncLease({ storage, userId: 10, sessionId: 20, ownerId: 'tab-a' });
    expect(storage.getItem(offlineDraftSyncLeaseKey(10, 20))).toContain('tab-b');
    releaseOfflineDraftSyncLease({ storage, userId: 10, sessionId: 20, ownerId: 'tab-b' });
    expect(storage.getItem(offlineDraftSyncLeaseKey(10, 20))).toBeNull();
  });
});

describe('syncOfflineDraftRecord', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('clears local pending and invalidates queries after successful sync', async () => {
    const store = createMemoryOfflineDraftStore();
    const record = createRecord();
    await store.put(record);
    const invalidate = vi.fn();

    await expect(syncOfflineDraftRecord({
      store,
      record,
      saveDraft: vi.fn().mockResolvedValue({ id: 1 }),
      invalidate,
      acquireLease: () => true,
      releaseLease: vi.fn(),
      now: () => 1_000,
    })).resolves.toMatchObject({ kind: 'synced' });

    await expect(store.get(10, 20)).resolves.toBeNull();
    expect(invalidate).toHaveBeenCalledWith(20);
  });

  it('keeps retryable failures pending and writes retryAfterAt', async () => {
    const store = createMemoryOfflineDraftStore();
    const record = createRecord();
    await store.put(record);

    await syncOfflineDraftRecord({
      store,
      record,
      saveDraft: vi.fn().mockRejectedValue(new Error('network')),
      invalidate: vi.fn(),
      acquireLease: () => true,
      releaseLease: vi.fn(),
      now: () => 1_000,
    });

    await expect(store.get(10, 20)).resolves.toMatchObject({
      status: 'pending',
      lastSyncAttemptAt: 1_000,
      retryAfterAt: 6_000,
      blockedReason: undefined,
    });
  });

  it('blocks auth, not_found, and bad_request failures without deleting pending', async () => {
    await expectBlocked(401, 'auth');
    await expectBlocked(403, 'auth');
    await expectBlocked(404, 'not_found');
    await expectBlocked(400, 'bad_request');
  });

  it('deletes terminal 409 failures and never retries them', async () => {
    const store = createMemoryOfflineDraftStore();
    const record = createRecord();
    await store.put(record);

    const result = await syncOfflineDraftRecord({
      store,
      record,
      saveDraft: vi.fn().mockRejectedValue(new OfflineDraftSyncHttpError(409, 'terminal')),
      invalidate: vi.fn(),
      acquireLease: () => true,
      releaseLease: vi.fn(),
      now: () => 1_000,
    });

    expect(result).toEqual({ kind: 'terminal-cleared', sessionId: 20 });
    await expect(store.get(10, 20)).resolves.toBeNull();
  });

  it('skips background sync while retryAfterAt is in the future but allows manual retry through the lease', async () => {
    const store = createMemoryOfflineDraftStore();
    const record = createRecord({ retryAfterAt: 10_000 });
    const saveDraft = vi.fn().mockResolvedValue({ id: 1 });
    await store.put(record);

    await expect(syncOfflineDraftRecord({
      store,
      record,
      saveDraft,
      invalidate: vi.fn(),
      acquireLease: () => true,
      releaseLease: vi.fn(),
      now: () => 1_000,
    })).resolves.toEqual({ kind: 'skipped-retry-after', sessionId: 20, retryAfterAt: 10_000 });
    expect(saveDraft).not.toHaveBeenCalled();

    await syncOfflineDraftRecord({
      store,
      record,
      saveDraft,
      invalidate: vi.fn(),
      acquireLease: () => true,
      releaseLease: vi.fn(),
      now: () => 1_000,
      manual: true,
    });
    expect(saveDraft).toHaveBeenCalledTimes(1);
  });

  it('does not PUT when another tab holds the lease', async () => {
    const store = createMemoryOfflineDraftStore();
    const record = createRecord();
    const saveDraft = vi.fn();

    await expect(syncOfflineDraftRecord({
      store,
      record,
      saveDraft,
      invalidate: vi.fn(),
      acquireLease: () => false,
      releaseLease: vi.fn(),
      now: () => 1_000,
    })).resolves.toEqual({ kind: 'skipped-lease', sessionId: 20 });
    expect(saveDraft).not.toHaveBeenCalled();
  });
});

describe('offline draft submit pre-sync result mapping', () => {
  it('blocks submit for auth, not_found, and terminal outcomes', () => {
    expect(toOfflineDraftSubmitPreSyncResult({ kind: 'blocked', sessionId: 20, reason: 'auth' })).toMatchObject({
      kind: 'block-submit',
      reason: 'auth',
    });
    expect(toOfflineDraftSubmitPreSyncResult({ kind: 'blocked', sessionId: 20, reason: 'not_found' })).toMatchObject({
      kind: 'block-submit',
      reason: 'not_found',
    });
    expect(toOfflineDraftSubmitPreSyncResult({ kind: 'terminal-cleared', sessionId: 20 })).toEqual({
      kind: 'block-submit',
      reason: 'terminal',
      message: '此会话已在别处提交/释放,本地草稿已弃',
    });
  });

  it('allows submit for retryable and draft-cache blocked outcomes', () => {
    expect(toOfflineDraftSubmitPreSyncResult({ kind: 'retry-scheduled', sessionId: 20, retryAfterAt: 6_000 })).toEqual({
      kind: 'continue-with-pending',
      reason: 'network',
    });
    expect(toOfflineDraftSubmitPreSyncResult({ kind: 'blocked', sessionId: 20, reason: 'bad_request' })).toEqual({
      kind: 'continue-with-pending',
      reason: 'bad_request',
    });
    expect(toOfflineDraftSubmitPreSyncResult({ kind: 'skipped-lease', sessionId: 20 })).toEqual({
      kind: 'continue-with-pending',
      reason: 'network',
    });
  });
});

describe('offline draft sync channel and status tag', () => {
  it('falls back to a no-op channel when BroadcastChannel is unavailable', () => {
    const channel = createOfflineDraftSyncChannel(undefined);
    const unsubscribe = channel.subscribe(vi.fn());

    expect(() => channel.post({ type: 'sync-failed', userId: 1, sessionId: 2, retryAfterAt: 3 })).not.toThrow();
    expect(() => unsubscribe()).not.toThrow();
    expect(() => channel.close()).not.toThrow();
  });

  it('renders sync status without calling pending drafts server-saved', () => {
    expect(renderStatus({ kind: 'syncing', sessionId: 20 })).toContain('本地草稿同步中');
    expect(renderStatus({ kind: 'retry-scheduled', sessionId: 20, retryAfterAt: 6_000 })).toContain('本地草稿待同步');
    expect(renderStatus({ kind: 'blocked', sessionId: 20, reason: 'auth' })).toContain('本地草稿无法同步');
    expect(renderStatus({ kind: 'retry-scheduled', sessionId: 20, retryAfterAt: 6_000 })).not.toContain('已保存');
  });
});

async function expectBlocked(status: 400 | 401 | 403 | 404, reason: 'auth' | 'not_found' | 'bad_request') {
  const store = createMemoryOfflineDraftStore();
  const record = createRecord();
  await store.put(record);

  await syncOfflineDraftRecord({
    store,
    record,
    saveDraft: vi.fn().mockRejectedValue(new OfflineDraftSyncHttpError(status, 'blocked')),
    invalidate: vi.fn(),
    acquireLease: () => true,
    releaseLease: vi.fn(),
    now: () => 1_000,
  });

  await expect(store.get(10, 20)).resolves.toMatchObject({
    status: 'blocked',
    blockedReason: reason,
    retryAfterAt: null,
  });
}

function createRecord(overrides: Partial<ReturnType<typeof createOfflineDraftRecord>> = {}) {
  return createOfflineDraftRecord({
    userId: 10,
    sessionId: 20,
    schemaVersionId: 30,
    payload,
    updatedAt: 500,
    ...overrides,
  });
}

function createMemoryLeaseStorage(): LeaseStorage {
  const values = new Map<string, string>();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}

function renderStatus(offlineSync: OfflineDraftSyncStatus) {
  const autosave: UseAutosaveResult = {
    status: 'saved',
    lastSavedAt: 1_000,
    lastError: null,
    flush: async () => {},
    disable: () => {},
  };

  return renderToString(<AutosaveStatusTag autosave={autosave} offlineSync={offlineSync} onRetryOfflineDraftSync={() => {}} />);
}
