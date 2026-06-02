import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type PlatformEfficiencyMetrics = components['schemas']['PlatformEfficiencyMetrics'];

export const platformEfficiencyMetricsQueryKey = ['platform-efficiency-metrics'];

export function usePlatformEfficiencyMetricsQuery() {
  return useQuery<PlatformEfficiencyMetrics>({
    queryKey: platformEfficiencyMetricsQueryKey,
    staleTime: 15_000,
    refetchInterval: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/platform/efficiency-metrics');

      if (error || !data) {
        throw new Error(error?.message ?? 'Token 复用计量数据加载失败。');
      }

      return data;
    },
  });
}
