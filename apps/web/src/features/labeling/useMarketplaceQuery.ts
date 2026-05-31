import { useQuery } from '@tanstack/react-query';
import type { PagedMarketplaceTasks } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export type MarketplaceQueryParams = {
  page: number;
  size: number;
  q?: string;
  tag?: string;
  hasReward?: boolean;
  deadline?: 'day' | 'week';
  enabled?: boolean;
};

export const marketplaceQueryKey = (params: MarketplaceQueryParams) => ['tasks', 'marketplace', params] as const;

export function useMarketplaceQuery(params: MarketplaceQueryParams) {
  const { enabled = true, ...queryParams } = params;

  return useQuery<PagedMarketplaceTasks>({
    queryKey: marketplaceQueryKey(queryParams),
    enabled,
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/marketplace', { params: { query: queryParams } });

      if (error || !data) {
        throw new Error('任务广场加载失败。');
      }

      return data;
    },
  });
}
