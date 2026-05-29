const DEFAULT_LEASE_TTL_MS = 15_000;
const LEASE_KEY_PREFIX = 'labelhub:offline-draft-sync-lease';

export type LeaseStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

type LeaseRecord = {
  ownerId: string;
  expiresAt: number;
};

export function offlineDraftSyncLeaseKey(userId: number, sessionId: number) {
  return `${LEASE_KEY_PREFIX}:user:${userId}:session:${sessionId}`;
}

export function acquireOfflineDraftSyncLease({
  storage = defaultLeaseStorage(),
  userId,
  sessionId,
  ownerId,
  now,
  ttlMs = DEFAULT_LEASE_TTL_MS,
}: {
  storage?: LeaseStorage | null;
  userId: number;
  sessionId: number;
  ownerId: string;
  now: number;
  ttlMs?: number;
}) {
  if (!storage) return true;

  const key = offlineDraftSyncLeaseKey(userId, sessionId);
  const current = parseLease(storage.getItem(key));
  if (current && current.expiresAt > now && current.ownerId !== ownerId) {
    return false;
  }

  storage.setItem(key, JSON.stringify({ ownerId, expiresAt: now + ttlMs } satisfies LeaseRecord));
  return true;
}

export function releaseOfflineDraftSyncLease({
  storage = defaultLeaseStorage(),
  userId,
  sessionId,
  ownerId,
}: {
  storage?: LeaseStorage | null;
  userId: number;
  sessionId: number;
  ownerId: string;
}) {
  if (!storage) return;

  const key = offlineDraftSyncLeaseKey(userId, sessionId);
  const current = parseLease(storage.getItem(key));
  if (!current || current.ownerId === ownerId) {
    storage.removeItem(key);
  }
}

function parseLease(value: string | null): LeaseRecord | null {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value) as Partial<LeaseRecord>;
    if (typeof parsed.ownerId === 'string' && typeof parsed.expiresAt === 'number') {
      return { ownerId: parsed.ownerId, expiresAt: parsed.expiresAt };
    }
    return null;
  } catch {
    return null;
  }
}

function defaultLeaseStorage(): LeaseStorage | null {
  return typeof window === 'undefined' ? null : window.localStorage;
}
