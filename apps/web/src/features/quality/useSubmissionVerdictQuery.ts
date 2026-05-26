import { useQuery } from '@tanstack/react-query';
import type { Verdict } from '../../entities/quality/qualityTypes';
import { apiClient } from '../../shared/api/client';

export const submissionVerdictQueryKey = (submissionId: number) => ['submissions', submissionId, 'verdict'] as const;

export function useSubmissionVerdictQuery(submissionId: number, options?: { enabled?: boolean }) {
  return useQuery<Verdict | null>({
    queryKey: submissionVerdictQueryKey(submissionId),
    enabled: (options?.enabled ?? true) && submissionId > 0,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/submissions/{submissionId}/verdict', {
        params: { path: { submissionId } },
      });
      if (response.status === 404) return null;
      if (error || !data) throw new Error(error?.message ?? 'Verdict 加载失败。');
      return data;
    },
  });
}
