import type { OfflineDraftBlockedReason } from './offlineDraftTypes';

export const OFFLINE_DRAFT_SYNC_CHANNEL = 'labelhub-offline-drafts';

export type OfflineDraftSyncMessage =
  | { type: 'pending-updated'; userId: number; sessionId: number; updatedAt: number }
  | { type: 'sync-started'; userId: number; sessionId: number }
  | { type: 'sync-succeeded'; userId: number; sessionId: number }
  | { type: 'sync-blocked'; userId: number; sessionId: number; reason: OfflineDraftBlockedReason }
  | { type: 'sync-failed'; userId: number; sessionId: number; retryAfterAt: number };

export type OfflineDraftSyncChannel = {
  post: (message: OfflineDraftSyncMessage) => void;
  subscribe: (listener: (message: OfflineDraftSyncMessage) => void) => () => void;
  close: () => void;
};

export function createOfflineDraftSyncChannel(
  BroadcastChannelCtor: typeof BroadcastChannel | undefined = typeof BroadcastChannel === 'undefined'
    ? undefined
    : BroadcastChannel,
): OfflineDraftSyncChannel {
  if (!BroadcastChannelCtor) {
    return {
      post() {},
      subscribe() {
        return () => {};
      },
      close() {},
    };
  }

  const channel = new BroadcastChannelCtor(OFFLINE_DRAFT_SYNC_CHANNEL);
  const listeners = new Set<(message: OfflineDraftSyncMessage) => void>();
  channel.onmessage = (event) => {
    for (const listener of listeners) {
      listener(event.data as OfflineDraftSyncMessage);
    }
  };

  return {
    post(message) {
      channel.postMessage(message);
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    close() {
      listeners.clear();
      channel.close();
    },
  };
}
