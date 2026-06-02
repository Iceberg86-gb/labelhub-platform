import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type PlatformCostMetrics = components['schemas']['PlatformCostMetrics'];

type CostMetricsParams = {
  from?: string;
  to?: string;
};

export const platformCostMetricsQueryKey = (params: CostMetricsParams) => ['platform-cost-metrics', params];

export function usePlatformCostMetricsQuery(params: CostMetricsParams = {}) {
  return useQuery<PlatformCostMetrics>({
    queryKey: platformCostMetricsQueryKey(params),
    staleTime: 15_000,
    refetchInterval: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/platform/cost-metrics', {
        params: { query: params },
      });

      if (error || !data) {
        throw new Error(error?.message ?? 'Token 成本数据加载失败。');
      }

      return data;
    },
  });
}
