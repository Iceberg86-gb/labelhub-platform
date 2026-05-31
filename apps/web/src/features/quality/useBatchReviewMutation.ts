import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { components } from '../../shared/api/generated/schema';
import { apiClient } from '../../shared/api/client';

export type BatchReviewInput = components['schemas']['BatchReviewRequest'];
export type BatchReviewResult = components['schemas']['BatchReviewResult'];

export function useBatchReviewMutation() {
  const queryClient = useQueryClient();
  return useMutation<BatchReviewResult, Error, BatchReviewInput>({
    mutationFn: async (input) => {
      const { data, error } = await apiClient.POST('/reviews/batch', {
        body: input,
      });
      if (error || !data) {
        throw new Error(error?.message ?? '批量审核失败');
      }
      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['reviewer-queue'] });
    },
  });
}
