import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ClaimTaskItemsResult, Session } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export class ClaimTaskFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    message: string,
  ) {
    super(message);
  }
}

export function useClaimMutation() {
  const queryClient = useQueryClient();

  return useMutation<Session, ClaimTaskFailure, number>({
    mutationFn: async (taskId) => {
      const { data, error, response } = await apiClient.POST('/tasks/{taskId}/claim', {
        params: { path: { taskId } },
      });
      const apiError = error as { code?: string; message?: string } | undefined;
      const status = Number((response as { status?: number }).status ?? 0);

      if (status === 409 && apiError?.code === 'TASK_NOT_AVAILABLE') {
        throw new ClaimTaskFailure(409, apiError.code, '任务暂不可领取,请刷新列表');
      }
      if (status === 409 && apiError?.code === 'NO_AVAILABLE_DATASET_ITEM') {
        throw new ClaimTaskFailure(409, apiError.code, '暂无可领取的数据项');
      }
      if (error || !data) {
        throw new ClaimTaskFailure(status, apiError?.code, apiError?.message ?? '领取任务失败。');
      }

      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tasks', 'marketplace'] });
      await queryClient.invalidateQueries({ queryKey: ['my', 'sessions'] });
    },
  });
}

export type ClaimBatchInput = {
  size: number;
  taskId: number;
};

export function useClaimBatchMutation() {
  const queryClient = useQueryClient();

  return useMutation<ClaimTaskItemsResult, ClaimTaskFailure, ClaimBatchInput>({
    mutationFn: async ({ size, taskId }) => {
      const { data, error, response } = await apiClient.POST('/tasks/{taskId}/claim-batch', {
        params: { path: { taskId } },
        body: { size },
      });
      const apiError = error as { code?: string; message?: string } | undefined;
      const status = Number((response as { status?: number }).status ?? 0);

      if (status === 409 && apiError?.code === 'TASK_NOT_AVAILABLE') {
        throw new ClaimTaskFailure(409, apiError.code, '任务暂不可领取,请刷新列表');
      }
      if (status === 409 && apiError?.code === 'NO_AVAILABLE_DATASET_ITEM') {
        throw new ClaimTaskFailure(409, apiError.code, '暂无可领取的数据项');
      }
      if (error || !data) {
        throw new ClaimTaskFailure(status, apiError?.code, apiError?.message ?? '领取任务失败。');
      }

      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tasks', 'marketplace'] });
      await queryClient.invalidateQueries({ queryKey: ['my', 'sessions'] });
    },
  });
}
