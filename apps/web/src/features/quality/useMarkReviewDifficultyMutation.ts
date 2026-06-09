import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { SeniorReviewCase } from '../../entities/quality/qualityTypes';
import { apiClient } from '../../shared/api/client';

export type MarkReviewDifficultyInput = {
  submissionId: number;
  reason: string;
};

export function useMarkReviewDifficultyMutation() {
  const queryClient = useQueryClient();

  return useMutation<SeniorReviewCase, Error, MarkReviewDifficultyInput>({
    mutationFn: async ({ submissionId, reason }) => {
      const { data, error, response } = await apiClient.POST('/submissions/{submissionId}/review-difficulty', {
        params: { path: { submissionId } },
        body: { reason },
      });
      if (error || !data) {
        throw new Error(response.status === 403 ? '没有标记疑难权限' : error?.message ?? '标记疑难失败');
      }
      return data;
    },
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['senior-review-cases'] }),
        queryClient.invalidateQueries({ queryKey: ['submissions', variables.submissionId, 'verdict'] }),
        queryClient.invalidateQueries({ queryKey: ['reviewer-queue'] }),
      ]);
    },
  });
}
