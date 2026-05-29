import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { createMemoryOfflineDraftStore } from './offlineDraft/offlineDraftMemoryStore';
import { createOfflineDraftRecord, type OfflineDraftStore } from './offlineDraft/offlineDraftTypes';
import { OfflineDraftStorageUnavailableError } from './offlineDraft/offlineDraftStore';
import {
  applyOfflineDraftHydrationResult,
  bufferPendingOfflineDraft,
  createOfflineDraftHydrationGuard,
  hydrateOfflineDraft,
  type OfflineDraftBufferStatus,
} from './useOfflineDraftBuffer';
import { AutosaveStatusTag } from './AutosaveStatusTag';
import type { UseAutosaveResult } from './useAutosave';

vi.mock('@douyinfe/semi-ui', () => ({
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Tooltip: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
}));

const serverPayload = { field_0: 'server' } satisfies AnswerPayload;
const localPayload = { field_0: 'local' } satisfies AnswerPayload;

describe('hydrateOfflineDraft', () => {
  it('uses matching local pending drafts before the server payload', async () => {
    const store = createMemoryOfflineDraftStore();
    await store.put(createOfflineDraftRecord({
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      payload: localPayload,
      updatedAt: 1_000,
    }));

    await expect(hydrateOfflineDraft({
      store,
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      serverPayload,
    })).resolves.toMatchObject({
      source: 'local',
      payload: localPayload,
    });
  });

  it('returns the server payload and avoids local reads when the user id is missing', async () => {
    const store = createMemoryOfflineDraftStore();
    const getSpy = vi.spyOn(store, 'get');

    await expect(hydrateOfflineDraft({
      store,
      userId: null,
      sessionId: 22,
      schemaVersionId: 300,
      serverPayload,
    })).resolves.toEqual({
      source: 'server',
      payload: serverPayload,
    });
    expect(getSpy).not.toHaveBeenCalled();
  });

  it('falls back to the unchanged server payload when no local pending draft exists', async () => {
    const store = createMemoryOfflineDraftStore();

    await expect(hydrateOfflineDraft({
      store,
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      serverPayload,
    })).resolves.toEqual({
      source: 'server',
      payload: serverPayload,
    });
  });

  it('blocks mismatched schema versions without hydrating local payload', async () => {
    const store = createMemoryOfflineDraftStore();
    await store.put(createOfflineDraftRecord({
      userId: 10,
      sessionId: 22,
      schemaVersionId: 299,
      payload: localPayload,
      updatedAt: 1_000,
    }));

    await expect(hydrateOfflineDraft({
      store,
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      serverPayload,
    })).resolves.toEqual({
      source: 'blocked',
      payload: serverPayload,
      reason: 'schema-version-mismatch',
    });
  });

  it('degrades to the server payload when IndexedDB storage is unavailable', async () => {
    const store = unavailableStore();

    await expect(hydrateOfflineDraft({
      store,
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      serverPayload,
    })).resolves.toEqual({
      source: 'storage-unavailable',
      payload: serverPayload,
    });
  });
});

describe('bufferPendingOfflineDraft', () => {
  it('writes a pending local draft and returns local-buffered status', async () => {
    const store = createMemoryOfflineDraftStore();

    await expect(bufferPendingOfflineDraft({
      store,
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      payload: localPayload,
      now: 2_000,
    })).resolves.toEqual({ buffered: true, savedAt: 2_000 });

    await expect(store.get(10, 22)).resolves.toMatchObject({
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      payload: localPayload,
      updatedAt: 2_000,
      status: 'pending',
    });
  });

  it('does not write when the user id is missing', async () => {
    const store = createMemoryOfflineDraftStore();
    const putSpy = vi.spyOn(store, 'put');

    await expect(bufferPendingOfflineDraft({
      store,
      userId: null,
      sessionId: 22,
      schemaVersionId: 300,
      payload: localPayload,
      now: 2_000,
    })).resolves.toEqual({ buffered: false, reason: 'missing-user' });
    expect(putSpy).not.toHaveBeenCalled();
  });
});

describe('offline draft hydration cancellation', () => {
  it('does not apply a stale async hydrate result after cancellation', () => {
    const guard = createOfflineDraftHydrationGuard();
    const apply = vi.fn();

    guard.cancel();
    const applied = applyOfflineDraftHydrationResult({ source: 'local', payload: localPayload, restoredAt: 2_000 }, guard, apply);

    expect(applied).toBe(false);
    expect(apply).not.toHaveBeenCalled();
  });
});

describe('AutosaveStatusTag offline draft status', () => {
  it('shows local buffered, restored, and blocked states without calling them server-saved', () => {
    expect(renderStatus({ kind: 'local-buffered', savedAt: 2_000 })).toContain('本地已暂存');
    expect(renderStatus({ kind: 'local-restored' })).toContain('已恢复本地草稿');
    expect(renderStatus({ kind: 'blocked', reason: 'schema-version-mismatch' })).toContain('本地草稿未恢复');
    expect(renderStatus({ kind: 'local-buffered', savedAt: 2_000 })).not.toContain('已保存');
  });
});

function unavailableStore(): OfflineDraftStore {
  return {
    async get() {
      throw new OfflineDraftStorageUnavailableError();
    },
    async put() {
      throw new OfflineDraftStorageUnavailableError();
    },
    async deleteBySession() {},
    async discard() {},
    async listByUser() {
      return [];
    },
    async deleteExpired() {
      return 0;
    },
  };
}

function renderStatus(offlineDraft: OfflineDraftBufferStatus) {
  const autosave: UseAutosaveResult = {
    status: 'saved',
    lastSavedAt: 1_000,
    lastError: null,
    flush: async () => {},
    disable: () => {},
  };

  return renderToString(<AutosaveStatusTag autosave={autosave} offlineDraft={offlineDraft} />);
}
