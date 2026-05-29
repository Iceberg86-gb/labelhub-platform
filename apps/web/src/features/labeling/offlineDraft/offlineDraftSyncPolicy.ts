import type { OfflineDraftBlockedReason } from './offlineDraftTypes';

export const OFFLINE_DRAFT_RETRY_DELAYS_MS = [5_000, 10_000, 20_000, 40_000, 60_000] as const;

export class OfflineDraftSyncHttpError extends Error {
  constructor(
    public readonly status: number,
    message = '草稿同步失败',
  ) {
    super(message);
    this.name = 'OfflineDraftSyncHttpError';
  }
}

export type OfflineDraftSyncFailurePlan =
  | { action: 'retry'; message: string }
  | { action: 'block'; blockedReason: OfflineDraftBlockedReason; message: string }
  | { action: 'delete'; message: string };

export function classifyOfflineDraftSyncFailure(error: unknown): OfflineDraftSyncFailurePlan {
  if (error instanceof OfflineDraftSyncHttpError) {
    if (error.status === 409) {
      return {
        action: 'delete',
        message: '此会话已在别处提交/释放,本地草稿已弃',
      };
    }
    if (error.status === 401 || error.status === 403) {
      return {
        action: 'block',
        blockedReason: 'auth',
        message: '登录状态或权限已失效,本地草稿已保留',
      };
    }
    if (error.status === 404) {
      return {
        action: 'block',
        blockedReason: 'not_found',
        message: '会话不可用,本地草稿已保留',
      };
    }
    if (error.status === 400) {
      return {
        action: 'block',
        blockedReason: 'bad_request',
        message: '草稿格式异常,请继续编辑或稍后重试',
      };
    }
    if (error.status >= 500) {
      return {
        action: 'retry',
        message: '服务器暂时不可用,本地草稿稍后重试同步',
      };
    }
  }

  return {
    action: 'retry',
    message: '网络不可用,本地草稿稍后重试同步',
  };
}

export function computeNextRetryAfter({
  now,
  lastSyncAttemptAt,
  retryAfterAt,
}: {
  now: number;
  lastSyncAttemptAt: number | null;
  retryAfterAt: number | null;
}) {
  const previousDelay = lastSyncAttemptAt && retryAfterAt
    ? Math.max(OFFLINE_DRAFT_RETRY_DELAYS_MS[0], retryAfterAt - lastSyncAttemptAt)
    : 0;
  const nextDelay = OFFLINE_DRAFT_RETRY_DELAYS_MS.find((delay) => delay > previousDelay)
    ?? OFFLINE_DRAFT_RETRY_DELAYS_MS[OFFLINE_DRAFT_RETRY_DELAYS_MS.length - 1];
  return now + nextDelay;
}
