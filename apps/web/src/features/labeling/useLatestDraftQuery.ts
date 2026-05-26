import { useQuery } from '@tanstack/react-query';
import type { Draft } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export const latestDraftQueryKey = (sessionId: number) => ['sessions', sessionId, 'draft'] as const;

export function useLatestDraftQuery(sessionId: number, options?: { enabled?: boolean }) {
  return useQuery<Draft | null>({
    queryKey: latestDraftQueryKey(sessionId),
    enabled: (options?.enabled ?? true) && sessionId > 0,
    retry: false,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/sessions/{sessionId}/draft', {
        params: { path: { sessionId } },
      });

      if (response.status === 404) {
        return null;
      }
      if (error) {
        throw new Error(error.message ?? '草稿加载失败。');
      }

      return data ?? null;
    },
  });
}
