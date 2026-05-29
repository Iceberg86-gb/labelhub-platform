import type { AnswerPayload } from '../../../entities/submission/answerPayload';

export const OFFLINE_DRAFT_STORAGE_VERSION = 1;
export const OFFLINE_DRAFT_ENCODING = 'plain-json-v1';
export const OFFLINE_DRAFT_TTL_MS = 7 * 24 * 60 * 60 * 1000;

export type OfflineDraftEncoding = typeof OFFLINE_DRAFT_ENCODING;
export type OfflineDraftStatus = 'pending' | 'syncing' | 'blocked';
export type OfflineDraftBlockedReason = 'auth' | 'not_found' | 'terminal' | 'bad_request';

export type OfflineDraftRecord = {
  storageVersion: typeof OFFLINE_DRAFT_STORAGE_VERSION;
  encoding: OfflineDraftEncoding;
  userId: number;
  sessionId: number;
  schemaVersionId: number;
  payload: AnswerPayload;
  updatedAt: number;
  lastSyncAttemptAt: number | null;
  retryAfterAt: number | null;
  status: OfflineDraftStatus;
  blockedReason?: OfflineDraftBlockedReason;
};

export type CreateOfflineDraftRecordInput = {
  userId: number;
  sessionId: number;
  schemaVersionId: number;
  payload: AnswerPayload;
  updatedAt: number;
  lastSyncAttemptAt?: number | null;
  retryAfterAt?: number | null;
  status?: OfflineDraftStatus;
  blockedReason?: OfflineDraftBlockedReason;
};

export interface OfflineDraftStore {
  get(userId: number, sessionId: number): Promise<OfflineDraftRecord | null>;
  put(record: OfflineDraftRecord): Promise<void>;
  deleteBySession(userId: number, sessionId: number): Promise<void>;
  discard(userId: number, sessionId: number): Promise<void>;
  listByUser(userId: number): Promise<OfflineDraftRecord[]>;
  deleteExpired(now: number, ttlMs?: number): Promise<number>;
}

export function buildOfflineDraftKey(userId: number, sessionId: number) {
  return `user:${userId}:session:${sessionId}`;
}

export function createOfflineDraftRecord(input: CreateOfflineDraftRecordInput): OfflineDraftRecord {
  return {
    storageVersion: OFFLINE_DRAFT_STORAGE_VERSION,
    encoding: OFFLINE_DRAFT_ENCODING,
    userId: input.userId,
    sessionId: input.sessionId,
    schemaVersionId: input.schemaVersionId,
    payload: input.payload,
    updatedAt: input.updatedAt,
    lastSyncAttemptAt: input.lastSyncAttemptAt ?? null,
    retryAfterAt: input.retryAfterAt ?? null,
    status: input.status ?? 'pending',
    blockedReason: input.blockedReason,
  };
}

export function isSupportedOfflineDraftRecord(value: unknown): value is OfflineDraftRecord {
  if (!value || typeof value !== 'object') return false;
  const record = value as Partial<OfflineDraftRecord>;
  return record.storageVersion === OFFLINE_DRAFT_STORAGE_VERSION && record.encoding === OFFLINE_DRAFT_ENCODING;
}
