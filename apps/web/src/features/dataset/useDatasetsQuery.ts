import { useQuery } from '@tanstack/react-query';
import type { PagedDatasets } from '../../entities/dataset/datasetTypes';
import { apiClient } from '../../shared/api/client';

export const datasetsQueryKey = (taskId: number) => ['datasets', taskId] as const;

export function useDatasetsQuery(taskId: number) {
  return useQuery<PagedDatasets>({
    queryKey: datasetsQueryKey(taskId),
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/datasets', {
        params: {
          query: {
            taskId,
            page: 1,
            size: 20,
          },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '数据集列表加载失败。');
      }

      return data;
    },
  });
}
