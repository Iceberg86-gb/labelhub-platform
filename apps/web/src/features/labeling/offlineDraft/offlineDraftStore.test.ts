import { describe, expect, it } from 'vitest';
import type { AnswerPayload } from '../../../entities/submission/answerPayload';
import {
  OFFLINE_DRAFT_ENCODING,
  OFFLINE_DRAFT_STORAGE_VERSION,
  buildOfflineDraftKey,
  createOfflineDraftRecord,
  isSupportedOfflineDraftRecord,
} from './offlineDraftTypes';
import { createMemoryOfflineDraftStore } from './offlineDraftMemoryStore';
import { OfflineDraftStorageUnavailableError, createIndexedDbOfflineDraftStore } from './offlineDraftStore';

const payload = { field_0: 'answer' } satisfies AnswerPayload;

describe('offline draft record schema', () => {
  it('builds keys from both user and session ids', () => {
    expect(buildOfflineDraftKey(10, 22)).toBe('user:10:session:22');
  });

  it('creates plaintext records with migration hooks', () => {
    const record = createOfflineDraftRecord({
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      payload,
      updatedAt: 1_000,
    });

    expect(record).toMatchObject({
      storageVersion: OFFLINE_DRAFT_STORAGE_VERSION,
      encoding: OFFLINE_DRAFT_ENCODING,
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      payload,
      updatedAt: 1_000,
      lastSyncAttemptAt: null,
      retryAfterAt: null,
      status: 'pending',
    });
  });

  it('rejects unsupported storage versions and encodings predictably', () => {
    const record = createOfflineDraftRecord({
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      payload,
      updatedAt: 1_000,
    });

    expect(isSupportedOfflineDraftRecord(record)).toBe(true);
    expect(isSupportedOfflineDraftRecord({ ...record, storageVersion: 2 })).toBe(false);
    expect(isSupportedOfflineDraftRecord({ ...record, encoding: 'encrypted-json-v1' })).toBe(false);
  });
});

describe('memory offline draft store', () => {
  it('stores and returns records for the matching user and session only', async () => {
    const store = createMemoryOfflineDraftStore();
    const user10 = createOfflineDraftRecord({
      userId: 10,
      sessionId: 22,
      schemaVersionId: 300,
      payload: { field_0: 'user-10' },
      updatedAt: 1_000,
    });
    const user11 = createOfflineDraftRecord({
      userId: 11,
      sessionId: 22,
      schemaVersionId: 300,
      payload: { field_0: 'user-11' },
      updatedAt: 1_001,
    });

    await store.put(user10);
    await store.put(user11);

    expect(await store.get(10, 22)).toEqual(user10);
    expect(await store.get(11, 22)).toEqual(user11);
    expect(await store.get(12, 22)).toBeNull();
  });

  it('does not return a tampered record whose stored user id does not match the key', async () => {
    const store = createMemoryOfflineDraftStore();
    await store.putRawForTest(
      buildOfflineDraftKey(10, 22),
      createOfflineDraftRecord({
        userId: 11,
        sessionId: 22,
        schemaVersionId: 300,
        payload,
        updatedAt: 1_000,
      }),
    );

    expect(await store.get(10, 22)).toBeNull();
  });

  it('deletes by session without touching another user with the same session id', async () => {
    const store = createMemoryOfflineDraftStore();
    await store.put(createOfflineDraftRecord({ userId: 10, sessionId: 22, schemaVersionId: 300, payload, updatedAt: 1_000 }));
    await store.put(createOfflineDraftRecord({ userId: 11, sessionId: 22, schemaVersionId: 300, payload, updatedAt: 1_000 }));

    await store.deleteBySession(10, 22);

    expect(await store.get(10, 22)).toBeNull();
    expect(await store.get(11, 22)).not.toBeNull();
  });

  it('discard removes a single pending draft', async () => {
    const store = createMemoryOfflineDraftStore();
    await store.put(createOfflineDraftRecord({ userId: 10, sessionId: 22, schemaVersionId: 300, payload, updatedAt: 1_000 }));

    await store.discard(10, 22);

    expect(await store.get(10, 22)).toBeNull();
  });

  it('deleteExpired removes old records and keeps fresh records', async () => {
    const store = createMemoryOfflineDraftStore();
    await store.put(createOfflineDraftRecord({ userId: 10, sessionId: 1, schemaVersionId: 300, payload, updatedAt: 1_000 }));
    await store.put(createOfflineDraftRecord({ userId: 10, sessionId: 2, schemaVersionId: 300, payload, updatedAt: 9_000 }));

    const deleted = await store.deleteExpired(10_000, 5_000);

    expect(deleted).toBe(1);
    expect(await store.get(10, 1)).toBeNull();
    expect(await store.get(10, 2)).not.toBeNull();
  });

  it('listByUser returns supported records for a single user', async () => {
    const store = createMemoryOfflineDraftStore();
    await store.put(createOfflineDraftRecord({ userId: 10, sessionId: 1, schemaVersionId: 300, payload, updatedAt: 1_000 }));
    await store.put(createOfflineDraftRecord({ userId: 11, sessionId: 2, schemaVersionId: 300, payload, updatedAt: 1_000 }));

    expect(await store.listByUser(10)).toHaveLength(1);
    expect((await store.listByUser(10))[0].userId).toBe(10);
  });
});

describe('indexedDB offline draft store', () => {
  it('throws a typed error when IndexedDB is unavailable instead of silently falling back', async () => {
    const store = createIndexedDbOfflineDraftStore({ databaseName: 'test-offline-drafts' });

    await expect(store.get(10, 22)).rejects.toBeInstanceOf(OfflineDraftStorageUnavailableError);
  });
});
