import { useQuery } from '@tanstack/react-query';
import type { SubmissionRenderSchema } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export const submissionRenderSchemaQueryKey = (submissionId: number) => ['submissions', submissionId, 'render-schema'] as const;

export function useSubmissionRenderSchemaQuery(submissionId: number, options?: { enabled?: boolean }) {
  return useQuery<SubmissionRenderSchema>({
    queryKey: submissionRenderSchemaQueryKey(submissionId),
    enabled: (options?.enabled ?? true) && submissionId > 0,
    staleTime: 60_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/submissions/{submissionId}/render-schema', {
        params: { path: { submissionId } },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '历史 Schema 加载失败。');
      }

      return data;
    },
  });
}
