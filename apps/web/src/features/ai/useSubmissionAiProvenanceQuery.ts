import { useQuery } from '@tanstack/react-query';
import type { SubmissionAiProvenance } from '../../entities/ai/aiTypes';
import { apiClient } from '../../shared/api/client';

export const aiProvenanceQueryKey = (submissionId: number) => ['submissions', submissionId, 'ai-review'] as const;

export function useSubmissionAiProvenanceQuery(submissionId: number, options?: { enabled?: boolean }) {
  return useQuery<SubmissionAiProvenance | null>({
    queryKey: aiProvenanceQueryKey(submissionId),
    enabled: (options?.enabled ?? true) && submissionId > 0,
    staleTime: 10_000,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/submissions/{submissionId}/ai-review', {
        params: { path: { submissionId } },
      });

      if (response.status === 404) {
        return null;
      }
      if (error || !data) {
        throw new Error(error?.message ?? 'AI provenance 加载失败。');
      }

      return data;
    },
  });
}
