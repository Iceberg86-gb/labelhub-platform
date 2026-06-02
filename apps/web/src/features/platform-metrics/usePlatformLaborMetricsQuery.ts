import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type PlatformLaborMetrics = components['schemas']['PlatformLaborMetrics'];

export const platformLaborMetricsQueryKey = ['platform-labor-metrics'];

export function usePlatformLaborMetricsQuery() {
  return useQuery<PlatformLaborMetrics>({
    queryKey: platformLaborMetricsQueryKey,
    staleTime: 15_000,
    refetchInterval: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/platform/labor-metrics');

      if (error || !data) {
        throw new Error(error?.message ?? '人力计量数据加载失败。');
      }

      return data;
    },
  });
}
