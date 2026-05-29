import {
  buildOfflineDraftKey,
  OFFLINE_DRAFT_TTL_MS,
  type OfflineDraftRecord,
  type OfflineDraftStore,
  isSupportedOfflineDraftRecord,
} from './offlineDraftTypes';

const DEFAULT_DATABASE_NAME = 'labelhub-offline-drafts';
const DEFAULT_STORE_NAME = 'pendingDrafts';
const DATABASE_VERSION = 1;

type StoredOfflineDraftRecord = {
  key: string;
  record: OfflineDraftRecord;
};

export class OfflineDraftStorageUnavailableError extends Error {
  constructor(message = '本地草稿存储不可用') {
    super(message);
    this.name = 'OfflineDraftStorageUnavailableError';
  }
}

export function createIndexedDbOfflineDraftStore(
  options: { databaseName?: string; storeName?: string } = {},
): OfflineDraftStore {
  const databaseName = options.databaseName ?? DEFAULT_DATABASE_NAME;
  const storeName = options.storeName ?? DEFAULT_STORE_NAME;

  async function withStore<T>(mode: IDBTransactionMode, operation: (store: IDBObjectStore) => IDBRequest<T> | Promise<T>) {
    const database = await openDatabase(databaseName, storeName);
    try {
      const transaction = database.transaction(storeName, mode);
      const store = transaction.objectStore(storeName);
      const result = await operation(store);
      return result instanceof IDBRequest ? await requestToPromise(result) : result;
    } finally {
      database.close();
    }
  }

  async function get(userId: number, sessionId: number) {
    const stored = await withStore<StoredOfflineDraftRecord | undefined>('readonly', (store) =>
      store.get(buildOfflineDraftKey(userId, sessionId)),
    );
    const record = stored?.record;
    if (!record || !isSupportedOfflineDraftRecord(record)) return null;
    if (record.userId !== userId || record.sessionId !== sessionId) return null;
    return record;
  }

  async function deleteBySession(userId: number, sessionId: number) {
    await withStore('readwrite', (store) => store.delete(buildOfflineDraftKey(userId, sessionId)));
  }

  return {
    get,
    async put(record) {
      const stored: StoredOfflineDraftRecord = {
        key: buildOfflineDraftKey(record.userId, record.sessionId),
        record,
      };
      await withStore('readwrite', (store) => store.put(stored));
    },
    deleteBySession,
    async discard(userId, sessionId) {
      await deleteBySession(userId, sessionId);
    },
    async listByUser(userId) {
      const records: OfflineDraftRecord[] = [];
      await withStore('readonly', async (store) => {
        await iterateStore(store, (stored) => {
          const record = stored.record;
          if (isSupportedOfflineDraftRecord(record) && record.userId === userId) {
            records.push(record);
          }
        });
      });
      return records;
    },
    async deleteExpired(now, ttlMs = OFFLINE_DRAFT_TTL_MS) {
      let deleted = 0;
      await withStore('readwrite', async (store) => {
        await iterateStore(store, (stored, cursor) => {
          if (stored.record.updatedAt < now - ttlMs) {
            cursor.delete();
            deleted += 1;
          }
        });
      });
      return deleted;
    },
  };
}

function openDatabase(databaseName: string, storeName: string): Promise<IDBDatabase> {
  if (typeof indexedDB === 'undefined') {
    return Promise.reject(new OfflineDraftStorageUnavailableError());
  }

  return new Promise((resolve, reject) => {
    const request = indexedDB.open(databaseName, DATABASE_VERSION);
    request.onerror = () => reject(new OfflineDraftStorageUnavailableError(request.error?.message));
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(storeName)) {
        database.createObjectStore(storeName, { keyPath: 'key' });
      }
    };
    request.onsuccess = () => resolve(request.result);
  });
}

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
}

function iterateStore(
  store: IDBObjectStore,
  onRecord: (record: StoredOfflineDraftRecord, cursor: IDBCursorWithValue) => void,
) {
  return new Promise<void>((resolve, reject) => {
    const request = store.openCursor();
    request.onerror = () => reject(request.error);
    request.onsuccess = () => {
      const cursor = request.result;
      if (!cursor) {
        resolve();
        return;
      }
      onRecord(cursor.value as StoredOfflineDraftRecord, cursor);
      cursor.continue();
    };
  });
}
