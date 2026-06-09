import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { SeniorReviewCase, SeniorReviewCaseResolution } from '../../entities/quality/qualityTypes';
import { apiClient } from '../../shared/api/client';

export type ResolveSeniorReviewCaseInput = {
  caseId: number;
  submissionId: number;
  resolution: SeniorReviewCaseResolution;
  reason?: string;
};

export function useResolveSeniorReviewCaseMutation() {
  const queryClient = useQueryClient();

  return useMutation<SeniorReviewCase, Error, ResolveSeniorReviewCaseInput>({
    mutationFn: async ({ caseId, resolution, reason }) => {
      const { data, error, response } = await apiClient.POST('/senior-review/cases/{caseId}/resolve', {
        params: { path: { caseId } },
        body: { resolution, reason },
      });
      if (error || !data) {
        throw new Error(response.status === 403 ? '没有高级审核权限' : error?.message ?? '提交仲裁结论失败');
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
