import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { DatasetItemBulkUpdateItem, DatasetItemBulkUpdateResult } from '../../entities/dataset/datasetTypes';
import { apiClient } from '../../shared/api/client';
import { datasetItemsQueryKey } from './useDatasetItemsQuery';

type BulkUpdateDatasetItemsVariables = {
  datasetId: number;
  items: DatasetItemBulkUpdateItem[];
};

export class BulkUpdateDatasetItemsFailure extends Error {
  constructor(public readonly status: number, public readonly userMessage: string) {
    super(userMessage);
  }
}

export function useBulkUpdateDatasetItemsMutation() {
  const queryClient = useQueryClient();

  return useMutation<DatasetItemBulkUpdateResult, BulkUpdateDatasetItemsFailure, BulkUpdateDatasetItemsVariables>({
    mutationFn: async ({ datasetId, items }) => {
      const { data, error, response } = await apiClient.PATCH('/datasets/{datasetId}/items:bulk-update', {
        params: { path: { datasetId } },
        body: { items },
      });

      if (error || !data) {
        throw new BulkUpdateDatasetItemsFailure(response.status, error?.message ?? '批量编辑失败,请稍后重试');
      }

      return data;
    },
    onSuccess: async (_result, variables) => {
      await queryClient.invalidateQueries({ queryKey: datasetItemsQueryKey(variables.datasetId) });
    },
  });
}
