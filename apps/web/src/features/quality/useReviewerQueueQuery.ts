import { useQuery } from '@tanstack/react-query';
import type { PagedReviewerSubmissions, VerdictStatus } from '../../entities/quality/qualityTypes';
import { apiClient } from '../../shared/api/client';

export type ReviewerQueueQueryOptions = {
  page?: number;
  size?: number;
  status?: string;
  verdict?: VerdictStatus;
  enabled?: boolean;
};

export const reviewerQueueQueryKey = (options: ReviewerQueueQueryOptions = {}) =>
  ['reviewer-queue', options.page ?? 1, options.size ?? 20, options.status ?? 'submitted', options.verdict ?? 'all'] as const;

export function useReviewerQueueQuery(options: ReviewerQueueQueryOptions = {}) {
  const page = options.page ?? 1;
  const size = options.size ?? 20;
  const status = options.status ?? 'submitted';

  return useQuery<PagedReviewerSubmissions>({
    queryKey: reviewerQueueQueryKey({ page, size, status, verdict: options.verdict }),
    enabled: options.enabled ?? true,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/reviewer/submissions', {
        params: { query: { page, size, status, verdict: options.verdict } },
      });
      if (error || !data) {
        throw new Error(response.status === 403 ? '无 reviewer 权限' : error?.message ?? '审核队列加载失败。');
      }
      return data;
    },
  });
}
