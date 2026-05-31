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
};

export const marketplaceQueryKey = (params: MarketplaceQueryParams) => ['tasks', 'marketplace', params] as const;

export function useMarketplaceQuery(params: MarketplaceQueryParams) {
  return useQuery<PagedMarketplaceTasks>({
    queryKey: marketplaceQueryKey(params),
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/marketplace', { params: { query: params } });

      if (error || !data) {
        throw new Error('任务广场加载失败。');
      }

      return data;
    },
  });
}
