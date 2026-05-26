import { useQuery } from '@tanstack/react-query';
import type { SessionDetail } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export const sessionDetailQueryKey = (sessionId: number) => ['sessions', sessionId] as const;

export function useSessionDetailQuery(sessionId: number, options?: { enabled?: boolean }) {
  return useQuery<SessionDetail>({
    queryKey: sessionDetailQueryKey(sessionId),
    enabled: (options?.enabled ?? true) && sessionId > 0,
    staleTime: 15_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/sessions/{sessionId}', {
        params: { path: { sessionId } },
      });

      if (error || !data) {
        throw new Error(error?.message ?? 'Session 详情加载失败。');
      }

      return data;
    },
  });
}
