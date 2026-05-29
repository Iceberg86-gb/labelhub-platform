import {
  buildOfflineDraftKey,
  OFFLINE_DRAFT_TTL_MS,
  type OfflineDraftRecord,
  type OfflineDraftStore,
  isSupportedOfflineDraftRecord,
} from './offlineDraftTypes';

export interface MemoryOfflineDraftStore extends OfflineDraftStore {
  putRawForTest(key: string, record: OfflineDraftRecord): Promise<void>;
}

export function createMemoryOfflineDraftStore(): MemoryOfflineDraftStore {
  const records = new Map<string, OfflineDraftRecord>();

  async function get(userId: number, sessionId: number) {
    const record = records.get(buildOfflineDraftKey(userId, sessionId));
    if (!record || !isSupportedOfflineDraftRecord(record)) return null;
    if (record.userId !== userId || record.sessionId !== sessionId) return null;
    return record;
  }

  async function deleteBySession(userId: number, sessionId: number) {
    records.delete(buildOfflineDraftKey(userId, sessionId));
  }

  return {
    get,
    async put(record) {
      records.set(buildOfflineDraftKey(record.userId, record.sessionId), record);
    },
    deleteBySession,
    async discard(userId, sessionId) {
      await deleteBySession(userId, sessionId);
    },
    async listByUser(userId) {
      return Array.from(records.values()).filter(
        (record) => isSupportedOfflineDraftRecord(record) && record.userId === userId,
      );
    },
    async deleteExpired(now, ttlMs = OFFLINE_DRAFT_TTL_MS) {
      let deleted = 0;
      for (const [key, record] of records.entries()) {
        if (record.updatedAt < now - ttlMs) {
          records.delete(key);
          deleted += 1;
        }
      }
      return deleted;
    },
    async putRawForTest(key, record) {
      records.set(key, record);
    },
  };
}
