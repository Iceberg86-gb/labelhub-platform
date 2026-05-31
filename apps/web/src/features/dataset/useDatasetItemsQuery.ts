import { useQuery } from '@tanstack/react-query';
import type { PagedDatasetItems } from '../../entities/dataset/datasetTypes';
import { apiClient } from '../../shared/api/client';

export const datasetItemsQueryKey = (datasetId: number | null | undefined) => ['dataset-items', datasetId] as const;

export function useDatasetItemsQuery(datasetId: number | null | undefined) {
  return useQuery<PagedDatasetItems>({
    queryKey: datasetItemsQueryKey(datasetId),
    enabled: datasetId != null,
    staleTime: 15_000,
    queryFn: async () => {
      if (datasetId == null) throw new Error('数据集不存在。');
      const { data, error } = await apiClient.GET('/datasets/{datasetId}/items', {
        params: {
          path: { datasetId },
          query: { page: 1, size: 100 },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '题目列表加载失败。');
      }

      return data;
    },
  });
}
