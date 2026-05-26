import { useQuery } from '@tanstack/react-query';
import type { Submission } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export const submissionDetailQueryKey = (submissionId: number) => ['submissions', submissionId] as const;

export class SubmissionDetailFailure extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function useSubmissionDetailQuery(submissionId: number, options?: { enabled?: boolean }) {
  return useQuery<Submission>({
    queryKey: submissionDetailQueryKey(submissionId),
    enabled: (options?.enabled ?? true) && submissionId > 0,
    staleTime: 20_000,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/submissions/{submissionId}', {
        params: { path: { submissionId } },
      });

      if (error || !data) {
        throw new SubmissionDetailFailure(response.status, error?.message ?? '提交详情加载失败。');
      }

      return data;
    },
  });
}
